package org.testcontainers.images.builder.traits;

import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * BuildContextBuilder's trait for NIO-based (Files and Paths) manipulations.
 *
 */
public interface FilesTrait<SELF extends FilesTrait<SELF> & BuildContextBuilderTrait<SELF>> {

    /**
     * Adds file to tarball copied into container.
     * @param path in tarball
     * @param file in host filesystem
     * @return self
     */
    default SELF withFileFromFile(String path, File file) {
        return withFileFromPath(path, file.toPath(), null);
    }

    /**
     * Adds file to tarball copied into container.
     * @param path in tarball
     * @param filePath in host filesystem
     * @return self
     */
    default SELF withFileFromPath(String path, Path filePath) {
        return withFileFromPath(path, filePath, null);
    }

    /**
     * Adds file with given mode to tarball copied into container.
     * @param path in tarball
     * @param file in host filesystem
     * @param mode octal value of posix file mode (000..777)
     * @return self
     */
    default SELF withFileFromFile(String path, File file, Integer mode) {
        return withFileFromPath(path, file.toPath(), mode);
    }

    /**
     * Adds file with given mode to tarball copied into container.
     * @param path in tarball
     * @param filePath in host filesystem
     * @param mode octal value of posix file mode (000..777)
     * @return self
     */
    default SELF withFileFromPath(String path, Path filePath, Integer mode) {
        final boolean fileStoredToDir = Files.isRegularFile(filePath) &&
            (path.endsWith("/")
                || path.endsWith("/.")
                || path.endsWith("/..")
                || path.endsWith("./")
                || path.endsWith("../")
                || ".".equals(path)
                || "..".equals(path));
        if (fileStoredToDir) {
            throw new IllegalArgumentException("Unable to store file '" + filePath + "' to docker path '" + path + "'");
        }
        final MountableFile mountableFile = MountableFile.forHostPath(filePath, mode);
        return ((SELF) this).withFileFromTransferable(path, mountableFile);
    }
}
