package org.testcontainers.dockerclient;

import com.sun.jna.Library;
import com.sun.jna.Native;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 *
 * @deprecated this class is used by the SPI and should not be used directly
 */
@Deprecated
@Slf4j
public final class RootlessPodmanClientProviderStrategy extends DockerClientProviderStrategy {

    public static final int PRIORITY = UnixSocketClientProviderStrategy.PRIORITY - 1;

    @Getter(lazy = true)
    @Nullable
    private final Path socketPath = resolveSocketPath();

    private Path resolveSocketPath() {
        return tryEnv()
            .orElseGet(() -> {
                Path implicitPath = Paths.get("/run/user/" + LibC.INSTANCE.getuid());
                return tryFolder(implicitPath).orElse(null);
            });
    }

    private Optional<Path> tryEnv() {
        String xdgRuntimeDir = System.getenv("XDG_RUNTIME_DIR");
        if (StringUtils.isBlank(xdgRuntimeDir)) {
            log.debug("$XDG_RUNTIME_DIR is not set.");
            return Optional.empty();
        }
        Path path = Paths.get(xdgRuntimeDir);
        if (!Files.exists(path)) {
            log.debug("$XDG_RUNTIME_DIR is set to '{}' but the folder does not exist.", path);
            return Optional.empty();
        }
        Path podmanSocketPath = path.resolve("podman/podman.sock");
        if (!Files.exists(podmanSocketPath)) {
            log.debug("$XDG_RUNTIME_DIR is set but '{}' does not exist.", podmanSocketPath);
            return Optional.empty();
        }
        return Optional.of(podmanSocketPath);
    }

    private Optional<Path> tryFolder(Path path) {
        if (!Files.exists(path)) {
            log.debug("'{}' does not exist.", path);
            return Optional.empty();
        }
        Path podmanSocketPath = path.resolve("podman/podman.sock");
        if (!Files.exists(podmanSocketPath)) {
            log.debug("'{}' does not exist.", podmanSocketPath);
            return Optional.empty();
        }
        return Optional.of(podmanSocketPath);
    }

    @Override
    public TransportConfig getTransportConfig() throws InvalidConfigurationException {
        return TransportConfig.builder().dockerHost(URI.create("unix://" + getSocketPath().toString())).build();
    }

    @Override
    protected boolean isApplicable() {
        return SystemUtils.IS_OS_LINUX && getSocketPath() != null && Files.exists(getSocketPath());
    }

    @Override
    public String getDescription() {
        return "Rootless Podman accessed via Unix socket (" + getSocketPath() + ")";
    }

    @Override
    protected int getPriority() {
        return PRIORITY;
    }

    private interface LibC extends Library {
        LibC INSTANCE = Native.loadLibrary("c", LibC.class);
        int getuid();
    }
}
