package org.testcontainers.containers;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import lombok.NonNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Testcontainers implementation for PostgreSQL.
 * <p>
 * Supported images: {@code postgres}, {@code pgvector/pgvector}
 * <p>
 * Exposed ports: 5432
 */
public class PostgreSQLContainer<SELF extends PostgreSQLContainer<SELF>> extends JdbcDatabaseContainer<SELF> {

    public static final String NAME = "postgresql";

    public static final String IMAGE = "postgres";

    public static final String DEFAULT_TAG = "9.6.12";

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("postgres");

    private static final DockerImageName PGVECTOR_IMAGE_NAME = DockerImageName.parse("pgvector/pgvector");

    public static final Integer POSTGRESQL_PORT = 5432;

    static final String DEFAULT_USER = "test";

    static final String DEFAULT_PASSWORD = "test";

    private String databaseName = "test";

    private String username = "test";

    private String password = "test";

    private final Map<String, String> CONFIG_OPTIONS = new HashMap<>(Map.of("fsync", "off"));

    /**
     * @deprecated use {@link #PostgreSQLContainer(DockerImageName)} or {@link #PostgreSQLContainer(String)} instead
     */
    @Deprecated
    public PostgreSQLContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    public PostgreSQLContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public PostgreSQLContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME, PGVECTOR_IMAGE_NAME);

        this.waitStrategy =
            new LogMessageWaitStrategy()
                .withRegEx(".*database system is ready to accept connections.*\\s")
                .withTimes(2)
                .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS));

        addExposedPort(POSTGRESQL_PORT);
    }

    /**
     * @return the ports on which to check if the container is ready
     * @deprecated use {@link #getLivenessCheckPortNumbers()} instead
     */
    @NotNull
    @Override
    @Deprecated
    protected Set<Integer> getLivenessCheckPorts() {
        return super.getLivenessCheckPorts();
    }

    @Override
    protected void configure() {
        // Disable Postgres driver use of java.util.logging to reduce noise at startup time
        withUrlParam("loggerLevel", "OFF");
        addEnv("POSTGRES_DB", databaseName);
        addEnv("POSTGRES_USER", username);
        addEnv("POSTGRES_PASSWORD", password);

        // If user never configured a command, and CONFIG_OPTIONS exist, then generate command
        if (this.getContainerDef().getCommand().length == 0 && CONFIG_OPTIONS.size() > 0) {
            this.setCommand("postgres -c " + CONFIG_OPTIONS.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(" -c ")));
        }
    }

    /**
     * Collects a set of configurations that will be set as a startup command of the PostgreSQL container.
     * Note: configuration options will be ignored if a command is set using either withCommand() or setCommand()
     * 
     * <pre>
     * For example:
     *   postgresql.withConfigOption("max_prepared_transactions", "5").withConfigOption("log_destination", "'syslog'");
     * 
     * Will result in the startup command:
     *   postgres -c max_prepared_transactions=5 -c log_destination='syslog'
     * </pre>
     * 
     * @param key The configuration name
     * @param value the configuration value
     * @return self
     */
    public SELF withConfigOption(@NonNull String key, @NonNull String value) {
        if(!key.matches("[a-zA-Z0-9_]")) {
            throw new IllegalArgumentException("PostgreSQL configuration option with key: " + key + " is an invalid configuration string.");
        }
        CONFIG_OPTIONS.put(key, value);
        return self();
    }

    @Override
    public String getDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String getJdbcUrl() {
        String additionalUrlParams = constructUrlParameters("?", "&");
        return (
            "jdbc:postgresql://" +
            getHost() +
            ":" +
            getMappedPort(POSTGRESQL_PORT) +
            "/" +
            databaseName +
            additionalUrlParams
        );
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
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

    @Override
    public SELF withDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
        return self();
    }

    @Override
    public SELF withUsername(final String username) {
        this.username = username;
        return self();
    }

    @Override
    public SELF withPassword(final String password) {
        this.password = password;
        return self();
    }

    @Override
    protected void waitUntilContainerStarted() {
        getWaitStrategy().waitUntilReady(this);
    }
}
