package org.testcontainers.containers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

/**
 * Constructs a single node MongoDB replica set for testing transactions etc. Please, use MongoDBContainer's methods to construct a URL connection string.
 * <p>To construct a multi-node MongoDB cluster, consider the <a href="https://github.com/silaev/mongodb-replica-set/">mongodb-replica-set project on GitHub</a>
 * <p>Tested on a MongoDB version 4.0.10+ (that is the default version if not specified).
 */
@Slf4j
public class MongoDBContainer extends GenericContainer<MongoDBContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("mongo");

    private static final String DEFAULT_TAG = "4.0.10";

    private static final int CONTAINER_EXIT_CODE_OK = 0;

    private static final int MONGODB_INTERNAL_PORT = 27017;

    private static final int AWAIT_INIT_REPLICA_SET_ATTEMPTS = 60;

    static final String DEFAULT_DATABASE_NAME = "test";

    private static final String DEFAULT_USER = "test";

    private static final String DEFAULT_PASSWORD = "test";

    static final String DEFAULT_AUTHENTICATION_DATABASE_NAME = "admin";

    private static final String AUTHENTICATION_KEY_FILE_NAME = "keyFile.key";

    private static final String AUTHENTICATION_KEY_FILE_NAME_CONTAINER_PATH =
        "/usr/local/bin/" + AUTHENTICATION_KEY_FILE_NAME;

    private String username = DEFAULT_USER;

    private String password = DEFAULT_PASSWORD;

    /**
     * @deprecated use {@link MongoDBContainer(DockerImageName)} instead
     */
    @Deprecated
    public MongoDBContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    public MongoDBContainer(@NonNull final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public MongoDBContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        withExposedPorts(MONGODB_INTERNAL_PORT);
        withClasspathResourceMapping(
            AUTHENTICATION_KEY_FILE_NAME,
            AUTHENTICATION_KEY_FILE_NAME_CONTAINER_PATH,
            BindMode.READ_ONLY
        );
        waitingFor(Wait.forLogMessage("(?i).*waiting for connections.*", 2));
    }

    @Override
    protected void configure() {
        addEnv("MONGO_INITDB_ROOT_USERNAME", this.username);
        addEnv("MONGO_INITDB_ROOT_PASSWORD", this.password);
        withCreateContainerCmdModifier(it -> it.withEntrypoint("bash"));
        setCommand(
            "-c",
            "chown mongodb " +
            AUTHENTICATION_KEY_FILE_NAME_CONTAINER_PATH +
            ";chmod 400 " +
            AUTHENTICATION_KEY_FILE_NAME_CONTAINER_PATH +
            ";/usr/local/bin/docker-entrypoint.sh --keyFile " +
            AUTHENTICATION_KEY_FILE_NAME_CONTAINER_PATH +
            " --replSet docker-rs"
        );
    }

    public MongoDBContainer withUsername(final String username) {
        this.username = username;
        return self();
    }

    public MongoDBContainer withPassword(final String password) {
        this.password = password;
        return self();
    }

    /**
     * Gets a connection string url, unlike {@link #getReplicaSetUrl} this does point to a
     * database
     * @return a connection url pointing to a mongodb instance
     */
    public String getConnectionString() {
        return constructConnectionString(
            ConnectionString.builder().username(this.username).password(this.password).build()
        );
    }

    /**
     * Gets a replica set url for the default {@value #DEFAULT_DATABASE_NAME} database.
     *
     * @return a replica set url.
     */
    public String getReplicaSetUrl() {
        return getReplicaSetUrl(ConnectionString.builder().username(this.username).password(this.password).build());
    }

    /**
     * Gets a replica set url for a provided <code>databaseName</code>.
     *
     * @param databaseName a database name.
     * @return a replica set url.
     */
    public String getReplicaSetUrl(final String databaseName) {
        if (!isRunning()) {
            throw new IllegalStateException("MongoDBContainer should be started first");
        }
        return constructConnectionString(
            ConnectionString
                .builder()
                .databaseName(databaseName)
                .username(this.username)
                .password(this.password)
                .build()
        );
    }

    /**
     * Gets a replica set url for a provided {@link org.testcontainers.containers.MongoDBContainer.ConnectionString}.
     *
     * @param connectionString an object describing a connection string.
     * @return a replica set url.
     */
    public String getReplicaSetUrl(final ConnectionString connectionString) {
        if (!isRunning()) {
            throw new IllegalStateException("MongoDBContainer should be started first");
        }
        return constructConnectionString(connectionString);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo, boolean reused) {
        if (reused && isReplicationSetAlreadyInitialized()) {
            log.debug("Replica set already initialized.");
        } else {
            initReplicaSet();
        }
    }

    private String[] buildMongoEvalCommand(final String command) {
        final String authOptions =
            " -u " +
            this.username +
            " -p " +
            this.password +
            " --authenticationDatabase " +
            DEFAULT_AUTHENTICATION_DATABASE_NAME;
        return new String[] {
            "sh",
            "-c",
            "mongosh " +
            authOptions +
            " --eval \"" +
            command +
            "\"  || mongo " +
            authOptions +
            " --eval \"" +
            command +
            "\"",
        };
    }

    private void checkMongoNodeExitCode(final Container.ExecResult execResult) {
        if (execResult.getExitCode() != CONTAINER_EXIT_CODE_OK) {
            final String errorMessage = String.format("An error occurred: %s", execResult.getStdout());
            log.error(errorMessage);
            throw new ReplicaSetInitializationException(errorMessage);
        }
    }

    private String buildMongoWaitCommand() {
        return String.format(
            "var attempt = 0; " +
            "while" +
            "(%s) " +
            "{ " +
            "if (attempt > %d) {quit(1);} " +
            "print('%s ' + attempt); sleep(100);  attempt++; " +
            " }",
            "db.runCommand( { isMaster: 1 } ).ismaster==false",
            AWAIT_INIT_REPLICA_SET_ATTEMPTS,
            "An attempt to await for a single node replica set initialization:"
        );
    }

    private void checkMongoNodeExitCodeAfterWaiting(final Container.ExecResult execResultWaitForMaster) {
        if (execResultWaitForMaster.getExitCode() != CONTAINER_EXIT_CODE_OK) {
            final String errorMessage = String.format(
                "A single node replica set was not initialized in a set timeout: %d attempts",
                AWAIT_INIT_REPLICA_SET_ATTEMPTS
            );
            log.error(errorMessage);
            throw new ReplicaSetInitializationException(errorMessage);
        }
    }

    @SneakyThrows(value = { IOException.class, InterruptedException.class })
    private void initReplicaSet() {
        log.debug("Initializing a single node node replica set...");
        final ExecResult execResultInitRs = execInContainer(buildMongoEvalCommand("rs.initiate();"));
        log.debug(execResultInitRs.getStdout());
        checkMongoNodeExitCode(execResultInitRs);

        log.debug(
            "Awaiting for a single node replica set initialization up to {} attempts",
            AWAIT_INIT_REPLICA_SET_ATTEMPTS
        );
        final ExecResult execResultWaitForMaster = execInContainer(buildMongoEvalCommand(buildMongoWaitCommand()));
        log.debug(execResultWaitForMaster.getStdout());

        checkMongoNodeExitCodeAfterWaiting(execResultWaitForMaster);
    }

    private String constructConnectionString(final ConnectionString connectionString) {
        return String.format(
            "mongodb://%s:%s@%s:%d/%s?authSource=%s",
            connectionString.getUsername(),
            connectionString.getPassword(),
            getHost(),
            getMappedPort(MONGODB_INTERNAL_PORT),
            connectionString.getDatabaseName(),
            DEFAULT_AUTHENTICATION_DATABASE_NAME
        );
    }

    public static class ReplicaSetInitializationException extends RuntimeException {

        ReplicaSetInitializationException(final String errorMessage) {
            super(errorMessage);
        }
    }

    @SneakyThrows
    private boolean isReplicationSetAlreadyInitialized() {
        // since we are creating a replica set with one node, this node must be primary (state = 1)
        final ExecResult execCheckRsInit = execInContainer(
            buildMongoEvalCommand("if(db.adminCommand({replSetGetStatus: 1})['myState'] != 1) quit(900)")
        );
        return execCheckRsInit.getExitCode() == CONTAINER_EXIT_CODE_OK;
    }

    @Builder
    @Getter
    public static class ConnectionString {

        @Builder.Default
        private final String databaseName = DEFAULT_DATABASE_NAME;

        private final String username;

        private final String password;
    }
}
