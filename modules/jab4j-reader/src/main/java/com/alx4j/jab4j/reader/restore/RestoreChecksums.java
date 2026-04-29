package com.alx4j.jab4j.reader.restore;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.CRC32C;

/**
 * Reader-local checksum helpers used without depending on non-exported core internals.
 */
final class RestoreChecksums {

    private static final int BUFFER_SIZE = 8 * 1024;

    private RestoreChecksums() {
    }

    /**
     * Calculates CRC32C for the given bytes.
     *
     * @param value source bytes
     * @return CRC32C value as an int
     */
    static int crc32c(byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        CRC32C crc32c = new CRC32C();
        crc32c.update(value, 0, value.length);
        return (int) crc32c.getValue();
    }

    /**
     * Calculates SHA-256 for the given bytes.
     *
     * @param value source bytes
     * @return lowercase SHA-256 hex digest
     */
    static String sha256Hex(byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        return HexFormat.of().formatHex(sha256Digest().digest(value));
    }

    /**
     * Calculates SHA-256 for a file path.
     *
     * @param path source path
     * @return lowercase SHA-256 hex digest
     */
    static String sha256Hex(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try (InputStream inputStream = Files.newInputStream(path)) {
            MessageDigest digest = sha256Digest();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to hash path " + path, exception);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
