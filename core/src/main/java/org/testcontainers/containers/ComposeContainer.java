package org.testcontainers.containers;

import com.github.dockerjava.api.model.Container;
import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.Base58;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Testcontainers implementation for Docker Compose V2. <br>
 * It uses either Compose V2 contained within the Docker binary, or a containerised version of Compose V2.
 */
@Slf4j
public class ComposeContainer extends FailureDetectingExternalResource implements Startable {

    private final Map<String, Integer> scalingPreferences = new HashMap<>();

    private boolean localCompose;

    private boolean pull = true;

    private boolean build = false;

    private Set<String> options = new HashSet<>();

    private boolean tailChildContainers;

    private static final Object MUTEX = new Object();

    private List<String> services = new ArrayList<>();

    /**
     * Properties that should be passed through to all Compose and ambassador containers (not
     * necessarily to containers that are spawned by Compose itself)
     */
    private Map<String, String> env = new HashMap<>();

    private RemoveImages removeImages;

    private boolean removeVolumes = true;

    public static final String COMPOSE_EXECUTABLE = SystemUtils.IS_OS_WINDOWS ? "docker.exe" : "docker";

    private static final String DEFAULT_DOCKER_IMAGE = "docker:27.5.0";

    private final ComposeDelegate composeDelegate;

    private String project;

    private List<String> filesInDirectory = new ArrayList<>();

    public ComposeContainer(DockerImageName image, File... composeFiles) {
        this(image, Arrays.asList(composeFiles));
    }

    public ComposeContainer(DockerImageName image, List<File> composeFiles) {
        this(image,Base58.randomString(6).toLowerCase(),composeFiles);
    }

    public ComposeContainer(DockerImageName image, String identifier, File... composeFiles) {
        this(image,identifier, Arrays.asList(composeFiles));
    }

    public ComposeContainer(DockerImageName image, String identifier, List<File> composeFiles) {
        this.composeDelegate =
            new ComposeDelegate(
                ComposeDelegate.ComposeVersion.V2,
                composeFiles,
                identifier,
                COMPOSE_EXECUTABLE,
                image
            );
        this.project = this.composeDelegate.getProject();
    }

    /**
     * @deprecated
     *  Use the new constructor ComposeContainer(DockerImageName image, File... composeFiles)
     */
    @Deprecated
    public ComposeContainer(File... composeFiles) {
        this(getDockerImageName(),Arrays.asList(composeFiles));
    }
    /**
     * @deprecated
     *  Use the new constructor ComposeContainer(DockerImageName image,List<File> composeFiles)
     */
    @Deprecated
    public ComposeContainer(List<File> composeFiles) {
        this(getDockerImageName(), composeFiles);
    }
    /**
     * @deprecated
     *  Use the new constructor ComposeContainer(DockerImageName image, String identifier, File... composeFile)
     */
    @Deprecated
    public ComposeContainer(String identifier, File... composeFiles) {
        this(getDockerImageName(),identifier, Arrays.asList(composeFiles));
    }

    /**
     * @deprecated
     * Use the new constructor ComposeContainer(DockerImageName image,String identifier, List<File> composeFiles)
     */
    @Deprecated
    public ComposeContainer(String identifier, List<File> composeFiles) {
       this(getDockerImageName(),identifier, composeFiles);
    }

    public static DockerImageName getDockerImageName() {
        return DockerImageName.parse(
            TestcontainersConfiguration
                .getInstance()
                .getEnvVarOrUserProperty("compose.container.image", DEFAULT_DOCKER_IMAGE)
        );
    }

    @Override
    @Deprecated
    public Statement apply(Statement base, Description description) {
        return super.apply(base, description);
    }

    @Override
    @Deprecated
    public void starting(Description description) {
        start();
    }

    @Override
    @Deprecated
    protected void succeeded(Description description) {}

    @Override
    @Deprecated
    protected void failed(Throwable e, Description description) {}

    @Override
    @Deprecated
    public void finished(Description description) {
        stop();
    }

