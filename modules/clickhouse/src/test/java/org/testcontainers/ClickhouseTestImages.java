package org.testcontainers;

import org.testcontainers.utility.DockerImageName;

public interface ClickhouseTestImages {
    DockerImageName CLICKHOUSE_IMAGE = DockerImageName.parse("yandex/clickhouse-server:21.3.8.76");
}
