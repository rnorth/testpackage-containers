package org.testcontainers.containers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.rnorth.ducttape.timeouts.Timeouts;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.VncRecordingContainer.VncRecordingFormat;
import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.lifecycle.TestDescription;
import org.testcontainers.lifecycle.TestLifecycleAware;
import org.testcontainers.utility.ComparableVersion;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * A chrome/firefox/custom container based on SeleniumHQ's standalone container sets.
 * <p>
 * The container should expose Selenium remote control protocol and VNC.
 */
public class BrowserWebDriverContainer<SELF extends BrowserWebDriverContainer<SELF>> extends GenericContainer<SELF> implements LinkableContainer, TestLifecycleAware {

    private static final DockerImageName CHROME_IMAGE = DockerImageName.parse("selenium/standalone-chrome");
    private static final DockerImageName FIREFOX_IMAGE = DockerImageName.parse("selenium/standalone-firefox");
    private static final DockerImageName CHROME_DEBUG_IMAGE = DockerImageName.parse("selenium/standalone-chrome-debug");
    private static final DockerImageName FIREFOX_DEBUG_IMAGE = DockerImageName.parse("selenium/standalone-firefox-debug");
    private static final DockerImageName[] COMPATIBLE_IMAGES = new DockerImageName[] {
        CHROME_DEBUG_IMAGE,
        FIREFOX_DEBUG_IMAGE,
        CHROME_IMAGE,
        FIREFOX_IMAGE
    };

    private static final String DEFAULT_PASSWORD = "secret";
    private static final int SELENIUM_PORT = 4444;
    private static final int VNC_PORT = 5900;

    private static final String NO_PROXY_KEY = "no_proxy";
    private static final String TC_TEMP_DIR_PREFIX = "tc";

    @Nullable
    private Capabilities capabilities;
    private DockerImageName customImageName = null;

    @Nullable
    private RemoteWebDriver driver;
    private VncRecordingMode recordingMode = VncRecordingMode.RECORD_FAILING;
    private VncRecordingFormat recordingFormat;
    private RecordingFileFactory recordingFileFactory;
    private File vncRecordingDirectory;

