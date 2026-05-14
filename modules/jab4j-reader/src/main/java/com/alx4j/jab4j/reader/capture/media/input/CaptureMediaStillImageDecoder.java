package com.alx4j.jab4j.reader.capture.media.input;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Decodes one supported still-image file into row-major ARGB pixels for media intake.
 */
public interface CaptureMediaStillImageDecoder {

    /**
     * Creates the default decoder chain for Java SE images and optional libheif-backed HEIC/HEIF images.
     *
     * @return default still-image decoder
     */
    static CaptureMediaStillImageDecoder defaultDecoder() {
        return new CompositeCaptureMediaStillImageDecoder(List.of(
                new ImageIoCaptureMediaStillImageDecoder(),
                LibHeifCaptureMediaStillImageDecoder.fromEnvironment()
        ));
    }

    /**
     * Indicates whether this decoder can handle the supplied lower-case file extension in the current runtime.
     *
     * @param extension lower-case file extension without dot
     * @return true when this decoder can attempt the image
     */
    boolean supportsExtension(String extension);

    /**
     * Decodes one still image into ARGB pixels.
     *
     * @param sourceFile image file to decode
     * @param extension lower-case file extension without dot
     * @return decoded image pixels and metadata
     * @throws IOException when the image cannot be decoded by this decoder
     */
    DecodedStillImage decode(Path sourceFile, String extension) throws IOException;

    /**
     * Normalizes a file extension for decoder matching.
     *
     * @param extension raw extension without dot
     * @return lower-case extension
     */
    static String normalizedExtension(String extension) {
        return Objects.requireNonNull(extension, "extension must not be null").toLowerCase(Locale.ROOT);
    }

    /**
     * Decoded still-image pixels and metadata.
     *
     * @param widthPixels image width in pixels
     * @param heightPixels image height in pixels
     * @param formatName decoded source format name
     * @param argbPixels row-major ARGB pixels
     */
    record DecodedStillImage(
            int widthPixels,
            int heightPixels,
            String formatName,
            int[] argbPixels
    ) {

        /**
         * Creates a validated decoded still-image value.
         */
        public DecodedStillImage {
            if (widthPixels <= 0 || heightPixels <= 0) {
                throw new IllegalArgumentException("image dimensions must be positive");
            }
            if (formatName == null || formatName.isBlank()) {
                throw new IllegalArgumentException("formatName must not be blank");
            }
            Objects.requireNonNull(argbPixels, "argbPixels must not be null");
            long expectedPixels = (long) widthPixels * heightPixels;
            if (expectedPixels > Integer.MAX_VALUE || argbPixels.length != (int) expectedPixels) {
                throw new IllegalArgumentException("argbPixels length must equal widthPixels * heightPixels");
            }
            argbPixels = Arrays.copyOf(argbPixels, argbPixels.length);
        }

        /**
         * Returns a defensive copy of row-major ARGB pixels.
         *
         * @return copied ARGB pixels
         */
        public int[] argbPixels() {
            return copyArgbPixels();
        }

        /**
         * Returns a defensive copy of row-major ARGB pixels.
         *
         * @return copied ARGB pixels
         */
        public int[] copyArgbPixels() {
            return Arrays.copyOf(argbPixels, argbPixels.length);
        }
    }
}
