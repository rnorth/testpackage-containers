package org.testcontainers.containers;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
@RunWith(Parameterized.class)
public class MySQLRootAccessTest {

    @Parameterized.Parameters(name = "{0}")
    public static String[] params() {
        return new String[]{
            "mysql:8",
            "mysql:5.7.22"
        };
    }

    @Parameterized.Parameter()
    public String image;

    @Test
    // Fails with both - cannot create duplicate root account
    public void testEasyRootAccountCreation() throws SQLException {
        try (MySQLContainer<?> db = new MySQLContainer<>(image)
            .withUsername("root")
            .withPassword("test")
            .withLogConsumer(new Slf4jLogConsumer(log))) {
            db.start();

            Connection connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            connection.createStatement().execute("SELECT 1");
            connection.createStatement().execute("set sql_log_bin=0");
        }
    }

    @Test
    // Fails with both, because db.getUsername() == "test" and we're trying to execute a root-only action
    public void testEnvVarOnlyRootAccountCreation() throws SQLException {
        try (MySQLContainer<?> db = new MySQLContainer<>(image)
            .withUsername("test")
            .withPassword("test")
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withLogConsumer(new Slf4jLogConsumer(log))) {
            db.start();

            Connection connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            connection.createStatement().execute("SELECT 1");
            connection.createStatement().execute("set sql_log_bin=0");
        }
    }

    @Test
    // Works with both
    public void testEnvVarOnlyRootAccountCreationAndHardcodedUser() throws SQLException {
        try (MySQLContainer<?> db = new MySQLContainer<>(image)
            .withUsername("test")
            .withPassword("test")
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withLogConsumer(new Slf4jLogConsumer(log))) {
            db.start();

            Connection connection = DriverManager.getConnection(db.getJdbcUrl(), "root", "test");
            connection.createStatement().execute("SELECT 1");
            connection.createStatement().execute("set sql_log_bin=0");
        }
    }
}
