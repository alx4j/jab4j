package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPolygon;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalControlPointEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementModelType;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSampleStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;

/**
 * Produces diagnostics-only local residual evidence from accepted global geometry and source-space module sampling.
 */
public final class LocalResidualAnalyzer {

    public static final int SCHEMA_VERSION = 1;

    private static final int MIN_RELIABLE_CONTROL_POINTS = 6;
    private static final int MIN_DISTRIBUTED_QUADRANTS = 3;
    private static final double MAX_RELIABLE_QUADRANT_SHARE = 0.75d;
    private static final double GLOBAL_SUFFICIENT_READABLE_RATIO = 0.85d;
    private static final double GLOBAL_SUFFICIENT_FAILURE_RATIO = 0.10d;
    private static final double COLOR_PRIMARY_REASON_SHARE = 0.75d;
    private static final double COLOR_PRIMARY_FAILURE_RATIO = 0.25d;
    private static final double LOCAL_DRIFT_MIN_FAILURE_RATIO = 0.08d;
    private static final double LOCAL_DRIFT_MAX_FAILURE_RATIO = 0.45d;
    private static final double FAILURE_CLUSTER_MAX_AREA_RATIO = 0.35d;
    private static final double MODEL_COMPLEX_FAILURE_RATIO = 0.35d;
    private static final double HARD_DISPLACEMENT_CAP_MODULES = 0.50d;
    private static final double DEFAULT_DISPLACEMENT_CAP_MODULES = 0.25d;
    private static final String SMOOTHNESS_CONSTRAINT = "diagnostics-only-no-local-correction-v1";
    private static final String SYNTHETIC_PIXEL_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    /**
     * Analyzes local residual diagnostics for concrete geometry and sampling evidence.
     *
     * @param geometryEvidence global geometry evidence
     * @param samplingEvidence source-space module sampling evidence
     * @return diagnostics-only local refinement evidence
     */
    public LocalRefinementEvidence analyze(
            GeometryFitEvidence geometryEvidence,
            ModuleSamplingEvidence samplingEvidence
    ) {
        return analyze(Optional.ofNullable(geometryEvidence), Optional.ofNullable(samplingEvidence));
    }

    /**
     * Analyzes local residual diagnostics when one prerequisite may be absent.
     *
     * <p>This method is intended for debug summaries where the exporter may not have retained source-space sampling
     * evidence. The returned evidence is stable and marked {@code NOT_AVAILABLE} when prerequisites are missing.</p>
     *
     * @param geometryEvidence optional global geometry evidence
     * @param samplingEvidence optional source-space module sampling evidence
     * @return diagnostics-only local refinement evidence
     */
    public LocalRefinementEvidence analyze(
            Optional<GeometryFitEvidence> geometryEvidence,
            Optional<ModuleSamplingEvidence> samplingEvidence
    ) {
        Objects.requireNonNull(geometryEvidence, "geometryEvidence must not be null");
        Objects.requireNonNull(samplingEvidence, "samplingEvidence must not be null");

        CaptureMediaCandidateId refinementId = refinementCandidateId(geometryEvidence, samplingEvidence);
        String baseGeometryCandidateId = refinementId.geometryCandidateId().orElseThrow();
        String baseSamplingCandidateId = refinementId.samplingCandidateId().orElseThrow();
        Optional<GeometryCandidateEvidence> baseGeometryCandidate = geometryEvidence
                .flatMap(evidence -> acceptedGeometryCandidate(evidence, baseGeometryCandidateId));

        boolean geometryAvailable = baseGeometryCandidate.isPresent();
        boolean samplingAvailable = samplingEvidence
                .filter(evidence -> sourceSpaceSamplingAvailable(evidence, baseGeometryCandidateId))
                .isPresent();

        ControlAnalysis controls = samplingAvailable
                ? analyzeControls(samplingEvidence.orElseThrow())
                : ControlAnalysis.unavailable(samplingEvidence);
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        LocalRefinementStatus status;

        if (!geometryAvailable || !samplingAvailable) {
            status = LocalRefinementStatus.NOT_AVAILABLE;
            if (!geometryAvailable) {
                reasonCodes.add(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GLOBAL_GEOMETRY);
            }
            if (!samplingAvailable) {
                reasonCodes.add(CaptureMediaEvidenceReasonCode.NO_SOURCE_SPACE_SAMPLING_EVIDENCE);
            }
        } else {
            Classification classification = classify(samplingEvidence.orElseThrow(), controls);
            status = classification.status();
            reasonCodes.addAll(classification.reasonCodes());
        }

        ReprojectionMetrics baseMetrics = baseGeometryCandidate
                .map(GeometryCandidateEvidence::reprojectionMetrics)
                .orElseGet(ReprojectionMetrics::zero);
        return new LocalRefinementEvidence(
                SCHEMA_VERSION,
                refinementId,
                status,
                baseGeometryCandidateId,
                baseSamplingCandidateId,
                baseMetrics,
                baseSamplingMetrics(samplingEvidence, controls),
                status == LocalRefinementStatus.NOT_AVAILABLE ? List.of() : controls.controlPoints(),
                LocalRefinementModelType.LOCAL_GRID,
                2,
                2,
                controls.residuals().max(),
                controls.residuals().max() * controls.moduleSizePixels(),
                SMOOTHNESS_CONSTRAINT,
                smoothnessScore(controls),
                regularizationScore(controls),
                improvementMetrics(samplingEvidence, controls, status, reasonCodes),
                false,
                deduplicated(reasonCodes)
        );
    }

