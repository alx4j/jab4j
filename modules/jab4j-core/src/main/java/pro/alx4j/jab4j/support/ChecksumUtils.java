package pro.alx4j.jab4j.support;

import java.util.Objects;
import java.util.zip.CRC32C;

/**
 * Utility methods for CRC32C checksums.
 */
public final class ChecksumUtils {

    private ChecksumUtils() {
    }

    /**
     * Calculates CRC32C for the given byte array.
     *
     * @param value source bytes
     * @return CRC32C value as an int
     */
    public static int crc32c(byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        return crc32c(value, 0, value.length);
    }

    /**
     * Calculates CRC32C for a range inside the given byte array.
     *
     * @param value source bytes
     * @param offset starting offset
     * @param length range length
     * @return CRC32C value as an int
     */
    public static int crc32c(byte[] value, int offset, int length) {
        Objects.requireNonNull(value, "value must not be null");
        if (offset < 0 || length < 0 || offset + length > value.length) {
            throw new IllegalArgumentException("offset and length must describe a valid byte range");
        }

        CRC32C crc32c = new CRC32C();
        crc32c.update(value, offset, length);
        return (int) crc32c.getValue();
    }
}
