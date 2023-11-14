package org.testcontainers.containers.wait.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Cleanup;
import org.apache.commons.lang3.function.FailableRunnable;
import org.assertj.core.api.ListAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class InternalCommandPortListeningCheckWithScratchTest {
    @Parameterized.Parameters(name = "dockerfile.{0} -> {1})")
    public static Object[][] params() {
        return new Object[][]{
            new Object[]{"scratch", false},
            new Object[]{"alpine", true},
        };
    }

    @Parameterized.Parameter(0)
    public String dockerfileKind;

    @Parameterized.Parameter(1)
    public boolean expectedEmpty;

    @Test
    public void testDockerfileFromScratch() throws Throwable {
        List<ILoggingEvent> logEvents = runForLogEvents(() -> {
            ImageFromDockerfile image = new ImageFromDockerfile("tc-scratch-wait-hostport-strategy")
                // based on https://github.com/jeremyhuiskamp/golang-docker-scratch
                .withDockerfileFromClasspath("/scratch-wait-strategy-dockerfile/Dockerfile." + dockerfileKind)
                .withFileFromClasspath("go.mod", "/scratch-wait-strategy-dockerfile/go.mod")
                .withFileFromClasspath("hello-world.go", "/scratch-wait-strategy-dockerfile/hello-world.go");
            try (GenericContainer<?> container = new GenericContainer<>(image)
                .withCommand("/hello-world")
                .withExposedPorts(8080)
            ) {
                container.start();

                // check if ports are correctly published
                String response = responseFromUrl(new URL("http://" + container.getHost() + ":" + container.getFirstMappedPort() + "/helloworld"));
                assertThat(response).isEqualTo("Hello, World!");
            }
        });

        ListAssert<ILoggingEvent> asserting = assertThat(logEvents.stream()
            .filter(it -> it.getLevel() == Level.WARN)
            .filter(it -> "Unable to find '/bin/sh'. Does your Dockerfile extends scratch base Dockerfile?".equals(it.getMessage()))
            .collect(Collectors.toList())
        );
        if (expectedEmpty) {
            asserting.isEmpty();
        } else {
            asserting.isNotEmpty();
        }
    }

    private static List<ILoggingEvent> runForLogEvents(FailableRunnable<?> action) throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(InternalCommandPortListeningCheck.class);
        TestLogAppender testLogAppender = new TestLogAppender();
        logger.addAppender(testLogAppender);
        testLogAppender.start();
        try {
            action.run();
            return testLogAppender.events;
        } finally {
            testLogAppender.stop();
        }
    }

    private static String responseFromUrl(URL baseUrl) throws IOException {
        URLConnection urlConnection = baseUrl.openConnection();
        @Cleanup
        BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        return reader.readLine();
    }

    private static class TestLogAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events;

        private TestLogAppender() {
            this.events = new ArrayList<>();
        }

        @Override
        protected void append(ILoggingEvent eventObject) {
            events.add(eventObject);
        }
    }
}
