package org.testcontainers.containers;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ComposeContainerTest {

    public static final String DOCKER_IMAGE = "docker:25.0.2";
    private TestLogAppender testLogAppender;

    private Logger rootLogger;

    @Before
    public void setup() {
        testLogAppender = new TestLogAppender();
        testLogAppender.start();
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(testLogAppender);
        TestcontainersConfiguration.getInstance().updateUserConfig("compose.container.image", DOCKER_IMAGE);
    }

    @After
    public void tearDown() {
        rootLogger.detachAppender(testLogAppender);
        TestcontainersConfiguration.getInstance().updateUserConfig("compose.container.image", "");
        System.clearProperty("compose.container.image");
    }

    @Test
    public void testWithCustomDockerImage() throws IOException {
        ComposeContainer composeContainer = new ComposeContainer(
            Lists.newArrayList(new File("src/test/resources/docker-compose-imagename-parsing-v2.yml"))
        );
        composeContainer.start();

        List<String> logs = testLogAppender.getLogs();
        composeContainer.stop();
        assertThat(logs).isNotNull();
        Optional<String> verification = logs
            .stream()
            .filter(line -> line.contains("Creating container for image: "+DOCKER_IMAGE))
            .findFirst();
        assertThat(verification.isPresent()).isTrue();
    }

    private static class TestLogAppender extends AppenderBase<ILoggingEvent> {

        private final List<String> logs = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent eventObject) {
            logs.add(eventObject.getFormattedMessage());
        }

        public List<String> getLogs() {
            return logs;
        }
    }
}
