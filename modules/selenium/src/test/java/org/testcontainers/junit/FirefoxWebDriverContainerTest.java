package org.testcontainers.junit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;

/**
 *
 */
public class FirefoxWebDriverContainerTest extends BaseWebDriverContainerTest {

    // junitRule {
    @Rule
    public BrowserWebDriverContainer<?> firefox = new BrowserWebDriverContainer<>("selenium/standalone-firefox:4.1.1")
    // }
        .withNetwork(NETWORK);

    @Before
    public void checkBrowserIsIndeedFirefox() {
        assertBrowserNameIs(firefox, "firefox");
    }

    @Test
    public void simpleExploreTest() {
        doSimpleExplore(firefox);
    }
}
