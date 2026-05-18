package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.Collection;
import java.util.List;

/**
 * Immutable polygon in canonical barcode or layout coordinates.
 *
 * @param vertices canonical vertices in deterministic winding order
 */
public record CanonicalPolygon(List<CanonicalPoint> vertices) {

    /**
     * Creates a validated canonical polygon with a defensive vertex copy.
     *
     * @param vertices canonical vertices
     */
    public CanonicalPolygon {
        vertices = EvidenceValidation.copyList(vertices, "vertices");
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("vertices must contain at least three points");
        }
    }

    /**
     * Creates a canonical polygon from a vertex collection.
     *
     * @param vertices canonical vertices
     * @return immutable canonical polygon
     */
    public static CanonicalPolygon of(Collection<CanonicalPoint> vertices) {
        return new CanonicalPolygon(List.copyOf(vertices));
    }
}
