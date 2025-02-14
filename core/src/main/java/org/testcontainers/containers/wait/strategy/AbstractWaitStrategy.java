package org.testcontainers.containers.wait.strategy;

import lombok.NonNull;
import org.rnorth.ducttape.ratelimits.RateLimiter;
import org.rnorth.ducttape.ratelimits.RateLimiterBuilder;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractWaitStrategy implements WaitStrategy {

    static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(
        new ThreadFactory() {
            private final AtomicLong COUNTER = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "testcontainers-wait-" + COUNTER.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        }
    );

    private static final RateLimiter DOCKER_CLIENT_RATE_LIMITER = RateLimiterBuilder
        .newBuilder()
        .withRate(1, TimeUnit.SECONDS)
        .withConstantThroughput()
        .build();

    protected WaitStrategyTarget waitStrategyTarget;

    @NonNull
    protected Duration startupTimeout = Duration.ofSeconds(TestcontainersConfiguration.getInstance().getStartupTimeout());

    @NonNull
    private RateLimiter rateLimiter = DOCKER_CLIENT_RATE_LIMITER;

    /**
     * Wait until the target has started.
     *
     * @param waitStrategyTarget the target of the WaitStrategy
     */
    @Override
    public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
        this.waitStrategyTarget = waitStrategyTarget;
        waitUntilReady();
    }

    /**
     * Wait until {@link #waitStrategyTarget} has started.
     */
    protected abstract void waitUntilReady();

    /**
     * Set the duration of waiting time until container treated as started.
     *
     * @param startupTimeout timeout
     * @return this
     * @see WaitStrategy#waitUntilReady(WaitStrategyTarget)
     */
    public WaitStrategy withStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
        return this;
    }

    /**
     * @return the ports on which to check if the container is ready
     */
    protected Set<Integer> getLivenessCheckPorts() {
        return waitStrategyTarget.getLivenessCheckPortNumbers();
    }

    /**
     * @return the rate limiter to use
     */
    protected RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * Set the rate limiter being used
     *
     * @param rateLimiter rateLimiter
     * @return this
     */
    public WaitStrategy withRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        return this;
    }
}
