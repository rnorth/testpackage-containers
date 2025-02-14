package org.testcontainers.images.builder;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Checksum;

public interface Transferable {
    int DEFAULT_FILE_MODE = 0100644;

    int DEFAULT_DIR_MODE = 040755;

    static Transferable of(String string) {
        return of(string.getBytes(StandardCharsets.UTF_8));
    }

    static Transferable of(String string, int fileMode) {
        return of(string.getBytes(StandardCharsets.UTF_8), fileMode);
    }

    static Transferable of(byte[] bytes) {
        return of(bytes, DEFAULT_FILE_MODE);
    }

    static Transferable of(byte[] bytes, int fileMode) {
        return of(bytes, fileMode, 0, 0);
    }

    static Transferable of(byte[] bytes, int fileMode, int userId, int groupId) {
        return new Transferable() {
            @Override
            public long getSize() {
                return bytes.length;
            }

            @Override
            public byte[] getBytes() {
                return bytes;
            }

            @Override
            public void updateChecksum(Checksum checksum) {
                checksum.update(bytes, 0, bytes.length);
            }

            @Override
            public int getFileMode() {
                return fileMode;
            }

            @Override
            public long getUserId() {
                return userId;
            }

            @Override
            public long getGroupId() {
                return groupId;
            }
        };
    }

    /**
     * Get file mode. Default is 0100644.
     *
     * @return file mode
     * @see Transferable#DEFAULT_FILE_MODE
     */
    default int getFileMode() {
        return DEFAULT_FILE_MODE;
    }

    /**
     * Size of an object.
     *
     * @return size in bytes
     */
    long getSize();

    /**
     * User ID owning the file
     *
     * @return ID of user owner
     */
    default long getUserId() {
        return 0;
    }

    /**
     * Group ID owning the file
     *
     * @return ID of group owner
     */
    default long getGroupId() {
        return 0;
    }

    /**
     * transfer content of this Transferable to the output stream. <b>Must not</b> close the stream.
     *
     * @param tarArchiveOutputStream stream to output
     * @param destination
     */
    default void transferTo(TarArchiveOutputStream tarArchiveOutputStream, final String destination) {
        TarArchiveEntry tarEntry = new TarArchiveEntry(destination);
        tarEntry.setSize(getSize());
        tarEntry.setMode(getFileMode());
        tarEntry.setUserId(getUserId());
        tarEntry.setGroupId(getGroupId());

        try {
            tarArchiveOutputStream.putArchiveEntry(tarEntry);
            IOUtils.write(getBytes(), tarArchiveOutputStream);
            tarArchiveOutputStream.closeArchiveEntry();
        } catch (IOException e) {
            throw new RuntimeException("Can't transfer " + getDescription(), e);
        }
    }

    default byte[] getBytes() {
        return new byte[0];
    }

    default String getDescription() {
        return "";
    }

    default void updateChecksum(Checksum checksum) {
        throw new UnsupportedOperationException("Provide implementation in subclass");
    }
}
