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

    private TestLogAppender testLogAppender;

    private Logger rootLogger;

    @Before
    public void setup() {
        testLogAppender = new TestLogAppender();
        testLogAppender.start();
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(testLogAppender);
    }

    @After
    public void tearDown() {
        rootLogger.detachAppender(testLogAppender);
    }

    @Test
    public void testWithCustomDockerImage() throws IOException {
        TestcontainersConfiguration.getInstance().updateUserConfig("compose.container.image", "docker:25.0.2");
        ComposeContainer composeContainer = new ComposeContainer(Lists.newArrayList(new File("src/test/resources/docker-compose-imagename-parsing-v2.yml")));
        composeContainer.start();
        System.clearProperty("compose.container.image");
        List<String> logs = testLogAppender.getLogs();
        composeContainer.stop();
        assertThat(logs).isNotNull();
        Optional<String> verification = logs.stream().filter(line -> line.contains("Creating container for image: docker:25.0.2")).findFirst();
        assertThat(verification.isPresent()).isTrue();
        TestcontainersConfiguration.getInstance().updateUserConfig("compose.container.image", "");
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

        public void clearLogs() {
            logs.clear();
        }
    }
}
