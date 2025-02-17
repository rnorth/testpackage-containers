package org.testcontainers.utility;

import com.google.common.util.concurrent.Futures;
import lombok.SneakyThrows;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class LazyFutureTest {

    @Test
    public void testLaziness() throws Exception {
        AtomicInteger counter = new AtomicInteger();

        Future<Integer> lazyFuture = new LazyFuture<Integer>() {
            @Override
            protected Integer resolve() {
                return counter.incrementAndGet();
            }
        };

        assertThat(counter).as("No resolve() invocations before get()").hasValue(0);
        assertThat(lazyFuture.get()).as("get() call returns proper result").isEqualTo(1);
        assertThat(counter).as("resolve() was called only once after single get() call").hasValue(1);

        counter.incrementAndGet();
        assertThat(lazyFuture.get()).as("result of resolve() must be cached").isEqualTo(1);
    }

    @Test(timeout = 5_000)
    public void timeoutWorks() {
        Future<Void> lazyFuture = new LazyFuture<Void>() {
            @Override
            @SneakyThrows(InterruptedException.class)
            protected Void resolve() {
                TimeUnit.MINUTES.sleep(1);
                return null;
            }
        };

        assertThat(catchThrowable(() -> lazyFuture.get(10, TimeUnit.MILLISECONDS)))
            .as("Should timeout")
            .isInstanceOf(TimeoutException.class);
    }

    @Test(timeout = 5_000)
    public void testThreadSafety() throws Exception {
        final int numOfThreads = 3;
        CountDownLatch latch = new CountDownLatch(numOfThreads);
        AtomicInteger counter = new AtomicInteger();

        Future<Integer> lazyFuture = new LazyFuture<Integer>() {
            @Override
            @SneakyThrows(InterruptedException.class)
            protected Integer resolve() {
                latch.await();
                return counter.incrementAndGet();
            }
        };

        Future<List<Integer>> task = new ForkJoinPool(numOfThreads)
            .submit(() -> {
                return IntStream
                    .rangeClosed(1, numOfThreads)
                    .parallel()
                    .mapToObj(i -> Futures.getUnchecked(lazyFuture))
                    .collect(Collectors.toList());
            });

        while (latch.getCount() > 0) {
            latch.countDown();
        }

        assertThat(task.get())
            .as("All threads receives the same result")
            .isEqualTo(Collections.nCopies(numOfThreads, 1));
    }
}
