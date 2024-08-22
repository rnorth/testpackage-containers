package org.testcontainers.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.Test;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.utility.DockerLoggerFactory;
import org.testcontainers.utility.MountableFile;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests of functionality special to the Neo4jContainer.
 */
public class Neo4jContainerTest {

    // See org.testcontainers.utility.LicenseAcceptance#ACCEPTANCE_FILE_NAME
    private static final String ACCEPTANCE_FILE_LOCATION = "/container-license-acceptance.txt";

    @Test
    public void shouldDisableAuthentication() {
        try (
            // spotless:off
            // withoutAuthentication {
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
                .withoutAuthentication()
            // }
            // spotless:on
        ) {
            neo4jContainer.start();
            try (Driver driver = getDriver(neo4jContainer); Session session = driver.session()) {
                long one = session.run("RETURN 1", Collections.emptyMap()).next().get(0).asLong();
                assertThat(one).isEqualTo(1L);
            }
        }
    }

    @Test
    public void shouldCopyDatabase() {
        // no aarch64 image available for Neo4j 3.5
        assumeThat(DockerClientFactory.instance().getInfo().getArchitecture()).isNotEqualTo("aarch64");
        try (
            // copyDatabase {
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:3.5.30")
                .withDatabase(MountableFile.forClasspathResource("/test-graph.db"))
            // }
        ) {
            neo4jContainer.start();
            try (Driver driver = getDriver(neo4jContainer); Session session = driver.session()) {
                Result result = session.run("MATCH (t:Thing) RETURN t");
                assertThat(result.list().stream().map(r -> r.get("t").get("name").asString()))
                    .containsExactlyInAnyOrder("Thing", "Thing 2", "Thing 3", "A box");
            }
        }
    }

