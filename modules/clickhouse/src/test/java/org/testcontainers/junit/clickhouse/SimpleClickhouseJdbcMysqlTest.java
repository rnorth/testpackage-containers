package org.testcontainers.junit.clickhouse;

import org.junit.Test;
import org.testcontainers.containers.ClickHouseContainerJdbcMysql;

import java.sql.Connection;
import java.sql.SQLException;

import static org.rnorth.visibleassertions.VisibleAssertions.assertTrue;
import static org.testcontainers.ClickhouseTestImages.CLICKHOUSE_IMAGE;

public class SimpleClickhouseJdbcMysqlTest {

    @Test
    public void testSimple() throws SQLException {
        try (ClickHouseContainerJdbcMysql clickhouse = new ClickHouseContainerJdbcMysql(CLICKHOUSE_IMAGE)) {
            clickhouse.start();

            try (Connection connection = clickhouse.createConnection("")) {
                boolean testQuerySucceeded = connection.createStatement().execute("SELECT 1");
                assertTrue("A basic SELECT query succeeds", testQuerySucceeded);
            }
        }
    }
}
