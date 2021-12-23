package org.testcontainers.containers;

import org.testcontainers.utility.DockerImageName;

public class ClickHouseContainerJdbcMysql extends AbstractClickHouseContainerJdbc {

    public ClickHouseContainerJdbcMysql(DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    @Override
    public String getDriverClassName() {
        return ClickHouseInit.MYSQL_DRIVER_CLASS_NAME;
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getMappedPort(ClickHouseInit.MYSQL_PORT) + "/" + getDatabaseName() + "?user=" + getUsername() + "&password=" + getPassword();
    }
}
