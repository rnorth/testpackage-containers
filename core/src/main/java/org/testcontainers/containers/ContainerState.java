package org.testcontainers.containers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.LogUtils;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.ThrowingFunction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public interface ContainerState {
    String STATE_HEALTHY = "healthy";

    /**
     * Get the IP address that this container may be reached on (may not be the local machine).
     *
     * @return an IP address
     * @deprecated use {@link #getHost()}
     * @see #getHost()
     */
    @Deprecated
    default String getContainerIpAddress() {
        return getHost();
    }

    default DockerClient getDockerClient() {
        return DockerClientFactory.lazyClient();
    }

    /**
     * Get the host that this container may be reached on (may not be the local machine).
     *
     * @return a host
     */
    default String getHost() {
        return DockerClientFactory.instance().dockerHostIpAddress();
    }

    /**
     * @return is the container currently running?
     */
    default boolean isRunning() {
        if (getContainerId() == null) {
            return false;
        }

        try {
            Boolean running = getCurrentContainerInfo().getState().getRunning();
            return Boolean.TRUE.equals(running);
        } catch (DockerException e) {
            return false;
        }
    }

    /**
     * @return is the container created?
     */
    default boolean isCreated() {
        if (getContainerId() == null) {
            return false;
        }

        try {
            String status = getCurrentContainerInfo().getState().getStatus();
            return "created".equalsIgnoreCase(status) || isRunning();
        } catch (DockerException e) {
            return false;
        }
    }

    /**
     * @return has the container health state 'healthy'?
     */
    default boolean isHealthy() {
        if (getContainerId() == null) {
            return false;
        }

        try {
            InspectContainerResponse inspectContainerResponse = getCurrentContainerInfo();
            HealthState health = inspectContainerResponse.getState().getHealth();
            if (health == null) {
                throw new RuntimeException(
                    "This container's image does not have a healthcheck declared, so health cannot be determined. Either amend the image or use another approach to determine whether containers are healthy."
                );
            }

            return STATE_HEALTHY.equals(health.getStatus());
        } catch (DockerException e) {
            return false;
        }
    }

    /**
     * Inspects the container and returns up-to-date inspection response.
     *
     * @return up-to-date container inspect response
     * @see #getContainerInfo()
     */
    default InspectContainerResponse getCurrentContainerInfo() {
        return getDockerClient().inspectContainerCmd(getContainerId()).exec();
    }

    /**
     * Get the actual mapped port for a first port exposed by the container.
     * Should be used in conjunction with {@link #getHost()}.
     *
     * @return the port that the exposed port is mapped to
     * @throws IllegalStateException if there are no exposed ports
     */
    default Integer getFirstMappedPort() {
        return getExposedPorts()
            .stream()
            .findFirst()
            .map(this::getMappedPort)
            .orElseThrow(() -> new IllegalStateException("Container doesn't expose any ports"));
    }

    /**
     * Get the actual mapped port for a given port exposed by the container.
     * It should be used in conjunction with {@link #getHost()}.
     * <p>
     * Note: The returned port number might be outdated (for instance, after disconnecting from a network and reconnecting
     * again). If you always need up-to-date value, override the {@link #getContainerInfo()} to return the
     * {@link #getCurrentContainerInfo()}.
     *
     * @param originalPort the original TCP port that is exposed
     * @return the port that the exposed port is mapped to, or null if it is not exposed
     * @see #getContainerInfo()
     * @see #getCurrentContainerInfo()
     */
    default Integer getMappedPort(int originalPort) {
        Preconditions.checkState(
            this.getContainerId() != null,
            "Mapped port can only be obtained after the container is started"
        );

        Ports.Binding[] binding = new Ports.Binding[0];
        final InspectContainerResponse containerInfo = this.getContainerInfo();
        if (containerInfo != null) {
            binding = containerInfo.getNetworkSettings().getPorts().getBindings().get(new ExposedPort(originalPort));
        }

        if (binding != null && binding.length > 0 && binding[0] != null) {
            return Integer.valueOf(binding[0].getHostPortSpec());
        } else {
            throw new IllegalArgumentException("Requested port (" + originalPort + ") is not mapped");
        }
    }

    /**
     * @return the exposed ports
     */
    List<Integer> getExposedPorts();

    /**
     * @return the port bindings
     */
    default List<String> getPortBindings() {
        List<String> portBindings = new ArrayList<>();
        final Ports hostPortBindings = this.getContainerInfo().getHostConfig().getPortBindings();
        for (Map.Entry<ExposedPort, Ports.Binding[]> binding : hostPortBindings.getBindings().entrySet()) {
            for (Ports.Binding portBinding : binding.getValue()) {
                portBindings.add(String.format("%s:%s", portBinding.toString(), binding.getKey()));
            }
        }
        return portBindings;
    }

    /**
     * @return the bound port numbers
     */
    default List<Integer> getBoundPortNumbers() {
        return getPortBindings()
            .stream()
            .map(PortBinding::parse)
            .map(PortBinding::getBinding)
            .map(Ports.Binding::getHostPortSpec)
            .filter(Objects::nonNull)
            .filter(NumberUtils::isNumber)
            .map(Integer::valueOf)
            .filter(port -> port > 0)
            .collect(Collectors.toList());
    }

    /**
     * @return all log output from the container from start until the current instant (both stdout and stderr)
     */
    default String getLogs() {
        return LogUtils.getOutput(getDockerClient(), getContainerId());
    }

    /**
     * @param types log types to return
     * @return all log output from the container from start until the current instant
     */
    default String getLogs(OutputFrame.OutputType... types) {
        return LogUtils.getOutput(getDockerClient(), getContainerId(), types);
    }

    /**
     * @return the id of the container
     */
    default String getContainerId() {
        return getContainerInfo().getId();
    }

    /**
     * Returns the container inspect response. The response might be cached/outdated.
     *
     * @return the container info
     * @see #getCurrentContainerInfo()
     */
    InspectContainerResponse getContainerInfo();

    /**
     * Run a command inside a running container, as though using "docker exec", and interpreting
     * the output as UTF8.
     * <p>
     * @see #execInContainer(Charset, String...)
     */
    default Container.ExecResult execInContainer(String... command)
        throws UnsupportedOperationException, IOException, InterruptedException {
        return execInContainer(StandardCharsets.UTF_8, command);
    }

    /**
     * Run a command inside a running container, as though using "docker exec".
     * <p>
     * @see ExecInContainerPattern#execInContainer(DockerClient, InspectContainerResponse, Charset, String...)
     */
    default Container.ExecResult execInContainer(Charset outputCharset, String... command)
        throws UnsupportedOperationException, IOException, InterruptedException {
        return ExecInContainerPattern.execInContainer(getDockerClient(), getContainerInfo(), outputCharset, command);
    }

    /**
     * Run a command inside a running container as a given user, as using "docker exec -u user".
     * <p>
     * @see ExecInContainerPattern#execInContainerWithUser(DockerClient, InspectContainerResponse, String, String...)
     * @deprecated use {@link #execInContainer(ExecConfig)}
     */
    @Deprecated
    default Container.ExecResult execInContainerWithUser(String user, String... command)
        throws UnsupportedOperationException, IOException, InterruptedException {
        return ExecInContainerPattern.execInContainer(
            getDockerClient(),
            getContainerInfo(),
            ExecConfig.builder().user(user).command(command).build()
        );
    }

    /**
     * Run a command inside a running container as a given user, as using "docker exec -u user".
     * <p>
     * @see ExecInContainerPattern#execInContainerWithUser(DockerClient, InspectContainerResponse, Charset, String, String...)
     * @deprecated use {@link #execInContainer(Charset, ExecConfig)}
     */
    @Deprecated
    default Container.ExecResult execInContainerWithUser(Charset outputCharset, String user, String... command)
        throws UnsupportedOperationException, IOException, InterruptedException {
        return ExecInContainerPattern.execInContainer(
            getDockerClient(),
            getContainerInfo(),
            outputCharset,
            ExecConfig.builder().user(user).command(command).build()
        );
    }

    /**
     * Run a command inside a running container, as though using "docker exec".
     */
    default Container.ExecResult execInContainer(ExecConfig execConfig)
        throws UnsupportedOperationException, IOException, InterruptedException {
        return ExecInContainerPattern.execInContainer(getDockerClient(), getContainerInfo(), execConfig);
    }

    /**
     * Run a command inside a running container, as though using "docker exec".
     */
    default Container.ExecResult execInContainer(Charset outputCharset, ExecConfig execConfig)
        throws UnsupportedOperationException, IOException, InterruptedException {
        return ExecInContainerPattern.execInContainer(getDockerClient(), getContainerInfo(), outputCharset, execConfig);
    }

    /**
     *
     * Copies a file or directory to the container.
     *
     * @param mountableFile file or directory which is copied into the container
     * @param containerPath destination path inside the container
     */
    default void copyFileToContainer(MountableFile mountableFile, String containerPath) {
        File sourceFile = new File(mountableFile.getResolvedPath());

        if (containerPath.endsWith("/") && sourceFile.isFile()) {
            final Logger logger = LoggerFactory.getLogger(GenericContainer.class);
            logger.warn(
                "folder-like containerPath in copyFileToContainer is deprecated, please explicitly specify a file path"
            );
            copyFileToContainer((Transferable) mountableFile, containerPath + sourceFile.getName());
        } else {
            copyFileToContainer((Transferable) mountableFile, containerPath);
        }
    }

    /**
     *
     * Copies a file to the container.
     *
     * @param transferable file which is copied into the container
     * @param containerPath destination path inside the container
     */
    @SneakyThrows({ IOException.class, InterruptedException.class })
    default void copyFileToContainer(Transferable transferable, String containerPath) {
        if (getContainerId() == null) {
            throw new IllegalStateException("copyFileToContainer can only be used with created / running container");
        }

        try (
            PipedOutputStream pipedOutputStream = new PipedOutputStream();
            PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);
            TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(pipedOutputStream)
        ) {
            Thread thread = new Thread(() -> {
                try {
                    tarArchive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                    tarArchive.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

                    transferable.transferTo(tarArchive, containerPath);
                } finally {
                    IOUtils.closeQuietly(tarArchive);
                }
            });

            thread.start();

            getDockerClient()
                .copyArchiveToContainerCmd(getContainerId())
                .withTarInputStream(pipedInputStream)
                .withRemotePath("/")
                .exec();

            thread.join();
        }
    }

    /**
     * Copies a file which resides inside the container to user defined directory
     *
     * @param containerPath path to file which is copied from container
     * @param destinationPath destination path to which file is copied with file name
     * @throws IOException if there's an issue communicating with Docker or receiving entry from TarArchiveInputStream
     * @throws InterruptedException if the thread waiting for the response is interrupted
     */
    default void copyFileFromContainer(String containerPath, String destinationPath)
        throws IOException, InterruptedException {
        copyFileFromContainer(
            containerPath,
            inputStream -> {
                try (FileOutputStream output = new FileOutputStream(destinationPath)) {
                    IOUtils.copy(inputStream, output);
                    return null;
                }
            }
        );
    }


    /**
     * Copies a file or directory from a container to the host.
     *
     * @param containerPath the path within the container from which to copy
     * @param hostPath the path on the host to which the contents will be copied
     */
    default void copyPathFromContainer(String containerPath, String hostPath) {
        if (getContainerId() == null) {
            throw new IllegalStateException("copyPathFromContainer can only be used when the Container is created.");
        }

        copyArchiveFromContainer(containerPath, (inputStream) -> {
            try(TarArchiveInputStream tarStream = new TarArchiveInputStream(inputStream)) {
                // advance to first tar entry
            TarArchiveEntry currentEntry = tarStream.getNextTarEntry();

            // copy is file only (no directory copy)
            boolean fileOnly = currentEntry.isFile();
            // in case of coping a directory,
            // we don't want to emit the root directory (would result in double nested directories)
            String dirPrefixToRemove = currentEntry.isDirectory() ? currentEntry.getName() : "";

            Path hostPathObj = Paths.get(hostPath);

            while (currentEntry != null) {
                File destFile;
                if (fileOnly) {
                    // if we copy only a single file, we use the specified hostPath as destination
                    destFile = hostPathObj.toFile();
                } else {
                    // if we copy a directory we have to resolve the path
                    destFile = hostPathObj.resolve(currentEntry.getName()
                          // remove the root directory of the copied TAR
                          .replaceFirst("^" + dirPrefixToRemove, "")
                    ).toFile();
                }

                if (currentEntry.isFile()) {
                    // create parent directory if they do not exist yet
                    FileUtils.forceMkdirParent(destFile);
                    // copy file to destination
                    try (FileOutputStream output = new FileOutputStream(destFile)) {
                        IOUtils.copy(tarStream, output);
                    }
                } else if (currentEntry.isDirectory()) {
                    if (destFile.exists() && !destFile.isDirectory()) {
                        // throw exception if directory would override already existing file
                        throw new IOException(
                              "copyPathFromContainer cannot create directory '" + destFile + "' as a file at this path already exists.");
                    }
                    // create a destination directory
                    FileUtils.forceMkdir(destFile);
                } else {
                    // if we cannot handle the entry, we throw an exception
                    throw new UnsupportedOperationException(
                          "copyPathFromContainer can only copy files and directories. '" +
                                currentEntry.getName() +
                                "' is neither a file nor a directory.");
                }

                // jump to next tar entry
                currentEntry = tarStream.getNextTarEntry();
            }

            return true;
            }
        });
    }


    /**
     * Streams a file which resides inside the container
     *
     * @param containerPath path to file which is copied from container
     * @param function function that takes InputStream of the copied file
     */
    @SneakyThrows
    default <T> T copyFileFromContainer(String containerPath, ThrowingFunction<InputStream, T> function) {
        if (getContainerId() == null) {
            throw new IllegalStateException("copyFileFromContainer can only be used when the Container is created.");
        }

        return copyArchiveFromContainer(containerPath, (stream) -> {
            try(
                  TarArchiveInputStream tarStream = new TarArchiveInputStream(stream)
                  ) {
                tarStream.getNextTarEntry();
                return function.apply(tarStream);
            }
        });
    }

    /**
     * Copies an archive from the container at the specified path
     * and processes it using the provided function.
     * The path can be a file or directory and is automatically archived.
     *
     * @param containerPath the path inside the container to the file or directory to copy
     * @param function a function that takes an {@link InputStream} of the copied
     *                 archive and returns a result
     * @return the result of applying the function to the InputStream
     */
    @SneakyThrows
    default <T> T copyArchiveFromContainer(String containerPath, ThrowingFunction<InputStream, T> function) {
        if (getContainerId() == null) {
            throw new IllegalStateException("copyArchiveFromContainer can only be used when the Container is created.");
        }
        DockerClient dockerClient = getDockerClient();
        try (
              InputStream inputStream = dockerClient.copyArchiveFromContainerCmd(getContainerId(), containerPath).exec();
        ) {
            return function.apply(inputStream);
        }
    }


}
