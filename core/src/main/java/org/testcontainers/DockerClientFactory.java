package org.testcontainers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerClientDelegate;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.api.model.Volume;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.DockerMachineClientProviderStrategy;
import org.testcontainers.dockerclient.TransportConfig;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.TimeLimitedLoggedPullImageResultCallback;
import org.testcontainers.utility.ComparableVersion;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.ResourceReaper;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Singleton class that provides initialized Docker clients.
 * <p>
 * The correct client configuration to use will be determined on first use, and cached thereafter.
 */
@Slf4j
public class DockerClientFactory {

    public static final ThreadGroup TESTCONTAINERS_THREAD_GROUP = new ThreadGroup("testcontainers");

    public static final String TESTCONTAINERS_LABEL = DockerClientFactory.class.getPackage().getName();

    public static final String TESTCONTAINERS_SESSION_ID_LABEL = TESTCONTAINERS_LABEL + ".sessionId";

    public static final String TESTCONTAINERS_LANG_LABEL = TESTCONTAINERS_LABEL + ".lang";

    public static final String TESTCONTAINERS_VERSION_LABEL = TESTCONTAINERS_LABEL + ".version";

    public static final String SESSION_ID = UUID.randomUUID().toString();

    public static final String TESTCONTAINERS_VERSION =
        DockerClientFactory.class.getPackage().getImplementationVersion();

    public static final Map<String, String> DEFAULT_LABELS = markerLabels();

    private static final String DEFAULT_DOCKER_DOCKER_PATH = "/var/run/docker.sock";

    static Map<String, String> markerLabels() {
        String testcontainersVersion = TESTCONTAINERS_VERSION == null ? "unspecified" : TESTCONTAINERS_VERSION;

        Map<String, String> labels = new HashMap<>();
        labels.put(TESTCONTAINERS_LABEL, "true");
        labels.put(TESTCONTAINERS_LANG_LABEL, "java");
        labels.put(TESTCONTAINERS_VERSION_LABEL, testcontainersVersion);
        return Collections.unmodifiableMap(labels);
    }

    private static final DockerImageName TINY_IMAGE = DockerImageName.parse("alpine:3.17");

    private static DockerClientFactory instance;

    // Cached client configuration
    @VisibleForTesting
    DockerClientProviderStrategy strategy;

    @VisibleForTesting
    DockerClient client;

    @VisibleForTesting
    RuntimeException cachedClientFailure;

    private String activeApiVersion;

    @Getter(lazy = true)
    private final boolean fileMountingSupported = checkMountableFile();

    @VisibleForTesting
    DockerClientFactory() {}

    public static DockerClient lazyClient() {
        return new DockerClientDelegate() {
            @Override
            protected DockerClient getDockerClient() {
                return instance().client();
            }

            @Override
            public String toString() {
                return "LazyDockerClient";
            }
        };
    }

    /**
     * Obtain an instance of the DockerClientFactory.
     *
     * @return the singleton instance of DockerClientFactory
     */
    public static synchronized DockerClientFactory instance() {
        if (instance == null) {
            instance = new DockerClientFactory();
        }

        return instance;
    }

