package org.testcontainers.containers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.delegate.DatabaseDelegate;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Base class for containers that expose a JDBC connection
 */
public abstract class JdbcDatabaseContainer<SELF extends JdbcDatabaseContainer<SELF>>
    extends GenericContainer<SELF>
    implements LinkableContainer {

    private static final Object DRIVER_LOAD_MUTEX = new Object();

    private Driver driver;

    private List<String> initScriptPaths = new ArrayList<>();

    protected Map<String, String> parameters = new HashMap<>();

    protected Map<String, String> urlParameters = new HashMap<>();

    private int startupTimeoutSeconds = 120;

    private int connectTimeoutSeconds = 120;

    private static final String QUERY_PARAM_SEPARATOR = "&";

    /**
     * @deprecated use {@link #JdbcDatabaseContainer(DockerImageName)} instead
     */
    public JdbcDatabaseContainer(@NonNull final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public JdbcDatabaseContainer(@NonNull final Future<String> image) {
        super(image);
    }

    public JdbcDatabaseContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    /**
     * @return the name of the actual JDBC driver to use
     */
    public abstract String getDriverClassName();

    /**
     * @return a JDBC URL that may be used to connect to the dockerized DB
     */
    public abstract String getJdbcUrl();

    /**
     * @return the database name
     */
    public String getDatabaseName() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the standard database username that should be used for connections
     */
    public abstract String getUsername();

    /**
     * @return the standard password that should be used for connections
     */
    public abstract String getPassword();

    /**
     * @return a test query string suitable for testing that this particular database type is alive
     */
    protected abstract String getTestQueryString();

    public SELF withUsername(String username) {
        throw new UnsupportedOperationException();
    }

    public SELF withPassword(String password) {
        throw new UnsupportedOperationException();
    }

    public SELF withDatabaseName(String dbName) {
        throw new UnsupportedOperationException();
    }

    public SELF withUrlParam(String paramName, String paramValue) {
        urlParameters.put(paramName, paramValue);
        return self();
    }

    /**
     * Set startup time to allow, including image pull time, in seconds.
     *
     * @param startupTimeoutSeconds startup time to allow, including image pull time, in seconds
     * @return self
     */
    public SELF withStartupTimeoutSeconds(int startupTimeoutSeconds) {
        this.startupTimeoutSeconds = startupTimeoutSeconds;
        return self();
    }

    /**
     * Set time to allow for the database to start and establish an initial connection, in seconds.
     *
     * @param connectTimeoutSeconds time to allow for the database to start and establish an initial connection in seconds
     * @return self
     */
    public SELF withConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        return self();
    }

    /**
     * Sets a script for initialization.
     *
     * @param initScriptPath path to the script file
     * @return self
     */
    public SELF withInitScript(String initScriptPath) {
        this.initScriptPaths = new ArrayList<>();
        this.initScriptPaths.add(initScriptPath);
        return self();
    }

    /**
     * Sets an ordered array of scripts for initialization.
     *
     * @param initScriptPaths paths to the script files
     * @return self
     */
    public SELF withInitScripts(String... initScriptPaths) {
        return withInitScripts(Arrays.asList(initScriptPaths));
    }

    /**
     * Sets an ordered collection of scripts for initialization.
     *
     * @param initScriptPaths paths to the script files
     * @return self
     */
    public SELF withInitScripts(Iterable<String> initScriptPaths) {
        this.initScriptPaths = new ArrayList<>();
        initScriptPaths.forEach(this.initScriptPaths::add);
        return self();
    }

    @SneakyThrows(InterruptedException.class)
    @Override
    protected void waitUntilContainerStarted() {
        super.waitUntilContainerStarted();

        logger()
            .info(
                "Waiting for database connection to become available at {} using query '{}'",
                getJdbcUrl(),
                getTestQueryString()
            );

        // Repeatedly try and open a connection to the DB and execute a test query
        long start = System.nanoTime();

        Exception lastConnectionException = null;
        while ((System.nanoTime() - start) < TimeUnit.SECONDS.toNanos(startupTimeoutSeconds)) {
            if (!isRunning()) {
                Thread.sleep(100L);
            } else {
                try (Connection connection = createConnection(""); Statement statement = connection.createStatement()) {
                    boolean testQuerySucceeded = statement.execute(this.getTestQueryString());
                    if (testQuerySucceeded) {
                        return;
                    }
                } catch (NoDriverFoundException e) {
                    // we explicitly want this exception to fail fast without retries
                    throw e;
                } catch (Exception e) {
                    lastConnectionException = e;
                    // ignore so that we can try again
                    logger().debug("Failure when trying test query", e);
                    Thread.sleep(100L);
                }
            }
        }

        throw new IllegalStateException(
            String.format(
                "Container is started, but cannot be accessed by (JDBC URL: %s), please check container logs",
                this.getJdbcUrl()
            ),
            lastConnectionException
        );
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        logger().info("Container is started (JDBC URL: {})", this.getJdbcUrl());
        runInitScriptIfRequired();
    }

    /**
     * Obtain an instance of the correct JDBC driver for this particular database container type
     *
     * @return a JDBC Driver
     */
    public Driver getJdbcDriverInstance() throws NoDriverFoundException {
        synchronized (DRIVER_LOAD_MUTEX) {
            if (driver == null) {
                try {
                    driver = (Driver) Class.forName(this.getDriverClassName()).newInstance();
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    throw new NoDriverFoundException("Could not get Driver", e);
                }
            }
        }

        return driver;
    }

    /**
     * Creates a connection to the underlying containerized database instance.
     *
     * @param queryString query string parameters that should be appended to the JDBC connection URL.
     *                    The '?' character must be included
     * @return a Connection
     * @throws SQLException if there is a repeated failure to create the connection
     */
    public Connection createConnection(String queryString) throws SQLException, NoDriverFoundException {
        return createConnection(queryString, new Properties());
    }

    /**
     * Creates a connection to the underlying containerized database instance.
     *
     * @param queryString query string parameters that should be appended to the JDBC connection URL.
     *                    The '?' character must be included
     * @param info  additional properties to be passed to the JDBC driver
     * @return a Connection
     * @throws SQLException if there is a repeated failure to create the connection
     */
    public Connection createConnection(String queryString, Properties info)
        throws SQLException, NoDriverFoundException {
        Properties properties = new Properties(info);
        properties.put("user", this.getUsername());
        properties.put("password", this.getPassword());
        final String url = constructUrlForConnection(queryString);

        final Driver jdbcDriverInstance = getJdbcDriverInstance();

        SQLException lastException = null;
        try {
            long start = System.nanoTime();
            // give up if we hit the time limit or the container stops running for some reason
            while ((System.nanoTime() - start < TimeUnit.SECONDS.toNanos(connectTimeoutSeconds)) && isRunning()) {
                try {
                    logger()
                        .debug(
                            "Trying to create JDBC connection using {} to {} with properties: {}",
                            jdbcDriverInstance.getClass().getName(),
                            url,
                            properties
                        );

                    return jdbcDriverInstance.connect(url, properties);
                } catch (SQLException e) {
                    lastException = e;
                    Thread.sleep(100L);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new SQLException("Could not create new connection", lastException);
    }

    /**
     * Template method for constructing the JDBC URL to be used for creating {@link Connection}s.
     * This should be overridden if the JDBC URL and query string concatenation or URL string
     * construction needs to be different to normal.
     *
     * @param queryString query string parameters that should be appended to the JDBC connection URL.
     *                    The '?' character must be included
     * @return a full JDBC URL including queryString
     */
    protected String constructUrlForConnection(String queryString) {
        String baseUrl = getJdbcUrl();

        if ("".equals(queryString)) {
            return baseUrl;
        }

        if (!queryString.startsWith("?")) {
            throw new IllegalArgumentException("The '?' character must be included");
        }

        return baseUrl.contains("?")
            ? baseUrl + QUERY_PARAM_SEPARATOR + queryString.substring(1)
            : baseUrl + queryString;
    }

    protected String constructUrlParameters(String startCharacter, String delimiter) {
        return constructUrlParameters(startCharacter, delimiter, StringUtils.EMPTY);
    }

    protected String constructUrlParameters(String startCharacter, String delimiter, String endCharacter) {
        String urlParameters = "";
        if (!this.urlParameters.isEmpty()) {
            String additionalParameters =
                this.urlParameters.entrySet().stream().map(Object::toString).collect(Collectors.joining(delimiter));
            urlParameters = startCharacter + additionalParameters + endCharacter;
        }
        return urlParameters;
    }

    @Deprecated
    protected void optionallyMapResourceParameterAsVolume(
        @NotNull String paramName,
        @NotNull String pathNameInContainer,
        @NotNull String defaultResource
    ) {
        optionallyMapResourceParameterAsVolume(paramName, pathNameInContainer, defaultResource, null);
    }

    protected void optionallyMapResourceParameterAsVolume(
        @NotNull String paramName,
        @NotNull String pathNameInContainer,
        @NotNull String defaultResource,
        @Nullable Integer fileMode
    ) {
        String resourceName = parameters.getOrDefault(paramName, defaultResource);

        if (resourceName != null) {
            final MountableFile mountableFile = MountableFile.forClasspathResource(resourceName, fileMode);
            withCopyFileToContainer(mountableFile, pathNameInContainer);
        }
    }

    /**
     * Load init script content and apply it to the database if initScriptPath is set
     */
    protected void runInitScriptIfRequired() {
        initScriptPaths
            .stream()
            .filter(Objects::nonNull)
            .forEach(path -> ScriptUtils.runInitScript(getDatabaseDelegate(), path));
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    @SuppressWarnings("unused")
    public void addParameter(String paramName, String value) {
        this.parameters.put(paramName, value);
    }

    /**
     * @return startup time to allow, including image pull time, in seconds
     * @deprecated should not be overridden anymore, use {@link #withStartupTimeoutSeconds(int)} in constructor instead
     */
    @Deprecated
    protected int getStartupTimeoutSeconds() {
        return startupTimeoutSeconds;
    }

    /**
     * @return time to allow for the database to start and establish an initial connection, in seconds
     * @deprecated should not be overridden anymore, use {@link #withConnectTimeoutSeconds(int)} in constructor instead
     */
    @Deprecated
    protected int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    protected DatabaseDelegate getDatabaseDelegate() {
        return new JdbcDatabaseDelegate(this, "");
    }

    public static class NoDriverFoundException extends RuntimeException {

        public NoDriverFoundException(String message, Throwable e) {
            super(message, e);
        }
    }
}
