package com.alx4j.jab4j.output;

/**
 * Supported optional export modes for prepared frames.
 */
public enum ExportMode {
    FRAME_SEQUENCE("frameSequence"),
    IMAGE_SEQUENCE("imageSequence");

    private final String wireValue;

    ExportMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the stable config token used for this mode.
     *
     * @return external config value
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves one configured export mode token.
     *
     * @param value external config value
     * @return resolved export mode
     */
    public static ExportMode fromValue(String value) {
        for (ExportMode mode : values()) {
            if (mode.wireValue.equals(value)) {
                return mode;
            }
        }
        throw new ExportException("Unsupported export mode: " + value);
    }
}
