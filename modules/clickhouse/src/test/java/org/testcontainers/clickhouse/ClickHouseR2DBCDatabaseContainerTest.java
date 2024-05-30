package org.testcontainers.clickhouse;

import io.r2dbc.spi.ConnectionFactoryOptions;
import org.testcontainers.r2dbc.AbstractR2DBCDatabaseContainerTest;

public class ClickHouseR2DBCDatabaseContainerTest extends AbstractR2DBCDatabaseContainerTest<ClickHouseContainer> {

    @Override
    protected ConnectionFactoryOptions getOptions(ClickHouseContainer container) {
        return ClickHouseR2DBCDatabaseContainer.getOptions(container);
    }

    @Override
    protected String createR2DBCUrl() {
        return "r2dbc:tc:clickhouse:///db?TC_IMAGE_TAG=24.8.12.28";
    }

    @Override
    protected ClickHouseContainer createContainer() {
        return new ClickHouseContainer("clickhouse/clickhouse-server:24.8.12.28");
    }
}