    private VncRecordingContainer vncRecordingContainer = null;

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserWebDriverContainer.class);

    /**
     * @deprecated please use {@link BrowserWebDriverContainer#BrowserWebDriverContainer(DockerImageName)}
     */
    @Deprecated
    public BrowserWebDriverContainer() {
        super();
        final WaitStrategy logWaitStrategy = new LogMessageWaitStrategy()
                .withRegEx(".*(RemoteWebDriver instances should connect to|Selenium Server is up and running|Started Selenium Standalone).*\n")
                .withStartupTimeout(Duration.of(15, SECONDS));

        this.waitStrategy = new WaitAllStrategy()
                .withStrategy(logWaitStrategy)
                .withStrategy(new HostPortWaitStrategy())
                .withStartupTimeout(Duration.of(15, SECONDS));

        this.withRecordingFileFactory(new DefaultRecordingFileFactory());
    }

    /**
     * Constructor taking a specific webdriver container name and tag
     * @param dockerImageName Name of the selenium docker image
     */
    public BrowserWebDriverContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * Constructor taking a specific webdriver container name and tag
     * @param dockerImageName Name of the selenium docker image
     */
    public BrowserWebDriverContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        // we assert compatibility with the chrome/firefox image later, after capabilities are processed

        final WaitStrategy logWaitStrategy = new LogMessageWaitStrategy()
            .withRegEx(".*(RemoteWebDriver instances should connect to|Selenium Server is up and running|Started Selenium Standalone).*\n")
            .withStartupTimeout(Duration.of(15, SECONDS));

        this.waitStrategy = new WaitAllStrategy()
            .withStrategy(logWaitStrategy)
            .withStrategy(new HostPortWaitStrategy())
            .withStartupTimeout(Duration.of(15, SECONDS));

        this.withRecordingFileFactory(new DefaultRecordingFileFactory());

        this.customImageName = dockerImageName;
        // We have to force SKIP mode for the recording by default because we don't know if the image has VNC or not
        recordingMode = VncRecordingMode.SKIP;
    }

    /**
     * @deprecated please use {@link BrowserWebDriverContainer#getSeleniumAddress()} to obtain the selenium server URL,
     * and call the {@link RemoteWebDriver} constructor ({@link RemoteWebDriver#RemoteWebDriver(URL, Capabilities)}),
     * passing in the URL and {@link Capabilities} object instead.
     *
     * @param capabilities Capabilities
     * @return SELF
     */
    @Deprecated
    public SELF withCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
        return self();
    }

    /**
     * @deprecated please use {@link BrowserWebDriverContainer#getSeleniumAddress()} to obtain the selenium server URL,
     * and call the {@link RemoteWebDriver} constructor ({@link RemoteWebDriver#RemoteWebDriver(URL, Capabilities)}),
     * passing in the URL and {@link Capabilities} object instead.
     *
     * @param capabilities DesiredCapabilities
     * @return SELF
     * */
    @Deprecated
    public SELF withDesiredCapabilities(DesiredCapabilities capabilities) {
        this.capabilities = capabilities;
        return self();
    }

    @NotNull
    @Override
    protected Set<Integer> getLivenessCheckPorts() {
        Integer seleniumPort = getMappedPort(SELENIUM_PORT);
        if (recordingMode == VncRecordingMode.SKIP) {
            return ImmutableSet.of(seleniumPort);
        } else {
            return ImmutableSet.of(seleniumPort, getMappedPort(VNC_PORT));
        }
    }

    @Override
    protected void configure() {
        String seleniumVersion = SeleniumUtils.determineClasspathSeleniumVersion();

        if (recordingMode != VncRecordingMode.SKIP) {

            if (vncRecordingDirectory == null) {
                try {
                    vncRecordingDirectory = Files.createTempDirectory(TC_TEMP_DIR_PREFIX).toFile();
                } catch (IOException e) {
                    // should never happen as per javadoc, since we use valid prefix
                    logger().error("Exception while trying to create temp directory", e);
                    throw new ContainerLaunchException("Exception while trying to create temp directory", e);
                }
            }

            if (getNetwork() == null) {
                withNetwork(Network.SHARED);
            }

            vncRecordingContainer = new VncRecordingContainer(this)
                    .withVncPassword(DEFAULT_PASSWORD)
                    .withVncPort(VNC_PORT)
                    .withVideoFormat(recordingFormat);
        }

        if (customImageName != null) {
            customImageName.assertCompatibleWith(COMPATIBLE_IMAGES);
            super.setDockerImageName(customImageName.asCanonicalNameString());
        } else {
            DockerImageName standardImageForCapabilities = getStandardImageForCapabilities(capabilities, seleniumVersion);
            logger().warn(
                "Image name for selenium image has not been set, and one will be inferred automatically based " +
                    "on capabilities ({}). Inferred image: {}. This feature is deprecated and will be removed " +
                    "in the future.",
                standardImageForCapabilities.asCanonicalNameString(),
                this.capabilities
            );
            super.setDockerImageName(standardImageForCapabilities.asCanonicalNameString());
        }

        String timeZone = System.getProperty("user.timezone");

        if (timeZone == null || timeZone.isEmpty()) {
            timeZone = "Etc/UTC";
        }

        addExposedPorts(SELENIUM_PORT, VNC_PORT);
        addEnv("TZ", timeZone);

        if (!getEnvMap().containsKey(NO_PROXY_KEY)) {
            addEnv(NO_PROXY_KEY, "localhost");
        }

        setCommand("/opt/bin/entry_point.sh");

        if (getShmSize() == null) {
            this.getBinds().add(new Bind("/dev/shm", new Volume("/dev/shm"), AccessMode.rw));
        }

        /*
         * Some unreliability of the selenium browser containers has been observed, so allow multiple attempts to start.
         */
        setStartupAttempts(3);
    }

    /**
     * @param capabilities a {@link Capabilities} object for either Chrome or Firefox
     * @param seleniumVersion the version of selenium in use
     * @return an image name for the default standalone Docker image for the appropriate browser
     *
     * @deprecated note that this method is deprecated and will be removed in the future.
     */
    @Deprecated
    public static String getDockerImageForCapabilities(Capabilities capabilities, String seleniumVersion) {
        return getStandardImageForCapabilities(capabilities, seleniumVersion).asCanonicalNameString();
    }

    private static DockerImageName getStandardImageForCapabilities(Capabilities capabilities, String seleniumVersion) {
        boolean supportsVncWithoutDebugImage = new ComparableVersion(seleniumVersion).isGreaterThanOrEqualTo("4");

        String browserName;
        if (capabilities == null) {
            // opinionated default for deprecated path
            browserName = "chrome";
        } else {
            browserName = capabilities.getBrowserName();
        }

        if (browserName.equals("chrome")) {
            if (supportsVncWithoutDebugImage) {
                return CHROME_IMAGE;
            } else {
                return CHROME_DEBUG_IMAGE;
            }
        } else if (browserName.equals("firefox")) {
            if (supportsVncWithoutDebugImage) {
                return FIREFOX_IMAGE;
            } else {
                return FIREFOX_DEBUG_IMAGE;
            }
        }

        throw new UnsupportedOperationException("Browser name must be 'chrome' or 'firefox'; provided '" + browserName + "' is not supported");
    }

    public URL getSeleniumAddress() {
        try {
            return new URL("http", getHost(), getMappedPort(SELENIUM_PORT), "/wd/hub");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to construct a valid URL!", e);
        }
    }

    public String getVncAddress() {
        return "vnc://vnc:secret@" + getHost() + ":" + getMappedPort(VNC_PORT);
    }

    @Deprecated
    public String getPassword() {
        return DEFAULT_PASSWORD;
    }

    @Deprecated
    public int getPort() {
        return VNC_PORT;
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (vncRecordingContainer != null) {
            LOGGER.debug("Starting VNC recording");
            vncRecordingContainer.start();
        }
    }

    /**
     * Obtain a RemoteWebDriver instance that is bound to an instance of the browser running inside a new container.
     * <p>
     * All containers and drivers will be automatically shut down after the test method finishes (if used as a @Rule) or the test
     * class (if used as a @ClassRule)
     *
     * @deprecated please use {@link BrowserWebDriverContainer#getSeleniumAddress()} to obtain the selenium server URL,
     * and call the {@link RemoteWebDriver} constructor ({@link RemoteWebDriver#RemoteWebDriver(URL, Capabilities)}),
     * passing in the URL and {@link Capabilities} object instead.
     *
     * @return a new Remote Web Driver instance
     */
    @Deprecated
    public synchronized RemoteWebDriver getWebDriver() {
        if (driver == null) {
            if (capabilities == null) {
                logger().warn("No capabilities provided - this will cause an exception in future versions. Falling back to ChromeOptions");
                capabilities = new ChromeOptions();
            }

            driver = Unreliables.retryUntilSuccess(30, TimeUnit.SECONDS,
                () -> Timeouts.getWithTimeout(10, TimeUnit.SECONDS,
                    () -> new RemoteWebDriver(getSeleniumAddress(), capabilities)));
        }

        return driver;
    }

    @Override
    public void afterTest(TestDescription description, Optional<Throwable> throwable) {
        retainRecordingIfNeeded(description.getFilesystemFriendlyName(), !throwable.isPresent());
    }

    @Override
    public void stop() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                LOGGER.debug("Failed to quit the driver", e);
            }
        }

        if (vncRecordingContainer != null) {
            try {
                vncRecordingContainer.stop();
            } catch (Exception e) {
                LOGGER.debug("Failed to stop vncRecordingContainer", e);
            }
        }

        super.stop();
    }

    private void retainRecordingIfNeeded(String prefix, boolean succeeded) {
        final boolean shouldRecord;
        switch (recordingMode) {
            case RECORD_ALL:
                shouldRecord = true;
                break;
            case RECORD_FAILING:
                shouldRecord = !succeeded;
                break;
            default:
                shouldRecord = false;
                break;
        }

        if (shouldRecord) {
            File recordingFile = recordingFileFactory.recordingFileForTest(vncRecordingDirectory, prefix, succeeded, vncRecordingContainer.getVideoFormat());
            LOGGER.info("Screen recordings for test {} will be stored at: {}", prefix, recordingFile);

            vncRecordingContainer.saveRecordingToFile(recordingFile);
        }
    }

    /**
     * Remember any other containers this needs to link to. We have to pass these down to the container so that
     * the other containers will be initialized before linking occurs.
     *
     * @param otherContainer the container rule to link to
     * @param alias          the alias (hostname) that this other container should be referred to by
     * @return this
     *
     * @deprecated Links are deprecated (see <a href="https://github.com/testcontainers/testcontainers-java/issues/465">#465</a>). Please use {@link Network} features instead.
     */
    @Deprecated
    public SELF withLinkToContainer(LinkableContainer otherContainer, String alias) {
        addLink(otherContainer, alias);
        return self();
    }

    public SELF withRecordingMode(VncRecordingMode recordingMode, File vncRecordingDirectory) {
        return withRecordingMode(recordingMode, vncRecordingDirectory, null);
    }

    public SELF withRecordingMode(VncRecordingMode recordingMode, File vncRecordingDirectory, VncRecordingFormat recordingFormat) {
        this.recordingMode = recordingMode;
        this.vncRecordingDirectory = vncRecordingDirectory;
        this.recordingFormat = recordingFormat;
        return self();
    }

    public SELF withRecordingFileFactory(RecordingFileFactory recordingFileFactory) {
        this.recordingFileFactory = recordingFileFactory;
        return self();
    }

    public enum VncRecordingMode {
        SKIP, RECORD_ALL, RECORD_FAILING
    }
}
