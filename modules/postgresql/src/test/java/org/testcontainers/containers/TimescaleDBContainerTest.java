package org.testcontainers.containers;

import org.junit.Test;
import org.testcontainers.db.AbstractContainerDatabaseTest;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class TimescaleDBContainerTest extends AbstractContainerDatabaseTest {

    @Test
    public void testSimple() throws SQLException {
        try (JdbcDatabaseContainer<?> postgres = new TimescaleDBContainerProvider().newInstance()) {
            postgres.start();

            ResultSet resultSet = performQuery(postgres, "SELECT 1");
            int resultSetInt = resultSet.getInt(1);
            assertThat(resultSetInt).as("A basic SELECT query succeeds").isEqualTo(1);
        }
    }

    @Test
    public void testCommandOverride() throws SQLException {
        try (
            GenericContainer<?> postgres = new TimescaleDBContainerProvider()
                .newInstance()
                .withCommand("postgres -c max_connections=42")
        ) {
            postgres.start();

            ResultSet resultSet = performQuery(
                (JdbcDatabaseContainer<?>) postgres,
                "SELECT current_setting('max_connections')"
            );
            String result = resultSet.getString(1);
            assertThat(result).as("max_connections should be overridden").isEqualTo("42");
        }
    }

    @Test
    public void testUnsetCommand() throws SQLException {
        try (
            GenericContainer<?> postgres = new TimescaleDBContainerProvider()
                .newInstance()
                .withCommand("postgres -c max_connections=42")
                .withCommand()
        ) {
            postgres.start();

            ResultSet resultSet = performQuery(
                (JdbcDatabaseContainer<?>) postgres,
                "SELECT current_setting('max_connections')"
            );
            String result = resultSet.getString(1);
            assertThat(result).as("max_connections should not be overridden").isNotEqualTo("42");
        }
    }

    @Test
    public void testExplicitInitScript() throws SQLException {
        try (
            JdbcDatabaseContainer<?> postgres = new TimescaleDBContainerProvider()
                .newInstance()
                .withInitScript("somepath/init_timescaledb.sql")
        ) {
            postgres.start();

            ResultSet resultSet = performQuery(postgres, "SELECT foo FROM bar");

            String firstColumnValue = resultSet.getString(1);
            assertThat(firstColumnValue).as("Value from init script should equal real value").isEqualTo("hello world");
        }
    }
}
