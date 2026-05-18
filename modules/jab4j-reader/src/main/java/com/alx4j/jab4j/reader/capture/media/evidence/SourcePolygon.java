package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.Collection;
import java.util.List;

/**
 * Immutable polygon in source-image pixel coordinates.
 *
 * @param vertices source-space vertices in deterministic winding order
 */
public record SourcePolygon(List<SourcePoint> vertices) {

    /**
     * Creates a validated source-space polygon with a defensive vertex copy.
     *
     * @param vertices source-space vertices
     */
    public SourcePolygon {
        vertices = EvidenceValidation.copyList(vertices, "vertices");
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("vertices must contain at least three points");
        }
    }

    /**
     * Creates a source-space polygon from a vertex collection.
     *
     * @param vertices source-space vertices
     * @return immutable source-space polygon
     */
    public static SourcePolygon of(Collection<SourcePoint> vertices) {
        return new SourcePolygon(List.copyOf(vertices));
    }
}