    private Classification classify(ModuleSamplingEvidence samplingEvidence, ControlAnalysis controls) {
        if (controls.reliableControlCount() < MIN_RELIABLE_CONTROL_POINTS) {
            if (colorPrimaryBlocker(controls, false)) {
                return rejected(CaptureMediaEvidenceReasonCode.COLOR_CLASSIFICATION_IS_PRIMARY_BLOCKER);
            }
            return rejected(CaptureMediaEvidenceReasonCode.LOCAL_EVIDENCE_TOO_SPARSE);
        }
        if (!controls.distributed()) {
            if (colorPrimaryBlocker(controls, false)) {
                return rejected(CaptureMediaEvidenceReasonCode.COLOR_CLASSIFICATION_IS_PRIMARY_BLOCKER);
            }
            return rejected(CaptureMediaEvidenceReasonCode.CONTROL_POINTS_NOT_DISTRIBUTED);
        }
        if (globalSufficient(samplingEvidence, controls)) {
            return new Classification(
                    LocalRefinementStatus.LEFT_GLOBAL,
                    List.of(CaptureMediaEvidenceReasonCode.NO_MEASURABLE_IMPROVEMENT)
            );
        }
        if (colorPrimaryBlocker(controls, true)) {
            return rejected(CaptureMediaEvidenceReasonCode.COLOR_CLASSIFICATION_IS_PRIMARY_BLOCKER);
        }
        Optional<Classification> unsafe = unsafeClassification(controls);
        if (unsafe.isPresent()) {
            return unsafe.orElseThrow();
        }
        if (ambiguous(controls)) {
            return new Classification(
                    LocalRefinementStatus.AMBIGUOUS,
                    List.of(CaptureMediaEvidenceReasonCode.AMBIGUOUS_LOCAL_REFINEMENT)
            );
        }
        if (likelyLocalDrift(controls)) {
            return rejected(CaptureMediaEvidenceReasonCode.LOCAL_GEOMETRIC_DRIFT_SUSPECTED);
        }
        return new Classification(
                LocalRefinementStatus.AMBIGUOUS,
                List.of(CaptureMediaEvidenceReasonCode.AMBIGUOUS_LOCAL_REFINEMENT)
        );
    }

    private Classification rejected(CaptureMediaEvidenceReasonCode reasonCode) {
        return new Classification(LocalRefinementStatus.REJECTED, List.of(reasonCode));
    }

