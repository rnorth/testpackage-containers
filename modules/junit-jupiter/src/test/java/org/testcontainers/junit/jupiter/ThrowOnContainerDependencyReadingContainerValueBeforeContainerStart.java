package org.testcontainers.junit.jupiter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
public class ThrowOnContainerDependencyReadingContainerValueBeforeContainerStart {

    private static final String FIRST_DEPENDENCY_ENV_KEY = "first_dependency";

    private static final String SECOND_DEPENDENCY_ENV_KEY = "second_dependency";

    // @Container - not applicable in this case. Manual container management is required
    private static final GenericContainer<?> FIRST_CONTAINER = new GenericContainer<>(
        JUnitJupiterTestImages.HTTPD_IMAGE
    )
        .withExposedPorts(80);

    // @Container - not applicable in this case. Manual container management is required
    private static final GenericContainer<?> SECOND_CONTAINER = new GenericContainer<>(
        JUnitJupiterTestImages.HTTPD_IMAGE
    )
        .withExposedPorts(80);

    // @Container - not applicable in this case. Manual container management is required
    private static final GenericContainer<?> APP_CONTAINER = new GenericContainer<>(JUnitJupiterTestImages.HTTPD_IMAGE)
        .dependsOn(FIRST_CONTAINER, SECOND_CONTAINER)
        // .withEnv can not use getFirstMappedPort() call here because FIRST_CONTAINER is not started yet
        .withExposedPorts(80);

    @BeforeAll
    public static void setupWithException() {
        assertThatThrownBy(() -> {
                /*
                 * Sequence of expressions are substitute when using @Container annotation:
                 * @Container GenericContainer<?> FIRST_CONTAINER
                 * @Container GenericContainer<?> SECOND_CONTAINER
                 * @Container GenericContainer<?> APP_CONTAINER
                 * APP_CONTAINER.withEnv(FIRST_DEPENDENCY_ENV_KEY, String.valueOf(FIRST_CONTAINER.getFirstMappedPort()))
                 * APP_CONTAINER.withEnv(SECOND_DEPENDENCY_ENV_KEY, String.valueOf(SECOND_CONTAINER.getFirstMappedPort()))
                 */
                // reading values from not started containers
                APP_CONTAINER
                    .withEnv(FIRST_DEPENDENCY_ENV_KEY, String.valueOf(FIRST_CONTAINER.getFirstMappedPort()))
                    .withEnv(SECOND_DEPENDENCY_ENV_KEY, String.valueOf(SECOND_CONTAINER.getFirstMappedPort()));

                FIRST_CONTAINER.start();
                SECOND_CONTAINER.start();
                APP_CONTAINER.start();
            })
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mapped port can only be obtained after the container is started");
    }

    @Test
    public void throw_on_container_dependency_reading_container_value_before_container_start() {
        // noop
    }
}
