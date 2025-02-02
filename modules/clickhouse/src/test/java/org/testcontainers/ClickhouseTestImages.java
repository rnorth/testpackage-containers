package org.testcontainers;

import org.testcontainers.utility.DockerImageName;

public interface ClickhouseTestImages {
    DockerImageName CLICKHOUSE_IMAGE = DockerImageName.parse("clickhouse/clickhouse-server:24.8.12.28");
}
