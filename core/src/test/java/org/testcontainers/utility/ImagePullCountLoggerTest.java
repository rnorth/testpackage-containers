package org.testcontainers.utility;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImagePullCountLoggerTest {

    private ImagePullCountLogger underTest;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @Before
    public void setUp() throws Exception {
        logger = (Logger) LoggerFactory.getLogger(ImagePullCountLogger.class);
        listAppender = new ListAppender<>();
        logger.addAppender(listAppender);
        listAppender.start();
    }

    @Test
    public void testPullCountsLogged() {
        underTest = new ImagePullCountLogger();

        underTest.recordPull("imageA");
        underTest.recordPull("imageA");
        underTest.recordPull("imageB");
        underTest.recordPull("imageC");

        underTest.logStatistics();

        assertEquals(1, listAppender.list.size());
        final Optional<String> messages = listAppender.list.stream().map(ILoggingEvent::getFormattedMessage).findFirst();
        assertTrue(messages.isPresent());
        final String message = messages.get();
        assertTrue(message.contains("imageA (2 times)\n"));
        assertTrue(message.contains("imageB\n"));
        assertTrue(message.contains("imageC\n"));
    }

    @Test
    public void testNoPullsLogged() {
        underTest = new ImagePullCountLogger();

        underTest.logStatistics();

        assertEquals(1, listAppender.list.size());
        final Optional<String> messages = listAppender.list.stream().map(ILoggingEvent::getFormattedMessage).findFirst();
        assertTrue(messages.isPresent());
        final String message = messages.get();
        assertEquals("Testcontainers did not need to pull any images during execution", message);
    }

    @After
    public void tearDown() throws Exception {
        logger.detachAppender(listAppender);
    }
}
