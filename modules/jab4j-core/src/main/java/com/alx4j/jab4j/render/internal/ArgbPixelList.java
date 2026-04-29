package com.alx4j.jab4j.render.internal;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Immutable {@link List} view over ARGB pixels stored in a primitive {@code int[]} buffer.
 */
public final class ArgbPixelList extends AbstractList<Integer> implements RandomAccess {

    private final int[] pixels;

    private ArgbPixelList(int[] pixels) {
        this.pixels = pixels;
    }

    /**
     * Creates an immutable pixel list by copying a primitive ARGB buffer.
     *
     * @param pixels source pixels
     * @return immutable pixel list
     */
    public static List<Integer> copyOf(int[] pixels) {
        return new ArgbPixelList(Arrays.copyOf(Objects.requireNonNull(pixels, "pixels must not be null"), pixels.length));
    }

    /**
     * Creates an immutable pixel list by copying boxed ARGB values into primitive storage.
     *
     * @param pixels source pixels
     * @return immutable pixel list
     */
    public static List<Integer> copyOf(List<Integer> pixels) {
        Objects.requireNonNull(pixels, "pixels must not be null");
        if (pixels instanceof ArgbPixelList argbPixels) {
            return argbPixels;
        }

        int[] copy = new int[pixels.size()];
        for (int index = 0; index < copy.length; index++) {
            copy[index] = Objects.requireNonNull(pixels.get(index), "pixels must not contain null values");
        }
        return new ArgbPixelList(copy);
    }

    /**
     * Copies a contiguous pixel range into a destination primitive buffer.
     *
     * @param sourcePosition source start index
     * @param destination destination buffer
     * @param destinationPosition destination start index
     * @param length number of pixels to copy
     */
    public void copyTo(int sourcePosition, int[] destination, int destinationPosition, int length) {
        System.arraycopy(pixels, sourcePosition, destination, destinationPosition, length);
    }

    /**
     * Returns the primitive ARGB pixel value at the given index.
     *
     * @param index pixel index
     * @return ARGB value
     */
    public int pixelAt(int index) {
        return pixels[index];
    }

    @Override
    public Integer get(int index) {
        return pixelAt(index);
    }

    @Override
    public int size() {
        return pixels.length;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof ArgbPixelList otherPixels) {
            return Arrays.equals(pixels, otherPixels.pixels);
        }
        if (!(other instanceof List<?> otherList) || otherList.size() != pixels.length) {
            return false;
        }

        for (int index = 0; index < pixels.length; index++) {
            Object value = otherList.get(index);
            if (!(value instanceof Integer pixel) || pixel.intValue() != pixels[index]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (int pixel : pixels) {
            hash = (31 * hash) + Integer.hashCode(pixel);
        }
        return hash;
    }
}
