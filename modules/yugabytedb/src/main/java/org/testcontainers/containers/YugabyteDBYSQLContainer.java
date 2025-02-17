package org.testcontainers.containers;

import org.testcontainers.containers.strategy.YugabyteDBYSQLWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Testcontainers implementation for YugabyteDB YSQL API.
 * <p>
 * Supported image: {@code yugabytedb/yugabyte}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>YSQL: 5433</li>
 *     <li>Master dashboard: 7000</li>
 *     <li>Tserver dashboard: 9000</li>
 * </ul>
 *
 * @see <a href="https://docs.yugabyte.com/stable/api/ysql/">YSQL API</a>
 */
public class YugabyteDBYSQLContainer extends JdbcDatabaseContainer<YugabyteDBYSQLContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("yugabytedb/yugabyte");

    private static final Integer YSQL_PORT = 5433;

    private static final Integer MASTER_DASHBOARD_PORT = 7000;

    private static final Integer TSERVER_DASHBOARD_PORT = 9000;

    private static final String JDBC_DRIVER_CLASS = "com.yugabyte.Driver";

    private static final String JDBC_CONNECT_PREFIX = "jdbc:yugabytedb";

    private static final String ENTRYPOINT = "bin/yugabyted start --background=false";

    private String database = "yugabyte";

    private String username = "yugabyte";

    private String password = "yugabyte";

    /**
     * @param imageName image name
     */
    public YugabyteDBYSQLContainer(final String imageName) {
        this(DockerImageName.parse(imageName));
    }

    /**
     * @param imageName image name
     */
    public YugabyteDBYSQLContainer(final DockerImageName imageName) {
        super(imageName);
        imageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        withExposedPorts(YSQL_PORT, MASTER_DASHBOARD_PORT, TSERVER_DASHBOARD_PORT);
        waitingFor(new YugabyteDBYSQLWaitStrategy(this).withStartupTimeout(Duration.ofSeconds(60)));
        withCommand(ENTRYPOINT);
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return Collections.singleton(getMappedPort(YSQL_PORT));
    }

    /**
     * Configures the environment variables. Setting up these variables would create the
     * custom objects. Setting {@link #withDatabaseName(String)},
     * {@link #withUsername(String)}, {@link #withPassword(String)} these parameters will
     * initialize the database with those custom values
     */

    @Override
    protected void configure() {
        addEnv("YSQL_DB", database);
        addEnv("YSQL_USER", username);
        addEnv("YSQL_PASSWORD", password);
    }

    @Override
    public String getDriverClassName() {
        return JDBC_DRIVER_CLASS;
    }

    @Override
    public String getJdbcUrl() {
        return (
            JDBC_CONNECT_PREFIX +
            "://" +
            getHost() +
            ":" +
            getMappedPort(YSQL_PORT) +
            "/" +
            database +
            constructUrlParameters("?", "&")
        );
    }

    @Override
    public String getDatabaseName() {
        return database;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getTestQueryString() {
        return "SELECT 1";
    }

    /**
     * Setting this would create the keyspace
     * @param database database name
     * @return {@link YugabyteDBYSQLContainer} instance
     */

    @Override
    public YugabyteDBYSQLContainer withDatabaseName(final String database) {
        this.database = database;
        return this;
    }

    /**
     * Setting this would create the custom user role
     * @param username user name
     * @return {@link YugabyteDBYSQLContainer} instance
     */

    @Override
    public YugabyteDBYSQLContainer withUsername(final String username) {
        this.username = username;
        return this;
    }

    /**
     * Setting this along with {@link #withUsername(String)} would enable authentication
     * @param password password
     * @return {@link YugabyteDBYSQLContainer} instance
     */

    @Override
    public YugabyteDBYSQLContainer withPassword(final String password) {
        this.password = password;
        return this;
    }

    @Override
    protected void waitUntilContainerStarted() {
        getWaitStrategy().waitUntilReady(this);
    }
}
