package org.testcontainers.clickhouse;

import org.junit.Test;
import org.testcontainers.db.AbstractContainerDatabaseTest;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class ClickHouseContainerTest extends AbstractContainerDatabaseTest {

    @Test
    public void testSimple() throws SQLException {
        try (ClickHouseContainer clickhouse = new ClickHouseContainer("clickhouse/clickhouse-server:24.8.12.28")) {
            clickhouse.start();

            ResultSet resultSet = performQuery(clickhouse, "SELECT 1");

            int resultSetInt = resultSet.getInt(1);
            assertThat(resultSetInt).isEqualTo(1);
        }
    }

    @Test
    public void customCredentialsWithUrlParams() throws SQLException {
        try (
            ClickHouseContainer clickhouse = new ClickHouseContainer("clickhouse/clickhouse-server:24.8.12.28")
                .withUsername("test")
                .withPassword("test")
                .withDatabaseName("test")
                .withUrlParam("max_result_rows", "5")
        ) {
            clickhouse.start();

            ResultSet resultSet = performQuery(
                clickhouse,
                "SELECT value FROM system.settings where name='max_result_rows'"
            );

            int resultSetInt = resultSet.getInt(1);
            assertThat(resultSetInt).isEqualTo(5);
        }
    }
}
