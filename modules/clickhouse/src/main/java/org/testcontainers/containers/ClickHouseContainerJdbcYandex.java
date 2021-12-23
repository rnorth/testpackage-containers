package org.testcontainers.containers;

import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.ClickHouseInit.HTTP_PORT;

public class ClickHouseContainerJdbcYandex extends ClickHouseContainerJdbcMysql {

    public ClickHouseContainerJdbcYandex(DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    @Override
    public String getDriverClassName() {
        return ClickHouseInit.CLICKHOUSE_DRIVER_CLASS_NAME;
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:clickhouse://" + getHost() + ":" + getMappedPort(HTTP_PORT) + "/" + getDatabaseName() + "?user=" + getUsername() + "&password=" + getPassword();
    }

}
