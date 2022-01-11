package org.testcontainers.junit;


import com.google.common.io.PatternFilenameFilter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.DefaultRecordingFileFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.VncRecordingContainer;
import org.testcontainers.containers.VncRecordingContainer.VncRecordingFormat;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.lifecycle.TestDescription;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL;
import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode.RECORD_FAILING;

@RunWith(Enclosed.class)
public class ChromeRecordingWebDriverContainerTest extends BaseWebDriverContainerTest {

    /**
     * Guaranty a minimum video length for FFmpeg re-encoding.
     * @see VncRecordingFormat#reencodeRecording(VncRecordingContainer, String)
     */
    private static final int MINIMUM_VIDEO_DURATION_MILLISECONDS = 200;

    public static class ChromeThatRecordsAllTests {

        @Rule
        public TemporaryFolder vncRecordingDirectory = new TemporaryFolder();

        @Test
        public void recordingTestThatShouldBeRecordedAndRetainedInFlvFormatAsDefault() throws InterruptedException {
            File target = vncRecordingDirectory.getRoot();
            try (
                // recordAll {
                // To do this, simply add extra parameters to the rule constructor, so video will default to FLV format:
                BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
                    .withRecordingMode(RECORD_ALL, target)
                    // }
                    .withRecordingFileFactory(new DefaultRecordingFileFactory())
                    .withNetwork(NETWORK)
            ) {
                File[] files = runSimpleExploreInContainer(chrome, "PASSED-.*\\.flv");
                assertEquals("Recorded file not found", 1, files.length);
            }
        }

        private File[] runSimpleExploreInContainer(BrowserWebDriverContainer<?> container, String fileNamePattern) throws InterruptedException {
            container.start();

            TimeUnit.MILLISECONDS.sleep(MINIMUM_VIDEO_DURATION_MILLISECONDS);
            doSimpleExplore(container);
            container.afterTest(new TestDescription() {
                @Override
                public String getTestId() {
                    return getFilesystemFriendlyName();
                }

                @Override
                public String getFilesystemFriendlyName() {
                    return "ChromeThatRecordsAllTests-recordingTestThatShouldBeRecordedAndRetained";
                }
            }, Optional.empty());

            return vncRecordingDirectory.getRoot().listFiles(new PatternFilenameFilter(fileNamePattern));
        }

        @Test
        public void recordingTestShouldHaveFlvExtension() throws InterruptedException {
            File target = vncRecordingDirectory.getRoot();
            try (
                // recordFlv {
                // Set (explicitly) FLV format for recorded video:
                BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
                    .withRecordingMode(RECORD_ALL, target, VncRecordingFormat.FLV)
                    // }
                    .withRecordingFileFactory(new DefaultRecordingFileFactory())
                    .withNetwork(NETWORK)
            ) {
                File[] files = runSimpleExploreInContainer(chrome, "PASSED-.*\\.flv");
                assertEquals("Recorded file not found", 1, files.length);
            }
        }

        @Test
        public void recordingTestShouldHaveMp4Extension() throws InterruptedException {
            File target = vncRecordingDirectory.getRoot();
            try (
                // recordMp4 {
                // Set MP4 format for recorded video:
                BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
                    .withRecordingMode(RECORD_ALL, target, VncRecordingFormat.MP4)
                    // }
                    .withRecordingFileFactory(new DefaultRecordingFileFactory())
                    .withNetwork(NETWORK)
            ) {
                File[] files = runSimpleExploreInContainer(chrome, "PASSED-.*\\.mp4");
                assertEquals("Recorded file not found", 1, files.length);
            }
        }

        @Test
        public void recordingTestThatShouldHaveCorrectDuration() throws IOException, InterruptedException {
            MountableFile mountableFile;
            try (
                BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
                    .withRecordingMode(RECORD_ALL, vncRecordingDirectory.getRoot())
                    .withRecordingFileFactory(new DefaultRecordingFileFactory())
                    .withNetwork(NETWORK)
            ) {
                File[] recordedFiles = runSimpleExploreInContainer(chrome, "PASSED-.*\\.flv");
                mountableFile = MountableFile.forHostPath(recordedFiles[0].getCanonicalPath());
            }

            try (GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("testcontainers/vnc-recorder:1.2.0"))) {
                String recordFileContainerPath = "/tmp/chromeTestRecord.flv";
                container.withCopyFileToContainer(mountableFile, recordFileContainerPath)
                        .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withEntrypoint("ffmpeg"))
                        .withCommand("-i", recordFileContainerPath, "-f", "null", "-")
                        .waitingFor(new LogMessageWaitStrategy()
                                                .withRegEx(".*Duration.*")
                                                .withStartupTimeout(Duration.of(60, SECONDS)))
                        .start();
                String ffmpegOutput = container.getLogs();

                assertTrue("Duration is incorrect in:\n " + ffmpegOutput,
                    ffmpegOutput.contains("Duration: 00:") && !(ffmpegOutput.contains("Duration: 00:00:00.00")));
            }
        }
    }

    public static class ChromeThatRecordsFailingTests {

        @Rule
        public TemporaryFolder vncRecordingDirectory = new TemporaryFolder();

        @Test
        public void recordingTestThatShouldBeRecordedButNotPersisted() {
            try (
                // withRecordingFileFactory {
                BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
                    // }
                    // withRecordingFileFactory {
                    .withRecordingFileFactory(new CustomRecordingFileFactory())
                    // }
                    .withNetwork(NETWORK)
            ) {
                chrome.start();

                doSimpleExplore(chrome);
            }
        }

        @Test
        public void recordingTestThatShouldBeRecordedAndRetained() throws InterruptedException {
            File target = vncRecordingDirectory.getRoot();
            try (
                // recordFailing {
                // or if you only want videos for test failures:
                BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4.1.1")
                    .withRecordingMode(RECORD_FAILING, target)
                    // }
                    .withRecordingFileFactory(new DefaultRecordingFileFactory())
                    .withNetwork(NETWORK)
            ) {
                chrome.start();

                TimeUnit.MILLISECONDS.sleep(MINIMUM_VIDEO_DURATION_MILLISECONDS);
                doSimpleExplore(chrome);
                chrome.afterTest(new TestDescription() {
                    @Override
                    public String getTestId() {
                        return getFilesystemFriendlyName();
                    }

                    @Override
                    public String getFilesystemFriendlyName() {
                        return "ChromeThatRecordsFailingTests-recordingTestThatShouldBeRecordedAndRetained";
                    }
                }, Optional.of(new RuntimeException("Force writing of video file.")));

                String[] files = vncRecordingDirectory.getRoot().list(new PatternFilenameFilter("FAILED-.*\\.flv"));
                assertEquals("Recorded file not found", 1, files.length);
            }

        }

        private static class CustomRecordingFileFactory extends DefaultRecordingFileFactory {
        }
    }
}
