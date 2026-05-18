package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.cv.PerspectiveTransform;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.CoordinateObservationSource;
import com.alx4j.jab4j.reader.capture.media.evidence.FinderRole;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitModelType;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidenceStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureType;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;

/**
 * Fits reader-owned barcode-plane geometry from pattern feature evidence and records reprojection evidence.
 */
public final class CaptureMediaGeometryFitter {

    public static final int SCHEMA_VERSION = 1;

    private static final double MEAN_GATE_MODULES = 0.25d;
    private static final double P95_GATE_MODULES = 0.40d;
    private static final double MAX_GATE_MODULES = 0.60d;
    private static final double MIN_DOMINANCE_MARGIN = 0.10d;
    private static final double DUPLICATE_TOLERANCE = 1.0e-7d;
    private static final double COLLINEAR_AREA_RATIO = 1.0e-10d;
    private static final double UNSTABLE_AREA_RATIO = 1.0e-6d;
    private static final int MIN_ACCEPTED_CONTROL_POINTS = 4;
    private static final int MAX_SUBSET_ATTEMPTS = 256;

    private static final List<Double> EMPTY_TRANSFORM = List.of(
            0.0d, 0.0d, 0.0d,
            0.0d, 0.0d, 0.0d,
            0.0d, 0.0d, 0.0d
    );

    private final ReprojectionMetricCalculator metricCalculator;

    /**
     * Creates a geometry fitter with the default reprojection metric calculator.
     */
    public CaptureMediaGeometryFitter() {
        this(new ReprojectionMetricCalculator());
    }

    /**
     * Creates a geometry fitter with an explicit metric calculator for focused tests.
     *
     * @param metricCalculator reprojection metric calculator
     */
    public CaptureMediaGeometryFitter(ReprojectionMetricCalculator metricCalculator) {
        this.metricCalculator = Objects.requireNonNull(metricCalculator, "metricCalculator must not be null");
    }

    /**
     * Fits barcode-plane geometry for one normalized capture candidate and its pattern evidence.
     *
     * @param frame normalized capture frame carrying source-space corner metadata
     * @param patternEvidence upstream candidate-scoped pattern evidence
     * @return immutable geometry fit evidence
     */
    public GeometryFitEvidence fit(NormalizedCaptureFrame frame, PatternEvidence patternEvidence) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(patternEvidence, "patternEvidence must not be null");

        FeaturePointExtraction extraction = extractControlPoints(frame, patternEvidence);
        if (extraction.expectedPointCount() == 0 || extraction.matchedPoints().isEmpty()) {
            return aggregateEvidence(
                    patternEvidence.candidateId(),
                    GeometryFitStatus.NOT_AVAILABLE,
                    List.of(),
                    List.of(CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE)
            );
        }

        List<CaptureMediaEvidenceReasonCode> blockingReasons = blockingReasons(extraction);
        if (!blockingReasons.isEmpty()) {
            GeometryFitStatus status = blockingReasons.contains(CaptureMediaEvidenceReasonCode.TOO_FEW_POINTS)
                    || blockingReasons.contains(CaptureMediaEvidenceReasonCode.UNSTABLE_TRANSFORM)
                    ? GeometryFitStatus.WITHHELD
                    : GeometryFitStatus.REJECTED;
            CandidateEvaluation candidate = placeholderCandidate(status, extraction, blockingReasons);
            return retainedEvidence(patternEvidence, List.of(candidate), List.of(candidate), status);
        }

        List<CandidateEvaluation> candidates = deduplicatedCandidates(evaluateCandidates(patternEvidence, extraction));
        if (candidates.isEmpty()) {
            CandidateEvaluation candidate = placeholderCandidate(
                    GeometryFitStatus.REJECTED,
                    extraction,
                    List.of(CaptureMediaEvidenceReasonCode.NON_INVERTIBLE_TRANSFORM)
            );
            return retainedEvidence(patternEvidence, List.of(candidate), List.of(candidate), GeometryFitStatus.REJECTED);
        }