    private Optional<Classification> unsafeClassification(ControlAnalysis controls) {
        if (controls.residuals().max() > HARD_DISPLACEMENT_CAP_MODULES) {
            return Optional.of(new Classification(
                    LocalRefinementStatus.REJECTED,
                    List.of(
                            CaptureMediaEvidenceReasonCode.LOCAL_CORRECTION_TOO_LARGE,
                            CaptureMediaEvidenceReasonCode.OVERFIT_RISK
                    )
            ));
        }
        if (controls.failureRatio() > MODEL_COMPLEX_FAILURE_RATIO && controls.failureQuadrantCount() >= 3) {
            return Optional.of(new Classification(
                    LocalRefinementStatus.REJECTED,
                    List.of(
                            CaptureMediaEvidenceReasonCode.LOCAL_MODEL_TOO_COMPLEX,
                            CaptureMediaEvidenceReasonCode.OVERFIT_RISK
                    )
            ));
        }
        return Optional.empty();
    }

    private boolean globalSufficient(ModuleSamplingEvidence samplingEvidence, ControlAnalysis controls) {
        if (samplingEvidence.status() == ModuleSamplingStatus.SAMPLED) {
            return true;
        }
        return controls.readableRatio() >= GLOBAL_SUFFICIENT_READABLE_RATIO
                && controls.failureRatio() <= GLOBAL_SUFFICIENT_FAILURE_RATIO
                && controls.clippedOrOutOfBoundsRatio() <= GLOBAL_SUFFICIENT_FAILURE_RATIO
                && controls.residuals().max() <= DEFAULT_DISPLACEMENT_CAP_MODULES;
    }

    private boolean colorPrimaryBlocker(ControlAnalysis controls, boolean distributedGeometryEvidence) {
        if (controls.failureCount() == 0) {
            return false;
        }
        boolean colorDominates = controls.colorReasonShare() >= COLOR_PRIMARY_REASON_SHARE;
        return colorDominates
                && (controls.failureRatio() >= COLOR_PRIMARY_FAILURE_RATIO || !distributedGeometryEvidence);
    }

    private boolean ambiguous(ControlAnalysis controls) {
        boolean mixedColorAndGeometry = controls.colorReasonShare() >= 0.25d
                && controls.colorReasonShare() < COLOR_PRIMARY_REASON_SHARE
                && controls.failureRatio() >= LOCAL_DRIFT_MIN_FAILURE_RATIO;
        boolean broadResiduals = controls.failureRatio() >= LOCAL_DRIFT_MIN_FAILURE_RATIO
                && !controls.failureClustered()
                && controls.failureQuadrantCount() > 1;
        return mixedColorAndGeometry || broadResiduals;
    }

    private boolean likelyLocalDrift(ControlAnalysis controls) {
        return controls.failureRatio() >= LOCAL_DRIFT_MIN_FAILURE_RATIO
                && controls.failureRatio() <= LOCAL_DRIFT_MAX_FAILURE_RATIO
                && controls.failureClustered()
                && controls.colorReasonShare() < 0.50d
                && controls.clippedOrOutOfBoundsRatio() <= GLOBAL_SUFFICIENT_FAILURE_RATIO;
    }

