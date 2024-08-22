package org.testcontainers.junit.jupiter;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class WithEnvAsStringSupplierDependsOnValueFromAnotherContainer {

    private static final String FIRST_DEPENDENCY_ENV_KEY = "first_dependency";

    private static final String SECOND_DEPENDENCY_ENV_KEY = "second_dependency";

    @Container
    private static final GenericContainer<?> FIRST_CONTAINER = new GenericContainer<>(
        JUnitJupiterTestImages.HTTPD_IMAGE
    )
        .withExposedPorts(80);

    @Container
    private static final GenericContainer<?> SECOND_CONTAINER = new GenericContainer<>(
        JUnitJupiterTestImages.HTTPD_IMAGE
    )
        .withExposedPorts(80);

    @Container
    private static final GenericContainer<?> APP_CONTAINER = new GenericContainer<>(JUnitJupiterTestImages.HTTPD_IMAGE)
        .dependsOn(FIRST_CONTAINER, SECOND_CONTAINER)
        .withEnv(FIRST_DEPENDENCY_ENV_KEY, () -> String.valueOf(FIRST_CONTAINER.getFirstMappedPort()))
        .withEnv(SECOND_DEPENDENCY_ENV_KEY, () -> String.valueOf(SECOND_CONTAINER.getFirstMappedPort()))
        .withExposedPorts(80);

    @Test
    public void smokeTest() {
        assertThat(FIRST_CONTAINER.isRunning()).isTrue();
        assertThat(SECOND_CONTAINER.isRunning()).isTrue();
        assertThat(APP_CONTAINER.isRunning()).isTrue();

        assertThat(getDependencyEnvOfMappedPort(FIRST_DEPENDENCY_ENV_KEY))
            .isEqualTo(String.valueOf(FIRST_CONTAINER.getFirstMappedPort()));
        assertThat(getDependencyEnvOfMappedPort(SECOND_DEPENDENCY_ENV_KEY))
            .isEqualTo(String.valueOf(SECOND_CONTAINER.getFirstMappedPort()));
    }

    private static String getDependencyEnvOfMappedPort(String dependencyEnvKey) {
        return APP_CONTAINER
            .getEnvMap()
            .entrySet()
            .stream()
            .filter(e -> e.getKey().startsWith(dependencyEnvKey))
            .map(e -> e.getValue().get())
            .findFirst()
            .orElse(null);
    }
}
