package org.testcontainers.junit.clickhouse;

import org.junit.Test;
import org.testcontainers.containers.ClickHouseContainerJdbcYandex;
import org.testcontainers.db.AbstractContainerDatabaseTest;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;
import static org.testcontainers.ClickhouseTestImages.CLICKHOUSE_IMAGE;

public class SimpleClickhouseJdbcYandexTest extends AbstractContainerDatabaseTest {

    @Test
    public void testSimple() throws SQLException {
        try (ClickHouseContainerJdbcYandex clickhouse = new ClickHouseContainerJdbcYandex(CLICKHOUSE_IMAGE)) {
            clickhouse.start();

            ResultSet resultSet = performQuery(clickhouse, "SELECT 1");

            int resultSetInt = resultSet.getInt(1);
            assertEquals("A basic SELECT query succeeds", 1, resultSetInt);
        }
    }
}
