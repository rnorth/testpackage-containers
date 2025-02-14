package org.testcontainers.utility;

import lombok.Cleanup;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class MountableFileTest {

    private static final int TEST_FILE_MODE = 0532;

    private static final int BASE_FILE_MODE = 0100000;

    private static final int BASE_DIR_MODE = 0040000;

    @Test
    public void forClasspathResource() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource("mappable-resource/test-resource.txt");

        performChecks(mountableFile);
    }

    @Test
    public void forClasspathResourceWithAbsolutePath() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource("/mappable-resource/test-resource.txt");

        performChecks(mountableFile);
    }

    @Test
    public void forClasspathResourceFromJar() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource("META-INF/dummy_unique_name.txt");

        performChecks(mountableFile);
    }

    @Test
    public void forClasspathResourceFromJarWithAbsolutePath() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource("/META-INF/dummy_unique_name.txt");

        performChecks(mountableFile);
    }

    @Test
    public void forHostPath() throws Exception {
        final Path file = createTempFile("somepath");
        final MountableFile mountableFile = MountableFile.forHostPath(file.toString());

        performChecks(mountableFile);
    }

    @Test
    public void forHostPathWithSpaces() throws Exception {
        final Path file = createTempFile("some path");
        final MountableFile mountableFile = MountableFile.forHostPath(file.toString());

        performChecks(mountableFile);

        assertThat(mountableFile.getResolvedPath()).as("The resolved path contains the original space").contains(" ");
        assertThat(mountableFile.getResolvedPath())
            .as("The resolved path does not contain an escaped space")
            .doesNotContain("\\ ");
    }

    @Test
    public void forHostPathWithPlus() throws Exception {
        final Path file = createTempFile("some+path");
        final MountableFile mountableFile = MountableFile.forHostPath(file.toString());

        performChecks(mountableFile);

        assertThat(mountableFile.getResolvedPath()).as("The resolved path contains the original space").contains("+");
        assertThat(mountableFile.getResolvedPath())
            .as("The resolved path does not contain an escaped space")
            .doesNotContain(" ");
    }

    @Test
    public void forClasspathResourceWithPermission() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource(
            "mappable-resource/test-resource.txt",
            TEST_FILE_MODE
        );

        performChecks(mountableFile);
        assertThat(mountableFile.getFileMode()).as("Valid file mode.").isEqualTo(BASE_FILE_MODE | TEST_FILE_MODE);
    }

    @Test
    public void forHostFilePathWithPermission() throws Exception {
        final Path file = createTempFile("somepath");
        final MountableFile mountableFile = MountableFile.forHostPath(file.toString(), TEST_FILE_MODE);
        performChecks(mountableFile);
        assertThat(mountableFile.getFileMode()).as("Valid file mode.").isEqualTo(BASE_FILE_MODE | TEST_FILE_MODE);
    }

    @Test
    public void forHostDirPathWithPermission() throws Exception {
        final Path dir = createTempDir();
        final MountableFile mountableFile = MountableFile.forHostPath(dir.toString(), TEST_FILE_MODE);
        performChecks(mountableFile);
        assertThat(mountableFile.getFileMode()).as("Valid dir mode.").isEqualTo(BASE_DIR_MODE | TEST_FILE_MODE);
    }

    @Test
    public void noTrailingSlashesInTarEntryNames() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource("mappable-resource/test-resource.txt");

        @Cleanup
        final TarArchiveInputStream tais = intoTarArchive(taos -> {
            mountableFile.transferTo(taos, "/some/path.txt");
            mountableFile.transferTo(taos, "/path.txt");
            mountableFile.transferTo(taos, "path.txt");
        });

        ArchiveEntry entry;
        while ((entry = tais.getNextEntry()) != null) {
            assertThat(entry.getName()).as("no entries should have a trailing slash").doesNotEndWith("/");
        }
    }

    @Test
    public void rootIsDefaultOwner() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource("mappable-resource/test-resource.txt");

        @Cleanup
        final TarArchiveInputStream tais = intoTarArchive(taos -> {
            mountableFile.transferTo(taos, "path.txt");
        });

        TarArchiveEntry entry;
        while ((entry = tais.getNextTarEntry()) != null) {
            assertEquals("User ID should be 0", 0, entry.getLongUserId());
            assertEquals("Group ID should be 0", 0, entry.getLongGroupId());
        }
    }

    @Test
    public void canSetOtherOwner() throws Exception {
        final MountableFile mountableFile = MountableFile.forClasspathResource(
            "mappable-resource/test-resource.txt",
            null,
            1,
            2
        );

        @Cleanup
        final TarArchiveInputStream tais = intoTarArchive(taos -> {
            mountableFile.transferTo(taos, "path.txt");
        });

        TarArchiveEntry entry;
        while ((entry = tais.getNextTarEntry()) != null) {
            assertEquals("User ID should be 1", 1L, entry.getLongUserId());
            assertEquals("Group ID should be 2", 2L, entry.getLongGroupId());
        }
    }

    private TarArchiveInputStream intoTarArchive(Consumer<TarArchiveOutputStream> consumer) throws IOException {
        @Cleanup
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        @Cleanup
        final TarArchiveOutputStream taos = new TarArchiveOutputStream(baos);
        consumer.accept(taos);
        taos.close();

        return new TarArchiveInputStream(new ByteArrayInputStream(baos.toByteArray()));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NotNull
    private Path createTempFile(final String name) throws IOException {
        final File tempParentDir = File.createTempFile("testcontainers", "");
        tempParentDir.delete();
        tempParentDir.mkdirs();
        final Path file = new File(tempParentDir, name).toPath();

        Files.copy(MountableFileTest.class.getResourceAsStream("/mappable-resource/test-resource.txt"), file);
        return file;
    }

    @NotNull
    private Path createTempDir() throws IOException {
        return Files.createTempDirectory("testcontainers");
    }

    private void performChecks(final MountableFile mountableFile) {
        final String mountablePath = mountableFile.getResolvedPath();
        assertThat(new File(mountablePath)).as("The filesystem path '" + mountablePath + "' can be found").exists();
        assertThat(mountablePath)
            .as("The filesystem path '" + mountablePath + "' does not contain any URL escaping")
            .doesNotContain("%20");
    }
}
