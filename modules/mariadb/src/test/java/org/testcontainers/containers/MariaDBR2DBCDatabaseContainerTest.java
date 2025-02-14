package org.testcontainers.containers;

import io.r2dbc.spi.ConnectionFactoryOptions;
import org.testcontainers.r2dbc.AbstractR2DBCDatabaseContainerTest;
import org.testcontainers.utility.DockerImageName;

public class MariaDBR2DBCDatabaseContainerTest extends AbstractR2DBCDatabaseContainerTest<MariaDBContainer<?>> {

    @Override
    protected ConnectionFactoryOptions getOptions(MariaDBContainer<?> container) {
        return MariaDBR2DBCDatabaseContainer.getOptions(container);
    }

    @Override
    protected String createR2DBCUrl() {
        return "r2dbc:tc:mariadb:///db?TC_IMAGE_TAG=10.11.10";
    }

    @Override
    protected MariaDBContainer<?> createContainer() {
        return new MariaDBContainer<>(DockerImageName.parse("mariadb:10.3.39"));
    }
}
