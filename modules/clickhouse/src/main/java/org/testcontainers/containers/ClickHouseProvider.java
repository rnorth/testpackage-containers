package org.testcontainers.containers;

import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

public class ClickHouseProvider extends JdbcDatabaseContainerProvider {

    @Override
    public boolean supports(String databaseType) {
        return databaseType.equals(ClickHouseContainer.NAME);
    }

    @Override
    public JdbcDatabaseContainer newInstance(String tag) {
        return new ClickHouseContainer(DockerImageName.parse(ClickHouseContainer.CLICKHOUSE_CLICKHOUSE_SERVER).withTag(tag));
    }
}
