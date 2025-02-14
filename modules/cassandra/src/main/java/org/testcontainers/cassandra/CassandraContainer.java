package org.testcontainers.cassandra;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.cassandra.delegate.CassandraDatabaseDelegate;
import org.testcontainers.cassandra.wait.CassandraQueryWaitStrategy;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.ext.ScriptUtils.ScriptLoadException;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Testcontainers implementation for Apache Cassandra.
 * <p>
 * Supported image: {@code cassandra}
 * <p>
 * Exposed ports: 9042
 */
public class CassandraContainer extends GenericContainer<CassandraContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("cassandra");

    private static final Integer CQL_PORT = 9042;

    private static final String DEFAULT_LOCAL_DATACENTER = "datacenter1";

    private static final String DEFAULT_INIT_SCRIPT_FILENAME = "init.cql";

    private static final String CONTAINER_CONFIG_LOCATION = "/etc/cassandra";

    private static final String USERNAME = "cassandra";

    private static final String PASSWORD = "cassandra";

    private String configLocation;

    private String initScriptPath;

    public CassandraContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public CassandraContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        addExposedPort(CQL_PORT);

        withEnv("CASSANDRA_SNITCH", "GossipingPropertyFileSnitch");
        withEnv("JVM_OPTS", "-Dcassandra.skip_wait_for_gossip_to_settle=0 -Dcassandra.initial_token=0");
        withEnv("HEAP_NEWSIZE", "128M");
        withEnv("MAX_HEAP_SIZE", "1024M");
        withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch");
        withEnv("CASSANDRA_DC", DEFAULT_LOCAL_DATACENTER);

        // Use the CassandraQueryWaitStrategy by default to avoid potential issues when the authentication is enabled.
        waitingFor(new CassandraQueryWaitStrategy());
    }

    @Override
    protected void configure() {
        // Map (effectively replace) directory in Docker with the content of resourceLocation if resource location is
        // not null.
        Optional
            .ofNullable(configLocation)
            .map(MountableFile::forClasspathResource)
            .ifPresent(mountableFile -> withCopyFileToContainer(mountableFile, CONTAINER_CONFIG_LOCATION));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        runInitScriptIfRequired();
    }

    /**
     * Load init script content and apply it to the database if initScriptPath is set
     */
    private void runInitScriptIfRequired() {
        if (initScriptPath != null) {
            try {
                final MountableFile originalInitScript = MountableFile.forClasspathResource(initScriptPath);
                // The init script is executed as is by the cqlsh command, so copy it into the container. The name
                // of the script is generic since it's not important to keep the original name.
                copyFileToContainer(originalInitScript, DEFAULT_INIT_SCRIPT_FILENAME);
                new CassandraDatabaseDelegate(this).execute(null, DEFAULT_INIT_SCRIPT_FILENAME, -1, false, false);
            } catch (IllegalArgumentException e) {
                // MountableFile.forClasspathResource will throw an IllegalArgumentException if the resource cannot
                // be found.
                logger().warn("Could not load classpath init script: {}", initScriptPath);
                throw new ScriptLoadException(
                    "Could not load classpath init script: " + initScriptPath + ". Resource not found.", e);
            } catch (ScriptUtils.ScriptStatementFailedException e) {
                logger().error("Error while executing init script: {}", initScriptPath, e);
                throw new ScriptUtils.UncategorizedScriptException(
                    "Error while executing init script: " + initScriptPath,
                    e
                );
            }
        }
    }

    /**
     * Initialize Cassandra with the custom overridden Cassandra configuration
     * <p>
     * Be aware, that Docker effectively replaces all /etc/cassandra content with the content of config location, so if
     * Cassandra.yaml in configLocation is absent or corrupted, then Cassandra just won't launch
     *
     * @param configLocation relative classpath with the directory that contains cassandra.yaml and other configuration files
     */
    public CassandraContainer withConfigurationOverride(String configLocation) {
        this.configLocation = configLocation;
        return self();
    }

    /**
     * Initialize Cassandra with init CQL script
     * <p>
     *     CQL script will be applied after container is started (see using WaitStrategy).
     * </p>
     *
     * @param initScriptPath relative classpath resource
     */
    public CassandraContainer withInitScript(String initScriptPath) {
        this.initScriptPath = initScriptPath;
        return self();
    }

    /**
     * Get username
     *
     * By default, Cassandra has authenticator: AllowAllAuthenticator in cassandra.yaml
     * If username and password need to be used, then authenticator should be set as PasswordAuthenticator
     * (through custom Cassandra configuration) and through CQL with default cassandra-cassandra credentials
     * user management should be modified
     */
    public String getUsername() {
        return USERNAME;
    }

    /**
     * Get password
     *
     * By default, Cassandra has authenticator: AllowAllAuthenticator in cassandra.yaml
     * If username and password need to be used, then authenticator should be set as PasswordAuthenticator
     * (through custom Cassandra configuration) and through CQL with default cassandra-cassandra credentials
     * user management should be modified
     */
    public String getPassword() {
        return PASSWORD;
    }

    /**
     * Retrieve an {@link InetSocketAddress} for connecting to the Cassandra container via the driver.
     *
     * @return A InetSocketAddress representation of this Cassandra container's host and port.
     */
    public InetSocketAddress getContactPoint() {
        return new InetSocketAddress(getHost(), getMappedPort(CQL_PORT));
    }

    /**
     * Retrieve the Local Datacenter for connecting to the Cassandra container via the driver.
     *
     * @return The configured local Datacenter name.
     */
    public String getLocalDatacenter() {
        return getEnvMap().getOrDefault("CASSANDRA_DC", DEFAULT_LOCAL_DATACENTER);
    }
}
