package org.testcontainers.cassandra;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.cassandra.delegate.CassandraDatabaseDelegate;
import org.testcontainers.cassandra.wait.CassandraQueryWaitStrategy;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.ext.ScriptUtils.ScriptLoadException;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
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

    private static final String CONTAINER_CONFIG_LOCATION = "/etc/cassandra";

    private static final String USERNAME = "cassandra";

    private static final String PASSWORD = "cassandra";

    private String configLocation;

    private String initScriptPath;

    private String clientCertFile;

    private String clientKeyFile;

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

        // If a secure connection is required by Cassandra configuration, copy the user certificate and key to a
        // dedicated location and define a cqlshrc file with the appropriate SSL configuration.
        // See: https://docs.datastax.com/en/cassandra-oss/3.x/cassandra/configuration/secureCqlshSSL.html
        if (isSslRequired()) {
            withCopyFileToContainer(MountableFile.forClasspathResource(clientCertFile), "ssl/user_cert.pem");
            withCopyFileToContainer(MountableFile.forClasspathResource(clientKeyFile), "ssl/user_key.pem");
            withCopyFileToContainer(MountableFile.forClasspathResource("cqlshrc"), "/root/.cassandra/cqlshrc");
        }
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
                URL resource = Thread.currentThread().getContextClassLoader().getResource(initScriptPath);
                if (resource == null) {
                    logger().warn("Could not load classpath init script: {}", initScriptPath);
                    throw new ScriptLoadException(
                        "Could not load classpath init script: " + initScriptPath + ". Resource not found."
                    );
                }
                // The init script is executed as is by the cqlsh command, so copy it into the container.
                String targetInitScriptName = new File(resource.toURI()).getName();
                copyFileToContainer(MountableFile.forClasspathResource(initScriptPath), targetInitScriptName);
                new CassandraDatabaseDelegate(this).execute(null, targetInitScriptName, -1, false, false);
            } catch (URISyntaxException e) {
                logger().warn("Could not copy init script into container: {}", initScriptPath);
                throw new ScriptLoadException("Could not copy init script into container: " + initScriptPath, e);
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
     * Cassandra.yaml in configLocation is absent or corrupted, then Cassandra just won't launch.
     *
     * @param configLocation relative classpath with the directory that contains cassandra.yaml and other configuration
     *                       files
     * @return The updated {@link CassandraContainer}.
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
     * @return The updated {@link CassandraContainer}.
     */
    public CassandraContainer withInitScript(String initScriptPath) {
        this.initScriptPath = initScriptPath;
        return self();
    }

    /**
     * Configure secured connection (TLS) when required by the Cassandra configuration
     * (i.e. cassandra.yaml file contains the property {@code client_encryption_options.optional} with value
     * {@code false}).
     *
     * @param clientCertFile The client certificate required to execute CQL scripts.
     * @param clientKeyFile  The client key required to execute CQL scripts.
     * @return The updated {@link CassandraContainer}.
     */
    public CassandraContainer withSsl(String clientCertFile, String clientKeyFile) {
        this.clientCertFile = clientCertFile;
        this.clientKeyFile = clientKeyFile;
        return self();
    }

    /**
     * @return Whether a secure connection is required between the client and the Cassandra server.
     */
    public boolean isSslRequired() {
        return StringUtils.isNoneBlank(this.clientCertFile, this.clientKeyFile);
    }

    /**
     * Get username
     * <p>
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
     * <p>
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