    private ControlAnalysis analyzeControls(ModuleSamplingEvidence samplingEvidence) {
        List<LocalControlPointEvidence> controlPoints = new ArrayList<>(samplingEvidence.modules().size());
        int[] reliableByQuadrant = new int[4];
        int[] failureByQuadrant = new int[4];
        int reliableCount = 0;
        int inlierCount = 0;
        int failureCount = 0;
        int colorReasonFailureCount = 0;
        int clippedOrOutOfBoundsCount = 0;
        int minFailureX = Integer.MAX_VALUE;
        int minFailureY = Integer.MAX_VALUE;
        int maxFailureX = Integer.MIN_VALUE;
        int maxFailureY = Integer.MIN_VALUE;
        double moduleSizeSum = 0.0d;
        int moduleSizeCount = 0;
        List<Double> residuals = new ArrayList<>(samplingEvidence.modules().size());

        for (ModuleEvidence module : samplingEvidence.modules()) {
            boolean reliable = reliableControl(module);
            double residualModules = residualEstimateModules(module);
            boolean inlier = reliable && residualModules <= DEFAULT_DISPLACEMENT_CAP_MODULES;
            int quadrant = quadrant(module, samplingEvidence.moduleWidth(), samplingEvidence.moduleHeight());
            if (reliable) {
                reliableCount++;
                reliableByQuadrant[quadrant]++;
                moduleSizeSum += moduleSizePixels(module);
                moduleSizeCount++;
            }
            if (inlier) {
                inlierCount++;
            }
            if (failure(module)) {
                failureCount++;
                failureByQuadrant[quadrant]++;
                if (colorReason(module)) {
                    colorReasonFailureCount++;
                }
                minFailureX = Math.min(minFailureX, module.moduleX());
                minFailureY = Math.min(minFailureY, module.moduleY());
                maxFailureX = Math.max(maxFailureX, module.moduleX());
                maxFailureY = Math.max(maxFailureY, module.moduleY());
            }
            if (module.status() == ModuleSampleStatus.CLIPPED
                    || module.status() == ModuleSampleStatus.OUT_OF_BOUNDS) {
                clippedOrOutOfBoundsCount++;
            }
            if (module.sampleCount() > 0 || module.status() == ModuleSampleStatus.OUT_OF_BOUNDS) {
                residuals.add(residualModules);
            }
            controlPoints.add(controlPoint(module, residualModules, reliable, inlier));
        }

        double observed = samplingEvidence.sampledModuleCount();
        double total = Math.max(1.0d, samplingEvidence.totalModuleCount());
        int reliableQuadrants = nonZeroCount(reliableByQuadrant);
        double maxQuadrantShare = reliableCount == 0 ? 0.0d : max(reliableByQuadrant) / (double) reliableCount;
        int failureQuadrants = nonZeroCount(failureByQuadrant);
        double failureAreaRatio = failureCount == 0
                ? 0.0d
                : ((maxFailureX - minFailureX + 1.0d) * (maxFailureY - minFailureY + 1.0d)) / total;
        boolean failureClustered = failureCount >= 2
                && failureAreaRatio <= FAILURE_CLUSTER_MAX_AREA_RATIO
                && failureQuadrants <= 2;
        return new ControlAnalysis(
                controlPoints,
                samplingEvidence.totalModuleCount(),
                (int) observed,
                reliableCount,
                inlierCount,
                reliableQuadrants,
                maxQuadrantShare,
                failureCount,
                failureQuadrants,
                failureClustered,
                failureAreaRatio,
                colorReasonFailureCount,
                clippedOrOutOfBoundsCount,
                samplingEvidence.readableModuleCount() / total,
                failureCount / total,
                clippedOrOutOfBoundsCount / total,
                ResidualSummary.from(residuals),
                moduleSizeCount == 0 ? 0.0d : moduleSizeSum / moduleSizeCount
        );
    }

    private LocalControlPointEvidence controlPoint(
            ModuleEvidence module,
            double residualModules,
            boolean reliable,
            boolean inlier
    ) {
        CanonicalPoint canonicalPoint = center(module.canonicalPolygon());
        SourcePoint expectedSourcePoint = center(module.sourcePolygon());
        Optional<SourcePoint> observedSourcePoint = module.sampleCount() > 0
                ? Optional.of(center(module.innerSourcePolygon()))
                : Optional.empty();
        double moduleSizePixels = moduleSizePixels(module);
        double measuredResidualPixels = observedSourcePoint
                .map(observed -> distance(expectedSourcePoint, observed))
                .orElse(residualModules * moduleSizePixels);
        double measuredResidualModules = moduleSizePixels == 0.0d
                ? residualModules
                : measuredResidualPixels / moduleSizePixels;
        return new LocalControlPointEvidence(
                "module-" + module.moduleX() + "-" + module.moduleY(),
                canonicalPoint,
                expectedSourcePoint,
                observedSourcePoint,
                measuredResidualPixels,
                measuredResidualPixels,
                measuredResidualModules,
                measuredResidualModules,
                inlier,
                reliable,
                confidence(module),
                module.reasonCodes()
        );
    }

