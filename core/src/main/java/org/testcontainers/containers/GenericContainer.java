package org.testcontainers.containers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.UnstableAPI;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.core.CreateContainerCmdModifier;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.lifecycle.TestDescription;
import org.testcontainers.lifecycle.TestLifecycleAware;
import org.testcontainers.utility.Base58;
import org.testcontainers.utility.CommandLine;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.DockerLoggerFactory;
import org.testcontainers.utility.DockerMachineClient;
import org.testcontainers.utility.DynamicPollInterval;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.PathUtils;
import org.testcontainers.utility.ResourceReaper;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static org.awaitility.Awaitility.await;

/**
 * Base class for that allows a container to be launched and controlled.
 */
@Data
public class GenericContainer<SELF extends GenericContainer<SELF>>
    extends FailureDetectingExternalResource
    implements Container<SELF>, AutoCloseable, WaitStrategyTarget, Startable {

    public static final int CONTAINER_RUNNING_TIMEOUT_SEC = 30;

    public static final String INTERNAL_HOST_HOSTNAME = "host.testcontainers.internal";

    static final String HASH_LABEL = "org.testcontainers.hash";

    static final String COPIED_FILES_HASH_LABEL = "org.testcontainers.copied_files.hash";

    /*
     * Default settings
     */
    @NonNull
    private List<String> extraHosts = new ArrayList<>();

    @NonNull
    private RemoteDockerImage image;

    @NonNull
    private List<VolumesFrom> volumesFroms = new ArrayList<>();

    /**
     * @deprecated Links are deprecated (see <a href="https://github.com/testcontainers/testcontainers-java/issues/465">#465</a>). Please use {@link Network} features instead.
     */
    @NonNull
    @Deprecated
    private Map<String, LinkableContainer> linkedContainers = new HashMap<>();

    private StartupCheckStrategy startupCheckStrategy = new IsRunningStartupCheckStrategy();

    private int startupAttempts = 1;

    @Nullable
    private String workingDirectory = null;

    /**
     * The shared memory size to use when starting the container.
     * This value is in bytes.
     */
    @Nullable
    private Long shmSize;

    // Maintain order in which entries are added, as earlier target location may be a prefix of a later location.
    @Deprecated
    private Map<MountableFile, String> copyToFileContainerPathMap = new LinkedHashMap<>();

    // Maintain order in which entries are added, as earlier target location may be a prefix of a later location.
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.MODULE)
    @VisibleForTesting
    private Map<Transferable, String> copyToTransferableContainerPathMap = new LinkedHashMap<>();

    protected final Set<Startable> dependencies = new HashSet<>();

    /**
     * Unique instance of DockerClient for use by this container object.
     * We use {@link DockerClientFactory#lazyClient()} here to avoid eager client creation
     */
    @Setter(AccessLevel.NONE)
    protected DockerClient dockerClient = DockerClientFactory.lazyClient();

    /**
     * Set during container startup
     */
    @Setter(AccessLevel.NONE)
    @VisibleForTesting
    String containerId;

    @Setter(AccessLevel.NONE)
    private InspectContainerResponse containerInfo;

    static WaitStrategy DEFAULT_WAIT_STRATEGY = Wait.defaultWaitStrategy();

    /**
     * The approach to determine if the container is ready.
     */
    @NonNull
    protected WaitStrategy waitStrategy = DEFAULT_WAIT_STRATEGY;

    private List<Consumer<OutputFrame>> logConsumers = new ArrayList<>();

    private static final Set<String> AVAILABLE_IMAGE_NAME_CACHE = new HashSet<>();

    @Nullable
    private Map<String, String> tmpFsMapping;

    @Setter(AccessLevel.NONE)
    private boolean shouldBeReused = false;

    private boolean hostAccessible = false;

    private final Set<CreateContainerCmdModifier> createContainerCmdModifiers = loadCreateContainerCmdCustomizers();

    private ContainerDef containerDef;

    ContainerDef createContainerDef() {
        return new ContainerDef();
    }

    ContainerDef getContainerDef() {
        return this.containerDef;
    }

    private Set<CreateContainerCmdModifier> loadCreateContainerCmdCustomizers() {
        ServiceLoader<CreateContainerCmdModifier> containerCmdCustomizers = ServiceLoader.load(
            CreateContainerCmdModifier.class
        );
        Set<CreateContainerCmdModifier> loadedCustomizers = new LinkedHashSet<>();
        for (CreateContainerCmdModifier customizer : containerCmdCustomizers) {
            loadedCustomizers.add(customizer);
        }
        return loadedCustomizers;
    }

    public GenericContainer(@NonNull final DockerImageName dockerImageName) {
        this(new RemoteDockerImage(dockerImageName));
    }

    public GenericContainer(@NonNull final RemoteDockerImage image) {
        this.image = image;
        this.containerDef = createContainerDef();
        this.containerDef.addNetworkAlias("tc-" + Base58.randomString(8));
        this.containerDef.setImage(image);
    }

    /**
     * @deprecated use {@link #GenericContainer(DockerImageName)} instead
     */
    @Deprecated
    public GenericContainer() {
        this(TestcontainersConfiguration.getInstance().getTinyImage());
    }

    public GenericContainer(@NonNull final String dockerImageName) {
        this(new RemoteDockerImage(DockerImageName.parse(dockerImageName)));
    }

    public GenericContainer(@NonNull final Future<String> image) {
        this(new RemoteDockerImage(image));
    }

    GenericContainer(@NonNull final ContainerDef containerDef) {
        this.image = containerDef.getImage();
        this.containerDef = containerDef;
    }

    public void setImage(Future<String> image) {
        this.image = new RemoteDockerImage(image);
        this.containerDef.setImage(new RemoteDockerImage(image));
    }

    @Override
    public List<Integer> getExposedPorts() {
        List<Integer> exposedPorts = new ArrayList<>();
        for (ExposedPort exposedPort : this.containerDef.getExposedPorts()) {
            exposedPorts.add(exposedPort.getPort());
        }
        return exposedPorts;
    }

    @Override
    public void setExposedPorts(List<Integer> exposedPorts) {
        this.containerDef.exposedPorts.clear();
        for (Integer exposedPort : exposedPorts) {
            this.containerDef.addExposedTcpPort(exposedPort);
        }
    }

    /**
     * @see #dependsOn(Iterable)
     */
    public SELF dependsOn(Startable... startables) {
        Collections.addAll(dependencies, startables);
        return self();
    }

    /**
     * @see #dependsOn(Iterable)
     */
    public SELF dependsOn(List<? extends Startable> startables) {
        return this.dependsOn((Iterable<? extends Startable>) startables);
    }

    /**
     * Delays this container's creation and start until provided {@link Startable}s start first.
     * Note that the circular dependencies are not supported.
     *
     * @param startables a list of {@link Startable} to depend on
     * @see Startables#deepStart(Iterable)
     */
    public SELF dependsOn(Iterable<? extends Startable> startables) {
        startables.forEach(dependencies::add);
        return self();
    }

    public String getContainerId() {
        return containerId;
    }

    /**
     * Starts the container using docker, pulling an image if necessary.
     */
    @Override
    @SneakyThrows({ InterruptedException.class, ExecutionException.class })
    public void start() {
        if (containerId != null) {
            return;
        }
        Startables.deepStart(dependencies).get();
        // trigger LazyDockerClient's resolve so that we fail fast here and not in getDockerImageName()
        dockerClient.authConfig();
        doStart();
    }

    protected void doStart() {
        Stopwatch stopWatch = Stopwatch.createStarted();
        try {
            if (this.waitStrategy != DEFAULT_WAIT_STRATEGY) {
                this.containerDef.setWaitStrategy(this.waitStrategy);
            }

            configure();

            logger().debug("Starting container: {}", getDockerImageName());

            AtomicInteger attempt = new AtomicInteger(0);
            Unreliables.retryUntilSuccess(
                startupAttempts,
                () -> {
                    logger()
                        .debug(
                            "Trying to start container: {} (attempt {}/{})",
                            getDockerImageName(),
                            attempt.incrementAndGet(),
                            startupAttempts
                        );
                    tryStart();
                    return true;
                }
            );
        } catch (Exception e) {
            throw new ContainerLaunchException(String.format("Container startup failed for image %s (elapsed %ss)", getDockerImageName(), stopWatch.elapsed(TimeUnit.SECONDS)), e);
        }
    }

    @UnstableAPI
    @SneakyThrows
    protected boolean canBeReused() {
        for (Class<?> type = getClass(); type != GenericContainer.class; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod("containerIsCreated", String.class);
                if (method.getDeclaringClass() != GenericContainer.class) {
                    logger().warn("{} can't be reused because it overrides {}", getClass(), method.getName());
                    return false;
                }
            } catch (NoSuchMethodException | NoClassDefFoundError e) {
                // ignore
            }
        }

        return true;
    }

    private void tryStart() {
        try {
            String dockerImageName = getDockerImageName();
            logger().debug("Starting container: {}", dockerImageName);

            Instant startedAt = Instant.now();
            logger().info("Creating container for image: {}", dockerImageName);
            CreateContainerCmd createCommand = dockerClient.createContainerCmd(dockerImageName);
            applyConfiguration(createCommand);

            createCommand.getLabels().putAll(DockerClientFactory.DEFAULT_LABELS);

            boolean reused = false;
            final boolean reusable;
            if (shouldBeReused) {
                if (!canBeReused()) {
                    throw new IllegalStateException("This container does not support reuse");
                }

                if (TestcontainersConfiguration.getInstance().environmentSupportsReuse()) {
                    createCommand
                        .getLabels()
                        .put(COPIED_FILES_HASH_LABEL, Long.toHexString(hashCopiedFiles().getValue()));

                    String hash = hash(createCommand);

                    containerId = findContainerForReuse(hash).orElse(null);

                    if (containerId != null) {
                        logger().info("Reusing container with ID: {} and hash: {}", containerId, hash);
                        reused = true;
                    } else {
                        logger().debug("Can't find a reusable running container with hash: {}", hash);

                        createCommand.getLabels().put(HASH_LABEL, hash);
                    }
                    reusable = true;
                } else {
                    logger()
                        .warn(
                            "" +
                            "Reuse was requested but the environment does not support the reuse of containers\n" +
                            "To enable reuse of containers, you must set 'testcontainers.reuse.enable=true' in a file located at {}",
                            Paths.get(System.getProperty("user.home"), ".testcontainers.properties")
                        );
                    reusable = false;
                }
            } else {
                reusable = false;
            }

            if (!reusable) {
                //noinspection deprecation
                createCommand = ResourceReaper.instance().register(this, createCommand);
            }

            if (!reused) {
                containerId = createCommand.exec().getId();

                // TODO use single "copy" invocation (and calculate an hash of the resulting tar archive)
                copyToFileContainerPathMap.forEach(this::copyFileToContainer);

                copyToTransferableContainerPathMap.forEach(this::copyFileToContainer);
            }

            connectToPortForwardingNetwork(createCommand.getNetworkMode());

            if (!reused) {
                containerIsCreated(containerId);

                logger().info("Container {} is starting: {}", dockerImageName, containerId);
                dockerClient.startContainerCmd(containerId).exec();
            } else {
                logger().info("Reusing existing container ({}) and not creating a new one", containerId);
            }

            // For all registered output consumers, start following as close to container startup as possible
            this.logConsumers.forEach(this::followOutput);

            // Wait until inspect container returns the mapped ports
            containerInfo =
                await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(DynamicPollInterval.ofMillis(50))
                    .pollInSameThread()
                    .until(
                        () -> dockerClient.inspectContainerCmd(containerId).exec(),
                        inspectContainerResponse -> {
                            Set<ExposedPort> exposedAndMappedPorts = inspectContainerResponse
                                .getNetworkSettings()
                                .getPorts()
                                .getBindings()
                                .entrySet()
                                .stream()
                                .filter(it -> Objects.nonNull(it.getValue())) // filter out exposed but not yet mapped
                                .map(Entry::getKey)
                                .collect(Collectors.toSet());

                            return exposedAndMappedPorts.containsAll(this.containerDef.getExposedPorts());
                        }
                    );

            String emulationWarning = checkForEmulation();
            if (emulationWarning != null) {
                logger().warn(emulationWarning);
            }

            // Tell subclasses that we're starting
            containerIsStarting(containerInfo, reused);

            // Wait until the container has reached the desired running state
            if (!this.startupCheckStrategy.waitUntilStartupSuccessful(this)) {
                // Bail out, don't wait for the port to start listening.
                // (Exception thrown here will be caught below and wrapped)
                throw new IllegalStateException("Container did not start correctly.");
            }

            // Wait until the process within the container has become ready for use (e.g. listening on network, log message emitted, etc).
            try {
                waitUntilContainerStarted();
            } catch (Exception e) {
                logger().debug("Wait strategy threw an exception", e);
                InspectContainerResponse inspectContainerResponse = null;
                try {
                    inspectContainerResponse = dockerClient.inspectContainerCmd(containerId).exec();
                } catch (NotFoundException notFoundException) {
                    logger().debug("Container {} not found", containerId, notFoundException);
                }

                if (inspectContainerResponse == null) {
                    throw new IllegalStateException("Wait strategy failed. Container is removed", e);
                }

                InspectContainerResponse.ContainerState state = inspectContainerResponse.getState();
                if (Boolean.TRUE.equals(state.getDead())) {
                    throw new IllegalStateException("Wait strategy failed. Container is dead", e);
                }

                if (Boolean.TRUE.equals(state.getOOMKilled())) {
                    throw new IllegalStateException(
                        "Wait strategy failed. Container crashed with out-of-memory (OOMKilled)",
                        e
                    );
                }

                String error = state.getError();
                if (!StringUtils.isBlank(error)) {
                    throw new IllegalStateException("Wait strategy failed. Container crashed: " + error, e);
                }

                if (!Boolean.TRUE.equals(state.getRunning())) {
                    throw new IllegalStateException(
                        "Wait strategy failed. Container exited with code " + state.getExitCode(),
                        e
                    );
                }

                throw e;
            }

            logger().info("Container {} started in {}", dockerImageName, Duration.between(startedAt, Instant.now()));
            containerIsStarted(containerInfo, reused);
        } catch (Exception e) {
            if (e instanceof UndeclaredThrowableException && e.getCause() instanceof Exception) {
                e = (Exception) e.getCause();
            }
            if (e instanceof InvocationTargetException && e.getCause() instanceof Exception) {
                e = (Exception) e.getCause();
            }
            logger().error("Could not start container", e);

            if (containerId != null) {
                // Log output if startup failed, either due to a container failure or exception (including timeout)
                final String containerLogs = getLogs();

                if (containerLogs.length() > 0) {
                    logger().error("Log output from the failed container:\n{}", getLogs());
                } else {
                    logger().error("There are no stdout/stderr logs available for the failed container");
                }
                stop();
            }

            throw new ContainerLaunchException("Could not create/start container", e);
        }
    }

    @VisibleForTesting
    Checksum hashCopiedFiles() {
        Checksum checksum = new Adler32();
        Stream
            .of(copyToFileContainerPathMap, copyToTransferableContainerPathMap)
            .flatMap(it -> it.entrySet().stream())
            .sorted(Entry.comparingByValue())
            .forEach(entry -> {
                byte[] pathBytes = entry.getValue().getBytes();
                // Add path to the hash
                checksum.update(pathBytes, 0, pathBytes.length);

                entry.getKey().updateChecksum(checksum);
            });
        return checksum;
    }

    @UnstableAPI
    @SneakyThrows(JsonProcessingException.class)
    final String hash(CreateContainerCmd createCommand) {
        DefaultDockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        byte[] commandJson = dockerClientConfig
            .getObjectMapper()
            .copy()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsBytes(createCommand);

        // TODO add Testcontainers' version to the hash
        return Hashing.sha1().hashBytes(commandJson).toString();
    }

    @VisibleForTesting
    Optional<String> findContainerForReuse(String hash) {
        // TODO locking
        return dockerClient
            .listContainersCmd()
            .withLabelFilter(ImmutableMap.of(HASH_LABEL, hash))
            .withLimit(1)
            .withStatusFilter(Arrays.asList("running"))
            .exec()
            .stream()
            .findAny()
            .map(it -> it.getId());
    }

    /**
     * Set any custom settings for the create command such as shared memory size.
     */
    private HostConfig buildHostConfig(HostConfig config) {
        if (shmSize != null) {
            config.withShmSize(shmSize);
        }
        if (tmpFsMapping != null) {
            config.withTmpFs(tmpFsMapping);
        }
        return config;
    }

    private void connectToPortForwardingNetwork(String networkMode) {
        PortForwardingContainer.INSTANCE
            .getNetwork()
            .map(ContainerNetwork::getNetworkID)
            .ifPresent(networkId -> {
                if (!Arrays.asList(networkId, "none", "host").contains(networkMode)) {
                    com.github.dockerjava.api.model.Network network =
                        this.dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
                    if (!network.getContainers().containsKey(this.containerId)) {
                        this.dockerClient.connectToNetworkCmd()
                            .withContainerId(this.containerId)
                            .withNetworkId(networkId)
                            .exec();
                    }
                }
            });
    }

    /**
     * Kill and remove the container.
     */
    @Override
    public void stop() {
        if (containerId == null) {
            return;
        }

        try {
            String imageName;

            try {
                imageName = getDockerImageName();
            } catch (Exception e) {
                imageName = "<unknown>";
            }

            containerIsStopping(containerInfo);
            ResourceReaper.instance().stopAndRemoveContainer(containerId, imageName);
            containerIsStopped(containerInfo);
        } finally {
            containerId = null;
            containerInfo = null;
        }
    }

    /**
     * Provide a logger that references the docker image name.
     *
     * @return a logger that references the docker image name
     */
    protected Logger logger() {
        return DockerLoggerFactory.getLogger(this.getDockerImageName());
    }

    /**
     * Creates a directory on the local filesystem which will be mounted as a volume for the container.
     *
     * @param temporary is the volume directory temporary? If true, the directory will be deleted on JVM shutdown.
     * @return path to the volume directory
     */
    @Deprecated
    protected Path createVolumeDirectory(boolean temporary) {
        Path directory = new File(".tmp-volume-" + UUID.randomUUID()).toPath();
        PathUtils.mkdirp(directory);

        if (temporary) {
            Runtime
                .getRuntime()
                .addShutdownHook(
                    new Thread(
                        DockerClientFactory.TESTCONTAINERS_THREAD_GROUP,
                        () -> {
                            PathUtils.recursiveDeleteDir(directory);
                        }
                    )
                );
        }

        return directory;
    }

    protected void configure() {}

    @SuppressWarnings({ "EmptyMethod", "UnusedParameters" })
    protected void containerIsCreated(String containerId) {}

    @SuppressWarnings({ "EmptyMethod", "UnusedParameters" })
    protected void containerIsStarting(InspectContainerResponse containerInfo) {}

    @SuppressWarnings({ "EmptyMethod", "UnusedParameters" })
    @UnstableAPI
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        containerIsStarting(containerInfo);
    }

    @SuppressWarnings({ "EmptyMethod", "UnusedParameters" })
    protected void containerIsStarted(InspectContainerResponse containerInfo) {}

    @SuppressWarnings({ "EmptyMethod", "UnusedParameters" })
    @UnstableAPI
    protected void containerIsStarted(InspectContainerResponse containerInfo, boolean reused) {
        containerIsStarted(containerInfo);
    }

    /**
     * A hook that is executed before the container is stopped with {@link #stop()}.
     * Warning! This hook won't be executed if the container is terminated during
     * the JVM's shutdown hook or by Ryuk.
     */
    @SuppressWarnings({ "EmptyMethod", "UnusedParameters" })
    protected void containerIsStopping(InspectContainerResponse containerInfo) {}

    /**
     * A hook that is executed after the container is stopped with {@link #stop()}.
     * Warning! This hook won't be executed if the container is terminated during
     * the JVM's shutdown hook or by Ryuk.
     */
    @SuppressWarnings({ "EmptyMethod", "UnusedParameters" })
    protected void containerIsStopped(InspectContainerResponse containerInfo) {}

    /**
     * @return the port on which to check if the container is ready
     * @deprecated see {@link GenericContainer#getLivenessCheckPorts()} for replacement
     */
    @Deprecated
    protected Integer getLivenessCheckPort() {
        // legacy implementation for backwards compatibility
        Iterator<ExposedPort> exposedPortsIterator = this.containerDef.getExposedPorts().iterator();
        if (exposedPortsIterator.hasNext()) {
            return getMappedPort(exposedPortsIterator.next().getPort());
        } else if (!this.containerDef.getPortBindings().isEmpty()) {
            return Integer.valueOf(
                this.containerDef.getPortBindings().iterator().next().getBinding().getHostPortSpec()
            );
        } else {
            return null;
        }
    }

    /**
     * @return the ports on which to check if the container is ready
     * @deprecated use {@link #getLivenessCheckPortNumbers()} instead
     */
    @NotNull
    @NonNull
    @Deprecated
    protected Set<Integer> getLivenessCheckPorts() {
        final Set<Integer> result = WaitStrategyTarget.super.getLivenessCheckPortNumbers();

        // for backwards compatibility
        if (this.getLivenessCheckPort() != null) {
            result.add(this.getLivenessCheckPort());
        }

        return result;
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return this.getLivenessCheckPorts();
    }

    private void applyConfiguration(CreateContainerCmd createCommand) {
        this.containerDef.applyTo(createCommand);
        buildHostConfig(createCommand.getHostConfig());

        VolumesFrom[] volumesFromsArray = volumesFroms.stream().toArray(VolumesFrom[]::new);
        createCommand.withVolumesFrom(volumesFromsArray);

        Set<Link> allLinks = new HashSet<>();
        Set<String> allLinkedContainerNetworks = new HashSet<>();
        for (Entry<String, LinkableContainer> linkEntries : linkedContainers.entrySet()) {
            String alias = linkEntries.getKey();
            LinkableContainer linkableContainer = linkEntries.getValue();

            Set<Link> links = findLinksFromThisContainer(alias, linkableContainer);
            allLinks.addAll(links);

            if (allLinks.size() == 0) {
                throw new ContainerLaunchException(
                    "Aborting attempt to link to container " +
                    linkableContainer.getContainerName() +
                    " as it is not running"
                );
            }

            Set<String> linkedContainerNetworks = findAllNetworksForLinkedContainers(linkableContainer);
            allLinkedContainerNetworks.addAll(linkedContainerNetworks);
        }

        createCommand.withLinks(allLinks.toArray(new Link[allLinks.size()]));

        allLinkedContainerNetworks.remove("bridge");
        if (allLinkedContainerNetworks.size() > 1) {
            logger()
                .warn(
                    "Container needs to be on more than one custom network to link to other " +
                    "containers - this is not currently supported. Required networks are: {}",
                    allLinkedContainerNetworks
                );
        }

        Optional<String> networkForLinks = allLinkedContainerNetworks.stream().findFirst();
        if (networkForLinks.isPresent()) {
            logger().debug("Associating container with network: {}", networkForLinks.get());
            createCommand.withNetworkMode(networkForLinks.get());
        }

        if (hostAccessible) {
            PortForwardingContainer.INSTANCE.start();
        }
        PortForwardingContainer.INSTANCE
            .getNetwork()
            .ifPresent(it -> {
                withExtraHost(INTERNAL_HOST_HOSTNAME, it.getIpAddress());
            });

        String[] extraHostsArray = extraHosts.stream().distinct().toArray(String[]::new);
        createCommand.withExtraHosts(extraHostsArray);

        if (workingDirectory != null) {
            createCommand.withWorkingDir(workingDirectory);
        }

        for (CreateContainerCmdModifier createContainerCmdModifier : this.createContainerCmdModifiers) {
            createCommand = createContainerCmdModifier.modify(createCommand);
        }
    }

    private Set<Link> findLinksFromThisContainer(String alias, LinkableContainer linkableContainer) {
        return dockerClient
            .listContainersCmd()
            .withStatusFilter(Arrays.asList("running"))
            .exec()
            .stream()
            .flatMap(container -> Stream.of(container.getNames()))
            .filter(name -> name.endsWith(linkableContainer.getContainerName()))
            .map(name -> new Link(name, alias))
            .collect(Collectors.toSet());
    }

    private Set<String> findAllNetworksForLinkedContainers(LinkableContainer linkableContainer) {
        return dockerClient
            .listContainersCmd()
            .exec()
            .stream()
            .filter(container -> container.getNames()[0].endsWith(linkableContainer.getContainerName()))
            .filter(container -> {
                return container.getNetworkSettings() != null && container.getNetworkSettings().getNetworks() != null;
            })
            .flatMap(container -> container.getNetworkSettings().getNetworks().keySet().stream())
            .distinct()
            .collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF waitingFor(@NonNull WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
        return self();
    }

    /**
     * The {@link WaitStrategy} to use to determine if the container is ready.
     * Defaults to {@link Wait#defaultWaitStrategy()}.
     *
     * @return the {@link WaitStrategy} to use
     */
    protected WaitStrategy getWaitStrategy() {
        return this.waitStrategy == DEFAULT_WAIT_STRATEGY ? this.containerDef.getWaitStrategy() : this.waitStrategy;
    }

    @Override
    public void setWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    /**
     * Wait until the container has started. The default implementation simply
     * waits for a port to start listening; other implementations are available
     * as implementations of {@link WaitStrategy}
     *
     * @see #waitingFor(WaitStrategy)
     */
    protected void waitUntilContainerStarted() {
        WaitStrategy waitStrategy = getWaitStrategy();
        if (waitStrategy != null) {
            waitStrategy.waitUntilReady(this);
        }
    }

    private String checkForEmulation() {
        try {
            DockerClient dockerClient = DockerClientFactory.instance().client();
            String imageId = getContainerInfo().getImageId();
            String imageArch = dockerClient.inspectImageCmd(imageId).exec().getArch();
            String serverArch = dockerClient.versionCmd().exec().getArch();

            if (!serverArch.equals(imageArch)) {
                return (
                    "The architecture '" +
                    imageArch +
                    "' for image '" +
                    getDockerImageName() +
                    "' (ID " +
                    imageId +
                    ") does not match the Docker server architecture '" +
                    serverArch +
                    "'. This will cause the container to execute much more slowly due to emulation and may lead to timeout failures."
                );
            }
        } catch (Exception archCheckException) {
            // ignore any exceptions since this is just used for a log message
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommand(@NonNull String command) {
        this.containerDef.setCommand(command.split(" "));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommand(@NonNull String... commandParts) {
        this.containerDef.setCommand(commandParts);
    }

    @Override
    public Map<String, String> getEnvMap() {
        return this.containerDef.envVars;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getEnv() {
        return this.containerDef.getEnvVars()
            .entrySet()
            .stream()
            .map(it -> it.getKey() + "=" + it.getValue())
            .collect(Collectors.toList());
    }

    @Override
    public void setEnv(List<String> env) {
        this.containerDef.setEnvVars(
                env.stream().map(it -> it.split("=")).collect(Collectors.toMap(it -> it[0], it -> it[1]))
            );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEnv(String key, String value) {
        this.containerDef.addEnvVar(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFileSystemBind(
        final String hostPath,
        final String containerPath,
        final BindMode mode,
        final SelinuxContext selinuxContext
    ) {
        if (SystemUtils.IS_OS_WINDOWS && hostPath.startsWith("/")) {
            // e.g. Docker socket mount
            this.containerDef.addBinds(
                    new Bind(hostPath, new Volume(containerPath), mode.accessMode, selinuxContext.selContext)
                );
        } else {
            final MountableFile mountableFile = MountableFile.forHostPath(hostPath);
            this.containerDef.addBinds(
                    new Bind(
                        mountableFile.getResolvedPath(),
                        new Volume(containerPath),
                        mode.accessMode,
                        selinuxContext.selContext
                    )
                );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withFileSystemBind(String hostPath, String containerPath, BindMode mode) {
        addFileSystemBind(hostPath, containerPath, mode);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withVolumesFrom(Container container, BindMode mode) {
        addVolumesFrom(container, mode);
        return self();
    }

    private void addVolumesFrom(Container container, BindMode mode) {
        volumesFroms.add(new VolumesFrom(container.getContainerName(), mode.accessMode));
    }

    /**
     * @deprecated Links are deprecated (see <a href="https://github.com/testcontainers/testcontainers-java/issues/465">#465</a>). Please use {@link Network} features instead.
     */
    @Deprecated
    @Override
    public void addLink(LinkableContainer otherContainer, String alias) {
        this.linkedContainers.put(alias, otherContainer);
    }

    @Override
    public void addExposedPort(Integer port) {
        this.containerDef.addExposedTcpPort(port);
    }

    @Override
    public void addExposedPorts(int... ports) {
        this.containerDef.addExposedTcpPorts(ports);
    }

    private TestDescription toDescription(Description description) {
        return new TestDescription() {
            @Override
            public String getTestId() {
                return description.getDisplayName();
            }

            @Override
            public String getFilesystemFriendlyName() {
                return description.getClassName() + "-" + description.getMethodName();
            }
        };
    }

    @Override
    @Deprecated
    public Statement apply(Statement base, Description description) {
        return super.apply(base, description);
    }

    @Override
    @Deprecated
    protected void starting(Description description) {
        if (this instanceof TestLifecycleAware) {
            ((TestLifecycleAware) this).beforeTest(toDescription(description));
        }
        this.start();
    }

    @Override
    @Deprecated
    protected void succeeded(Description description) {
        if (this instanceof TestLifecycleAware) {
            ((TestLifecycleAware) this).afterTest(toDescription(description), Optional.empty());
        }
    }

    @Override
    @Deprecated
    protected void failed(Throwable e, Description description) {
        if (this instanceof TestLifecycleAware) {
            ((TestLifecycleAware) this).afterTest(toDescription(description), Optional.of(e));
        }
    }

    @Override
    @Deprecated
    protected void finished(Description description) {
        this.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withExposedPorts(Integer... ports) {
        this.setExposedPorts(Lists.newArrayList(ports));
        return self();
    }

    /**
     * Add a TCP container port that should be bound to a fixed port on the docker host.
     * <p>
     * Note that this method is protected scope to discourage use, as clashes or instability are more likely when
     * using fixed port mappings. If you need to use this method from a test, please use {@link FixedHostPortGenericContainer}
     * instead of GenericContainer.
     *
     * @param hostPort
     * @param containerPort
     */
    protected void addFixedExposedPort(int hostPort, int containerPort) {
        addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP);
    }

    /**
     * Add a container port that should be bound to a fixed port on the docker host.
     * <p>
     * Note that this method is protected scope to discourage use, as clashes or instability are more likely when
     * using fixed port mappings. If you need to use this method from a test, please use {@link FixedHostPortGenericContainer}
     * instead of GenericContainer.
     *
     * @param hostPort
     * @param containerPort
     * @param protocol
     */
    protected void addFixedExposedPort(int hostPort, int containerPort, InternetProtocol protocol) {
        ExposedPort exposedPort = new ExposedPort(
            containerPort,
            com.github.dockerjava.api.model.InternetProtocol.parse(protocol.name())
        );
        PortBinding portBinding = new PortBinding(Ports.Binding.bindPort(hostPort), exposedPort);
        this.containerDef.addPortBindings(portBinding);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withEnv(String key, String value) {
        this.addEnv(key, value);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withEnv(Map<String, String> env) {
        env.forEach(this.containerDef::addEnvVar);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withLabel(String key, String value) {
        if (key.startsWith("org.testcontainers")) {
            throw new IllegalArgumentException("The org.testcontainers namespace is reserved for interal use");
        }
        this.containerDef.addLabel(key, value);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withLabels(Map<String, String> labels) {
        labels.forEach(this::withLabel);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withCommand(String cmd) {
        this.setCommand(cmd);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withCommand(String... commandParts) {
        this.setCommand(commandParts);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withExtraHost(String hostname, String ipAddress) {
        this.extraHosts.add(String.format("%s:%s", hostname, ipAddress));
        return self();
    }

    @Override
    public SELF withNetworkMode(String networkMode) {
        this.containerDef.setNetworkMode(networkMode);
        return self();
    }

    @Override
    public SELF withNetwork(Network network) {
        this.containerDef.setNetwork(network);
        return self();
    }

    @Override
    public SELF withNetworkAliases(String... aliases) {
        this.containerDef.addNetworkAliases(aliases);
        return self();
    }

    @Override
    public SELF withImagePullPolicy(ImagePullPolicy imagePullPolicy) {
        this.image = this.image.withImagePullPolicy(imagePullPolicy);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withClasspathResourceMapping(
        final String resourcePath,
        final String containerPath,
        final BindMode mode
    ) {
        return withClasspathResourceMapping(resourcePath, containerPath, mode, SelinuxContext.SHARED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withClasspathResourceMapping(
        final String resourcePath,
        final String containerPath,
        final BindMode mode,
        final SelinuxContext selinuxContext
    ) {
        final MountableFile mountableFile = MountableFile.forClasspathResource(resourcePath);

        if (mode == BindMode.READ_WRITE) {
            addFileSystemBind(mountableFile.getResolvedPath(), containerPath, mode, selinuxContext);
        } else {
            withCopyFileToContainer(mountableFile, containerPath);
        }

        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withStartupTimeout(Duration startupTimeout) {
        getWaitStrategy().withStartupTimeout(startupTimeout);
        return self();
    }

    @Override
    public SELF withPrivilegedMode(boolean mode) {
        this.containerDef.setPrivilegedMode(mode);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withMinimumRunningDuration(Duration minimumRunningDuration) {
        this.startupCheckStrategy = new MinimumDurationRunningStartupCheckStrategy(minimumRunningDuration);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withStartupCheckStrategy(StartupCheckStrategy strategy) {
        this.startupCheckStrategy = strategy;
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withWorkingDirectory(String workDir) {
        this.setWorkingDirectory(workDir);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withCopyFileToContainer(MountableFile mountableFile, String containerPath) {
        if (copyToFileContainerPathMap.containsKey(mountableFile)) {
            throw new IllegalStateException("Path already configured for copy: " + mountableFile);
        }
        copyToFileContainerPathMap.put(mountableFile, containerPath);
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withCopyToContainer(Transferable transferable, String containerPath) {
        copyToTransferableContainerPathMap.put(transferable, containerPath);
        return self();
    }

    /**
     * Get the IP address that this container may be reached on (may not be the local machine).
     *
     * @return an IP address
     * @deprecated please use getContainerIpAddress() instead
     */
    @Deprecated
    public String getIpAddress() {
        return getHost();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDockerImageName(@NonNull String dockerImageName) {
        this.image = new RemoteDockerImage(dockerImageName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String getDockerImageName() {
        try {
            return image.get();
        } catch (Exception e) {
            throw new ContainerFetchException("Can't get Docker image: " + image, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public String getTestHostIpAddress() {
        if (DockerMachineClient.instance().isInstalled()) {
            try {
                Optional<String> defaultMachine = DockerMachineClient.instance().getDefaultMachine();
                if (!defaultMachine.isPresent()) {
                    throw new IllegalStateException("Could not find a default docker-machine instance");
                }

                String sshConnectionString = CommandLine
                    .runShellCommand("docker-machine", "ssh", defaultMachine.get(), "echo $SSH_CONNECTION")
                    .trim();
                if (Strings.isNullOrEmpty(sshConnectionString)) {
                    throw new IllegalStateException(
                        "Could not obtain SSH_CONNECTION environment variable for docker machine " +
                        defaultMachine.get()
                    );
                }

                String[] sshConnectionParts = sshConnectionString.split("\\s");
                if (sshConnectionParts.length != 4) {
                    throw new IllegalStateException(
                        "Unexpected pattern for SSH_CONNECTION for docker machine - expected 'IP PORT IP PORT' pattern but found '" +
                        sshConnectionString +
                        "'"
                    );
                }

                return sshConnectionParts[0];
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new UnsupportedOperationException(
                "getTestHostIpAddress() is only implemented for docker-machine right now"
            );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SELF withLogConsumer(Consumer<OutputFrame> consumer) {
        this.logConsumers.add(consumer);

        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public void copyFileFromContainer(String containerPath, String destinationPath) {
        Container.super.copyFileFromContainer(containerPath, destinationPath);
    }

    /**
     * Allow container startup to be attempted more than once if an error occurs. To be used if containers are
     * 'flaky' but this flakiness is not something that should affect test outcomes.
     *
     * @param attempts number of attempts
     */
    public SELF withStartupAttempts(int attempts) {
        this.startupAttempts = attempts;
        return self();
    }

    /**
     * Allow low level modifications of {@link CreateContainerCmd} after it was pre-configured in {@link #tryStart()}.
     * Invocation happens eagerly on a moment when container is created.
     * Warning: this does expose the underlying docker-java API so might change outside of our control.
     *
     * @param modifier {@link Consumer} of {@link CreateContainerCmd}.
     * @return this
     */
    public SELF withCreateContainerCmdModifier(Consumer<CreateContainerCmd> modifier) {
        this.createContainerCmdModifiers.add(cmd -> {
                modifier.accept(cmd);
                return cmd;
            });
        return self();
    }

    /**
     * Size of /dev/shm
     *
     * @param bytes The number of bytes to assign the shared memory. If null, it will apply the Docker default which is 64 MB.
     * @return this
     */
    public SELF withSharedMemorySize(Long bytes) {
        this.shmSize = bytes;
        return self();
    }

    /**
     * First class support for configuring tmpfs
     *
     * @param mapping path and params of tmpfs/mount flag for container
     * @return this
     */
    public SELF withTmpFs(Map<String, String> mapping) {
        this.tmpFsMapping = mapping;
        return self();
    }

    @UnstableAPI
    public SELF withReuse(boolean reusable) {
        this.shouldBeReused = reusable;
        return self();
    }

    /**
     * Forces access to the tests host machine.
     * Use this method if you need to call {@link org.testcontainers.Testcontainers#exposeHostPorts(int...)}
     * after you start this container.
     *
     * @return this
     */
    public SELF withAccessToHost(boolean value) {
        this.hostAccessible = value;
        return self();
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String getContainerName() {
        return getContainerInfo().getName();
    }

    public Network getNetwork() {
        return this.containerDef.getNetwork();
    }

    @Override
    public List<Bind> getBinds() {
        return this.containerDef.binds;
    }

    @Override
    public void setBinds(List<Bind> binds) {
        this.containerDef.setBinds(binds);
    }

    @Override
    public String[] getCommandParts() {
        return this.containerDef.getCommand();
    }

    @Override
    public void setCommandParts(String[] commandParts) {
        this.containerDef.setCommand(commandParts);
    }

    public List<String> getNetworkAliases() {
        return new ArrayList<>(this.containerDef.getNetworkAliases());
    }

    public void setNetworkAliases(List<String> aliases) {
        this.containerDef.setNetworkAliases(new HashSet<>(aliases));
    }

    @Override
    public List<String> getPortBindings() {
        return this.containerDef.portBindings.stream()
            .map(it -> String.format("%s:%s", it.getBinding(), it.getExposedPort()))
            .collect(Collectors.toList());
    }

    @Override
    public void setPortBindings(List<String> portBindings) {
        this.containerDef.setPortBindings(portBindings.stream().map(PortBinding::parse).collect(Collectors.toSet()));
    }

    public void setPrivilegedMode(boolean mode) {
        this.containerDef.setPrivilegedMode(mode);
    }

    public boolean isPrivilegedMode() {
        return this.containerDef.isPrivilegedMode();
    }

    public Map<String, String> getLabels() {
        return this.containerDef.labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.containerDef.setLabels(labels);
    }

    public String getNetworkMode() {
        return this.containerDef.getNetworkMode();
    }

    public void setNetworkMode(String networkMode) {
        this.containerDef.setNetworkMode(networkMode);
    }

    public void setNetwork(Network network) {
        this.containerDef.setNetwork(network);
    }
}
