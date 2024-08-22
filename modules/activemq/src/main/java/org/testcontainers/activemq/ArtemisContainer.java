package org.testcontainers.activemq;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Testcontainers implementation for Apache ActiveMQ Artemis.
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Console: 8161</li>
 *     <li>TCP: 61616</li>
 *     <li>HORNETQ: 5445</li>
 *     <li>AMQP: 5672</li>
 *     <li>STOMP: 61613</li>
 *     <li>MQTT: 1883</li>
 *     <li>WS: 61614</li>
 * </ul>
 */
public class ArtemisContainer extends GenericContainer<ArtemisContainer> {

    private static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("apache/activemq-artemis");

    private static final int WEB_CONSOLE_PORT = 8161;

    // CORE,MQTT,AMQP,HORNETQ,STOMP,OPENWIRE
    private static final int TCP_PORT = 61616;

    private static final int HORNETQ_STOMP_PORT = 5445;

    private static final int AMQP_PORT = 5672;

    private static final int STOMP_PORT = 61613;

    private static final int MQTT_PORT = 1883;

    private static final int WS_PORT = 61614;

    private String username = "artemis";

    private String password = "artemis";

    public ArtemisContainer(String image) {
        this(DockerImageName.parse(image));
    }

    public ArtemisContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE);

        withExposedPorts(WEB_CONSOLE_PORT, TCP_PORT, HORNETQ_STOMP_PORT, AMQP_PORT, STOMP_PORT, MQTT_PORT, WS_PORT);
        waitingFor(Wait.forLogMessage(".*HTTP Server started.*", 1).withStartupTimeout(Duration.ofMinutes(1)));
    }

    @Override
    protected void configure() {
        withEnv("ARTEMIS_USER", this.username);
        withEnv("ARTEMIS_PASSWORD", this.password);
    }

    public ArtemisContainer withUser(String username) {
        this.username = username;
        return this;
    }

    public ArtemisContainer withPassword(String password) {
        this.password = password;
        return this;
    }

    public String getBrokerUrl() {
        return String.format("tcp://%s:%s", getHost(), getMappedPort(TCP_PORT));
    }

    public String getUser() {
        return getEnvMap().get("ARTEMIS_USER").get();
    }

    public String getPassword() {
        return getEnvMap().get("ARTEMIS_PASSWORD").get();
    }
}
