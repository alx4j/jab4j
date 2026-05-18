package com.alx4j.jab4j.reader.capture.media.evidence;

/**
 * Immutable point in source-image pixel coordinates.
 *
 * @param x horizontal source pixel coordinate
 * @param y vertical source pixel coordinate
 */
public record SourcePoint(double x, double y) {

    /**
     * Creates a validated source-space point.
     *
     * @param x horizontal source coordinate
     * @param y vertical source coordinate
     */
    public SourcePoint {
        EvidenceValidation.requireFinite(x, "x");
        EvidenceValidation.requireFinite(y, "y");
    }
}

