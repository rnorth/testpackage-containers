package org.testcontainers.utility;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Simple utility to log which images have been pulled by {@link org.testcontainers.Testcontainers} and how many times.
 */
@Slf4j
public class ImagePullCountLogger {

    private static ImagePullCountLogger instance;
    private final Map<String, AtomicInteger> pullCounters = new ConcurrentHashMap<>();

    public synchronized static ImagePullCountLogger instance() {
        if (instance == null) {
            instance = new ImagePullCountLogger();
            Runtime.getRuntime().addShutdownHook(new Thread(instance::logStatistics));
        }

        return instance;
    }

    @VisibleForTesting
    ImagePullCountLogger() {

    }

    public void logStatistics() {
        if (pullCounters.size() > 0) {
            final String summary = pullCounters.entrySet().stream()
                .map(it -> it.getKey() + (it.getValue().intValue() > 1 ? " (" + it.getValue() + " times)" : ""))
                .sorted()
                .collect(Collectors.joining("\n    ", "\n    ", "\n"));

            log.info("Testcontainers pulled the following images during execution:{}", summary);
        } else {
            log.info("Testcontainers did not need to pull any images during execution");
        }
    }

    public void recordPull(final String image) {
        pullCounters.computeIfAbsent(image, __ -> new AtomicInteger()).incrementAndGet();
    }
}
