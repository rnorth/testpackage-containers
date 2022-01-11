package org.testcontainers.junit;

import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 *
 */
public class CustomWaitTimeoutWebDriverContainerTest extends BaseWebDriverContainerTest {

    @Rule
    public BrowserWebDriverContainer<?> chromeWithCustomTimeout = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
        .withStartupTimeout(Duration.of(30, SECONDS))
        .withNetwork(NETWORK);

    @Test
    public void simpleExploreTest() {
        doSimpleExplore(chromeWithCustomTimeout);
    }
}
