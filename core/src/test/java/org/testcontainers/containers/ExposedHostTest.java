package org.testcontainers.containers;

import com.google.common.collect.ImmutableMap;
import com.sun.net.httpserver.HttpServer;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.TestImages;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

public class ExposedHostTest {

    private static final int FIXED_PORT_80 = 80;
    
    private static final int FIXED_PORT_81 = 81;

    private static HttpServer server;

    @BeforeClass
    public static void setUpClass() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
            "/",
            exchange -> {
                byte[] content = "Hello World!".getBytes();
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(content);
                    responseBody.flush();
                }
            }
        );
        server.start();
    }

    @AfterClass
    public static void tearDownClass() {
        server.stop(0);
    }

    @After
    public void tearDown() {
        PortForwardingContainer.INSTANCE.reset();
    }

    @Test
    public void testExposedHostAfterContainerIsStarted() {
        try (GenericContainer<?> container = new GenericContainer<>(tinyContainerDef()).withAccessToHost(true)) {
            container.start();
            Testcontainers.exposeHostPorts(server.getAddress().getPort());
            assertResponse(container, server.getAddress().getPort());
        }
    }

    @Test
    public void testExposedHost() {
        Testcontainers.exposeHostPorts(server.getAddress().getPort());
        assertResponse(new GenericContainer<>(tinyContainerDef()), server.getAddress().getPort());
    }

    @Test
    public void testExposedHostWithNetwork() {
        Testcontainers.exposeHostPorts(server.getAddress().getPort());
        try (Network network = Network.newNetwork()) {
            assertResponse(
                new GenericContainer<>(tinyContainerDef()).withNetwork(network),
                server.getAddress().getPort()
            );
        }
    }

    @Test
    public void testExposedHostPortOnFixedInternalPorts() {
        Testcontainers.exposeHostPorts(ImmutableMap.of(server.getAddress().getPort(), FIXED_PORT_80));
        Testcontainers.exposeHostPorts(ImmutableMap.of(server.getAddress().getPort(), FIXED_PORT_81));
    
        assertResponse(new GenericContainer<>(tinyContainerDef()), FIXED_PORT_80);
        assertResponse(new GenericContainer<>(tinyContainerDef()), FIXED_PORT_81);
    }

    @Test
    public void testExposedHostWithReusableContainerAndFixedNetworkName() throws IOException, InterruptedException {
        assumeThat(TestcontainersConfiguration.getInstance().environmentSupportsReuse()).isTrue();
        Network network = createReusableNetwork(UUID.randomUUID());
        Testcontainers.exposeHostPorts(server.getAddress().getPort());

        GenericContainer<?> container = new GenericContainer<>(tinyContainerDef()).withReuse(true).withNetwork(network);
        container.start();

        assertHttpResponseFromHost(container, server.getAddress().getPort());

        PortForwardingContainer.INSTANCE.reset();
        Testcontainers.exposeHostPorts(server.getAddress().getPort());

        GenericContainer<?> reusedContainer = new GenericContainer<>(tinyContainerDef())
            .withReuse(true)
            .withNetwork(network);
        reusedContainer.start();

        assertThat(reusedContainer.getContainerId()).isEqualTo(container.getContainerId());
        assertHttpResponseFromHost(reusedContainer, server.getAddress().getPort());

        container.stop();
        reusedContainer.stop();
        DockerClientFactory.lazyClient().removeNetworkCmd(network.getId()).exec();
    }

    @Test
    public void testExposedHostOnFixedInternalPortsWithReusableContainerAndFixedNetworkName()
        throws IOException, InterruptedException {
        assumeThat(TestcontainersConfiguration.getInstance().environmentSupportsReuse()).isTrue();
        Network network = createReusableNetwork(UUID.randomUUID());
        Testcontainers.exposeHostPorts(ImmutableMap.of(server.getAddress().getPort(), 1234));

        GenericContainer<?> container = new GenericContainer<>(tinyContainerDef()).withReuse(true).withNetwork(network);
        container.start();

        assertHttpResponseFromHost(container, 1234);

        PortForwardingContainer.INSTANCE.reset();
        Testcontainers.exposeHostPorts(ImmutableMap.of(server.getAddress().getPort(), 1234));

        GenericContainer<?> reusedContainer = new GenericContainer<>(tinyContainerDef())
            .withReuse(true)
            .withNetwork(network);
        reusedContainer.start();

        assertThat(reusedContainer.getContainerId()).isEqualTo(container.getContainerId());
        assertHttpResponseFromHost(reusedContainer, 1234);

        container.stop();
        reusedContainer.stop();
        DockerClientFactory.lazyClient().removeNetworkCmd(network.getId()).exec();
    }

    @SneakyThrows
    protected void assertResponse(GenericContainer<?> container, int port) {
        try {
            container.start();

            String response = container
                .execInContainer("wget", "-O", "-", "http://host.testcontainers.internal:" + port)
                .getStdout();

            assertThat(response).as("received response").isEqualTo("Hello World!");
        } finally {
            container.stop();
        }
    }

    private ContainerDef tinyContainerDef() {
        return new TinyContainerDef();
    }

    private static class TinyContainerDef extends ContainerDef {

        TinyContainerDef() {
            setImage(TestImages.TINY_IMAGE);
            setCommand("top");
        }
    }

    private void assertHttpResponseFromHost(GenericContainer<?> container, int port)
        throws IOException, InterruptedException {
        String httpResponseFromHost = container
            .execInContainer("wget", "-O", "-", "http://host.testcontainers.internal:" + port)
            .getStdout();
        assertThat(httpResponseFromHost).isEqualTo("Hello World!");
    }

    private static Network createReusableNetwork(UUID name) {
        String networkName = name.toString();
        Network network = new Network() {
            @Override
            public String getId() {
                return networkName;
            }

            @Override
            public void close() {}

            @Override
            public Statement apply(Statement base, Description description) {
                return null;
            }
        };

        List<com.github.dockerjava.api.model.Network> networks = DockerClientFactory
            .lazyClient()
            .listNetworksCmd()
            .withNameFilter(networkName)
            .exec();
        if (networks.isEmpty()) {
            Network.builder().createNetworkCmdModifier(cmd -> cmd.withName(networkName)).build().getId();
        }
        return network;
    }
}
