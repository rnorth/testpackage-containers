package org.testcontainers.clickhouse;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Testcontainers implementation for ClickHouse.
 * <p>
 * Supported image: {@code clickhouse/clickhouse-server}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Database: 8123</li>
 *     <li>Console: 9000</li>
 * </ul>
 */
public class ClickHouseContainer extends JdbcDatabaseContainer<ClickHouseContainer> {

    public static final String NAME = "clickhouse";

    public static final String CLICKHOUSE_CLICKHOUSE_SERVER = "clickhouse/clickhouse-server";

    private static final DockerImageName CLICKHOUSE_IMAGE_NAME = DockerImageName.parse(CLICKHOUSE_CLICKHOUSE_SERVER);

    static final Integer HTTP_PORT = 8123;

    static final Integer NATIVE_PORT = 9000;

    private static final String DRIVER_CLASS_NAME = "com.clickhouse.jdbc.ClickHouseDriver";

    private static final String JDBC_URL_PREFIX = "jdbc:" + NAME + "://";

    private static final String TEST_QUERY = "SELECT 1";

    static final String DEFAULT_USER = "default";

    static final String DEFAULT_PASSWORD = "";

    private String databaseName = "default";

    private String username = DEFAULT_USER;

    private String password = DEFAULT_PASSWORD;

    public ClickHouseContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public ClickHouseContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(CLICKHOUSE_IMAGE_NAME);

        addExposedPorts(HTTP_PORT, NATIVE_PORT);
        waitingFor(
            Wait
                .forHttp("/")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .forResponsePredicate("Ok."::equals)
                .withStartupTimeout(Duration.ofMinutes(1))
        );
    }

    @Override
    protected void configure() {
        withEnv("CLICKHOUSE_DB", this.databaseName);
        withEnv("CLICKHOUSE_USER", this.username);
        withEnv("CLICKHOUSE_PASSWORD", this.password);
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return new HashSet<>(getMappedPort(HTTP_PORT));
    }

    @Override
    public String getDriverClassName() {
        return DRIVER_CLASS_NAME;
    }

    @Override
    public String getJdbcUrl() {
        return (
            JDBC_URL_PREFIX +
            getHost() +
            ":" +
            getMappedPort(HTTP_PORT) +
            "/" +
            this.databaseName +
            constructUrlParameters("?", "&")
        );
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
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String getTestQueryString() {
        return TEST_QUERY;
    }

    @Override
    public ClickHouseContainer withUsername(String username) {
        this.username = username;
        return this;
    }

    @Override
    public ClickHouseContainer withPassword(String password) {
        this.password = password;
        return this;
    }

    @Override
    public ClickHouseContainer withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    @Override
    protected void waitUntilContainerStarted() {
        getWaitStrategy().waitUntilReady(this);
    }
}
