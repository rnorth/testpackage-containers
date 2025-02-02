package org.testcontainers.junit.clickhouse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.ClickhouseTestImages;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.db.AbstractContainerDatabaseTest;
import org.testcontainers.utility.DockerImageName;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class SimpleClickhouseTest extends AbstractContainerDatabaseTest {

    private final DockerImageName imageName;

    public SimpleClickhouseTest(DockerImageName imageName) {
        this.imageName = imageName;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[][] data() {
        return new Object[][] { //
            { ClickhouseTestImages.CLICKHOUSE_IMAGE },
        };
    }

    @Test
    public void testSimple() throws SQLException {
        try (ClickHouseContainer clickhouse = new ClickHouseContainer(this.imageName)) {
            clickhouse.start();

            ResultSet resultSet = performQuery(clickhouse, "SELECT 1");

            int resultSetInt = resultSet.getInt(1);
            assertThat(resultSetInt).as("A basic SELECT query succeeds").isEqualTo(1);
        }
    }
}
