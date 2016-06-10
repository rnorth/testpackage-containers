package org.testcontainers.junit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.DockerComposeContainer;
import redis.clients.jedis.Jedis;

import java.io.File;

import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;

/**
 * Created by rnorth on 08/08/2015.
 */
public class DockerComposeContainerScalingTest {

    private static final int REDIS_PORT = 6379;

    private Jedis[] clients = new Jedis[3];

    @Rule
    public DockerComposeContainer environment = new DockerComposeContainer(new File("src/test/resources/scaled-compose-test.yml"))
            .withScaledService("redis", 3)
            .withExposedService("redis_1", REDIS_PORT)
            .withExposedService("redis_2", REDIS_PORT)
            .withExposedService("redis_3", REDIS_PORT);

    @Before
    public void setupClients() {
        for (int i = 0; i < 3; i++) {

            String name = String.format("redis_%d", i + 1);

            clients[i] = new Jedis(environment.getServiceHost(name, REDIS_PORT), environment.getServicePort(name, REDIS_PORT));
        }
    }

    @Test
    public void simpleTest() {

        for (int i = 0; i < 3; i++) {
            clients[i].incr("somekey");

            assertEquals("Each redis instance is separate", "1", clients[i].get("somekey"));
        }
    }
}
