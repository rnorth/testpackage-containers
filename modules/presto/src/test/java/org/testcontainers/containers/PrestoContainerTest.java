package org.testcontainers.containers;

import org.junit.Test;
import org.testcontainers.PrestoTestImages;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PrestoContainerTest {

    @Test
    public void testSimple() throws Exception {
        try (PrestoContainer<?> prestoSql = new PrestoContainer<>(PrestoTestImages.PRESTO_TEST_IMAGE)) {
            prestoSql.start();
            try (
                Connection connection = prestoSql.createConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT DISTINCT node_version FROM system.runtime.nodes")
            ) {
                assertThat(resultSet.next()).as("has result").isTrue();
                assertThat(resultSet.getString("node_version"))
                    .as("Presto version")
                    .startsWith(PrestoContainer.DEFAULT_TAG);
                assertHasCorrectExposedAndLivenessCheckPorts(prestoSql);
            }
        }
    }

    @Test
    public void testSpecificVersion() throws Exception {
        try (
            PrestoContainer<?> prestoSql = new PrestoContainer<>(PrestoTestImages.PRESTO_PREVIOUS_VERSION_TEST_IMAGE)
        ) {
            prestoSql.start();
            try (
                Connection connection = prestoSql.createConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT DISTINCT node_version FROM system.runtime.nodes")
            ) {
                assertThat(resultSet.next()).as("has result").isTrue();
                assertThat(resultSet.getString("node_version"))
                    .as("Presto version")
                    .startsWith(PrestoTestImages.PRESTO_PREVIOUS_VERSION_TEST_IMAGE.getVersionPart());
            }
        }
    }

    @Test
    public void testQueryMemoryAndTpch() throws SQLException {
        try (PrestoContainer<?> prestoSql = new PrestoContainer<>(PrestoTestImages.PRESTO_TEST_IMAGE)) {
            prestoSql.start();
            try (
                Connection connection = prestoSql.createConnection();
                Statement statement = connection.createStatement()
            ) {
                // Prepare data
                statement.execute(
                    "CREATE TABLE memory.default.table_with_array AS SELECT 1 id, ARRAY[1, 42, 2, 42, 4, 42] my_array"
                );

                // Query Presto using newly created table and a builtin connector
                try (
                    ResultSet resultSet = statement.executeQuery(
                        "" +
                        "SELECT nationkey, element " +
                        "FROM tpch.tiny.nation " +
                        "JOIN memory.default.table_with_array twa ON nationkey = twa.id " +
                        "CROSS JOIN UNNEST(my_array) a(element) " +
                        "ORDER BY element OFFSET 1 FETCH FIRST 3 ROWS ONLY "
                    )
                ) {
                    List<Integer> actualElements = new ArrayList<>();
                    while (resultSet.next()) {
                        actualElements.add(resultSet.getInt("element"));
                    }
                    assertThat(actualElements).isEqualTo(Arrays.asList(2, 4, 42));
                }
            }
        }
    }

    @Test
    public void testInitScript() throws Exception {
        try (PrestoContainer<?> prestoSql = new PrestoContainer<>(PrestoTestImages.PRESTO_TEST_IMAGE)) {
            prestoSql.withInitScript("initial.sql");
            prestoSql.start();
            try (
                Connection connection = prestoSql.createConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT a FROM memory.default.test_table")
            ) {
                assertThat(resultSet.next()).as("has result").isTrue();
                assertThat(resultSet.getObject("a")).as("Value").isEqualTo(12345678909324L);
                assertThat(resultSet.next()).as("only has one result").isFalse();
            }
        }
    }

    @Test
    public void testTcJdbcUri() throws Exception {
        try (
            Connection connection = DriverManager.getConnection(
                String.format("jdbc:tc:presto:%s://hostname/", PrestoContainer.DEFAULT_TAG)
            )
        ) {
            // Verify metadata with tc: JDBC connection URI
            assertThat(0).isEqualTo(connection.getMetaData().getDatabaseMajorVersion());

            // Verify transactions with tc: JDBC connection URI
            assertThat(connection.getAutoCommit()).as("Is autocommit").isTrue();
            connection.setAutoCommit(false);
            assertThat(connection.getAutoCommit()).as("Is autocommit").isFalse();
            assertThat(connection.getTransactionIsolation())
                .as("Transaction isolation")
                .isEqualTo(Connection.TRANSACTION_READ_UNCOMMITTED);

            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("CREATE TABLE memory.default.test_tc(a bigint)"))
                    .as("Update result")
                    .isEqualTo(0);
                try (
                    ResultSet resultSet = statement.executeQuery("SELECT node_version AS v FROM system.runtime.nodes")
                ) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("v")).startsWith(PrestoContainer.DEFAULT_TAG);
                    assertThat(resultSet.next()).isFalse();
                }
                connection.commit();
            } finally {
                connection.rollback();
            }
            connection.setAutoCommit(true);
            assertThat(connection.getAutoCommit()).as("Is autocommit").isTrue();
            assertThat(connection.getTransactionIsolation())
                .as("Transaction isolation should be retained")
                .isEqualTo(Connection.TRANSACTION_READ_UNCOMMITTED);
        }
    }

    private void assertHasCorrectExposedAndLivenessCheckPorts(PrestoContainer<?> prestoSql) {
        assertThat(prestoSql.getExposedPorts()).containsExactly(PrestoContainer.PRESTO_PORT);
        assertThat(prestoSql.getLivenessCheckPortNumbers())
            .containsExactly(prestoSql.getMappedPort(PrestoContainer.PRESTO_PORT));
    }
}
