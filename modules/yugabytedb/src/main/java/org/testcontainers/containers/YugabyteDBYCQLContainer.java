package org.testcontainers.containers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.delegate.YugabyteDBYCQLDelegate;
import org.testcontainers.containers.strategy.YugabyteDBYCQLWaitStrategy;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Testcontainers implementation for YugabyteDB YCQL API.
 * <p>
 * Supported image: {@code yugabytedb/yugabyte}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>YCQL: 5433</li>
 *     <li>Master dashboard: 7000</li>
 *     <li>Tserver dashboard: 9000</li>
 * </ul>
 *
 * @see <a href="https://docs.yugabyte.com/stable/api/ycql/">YCQL API</a>
 */
public class YugabyteDBYCQLContainer extends GenericContainer<YugabyteDBYCQLContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("yugabytedb/yugabyte");

    private static final Integer YCQL_PORT = 9042;

    private static final Integer MASTER_DASHBOARD_PORT = 7000;

    private static final Integer TSERVER_DASHBOARD_PORT = 9000;

    private static final String ENTRYPOINT = "bin/yugabyted start --background=false";

    private static final String LOCAL_DC = "datacenter1";

    private String keyspace;

    private String username;

    private String password;

    private String initScript;

    /**
     * @param imageName image name
     */
    public YugabyteDBYCQLContainer(final String imageName) {
        this(DockerImageName.parse(imageName));
    }

    /**
     * @param imageName image name
     */
    public YugabyteDBYCQLContainer(final DockerImageName imageName) {
        super(imageName);
        imageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        withExposedPorts(YCQL_PORT, MASTER_DASHBOARD_PORT, TSERVER_DASHBOARD_PORT);
        waitingFor(new YugabyteDBYCQLWaitStrategy(this).withStartupTimeout(Duration.ofSeconds(60)));
        withCommand(ENTRYPOINT);
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return Collections.singleton(getMappedPort(YCQL_PORT));
    }

    /**
     * Configures the environment variables. Setting up these variables would create the
     * custom objects. Setting {@link #withKeyspaceName(String)},
     * {@link #withUsername(String)}, {@link #withPassword(String)} these parameters will
     * initialize the database with those custom values
     */
    @Override
    protected void configure() {
        addEnv("YCQL_KEYSPACE", keyspace);
        addEnv("YCQL_USER", username);
        addEnv("YCQL_PASSWORD", password);
    }

    /**
     * @param initScript path of the initialization script file
     * @return {@link YugabyteDBYCQLContainer} instance
     */
    public YugabyteDBYCQLContainer withInitScript(String initScript) {
        this.initScript = initScript;
        return this;
    }

    /**
     * Setting this would create the keyspace
     * @param keyspace keyspace
     * @return {@link YugabyteDBYCQLContainer} instance
     */
    public YugabyteDBYCQLContainer withKeyspaceName(final String keyspace) {
        this.keyspace = keyspace;
        return this;
    }

    /**
     * Setting this would create the custom user role
     * @param username user name
     * @return {@link YugabyteDBYCQLContainer} instance
     */
    public YugabyteDBYCQLContainer withUsername(final String username) {
        this.username = username;
        return this;
    }

    /**
     * Setting this along with {@link #withUsername(String)} would enable authentication
     * @param password password
     * @return {@link YugabyteDBYCQLContainer} instance
     */
    public YugabyteDBYCQLContainer withPassword(final String password) {
        this.password = password;
        return this;
    }

    /**
     * Executes the initialization script
     * @param containerInfo containerInfo
     */
    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (this.initScript != null) {
            ScriptUtils.runInitScript(new YugabyteDBYCQLDelegate(this), initScript);
        }
    }

    /**
     * Returns a {@link InetSocketAddress} representation of YCQL's contact point info
     * @return contactpoint
     */
    public InetSocketAddress getContactPoint() {
        return new InetSocketAddress(getHost(), getMappedPort(YCQL_PORT));
    }

    /**
     * Returns the local datacenter name
     * @return localdc name
     */
    public String getLocalDc() {
        return LOCAL_DC;
    }

    /**
     * Username getter method
     * @return username
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Password getter method
     * @return password
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Keyspace getter method
     * @return keyspace
     */
    public String getKeyspace() {
        return this.keyspace;
    }
}
