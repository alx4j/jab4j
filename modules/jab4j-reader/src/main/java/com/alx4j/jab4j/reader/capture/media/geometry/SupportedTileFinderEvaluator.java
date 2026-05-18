package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.evidence.FinderRole;

/**
 * Evaluates the reader-supported four-corner 3x3 tile finder contract.
 *
 * <p>The evaluator owns finder role placement, expected palette indexes, strict exact matching, and camera recovery
 * thresholds. It does not decode tile payloads.</p>
 */
public final class SupportedTileFinderEvaluator {

    public static final int FINDER_SIZE_MODULES = 3;
    public static final int EXPECTED_MODULES_PER_FINDER = FINDER_SIZE_MODULES * FINDER_SIZE_MODULES;
    public static final int CAMERA_MIN_RECOVERABLE_FINDER_COUNT = 3;
    public static final int CAMERA_MIN_MATCHES_PER_RECOVERABLE_FINDER = 6;
    public static final double MIN_CAMERA_RECOVERABLE_AVERAGE_CONFIDENCE = 0.30d;

    private static final List<FinderSpec> FINDER_SPECS = List.of(
            new FinderSpec(FinderRole.TOP_LEFT, false, false, 0),
            new FinderSpec(FinderRole.TOP_RIGHT, false, true, 0),
            new FinderSpec(FinderRole.BOTTOM_LEFT, true, false, 6),
            new FinderSpec(FinderRole.BOTTOM_RIGHT, true, true, 3)
    );

    /**
     * Returns the supported finder windows for the supplied square tile dimension.
     *
     * @param dimension tile dimension in modules
     * @return ordered finder windows in canonical role order
     */
    public List<FinderWindow> finderWindows(int dimension) {
        requireDimension(dimension);
        return FINDER_SPECS.stream()
                .map(spec -> spec.window(dimension))
                .toList();
    }

    /**
     * Evaluates finder role matches against the supported four-corner contract.
     *
     * @param moduleColors row-major module palette indexes
     * @param dimension square tile dimension in modules
     * @return per-role finder evaluation and aggregate match confidence
     */
    public Evaluation evaluate(List<Integer> moduleColors, int dimension) {
        Objects.requireNonNull(moduleColors, "moduleColors must not be null");
        requireDimension(dimension);
        int expectedModuleCount = dimension * dimension;
        if (moduleColors.size() != expectedModuleCount) {
            throw new IllegalArgumentException("moduleColors size must equal dimension squared");
        }

        EnumMap<FinderRole, FinderRoleEvaluation> roleEvaluations = new EnumMap<>(FinderRole.class);
        for (FinderWindow window : finderWindows(dimension)) {
            int matches = 0;
            for (int row = window.startRow(); row < window.startRow() + window.sizeModules(); row++) {
                for (int col = window.startCol(); col < window.startCol() + window.sizeModules(); col++) {
                    if (moduleColors.get((row * dimension) + col) == window.expectedColor()) {
                        matches++;
                    }
                }
            }
            roleEvaluations.put(
                    window.role(),
                    new FinderRoleEvaluation(window.role(), matches, EXPECTED_MODULES_PER_FINDER)
            );
        }
        return new Evaluation(roleEvaluations);
    }

    private void requireDimension(int dimension) {
        if (dimension < FINDER_SIZE_MODULES) {
            throw new IllegalArgumentException("dimension must be at least the finder size");
        }
    }

    /**
     * Canonical window occupied by one supported finder role.
     *
     * @param role canonical finder role
     * @param startRow zero-based starting row
     * @param startCol zero-based starting column
     * @param sizeModules finder width and height in modules
     * @param expectedColor expected palette index for every module in the window
     */
    public record FinderWindow(
            FinderRole role,
            int startRow,
            int startCol,
            int sizeModules,
            int expectedColor
    ) {

        /**
         * Creates a validated finder window.
         *
         * @param role canonical finder role
         * @param startRow zero-based starting row
         * @param startCol zero-based starting column
         * @param sizeModules finder width and height in modules
         * @param expectedColor expected palette index
         */
        public FinderWindow {
            Objects.requireNonNull(role, "role must not be null");
            if (startRow < 0) {
                throw new IllegalArgumentException("startRow must be non-negative");
            }
            if (startCol < 0) {
                throw new IllegalArgumentException("startCol must be non-negative");
            }
            if (sizeModules <= 0) {
                throw new IllegalArgumentException("sizeModules must be positive");
            }
            if (expectedColor < 0) {
                throw new IllegalArgumentException("expectedColor must be non-negative");
            }
        }
    }

