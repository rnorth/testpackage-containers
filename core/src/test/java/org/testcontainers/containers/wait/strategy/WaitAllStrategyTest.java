package org.testcontainers.containers.wait.strategy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.rnorth.ducttape.TimeoutException;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class WaitAllStrategyTest {
    
    private static final long CHILD_INITIAL_TIMEOUT = 10;
    
    private static final long CHILD_DIRECT_SET_TIMEOUT = 20;
    
    private static final long PARENT_OVERRIDE_TIMEOUT = 30;
    
    @Mock
    private GenericContainer container;

    @Mock
    private WaitStrategy strategy1;

    @Mock
    private WaitStrategy strategy2;

    @Mock
    private WaitStrategy strategy3;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /*
     * Dummy-based tests, to check that timeout values are propagated correctly, without involving actual timing-sensitive code
     */
    @Test
    public void parentTimeoutApplies() {
        DummyStrategy child1 = new DummyStrategy(Duration.ofMillis(CHILD_INITIAL_TIMEOUT));
        child1.withStartupTimeout(Duration.ofMillis(CHILD_DIRECT_SET_TIMEOUT));

        assertThat(child1.startupTimeout.toMillis()).as("withStartupTimeout directly sets the timeout").isEqualTo(CHILD_DIRECT_SET_TIMEOUT);

        new WaitAllStrategy().withStrategy(child1).withStartupTimeout(Duration.ofMillis(PARENT_OVERRIDE_TIMEOUT));

        assertThat(child1.startupTimeout.toMillis()).as("WaitAllStrategy overrides a child's timeout").isEqualTo(PARENT_OVERRIDE_TIMEOUT);
    }

    @Test
    public void parentTimeoutAppliesToMultipleChildren() {
        Duration defaultInnerWait = Duration.ofMillis(2);
        Duration outerWait = Duration.ofMillis(6);

        DummyStrategy child1 = new DummyStrategy(defaultInnerWait);
        DummyStrategy child2 = new DummyStrategy(defaultInnerWait);

        new WaitAllStrategy().withStrategy(child1).withStrategy(child2).withStartupTimeout(outerWait);

        assertThat(child1.startupTimeout.toMillis())
            .as("WaitAllStrategy overrides a child's timeout (1st)")
            .isEqualTo(6L);
        assertThat(child2.startupTimeout.toMillis())
            .as("WaitAllStrategy overrides a child's timeout (2nd)")
            .isEqualTo(6L);
    }

    @Test
    public void parentTimeoutAppliesToAdditionalChildren() {
        Duration defaultInnerWait = Duration.ofMillis(2);
        Duration outerWait = Duration.ofMillis(20);

        DummyStrategy child1 = new DummyStrategy(defaultInnerWait);
        DummyStrategy child2 = new DummyStrategy(defaultInnerWait);

        new WaitAllStrategy().withStrategy(child1).withStartupTimeout(outerWait).withStrategy(child2);

        assertThat(child1.startupTimeout.toMillis())
            .as("WaitAllStrategy overrides a child's timeout (1st)")
            .isEqualTo(20L);
        assertThat(child2.startupTimeout.toMillis())
            .as("WaitAllStrategy overrides a child's timeout (2nd, additional)")
            .isEqualTo(20L);
    }

    /*
     * Mock-based tests to check overall behaviour, without involving timing-sensitive code
     */
    @Test
    public void childExecutionTest() {
        final WaitStrategy underTest = new WaitAllStrategy().withStrategy(strategy1).withStrategy(strategy2);

        doNothing().when(strategy1).waitUntilReady(eq(container));
        doNothing().when(strategy2).waitUntilReady(eq(container));

        underTest.waitUntilReady(container);

        InOrder inOrder = inOrder(strategy1, strategy2);
        inOrder.verify(strategy1).waitUntilReady(any());
        inOrder.verify(strategy2).waitUntilReady(any());
    }

    @Test
    public void withoutOuterTimeoutShouldRelyOnInnerStrategies() {
        final WaitStrategy underTest = new WaitAllStrategy(WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY)
            .withStrategy(strategy1)
            .withStrategy(strategy2)
            .withStrategy(strategy3);

        doNothing().when(strategy1).waitUntilReady(eq(container));
        doThrow(TimeoutException.class).when(strategy2).waitUntilReady(eq(container));

        assertThat(
            catchThrowable(() -> {
                underTest.waitUntilReady(container);
            })
        )
            .as("The outer strategy timeout applies")
            .isInstanceOf(TimeoutException.class);

        InOrder inOrder = inOrder(strategy1, strategy2, strategy3);
        inOrder.verify(strategy1).waitUntilReady(any());
        inOrder.verify(strategy2).waitUntilReady(any());
        inOrder.verify(strategy3, never()).waitUntilReady(any());
    }

    @Test
    public void timeoutChangeShouldNotBePossibleWithIndividualTimeoutMode() {
        final WaitStrategy underTest = new WaitAllStrategy(WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY);

        assertThat(
            catchThrowable(() -> {
                underTest.withStartupTimeout(Duration.ofSeconds(42));
            })
        )
            .as("Cannot change timeout for individual timeouts")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldNotMessWithIndividualTimeouts() {
        new WaitAllStrategy(WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY)
            .withStrategy(strategy1)
            .withStrategy(strategy2);

        verify(strategy1, never()).withStartupTimeout(any());
        verify(strategy1, never()).withStartupTimeout(any());
    }

    @Test
    public void shouldOverwriteIndividualTimeouts() {
        Duration someSeconds = Duration.ofSeconds(23);
        new WaitAllStrategy().withStartupTimeout(someSeconds).withStrategy(strategy1).withStrategy(strategy2);

        verify(strategy1).withStartupTimeout(someSeconds);
        verify(strategy1).withStartupTimeout(someSeconds);
    }

    static class DummyStrategy extends AbstractWaitStrategy {

        DummyStrategy(Duration defaultInnerWait) {
            super.startupTimeout = defaultInnerWait;
        }

        @Override
        protected void waitUntilReady() {
            // no-op
        }
    }
}
