package com.alx4j.jab4j.reader.capture.media.evidence;

/**
 * Immutable point in canonical barcode or layout coordinates.
 *
 * @param x horizontal canonical coordinate
 * @param y vertical canonical coordinate
 */
public record CanonicalPoint(double x, double y) {

    /**
     * Creates a validated canonical point.
     *
     * @param x horizontal canonical coordinate
     * @param y vertical canonical coordinate
     */
    public CanonicalPoint {
        EvidenceValidation.requireFinite(x, "x");
        EvidenceValidation.requireFinite(y, "y");
    }
}

