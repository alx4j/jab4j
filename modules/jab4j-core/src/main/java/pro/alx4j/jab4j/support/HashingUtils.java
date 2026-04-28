package pro.alx4j.jab4j.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Utility methods for SHA-256 hashing.
 */
public final class HashingUtils {

    private static final int BUFFER_SIZE = 8 * 1024;

    private HashingUtils() {
    }

    /**
     * Calculates SHA-256 over the given bytes and returns the lowercase hex digest.
     *
     * @param value source bytes
     * @return lowercase SHA-256 hex digest
     */
    public static String sha256Hex(byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        return HexFormat.of().formatHex(sha256Digest().digest(value));
    }

    /**
     * Calculates SHA-256 over an input stream and returns the lowercase hex digest.
     *
     * @param inputStream source stream
     * @return lowercase SHA-256 hex digest
     */
    public static String sha256Hex(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");

        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to hash input stream", exception);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Calculates SHA-256 over a file path and returns the lowercase hex digest.
     *
     * @param path source path
     * @return lowercase SHA-256 hex digest
     */
    public static String sha256Hex(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try (InputStream inputStream = Files.newInputStream(path)) {
            return sha256Hex(inputStream);
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
