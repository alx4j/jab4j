package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;

/**
 * Reader-owned homography fitted from canonical feature coordinates to source-image coordinates.
 */
final class HomographyTransform {

    private static final double EPSILON = 1.0e-10d;
    private static final double MAX_STABLE_CONDITION_SCORE = 1.0e12d;
    private static final double MAX_STABLE_PARAMETER = 1.0e12d;
    private static final int UNKNOWN_COUNT = 8;
    private static final int MATRIX_SIZE = 3;

    private final double[] parameters;
    private final double conditionScore;

    private HomographyTransform(double[] parameters, double conditionScore) {
        this.parameters = Arrays.copyOf(parameters, parameters.length);
        this.conditionScore = conditionScore;
    }

    /**
     * Fits a homography from at least four canonical-to-source point pairs.
     *
     * @param pointPairs matched control points
     * @return fitted transform, or empty when the point set is singular
     */
    static Optional<HomographyTransform> fit(List<PointPair> pointPairs) {
        if (pointPairs.size() < 4) {
            return Optional.empty();
        }

        Optional<PointNormalization> canonicalNormalization = PointNormalization.fromCanonical(pointPairs);
        Optional<PointNormalization> sourceNormalization = PointNormalization.fromSource(pointPairs);
        if (canonicalNormalization.isEmpty() || sourceNormalization.isEmpty()) {
            return Optional.empty();
        }

        PointNormalization canonical = canonicalNormalization.orElseThrow();
        PointNormalization source = sourceNormalization.orElseThrow();
        double[][] normal = new double[UNKNOWN_COUNT][UNKNOWN_COUNT];
        double[] rhs = new double[UNKNOWN_COUNT];

        for (PointPair pointPair : pointPairs) {
            double x = canonical.normalizedX(pointPair.canonicalPoint());
            double y = canonical.normalizedY(pointPair.canonicalPoint());
            double u = source.normalizedX(pointPair.sourcePoint());
            double v = source.normalizedY(pointPair.sourcePoint());
            accumulate(normal, rhs, new double[] {x, y, 1.0d, 0.0d, 0.0d, 0.0d, -u * x, -u * y}, u);
            accumulate(normal, rhs, new double[] {0.0d, 0.0d, 0.0d, x, y, 1.0d, -v * x, -v * y}, v);
        }

        Optional<LinearSolution> solution = solve(normal, rhs);
        if (solution.isEmpty()) {
            return Optional.empty();
        }
        double[] solved = solution.orElseThrow().values();
        double[][] normalizedHomography = {
                {solved[0], solved[1], solved[2]},
                {solved[3], solved[4], solved[5]},
                {solved[6], solved[7], 1.0d}
        };
        double[][] denormalized = multiply(
                source.inverseMatrix(),
                multiply(normalizedHomography, canonical.matrix())
        );
        double[] parameters = rowMajor(normalizeScale(denormalized));
        if (!finite(parameters)) {
            return Optional.empty();
        }
        return Optional.of(new HomographyTransform(parameters, solution.orElseThrow().conditionScore()));
    }

    /**
     * Maps one canonical point into source-image coordinates.
     *
     * @param point canonical point
     * @return mapped source point
     */
    SourcePoint map(CanonicalPoint point) {
        double denominator = (parameters[6] * point.x()) + (parameters[7] * point.y()) + parameters[8];
        if (Math.abs(denominator) < EPSILON) {
            throw new IllegalArgumentException("homography mapping reached a degenerate point");
        }
        return new SourcePoint(
                ((parameters[0] * point.x()) + (parameters[1] * point.y()) + parameters[2]) / denominator,
                ((parameters[3] * point.x()) + (parameters[4] * point.y()) + parameters[5]) / denominator
        );
    }

    /**
     * Returns row-major homography parameters.
     *
     * @return immutable row-major 3x3 parameter list
     */
    List<Double> parameters() {
        List<Double> values = new ArrayList<>(parameters.length);
        for (double parameter : parameters) {
            values.add(parameter);
        }
        return List.copyOf(values);
    }

    /**
     * Returns a deterministic conditioning proxy from the linear solve.
     *
     * @return non-negative conditioning score
     */
    double conditionScore() {
        return conditionScore;
    }

