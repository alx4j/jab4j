package com.alx4j.jab4j.support;

/**
 * Network-byte-order helpers for deterministic transport serialization.
 */
public final class ByteOrderUtils {

    private ByteOrderUtils() {
    }

    /**
     * Writes an int in big-endian order.
     *
     * @param target target byte array
     * @param offset starting offset
     * @param value int value to write
     */
    public static void writeIntBigEndian(byte[] target, int offset, int value) {
        validateRange(target, offset, Integer.BYTES);
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    /**
     * Writes a long in big-endian order.
     *
     * @param target target byte array
     * @param offset starting offset
     * @param value long value to write
     */
    public static void writeLongBigEndian(byte[] target, int offset, long value) {
        validateRange(target, offset, Long.BYTES);
        for (int index = 0; index < Long.BYTES; index++) {
            target[offset + index] = (byte) (value >>> (56 - (index * 8)));
        }
    }

    /**
     * Reads an int in big-endian order.
     *
     * @param source source byte array
     * @param offset starting offset
     * @return int value
     */
    public static int readIntBigEndian(byte[] source, int offset) {
        validateRange(source, offset, Integer.BYTES);
        return (source[offset] & 0xFF) << 24
                | (source[offset + 1] & 0xFF) << 16
                | (source[offset + 2] & 0xFF) << 8
                | (source[offset + 3] & 0xFF);
    }

    /**
     * Reads a long in big-endian order.
     *
     * @param source source byte array
     * @param offset starting offset
     * @return long value
     */
    public static long readLongBigEndian(byte[] source, int offset) {
        validateRange(source, offset, Long.BYTES);
        long result = 0;
        for (int index = 0; index < Long.BYTES; index++) {
            result = (result << 8) | (source[offset + index] & 0xFFL);
        }
        return result;
    }

    private static void validateRange(byte[] bytes, int offset, int requiredLength) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        if (offset < 0 || offset + requiredLength > bytes.length) {
            throw new IllegalArgumentException("offset does not describe a valid byte range");
        }
    }
}
