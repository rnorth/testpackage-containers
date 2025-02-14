package org.testcontainers.containers;

import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * Testcontainers implementation for Presto.
 * <p>
 * Supported image: {@code prestodb/presto}
 * <p>
 * Exposed ports: 8080
 */
public class PrestoContainer<SELF extends PrestoContainer<SELF>> extends JdbcDatabaseContainer<SELF> {

    public static final String NAME = "presto";

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("prestodb/presto");

    public static final String IMAGE = "prestodb/presto";

    public static final String DEFAULT_TAG = "0.290";

    public static final Integer PRESTO_PORT = 8080;

    private String username = "test";

    private String catalog = null;

    /**
     * @deprecated use {@link #PrestoContainer(DockerImageName)} instead
     */
    @Deprecated
    public PrestoContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    public PrestoContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public PrestoContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        waitingFor(new WaitAllStrategy()
            .withStrategy(Wait.forHttp("/v1/info/state").forPort(8080).forResponsePredicate("\"ACTIVE\""::equals))
            .withStrategy(new PrestoWaitStrategy(this)));
        addExposedPort(PRESTO_PORT);
        withCopyFileToContainer(
            MountableFile.forClasspathResource("default/catalog", Transferable.DEFAULT_DIR_MODE),
            "/opt/presto-server/etc/catalog"
        );
        withCopyFileToContainer(
            MountableFile.forClasspathResource("default/config.properties", Transferable.DEFAULT_DIR_MODE),
            "/opt/presto-server/etc/config.properties"
        );
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
    public String getDriverClassName() {
        return "com.facebook.presto.jdbc.PrestoDriver";
    }

    @Override
    public String getJdbcUrl() {
        return String.format(
            "jdbc:presto://%s:%s/%s",
            getHost(),
            getMappedPort(PRESTO_PORT),
            Strings.nullToEmpty(catalog)
        );
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getDatabaseName() {
        return catalog;
    }

    @Override
    public String getTestQueryString() {
        return "SELECT count(*) FROM tpch.tiny.nation";
    }

    @Override
    public SELF withUsername(final String username) {
        this.username = username;
        return self();
    }

    /**
     * @deprecated This operation is not supported.
     */
    @Override
    @Deprecated
    public SELF withPassword(final String password) {
        // ignored, Presto does not support password authentication without TLS
        // TODO: make JDBCDriverTest not pass a password unconditionally and remove this method
        return self();
    }

    @Override
    public SELF withDatabaseName(String dbName) {
        this.catalog = dbName;
        return self();
    }

    public Connection createConnection() throws SQLException, NoDriverFoundException {
        return createConnection("");
    }
}
