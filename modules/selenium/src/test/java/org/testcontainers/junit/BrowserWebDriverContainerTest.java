package org.testcontainers.junit;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;

public class BrowserWebDriverContainerTest {

    private static final String NO_PROXY_KEY = "no_proxy";

    private static final String NO_PROXY_VALUE = "localhost,.noproxy-domain.com";

    private static final String CHROME_IMAGE = "selenium/standalone-chrome:4.1.1";
    private static final String FIREFOX_IMAGE = "selenium/standalone-firefox:4.1.1";

    @Test
    public void honorPresetNoProxyEnvironment() {
        try (
            BrowserWebDriverContainer<?> chromeWithNoProxySet = new BrowserWebDriverContainer<>(CHROME_IMAGE)
                .withEnv(NO_PROXY_KEY, NO_PROXY_VALUE)
        ) {
            chromeWithNoProxySet.start();

            Object noProxy = chromeWithNoProxySet.getEnvMap().get(NO_PROXY_KEY);
            assertEquals("no_proxy should be preserved by the container rule", NO_PROXY_VALUE, noProxy);
        }
    }

    @Test
    public void provideDefaultNoProxyEnvironmentIfNotSet() {
        try (
            BrowserWebDriverContainer<?> chromeWithoutNoProxySet = new BrowserWebDriverContainer<>(CHROME_IMAGE)
        ) {
            chromeWithoutNoProxySet.start();

            Object noProxy = chromeWithoutNoProxySet.getEnvMap().get(NO_PROXY_KEY);
            assertEquals("no_proxy should be set to default if not already present", "localhost", noProxy);
        }
    }


    @Test
    public void createContainerWithShmVolume() {
        try (
            BrowserWebDriverContainer<?> webDriverContainer = new BrowserWebDriverContainer<>(FIREFOX_IMAGE)
        ) {
            webDriverContainer.start();

            final List<InspectContainerResponse.Mount> shmVolumes = shmVolumes(webDriverContainer);

            assertEquals("Only one shm mount present", 1, shmVolumes.size());
            assertEquals("Shm mount source is correct", "/dev/shm", shmVolumes.get(0).getSource());
            assertEquals("Shm mount mode is correct", "rw", shmVolumes.get(0).getMode());
        }
    }

    @Test
    public void createContainerWithoutShmVolume() {
        try (
            BrowserWebDriverContainer<?> webDriverContainer = new BrowserWebDriverContainer<>(FIREFOX_IMAGE)
                .withSharedMemorySize(512 * FileUtils.ONE_MB)
        ) {
            webDriverContainer.start();

            assertEquals("Shared memory size is configured",
                512 * FileUtils.ONE_MB,
                webDriverContainer.getShmSize());

            assertEquals("No shm mounts present", emptyList(), shmVolumes(webDriverContainer));
        }
    }

    private List<InspectContainerResponse.Mount> shmVolumes(final BrowserWebDriverContainer<?> container) {
        return container.getContainerInfo().getMounts()
            .stream()
            // destination path is always /dev/shm
            .filter(m -> m.getDestination().getPath().equals("/dev/shm"))
            .collect(toList());
    }
}
