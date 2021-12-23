package org.testcontainers.containers;

public class ClickHouseProvider extends JdbcDatabaseContainerProvider {
    @Override
    public boolean supports(String databaseType) {
        return databaseType.equals(ClickHouseInit.JDBC_NAME_YANDEX) || databaseType.equals(ClickHouseInit.JDBC_NAME_MYSQL);
    }

    @Override
    public JdbcDatabaseContainer newInstance() {
        return newInstance(ClickHouseInit.DEFAULT_TAG);
    }

    @Override
    public JdbcDatabaseContainer newInstance(String tag) {
        return new ClickHouseContainerJdbcYandex(ClickHouseInit.DEFAULT_IMAGE_NAME.withTag(tag));
    }
}
