package org.testcontainers.junit;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.utility.TestEnvironment;
import redis.clients.jedis.Jedis;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by rnorth on 08/08/2015.
 */
public class DockerComposeContainerScalingTest {

    private static final int REDIS_PORT = 6379;

    private Jedis[] clients = new Jedis[3];

    @BeforeClass
    public static void checkVersion() {
        Assume.assumeTrue(TestEnvironment.dockerApiAtLeast("1.22"));
    }

    @Rule
    public DockerComposeContainer environment = new DockerComposeContainer(
        new File("src/test/resources/scaled-compose-test.yml")
    )
        .withScaledService("redis", 3)
        .withExposedService("redis", REDIS_PORT) // implicit '_1'
        .withExposedService("redis_2", REDIS_PORT) // explicit service index
        .withExposedService("redis", 3, REDIS_PORT); // explicit service index via parameter

    @Before
    public void setupClients() {
        for (int i = 0; i < 3; i++) {
            String name = String.format("redis_%d", i + 1);

            clients[i] =
                new Jedis(environment.getServiceHost(name, REDIS_PORT), environment.getServicePort(name, REDIS_PORT));
        }
    }

    @Test
    public void incrAndGet_somekey_in_client_0() {
        clients[0].incr("somekey");
        assertThat(clients[0].get("somekey")).as("Client 0: Each redis instance is separate").isEqualTo("1");
    }

    @Test
    public void incrAndGet_somekey_in_client_1() {
        clients[1].incr("somekey");
        assertThat(clients[1].get("somekey")).as("Client 1: Each redis instance is separate").isEqualTo("1");
    }

    @Test
    public void incrAndGet_somekey_in_client_2() {
        clients[2].incr("somekey");
        assertThat(clients[2].get("somekey")).as("Client 2: Each redis instance is separate").isEqualTo("1");
    }
}
