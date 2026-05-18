package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalControlPointEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;

/**
 * Bounded 2x2 local-grid correction applied after global homography projection.
 */
public final class LocalGridRefinement {

    static final double DEFAULT_DISPLACEMENT_CAP_MODULES = 0.25d;
    static final double HARD_DISPLACEMENT_CAP_MODULES = 0.50d;
    private static final int REQUIRED_CONTROL_POINTS = 6;
    private static final int REQUIRED_DISTRIBUTED_QUADRANTS = 3;
    private static final double SMOOTHNESS_DELTA_CAP_MODULES = 0.25d;
    private static final double MINIMUM_MODULE_SIZE_PX = 1.0d;
    private static final double MINIMUM_CORRECTION_PIXELS = 1.0e-6d;

    private final double minX;
    private final double minY;
    private final double width;
    private final double height;
    private final Vector topLeft;
    private final Vector topRight;
    private final Vector bottomLeft;
    private final Vector bottomRight;
    private final double moduleSizePixels;
    private final double maxDisplacementModules;
    private final double maxDisplacementPixels;
    private final double smoothnessScore;
    private final double regularizationScore;
    private final List<LocalControlPointEvidence> controls;

    private LocalGridRefinement(
            double minX,
            double minY,
            double width,
            double height,
            Vector topLeft,
            Vector topRight,
            Vector bottomLeft,
            Vector bottomRight,
            double moduleSizePixels,
            List<LocalControlPointEvidence> controls
    ) {
        this.minX = minX;
        this.minY = minY;
        this.width = width;
        this.height = height;
        this.topLeft = Objects.requireNonNull(topLeft, "topLeft must not be null");
        this.topRight = Objects.requireNonNull(topRight, "topRight must not be null");
        this.bottomLeft = Objects.requireNonNull(bottomLeft, "bottomLeft must not be null");
        this.bottomRight = Objects.requireNonNull(bottomRight, "bottomRight must not be null");
        this.moduleSizePixels = moduleSizePixels;
        this.controls = List.copyOf(Objects.requireNonNull(controls, "controls must not be null"));
        this.maxDisplacementPixels = List.of(topLeft, topRight, bottomLeft, bottomRight)
                .stream()
                .mapToDouble(Vector::length)
                .max()
                .orElse(0.0d);
        this.maxDisplacementModules = maxDisplacementPixels / moduleSizePixels;
        this.smoothnessScore = smoothnessScore(List.of(topLeft, topRight, bottomLeft, bottomRight));
        this.regularizationScore = regularizationScore(this.controls.size(), distributedQuadrants(this.controls));
    }

    /**
     * Builds a bounded local-grid correction from local residual evidence.
     *
     * @param evidence diagnostic local-refinement evidence
     * @return correction when control evidence is sufficient and smooth
     */
    public static Optional<LocalGridRefinement> from(LocalRefinementEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");
        List<LocalControlPointEvidence> controls = evidence.controlPoints()
                .stream()
                .filter(LocalControlPointEvidence::inlier)
                .filter(LocalControlPointEvidence::distributed)
                .filter(control -> control.observedSourcePoint().isPresent())
                .toList();
        if (controls.size() < REQUIRED_CONTROL_POINTS
                || distributedQuadrants(controls) < REQUIRED_DISTRIBUTED_QUADRANTS) {
            return Optional.empty();
        }

        Bounds bounds = bounds(controls);
        double moduleSizePixels = moduleSizePixels(controls);
        List<ControlDisplacement> displacements = controls.stream()
                .map(control -> displacement(control, moduleSizePixels))
                .filter(displacement -> displacement.modules() <= HARD_DISPLACEMENT_CAP_MODULES)
                .toList();
        if (displacements.size() < REQUIRED_CONTROL_POINTS) {
            return Optional.empty();
        }

        Vector topLeft = cornerVector(displacements, bounds.minX(), bounds.minY(), moduleSizePixels);
        Vector topRight = cornerVector(displacements, bounds.maxX(), bounds.minY(), moduleSizePixels);
        Vector bottomLeft = cornerVector(displacements, bounds.minX(), bounds.maxY(), moduleSizePixels);
        Vector bottomRight = cornerVector(displacements, bounds.maxX(), bounds.maxY(), moduleSizePixels);
        if (List.of(topLeft, topRight, bottomLeft, bottomRight)
                .stream()
                .mapToDouble(Vector::length)
                .max()
                .orElse(0.0d) < MINIMUM_CORRECTION_PIXELS) {
            return Optional.empty();
        }
        if (List.of(topLeft, topRight, bottomLeft, bottomRight)
                .stream()
                .mapToDouble(vector -> vector.length() / moduleSizePixels)
                .anyMatch(modules -> modules > DEFAULT_DISPLACEMENT_CAP_MODULES)) {
            return Optional.empty();
        }
        if (!smoothEnough(List.of(topLeft, topRight, bottomLeft, bottomRight), moduleSizePixels)) {
            return Optional.empty();
        }

        return Optional.of(new LocalGridRefinement(
                bounds.minX(),
                bounds.minY(),
                Math.max(1.0d, bounds.maxX() - bounds.minX()),
                Math.max(1.0d, bounds.maxY() - bounds.minY()),
                topLeft,
                topRight,
                bottomLeft,
                bottomRight,
                moduleSizePixels,
                controls
        ));
    }

