package org.testcontainers.containers;

import com.github.dockerjava.api.model.ContainerNetwork;
import com.trilead.ssh2.Connection;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public enum PortForwardingContainer {
    INSTANCE;

    private static String PASSWORD = UUID.randomUUID().toString();

    private static ContainerDef DEFINITION = new ContainerDef() {
        {
            setImage(DockerImageName.parse("testcontainers/sshd:1.2.0"));
            addExposedTcpPort(22);
            addEnvVar("PASSWORD", PASSWORD);
        }
    };

    private GenericContainer<?> container;

    private final Set<Entry<Integer, Integer>> exposedPorts = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final Connection sshConnection = createSSHSession();

    @SneakyThrows
    private Connection createSSHSession() {
        container = new GenericContainer<>(DEFINITION);
        container.start();

        Connection connection = new Connection(container.getHost(), container.getMappedPort(22));

        connection.setTCPNoDelay(true);
        connection.connect(
            (hostname, port, serverHostKeyAlgorithm, serverHostKey) -> true,
            (int) Duration.ofSeconds(30).toMillis(),
            (int) Duration.ofSeconds(30).toMillis()
        );

        if (!connection.authenticateWithPassword("root", PASSWORD)) {
            throw new IllegalStateException("Authentication failed.");
        }

        return connection;
    }

    @SneakyThrows
    public void exposeHostPort(int port) {
        exposeHostPort(port, port);
    }

    @SneakyThrows
    public void exposeHostPort(int hostPort, int containerPort) {
        if (exposedPorts.add(new AbstractMap.SimpleEntry<>(hostPort, containerPort))) {
            getSshConnection().requestRemotePortForwarding("", containerPort, "localhost", hostPort);
        }
    }

    void start() {
        getSshConnection();
    }

    Optional<ContainerNetwork> getNetwork() {
        return Optional
            .ofNullable(container)
            .map(GenericContainer::getContainerInfo)
            .flatMap(it -> Optional.ofNullable(it.getNetworkSettings()))
            .flatMap(it -> Optional.ofNullable(it.getNetworks()))
            .flatMap(it -> it.values().stream().findFirst());
    }

    void reset() {
        if (container != null) {
            container.stop();
        }
        container = null;

        ((AtomicReference<?>) (Object) sshConnection).set(null);

        exposedPorts.clear();
    }
}
