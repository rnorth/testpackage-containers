package org.testcontainers.junit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Simple test to check that readiness detection works correctly across major versions of the containers.
 */
@RunWith(Parameterized.class)
public class SeleniumStartTest {

    @Parameterized.Parameters(name = "tag: {0}")
    public static String[] data() {
        return new String[]{"4.0.0", "3.4.0"};
    }

    @Parameterized.Parameter()
    public String tag;

    @Test
    public void testAdditionalStartupString() {
        final DockerImageName imageName = DockerImageName
            .parse("selenium/standalone-chrome")
            .withTag(tag);
        try (BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>(imageName)
                .withCapabilities(new ChromeOptions())) {
            chrome.start();
        }
    }
}