    @Override
    public void start() {
        synchronized (MUTEX) {
            this.composeDelegate.registerContainersForShutdown();
            if (pull) {
                try {
                    this.composeDelegate.pullImages();
                } catch (ContainerLaunchException e) {
                    log.warn("Exception while pulling images, using local images if available", e);
                }
            }
            this.composeDelegate.createServices(
                    this.localCompose,
                    this.build,
                    this.options,
                    this.services,
                    this.scalingPreferences,
                    this.env,
                    this.filesInDirectory
                );
            this.composeDelegate.startAmbassadorContainer();
            this.composeDelegate.waitUntilServiceStarted(this.tailChildContainers);
        }
    }

    @VisibleForTesting
    List<Container> listChildContainers() {
        return this.composeDelegate.listChildContainers();
    }

    public ComposeContainer withServices(@NonNull String... services) {
        this.services = Arrays.asList(services);
        return this;
    }

    @Override
    public void stop() {
        synchronized (MUTEX) {
            try {
                this.composeDelegate.getAmbassadorContainer().stop();

                // Kill the services using docker
                String cmd = ComposeCommand.getDownCommand(ComposeDelegate.ComposeVersion.V2, this.options);

                if (removeVolumes) {
                    cmd += " -v";
                }
                if (removeImages != null) {
                    cmd += " --rmi " + removeImages.dockerRemoveImagesType();
                }
                this.composeDelegate.runWithCompose(this.localCompose, cmd, this.env, this.filesInDirectory);
            } finally {
                this.composeDelegate.clear();
                this.project = this.composeDelegate.randomProjectId();
            }
        }
    }

    public ComposeContainer withExposedService(String serviceName, int servicePort) {
        this.composeDelegate.withExposedService(serviceName, servicePort, Wait.defaultWaitStrategy());
        return this;
    }

    public ComposeContainer withExposedService(String serviceName, int instance, int servicePort) {
        return withExposedService(serviceName + "-" + instance, servicePort);
    }

    public ComposeContainer withExposedService(
        String serviceName,
        int instance,
        int servicePort,
        WaitStrategy waitStrategy
    ) {
        this.composeDelegate.withExposedService(serviceName + "-" + instance, servicePort, waitStrategy);
        return this;
    }

    public ComposeContainer withExposedService(
        String serviceName,
        int servicePort,
        @NonNull WaitStrategy waitStrategy
    ) {
        this.composeDelegate.withExposedService(serviceName, servicePort, waitStrategy);
        return this;
    }

    /**
     * Specify the {@link WaitStrategy} to use to determine if the container is ready.
     *
     * @param serviceName  the name of the service to wait for
     * @param waitStrategy the WaitStrategy to use
     * @return this
     * @see org.testcontainers.containers.wait.strategy.Wait#defaultWaitStrategy()
     */
    public ComposeContainer waitingFor(String serviceName, @NonNull WaitStrategy waitStrategy) {
        String serviceInstanceName = this.composeDelegate.getServiceInstanceName(serviceName);
        this.composeDelegate.addWaitStrategy(serviceInstanceName, waitStrategy);
        return this;
    }

    /**
     * Get the host (e.g. IP address or hostname) that an exposed service can be found at, from the host machine
     * (i.e. should be the machine that's running this Java process).
     * <p>
     * The service must have been declared using ComposeContainer#withExposedService.
     *
     * @param serviceName the name of the service as set in the docker-compose.yml file.
     * @param servicePort the port exposed by the service container.
     * @return a host IP address or hostname that can be used for accessing the service container.
     */
    public String getServiceHost(String serviceName, Integer servicePort) {
        return this.composeDelegate.getServiceHost();
    }

    /**
     * Get the port that an exposed service can be found at, from the host machine
     * (i.e. should be the machine that's running this Java process).
     * <p>
     * The service must have been declared using ComposeContainer#withExposedService.
     *
     * @param serviceName the name of the service as set in the docker-compose.yml file.
     * @param servicePort the port exposed by the service container.
     * @return a port that can be used for accessing the service container.
     */
    public Integer getServicePort(String serviceName, Integer servicePort) {
        return this.composeDelegate.getServicePort(serviceName, servicePort);
    }

