package com.alx4j.jab4j.reader.capture.media.cv;

import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;

/**
 * Internal perspective transform used to map normalized frame coordinates into source-image coordinates.
 */
public final class PerspectiveTransform {

    private static final double EPSILON = 1.0e-9d;

    private final double h00;
    private final double h01;
    private final double h02;
    private final double h10;
    private final double h11;
    private final double h12;
    private final double h20;
    private final double h21;
    private final double h22;

    private PerspectiveTransform(
            double h00,
            double h01,
            double h02,
            double h10,
            double h11,
            double h12,
            double h20,
            double h21,
            double h22
    ) {
        this.h00 = h00;
        this.h01 = h01;
        this.h02 = h02;
        this.h10 = h10;
        this.h11 = h11;
        this.h12 = h12;
        this.h20 = h20;
        this.h21 = h21;
        this.h22 = h22;
    }

    /**
     * Builds a transform from the unit square to the supplied source-space quadrilateral.
     *
     * @param corners destination source-space quadrilateral corners
     * @return perspective transform from normalized coordinates to source coordinates
     */
    public static PerspectiveTransform fromUnitSquareTo(FrameCorners corners) {
        double x0 = corners.topLeftX();
        double y0 = corners.topLeftY();
        double x1 = corners.topRightX();
        double y1 = corners.topRightY();
        double x2 = corners.bottomRightX();
        double y2 = corners.bottomRightY();
        double x3 = corners.bottomLeftX();
        double y3 = corners.bottomLeftY();

        double dx1 = x1 - x2;
        double dy1 = y1 - y2;
        double dx2 = x3 - x2;
        double dy2 = y3 - y2;
        double sx = x0 - x1 + x2 - x3;
        double sy = y0 - y1 + y2 - y3;

        if (Math.abs(sx) < EPSILON && Math.abs(sy) < EPSILON) {
            return new PerspectiveTransform(
                    x1 - x0,
                    x3 - x0,
                    x0,
                    y1 - y0,
                    y3 - y0,
                    y0,
                    0.0d,
                    0.0d,
                    1.0d
            );
        }

        double denominator = (dx1 * dy2) - (dx2 * dy1);
        if (Math.abs(denominator) < EPSILON) {
            throw new IllegalArgumentException("perspective corners must not be degenerate");
        }
        double g = ((sx * dy2) - (dx2 * sy)) / denominator;
        double h = ((dx1 * sy) - (sx * dy1)) / denominator;
        return new PerspectiveTransform(
                x1 - x0 + (g * x1),
                x3 - x0 + (h * x3),
                x0,
                y1 - y0 + (g * y1),
                y3 - y0 + (h * y3),
                y0,
                g,
                h,
                1.0d
        );
    }

    /**
     * Maps one normalized coordinate through this transform.
     *
     * @param x normalized x coordinate
     * @param y normalized y coordinate
     * @return mapped source coordinate
     */
    public PerspectivePoint map(double x, double y) {
        double denominator = (h20 * x) + (h21 * y) + h22;
        if (Math.abs(denominator) < EPSILON) {
            throw new IllegalArgumentException("perspective mapping reached a degenerate point");
        }
        return new PerspectivePoint(
                ((h00 * x) + (h01 * y) + h02) / denominator,
                ((h10 * x) + (h11 * y) + h12) / denominator
        );
    }

    /**
     * Returns the inverse transform.
     *
     * @return inverse perspective transform
     */
    public PerspectiveTransform inverse() {
        double determinant = (h00 * ((h11 * h22) - (h12 * h21)))
                - (h01 * ((h10 * h22) - (h12 * h20)))
                + (h02 * ((h10 * h21) - (h11 * h20)));
        if (Math.abs(determinant) < EPSILON) {
            throw new IllegalArgumentException("perspective transform must be invertible");
        }
        double inv00 = ((h11 * h22) - (h12 * h21)) / determinant;
        double inv01 = ((h02 * h21) - (h01 * h22)) / determinant;
        double inv02 = ((h01 * h12) - (h02 * h11)) / determinant;
        double inv10 = ((h12 * h20) - (h10 * h22)) / determinant;
        double inv11 = ((h00 * h22) - (h02 * h20)) / determinant;
        double inv12 = ((h02 * h10) - (h00 * h12)) / determinant;
        double inv20 = ((h10 * h21) - (h11 * h20)) / determinant;
        double inv21 = ((h01 * h20) - (h00 * h21)) / determinant;
        double inv22 = ((h00 * h11) - (h01 * h10)) / determinant;
        return new PerspectiveTransform(inv00, inv01, inv02, inv10, inv11, inv12, inv20, inv21, inv22);
    }

    /**
     * Mapped two-dimensional point.
     *
     * @param x mapped x coordinate
     * @param y mapped y coordinate
     */
    public record PerspectivePoint(double x, double y) {
    }
}
