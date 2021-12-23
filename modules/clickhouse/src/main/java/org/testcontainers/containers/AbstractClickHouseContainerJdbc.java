package org.testcontainers.containers;

import org.testcontainers.utility.DockerImageName;

abstract class AbstractClickHouseContainerJdbc extends JdbcDatabaseContainer {

    public AbstractClickHouseContainerJdbc(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(ClickHouseInit.DEFAULT_IMAGE_NAME);
        ClickHouseInit.init(this);
    }

    @Override
    protected Integer getLivenessCheckPort() {
        return getMappedPort(ClickHouseInit.HTTP_PORT);
    }

    @Override
    public String getDriverClassName() {
        return ClickHouseInit.MYSQL_DRIVER_CLASS_NAME;
    }

    @Override
    public String getDatabaseName() {
        return ClickHouseInit.DATABASE_NAME;
    }

    @Override
    public String getUsername() {
        return ClickHouseInit.USERNAME;
    }

    @Override
    public String getPassword() {
        return ClickHouseInit.PASSWORD;
    }

    @Override
    public String getTestQueryString() {
        return ClickHouseInit.TEST_QUERY;
    }

    @Override
    public AbstractClickHouseContainerJdbc withUrlParam(String paramName, String paramValue) {
        throw new UnsupportedOperationException("The ClickHouse does not support this");
    }
}
