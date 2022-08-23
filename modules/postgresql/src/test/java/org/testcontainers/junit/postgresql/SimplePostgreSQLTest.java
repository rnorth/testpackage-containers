package org.testcontainers.junit.postgresql;

import org.junit.Test;
import org.testcontainers.PostgreSQLTestImages;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.db.AbstractContainerDatabaseTest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static org.assertj.core.api.Assertions.assertThat;

public class SimplePostgreSQLTest extends AbstractContainerDatabaseTest {
    static {
        // Postgres JDBC driver uses JUL; disable it to avoid annoying, irrelevant, stderr logs during connection testing
        LogManager.getLogManager().getLogger("").setLevel(Level.OFF);
    }

    @Test
    public void testSimple() throws SQLException {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLTestImages.POSTGRES_TEST_IMAGE)) {
            postgres.start();

            ResultSet resultSet = performQuery(postgres, "SELECT 1");
            int resultSetInt = resultSet.getInt(1);
            assertThat(resultSetInt).as("A basic SELECT query succeeds").isEqualTo(1);
        }
    }

    @Test
    public void testCommandOverride() throws SQLException {
        try (
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLTestImages.POSTGRES_TEST_IMAGE)
                .withCommand("postgres -c max_connections=42")
        ) {
            postgres.start();

            ResultSet resultSet = performQuery(postgres, "SELECT current_setting('max_connections')");
            String result = resultSet.getString(1);
            assertThat(result).as("max_connections should be overriden").isEqualTo("42");
        }
    }

    @Test
    public void testUnsetCommand() throws SQLException {
        try (
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLTestImages.POSTGRES_TEST_IMAGE)
                .withCommand("postgres -c max_connections=42")
                .withCommand()
        ) {
            postgres.start();

            ResultSet resultSet = performQuery(postgres, "SELECT current_setting('max_connections')");
            String result = resultSet.getString(1);
            assertThat(result).as("max_connections should not be overriden").isNotEqualTo("42");
        }
    }

    @Test
    public void testExplicitInitScript() throws SQLException {
        try (
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLTestImages.POSTGRES_TEST_IMAGE)
                .withInitScript("somepath/init_postgresql.sql")
        ) {
            postgres.start();

            ResultSet resultSet = performQuery(postgres, "SELECT foo FROM bar");

            String firstColumnValue = resultSet.getString(1);
            assertThat(firstColumnValue).as("Value from init script should equal real value").isEqualTo("hello world");
        }
    }

    @Test
    public void testWithAdditionalUrlParamInJdbcUrl() {
        try (
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLTestImages.POSTGRES_TEST_IMAGE)
                .withUrlParam("charSet", "UNICODE")
        ) {
            postgres.start();
            String jdbcUrl = postgres.getJdbcUrl();
            assertThat(jdbcUrl).contains("?");
            assertThat(jdbcUrl).contains("&");
            assertThat(jdbcUrl).contains("charSet=UNICODE");
        }
    }

    @Test
    public void testExposedAndLivenessCheckPorts() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLTestImages.POSTGRES_TEST_IMAGE);) {
            postgres.start();
            assertThat(postgres.getExposedPorts()).containsExactly(PostgreSQLContainer.POSTGRESQL_PORT);
            assertThat(postgres.getLivenessCheckPortNumbers())
                .containsExactly(postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        }
    }
}
