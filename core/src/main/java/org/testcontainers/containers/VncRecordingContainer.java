package org.testcontainers.containers;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
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

    public void stopRecording() {
        if (isRunning()) {
            /*
             * The container doesn't have pkill, killall, or even kill, and doesn't have ps, so we get
             * a little interesting here. We look in /proc to find the processes, sed/grep/etc. our
             * way to success to get what we want, and then kill with the shell built-in.
             */
            String command =
                "kill -STOP " +
                "$(" +
                "grep -l -F flvrec.py /proc/*/cmdline" +
                " | sed 's,^/proc/,,;s,/.*,,'" +
                " | grep '^[0-9]*$'" +
                " | grep -v '^1$'" +
                " | sort -n" +
                " | head -1" +
                ")";
            final ExecResult results;
            try {
                results = execInContainer("sh", "-c", command);
            } catch (IOException e) {
                throw new RuntimeException("While trying to send SIGSTOP to the VNC recorder process", e);
            } catch (InterruptedException e) {
                throw new RuntimeException("While trying to send SIGSTOP to the VNC recorder process", e);
            }
            if (results.getExitCode() != 0) {
                throw new RuntimeException(
                    "Got non-zero exit code " +
                    results.getExitCode() +
                    " when attempting to " +
                    "send a SIGSTOP to the VNC recorder process in the container.\n" +
                    "Command: " +
                    command +
                    "\n" +
                    "Stdout: " +
                    results.getStdout() +
                    "\n" +
                    "Stderr: " +
                    results.getStderr()
                );
            }
        }
    }

    @SneakyThrows
    public InputStream streamRecording() {
        String newRecordingFileName = videoFormat.reencodeRecording(this, ORIGINAL_RECORDING_FILE_NAME);

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
                return execReencoder(
                    container,
                    new String[] { "ffmpeg", "-i", source, "-vcodec", "libx264", NEW_FILE_OUTPUT }
                );
            }
        },
        MP4("mp4") {
            @Override
            String reencodeRecording(@NonNull VncRecordingContainer container, @NonNull String source)
                throws IOException, InterruptedException {
                return execReencoder(
                    container,
                    new String[] {
                        "ffmpeg",
                        "-i",
                        source,
                        "-vcodec",
                        "libx264",
                        "-movflags",
                        "faststart",
                        "-pix_fmt",
                        "yuv420p",
                        NEW_FILE_OUTPUT,
                    }
                );
            }
        };

        public static final String NEW_FILE_OUTPUT = "/newScreen.mp4";

        abstract String reencodeRecording(VncRecordingContainer container, String source)
            throws IOException, InterruptedException;

        @Getter
        private final String filenameExtension;

        String execReencoder(@NonNull VncRecordingContainer container, @NonNull String[] command)
            throws IOException, InterruptedException {
            if (!container.isRunning()) {
                throw new IOException(
                    "VncRecordingContainer is not running when reencodeRecording was called: " +
                    container.getCurrentContainerInfo().getState() +
                    ": " +
                    container.getLogs()
                );
            }

            ExecResult results = container.execInContainer(command);
            if (results.getExitCode() != 0) {
                throw new RuntimeException(
                    "Got non-zero exit code " +
                    results.getExitCode() +
                    " when attempting to " +
                    "invoke ffmpeg in container to re-encode screen recording.\n" +
                    "Command: " +
                    StringUtils.join(command, ' ') +
                    "\n" +
                    "Stdout: " +
                    results.getStdout() +
                    "\n" +
                    "Stderr: " +
                    results.getStderr()
                );
            }
            return NEW_FILE_OUTPUT;
        }
    }
}
