package org.testcontainers.containers;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 'Sidekick container' with the sole purpose of recording the VNC screen output from another container.
 *
 */
@Getter
@ToString
public class VncRecordingContainer extends GenericContainer<VncRecordingContainer> {

    private static final String ORIGINAL_RECORDING_FILE_NAME = "/screen.flv";

    private static final String COPY_OF_ORIGINAL_RECORDING_FILE_NAME = "/screen_copy.flv";

    public static final String DEFAULT_VNC_PASSWORD = "secret";

    public static final int DEFAULT_VNC_PORT = 5900;

    static final VncRecordingFormat DEFAULT_RECORDING_FORMAT = VncRecordingFormat.FLV;

    private final String targetNetworkAlias;

    private String vncPassword = DEFAULT_VNC_PASSWORD;

    private VncRecordingFormat videoFormat = DEFAULT_RECORDING_FORMAT;

    private int vncPort = 5900;

    private int frameRate = 30;

    public VncRecordingContainer(@NonNull GenericContainer<?> targetContainer) {
        this(
            targetContainer.getNetwork(),
            targetContainer
                .getNetworkAliases()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Target container must have a network alias"))
        );
    }

    /**
     * Create a sidekick container and attach it to another container. The VNC output of that container will be recorded.
     */
    public VncRecordingContainer(@NonNull Network network, @NonNull String targetNetworkAlias)
        throws IllegalStateException {
        super(DockerImageName.parse("testcontainers/vnc-recorder:1.3.0"));
        this.targetNetworkAlias = targetNetworkAlias;
        withNetwork(network);
        waitingFor(
            new LogMessageWaitStrategy()
                .withRegEx(".*Connected.*")
                .withStartupTimeout(Duration.of(15, ChronoUnit.SECONDS))
        );
    }

    public VncRecordingContainer withVncPassword(@NonNull String vncPassword) {
        this.vncPassword = vncPassword;
        return this;
    }

    public VncRecordingContainer withVncPort(int vncPort) {
        this.vncPort = vncPort;
        return this;
    }

    public VncRecordingContainer withVideoFormat(VncRecordingFormat videoFormat) {
        if (videoFormat != null) {
            this.videoFormat = videoFormat;
        }
        return this;
    }

    public VncRecordingContainer withFrameRate(int frameRate) {
        this.frameRate = frameRate;
        return this;
    }

    @Override
    protected void configure() {
        withCreateContainerCmdModifier(it -> it.withEntrypoint("/bin/sh"));
        String encodedPassword = Base64.getEncoder().encodeToString(vncPassword.getBytes());
        setCommand(
            "-c",
            "echo '" +
            encodedPassword +
            "' | base64 -d > /vnc_password && " +
            "flvrec.py -o " +
            ORIGINAL_RECORDING_FILE_NAME +
            " -d -r " +
            frameRate +
            " -P /vnc_password " +
            targetNetworkAlias +
            " " +
            vncPort
        );
    }

    @SneakyThrows
    public InputStream streamRecording() {
        execInContainer("cp", ORIGINAL_RECORDING_FILE_NAME, COPY_OF_ORIGINAL_RECORDING_FILE_NAME);
        String newRecordingFileName = videoFormat.reencodeRecording(this, COPY_OF_ORIGINAL_RECORDING_FILE_NAME);

        TarArchiveInputStream archiveInputStream = new TarArchiveInputStream(
            dockerClient.copyArchiveFromContainerCmd(getContainerId(), newRecordingFileName).exec()
        );
        archiveInputStream.getNextEntry();
        return archiveInputStream;
    }

    @SneakyThrows
    public void saveRecordingToFile(@NonNull File file) {
        try (InputStream inputStream = streamRecording()) {
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @RequiredArgsConstructor
    public enum VncRecordingFormat {
        FLV("flv") {
            @Override
            String reencodeRecording(@NonNull VncRecordingContainer container, @NonNull String source)
                throws IOException, InterruptedException {
                String newFileOutput = "/newScreen.flv";
                container.execInContainer("ffmpeg", "-i", source, "-vcodec", "libx264", newFileOutput);
                return newFileOutput;
            }
        },
        MP4("mp4") {
            @Override
            String reencodeRecording(@NonNull VncRecordingContainer container, @NonNull String source)
                throws IOException, InterruptedException {
                String newFileOutput = "/newScreen.mp4";
                container.execInContainer(
                    "ffmpeg",
                    "-i",
                    source,
                    "-vcodec",
                    "libx264",
                    "-movflags",
                    "faststart",
                    "-pix_fmt",
                    "yuv420p",
                    newFileOutput
                );
                return newFileOutput;
            }
        };

        abstract String reencodeRecording(VncRecordingContainer container, String source)
            throws IOException, InterruptedException;

        @Getter
        private final String filenameExtension;
    }
}