    /**
     * Returns whether the transform determinant is safely non-zero.
     *
     * @return true when the transform is invertible
     */
    boolean invertible() {
        double scale = 0.0d;
        for (double parameter : parameters) {
            scale = Math.max(scale, Math.abs(parameter));
        }
        if (scale < EPSILON) {
            return false;
        }
        return Math.abs(determinant()) > EPSILON * scale * scale * scale;
    }

    /**
     * Returns whether the transform is invertible and numerically bounded.
     *
     * @return true when the transform is stable enough for downstream evidence
     */
    boolean stable() {
        if (!invertible() || !Double.isFinite(conditionScore) || conditionScore > MAX_STABLE_CONDITION_SCORE) {
            return false;
        }
        for (double parameter : parameters) {
            if (!Double.isFinite(parameter) || Math.abs(parameter) > MAX_STABLE_PARAMETER) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a rounded stable key for deterministic candidate deduplication.
     *
     * @return rounded transform key
     */
    String roundedKey() {
        StringBuilder key = new StringBuilder();
        for (double parameter : parameters) {
            if (!key.isEmpty()) {
                key.append('|');
            }
            key.append(Math.rint(parameter * 1_000_000.0d) / 1_000_000.0d);
        }
        return key.toString();
    }

    private double determinant() {
        return parameters[0] * ((parameters[4] * parameters[8]) - (parameters[5] * parameters[7]))
                - parameters[1] * ((parameters[3] * parameters[8]) - (parameters[5] * parameters[6]))
                + parameters[2] * ((parameters[3] * parameters[7]) - (parameters[4] * parameters[6]));
    }

    private static void accumulate(double[][] normal, double[] rhs, double[] row, double value) {
        for (int outer = 0; outer < UNKNOWN_COUNT; outer++) {
            rhs[outer] += row[outer] * value;
            for (int inner = 0; inner < UNKNOWN_COUNT; inner++) {
                normal[outer][inner] += row[outer] * row[inner];
            }
        }
    }

    private static Optional<LinearSolution> solve(double[][] matrix, double[] rhs) {
        double[][] augmented = new double[UNKNOWN_COUNT][UNKNOWN_COUNT + 1];
        for (int row = 0; row < UNKNOWN_COUNT; row++) {
            System.arraycopy(matrix[row], 0, augmented[row], 0, UNKNOWN_COUNT);
            augmented[row][UNKNOWN_COUNT] = rhs[row];
        }

        double minPivot = Double.POSITIVE_INFINITY;
        double maxPivot = 0.0d;
        for (int pivotIndex = 0; pivotIndex < UNKNOWN_COUNT; pivotIndex++) {
            int pivotRow = pivotIndex;
            double pivotMagnitude = Math.abs(augmented[pivotIndex][pivotIndex]);
            for (int row = pivotIndex + 1; row < UNKNOWN_COUNT; row++) {
                double candidate = Math.abs(augmented[row][pivotIndex]);
                if (candidate > pivotMagnitude) {
                    pivotMagnitude = candidate;
                    pivotRow = row;
                }
            }
            if (pivotMagnitude < EPSILON) {
                return Optional.empty();
            }
            swapRows(augmented, pivotIndex, pivotRow);
            minPivot = Math.min(minPivot, pivotMagnitude);
            maxPivot = Math.max(maxPivot, pivotMagnitude);

            double pivot = augmented[pivotIndex][pivotIndex];
            for (int col = pivotIndex; col <= UNKNOWN_COUNT; col++) {
                augmented[pivotIndex][col] /= pivot;
            }
            for (int row = 0; row < UNKNOWN_COUNT; row++) {
                if (row == pivotIndex) {
                    continue;
                }
                double factor = augmented[row][pivotIndex];
                if (Math.abs(factor) < EPSILON) {
                    continue;
                }
                for (int col = pivotIndex; col <= UNKNOWN_COUNT; col++) {
                    augmented[row][col] -= factor * augmented[pivotIndex][col];
                }
            }
        }

        double[] values = new double[UNKNOWN_COUNT];
        for (int row = 0; row < UNKNOWN_COUNT; row++) {
            values[row] = augmented[row][UNKNOWN_COUNT];
        }
        double conditionScore = minPivot == 0.0d ? Double.POSITIVE_INFINITY : maxPivot / minPivot;
        return Optional.of(new LinearSolution(values, conditionScore));
    }

    private static void swapRows(double[][] matrix, int first, int second) {
        if (first == second) {
            return;
        }
        double[] temporary = matrix[first];
        matrix[first] = matrix[second];
        matrix[second] = temporary;
    }

    private static double[][] multiply(double[][] left, double[][] right) {
        double[][] result = new double[MATRIX_SIZE][MATRIX_SIZE];
        for (int row = 0; row < MATRIX_SIZE; row++) {
            for (int col = 0; col < MATRIX_SIZE; col++) {
                for (int index = 0; index < MATRIX_SIZE; index++) {
                    result[row][col] += left[row][index] * right[index][col];
                }
            }
        }
        return result;
    }

    private static double[][] normalizeScale(double[][] matrix) {
        double scale = Math.abs(matrix[2][2]) < EPSILON ? frobeniusNorm(matrix) : matrix[2][2];
        if (Math.abs(scale) < EPSILON) {
            return matrix;
        }
        double[][] normalized = new double[MATRIX_SIZE][MATRIX_SIZE];
        for (int row = 0; row < MATRIX_SIZE; row++) {
            for (int col = 0; col < MATRIX_SIZE; col++) {
                normalized[row][col] = matrix[row][col] / scale;
            }
        }
        return normalized;
    }

    private static double frobeniusNorm(double[][] matrix) {
        double sum = 0.0d;
        for (double[] row : matrix) {
            for (double value : row) {
                sum += value * value;
            }
        }
        return Math.sqrt(sum);
    }

    private static double[] rowMajor(double[][] matrix) {
        double[] values = new double[MATRIX_SIZE * MATRIX_SIZE];
        int index = 0;
        for (int row = 0; row < MATRIX_SIZE; row++) {
            for (int col = 0; col < MATRIX_SIZE; col++) {
                values[index++] = matrix[row][col];
            }
        }
        return values;
    }

    private static boolean finite(double[] values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Matched canonical and source control point used for homography fitting.
     *
     * @param canonicalPoint canonical point
     * @param sourcePoint source-image point
     */
    record PointPair(CanonicalPoint canonicalPoint, SourcePoint sourcePoint) {
    }

    private record LinearSolution(double[] values, double conditionScore) {
    }

    private record PointNormalization(double meanX, double meanY, double scale) {

        private static Optional<PointNormalization> fromCanonical(List<PointPair> pointPairs) {
            List<CanonicalPoint> points = pointPairs.stream().map(PointPair::canonicalPoint).toList();
            return fromValues(
                    points.stream().mapToDouble(CanonicalPoint::x).toArray(),
                    points.stream().mapToDouble(CanonicalPoint::y).toArray()
            );
        }

        private static Optional<PointNormalization> fromSource(List<PointPair> pointPairs) {
            List<SourcePoint> points = pointPairs.stream().map(PointPair::sourcePoint).toList();
            return fromValues(
                    points.stream().mapToDouble(SourcePoint::x).toArray(),
                    points.stream().mapToDouble(SourcePoint::y).toArray()
            );
        }

        private static Optional<PointNormalization> fromValues(double[] xs, double[] ys) {
            double meanX = Arrays.stream(xs).average().orElse(0.0d);
            double meanY = Arrays.stream(ys).average().orElse(0.0d);
            double meanDistance = 0.0d;
            for (int index = 0; index < xs.length; index++) {
                meanDistance += Math.hypot(xs[index] - meanX, ys[index] - meanY);
            }
            meanDistance /= xs.length;
            if (meanDistance < EPSILON) {
                return Optional.empty();
            }
            return Optional.of(new PointNormalization(meanX, meanY, Math.sqrt(2.0d) / meanDistance));
        }

        private double normalizedX(CanonicalPoint point) {
            return scale * (point.x() - meanX);
        }

        private double normalizedY(CanonicalPoint point) {
            return scale * (point.y() - meanY);
        }

        private double normalizedX(SourcePoint point) {
            return scale * (point.x() - meanX);
        }

        private double normalizedY(SourcePoint point) {
            return scale * (point.y() - meanY);
        }

        private double[][] matrix() {
            return new double[][] {
                    {scale, 0.0d, -scale * meanX},
                    {0.0d, scale, -scale * meanY},
                    {0.0d, 0.0d, 1.0d}
            };
        }

        private double[][] inverseMatrix() {
            return new double[][] {
                    {1.0d / scale, 0.0d, meanX},
                    {0.0d, 1.0d / scale, meanY},
                    {0.0d, 0.0d, 1.0d}
            };
        }
    }
}
