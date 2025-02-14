package org.testcontainers.cloudflare;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class CloudflaredContainerTest {

    @Test
    public void shouldStartAndTunnelToHelloWorld() throws IOException {
        try (
            GenericContainer<?> helloworld = new GenericContainer<>(
                DockerImageName.parse("testcontainers/helloworld:1.1.0")
            )
                .withNetworkAliases("helloworld")
                .withExposedPorts(8080, 8081)
                .waitingFor(new HttpWaitStrategy())
        ) {
            helloworld.start();

            try (
                // starting {
                CloudflaredContainer cloudflare = new CloudflaredContainer(
                    DockerImageName.parse("cloudflare/cloudflared:latest"),
                    helloworld.getFirstMappedPort()
                );
                //
            ) {
                cloudflare.start();
                // get_public_url {
                String url = cloudflare.getPublicUrl();
                // }

                assertThat(url).as("Public url contains 'cloudflare'").contains("cloudflare");
                String body = readUrl(url);

                assertThat(body.trim()).as("the index page contains the title 'Hello world'").contains("Hello world");
            }
        }
    }

    private String readUrl(String url) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()));

        StringBuilder sb = new StringBuilder();
        String inputLine = null;
        while ((inputLine = in.readLine()) != null) {
            sb.append(inputLine);
        }
        in.close();

        return sb.toString();
    }
}