    @Test
    public void shouldFailOnCopyDatabaseForDefaultNeo4j4Image() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Neo4jContainer<>().withDatabase(MountableFile.forClasspathResource("/test-graph.db")))
            .withMessage("Copying database folder is not supported for Neo4j instances with version 4.0 or higher.");
    }

    @Test
    public void shouldFailOnCopyDatabaseForCustomNeo4j4Image() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> {
                new Neo4jContainer<>("neo4j:4.4.1").withDatabase(MountableFile.forClasspathResource("/test-graph.db"));
            })
            .withMessage("Copying database folder is not supported for Neo4j instances with version 4.0 or higher.");
    }

    @Test
    public void shouldFailOnCopyDatabaseForCustomNonSemverNeo4j4Image() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> {
                new Neo4jContainer<>("neo4j:latest").withDatabase(MountableFile.forClasspathResource("/test-graph.db"));
            })
            .withMessage("Copying database folder is not supported for Neo4j instances with version 4.0 or higher.");
    }

    @Test
    public void shouldCopyPlugins() {
        try (
            // registerPluginsPath {
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
                .withPlugins(MountableFile.forClasspathResource("/custom-plugins"))
            // }
        ) {
            neo4jContainer.start();
            try (Driver driver = getDriver(neo4jContainer); Session session = driver.session()) {
                assertThatCustomPluginWasCopied(session);
            }
        }
    }

    @Test
    public void shouldCopyPlugin() {
        try (
            // registerPluginsJar {
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
                .withPlugins(MountableFile.forClasspathResource("/custom-plugins/hello-world.jar"))
            // }
        ) {
            neo4jContainer.start();
            try (Driver driver = getDriver(neo4jContainer); Session session = driver.session()) {
                assertThatCustomPluginWasCopied(session);
            }
        }
    }

    private static void assertThatCustomPluginWasCopied(Session session) {
        Result result = session.run("RETURN ac.simons.helloWorld('Testcontainers') AS greeting");
        Record singleRecord = result.single();
        assertThat(singleRecord).isNotNull();
        assertThat(singleRecord.get("greeting").asString()).isEqualTo("Hello, Testcontainers");
    }

    @Test
    public void shouldCheckEnterpriseLicense() {
        assumeThat(Neo4jContainerTest.class.getResource(ACCEPTANCE_FILE_LOCATION)).isNull();

        String expectedImageName = "neo4j:4.4-enterprise";

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> new Neo4jContainer<>("neo4j:4.4").withEnterpriseEdition())
            .withMessageContaining("The image " + expectedImageName + " requires you to accept a license agreement.");
    }

    @Test
    public void shouldRunEnterprise() {
        assumeThat(Neo4jContainerTest.class.getResource(ACCEPTANCE_FILE_LOCATION)).isNotNull();

        try (
            // enterpriseEdition {
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
                .withEnterpriseEdition()
                // }
                .withAdminPassword("Picard123")
        ) {
            neo4jContainer.start();
            try (Driver driver = getDriver(neo4jContainer); Session session = driver.session()) {
                String edition = session
                    .run("CALL dbms.components() YIELD edition RETURN edition", Collections.emptyMap())
                    .next()
                    .get(0)
                    .asString();
                assertThat(edition).isEqualTo("enterprise");
            }
        }
    }

    @Test
    public void shouldAddConfigToEnvironment() {
        // neo4jConfiguration {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
            .withNeo4jConfig("dbms.security.procedures.unrestricted", "apoc.*,algo.*")
            .withNeo4jConfig("dbms.tx_log.rotation.size", "42M");
        // }

        assertThat(neo4jContainer.getEnvMap())
            .containsEntry("NEO4J_dbms_security_procedures_unrestricted", () -> "apoc.*,algo.*");
        assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4J_dbms_tx__log_rotation_size", () -> "42M");
    }

    @Test
    public void shouldRespectEnvironmentAuth() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withEnv("NEO4J_AUTH", "neo4j/secret");

        neo4jContainer.configure();

        assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4J_AUTH", () -> "neo4j/secret");
    }

    @Test
    public void shouldSetCustomPasswordCorrectly() {
        // withAdminPassword {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withAdminPassword("verySecret");
        // }

        neo4jContainer.configure();

        assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4J_AUTH", () -> "neo4j/verySecret");
    }

    @Test
    public void containerAdminPasswordOverrulesEnvironmentAuth() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
            .withEnv("NEO4J_AUTH", "neo4j/secret")
            .withAdminPassword("anotherSecret");

        neo4jContainer.configure();

        assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4J_AUTH", () -> "neo4j/anotherSecret");
    }

    @Test
    public void containerWithoutAuthenticationOverrulesEnvironmentAuth() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
            .withEnv("NEO4J_AUTH", "neo4j/secret")
            .withoutAuthentication();

        neo4jContainer.configure();

        assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4J_AUTH", () -> "none");
    }

    @Test
    public void shouldRespectAlreadyDefinedPortMappingsBolt() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withExposedPorts(7687);

        neo4jContainer.configure();

        assertThat(neo4jContainer.getExposedPorts()).containsExactly(7687);
    }

    @Test
    public void shouldRespectAlreadyDefinedPortMappingsHttp() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withExposedPorts(7474);

        neo4jContainer.configure();

        assertThat(neo4jContainer.getExposedPorts()).containsExactly(7474);
    }

    @Test
    public void shouldRespectAlreadyDefinedPortMappingsWithoutHttps() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withExposedPorts(7687, 7474);

        neo4jContainer.configure();

        assertThat(neo4jContainer.getExposedPorts()).containsExactlyInAnyOrder(7474, 7687);
    }

    @Test
    public void shouldDefaultExportBoltHttpAndHttps() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4");

        neo4jContainer.configure();

        assertThat(neo4jContainer.getExposedPorts()).containsExactlyInAnyOrder(7473, 7474, 7687);
    }

    @Test
    public void shouldRespectCustomWaitStrategy() {
        Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").waitingFor(new CustomDummyWaitStrategy());

        neo4jContainer.configure();

        assertThat(neo4jContainer.getWaitStrategy()).isInstanceOf(CustomDummyWaitStrategy.class);
    }

    // Test for deprecated functionality to be still alive, if `Neo4jLabsPlugin` gets removed, remove this test.
    @Test
    public void shouldConfigureSingleLabsPluginByType() {
        try (
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withLabsPlugins(Neo4jLabsPlugin.APOC)
        ) {
            // needs to get called explicitly for setup
            neo4jContainer.configure();

            assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4JLABS_PLUGINS", () -> "[\"apoc\"]");
        }
    }

    // Test for deprecated functionality to be still alive, if `Neo4jLabsPlugin` gets removed, remove this test.
    @Test
    public void shouldConfigureMultipleLabsPluginsByType() {
        try (
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4")
                .withLabsPlugins(Neo4jLabsPlugin.APOC, Neo4jLabsPlugin.BLOOM);
        ) {
            // needs to get called explicitly for setup
            neo4jContainer.configure();

            assertThat(neo4jContainer.getEnvMap().get("NEO4JLABS_PLUGINS").get())
                .containsAnyOf("[\"apoc\",\"bloom\"]", "[\"bloom\",\"apoc\"]");
        }
    }

    // Test for deprecated functionality to be still alive, if `Neo4jContainer#withLabsPlugins` gets removed, remove this test.
    @Test
    public void shouldConfigureSingleLabsPlugin() {
        try (Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withLabsPlugins("apoc")) {
            // needs to get called explicitly for setup
            neo4jContainer.configure();

            assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4JLABS_PLUGINS", () -> "[\"apoc\"]");
        }
    }

    // Test for deprecated functionality to be still alive, if `Neo4jContainer#withLabsPlugins` gets removed, remove this test.
    @Test
    public void shouldConfigureMultipleLabsPlugins() {
        try (Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withLabsPlugins("apoc", "bloom");) {
            // needs to get called explicitly for setup
            neo4jContainer.configure();

            assertThat(neo4jContainer.getEnvMap().get("NEO4JLABS_PLUGINS").get())
                .containsAnyOf("[\"apoc\",\"bloom\"]", "[\"bloom\",\"apoc\"]");
        }
    }

    @Test
    public void shouldConfigureSinglePluginByName() {
        try (Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withPlugins("apoc")) {
            // needs to get called explicitly for setup
            neo4jContainer.configure();

            assertThat(neo4jContainer.getEnvMap()).containsEntry("NEO4JLABS_PLUGINS", () -> "[\"apoc\"]");
        }
    }

    @Test
    public void shouldConfigureMultiplePluginsByName() {
        try (
            // configureLabsPlugins {
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4") //
                .withPlugins("apoc", "bloom");
            // }
        ) {
            // needs to get called explicitly for setup
            neo4jContainer.configure();

            assertThat(neo4jContainer.getEnvMap().get("NEO4JLABS_PLUGINS").get())
                .containsAnyOf("[\"apoc\",\"bloom\"]", "[\"bloom\",\"apoc\"]");
        }
    }

    @Test
    public void shouldCreateRandomUuidBasedPasswords() {
        try (
            // withRandomPassword {
            Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4").withRandomPassword();
            // }
        ) {
            // It will throw an exception if it's not UUID parsable.
            assertThatNoException().isThrownBy(neo4jContainer::configure);
            // This basically is always true at if the random password is UUID-like.
            assertThat(neo4jContainer.getAdminPassword())
                .satisfies(password -> assertThat(UUID.fromString(password).toString()).isEqualTo(password));
        }
    }

    @Test
    public void shouldWarnOnPasswordTooShort() {
        try (Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:4.4");) {
            Logger logger = (Logger) DockerLoggerFactory.getLogger("neo4j:4.4");
            TestLogAppender testLogAppender = new TestLogAppender();
            logger.addAppender(testLogAppender);
            testLogAppender.start();

            neo4jContainer.withAdminPassword("short");

            testLogAppender.stop();

            assertThat(testLogAppender.passwordTooShortWarningAppeared).isTrue();
        }
    }

    private static class CustomDummyWaitStrategy extends AbstractWaitStrategy {

        @Override
        protected void waitUntilReady() {
            // ehm...ready
        }
    }

    private static class TestLogAppender extends AppenderBase<ILoggingEvent> {

        boolean passwordTooShortWarningAppeared = false;

        @Override
        protected void append(ILoggingEvent eventObject) {
            if (eventObject.getLevel().equals(Level.WARN)) {
                if (
                    eventObject
                        .getMessage()
                        .equals("Your provided admin password is too short and will not work with Neo4j 5.3+.")
                ) {
                    passwordTooShortWarningAppeared = true;
                }
            }
        }
    }

    private static Driver getDriver(Neo4jContainer<?> container) {
        AuthToken authToken = AuthTokens.none();
        if (container.getAdminPassword() != null) {
            authToken = AuthTokens.basic("neo4j", container.getAdminPassword());
        }
        return GraphDatabase.driver(container.getBoltUrl(), authToken);
    }
}
