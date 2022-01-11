package org.testcontainers.junit;

import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.BrowserWebDriverContainer;

public class ContainerWithoutCapabilitiesTest extends BaseWebDriverContainerTest{

    @Rule
    public BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
        .withNetwork(NETWORK);

    @Test
    public void chromeIsStartedIfNoCapabilitiesProvided() {
        assertBrowserNameIs(chrome, "chrome");
    }

    @Test
    public void simpleExploreTestWhenNoCapabilitiesProvided(){
        doSimpleExplore(chrome);
    }
}
