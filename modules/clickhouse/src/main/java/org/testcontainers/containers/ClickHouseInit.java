package org.testcontainers.containers;

import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class ClickHouseInit {
    public static final String JDBC_NAME_YANDEX = "clickhouse";
    public static final String JDBC_NAME_MYSQL = "mysql";

    public static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("yandex/clickhouse-server");

    public static final String CLICKHOUSE_DRIVER_CLASS_NAME = "ru.yandex.clickhouse.ClickHouseDriver";
    public static final String MYSQL_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

    public static final Integer HTTP_PORT = 8123;
    public static final Integer NATIVE_PORT = 9000;
    public static final Integer MYSQL_PORT = 9004;

    public static final String DATABASE_NAME = "default";
    public static final String USERNAME = "default";
    public static final String PASSWORD = "";

    static final String TEST_QUERY = "SELECT 1";

    public static final String DEFAULT_TAG = "21.3.8.76";

    public static void init(GenericContainer<?> container) {
        container.withExposedPorts(HTTP_PORT, NATIVE_PORT, MYSQL_PORT);

        container.waitingFor(
            new HttpWaitStrategy()
                .forStatusCode(200)
                .forPort(HTTP_PORT)
                .forResponsePredicate(responseBody -> "Ok.".equals(responseBody))
                .withStartupTimeout(Duration.ofMinutes(1))
        );
    }
}