    /**
     * Applies piecewise-bilinear local correction to one globally projected source point.
     *
     * @param canonicalPoint canonical source-model point
     * @param sourcePoint globally projected source point
     * @return adjusted source point
     */
    public SourcePoint apply(CanonicalPoint canonicalPoint, SourcePoint sourcePoint) {
        Objects.requireNonNull(canonicalPoint, "canonicalPoint must not be null");
        Objects.requireNonNull(sourcePoint, "sourcePoint must not be null");
        double horizontal = clampUnit((canonicalPoint.x() - minX) / width);
        double vertical = clampUnit((canonicalPoint.y() - minY) / height);
        Vector top = topLeft.interpolate(topRight, horizontal);
        Vector bottom = bottomLeft.interpolate(bottomRight, horizontal);
        Vector correction = top.interpolate(bottom, vertical);
        return new SourcePoint(sourcePoint.x() + correction.dx(), sourcePoint.y() + correction.dy());
    }

    /**
     * Returns the largest grid-node correction in module units after the default cap is enforced.
     *
     * @return maximum displacement in module units
     */
    public double maxDisplacementModules() {
        return maxDisplacementModules;
    }

    /**
     * Returns the largest grid-node correction in source pixels after the default cap is enforced.
     *
     * @return maximum displacement in source pixels
     */
    public double maxDisplacementPixels() {
        return maxDisplacementPixels;
    }

    /**
     * Returns a normalized score describing how smoothly the 2x2 correction grid changes across adjacent nodes.
     *
     * @return smoothness score between zero and one
     */
    public double smoothnessScore() {
        return smoothnessScore;
    }

    /**
     * Returns a normalized score describing whether the correction is supported by enough distributed controls.
     *
     * @return regularization score between zero and one
     */
    public double regularizationScore() {
        return regularizationScore;
    }

    /**
     * Returns the reliable controls used to build this bounded correction.
     *
     * @return immutable control-point list
     */
    public List<LocalControlPointEvidence> controls() {
        return controls;
    }

    private static ControlDisplacement displacement(LocalControlPointEvidence control, double moduleSizePixels) {
        SourcePoint observed = control.observedSourcePoint().orElseThrow();
        SourcePoint expected = control.expectedSourcePoint();
        Vector vector = new Vector(observed.x() - expected.x(), observed.y() - expected.y());
        double modules = vector.length() / moduleSizePixels;
        return new ControlDisplacement(control, vector, modules);
    }

    private static Vector cornerVector(
            List<ControlDisplacement> displacements,
            double cornerX,
            double cornerY,
            double moduleSizePixels
    ) {
        List<WeightedVector> nearest = displacements.stream()
                .map(displacement -> weighted(displacement, cornerX, cornerY))
                .sorted(Comparator.comparingDouble(WeightedVector::distance))
                .limit(6)
                .toList();
        double weightSum = 0.0d;
        double dx = 0.0d;
        double dy = 0.0d;
        for (WeightedVector vector : nearest) {
            double weight = 1.0d / Math.max(0.0001d, vector.distance());
            weightSum += weight;
            dx += vector.vector().dx() * weight;
            dy += vector.vector().dy() * weight;
        }
        Vector averaged = weightSum == 0.0d ? Vector.zero() : new Vector(dx / weightSum, dy / weightSum);
        return averaged.capped(DEFAULT_DISPLACEMENT_CAP_MODULES * moduleSizePixels);
    }

