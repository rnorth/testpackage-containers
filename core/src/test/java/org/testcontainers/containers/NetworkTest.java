package org.testcontainers.containers;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.TestImages;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class NetworkTest {

    public static final String MY_NETWORK = "myNetwork";

    public static class WithRules {

        @Rule
        public Network network = Network.newNetwork();

        @Rule
        public GenericContainer<?> foo = new GenericContainer<>(TestImages.TINY_IMAGE)
            .withNetwork(network)
            .withNetworkAliases("foo")
            .withCommand("/bin/sh", "-c", "while true ; do printf 'HTTP/1.1 200 OK\\n\\nyay' | nc -l -p 8080; done");

        @Rule
        public GenericContainer<?> bar = new GenericContainer<>(TestImages.TINY_IMAGE)
            .withNetwork(network)
            .withCommand("top");

        @Test
        public void testNetworkSupport() throws Exception {
            String response = bar.execInContainer("wget", "-O", "-", "http://foo:8080").getStdout();
            assertThat(response).as("received response").isEqualTo("yay");
        }
    }

    public static class WithoutRules {

        @Test
        public void testNetworkSupport() throws Exception {
            // useCustomNetwork {
            try (
                Network network = Network.newNetwork();
                GenericContainer<?> foo = new GenericContainer<>(TestImages.TINY_IMAGE)
                    .withNetwork(network)
                    .withNetworkAliases("foo")
                    .withCommand(
                        "/bin/sh",
                        "-c",
                        "while true ; do printf 'HTTP/1.1 200 OK\\n\\nyay' | nc -l -p 8080; done"
                    );
                GenericContainer<?> bar = new GenericContainer<>(TestImages.TINY_IMAGE)
                    .withNetwork(network)
                    .withCommand("top")
            ) {
                foo.start();
                bar.start();

                String response = bar.execInContainer("wget", "-O", "-", "http://foo:8080").getStdout();
                assertThat(response).as("received response").isEqualTo("yay");
            }
            // }
        }

        @Test
        public void testNetworkSupportReuseNetwork() throws Exception {
            // useCustomNetwork {
            try (
                Network network1 = Network.builder().name(MY_NETWORK).build();
                Network network2 = Network.builder().name(MY_NETWORK).build();
            ) {
                assertThat(network1.getId()).as("network is the same").isEqualTo(network2.getId());
            }
            // }
        }

        @Test
        public void testBuilder() {
            try (Network network = Network.builder().driver("macvlan").name(MY_NETWORK).build()) {
                String id = network.getId();
                com.github.dockerjava.api.model.Network networkResult = DockerClientFactory.instance().client().inspectNetworkCmd().withNetworkId(id).exec();
                assertThat(
                    networkResult.getDriver()
                )
                    .as("Flag is set")
                    .isEqualTo("macvlan");
                assertThat(
                    networkResult.getName()
                )
                    .as("Name is set")
                    .isEqualTo(MY_NETWORK);
            }
        }

        @Test
        public void testModifiers() {
            try (
                Network network = Network.builder().createNetworkCmdModifier(cmd -> cmd.withDriver("macvlan")).build()
            ) {
                String id = network.getId();
                assertThat(
                    DockerClientFactory.instance().client().inspectNetworkCmd().withNetworkId(id).exec().getDriver()
                )
                    .as("Flag is set")
                    .isEqualTo("macvlan");
            }
        }

        @Test
        public void testReusability() {
            try (Network network = Network.newNetwork()) {
                String firstId = network.getId();
                assertThat(DockerClientFactory.instance().client().inspectNetworkCmd().withNetworkId(firstId).exec())
                    .as("Network exists")
                    .isNotNull();

                network.close();

                assertThat(
                    DockerClientFactory
                        .instance()
                        .client()
                        .inspectNetworkCmd()
                        .withNetworkId(network.getId())
                        .exec()
                        .getId()
                )
                    .as("New network created")
                    .isNotEqualTo(firstId);
            }
        }
    }
}
