package org.testcontainers;

import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class RequireContainerSupportRuleTest {

    @Test
    public void assumptionViolationisThrownWithoutDockerSpport() {
        try (MockedStatic<DockerClientFactory> staticallyMockedFactory = mockStatic(DockerClientFactory.class)) {
            DockerClientFactory mockedFactory = mock(DockerClientFactory.class);
            when(mockedFactory.isDockerAvailable()).thenReturn(false);
            staticallyMockedFactory.when(DockerClientFactory::instance).thenReturn(mockedFactory);

            RequireContainerSupportRule rcsr = new RequireContainerSupportRule();

            assertThatThrownBy(() -> rcsr.apply(new EmptyStatement(), null).evaluate())
                .isInstanceOf(AssumptionViolatedException.class)
                .hasMessage("Docker support is not available and this test requires TestContainers which needs docker");
        }
    }

    @Test
    public void assumptionViolationisNotThrownWithDockerSpport() throws Throwable {
        try (MockedStatic<DockerClientFactory> staticallyMockedFactory = mockStatic(DockerClientFactory.class)) {
            DockerClientFactory mockedFactory = mock(DockerClientFactory.class);
            when(mockedFactory.isDockerAvailable()).thenReturn(true);
            staticallyMockedFactory.when(DockerClientFactory::instance).thenReturn(mockedFactory);

            RequireContainerSupportRule rcsr = new RequireContainerSupportRule();

            // any exception thrown will ripple out and fail the test
            rcsr.apply(new EmptyStatement(), null).evaluate();
        }
    }

    private static class EmptyStatement extends Statement {

        @Override
        public void evaluate() throws Throwable {
            // no op.
        }
    }
}
