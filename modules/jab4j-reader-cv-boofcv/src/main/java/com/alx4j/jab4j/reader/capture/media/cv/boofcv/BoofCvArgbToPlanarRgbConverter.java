package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;

/**
 * Converts reader-owned ARGB media frames into BoofCV planar RGB images without mutating the source frame.
 */
final class BoofCvArgbToPlanarRgbConverter {

    private static final String COLOR_ORDER = "RGB";
    private static final String COPY_BEHAVIOR = "copied-argb-to-planar-u8-bands-alpha-ignored";

    private BoofCvArgbToPlanarRgbConverter() {
    }

    /**
     * Copies retained row-major ARGB pixels into BoofCV planar RGB bands.
     *
     * @param frame decoded media input frame
     * @return copied BoofCV planar image and conversion metadata
     */
    static ConvertedArgbImage convert(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        int width = frame.widthPixels();
        int height = frame.heightPixels();
        Planar<GrayU8> rgb = new Planar<>(GrayU8.class, width, height, 3);
        int[] rowPixels = new int[width];
        for (int row = 0; row < height; row++) {
            frame.copyArgbRow(row, 0, rowPixels, 0, width);
            for (int col = 0; col < width; col++) {
                int argb = rowPixels[col];
                rgb.getBand(0).set(col, row, (argb >>> 16) & 0xFF);
                rgb.getBand(1).set(col, row, (argb >>> 8) & 0xFF);
                rgb.getBand(2).set(col, row, argb & 0xFF);
            }
        }
        return new ConvertedArgbImage(
                rgb,
                new ConversionMetadata(width, height, COLOR_ORDER, COPY_BEHAVIOR)
        );
    }

    /**
     * Copied BoofCV image plus the metadata needed to explain ARGB conversion.
     *
     * @param image copied BoofCV planar RGB image
     * @param metadata conversion metadata
     */
    record ConvertedArgbImage(Planar<GrayU8> image, ConversionMetadata metadata) {

        ConvertedArgbImage {
            Objects.requireNonNull(image, "image must not be null");
            Objects.requireNonNull(metadata, "metadata must not be null");
        }
    }

    /**
     * Stable metadata for one ARGB-to-BoofCV conversion.
     *
     * @param widthPixels source image width
     * @param heightPixels source image height
     * @param colorOrder BoofCV band color order
     * @param copyBehavior copy semantics used by the conversion
     */
    record ConversionMetadata(
            int widthPixels,
            int heightPixels,
            String colorOrder,
            String copyBehavior
    ) {

        ConversionMetadata {
            if (widthPixels <= 0 || heightPixels <= 0) {
                throw new IllegalArgumentException("conversion dimensions must be positive");
            }
            if (colorOrder == null || colorOrder.isBlank()) {
                throw new IllegalArgumentException("colorOrder must not be blank");
            }
            if (copyBehavior == null || copyBehavior.isBlank()) {
                throw new IllegalArgumentException("copyBehavior must not be blank");
            }
        }
    }
}
