package pro.alx4j.jab4j.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Small stream helpers for deterministic, dependency-light module code.
 */
public final class StreamUtils {

    private static final int BUFFER_SIZE = 8 * 1024;

    private StreamUtils() {
    }

    /**
     * Copies a stream to an output stream.
     *
     * @param inputStream source stream
     * @param outputStream target stream
     * @return bytes copied
     */
    public static long copy(InputStream inputStream, OutputStream outputStream) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");

        byte[] buffer = new byte[BUFFER_SIZE];
        long totalBytes = 0;
        int bytesRead;
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            return totalBytes;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to copy stream", exception);
        }
    }

    /**
     * Reads an input stream fully into memory.
     *
     * @param inputStream source stream
     * @return byte array containing the stream contents
     */
    public static byte[] readFully(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        copy(inputStream, outputStream);
        return outputStream.toByteArray();
    }
}