    /**
     * Finder match counts for one supported role.
     *
     * @param role canonical finder role
     * @param matchedModuleCount modules matching the role's expected palette index
     * @param expectedModuleCount modules expected in the role window
     */
    public record FinderRoleEvaluation(
            FinderRole role,
            int matchedModuleCount,
            int expectedModuleCount
    ) {

        /**
         * Creates a validated per-role finder evaluation.
         *
         * @param role canonical finder role
         * @param matchedModuleCount matched modules
         * @param expectedModuleCount expected modules
         */
        public FinderRoleEvaluation {
            Objects.requireNonNull(role, "role must not be null");
            if (matchedModuleCount < 0) {
                throw new IllegalArgumentException("matchedModuleCount must be non-negative");
            }
            if (expectedModuleCount <= 0) {
                throw new IllegalArgumentException("expectedModuleCount must be positive");
            }
            if (matchedModuleCount > expectedModuleCount) {
                throw new IllegalArgumentException("matchedModuleCount must not exceed expectedModuleCount");
            }
        }

        /**
         * Returns the matched-to-expected module ratio for this role.
         *
         * @return normalized role confidence between 0 and 1
         */
        public double confidence() {
            return (double) matchedModuleCount / expectedModuleCount;
        }

        /**
         * Returns whether all modules in this role match the expected palette index.
         *
         * @return true when the role is an exact finder match
         */
        public boolean exact() {
            return matchedModuleCount == expectedModuleCount;
        }

        /**
         * Returns whether this role satisfies the camera recoverability threshold.
         *
         * @return true when at least six of the nine finder modules match
         */
        public boolean recoverable() {
            return matchedModuleCount >= CAMERA_MIN_MATCHES_PER_RECOVERABLE_FINDER;
        }
    }

    /**
     * Aggregate result for the supported tile-finder contract.
     *
     * @param roleEvaluations per-role finder match results
     */
    public record Evaluation(Map<FinderRole, FinderRoleEvaluation> roleEvaluations) {

        /**
         * Creates a validated aggregate finder evaluation.
         *
         * @param roleEvaluations per-role finder match results
         */
        public Evaluation {
            Objects.requireNonNull(roleEvaluations, "roleEvaluations must not be null");
            EnumMap<FinderRole, FinderRoleEvaluation> copy = new EnumMap<>(FinderRole.class);
            for (FinderSpec spec : FINDER_SPECS) {
                FinderRole role = spec.role();
                FinderRoleEvaluation roleEvaluation = roleEvaluations.get(role);
                if (roleEvaluation == null) {
                    throw new IllegalArgumentException("roleEvaluations must include " + role);
                }
                if (roleEvaluation.role() != role) {
                    throw new IllegalArgumentException("role evaluation key must match its role");
                }
                copy.put(role, roleEvaluation);
            }
            roleEvaluations = Collections.unmodifiableMap(copy);
        }

        /**
         * Returns the evaluation for one finder role.
         *
         * @param role finder role
         * @return role evaluation
         */
        public FinderRoleEvaluation role(FinderRole role) {
            return roleEvaluations.get(Objects.requireNonNull(role, "role must not be null"));
        }

        /**
         * Returns whether every supported finder role is an exact 9-of-9 match.
         *
         * @return true when all roles are exact
         */
        public boolean exact() {
            return roleEvaluations.values().stream().allMatch(FinderRoleEvaluation::exact);
        }

        /**
         * Returns whether the evaluation is supported before downstream sampling-confidence checks.
         *
         * @param cameraDerivedCandidate true when the candidate came from camera-derived media
         * @return true for exact candidates, or recoverable camera-derived candidates
         */
        public boolean supported(boolean cameraDerivedCandidate) {
            return exact() || (cameraDerivedCandidate && recoverableCount() >= CAMERA_MIN_RECOVERABLE_FINDER_COUNT);
        }

        /**
         * Returns whether the finder roles and supplied average confidence satisfy camera recovery thresholds.
         *
         * @param averageConfidence average sampling confidence for the candidate
         * @return true when at least three roles are recoverable and confidence is high enough
         */
        public boolean recoverableWithAverageConfidence(double averageConfidence) {
            return recoverableCount() >= CAMERA_MIN_RECOVERABLE_FINDER_COUNT
                    && averageConfidence >= MIN_CAMERA_RECOVERABLE_AVERAGE_CONFIDENCE;
        }

        /**
         * Returns the number of finder roles that satisfy the camera partial-match threshold.
         *
         * @return recoverable finder role count
         */
        public int recoverableCount() {
            int count = 0;
            for (FinderRoleEvaluation roleEvaluation : roleEvaluations.values()) {
                count += roleEvaluation.recoverable() ? 1 : 0;
            }
            return count;
        }

        /**
         * Returns all matched finder modules across supported roles.
         *
         * @return aggregate matched module count
         */
        public int matchedModuleCount() {
            return roleEvaluations.values().stream()
                    .mapToInt(FinderRoleEvaluation::matchedModuleCount)
                    .sum();
        }

        /**
         * Returns all expected finder modules across supported roles.
         *
         * @return aggregate expected module count
         */
        public int expectedModuleCount() {
            return roleEvaluations.values().stream()
                    .mapToInt(FinderRoleEvaluation::expectedModuleCount)
                    .sum();
        }

        /**
         * Returns aggregate matched-to-expected finder confidence.
         *
         * @return normalized aggregate confidence between 0 and 1
         */
        public double aggregateConfidence() {
            return (double) matchedModuleCount() / expectedModuleCount();
        }
    }

    private record FinderSpec(FinderRole role, boolean bottom, boolean right, int expectedColor) {

        private FinderWindow window(int dimension) {
            int startRow = bottom ? dimension - FINDER_SIZE_MODULES : 0;
            int startCol = right ? dimension - FINDER_SIZE_MODULES : 0;
            return new FinderWindow(role, startRow, startCol, FINDER_SIZE_MODULES, expectedColor);
        }
    }
}