    private Map<String, Double> baseSamplingMetrics(
            Optional<ModuleSamplingEvidence> samplingEvidence,
            ControlAnalysis controls
    ) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        ModuleSamplingEvidence sampling = samplingEvidence.orElse(null);
        metrics.put("totalModuleCount", sampling == null ? 0.0d : sampling.totalModuleCount());
        metrics.put("sampledModuleCount", sampling == null ? 0.0d : sampling.sampledModuleCount());
        metrics.put("readableModuleCount", sampling == null ? 0.0d : sampling.readableModuleCount());
        metrics.put("ambiguousModuleCount", sampling == null ? 0.0d : sampling.ambiguousModuleCount());
        metrics.put("unreadableModuleCount", sampling == null ? 0.0d : sampling.unreadableModuleCount());
        metrics.put("clippedModuleCount", sampling == null ? 0.0d : sampling.clippedModuleCount());
        metrics.put("outOfBoundsModuleCount", sampling == null ? 0.0d : sampling.outOfBoundsModuleCount());
        metrics.put("readableRatio", controls.readableRatio());
        metrics.put("ambiguousUnreadableRatio", controls.failureRatio());
        metrics.put("clippedOutOfBoundsRatio", controls.clippedOrOutOfBoundsRatio());
        metrics.put("tileDecodeAttemptCount", sampling == null ? 0.0d : sampling.tileDecodeAttemptCount());
        metrics.put("acceptedPayloadCount", sampling == null ? 0.0d : sampling.acceptedPayloadCount());
        metrics.put("control.expectedCount", (double) controls.expectedControlPointCount());
        metrics.put("control.observedCount", (double) controls.observedControlPointCount());
        metrics.put("control.matchedCount", (double) controls.reliableControlCount());
        metrics.put("control.inlierCount", (double) controls.inlierControlCount());
        metrics.put("control.distributedQuadrantCount", (double) controls.distributedQuadrantCount());
        metrics.put("control.maxQuadrantShare", controls.maxReliableQuadrantShare());
        metrics.put("residual.meanModules", controls.residuals().mean());
        metrics.put("residual.medianModules", controls.residuals().median());
        metrics.put("residual.p95Modules", controls.residuals().p95());
        metrics.put("residual.maxModules", controls.residuals().max());
        samplingEvidence.ifPresent(evidence -> addPrefixed(metrics, "confidenceMargin.", evidence.confidenceMarginSummary()));
        samplingEvidence.ifPresent(evidence -> addPrefixed(metrics, "colorVariance.", evidence.colorVarianceSummary()));
        return Map.copyOf(metrics);
    }

    private Map<String, Double> improvementMetrics(
            Optional<ModuleSamplingEvidence> samplingEvidence,
            ControlAnalysis controls,
            LocalRefinementStatus status,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        ModuleSamplingEvidence sampling = samplingEvidence.orElse(null);
        double readableModules = sampling == null ? 0.0d : sampling.readableModuleCount();
        double acceptedPayloads = sampling == null ? 0.0d : sampling.acceptedPayloadCount();
        double samplingConfidence = sampling == null
                ? 0.0d
                : sampling.confidenceMarginSummary().getOrDefault("mean", 0.0d);
        metrics.put("samplingConfidenceBefore", samplingConfidence);
        metrics.put("samplingConfidenceAfter", samplingConfidence);
        metrics.put("readableModulesBefore", readableModules);
        metrics.put("readableModulesAfter", readableModules);
        metrics.put("decodedTilesBefore", acceptedPayloads);
        metrics.put("decodedTilesAfter", acceptedPayloads);
        metrics.put("residualMeanBeforeModules", controls.residuals().mean());
        metrics.put("residualMeanAfterModules", controls.residuals().mean());
        metrics.put("localGeometricDriftLikely",
                reasonCodes.contains(CaptureMediaEvidenceReasonCode.LOCAL_GEOMETRIC_DRIFT_SUSPECTED) ? 1.0d : 0.0d);
        metrics.put("colorPrimaryBlockerLikely",
                reasonCodes.contains(CaptureMediaEvidenceReasonCode.COLOR_CLASSIFICATION_IS_PRIMARY_BLOCKER)
                        ? 1.0d
                        : 0.0d);
        metrics.put("ambiguousResidualPattern", status == LocalRefinementStatus.AMBIGUOUS ? 1.0d : 0.0d);
        metrics.put("overfitRisk",
                reasonCodes.contains(CaptureMediaEvidenceReasonCode.OVERFIT_RISK) ? 1.0d : 0.0d);
        return Map.copyOf(metrics);
    }

    private void addPrefixed(Map<String, Double> target, String prefix, Map<String, Double> source) {
        source.forEach((name, value) -> target.put(prefix + name, value));
    }

    private double smoothnessScore(ControlAnalysis controls) {
        if (controls.expectedControlPointCount() == 0) {
            return 0.0d;
        }
        double clusterPenalty = controls.failureClustered()
                ? 0.10d
                : Math.min(0.70d, controls.failureQuadrantCount() * 0.15d);
        return clampUnit(1.0d - controls.residuals().mean() - clusterPenalty);
    }

    private double regularizationScore(ControlAnalysis controls) {
        if (controls.expectedControlPointCount() == 0) {
            return 0.0d;
        }
        double countScore = Math.min(1.0d, controls.reliableControlCount() / (double) MIN_RELIABLE_CONTROL_POINTS);
        double distributionScore = controls.distributedQuadrantCount() / 4.0d;
        return clampUnit((0.45d * countScore) + (0.45d * distributionScore) + (0.10d * (1.0d - controls.failureRatio())));
    }

    private CaptureMediaCandidateId refinementCandidateId(
            Optional<GeometryFitEvidence> geometryEvidence,
            Optional<ModuleSamplingEvidence> samplingEvidence
    ) {
        if (samplingEvidence.isPresent()) {
            return CaptureMediaCandidateId.refinementCandidate(samplingEvidence.orElseThrow().candidateId());
        }
        Optional<GeometryCandidateEvidence> acceptedCandidate = geometryEvidence.flatMap(this::selectedAcceptedCandidate);
        CaptureMediaCandidateId geometryId = acceptedCandidate
                .map(GeometryCandidateEvidence::candidateId)
                .orElseGet(() -> placeholderGeometryCandidateId(geometryEvidence));
        return CaptureMediaCandidateId.refinementCandidate(
                CaptureMediaCandidateId.samplingCandidate(geometryId, 50, 1)
        );
    }

    private Optional<GeometryCandidateEvidence> selectedAcceptedCandidate(GeometryFitEvidence evidence) {
        if (evidence.status() != GeometryFitStatus.ACCEPTED) {
            return Optional.empty();
        }
        if (evidence.selectedGeometryCandidateId().isPresent()) {
            String selectedId = evidence.selectedGeometryCandidateId().orElseThrow();
            Optional<GeometryCandidateEvidence> selected = acceptedGeometryCandidate(evidence, selectedId);
            if (selected.isPresent()) {
                return selected;
            }
        }
        return evidence.retainedCandidates()
                .stream()
                .filter(candidate -> candidate.status() == GeometryFitStatus.ACCEPTED)
                .filter(GeometryCandidateEvidence::invertible)
                .min(Comparator.comparingInt(GeometryCandidateEvidence::rank));
    }

    private Optional<GeometryCandidateEvidence> acceptedGeometryCandidate(
            GeometryFitEvidence evidence,
            String geometryCandidateId
    ) {
        if (evidence.status() != GeometryFitStatus.ACCEPTED) {
            return Optional.empty();
        }
        return evidence.retainedCandidates()
                .stream()
                .filter(candidate -> candidate.status() == GeometryFitStatus.ACCEPTED)
                .filter(GeometryCandidateEvidence::invertible)
                .filter(candidate -> candidate.candidateId()
                        .geometryCandidateId()
                        .filter(geometryCandidateId::equals)
                        .isPresent())
                .findFirst();
    }

    private CaptureMediaCandidateId placeholderGeometryCandidateId(
            Optional<GeometryFitEvidence> geometryEvidence
    ) {
        if (geometryEvidence.isEmpty()) {
            return CaptureMediaCandidateId.geometryCandidate(
                    CaptureMediaCandidateId.patternEvidence(CaptureMediaCandidateId.proposalCandidate(
                            "LOCAL_RESIDUAL",
                            0,
                            "not-available",
                            SYNTHETIC_PIXEL_HASH,
                            1,
                            1,
                            1
                    )),
                    1
            );
        }
        CaptureMediaCandidateId base = geometryEvidence.orElseThrow().candidateId();
        CaptureMediaCandidateId patternId = base.patternEvidenceId().isPresent()
                ? base
                : CaptureMediaCandidateId.patternEvidence(base);
        return CaptureMediaCandidateId.geometryCandidate(patternId, 1);
    }

    private boolean sourceSpaceSamplingAvailable(ModuleSamplingEvidence samplingEvidence, String geometryCandidateId) {
        return samplingEvidence.status() != ModuleSamplingStatus.NOT_AVAILABLE
                && samplingEvidence.status() != ModuleSamplingStatus.WITHHELD
                && samplingEvidence.geometryCandidateId().equals(geometryCandidateId)
                && !samplingEvidence.modules().isEmpty();
    }

    private boolean reliableControl(ModuleEvidence module) {
        return module.status() == ModuleSampleStatus.READABLE
                && module.assignedPaletteIndex().isPresent()
                && module.sampleCount() > 0;
    }

    private boolean failure(ModuleEvidence module) {
        return module.status() == ModuleSampleStatus.AMBIGUOUS
                || module.status() == ModuleSampleStatus.UNREADABLE;
    }

    private boolean colorReason(ModuleEvidence module) {
        return module.reasonCodes().contains(CaptureMediaEvidenceReasonCode.HIGH_COLOR_VARIANCE)
                || module.reasonCodes().contains(CaptureMediaEvidenceReasonCode.LOW_COLOR_MARGIN)
                || module.reasonCodes().contains(CaptureMediaEvidenceReasonCode.PALETTE_CALIBRATION_UNAVAILABLE);
    }

    private double residualEstimateModules(ModuleEvidence module) {
        return switch (module.status()) {
            case READABLE -> Math.min(0.20d,
                    marginRisk(module.confidenceMargin()) + Math.min(0.08d, module.colorVariance() / 12000.0d));
            case AMBIGUOUS -> colorReason(module) ? 0.35d : 0.40d;
            case UNREADABLE -> colorReason(module) ? 0.45d : 0.48d;
            case CLIPPED, OUT_OF_BOUNDS -> 0.60d;
        };
    }

    private double marginRisk(double confidenceMargin) {
        if (confidenceMargin >= 64.0d) {
            return 0.0d;
        }
        return ((64.0d - Math.max(0.0d, confidenceMargin)) / 64.0d) * 0.12d;
    }

    private double confidence(ModuleEvidence module) {
        return switch (module.status()) {
            case READABLE -> clampUnit(0.55d + (Math.min(128.0d, module.confidenceMargin()) / 256.0d)
                    - Math.min(0.20d, module.colorVariance() / 15000.0d));
            case AMBIGUOUS -> 0.35d;
            case UNREADABLE -> module.sampleCount() > 0 ? 0.15d : 0.0d;
            case CLIPPED, OUT_OF_BOUNDS -> 0.0d;
        };
    }

    private int quadrant(ModuleEvidence module, int moduleWidth, int moduleHeight) {
        boolean right = module.moduleX() >= moduleWidth / 2.0d;
        boolean bottom = module.moduleY() >= moduleHeight / 2.0d;
        return (bottom ? 2 : 0) + (right ? 1 : 0);
    }

    private int nonZeroCount(int[] values) {
        int count = 0;
        for (int value : values) {
            if (value > 0) {
                count++;
            }
        }
        return count;
    }

    private int max(int[] values) {
        int max = 0;
        for (int value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private CanonicalPoint center(CanonicalPolygon polygon) {
        double x = 0.0d;
        double y = 0.0d;
        for (CanonicalPoint point : polygon.vertices()) {
            x += point.x();
            y += point.y();
        }
        return new CanonicalPoint(x / polygon.vertices().size(), y / polygon.vertices().size());
    }

    private SourcePoint center(SourcePolygon polygon) {
        double x = 0.0d;
        double y = 0.0d;
        for (SourcePoint point : polygon.vertices()) {
            x += point.x();
            y += point.y();
        }
        return new SourcePoint(x / polygon.vertices().size(), y / polygon.vertices().size());
    }

    private double moduleSizePixels(ModuleEvidence module) {
        List<SourcePoint> vertices = module.sourcePolygon().vertices();
        if (vertices.size() < 4) {
            return 0.0d;
        }
        double top = distance(vertices.get(0), vertices.get(1));
        double right = distance(vertices.get(1), vertices.get(2));
        double bottom = distance(vertices.get(2), vertices.get(3));
        double left = distance(vertices.get(3), vertices.get(0));
        return (top + right + bottom + left) / 4.0d;
    }

    private double distance(SourcePoint first, SourcePoint second) {
        double deltaX = first.x() - second.x();
        double deltaY = first.y() - second.y();
        return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
    }

    private double clampUnit(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private List<CaptureMediaEvidenceReasonCode> deduplicated(
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return List.copyOf(new LinkedHashSet<>(reasonCodes));
    }

    private record Classification(
            LocalRefinementStatus status,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
    }

    private record ControlAnalysis(
            List<LocalControlPointEvidence> controlPoints,
            int expectedControlPointCount,
            int observedControlPointCount,
            int reliableControlCount,
            int inlierControlCount,
            int distributedQuadrantCount,
            double maxReliableQuadrantShare,
            int failureCount,
            int failureQuadrantCount,
            boolean failureClustered,
            double failureAreaRatio,
            int colorReasonFailureCount,
            int clippedOrOutOfBoundsCount,
            double readableRatio,
            double failureRatio,
            double clippedOrOutOfBoundsRatio,
            ResidualSummary residuals,
            double moduleSizePixels
    ) {

        private ControlAnalysis {
            controlPoints = List.copyOf(controlPoints);
        }

        private static ControlAnalysis unavailable(Optional<ModuleSamplingEvidence> samplingEvidence) {
            int expectedCount = samplingEvidence.map(ModuleSamplingEvidence::totalModuleCount).orElse(0);
            int observedCount = samplingEvidence.map(ModuleSamplingEvidence::sampledModuleCount).orElse(0);
            double total = Math.max(1.0d, expectedCount);
            return new ControlAnalysis(
                    List.of(),
                    expectedCount,
                    observedCount,
                    0,
                    0,
                    0,
                    0.0d,
                    0,
                    0,
                    false,
                    0.0d,
                    0,
                    samplingEvidence
                            .map(evidence -> evidence.clippedModuleCount() + evidence.outOfBoundsModuleCount())
                            .orElse(0),
                    samplingEvidence.map(evidence -> evidence.readableModuleCount() / total).orElse(0.0d),
                    0.0d,
                    samplingEvidence
                            .map(evidence -> (evidence.clippedModuleCount() + evidence.outOfBoundsModuleCount())
                                    / total)
                            .orElse(0.0d),
                    ResidualSummary.empty(),
                    0.0d
            );
        }

        private boolean distributed() {
            return distributedQuadrantCount >= MIN_DISTRIBUTED_QUADRANTS
                    && maxReliableQuadrantShare <= MAX_RELIABLE_QUADRANT_SHARE;
        }

        private double colorReasonShare() {
            return failureCount == 0 ? 0.0d : colorReasonFailureCount / (double) failureCount;
        }
    }

    private record ResidualSummary(double mean, double median, double p95, double max) {

        private static ResidualSummary empty() {
            return new ResidualSummary(0.0d, 0.0d, 0.0d, 0.0d);
        }

        private static ResidualSummary from(List<Double> residuals) {
            if (residuals.isEmpty()) {
                return empty();
            }
            List<Double> sorted = residuals.stream().sorted().toList();
            double sum = 0.0d;
            for (double residual : sorted) {
                sum += residual;
            }
            return new ResidualSummary(
                    sum / sorted.size(),
                    percentile(sorted, 0.50d),
                    percentile(sorted, 0.95d),
                    sorted.get(sorted.size() - 1)
            );
        }

        private static double percentile(List<Double> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
        }
    }
}
