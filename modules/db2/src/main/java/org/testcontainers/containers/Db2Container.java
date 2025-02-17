package org.testcontainers.containers;

import com.github.dockerjava.api.model.Capability;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LicenseAcceptance;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Testcontainers implementation for IBM DB2.
 * <p>
 * Supported images: {@code icr.io/db2_community/db2}, {@code ibmcom/db2}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Database: 50000</li>
 * </ul>
 */
public class Db2Container extends JdbcDatabaseContainer<Db2Container> {

    public static final String NAME = "db2";

    @Deprecated
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("ibmcom/db2");

    private static final DockerImageName DEFAULT_NEW_IMAGE_NAME = DockerImageName.parse("icr.io/db2_community/db2");

    @Deprecated
    public static final String DEFAULT_DB2_IMAGE_NAME = DEFAULT_IMAGE_NAME.getUnversionedPart();

    @Deprecated
    public static final String DEFAULT_TAG = "11.5.0.0a";

    public static final int DB2_PORT = 50000;

    private String databaseName = "test";

    private String username = "db2inst1";

    private String password = "foobar1234";

    /**
     * @deprecated use {@link #Db2Container(DockerImageName)} instead
     */
    @Deprecated
    public Db2Container() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    public Db2Container(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public Db2Container(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_NEW_IMAGE_NAME, DEFAULT_IMAGE_NAME);

        withCreateContainerCmdModifier(cmd -> cmd.withCapAdd(Capability.IPC_LOCK).withCapAdd(Capability.IPC_OWNER));
        this.waitStrategy =
            new LogMessageWaitStrategy()
                .withRegEx(".*Setup has completed\\..*")
                .withStartupTimeout(Duration.of(10, ChronoUnit.MINUTES));

        addExposedPort(DB2_PORT);
    }

    /**
     * @return the ports on which to check if the container is ready
     * @deprecated use {@link #getLivenessCheckPortNumbers()} instead
     */
    @Override
    @Deprecated
    protected Set<Integer> getLivenessCheckPorts() {
        return super.getLivenessCheckPorts();
    }

    @Override
    protected void configure() {
        // If license was not accepted programmatically, check if it was accepted via resource file
        if (!getEnvMap().containsKey("LICENSE")) {
            LicenseAcceptance.assertLicenseAccepted(this.getDockerImageName());
            acceptLicense();
        }

        addEnv("DBNAME", databaseName);
        addEnv("DB2INSTANCE", username);
        addEnv("DB2INST1_PASSWORD", password);

        // These settings help the DB2 container start faster
        if (!getEnvMap().containsKey("AUTOCONFIG")) {
            addEnv("AUTOCONFIG", "false");
        }
        if (!getEnvMap().containsKey("ARCHIVE_LOGS")) {
            addEnv("ARCHIVE_LOGS", "false");
        }
    }

    /**
     * Accepts the license for the DB2 container by setting the LICENSE=accept
     * variable as described at <a href="https://hub.docker.com/r/ibmcom/db2">https://hub.docker.com/r/ibmcom/db2</a>
     */
    public Db2Container acceptLicense() {
        addEnv("LICENSE", "accept");
        return this;
    }

    @Override
    public String getDriverClassName() {
        return "com.ibm.db2.jcc.DB2Driver";
    }

    @Override
    public String getJdbcUrl() {
        String additionalUrlParams = constructUrlParameters(":", ";", ";");
        return "jdbc:db2://" + getHost() + ":" + getMappedPort(DB2_PORT) + "/" + databaseName + additionalUrlParams;
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
    public Db2Container withUsername(String username) {
        this.username = username;
        return this;
    }

    @Override
    public Db2Container withPassword(String password) {
        this.password = password;
        return this;
    }

    @Override
    public Db2Container withDatabaseName(String dbName) {
        this.databaseName = dbName;
        return this;
    }

    @Override
    protected void waitUntilContainerStarted() {
        getWaitStrategy().waitUntilReady(this);
    }

    @Override
    protected String getTestQueryString() {
        return "SELECT 1 FROM SYSIBM.SYSDUMMY1";
    }
}
