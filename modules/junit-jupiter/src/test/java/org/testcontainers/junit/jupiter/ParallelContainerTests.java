package org.testcontainers.junit.jupiter;

import static org.junit.Assert.assertEquals;
import static org.testcontainers.junit.jupiter.JUnitJupiterTestImages.POSTGRES_IMAGE;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.PostgreSQLContainer;

@Testcontainers
@Execution(ExecutionMode.CONCURRENT)
public class ParallelContainerTests {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER = new PostgreSQLContainer<>(POSTGRES_IMAGE)
        .withDatabaseName("foo")
        .withUsername("foo")
        .withPassword("secret");

    @Test
    void container_should_be_running_first_test() throws SQLException {
        assertContainerIsRunning(POSTGRE_SQL_CONTAINER);
    }

    @Test
    void container_should_be_running_second_test() throws SQLException {
        assertContainerIsRunning(POSTGRE_SQL_CONTAINER);
    }

    @Nested
    @Execution(ExecutionMode.CONCURRENT)
    class FirstParallelTest extends BaseContainerTests {

        @Test
        void container_should_be_running() throws SQLException {
            assertContainerIsRunning(POSTGRE_SQL_BASE_CONTAINER);
        }

    }

    @Nested
    @Execution(ExecutionMode.CONCURRENT)
    class SecondParallelTest extends BaseContainerTests {

        @Test
        void container_should_be_running() throws SQLException {
            assertContainerIsRunning(POSTGRE_SQL_BASE_CONTAINER);
        }

    }

    @Testcontainers
    private static class BaseContainerTests {

        @Container
        protected static final PostgreSQLContainer<?> POSTGRE_SQL_BASE_CONTAINER = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("foo")
            .withUsername("foo")
            .withPassword("secret");

    }

    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    private static void assertContainerIsRunning(PostgreSQLContainer<?> container) throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(container.getJdbcUrl());
        hikariConfig.setUsername("foo");
        hikariConfig.setPassword("secret");

        try (HikariDataSource ds = new HikariDataSource(hikariConfig)) {
            Statement statement = ds.getConnection().createStatement();
            statement.execute("SELECT 1");
            ResultSet resultSet = statement.getResultSet();
            resultSet.next();

            int resultSetInt = resultSet.getInt(1);
            assertEquals(1, resultSetInt);
        }
    }

}