        List<CandidateEvaluation> sorted = sortedCandidates(candidates);
        GeometryFitStatus aggregateStatus = aggregateStatus(sorted);
        List<CandidateEvaluation> retained = sorted.stream()
                .limit(CaptureMediaCandidateId.MAX_GEOMETRY_CANDIDATE_RANK)
                .toList();
        return retainedEvidence(patternEvidence, sorted, retained, aggregateStatus);
    }

    private FeaturePointExtraction extractControlPoints(NormalizedCaptureFrame frame, PatternEvidence patternEvidence) {
        List<GeometryControlPoint> points = new ArrayList<>();
        List<Double> moduleSizeEstimates = new ArrayList<>();
        EnumMap<FinderRole, RoleCenter> roleCenters = new EnumMap<>(FinderRole.class);
        List<CaptureMediaEvidenceReasonCode> extractionReasons = new ArrayList<>();
        int expectedPointCount = 0;
        int observedPointCount = 0;
        int missingExpectedPointCount = 0;
        int featureIndex = 0;

        for (PatternFeatureEvidence feature : patternEvidence.features()) {
            if (!supportedControlFeature(feature)) {
                featureIndex++;
                continue;
            }
            List<CanonicalPoint> canonicalVertices = feature.canonicalFootprint().vertices();
            expectedPointCount += canonicalVertices.size();
            if (feature.matchedModuleCount() <= 0 || feature.confidence() <= 0.0d) {
                missingExpectedPointCount += canonicalVertices.size();
                featureIndex++;
                continue;
            }

            List<SourcePoint> sourceVertices = sourceVertices(frame, feature, extractionReasons);
            int matchedVertices = Math.min(canonicalVertices.size(), sourceVertices.size());
            observedPointCount += matchedVertices;
            missingExpectedPointCount += Math.max(0, canonicalVertices.size() - matchedVertices);
            for (int vertexIndex = 0; vertexIndex < matchedVertices; vertexIndex++) {
                points.add(new GeometryControlPoint(
                        canonicalVertices.get(vertexIndex),
                        sourceVertices.get(vertexIndex),
                        feature.observationSource(),
                        feature.finderRole(),
                        featureIndex,
                        vertexIndex
                ));
            }
            moduleSizeEstimates.addAll(moduleSizeEstimates(sourceVertices, feature.expectedModuleCount()));
            feature.finderRole().ifPresent(role -> roleCenters.put(
                    role,
                    new RoleCenter(centroidCanonical(canonicalVertices), centroidSource(sourceVertices))
            ));
            featureIndex++;
        }

        return new FeaturePointExtraction(
                points,
                expectedPointCount,
                observedPointCount,
                Math.max(0, missingExpectedPointCount),
                estimatedModuleSize(moduleSizeEstimates),
                roleCenters,
                deduplicated(extractionReasons)
        );
    }

    private boolean supportedControlFeature(PatternFeatureEvidence feature) {
        return feature.featureType() == PatternFeatureType.FINDER
                || feature.featureType() == PatternFeatureType.ALIGNMENT;
    }

    private List<SourcePoint> sourceVertices(
            NormalizedCaptureFrame frame,
            PatternFeatureEvidence feature,
            List<CaptureMediaEvidenceReasonCode> extractionReasons
    ) {
        if (feature.observationSource() == CoordinateObservationSource.SOURCE_SPACE) {
            return feature.sourceFootprint().vertices();
        }
        try {
            PerspectiveTransform normalizedToSource = PerspectiveTransform.fromUnitSquareTo(frame.frameCorners());
            List<SourcePoint> mapped = new ArrayList<>();
            for (SourcePoint point : feature.sourceFootprint().vertices()) {
                PerspectiveTransform.PerspectivePoint mappedPoint = normalizedToSource.map(
                        point.x() / frame.normalizedWidthPixels(),
                        point.y() / frame.normalizedHeightPixels()
                );
                mapped.add(new SourcePoint(mappedPoint.x(), mappedPoint.y()));
            }
            return List.copyOf(mapped);
        } catch (IllegalArgumentException exception) {
            extractionReasons.add(CaptureMediaEvidenceReasonCode.NON_INVERTIBLE_TRANSFORM);
            return List.of();
        }
    }

    private List<Double> moduleSizeEstimates(List<SourcePoint> vertices, int expectedModuleCount) {
        if (vertices.size() < 2 || expectedModuleCount <= 0) {
            return List.of();
        }
        double sideModules = Math.sqrt(expectedModuleCount);
        if (!Double.isFinite(sideModules) || sideModules <= 0.0d) {
            return List.of();
        }
        List<Double> estimates = new ArrayList<>();
        for (int index = 0; index < vertices.size(); index++) {
            SourcePoint first = vertices.get(index);
            SourcePoint second = vertices.get((index + 1) % vertices.size());
            double length = distance(first, second);
            if (length > DUPLICATE_TOLERANCE) {
                estimates.add(length / sideModules);
            }
        }
        return List.copyOf(estimates);
    }

    private double estimatedModuleSize(List<Double> moduleSizeEstimates) {
        List<Double> positive = moduleSizeEstimates.stream()
                .filter(value -> Double.isFinite(value) && value > DUPLICATE_TOLERANCE)
                .sorted()
                .toList();
        if (positive.isEmpty()) {
            return 1.0d;
        }
        return positive.get(positive.size() / 2);
    }

    private List<CaptureMediaEvidenceReasonCode> blockingReasons(FeaturePointExtraction extraction) {
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>(extraction.extractionReasons());
        if (extraction.matchedPoints().size() < MIN_ACCEPTED_CONTROL_POINTS) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.TOO_FEW_POINTS);
        }
        if (hasDuplicatePoints(extraction.matchedPoints())) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.DUPLICATE_POINTS);
        }
        PointSpread canonicalSpread = PointSpread.canonical(extraction.matchedPoints());
        PointSpread sourceSpread = PointSpread.source(extraction.matchedPoints());
        if (canonicalSpread.collinear() || sourceSpread.collinear()) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.DEGENERATE_POINTS);
        } else if (canonicalSpread.unstable() || sourceSpread.unstable()) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.UNSTABLE_TRANSFORM);
        }
        return deduplicated(reasonCodes);
    }

    private boolean hasDuplicatePoints(List<GeometryControlPoint> points) {
        for (int outer = 0; outer < points.size(); outer++) {
            GeometryControlPoint first = points.get(outer);
            for (int inner = outer + 1; inner < points.size(); inner++) {
                GeometryControlPoint second = points.get(inner);
                if (distance(first.canonicalPoint(), second.canonicalPoint()) <= DUPLICATE_TOLERANCE
                        || distance(first.sourcePoint(), second.sourcePoint()) <= DUPLICATE_TOLERANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<CandidateEvaluation> evaluateCandidates(
            PatternEvidence patternEvidence,
            FeaturePointExtraction extraction
    ) {
        List<CandidateEvaluation> candidates = new ArrayList<>();
        int generationOrder = 0;
        Optional<CandidateEvaluation> allPoints = evaluateCandidate(
                patternEvidence,
                extraction,
                extraction.matchedPoints(),
                generationOrder++
        );
        allPoints.ifPresent(candidates::add);
        if (allPoints.filter(candidate -> candidate.status() == GeometryFitStatus.ACCEPTED).isPresent()) {
            return List.copyOf(candidates);
        }

        if (extraction.matchedPoints().size() > MIN_ACCEPTED_CONTROL_POINTS) {
            List<int[]> combinations = fourPointCombinations(extraction.matchedPoints().size());
            for (int[] combination : combinations) {
                List<GeometryControlPoint> subset = new ArrayList<>(MIN_ACCEPTED_CONTROL_POINTS);
                for (int pointIndex : combination) {
                    subset.add(extraction.matchedPoints().get(pointIndex));
                }
                if (PointSpread.canonical(subset).collinear() || PointSpread.source(subset).collinear()) {
                    continue;
                }
                evaluateCandidate(patternEvidence, extraction, subset, generationOrder++).ifPresent(candidates::add);
                if (generationOrder > MAX_SUBSET_ATTEMPTS) {
                    break;
                }
            }
        }
        return List.copyOf(candidates);
    }

    private Optional<CandidateEvaluation> evaluateCandidate(
            PatternEvidence patternEvidence,
            FeaturePointExtraction extraction,
            List<GeometryControlPoint> fitPoints,
            int generationOrder
    ) {
        Optional<HomographyTransform> fitted = HomographyTransform.fit(pointPairs(fitPoints));
        if (fitted.isEmpty()) {
            return Optional.empty();
        }
        HomographyTransform transform = fitted.orElseThrow();
        List<Double> errorsPixels = new ArrayList<>();
        for (GeometryControlPoint point : extraction.matchedPoints()) {
            try {
                errorsPixels.add(distance(transform.map(point.canonicalPoint()), point.sourcePoint()));
            } catch (IllegalArgumentException exception) {
                return Optional.of(rejectedCandidate(
                        extraction,
                        transform,
                        generationOrder,
                        CaptureMediaEvidenceReasonCode.NON_INVERTIBLE_TRANSFORM
                ));
            }
        }
        ReprojectionMetrics metrics = metricCalculator.calculate(errorsPixels, extraction.moduleSizePixels());
        List<CaptureMediaEvidenceReasonCode> reasonCodes = candidateReasonCodes(patternEvidence, extraction, transform, metrics);
        GeometryFitStatus status = candidateStatus(patternEvidence, extraction, transform, metrics, reasonCodes);
        return Optional.of(new CandidateEvaluation(
                status,
                transform.parameters(),
                transform.conditionScore(),
                transform.invertible(),
                extraction.expectedPointCount(),
                extraction.observedPointCount(),
                extraction.matchedPoints().size(),
                extraction.missingExpectedPointCount(),
                metrics,
                score(metrics),
                inlierCount(metrics),
                extraction.matchedPoints().size() - inlierCount(metrics),
                degeneracyFlags(reasonCodes),
                reasonCodes,
                generationOrder,
                transform.roundedKey()
        ));
    }

    private CandidateEvaluation rejectedCandidate(
            FeaturePointExtraction extraction,
            HomographyTransform transform,
            int generationOrder,
            CaptureMediaEvidenceReasonCode reasonCode
    ) {
        return new CandidateEvaluation(
                GeometryFitStatus.REJECTED,
                transform.parameters(),
                transform.conditionScore(),
                transform.invertible(),
                extraction.expectedPointCount(),
                extraction.observedPointCount(),
                extraction.matchedPoints().size(),
                extraction.missingExpectedPointCount(),
                ReprojectionMetrics.zero(),
                0.0d,
                0,
                extraction.matchedPoints().size(),
                degeneracyFlags(List.of(reasonCode)),
                List.of(reasonCode),
                generationOrder,
                transform.roundedKey()
        );
    }

    private List<HomographyTransform.PointPair> pointPairs(List<GeometryControlPoint> points) {
        return points.stream()
                .map(point -> new HomographyTransform.PointPair(point.canonicalPoint(), point.sourcePoint()))
                .toList();
    }

    private List<CaptureMediaEvidenceReasonCode> candidateReasonCodes(
            PatternEvidence patternEvidence,
            FeaturePointExtraction extraction,
            HomographyTransform transform,
            ReprojectionMetrics metrics
    ) {
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        if (!transform.invertible()) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.NON_INVERTIBLE_TRANSFORM);
        }
        if (!transform.stable()) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.UNSTABLE_TRANSFORM);
        }
        if (mirroredOrSwapped(extraction.roleCenters())) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MIRRORED_OR_SWAPPED_ROLES);
        }
        if (!withinReprojectionGates(metrics)) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.HIGH_REPROJECTION_ERROR);
        }
        if (normalizedCandidateOnly(extraction)) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.NORMALIZED_CANDIDATE_ONLY);
        }
        if (patternEvidence.status() == PatternEvidenceStatus.AMBIGUOUS) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MULTIPLE_PLAUSIBLE_FITS);
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_DOMINANCE_MARGIN);
        } else if (patternEvidence.status() != PatternEvidenceStatus.DETECTED) {
            reasonCodes.addAll(patternEvidence.reasonCodes());
        }
        return deduplicated(reasonCodes);
    }

    private GeometryFitStatus candidateStatus(
            PatternEvidence patternEvidence,
            FeaturePointExtraction extraction,
            HomographyTransform transform,
            ReprojectionMetrics metrics,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        if (!transform.invertible()
                || reasonCodes.contains(CaptureMediaEvidenceReasonCode.MIRRORED_OR_SWAPPED_ROLES)
                || reasonCodes.contains(CaptureMediaEvidenceReasonCode.HIGH_REPROJECTION_ERROR)) {
            return GeometryFitStatus.REJECTED;
        }
        if (!transform.stable()
                || normalizedCandidateOnly(extraction)
                || patternEvidence.status() != PatternEvidenceStatus.DETECTED) {
            return patternEvidence.status() == PatternEvidenceStatus.AMBIGUOUS
                    ? GeometryFitStatus.AMBIGUOUS
                    : GeometryFitStatus.WITHHELD;
        }
        if (!withinReprojectionGates(metrics)) {
            return GeometryFitStatus.REJECTED;
        }
        return GeometryFitStatus.ACCEPTED;
    }

    private boolean withinReprojectionGates(ReprojectionMetrics metrics) {
        return metrics.meanErrorModules() <= MEAN_GATE_MODULES
                && metrics.p95ErrorModules() <= P95_GATE_MODULES
                && metrics.maxErrorModules() <= MAX_GATE_MODULES;
    }

    private boolean normalizedCandidateOnly(FeaturePointExtraction extraction) {
        return !extraction.matchedPoints().isEmpty()
                && extraction.matchedPoints()
                .stream()
                .allMatch(point -> point.observationSource() == CoordinateObservationSource.NORMALIZED_CANDIDATE);
    }

    private boolean mirroredOrSwapped(Map<FinderRole, RoleCenter> roleCenters) {
        if (!roleCenters.keySet().containsAll(List.of(
                FinderRole.TOP_LEFT,
                FinderRole.TOP_RIGHT,
                FinderRole.BOTTOM_LEFT,
                FinderRole.BOTTOM_RIGHT
        ))) {
            return false;
        }
        List<CanonicalPoint> canonical = List.of(
                roleCenters.get(FinderRole.TOP_LEFT).canonicalCenter(),
                roleCenters.get(FinderRole.TOP_RIGHT).canonicalCenter(),
                roleCenters.get(FinderRole.BOTTOM_RIGHT).canonicalCenter(),
                roleCenters.get(FinderRole.BOTTOM_LEFT).canonicalCenter()
        );
        List<SourcePoint> source = List.of(
                roleCenters.get(FinderRole.TOP_LEFT).sourceCenter(),
                roleCenters.get(FinderRole.TOP_RIGHT).sourceCenter(),
                roleCenters.get(FinderRole.BOTTOM_RIGHT).sourceCenter(),
                roleCenters.get(FinderRole.BOTTOM_LEFT).sourceCenter()
        );
        if (Math.signum(signedAreaCanonical(canonical)) != Math.signum(signedAreaSource(source))) {
            return true;
        }
        CanonicalPoint canonicalCentroid = centroidCanonical(canonical);
        SourcePoint sourceCentroid = centroidSource(source);
        for (FinderRole role : FinderRole.values()) {
            RoleCenter center = roleCenters.get(role);
            if (Math.signum(center.canonicalCenter().x() - canonicalCentroid.x())
                    != Math.signum(center.sourceCenter().x() - sourceCentroid.x())) {
                return true;
            }
            if (Math.signum(center.canonicalCenter().y() - canonicalCentroid.y())
                    != Math.signum(center.sourceCenter().y() - sourceCentroid.y())) {
                return true;
            }
        }
        return false;
    }

    private double score(ReprojectionMetrics metrics) {
        double weightedRatio = (0.50d * (metrics.meanErrorModules() / MEAN_GATE_MODULES))
                + (0.30d * (metrics.p95ErrorModules() / P95_GATE_MODULES))
                + (0.20d * (metrics.maxErrorModules() / MAX_GATE_MODULES));
        return clampUnit(1.0d - weightedRatio);
    }

    private int inlierCount(ReprojectionMetrics metrics) {
        int inliers = 0;
        for (double errorModules : metrics.pointErrorsModules()) {
            if (errorModules <= MAX_GATE_MODULES) {
                inliers++;
            }
        }
        return inliers;
    }

    private List<String> degeneracyFlags(
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return reasonCodes.stream()
                .filter(reason -> reason == CaptureMediaEvidenceReasonCode.DEGENERATE_POINTS
                        || reason == CaptureMediaEvidenceReasonCode.DUPLICATE_POINTS
                        || reason == CaptureMediaEvidenceReasonCode.MIRRORED_OR_SWAPPED_ROLES
                        || reason == CaptureMediaEvidenceReasonCode.NON_INVERTIBLE_TRANSFORM
                        || reason == CaptureMediaEvidenceReasonCode.UNSTABLE_TRANSFORM
                        || reason == CaptureMediaEvidenceReasonCode.NORMALIZED_CANDIDATE_ONLY)
                .map(Enum::name)
                .toList();
    }

    private CandidateEvaluation placeholderCandidate(
            GeometryFitStatus status,
            FeaturePointExtraction extraction,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return new CandidateEvaluation(
                status,
                EMPTY_TRANSFORM,
                0.0d,
                false,
                extraction.expectedPointCount(),
                extraction.observedPointCount(),
                extraction.matchedPoints().size(),
                extraction.missingExpectedPointCount(),
                ReprojectionMetrics.zero(),
                0.0d,
                0,
                extraction.matchedPoints().size(),
                degeneracyFlags(reasonCodes),
                reasonCodes,
                0,
                "placeholder"
        );
    }

    private List<CandidateEvaluation> deduplicatedCandidates(List<CandidateEvaluation> candidates) {
        Map<String, CandidateEvaluation> byTransform = new LinkedHashMap<>();
        for (CandidateEvaluation candidate : candidates) {
            CandidateEvaluation current = byTransform.get(candidate.transformKey());
            if (current == null || compareCandidate(candidate, current) < 0) {
                byTransform.put(candidate.transformKey(), candidate);
            }
        }
        return List.copyOf(byTransform.values());
    }

    private List<CandidateEvaluation> sortedCandidates(List<CandidateEvaluation> candidates) {
        return candidates.stream()
                .sorted(this::compareCandidate)
                .toList();
    }

    private int compareCandidate(CandidateEvaluation first, CandidateEvaluation second) {
        Comparator<CandidateEvaluation> comparator = Comparator
                .comparingInt((CandidateEvaluation candidate) -> statusRank(candidate.status()))
                .thenComparing(candidate -> candidate.metrics().meanErrorModules())
                .thenComparing(candidate -> candidate.metrics().p95ErrorModules())
                .thenComparing(candidate -> candidate.metrics().maxErrorModules())
                .thenComparing(Comparator.comparingDouble(CandidateEvaluation::score).reversed())
                .thenComparing(CandidateEvaluation::transformKey)
                .thenComparingInt(CandidateEvaluation::generationOrder);
        return comparator.compare(first, second);
    }

    private int statusRank(GeometryFitStatus status) {
        return switch (status) {
            case ACCEPTED -> 0;
            case AMBIGUOUS -> 1;
            case WITHHELD -> 2;
            case REJECTED -> 3;
            case NOT_AVAILABLE -> 4;
        };
    }

    private GeometryFitStatus aggregateStatus(List<CandidateEvaluation> sorted) {
        List<CandidateEvaluation> accepted = sorted.stream()
                .filter(candidate -> candidate.status() == GeometryFitStatus.ACCEPTED)
                .toList();
        if (accepted.isEmpty()) {
            return sorted.get(0).status();
        }
        if (accepted.size() > 1 && dominanceMargin(accepted, 0) < MIN_DOMINANCE_MARGIN) {
            return GeometryFitStatus.AMBIGUOUS;
        }
        return GeometryFitStatus.ACCEPTED;
    }

    private GeometryFitEvidence retainedEvidence(
            PatternEvidence patternEvidence,
            List<CandidateEvaluation> sorted,
            List<CandidateEvaluation> retained,
            GeometryFitStatus aggregateStatus
    ) {
        List<GeometryCandidateEvidence> evidenceCandidates = new ArrayList<>();
        List<CandidateEvaluation> accepted = sorted.stream()
                .filter(candidate -> candidate.status() == GeometryFitStatus.ACCEPTED)
                .toList();
        for (int index = 0; index < retained.size(); index++) {
            CandidateEvaluation candidate = retained.get(index);
            int rank = index + 1;
            evidenceCandidates.add(candidateEvidence(
                    patternEvidence,
                    candidate,
                    rank,
                    aggregateStatus,
                    dominanceMarginForCandidate(accepted, candidate)
            ));
        }

        Optional<String> selected = aggregateStatus == GeometryFitStatus.ACCEPTED
                ? evidenceCandidates.stream()
                .filter(GeometryCandidateEvidence::retainedForSampling)
                .map(GeometryCandidateEvidence::candidateId)
                .map(CaptureMediaCandidateId::geometryCandidateId)
                .flatMap(Optional::stream)
                .findFirst()
                : Optional.empty();
        return new GeometryFitEvidence(
                SCHEMA_VERSION,
                patternEvidence.candidateId(),
                aggregateStatus,
                evidenceCandidates,
                selected,
                Optional.empty(),
                aggregateReasonCodes(aggregateStatus, retained)
        );
    }

    private GeometryFitEvidence aggregateEvidence(
            CaptureMediaCandidateId candidateId,
            GeometryFitStatus status,
            List<GeometryCandidateEvidence> retainedCandidates,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return new GeometryFitEvidence(
                SCHEMA_VERSION,
                candidateId,
                status,
                retainedCandidates,
                Optional.empty(),
                Optional.empty(),
                deduplicated(reasonCodes)
        );
    }

    private GeometryCandidateEvidence candidateEvidence(
            PatternEvidence patternEvidence,
            CandidateEvaluation candidate,
            int rank,
            GeometryFitStatus aggregateStatus,
            double dominanceMargin
    ) {
        boolean retainedForSampling = aggregateStatus == GeometryFitStatus.ACCEPTED
                && candidate.status() == GeometryFitStatus.ACCEPTED
                && rank == 1;
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>(candidate.reasonCodes());
        if (aggregateStatus == GeometryFitStatus.AMBIGUOUS && candidate.status() == GeometryFitStatus.ACCEPTED) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MULTIPLE_PLAUSIBLE_FITS);
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_DOMINANCE_MARGIN);
        }
        return new GeometryCandidateEvidence(
                CaptureMediaCandidateId.geometryCandidate(patternEvidence.candidateId(), rank),
                rank,
                candidate.status(),
                GeometryFitModelType.HOMOGRAPHY,
                "pattern-feature-canonical-pixels:" + patternEvidence.layoutProfileId(),
                "source-image-pixels",
                candidate.transformParameters(),
                candidate.conditionScore(),
                candidate.degeneracyFlags(),
                candidate.invertible(),
                candidate.observedPointCount(),
                candidate.expectedPointCount(),
                candidate.matchedPointCount(),
                candidate.inlierCount(),
                candidate.outlierCount(),
                candidate.missingExpectedPointCount(),
                candidate.metrics(),
                candidate.score(),
                dominanceMargin,
                retainedForSampling,
                false,
                deduplicated(reasonCodes)
        );
    }

    private List<CaptureMediaEvidenceReasonCode> aggregateReasonCodes(
            GeometryFitStatus aggregateStatus,
            List<CandidateEvaluation> retained
    ) {
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        for (CandidateEvaluation candidate : retained) {
            reasonCodes.addAll(candidate.reasonCodes());
        }
        if (aggregateStatus == GeometryFitStatus.AMBIGUOUS) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MULTIPLE_PLAUSIBLE_FITS);
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_DOMINANCE_MARGIN);
        }
        return deduplicated(reasonCodes);
    }

    private double dominanceMarginForCandidate(List<CandidateEvaluation> accepted, CandidateEvaluation candidate) {
        int index = accepted.indexOf(candidate);
        if (index < 0) {
            return 0.0d;
        }
        return dominanceMargin(accepted, index);
    }

    private double dominanceMargin(List<CandidateEvaluation> accepted, int index) {
        if (accepted.size() <= index + 1) {
            return 1.0d;
        }
        return clampUnit(accepted.get(index).score() - accepted.get(index + 1).score());
    }

    private List<int[]> fourPointCombinations(int size) {
        List<int[]> combinations = new ArrayList<>();
        for (int first = 0; first < size - 3; first++) {
            for (int second = first + 1; second < size - 2; second++) {
                for (int third = second + 1; third < size - 1; third++) {
                    for (int fourth = third + 1; fourth < size; fourth++) {
                        combinations.add(new int[] {first, second, third, fourth});
                        if (combinations.size() >= MAX_SUBSET_ATTEMPTS) {
                            return combinations;
                        }
                    }
                }
            }
        }
        return combinations;
    }

    private double clampUnit(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static double distance(CanonicalPoint first, CanonicalPoint second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private static double distance(SourcePoint first, SourcePoint second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private static CanonicalPoint centroidCanonical(List<CanonicalPoint> points) {
        double x = 0.0d;
        double y = 0.0d;
        for (CanonicalPoint point : points) {
            x += point.x();
            y += point.y();
        }
        return new CanonicalPoint(x / points.size(), y / points.size());
    }

    private static SourcePoint centroidSource(List<SourcePoint> points) {
        double x = 0.0d;
        double y = 0.0d;
        for (SourcePoint point : points) {
            x += point.x();
            y += point.y();
        }
        return new SourcePoint(x / points.size(), y / points.size());
    }

    private static double signedAreaCanonical(List<CanonicalPoint> points) {
        double area = 0.0d;
        for (int index = 0; index < points.size(); index++) {
            CanonicalPoint first = points.get(index);
            CanonicalPoint second = points.get((index + 1) % points.size());
            area += (first.x() * second.y()) - (first.y() * second.x());
        }
        return area / 2.0d;
    }

    private static double signedAreaSource(List<SourcePoint> points) {
        double area = 0.0d;
        for (int index = 0; index < points.size(); index++) {
            SourcePoint first = points.get(index);
            SourcePoint second = points.get((index + 1) % points.size());
            area += (first.x() * second.y()) - (first.y() * second.x());
        }
        return area / 2.0d;
    }

    private <T> List<T> deduplicated(List<T> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private record GeometryControlPoint(
            CanonicalPoint canonicalPoint,
            SourcePoint sourcePoint,
            CoordinateObservationSource observationSource,
            Optional<FinderRole> finderRole,
            int featureIndex,
            int vertexIndex
    ) {
    }

    private record RoleCenter(CanonicalPoint canonicalCenter, SourcePoint sourceCenter) {
    }

    private record FeaturePointExtraction(
            List<GeometryControlPoint> matchedPoints,
            int expectedPointCount,
            int observedPointCount,
            int missingExpectedPointCount,
            double moduleSizePixels,
            Map<FinderRole, RoleCenter> roleCenters,
            List<CaptureMediaEvidenceReasonCode> extractionReasons
    ) {

        private FeaturePointExtraction {
            matchedPoints = List.copyOf(matchedPoints);
            roleCenters = Map.copyOf(roleCenters);
            extractionReasons = List.copyOf(extractionReasons);
        }
    }

    private record CandidateEvaluation(
            GeometryFitStatus status,
            List<Double> transformParameters,
            double conditionScore,
            boolean invertible,
            int expectedPointCount,
            int observedPointCount,
            int matchedPointCount,
            int missingExpectedPointCount,
            ReprojectionMetrics metrics,
            double score,
            int inlierCount,
            int outlierCount,
            List<String> degeneracyFlags,
            List<CaptureMediaEvidenceReasonCode> reasonCodes,
            int generationOrder,
            String transformKey
    ) {
    }

    private record PointSpread(double areaRatio) {

        private static PointSpread canonical(List<GeometryControlPoint> points) {
            return fromCanonical(points.stream().map(GeometryControlPoint::canonicalPoint).toList());
        }

        private static PointSpread source(List<GeometryControlPoint> points) {
            return fromSource(points.stream().map(GeometryControlPoint::sourcePoint).toList());
        }

        private static PointSpread fromCanonical(List<CanonicalPoint> points) {
            double minX = points.stream().mapToDouble(CanonicalPoint::x).min().orElse(0.0d);
            double maxX = points.stream().mapToDouble(CanonicalPoint::x).max().orElse(0.0d);
            double minY = points.stream().mapToDouble(CanonicalPoint::y).min().orElse(0.0d);
            double maxY = points.stream().mapToDouble(CanonicalPoint::y).max().orElse(0.0d);
            double maxArea = 0.0d;
            for (int first = 0; first < points.size() - 2; first++) {
                for (int second = first + 1; second < points.size() - 1; second++) {
                    for (int third = second + 1; third < points.size(); third++) {
                        maxArea = Math.max(maxArea, Math.abs(area2(
                                points.get(first).x(),
                                points.get(first).y(),
                                points.get(second).x(),
                                points.get(second).y(),
                                points.get(third).x(),
                                points.get(third).y()
                        )));
                    }
                }
            }
            return new PointSpread(maxArea / spreadScale(minX, maxX, minY, maxY));
        }

        private static PointSpread fromSource(List<SourcePoint> points) {
            double minX = points.stream().mapToDouble(SourcePoint::x).min().orElse(0.0d);
            double maxX = points.stream().mapToDouble(SourcePoint::x).max().orElse(0.0d);
            double minY = points.stream().mapToDouble(SourcePoint::y).min().orElse(0.0d);
            double maxY = points.stream().mapToDouble(SourcePoint::y).max().orElse(0.0d);
            double maxArea = 0.0d;
            for (int first = 0; first < points.size() - 2; first++) {
                for (int second = first + 1; second < points.size() - 1; second++) {
                    for (int third = second + 1; third < points.size(); third++) {
                        maxArea = Math.max(maxArea, Math.abs(area2(
                                points.get(first).x(),
                                points.get(first).y(),
                                points.get(second).x(),
                                points.get(second).y(),
                                points.get(third).x(),
                                points.get(third).y()
                        )));
                    }
                }
            }
            return new PointSpread(maxArea / spreadScale(minX, maxX, minY, maxY));
        }

        private boolean collinear() {
            return areaRatio <= COLLINEAR_AREA_RATIO;
        }

        private boolean unstable() {
            return areaRatio <= UNSTABLE_AREA_RATIO;
        }

        private static double area2(double ax, double ay, double bx, double by, double cx, double cy) {
            return ((bx - ax) * (cy - ay)) - ((by - ay) * (cx - ax));
        }

        private static double spreadScale(double minX, double maxX, double minY, double maxY) {
            double width = maxX - minX;
            double height = maxY - minY;
            double scale = (width * width) + (height * height);
            return Math.max(scale, 1.0d);
        }
    }
}
