package org.testcontainers.containers;

import com.github.dockerjava.api.model.Bind;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.LogUtils;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Container<SELF extends Container<SELF>> extends LinkableContainer, ContainerState {
    /**
     * @return a reference to this container instance, cast to the expected generic type.
     */
    @SuppressWarnings("unchecked")
    default SELF self() {
        return (SELF) this;
    }

    /**
     * Class to hold results from a "docker exec" command
     */
    @Value
    @AllArgsConstructor(access = AccessLevel.MODULE)
    class ExecResult {

        int exitCode;

        String stdout;

        String stderr;
    }

    /**
     * Set the command that should be run in the container. Consider using {@link #withCommand(String)}
     * for building a container in a fluent style.
     *
     * @param command a command in single string format (will automatically be split on spaces)
     */
    void setCommand(@NonNull String command);

    /**
     * Set the command that should be run in the container. Consider using {@link #withCommand(String...)}
     * for building a container in a fluent style.
     *
     * @param commandParts a command as an array of string parts
     */
    void setCommand(@NonNull String... commandParts);

    /**
     * Add an environment variable to be passed to the container. Consider using {@link #withEnv(String, String)}
     * for building a container in a fluent style.
     *
     * @param key   environment variable key
     * @param value environment variable value
     */
    void addEnv(String key, String value);

    /**
     * Adds a file system binding. Consider using {@link #withFileSystemBind(String, String, BindMode)}
     * for building a container in a fluent style.
     *
     * @param hostPath      the file system path on the host
     * @param containerPath the file system path inside the container
     * @param mode          the bind mode
     * @deprecated use {@link GenericContainer#withCopyToContainer(Transferable, String)}
     */
    @Deprecated
    default void addFileSystemBind(final String hostPath, final String containerPath, final BindMode mode) {
        addFileSystemBind(hostPath, containerPath, mode, SelinuxContext.SHARED);
    }

    /**
     * Adds a file system binding. Consider using {@link #withFileSystemBind(String, String, BindMode)}
     * for building a container in a fluent style.
     *
     * @param hostPath      the file system path on the host
     * @param containerPath the file system path inside the container
     * @param mode          the bind mode
     * @param selinuxContext selinux context argument to use for this file
     * @deprecated use {@link GenericContainer#withCopyToContainer(Transferable, String)}
     */
    @Deprecated
    void addFileSystemBind(String hostPath, String containerPath, BindMode mode, SelinuxContext selinuxContext);

    /**
     * Add a link to another container.
     *
     * @param otherContainer the other container object to link to
     * @param alias the alias (for the other container) that this container should be able to use
     * @deprecated Links are deprecated (see <a href="https://github.com/testcontainers/testcontainers-java/issues/465">#465</a>). Please use {@link Network} features instead.
     */
    @Deprecated
    void addLink(LinkableContainer otherContainer, String alias);

    /**
     * Add an exposed port. Consider using {@link #withExposedPorts(Integer...)}
     * for building a container in a fluent style.
     *
     * @param port a TCP port
     */
    void addExposedPort(Integer port);

    /**
     * Add exposed ports. Consider using {@link #withExposedPorts(Integer...)}
     * for building a container in a fluent style.
     *
     * @param ports an array of TCP ports
     */
    void addExposedPorts(int... ports);

    /**
     * Specify the {@link WaitStrategy} to use to determine if the container is ready.
     *
     * @see org.testcontainers.containers.wait.strategy.Wait#defaultWaitStrategy()
     * @param waitStrategy the WaitStrategy to use
     * @return this
     */
    SELF waitingFor(@NonNull WaitStrategy waitStrategy);

    /**
     * Adds a file system binding.
     *
     * @param hostPath the file system path on the host
     * @param containerPath the file system path inside the container
     * @return this
     * @deprecated use {@link GenericContainer#withCopyToContainer(Transferable, String)}
     */
    @Deprecated
    default SELF withFileSystemBind(String hostPath, String containerPath) {
        return withFileSystemBind(hostPath, containerPath, BindMode.READ_WRITE);
    }

    /**
     * Adds a file system binding.
     *
     * @param hostPath the file system path on the host
     * @param containerPath the file system path inside the container
     * @param mode the bind mode
     * @return this
     * @deprecated use {@link GenericContainer#withCopyToContainer(Transferable, String)}
     */
    @Deprecated
    SELF withFileSystemBind(String hostPath, String containerPath, BindMode mode);

    /**
     * Adds container volumes.
     *
     * @param container the container to add volumes from
     * @param mode the bind mode
     * @return this
     */
    SELF withVolumesFrom(Container container, BindMode mode);

    /**
     * Set the ports that this container listens on
     *
     * @param ports an array of TCP ports
     * @return this
     */
    SELF withExposedPorts(Integer... ports);

    /**
     * Set the file to be copied before starting a created container
     *
     * @param mountableFile a Mountable file with path of source file / folder on host machine
     * @param containerPath a destination path on container to which the files / folders to be copied
     * @return this
     *
     * @deprecated Use {@link #withCopyToContainer(Transferable, String)} instead
     */
    @Deprecated
    SELF withCopyFileToContainer(MountableFile mountableFile, String containerPath);

    /**
     * Set the content to be copied before starting a created container
     *
     * @param transferable a Transferable
     * @param containerPath a destination path on container to which the files / folders to be copied
     * @return this
     */
    SELF withCopyToContainer(Transferable transferable, String containerPath);

    /**
     * Add an environment variable to be passed to the container.
     *
     * @param key   environment variable key
     * @param value environment variable value
     * @return this
     */
    SELF withEnv(String key, String value);

    /**
     * Add an environment variable to be passed to the container.
     *
     * @param key   environment variable key
     * @param mapper environment variable value mapper, accepts old value as an argument
     * @return this
     */
    default SELF withEnv(String key, Function<Optional<String>, String> mapper) {
        Optional<String> oldValue = Optional.ofNullable(getEnvMap().get(key).get());
        return withEnv(key, mapper.apply(oldValue));
    }

    /**
     * Add environment variables to be passed to the container.
     *
     * @param env map of environment variables
     * @return this
     */
    SELF withEnv(Map<String, String> env);

    /**
     * Add a label to the container.
     *
     * @param key   label key
     * @param value label value
     * @return this
     */
    SELF withLabel(String key, String value);

    /**
     * Add labels to the container.
     * @param labels map of labels
     * @return this
     */
    SELF withLabels(Map<String, String> labels);

    /**
     * Set the command that should be run in the container
     *
     * @param cmd a command in single string format (will automatically be split on spaces)
     * @return this
     */
    SELF withCommand(String cmd);

    /**
     * Set the command that should be run in the container
     *
     * @param commandParts a command as an array of string parts
     * @return this
     */
    SELF withCommand(String... commandParts);

    /**
     * Add an extra host entry to be passed to the container
     * @param hostname hostname to use for this hosts file entry
     * @param ipAddress IP address to use for this hosts file entry
     * @return this
     */
    SELF withExtraHost(String hostname, String ipAddress);

    /**
     * Set the network mode for this container, similar to the <code>--net &lt;name&gt;</code>
     * option on the docker CLI.
     *
     * @param networkMode network mode, e.g. including 'host', 'bridge', 'none' or the name of an existing named network.
     * @return this
     */
    SELF withNetworkMode(String networkMode);

    /**
     * Set the network for this container, similar to the <code>--network &lt;name&gt;</code>
     * option on the docker CLI.
     *
     * @param network the instance of {@link Network}
     * @return this
     */
    SELF withNetwork(Network network);

    /**
     * Set the network aliases for this container, similar to the <code>--network-alias &lt;my-service&gt;</code>
     * option on the docker CLI.
     *
     * @param aliases the list of aliases
     * @return this
     */
    SELF withNetworkAliases(String... aliases);

    /**
     * Set the image pull policy of the container
     * @return
     */
    SELF withImagePullPolicy(ImagePullPolicy policy);

    /**
     * Map a resource (file or directory) on the classpath to a path inside the container.
     * This will only work if you are running your tests outside a Docker container.
     *
     * @param resourcePath  path to the resource on the classpath (relative to the classpath root; should not start with a leading slash)
     * @param containerPath path this should be mapped to inside the container
     * @param mode          access mode for the file
     * @return this
     * @deprecated use {@link GenericContainer#withCopyToContainer(Transferable, String)}
     */
    @Deprecated
    default SELF withClasspathResourceMapping(
        final String resourcePath,
        final String containerPath,
        final BindMode mode
    ) {
        withClasspathResourceMapping(resourcePath, containerPath, mode, SelinuxContext.SHARED);
        return self();
    }

    /**
     * Map a resource (file or directory) on the classpath to a path inside the container.
     * This will only work if you are running your tests outside a Docker container.
     *
     * @param resourcePath   path to the resource on the classpath (relative to the classpath root; should not start with a leading slash)
     * @param containerPath  path this should be mapped to inside the container
     * @param mode           access mode for the file
     * @param selinuxContext selinux context argument to use for this file
     * @return this
     * @deprecated use {@link GenericContainer#withCopyToContainer(Transferable, String)}
     */
    @Deprecated
    SELF withClasspathResourceMapping(
        String resourcePath,
        String containerPath,
        BindMode mode,
        SelinuxContext selinuxContext
    );

    /**
     * Set the duration of waiting time until container treated as started.
     * @see WaitStrategy#waitUntilReady(org.testcontainers.containers.wait.strategy.WaitStrategyTarget)
     *
     * @param startupTimeout timeout
     * @return this
     */
    SELF withStartupTimeout(Duration startupTimeout);

    /**
     * Set the privilegedMode mode for the container
     * @param mode boolean
     * @return this
     */
    SELF withPrivilegedMode(boolean mode);

    /**
     * Only consider a container to have successfully started if it has been running for this duration. The default
     * value is null; if that's the value, ignore this check.
     *
     * @param minimumRunningDuration duration this container should run for if started successfully
     * @return this
     */
    SELF withMinimumRunningDuration(Duration minimumRunningDuration);

    /**
     * Set the startup check strategy used for checking whether the container has started.
     *
     * @param strategy startup check strategy
     */
    SELF withStartupCheckStrategy(StartupCheckStrategy strategy);

    /**
     * Set the working directory that the container should use on startup.
     *
     * @param workDir path to the working directory inside the container
     */
    SELF withWorkingDirectory(String workDir);

    /**
     * <b>Resolve</b> Docker image and set it.
     *
     * @param dockerImageName image name
     */
    void setDockerImageName(@NonNull String dockerImageName);

    /**
     * Get image name.
     *
     * @return image name
     */
    @NonNull
    String getDockerImageName();

    /**
     * Get the IP address that containers (e.g. browsers) can use to reference a service running on the local machine,
     * i.e. the machine on which this test is running.
     * <p>
     * For example, if a web server is running on port 8080 on this local machine, the containerized web driver needs
     * to be pointed at "http://" + getTestHostIpAddress() + ":8080" in order to access it. Trying to hit localhost
     * from inside the container is not going to work, since the container has its own IP address.
     *
     * @return the IP address of the host machine
     * @deprecated use {@link org.testcontainers.Testcontainers#exposeHostPorts(int...)}
     */
    @Deprecated
    String getTestHostIpAddress();

    /**
     * Follow container output, sending each frame (usually, line) to a consumer. Stdout and stderr will be followed.
     *
     * @param consumer consumer that the frames should be sent to
     */
    default void followOutput(Consumer<OutputFrame> consumer) {
        LogUtils.followOutput(getDockerClient(), getContainerId(), consumer);
    }

    /**
     * Follow container output, sending each frame (usually, line) to a consumer. This method allows Stdout and/or stderr
     * to be selected.
     *
     * @param consumer consumer that the frames should be sent to
     * @param types    types that should be followed (one or both of STDOUT, STDERR)
     */
    default void followOutput(Consumer<OutputFrame> consumer, OutputFrame.OutputType... types) {
        LogUtils.followOutput(getDockerClient(), getContainerId(), consumer, types);
    }

    /**
     * Attach an output consumer at container startup, enabling stdout and stderr to be followed, waited on, etc.
     * <p>
     * More than one consumer may be registered.
     *
     * @param consumer consumer that output frames should be sent to
     * @return this
     */
    SELF withLogConsumer(Consumer<OutputFrame> consumer);

    List<String> getPortBindings();

    List<String> getExtraHosts();

    Future<String> getImage();

    /**
     *
     * @deprecated use getEnvMap
     */
    @Deprecated
    List<String> getEnv();

    Map<String, Supplier<String>> getEnvMap();

    String[] getCommandParts();

    List<Bind> getBinds();

    /**
     * @deprecated Links are deprecated (see <a href="https://github.com/testcontainers/testcontainers-java/issues/465">#465</a>). Please use {@link Network} features instead.
     */
    @Deprecated
    Map<String, LinkableContainer> getLinkedContainers();

    void setExposedPorts(List<Integer> exposedPorts);

    void setPortBindings(List<String> portBindings);

    void setExtraHosts(List<String> extraHosts);

    void setImage(Future<String> image);

    void setEnv(List<String> env);

    void setCommandParts(String[] commandParts);

    void setBinds(List<Bind> binds);

    /**
     * @deprecated Links are deprecated (see <a href="https://github.com/testcontainers/testcontainers-java/issues/465">#465</a>). Please use {@link Network} features instead.
     */
    @Deprecated
    void setLinkedContainers(Map<String, LinkableContainer> linkedContainers);

    void setWaitStrategy(WaitStrategy waitStrategy);
}
