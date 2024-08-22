package org.testcontainers.containers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.ComparableVersion;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Testcontainers implementation for Apache Kafka.
 * Zookeeper can be optionally configured.
 * <p>
 * Supported image: {@code confluentinc/cp-kafka}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Kafka: 9093</li>
 *     <li>Zookeeper: 2181</li>
 * </ul>
 */
public class KafkaContainer extends GenericContainer<KafkaContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("confluentinc/cp-kafka");

    private static final String DEFAULT_TAG = "5.4.3";

    public static final int KAFKA_PORT = 9093;

    public static final int ZOOKEEPER_PORT = 2181;

    private static final String DEFAULT_INTERNAL_TOPIC_RF = "1";

    private static final String STARTER_SCRIPT = "/tmp/testcontainers_start.sh";

    // https://docs.confluent.io/platform/7.0.0/release-notes/index.html#ak-raft-kraft
    private static final String MIN_KRAFT_TAG = "7.0.0";

    public static final String DEFAULT_CLUSTER_ID = "4L6g3nShT-eMCtK--X86sw";

    protected String externalZookeeperConnect = null;

    private boolean kraftEnabled = false;

    private static final String PROTOCOL_PREFIX = "TC";

    /**
     * @deprecated use {@link #KafkaContainer(DockerImageName)} instead
     */
    @Deprecated
    public KafkaContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    /**
     * @deprecated use {@link #KafkaContainer(DockerImageName)} instead
     */
    @Deprecated
    public KafkaContainer(String confluentPlatformVersion) {
        this(DEFAULT_IMAGE_NAME.withTag(confluentPlatformVersion));
    }

    public KafkaContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
    }

    @Override
    KafkaContainerDef createContainerDef() {
        return new KafkaContainerDef();
    }

    @Override
    KafkaContainerDef getContainerDef() {
        return (KafkaContainerDef) super.getContainerDef();
    }

    public KafkaContainer withEmbeddedZookeeper() {
        if (this.kraftEnabled) {
            throw new IllegalStateException("Cannot configure Zookeeper when using Kraft mode");
        }
        this.externalZookeeperConnect = null;
        return self();
    }

    public KafkaContainer withExternalZookeeper(String connectString) {
        if (this.kraftEnabled) {
            throw new IllegalStateException("Cannot configure Zookeeper when using Kraft mode");
        }
        this.externalZookeeperConnect = connectString;
        return self();
    }

    public KafkaContainer withKraft() {
        if (this.externalZookeeperConnect != null) {
            throw new IllegalStateException("Cannot configure Kraft mode when Zookeeper configured");
        }
        verifyMinKraftVersion();
        this.kraftEnabled = true;
        return self();
    }

    private void verifyMinKraftVersion() {
        String actualVersion = DockerImageName.parse(getDockerImageName()).getVersionPart();
        if (new ComparableVersion(actualVersion).isLessThan(MIN_KRAFT_TAG)) {
            throw new IllegalArgumentException(
                String.format(
                    "Provided Confluent Platform's version %s is not supported in Kraft mode (must be %s or above)",
                    actualVersion,
                    MIN_KRAFT_TAG
                )
            );
        }
    }

    private boolean isLessThanCP740() {
        String actualVersion = DockerImageName.parse(getDockerImageName()).getVersionPart();
        return new ComparableVersion(actualVersion).isLessThan("7.4.0");
    }

    public KafkaContainer withClusterId(String clusterId) {
        Objects.requireNonNull(clusterId, "clusterId cannot be null");
        getContainerDef().withClusterId(clusterId);
        return self();
    }

    public String getBootstrapServers() {
        return String.format("PLAINTEXT://%s:%s", getHost(), getMappedPort(KAFKA_PORT));
    }

    @Override
    protected void configure() {
        getContainerDef().resolveListeners();

        if (this.kraftEnabled) {
            configureKraft();
        } else {
            configureZookeeper();
        }
    }

    protected void configureKraft() {
        getContainerDef().withRaft();
    }

    protected void configureZookeeper() {
        if (this.externalZookeeperConnect == null) {
            getContainerDef().withEmbeddedZookeeper();
        } else {
            getContainerDef().withZookeeper(this.externalZookeeperConnect);
        }
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        super.containerIsStarting(containerInfo);

        List<String> advertisedListeners = new ArrayList<>();
        advertisedListeners.add(getBootstrapServers());
        advertisedListeners.add(brokerAdvertisedListener(containerInfo));

        List<Supplier<String>> listenersToTransform = new ArrayList<>(getContainerDef().listeners);
        for (int i = 0; i < listenersToTransform.size(); i++) {
            Supplier<String> listenerSupplier = listenersToTransform.get(i);
            String protocol = String.format("%s-%d", PROTOCOL_PREFIX, i);
            String listener = listenerSupplier.get();
            String listenerProtocol = String.format("%s://%s", protocol, listener);
            advertisedListeners.add(listenerProtocol);
        }

        String kafkaAdvertisedListeners = String.join(",", advertisedListeners);

        String command = "#!/bin/bash\n";
        // exporting KAFKA_ADVERTISED_LISTENERS with the container hostname
        command += String.format("export KAFKA_ADVERTISED_LISTENERS=%s\n", kafkaAdvertisedListeners);

        if (!this.kraftEnabled || isLessThanCP740()) {
            // Optimization: skip the checks
            command += "echo '' > /etc/confluent/docker/ensure \n";
        }

        if (this.kraftEnabled) {
            command += commandKraft();
        } else if (this.externalZookeeperConnect == null) {
            command += commandZookeeper();
        }

        // Run the original command
        command += "/etc/confluent/docker/run \n";
        copyFileToContainer(Transferable.of(command, 0777), STARTER_SCRIPT);
    }

    protected String commandKraft() {
        String command = "sed -i '/KAFKA_ZOOKEEPER_CONNECT/d' /etc/confluent/docker/configure\n";
        command +=
            "echo 'kafka-storage format --ignore-formatted -t \"" +
            getContainerDef().getEnvVars().get("CLUSTER_ID") +
            "\" -c /etc/kafka/kafka.properties' >> /etc/confluent/docker/configure\n";
        return command;
    }

    protected String commandZookeeper() {
        String command = "echo 'clientPort=" + ZOOKEEPER_PORT + "' > /tmp/zookeeper.properties\n";
        command += "echo 'dataDir=/var/lib/zookeeper/data' >> /tmp/zookeeper.properties\n";
        command += "echo 'dataLogDir=/var/lib/zookeeper/log' >> /tmp/zookeeper.properties\n";
        command += "zookeeper-server-start /tmp/zookeeper.properties &\n";
        return command;
    }

    /**
     * Add a {@link Supplier} that will provide a listener with format {@code host:port}.
     * Host will be added as a network alias.
     * <p>
     * The listener will be added to the list of default listeners.
     * <p>
     * Default listeners:
     * <ul>
     *     <li>0.0.0.0:9092</li>
     *     <li>0.0.0.0:9093</li>
     * </ul>
     * <p>
     * Default advertised listeners:
     * <ul>
     *      <li>{@code container.getHost():container.getMappedPort(9093)}</li>
     *      <li>{@code container.getConfig().getHostName():9092}</li>
     * </ul>
     *
     * @param listenerSupplier a supplier that will provide a listener
     * @return this {@link KafkaContainer} instance
     */
    public KafkaContainer withListener(Supplier<String> listenerSupplier) {
        getContainerDef().withListener(listenerSupplier);
        return this;
    }

    protected String brokerAdvertisedListener(InspectContainerResponse containerInfo) {
        return String.format("BROKER://%s:%s", containerInfo.getConfig().getHostName(), "9092");
    }

    private static class KafkaContainerDef extends ContainerDef {

        private final Set<Supplier<String>> listeners = new HashSet<>();

        private String clusterId = DEFAULT_CLUSTER_ID;

        KafkaContainerDef() {
            // Use two listeners with different names, it will force Kafka to communicate with itself via internal
            // listener when KAFKA_INTER_BROKER_LISTENER_NAME is set, otherwise Kafka will try to use the advertised listener
            addEnvVar("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:" + KAFKA_PORT + ",BROKER://0.0.0.0:9092");
            addEnvVar("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT");
            addEnvVar("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER");

            addEnvVar("KAFKA_BROKER_ID", "1");
            addEnvVar("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", DEFAULT_INTERNAL_TOPIC_RF);
            addEnvVar("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", DEFAULT_INTERNAL_TOPIC_RF);
            addEnvVar("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", DEFAULT_INTERNAL_TOPIC_RF);
            addEnvVar("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", DEFAULT_INTERNAL_TOPIC_RF);
            addEnvVar("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", Long.MAX_VALUE + "");
            addEnvVar("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");

            addExposedTcpPort(KAFKA_PORT);

            setEntrypoint("sh");
            setCommand("-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);

            setWaitStrategy(Wait.forLogMessage(".*\\[KafkaServer id=\\d+\\] started.*", 1));
        }

        private void resolveListeners() {
            Set<String> listeners = Arrays
                .stream(this.envVars.get("KAFKA_LISTENERS").get().split(","))
                .collect(Collectors.toSet());
            Set<String> listenerSecurityProtocolMap = Arrays
                .stream(this.envVars.get("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP").get().split(","))
                .collect(Collectors.toSet());

            List<Supplier<String>> listenersToTransform = new ArrayList<>(this.listeners);
            for (int i = 0; i < listenersToTransform.size(); i++) {
                Supplier<String> listenerSupplier = listenersToTransform.get(i);
                String protocol = String.format("%s-%d", PROTOCOL_PREFIX, i);
                String listener = listenerSupplier.get();
                String listenerPort = listener.split(":")[1];
                String listenerProtocol = String.format("%s://0.0.0.0:%s", protocol, listenerPort);
                String protocolMap = String.format("%s:PLAINTEXT", protocol);
                listeners.add(listenerProtocol);
                listenerSecurityProtocolMap.add(protocolMap);

                String host = listener.split(":")[0];
                addNetworkAlias(host);
            }

            String kafkaListeners = String.join(",", listeners);
            String kafkaListenerSecurityProtocolMap = String.join(",", listenerSecurityProtocolMap);

            this.envVars.put("KAFKA_LISTENERS", () -> kafkaListeners);
            this.envVars.put("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", () -> kafkaListenerSecurityProtocolMap);
        }

        void withListener(Supplier<String> listenerSupplier) {
            this.listeners.add(listenerSupplier);
        }

        void withEmbeddedZookeeper() {
            addExposedTcpPort(ZOOKEEPER_PORT);
            addEnvVar("KAFKA_ZOOKEEPER_CONNECT", "localhost:" + ZOOKEEPER_PORT);
        }

        void withZookeeper(String connectionString) {
            addEnvVar("KAFKA_ZOOKEEPER_CONNECT", connectionString);
        }

        void withClusterId(String clusterId) {
            this.clusterId = clusterId;
        }

        void withRaft() {
            this.envVars.computeIfAbsent("CLUSTER_ID", key -> () -> clusterId);
            this.envVars.computeIfAbsent("KAFKA_NODE_ID", key -> getEnvVars().get("KAFKA_BROKER_ID"));
            addEnvVar("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", kafkaListenerSecurityProtocolMap());
            addEnvVar("KAFKA_LISTENERS", kafkaListeners());
            addEnvVar("KAFKA_PROCESS_ROLES", "broker,controller");

            String firstNetworkAlias = getNetworkAliases().stream().findFirst().orElse(null);
            String networkAlias = getNetwork() != null ? firstNetworkAlias : "localhost";
            String controllerQuorumVoters = String.format(
                "%s@%s:9094",
                getEnvVars().get("KAFKA_NODE_ID"),
                networkAlias
            );
            this.envVars.computeIfAbsent("KAFKA_CONTROLLER_QUORUM_VOTERS", key -> () -> controllerQuorumVoters);
            addEnvVar("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER");

            setWaitStrategy(Wait.forLogMessage(".*Transitioning from RECOVERY to RUNNING.*", 1));
        }

        private String kafkaListenerSecurityProtocolMap() {
            Supplier<String> kafkaListenerSecurityProtocolMapEnvVarSupplier = getEnvVars()
                .get("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP");
            String kafkaListenerSecurityProtocolMapEnvVar = kafkaListenerSecurityProtocolMapEnvVarSupplier == null
                ? null
                : kafkaListenerSecurityProtocolMapEnvVarSupplier.get();
            String kafkaListenerSecurityProtocolMap = String.format(
                "%s,CONTROLLER:PLAINTEXT",
                kafkaListenerSecurityProtocolMapEnvVar
            );
            Set<String> listenerSecurityProtocolMap = new HashSet<>(
                Arrays.asList(kafkaListenerSecurityProtocolMap.split(","))
            );
            return String.join(",", listenerSecurityProtocolMap);
        }

        private String kafkaListeners() {
            Supplier<String> kafkaListenersEnvVarSupplier = getEnvVars().get("KAFKA_LISTENERS");
            String kafkaListenersEnvVar = kafkaListenersEnvVarSupplier == null
                ? null
                : kafkaListenersEnvVarSupplier.get();
            String kafkaListeners = String.format("%s,CONTROLLER://0.0.0.0:9094", kafkaListenersEnvVar);
            Set<String> listeners = new HashSet<>(Arrays.asList(kafkaListeners.split(",")));
            return String.join(",", listeners);
        }
    }
}
