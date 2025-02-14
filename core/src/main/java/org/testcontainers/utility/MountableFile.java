package org.testcontainers.utility;

import com.google.common.base.Charsets;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.UnstableAPI;
import org.testcontainers.images.builder.Transferable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.Checksum;

/**
 * An abstraction over files and classpath resources aimed at encapsulating all the complexity of generating
 * a path that the Docker daemon is about to create a volume mount for.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
public class MountableFile implements Transferable {

    private static final String TESTCONTAINERS_TMP_DIR_PREFIX = ".testcontainers-tmp-";

    private static final String OS_MAC_TMP_DIR = "/tmp";

    private static final int BASE_FILE_MODE = 0100000;

    private static final int BASE_DIR_MODE = 0040000;

    private final String path;

    private final Integer forcedFileMode;

    private final long userId;

    private final long groupId;

    @Getter(lazy = true)
    private final String resolvedPath = resolvePath();

    @Getter(lazy = true)
    private final String filesystemPath = resolveFilesystemPath();

    private String resourcePath;

    /**
     * Obtains a {@link MountableFile} corresponding to a resource on the classpath (including resources in JAR files)
     *
     * @param resourceName the classpath path to the resource
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forClasspathResource(@NotNull final String resourceName) {
        return forClasspathResource(resourceName, null);
    }

    /**
     * Obtains a {@link MountableFile} corresponding to a file on the docker host filesystem.
     *
     * @param path the path to the resource
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forHostPath(@NotNull final String path) {
        return forHostPath(path, null);
    }

    /**
     * Obtains a {@link MountableFile} corresponding to a file on the docker host filesystem.
     *
     * @param path the path to the resource
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forHostPath(final Path path) {
        return forHostPath(path, null);
    }

    /**
     * Obtains a {@link MountableFile} corresponding to a resource on the classpath (including resources in JAR files)
     *
     * @param resourceName the classpath path to the resource
     * @param mode octal value of posix file mode (000..777)
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forClasspathResource(@NotNull final String resourceName, Integer mode) {
        return forClasspathResource(getClasspathResource(resourceName, new HashSet<>()).toString(), mode, 0, 0);
    }

    /**
     * Obtains a {@link MountableFile} corresponding to a resource on the classpath (including resources in JAR files)
     *
     * @param resourceName the classpath path to the resource
     * @param mode octal value of posix file mode (000..777)
     * @param userId the id of the user owning the file
     * @param groupId the id of the group owning the file
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forClasspathResource(@NotNull final String resourceName, Integer mode, int userId, int groupId) {
        return new MountableFile(getClasspathResource(resourceName, new HashSet<>()).toString(), mode, userId, groupId);
    }

    /**
     * Obtains a {@link MountableFile} corresponding to a file on the docker host filesystem.
     *
     * @param path the path to the resource
     * @param mode octal value of posix file mode (000..777)
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forHostPath(@NotNull final String path, Integer mode) {
        return forHostPath(Paths.get(path), mode);
    }

    /**
     * Obtains a {@link MountableFile} corresponding to a file on the docker host filesystem.
     *
     * @param path the path to the resource
     * @param mode octal value of posix file mode (000..777)
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forHostPath(final Path path, Integer mode) {
        return new MountableFile(path.toAbsolutePath().toString(), mode, 0, 0);
    }

    /**
     * Obtains a {@link MountableFile} corresponding to a file on the docker host filesystem.
     *
     * @param path the path to the resource
     * @param mode octal value of posix file mode (000..777)
     * @param userId the id of the user owning the file
     * @param groupId the id of the group owning the file
     * @return a {@link MountableFile} that may be used to obtain a mountable path
     */
    public static MountableFile forHostPath(final Path path, Integer mode, int userId, int groupId) {
        return new MountableFile(path.toAbsolutePath().toString(), mode, userId, groupId);
    }

    @NotNull
    private static URL getClasspathResource(
        @NotNull final String resourcePath,
        @NotNull final Set<ClassLoader> classLoaders
    ) {
        final Set<ClassLoader> classLoadersToSearch = new HashSet<>(classLoaders);
        // try context and system classloaders as well
        classLoadersToSearch.add(Thread.currentThread().getContextClassLoader());
        classLoadersToSearch.add(ClassLoader.getSystemClassLoader());
        classLoadersToSearch.add(MountableFile.class.getClassLoader());

        for (final ClassLoader classLoader : classLoadersToSearch) {
            if (classLoader == null) {
                continue;
            }

            URL resource = classLoader.getResource(resourcePath);
            if (resource != null) {
                return resource;
            }

            // Be lenient if an absolute path was given
            if (resourcePath.startsWith("/")) {
                resource = classLoader.getResource(resourcePath.replaceFirst("/", ""));
                if (resource != null) {
                    return resource;
                }
            }
        }

        throw new IllegalArgumentException(
            "Resource with path " +
            resourcePath +
            " could not be found on any of these classloaders: " +
            classLoadersToSearch
        );
    }

    private static String unencodeResourceURIToFilePath(@NotNull final String resource) {
        try {
            // Convert any url-encoded characters (e.g. spaces) back into unencoded form
            return URLDecoder
                .decode(resource.replaceAll("\\+", "%2B"), Charsets.UTF_8.name())
                .replaceFirst("jar:", "")
                .replaceFirst("file:", "")
                .replaceAll("!.*", "");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Obtain a path that the Docker daemon should be able to use to volume mount a file/resource
     * into a container. If this is a classpath resource residing in a JAR, it will be extracted to
     * a temporary location so that the Docker daemon is able to access it.
     *
     * @return a volume-mountable path.
     */
    private String resolvePath() {
        String result = getResourcePath();

        // Special case for Windows
        if (SystemUtils.IS_OS_WINDOWS && result.startsWith("/")) {
            // Remove leading /
            result = result.substring(1);
        }

        return result;
    }

    /**
     * Obtain a path in local filesystem that the Docker daemon should be able to use to volume mount a file/resource
     * into a container. If this is a classpath resource residing in a JAR, it will be extracted to
     * a temporary location so that the Docker daemon is able to access it.
     *
     * TODO: rename method accordingly and check if really needed like this
     *
     * @return
     */
    private String resolveFilesystemPath() {
        String result = getResourcePath();

        if (SystemUtils.IS_OS_WINDOWS && result.startsWith("/")) {
            result = PathUtils.createMinGWPath(result).substring(1);
        }

        return result;
    }

    private String getResourcePath() {
        if (path.contains(".jar!")) {
            resourcePath = extractClassPathResourceToTempLocation(this.path);
        } else {
            resourcePath = unencodeResourceURIToFilePath(path);
        }
        return resourcePath;
    }

    /**
     * Extract a file or directory tree from a JAR file to a temporary location.
     * This allows Docker to mount classpath resources as files.
     *
     * @param hostPath the path on the host, expected to be of the format 'file:/path/to/some.jar!/classpath/path/to/resource'
     * @return the path of the temporary file/directory
     */
    private String extractClassPathResourceToTempLocation(final String hostPath) {
        File tmpLocation = createTempDirectory();
        //noinspection ResultOfMethodCallIgnored
        tmpLocation.delete();

        String urldecodedJarPath = unencodeResourceURIToFilePath(hostPath);
        String internalPath = hostPath.replaceAll("[^!]*!/", "");

        try (JarFile jarFile = new JarFile(urldecodedJarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (name.startsWith(internalPath)) {
                    log.debug(
                        "Copying classpath resource(s) from {} to {} to permit Docker to bind",
                        hostPath,
                        tmpLocation
                    );
                    copyFromJarToLocation(jarFile, entry, internalPath, tmpLocation);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to process JAR file when extracting classpath resource: " + hostPath,
                e
            );
        }

        // Mark temporary files/dirs for deletion at JVM shutdown
        deleteOnExit(tmpLocation.toPath());

        try {
            return tmpLocation.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private File createTempDirectory() {
        try {
            if (SystemUtils.IS_OS_MAC) {
                return Files.createTempDirectory(Paths.get(OS_MAC_TMP_DIR), TESTCONTAINERS_TMP_DIR_PREFIX).toFile();
            }
            return Files.createTempDirectory(TESTCONTAINERS_TMP_DIR_PREFIX).toFile();
        } catch (IOException e) {
            return new File(TESTCONTAINERS_TMP_DIR_PREFIX + Base58.randomString(5));
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void copyFromJarToLocation(
        final JarFile jarFile,
        final JarEntry entry,
        final String fromRoot,
        final File toRoot
    ) throws IOException {
        String destinationName = entry.getName().replaceFirst(fromRoot, "");
        File newFile = new File(toRoot, destinationName);

        log.debug("Copying resource {} from JAR file {}", fromRoot, jarFile.getName());

        if (!entry.isDirectory()) {
            // Create parent directories
            Path parent = newFile.getAbsoluteFile().toPath().getParent();
            parent.toFile().mkdirs();
            newFile.deleteOnExit();

            try (InputStream is = jarFile.getInputStream(entry)) {
                Files.copy(is, newFile.toPath());
            } catch (IOException e) {
                log.error(
                    "Failed to extract classpath resource " + entry.getName() + " from JAR file " + jarFile.getName(),
                    e
                );
                throw e;
            }
        }
    }

    private void deleteOnExit(final Path path) {
        Runtime
            .getRuntime()
            .addShutdownHook(
                new Thread(DockerClientFactory.TESTCONTAINERS_THREAD_GROUP, () -> PathUtils.recursiveDeleteDir(path))
            );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transferTo(final TarArchiveOutputStream outputStream, String destinationPathInTar) {
        recursiveTar(destinationPathInTar, this.getResolvedPath(), this.getResolvedPath(), outputStream);
    }

    /*
     * Recursively copies a file/directory into a TarArchiveOutputStream
     */
    private void recursiveTar(
        String entryFilename,
        String rootPath,
        String itemPath,
        TarArchiveOutputStream tarArchive
    ) {
        try {
            final File sourceFile = new File(itemPath).getCanonicalFile(); // e.g. /foo/bar/baz
            final File sourceRootFile = new File(rootPath).getCanonicalFile(); // e.g. /foo
            final String relativePathToSourceFile = sourceRootFile
                .toPath()
                .relativize(sourceFile.toPath())
                .toFile()
                .toString(); // e.g. /bar/baz

            final String tarEntryFilename;
            if (relativePathToSourceFile.isEmpty()) {
                tarEntryFilename = entryFilename; // entry filename e.g. xyz => xyz
            } else {
                tarEntryFilename = entryFilename + "/" + relativePathToSourceFile; // entry filename e.g. /xyz/bar/baz => /foo/bar/baz
            }

            final TarArchiveEntry tarEntry = new TarArchiveEntry(sourceFile, tarEntryFilename.replaceAll("^/", ""));

            // TarArchiveEntry automatically sets the mode for file/directory, but we can update to ensure that the mode is set exactly (inc executable bits)
            tarEntry.setMode(getUnixFileMode(itemPath));
            tarEntry.setUserId(userId);
            tarEntry.setGroupId(groupId);
            tarArchive.putArchiveEntry(tarEntry);

            if (sourceFile.isFile()) {
                Files.copy(sourceFile.toPath(), tarArchive);
            }
            // a directory entry merely needs to exist in the TAR file - there is no data stored yet
            tarArchive.closeArchiveEntry();

            final File[] children = sourceFile.listFiles();
            if (children != null) {
                // recurse into child files/directories
                for (final File child : children) {
                    recursiveTar(
                        entryFilename,
                        sourceRootFile.getCanonicalPath(),
                        child.getCanonicalPath(),
                        tarArchive
                    );
                }
            }
        } catch (IOException e) {
            log.error("Error when copying TAR file entry: {}", itemPath, e);
            throw new UncheckedIOException(e); // fail fast
        }
    }

    @Override
    public long getSize() {
        final File file = new File(this.getResolvedPath());
        if (file.isFile()) {
            return file.length();
        } else {
            return 0;
        }
    }

    @Override
    public long getUserId() {
        return userId;
    }

    @Override
    public long getGroupId() {
        return groupId;
    }

    @Override
    public String getDescription() {
        return this.getResolvedPath();
    }

    @Override
    public void updateChecksum(Checksum checksum) {
        File file = new File(getResolvedPath());
        checksumFile(file, checksum);
    }

    @SneakyThrows(IOException.class)
    private void checksumFile(File file, Checksum checksum) {
        Path path = file.toPath();
        checksum.update(MountableFile.getUnixFileMode(path));
        if (file.isDirectory()) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream
                    .filter(it -> it != path)
                    .forEach(it -> {
                        checksumFile(it.toFile(), checksum);
                    });
            }
        } else {
            FileUtils.checksum(file, checksum);
        }
    }

    @Override
    public int getFileMode() {
        return getUnixFileMode(this.getResolvedPath());
    }

    private int getUnixFileMode(final String pathAsString) {
        final Path path = Paths.get(pathAsString);
        if (this.forcedFileMode != null) {
            return this.getModeValue(path);
        }
        return getUnixFileMode(path);
    }

    @UnstableAPI
    public static int getUnixFileMode(final Path path) {
        try {
            int unixMode = (int) Files.readAttributes(path, "unix:mode").get("mode");
            // Truncate mode bits for z/OS
            if (
                "OS/390".equals(SystemUtils.OS_NAME) ||
                "z/OS".equals(SystemUtils.OS_NAME) ||
                "zOS".equals(SystemUtils.OS_NAME)
            ) {
                unixMode &= TarConstants.MAXID;
                unixMode |= Files.isDirectory(path) ? 040000 : 0100000;
            }
            return unixMode;
        } catch (IOException | UnsupportedOperationException e) {
            // fallback for non-posix environments
            int mode = DEFAULT_FILE_MODE;
            if (Files.isDirectory(path)) {
                mode = DEFAULT_DIR_MODE;
            } else if (Files.isExecutable(path)) {
                mode |= 0111; // equiv to +x for user/group/others
            }

            return mode;
        }
    }

    private int getModeValue(final Path path) {
        int result = Files.isDirectory(path) ? BASE_DIR_MODE : BASE_FILE_MODE;
        return result | this.forcedFileMode;
    }
}