    public ComposeContainer withScaledService(String serviceBaseName, int numInstances) {
        scalingPreferences.put(serviceBaseName, numInstances);
        return this;
    }

    public ComposeContainer withEnv(String key, String value) {
        env.put(key, value);
        return this;
    }

    public ComposeContainer withEnv(Map<String, String> env) {
        env.forEach(this.env::put);
        return this;
    }

    /**
     * Use a local Docker Compose binary instead of a container.
     *
     * @return this instance, for chaining
     */
    public ComposeContainer withLocalCompose(boolean localCompose) {
        this.localCompose = localCompose;
        return this;
    }

    /**
     * Whether to pull images first.
     *
     * @return this instance, for chaining
     */
    public ComposeContainer withPull(boolean pull) {
        this.pull = pull;
        return this;
    }

    /**
     * Whether to tail child container logs.
     *
     * @return this instance, for chaining
     */
    public ComposeContainer withTailChildContainers(boolean tailChildContainers) {
        this.tailChildContainers = tailChildContainers;
        return this;
    }

    /**
     * Attach an output consumer at container startup, enabling stdout and stderr to be followed, waited on, etc.
     * <p>
     * More than one consumer may be registered.
     *
     * @param serviceName the name of the service as set in the docker-compose.yml file
     * @param consumer    consumer that output frames should be sent to
     * @return this instance, for chaining
     */
    public ComposeContainer withLogConsumer(String serviceName, Consumer<OutputFrame> consumer) {
        this.composeDelegate.withLogConsumer(serviceName, consumer);
        return this;
    }

    /**
     * Whether to always build images before starting containers.
     *
     * @return this instance, for chaining
     */
    public ComposeContainer withBuild(boolean build) {
        this.build = build;
        return this;
    }

    /**
     * Adds options to the docker command, e.g. docker --compatibility.
     *
     * @return this instance, for chaining
     */
    public ComposeContainer withOptions(String... options) {
        this.options = new HashSet<>(Arrays.asList(options));
        return this;
    }

    /**
     * Remove images after containers shutdown.
     *
     * @return this instance, for chaining
     */
    public ComposeContainer withRemoveImages(ComposeContainer.RemoveImages removeImages) {
        this.removeImages = removeImages;
        return this;
    }

    /**
     * Remove volumes after containers shut down.
     *
     * @param removeVolumes whether volumes are to be removed.
     * @return this instance, for chaining.
     */
    public ComposeContainer withRemoveVolumes(boolean removeVolumes) {
        this.removeVolumes = removeVolumes;
        return this;
    }

    /**
     * Set the maximum startup timeout all the waits set are bounded to.
     *
     * @return this instance. for chaining
     */
    public ComposeContainer withStartupTimeout(Duration startupTimeout) {
        this.composeDelegate.setStartupTimeout(startupTimeout);
        return this;
    }

    public ComposeContainer withCopyFilesInContainer(String... fileCopyInclusions) {
        this.filesInDirectory = Arrays.asList(fileCopyInclusions);
        return this;
    }

    public Optional<ContainerState> getContainerByServiceName(String serviceName) {
        return this.composeDelegate.getContainerByServiceName(serviceName);
    }

    private void followLogs(String containerId, Consumer<OutputFrame> consumer) {
        this.followLogs(containerId, consumer);
    }

    public enum RemoveImages {
        /**
         * Remove all images used by any service.
         */
        ALL("all"),

        /**
         * Remove only images that don't have a custom tag set by the `image` field.
         */
        LOCAL("local");

        private final String dockerRemoveImagesType;

        RemoveImages(final String dockerRemoveImagesType) {
            this.dockerRemoveImagesType = dockerRemoveImagesType;
        }

        public String dockerRemoveImagesType() {
            return dockerRemoveImagesType;
        }
    }
}