    private static WeightedVector weighted(ControlDisplacement displacement, double cornerX, double cornerY) {
        CanonicalPoint point = displacement.control().canonicalPoint();
        return new WeightedVector(
                displacement.vector(),
                Math.hypot(point.x() - cornerX, point.y() - cornerY)
        );
    }

    private static Bounds bounds(List<LocalControlPointEvidence> controls) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (LocalControlPointEvidence control : controls) {
            CanonicalPoint point = control.canonicalPoint();
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static double moduleSizePixels(List<LocalControlPointEvidence> controls) {
        List<Double> candidates = new ArrayList<>();
        for (LocalControlPointEvidence control : controls) {
            if (control.residualBeforeModules() > 0.000001d) {
                candidates.add(control.residualBeforePixels() / control.residualBeforeModules());
            }
        }
        if (candidates.isEmpty()) {
            return MINIMUM_MODULE_SIZE_PX;
        }
        return Math.max(MINIMUM_MODULE_SIZE_PX, candidates.stream().mapToDouble(Double::doubleValue).average().orElse(1.0d));
    }

    private static boolean smoothEnough(List<Vector> vectors, double moduleSizePixels) {
        for (int index = 0; index < vectors.size(); index++) {
            Vector current = vectors.get(index);
            Vector next = vectors.get((index + 1) % vectors.size());
            if (current.delta(next).length() / moduleSizePixels > SMOOTHNESS_DELTA_CAP_MODULES) {
                return false;
            }
        }
        return true;
    }

    private static double smoothnessScore(List<Vector> vectors) {
        double maxDelta = 0.0d;
        for (int index = 0; index < vectors.size(); index++) {
            maxDelta = Math.max(maxDelta, vectors.get(index).delta(vectors.get((index + 1) % vectors.size())).length());
        }
        double maxLength = Math.max(1.0d, vectors.stream().mapToDouble(Vector::length).max().orElse(0.0d));
        return clampUnit(1.0d - (maxDelta / maxLength));
    }

    private static double regularizationScore(int controls, int quadrants) {
        return clampUnit((0.60d * Math.min(1.0d, controls / (double) REQUIRED_CONTROL_POINTS))
                + (0.40d * (quadrants / 4.0d)));
    }

    private static int distributedQuadrants(List<LocalControlPointEvidence> controls) {
        Bounds bounds = bounds(controls);
        boolean[] quadrants = new boolean[4];
        for (LocalControlPointEvidence control : controls) {
            CanonicalPoint point = control.canonicalPoint();
            boolean right = point.x() >= bounds.minX() + ((bounds.maxX() - bounds.minX()) / 2.0d);
            boolean bottom = point.y() >= bounds.minY() + ((bounds.maxY() - bounds.minY()) / 2.0d);
            quadrants[(bottom ? 2 : 0) + (right ? 1 : 0)] = true;
        }
        int count = 0;
        for (boolean present : quadrants) {
            if (present) {
                count++;
            }
        }
        return count;
    }

    private static double clampUnit(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
    }

    private record ControlDisplacement(
            LocalControlPointEvidence control,
            Vector vector,
            double modules
    ) {
    }

    private record WeightedVector(Vector vector, double distance) {
    }

    private record Vector(double dx, double dy) {

        private static Vector zero() {
            return new Vector(0.0d, 0.0d);
        }

        private double length() {
            return Math.hypot(dx, dy);
        }

        private Vector interpolate(Vector other, double fraction) {
            return new Vector(
                    dx + ((other.dx - dx) * fraction),
                    dy + ((other.dy - dy) * fraction)
            );
        }

        private Vector delta(Vector other) {
            return new Vector(dx - other.dx, dy - other.dy);
        }

        private Vector capped(double maximumLength) {
            double length = length();
            if (length <= maximumLength || length == 0.0d) {
                return this;
            }
            double scale = maximumLength / length;
            return new Vector(dx * scale, dy * scale);
        }
    }
}
