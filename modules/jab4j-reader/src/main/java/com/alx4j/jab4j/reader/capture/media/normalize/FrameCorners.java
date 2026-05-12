package com.alx4j.jab4j.reader.capture.media.normalize;

/**
 * Detected source-space corners for a normalized capture frame.
 *
 * @param topLeftX top-left x coordinate
 * @param topLeftY top-left y coordinate
 * @param topRightX top-right x coordinate
 * @param topRightY top-right y coordinate
 * @param bottomRightX bottom-right x coordinate
 * @param bottomRightY bottom-right y coordinate
 * @param bottomLeftX bottom-left x coordinate
 * @param bottomLeftY bottom-left y coordinate
 */
public record FrameCorners(
        double topLeftX,
        double topLeftY,
        double topRightX,
        double topRightY,
        double bottomRightX,
        double bottomRightY,
        double bottomLeftX,
        double bottomLeftY
) {

    /**
     * Creates validated frame corners.
     *
     * @param topLeftX top-left x coordinate
     * @param topLeftY top-left y coordinate
     * @param topRightX top-right x coordinate
     * @param topRightY top-right y coordinate
     * @param bottomRightX bottom-right x coordinate
     * @param bottomRightY bottom-right y coordinate
     * @param bottomLeftX bottom-left x coordinate
     * @param bottomLeftY bottom-left y coordinate
     */
    public FrameCorners {
        requireFinite(topLeftX, "topLeftX");
        requireFinite(topLeftY, "topLeftY");
        requireFinite(topRightX, "topRightX");
        requireFinite(topRightY, "topRightY");
        requireFinite(bottomRightX, "bottomRightX");
        requireFinite(bottomRightY, "bottomRightY");
        requireFinite(bottomLeftX, "bottomLeftX");
        requireFinite(bottomLeftY, "bottomLeftY");
    }

    /**
     * Creates exact full-frame corners for a pass-through rendered frame.
     *
     * @param widthPixels frame width in pixels
     * @param heightPixels frame height in pixels
     * @return full-frame source-space corners
     */
    public static FrameCorners exactFrame(int widthPixels, int heightPixels) {
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new IllegalArgumentException("frame dimensions must be positive");
        }
        return new FrameCorners(0.0d, 0.0d, widthPixels, 0.0d, widthPixels, heightPixels, 0.0d, heightPixels);
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}
