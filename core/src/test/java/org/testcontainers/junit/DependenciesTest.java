package org.testcontainers.junit;

import lombok.Getter;
import org.junit.Test;
import org.testcontainers.TestImages;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DependenciesTest {

    @Test
    public void shouldWorkWithSimpleDependency() {
        InvocationCountingStartable startable = new InvocationCountingStartable();

        try (
            GenericContainer<?> container = new GenericContainer<>(TestImages.TINY_IMAGE)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
                .dependsOn(startable)
        ) {
            container.start();
        }

        assertThat(startable.getStartInvocationCount().intValue()).as("Started once").isEqualTo(1);
        assertThat(startable.getStopInvocationCount().intValue()).as("Does not trigger .stop()").isZero();
    }

    @Test
    public void shouldWorkWithMultipleDependencies() {
        InvocationCountingStartable startable1 = new InvocationCountingStartable();
        InvocationCountingStartable startable2 = new InvocationCountingStartable();

        try (
            GenericContainer<?> container = new GenericContainer<>(TestImages.TINY_IMAGE)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
                .dependsOn(startable1, startable2)
        ) {
            container.start();
        }

        assertThat(startable1.getStartInvocationCount().intValue()).as("Startable1 started once").isEqualTo(1);
        assertThat(startable2.getStartInvocationCount().intValue()).as("Startable2 started once").isEqualTo(1);
    }

    @Test
    public void shouldStartEveryTime() {
        InvocationCountingStartable startable = new InvocationCountingStartable();

        try (
            GenericContainer<?> container = new GenericContainer<>(TestImages.TINY_IMAGE)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
                .dependsOn(startable)
        ) {
            container.start();
            container.stop();

            container.start();
            container.stop();

            container.start();
        }

        assertThat(startable.getStartInvocationCount().intValue()).as("Started multiple times").isEqualTo(3);
        assertThat(startable.getStopInvocationCount().intValue()).as("Does not trigger .stop()").isZero();
    }

    @Test
    public void shouldStartTransitiveDependencies() {
        InvocationCountingStartable transitiveOfTransitiveStartable = new InvocationCountingStartable();
        InvocationCountingStartable transitiveStartable = new InvocationCountingStartable();
        transitiveStartable.getDependencies().add(transitiveOfTransitiveStartable);

        InvocationCountingStartable startable = new InvocationCountingStartable();
        startable.getDependencies().add(transitiveStartable);

        try (
            GenericContainer<?> container = new GenericContainer<>(TestImages.TINY_IMAGE)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
                .dependsOn(startable)
        ) {
            container.start();
            container.stop();
        }

        assertThat(startable.getStartInvocationCount().intValue()).as("Root started").isEqualTo(1);
        assertThat(transitiveStartable.getStartInvocationCount().intValue()).as("Transitive started").isEqualTo(1);
        assertThat(transitiveOfTransitiveStartable.getStartInvocationCount().intValue())
            .as("Transitive of transitive started")
            .isEqualTo(1);
    }

    @Test
    public void shouldHandleDiamondDependencies() throws Exception {
        InvocationCountingStartable a = new InvocationCountingStartable();
        InvocationCountingStartable b = new InvocationCountingStartable();
        InvocationCountingStartable c = new InvocationCountingStartable();
        InvocationCountingStartable d = new InvocationCountingStartable();
        //  / b \
        // a     d
        //  \ c /
        b.getDependencies().add(a);
        c.getDependencies().add(a);

        d.getDependencies().add(b);
        d.getDependencies().add(c);

        Startables.deepStart(Stream.of(d)).get(1, TimeUnit.SECONDS);

        assertThat(a.getStartInvocationCount().intValue()).as("A started").isEqualTo(1);
        assertThat(b.getStartInvocationCount().intValue()).as("B started").isEqualTo(1);
        assertThat(c.getStartInvocationCount().intValue()).as("C started").isEqualTo(1);
        assertThat(d.getStartInvocationCount().intValue()).as("D started").isEqualTo(1);
    }

    @Test
    public void shouldHandleParallelStream() throws Exception {
        List<Startable> startables = Stream
            .generate(InvocationCountingStartable::new)
            .limit(10)
            .collect(Collectors.toList());

        for (int i = 1; i < startables.size(); i++) {
            startables.get(0).getDependencies().add(startables.get(i));
        }

        Startables.deepStart(startables.parallelStream()).get(1, TimeUnit.SECONDS);
    }

    private static class InvocationCountingStartable implements Startable {

        @Getter
        Set<Startable> dependencies = new HashSet<>();

        @Getter
        AtomicLong startInvocationCount = new AtomicLong(0);

        @Getter
        AtomicLong stopInvocationCount = new AtomicLong(0);

        @Override
        public void start() {
            startInvocationCount.getAndIncrement();
        }

        @Override
        public void stop() {
            stopInvocationCount.getAndIncrement();
        }
    }
}