    /**
     * Checks whether Docker is accessible and {@link #client()} is able to produce a client.
     *
     * @return true if Docker is available, false if not.
     */
    public synchronized boolean isDockerAvailable() {
        try {
            client();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    @Synchronized
    private DockerClientProviderStrategy getOrInitializeStrategy() {
        if (strategy != null) {
            return strategy;
        }
        log.info("Testcontainers version: {}", DEFAULT_LABELS.get(TESTCONTAINERS_VERSION_LABEL));
        List<DockerClientProviderStrategy> configurationStrategies = new ArrayList<>();
        ServiceLoader.load(DockerClientProviderStrategy.class).forEach(configurationStrategies::add);

        strategy = DockerClientProviderStrategy.getFirstValidStrategy(configurationStrategies);
        return strategy;
    }

    @UnstableAPI
    public TransportConfig getTransportConfig() {
        return getOrInitializeStrategy().getTransportConfig();
    }

    @UnstableAPI
    public String getRemoteDockerUnixSocketPath() {
        DockerClientProviderStrategy strategy = getOrInitializeStrategy();
        if (strategy.allowUserOverrides()) {
            String dockerSocketOverride = System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE");
            if (!StringUtils.isBlank(dockerSocketOverride)) {
                return dockerSocketOverride;
            }
        }
        if (strategy.getRemoteDockerUnixSocketPath() != null) {
            return strategy.getRemoteDockerUnixSocketPath();
        }

        URI dockerHost = getTransportConfig().getDockerHost();

        // When the docker host is not local, assume that the remote one is running using the default privileged mode
        // and used the standard /var/run/docker.sock socket
        String path = this.isDockerDaemonSocketLocal(dockerHost) ? dockerHost.getRawPath() : DEFAULT_DOCKER_DOCKER_PATH;

        return SystemUtils.IS_OS_WINDOWS ? "/" + path : path;
    }

    private Boolean isDockerDaemonSocketLocal(URI dockerHost) {
        if (!"unix".equals(dockerHost.getScheme())) {
            log.debug("docker host is not a unix socket. Docker socket is not considered local");
            return false;
        }
        // Several Desktop providers runs docker within a virtual machime running on the host
        // (including Docker Desktop and Rancher Desktop) exposes the Docker socket within the user's home directory.
        //
        // Docker ce linux also has a rootless capability that creates a user-owned socket in the $XDG_RUNTIME_DIR folder (usually /run/user/$UID)
        // see https://docs.docker.com/engine/security/rootless/
        try {
            return !Paths
                .get(dockerHost.getRawPath())
                .toRealPath()
                .normalize()
                .startsWith(System.getProperty("user.home"));
        } catch (IOException e) {
            log.debug(
                "error checking whether the final docker socket path is in user home. Docker socket is considered local"
            );
            return true;
        }
    }

    /**
     * @return a new initialized Docker client
     */
    @Synchronized
    public DockerClient client() {
        // fail-fast if checks have failed previously
        if (cachedClientFailure != null) {
            log.debug("There is a cached checks failure - throwing", cachedClientFailure);
            throw cachedClientFailure;
        }

        if (client != null) {
            return client;
        }

        final DockerClientProviderStrategy strategy = getOrInitializeStrategy();

        client =
            new DockerClientDelegate() {
                @Getter
                final DockerClient dockerClient = strategy.getDockerClient();

                @Override
                public void close() {
                    throw new IllegalStateException("You should never close the global DockerClient!");
                }
            };
        log.info("Docker host IP address is {}", strategy.getDockerHostIpAddress());

        Info dockerInfo = strategy.getInfo();
        log.debug("Docker info: {}", dockerInfo.getRawValues());
        Version version = client.versionCmd().exec();
        log.debug("Docker version: {}", version.getRawValues());
        activeApiVersion = version.getApiVersion();

        String serverInfo =
            "Connected to docker: \n" +
            "  Server Version: " +
            dockerInfo.getServerVersion() +
            "\n" +
            "  API Version: " +
            activeApiVersion +
            "\n" +
            "  Operating System: " +
            dockerInfo.getOperatingSystem() +
            "\n" +
            "  Total Memory: " +
            dockerInfo.getMemTotal() /
            (1024 * 1024) +
            " MB";

        String[] labels = dockerInfo.getLabels();
        boolean hasLabels = labels != null && labels.length > 0;
        if (hasLabels) {
            String formattedLabels = Arrays
                .stream(labels)
                .map(label -> "    " + label)
                .collect(Collectors.joining("\n"));
            serverInfo += "\n  Labels: \n" + formattedLabels;
        }
        log.info(serverInfo);

        try {
            //noinspection deprecation
            ResourceReaper.instance().init();
        } catch (RuntimeException e) {
            cachedClientFailure = e;
            throw e;
        }

        boolean checksEnabled = !TestcontainersConfiguration.getInstance().isDisableChecks();
        if (checksEnabled) {
            log.debug("Checks are enabled");

            try {
                log.info("Checking the system...");
                checkDockerVersion(version.getVersion());
            } catch (RuntimeException e) {
                cachedClientFailure = e;
                throw e;
            }
        } else {
            log.debug("Checks are disabled");
        }

        return client;
    }

    private void checkDockerVersion(String dockerVersion) {
        boolean versionIsSufficient = new ComparableVersion(dockerVersion).compareTo(new ComparableVersion("1.6.0")) >=
        0;
        check("Docker server version should be at least 1.6.0", versionIsSufficient);
    }

    private void check(String message, boolean isSuccessful) {
        if (isSuccessful) {
            log.info("\u2714\ufe0e {}", message);
        } else {
            log.error("\u274c {}", message);
            throw new IllegalStateException("Check failed: " + message);
        }
    }

    private boolean checkMountableFile() {
        DockerClient dockerClient = client();

        MountableFile mountableFile = MountableFile.forClasspathResource(
            ResourceReaper.class.getName().replace(".", "/") + ".class"
        );

        Volume volume = new Volume("/dummy");
        try {
            return runInsideDocker(
                createContainerCmd -> {
                    createContainerCmd.withBinds(new Bind(mountableFile.getResolvedPath(), volume, AccessMode.ro));
                },
                (__, containerId) -> {
                    try (
                        InputStream stream = dockerClient
                            .copyArchiveFromContainerCmd(containerId, volume.getPath())
                            .exec()
                    ) {
                        stream.read();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            );
        } catch (Exception e) {
            log.debug("Failure while checking for mountable file support", e);
            return false;
        }
    }

    /**
     * Check whether the image is available locally and pull it otherwise
     *
     * @deprecated use {@link RemoteDockerImage}
     */
    @SneakyThrows
    @Deprecated
    public void checkAndPullImage(DockerClient client, String image) {
        try {
            client.inspectImageCmd(image).exec();
        } catch (NotFoundException notFoundException) {
            PullImageCmd pullImageCmd = client.pullImageCmd(image);
            try {
                pullImageCmd.exec(new TimeLimitedLoggedPullImageResultCallback(log)).awaitCompletion();
            } catch (DockerClientException e) {
                // Try to fallback to x86
                pullImageCmd
                    .withPlatform("linux/amd64")
                    .exec(new TimeLimitedLoggedPullImageResultCallback(log))
                    .awaitCompletion();
            }
        }
    }

    /**
     * @return the IP address of the host running Docker
     */
    public String dockerHostIpAddress() {
        return getOrInitializeStrategy().getDockerHostIpAddress();
    }

    public <T> T runInsideDocker(
        Consumer<CreateContainerCmd> createContainerCmdConsumer,
        BiFunction<DockerClient, String, T> block
    ) {
        return runInsideDocker(TINY_IMAGE, createContainerCmdConsumer, block);
    }

    <T> T runInsideDocker(
        DockerImageName imageName,
        Consumer<CreateContainerCmd> createContainerCmdConsumer,
        BiFunction<DockerClient, String, T> block
    ) {
        RemoteDockerImage dockerImage = new RemoteDockerImage(imageName);
        HashMap<String, String> labels = new HashMap<>(DEFAULT_LABELS);
        labels.putAll(ResourceReaper.instance().getLabels());
        CreateContainerCmd createContainerCmd = client.createContainerCmd(dockerImage.get()).withLabels(labels);
        createContainerCmdConsumer.accept(createContainerCmd);
        String id = createContainerCmd.exec().getId();

        try {
            client.startContainerCmd(id).exec();
            return block.apply(client, id);
        } finally {
            try {
                client.removeContainerCmd(id).withRemoveVolumes(true).withForce(true).exec();
            } catch (NotFoundException | InternalServerErrorException e) {
                log.debug("Swallowed exception while removing container", e);
            }
        }
    }

    /**
     * @return the docker API version of the daemon that we have connected to
     */
    public String getActiveApiVersion() {
        client();
        return activeApiVersion;
    }

    /**
     * @return the docker execution driver of the daemon that we have connected to
     */
    public String getActiveExecutionDriver() {
        return getInfo().getExecutionDriver();
    }

    /**
     * @param providerStrategyClass a class that extends {@link DockerMachineClientProviderStrategy}
     * @return whether or not the currently active strategy is of the provided type
     */
    public boolean isUsing(Class<? extends DockerClientProviderStrategy> providerStrategyClass) {
        return strategy != null && providerStrategyClass.isAssignableFrom(this.strategy.getClass());
    }

    @UnstableAPI
    public Info getInfo() {
        return getOrInitializeStrategy().getInfo();
    }
}
