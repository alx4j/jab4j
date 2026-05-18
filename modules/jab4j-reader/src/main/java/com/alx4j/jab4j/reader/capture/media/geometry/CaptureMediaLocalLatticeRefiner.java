package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalControlPointEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementModelType;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample;

/**
 * Coordinates diagnostics and bounded applied local-grid refinement for MVP-9 source-space sampling.
 */
public final class CaptureMediaLocalLatticeRefiner {

    private static final double CONFIDENCE_IMPROVEMENT_THRESHOLD = 1.0d;

    private final LocalResidualAnalyzer residualAnalyzer;
    private final boolean localRefinementEnabled;

    /**
     * Creates a refiner facade with the default residual analyzer.
     */
    public CaptureMediaLocalLatticeRefiner() {
        this(new LocalResidualAnalyzer(), true);
    }

    /**
     * Creates a refiner facade with an explicit analyzer for focused tests.
     *
     * @param residualAnalyzer analyzer used to classify local residual evidence
     */
    public CaptureMediaLocalLatticeRefiner(LocalResidualAnalyzer residualAnalyzer) {
        this(residualAnalyzer, true);
    }

    /**
     * Creates a refiner facade with explicit enablement for internal rollback-switch checks.
     *
     * @param residualAnalyzer analyzer used to classify local residual evidence
     * @param localRefinementEnabled true when bounded local corrections may be sampled
     */
    public CaptureMediaLocalLatticeRefiner(
            LocalResidualAnalyzer residualAnalyzer,
            boolean localRefinementEnabled
    ) {
        this.residualAnalyzer = Objects.requireNonNull(residualAnalyzer, "residualAnalyzer must not be null");
        this.localRefinementEnabled = localRefinementEnabled;
    }

    /**
     * Produces local residual diagnostics without applying or returning corrected sampling positions.
     *
     * @param geometryEvidence global geometry evidence
     * @param samplingEvidence source-space module sampling evidence
     * @return diagnostics-only local refinement evidence
     */
    public LocalRefinementEvidence diagnose(
            GeometryFitEvidence geometryEvidence,
            ModuleSamplingEvidence samplingEvidence
    ) {
        return residualAnalyzer.analyze(geometryEvidence, samplingEvidence);
    }

    /**
     * Evaluates whether baseline source-space sampling can safely produce one local-grid correction.
     *
     * @param geometryEvidence accepted global geometry evidence
     * @param samplingEvidence baseline source-space sampling evidence
     * @return attempted local refinement with diagnostics and optional correction
     */
    public LocalRefinementAttempt attempt(
            GeometryFitEvidence geometryEvidence,
            ModuleSamplingEvidence samplingEvidence
    ) {
        LocalRefinementEvidence diagnostics = diagnose(geometryEvidence, samplingEvidence);
        Optional<LocalGridRefinement> localGrid = LocalGridRefinement.from(diagnostics);
        return new LocalRefinementAttempt(diagnostics, samplingEvidence, localGrid);
    }

    /**
     * Applies a bounded local-grid correction only when a refined source-space sample improves the baseline.
     *
     * <p>The baseline sample remains authoritative unless the corrected sample improves decoded payload count,
     * tile-attempt count, readable-module count, problem-module count, or confidence margin. This keeps the local
     * lattice path fail-closed behind the existing protocol validation gates.</p>
     *
     * @param sourceFrame retained source frame whose pixels are still available
     * @param normalizedFrame normalized candidate and layout context
     * @param geometryEvidence fitted geometry evidence for the normalized candidate
     * @param baselineSample already measured baseline source-space sample
     * @param sampler source-space sampler used to measure the local-grid correction
     * @return selected validation sample and local-refinement evidence
     */
    public RefinementResult applyIfBeneficial(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame normalizedFrame,
            GeometryFitEvidence geometryEvidence,
            SourceSpaceValidationSample baselineSample,
            CaptureMediaSourceSpaceModuleSampler sampler
    ) {
        Objects.requireNonNull(sourceFrame, "sourceFrame must not be null");
        Objects.requireNonNull(normalizedFrame, "normalizedFrame must not be null");
        Objects.requireNonNull(geometryEvidence, "geometryEvidence must not be null");
        Objects.requireNonNull(baselineSample, "baselineSample must not be null");
        Objects.requireNonNull(sampler, "sampler must not be null");

        Optional<ModuleSamplingEvidence> baselineEvidence = selectedSamplingEvidence(baselineSample.evidence());
        if (baselineEvidence.isEmpty()) {
            return new RefinementResult(baselineSample, diagnose(geometryEvidence, null));
        }
        ModuleSamplingEvidence baseline = baselineEvidence.orElseThrow();
        if (!localRefinementEnabled) {
            return new RefinementResult(baselineSample, disabled(geometryEvidence, baseline));
        }

        LocalRefinementAttempt attempt = attempt(geometryEvidence, baseline);
        if (attempt.localGrid().isEmpty()) {
            return new RefinementResult(baselineSample, attempt.diagnostics());
        }

        SourceSpaceValidationSample refinedSample = sampler.sampleAndValidate(
                sourceFrame,
                normalizedFrame,
                geometryEvidence,
                attempt.localGrid()
        );
        LocalRefinementEvidence finalEvidence = complete(
                attempt,
                selectedSamplingEvidence(refinedSample.evidence())
        );
        SourceSpaceValidationSample selectedSample = finalEvidence.appliedToSampling()
                ? refinedSample
                : baselineSample;
        return new RefinementResult(selectedSample, finalEvidence);
    }

    /**
     * Completes a local-refinement attempt after refined sampling has been measured.
     *
     * @param attempt local-refinement attempt
     * @param refinedSamplingEvidence refined sampling evidence, when a correction was sampled
     * @return final local-refinement evidence marked {@code APPLIED} only when sampling measurably improved
     */
    public LocalRefinementEvidence complete(
            LocalRefinementAttempt attempt,
            Optional<ModuleSamplingEvidence> refinedSamplingEvidence
    ) {
        Objects.requireNonNull(attempt, "attempt must not be null");
        Objects.requireNonNull(refinedSamplingEvidence, "refinedSamplingEvidence must not be null");
        if (attempt.localGrid().isEmpty() || refinedSamplingEvidence.isEmpty()) {
            return attempt.diagnostics();
        }

        LocalGridRefinement localGrid = attempt.localGrid().orElseThrow();
        ModuleSamplingEvidence baseline = attempt.baselineSamplingEvidence();
        ModuleSamplingEvidence refined = refinedSamplingEvidence.orElseThrow();
        boolean improved = measurableImprovement(baseline, refined);
        LocalRefinementStatus status = improved ? LocalRefinementStatus.APPLIED : LocalRefinementStatus.LEFT_GLOBAL;
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        reasonCodes.addAll(attempt.diagnostics().reasonCodes());
        reasonCodes.remove(CaptureMediaEvidenceReasonCode.NO_MEASURABLE_IMPROVEMENT);
        reasonCodes.remove(CaptureMediaEvidenceReasonCode.LOCAL_GEOMETRIC_DRIFT_SUSPECTED);
        reasonCodes.add(improved
                ? CaptureMediaEvidenceReasonCode.LOCAL_REFINEMENT_IMPROVED_SAMPLING
                : CaptureMediaEvidenceReasonCode.NO_MEASURABLE_IMPROVEMENT);

        return evidenceWith(
                attempt.diagnostics(),
                status,
                correctedControlPoints(localGrid),
                localGrid.maxDisplacementModules(),
                localGrid.maxDisplacementPixels(),
                localGrid.smoothnessScore(),
                localGrid.regularizationScore(),
                improvementMetrics(baseline, refined, localGrid),
                improved,
                reasonCodes
        );
    }

    /**
     * Produces disabled local-refinement evidence for internal rollback-switch tests.
     *
     * @param geometryEvidence global geometry evidence
     * @param samplingEvidence baseline source-space sampling evidence
     * @return local-refinement evidence marked not available because the internal switch is disabled
     */
    public LocalRefinementEvidence disabled(
            GeometryFitEvidence geometryEvidence,
            ModuleSamplingEvidence samplingEvidence
    ) {
        LocalRefinementEvidence diagnostics = diagnose(geometryEvidence, samplingEvidence);
        return evidenceWith(
                diagnostics,
                LocalRefinementStatus.NOT_AVAILABLE,
                List.of(),
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                diagnostics.improvementMetrics(),
                false,
                List.of(CaptureMediaEvidenceReasonCode.LOCAL_REFINEMENT_DISABLED)
        );
    }

    private LocalRefinementEvidence evidenceWith(
            LocalRefinementEvidence baseline,
            LocalRefinementStatus status,
            List<LocalControlPointEvidence> controlPoints,
            double maxLocalDisplacementModules,
            double maxLocalDisplacementPixels,
            double smoothnessScore,
            double regularizationScore,
            Map<String, Double> improvementMetrics,
            boolean appliedToSampling,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return new LocalRefinementEvidence(
                baseline.schemaVersion(),
                refinementCandidateId(baseline),
                status,
                baseline.baseGeometryCandidateId(),
                baseline.baseSamplingCandidateId(),
                baseline.baseReprojectionMetrics(),
                baseline.baseSamplingMetrics(),
                controlPoints,
                LocalRefinementModelType.LOCAL_GRID,
                baseline.gridColumns(),
                baseline.gridRows(),
                maxLocalDisplacementModules,
                maxLocalDisplacementPixels,
                "local-grid-2x2-piecewise-bilinear:v1:cap0.25:hard0.50",
                smoothnessScore,
                regularizationScore,
                improvementMetrics,
                appliedToSampling,
                deduplicated(reasonCodes)
        );
    }

    private CaptureMediaCandidateId refinementCandidateId(LocalRefinementEvidence evidence) {
        if (evidence.candidateId().refinementCandidateId().isPresent()) {
            return evidence.candidateId();
        }
        return CaptureMediaCandidateId.refinementCandidate(evidence.candidateId());
    }

    private List<LocalControlPointEvidence> correctedControlPoints(LocalGridRefinement localGrid) {
        List<LocalControlPointEvidence> corrected = new ArrayList<>(localGrid.controls().size());
        for (LocalControlPointEvidence control : localGrid.controls()) {
            SourcePoint correctedExpected = localGrid.apply(control.canonicalPoint(), control.expectedSourcePoint());
            double afterPixels = control.observedSourcePoint()
                    .map(observed -> distance(correctedExpected, observed))
                    .orElse(control.residualBeforePixels());
            double moduleSize = moduleSizePixels(control);
            corrected.add(new LocalControlPointEvidence(
                    control.controlPointId(),
                    control.canonicalPoint(),
                    control.expectedSourcePoint(),
                    control.observedSourcePoint(),
                    control.residualBeforePixels(),
                    afterPixels,
                    control.residualBeforeModules(),
                    moduleSize <= 0.0d ? control.residualBeforeModules() : afterPixels / moduleSize,
                    control.inlier(),
                    control.distributed(),
                    control.confidence(),
                    control.reasonCodes()
            ));
        }
        return List.copyOf(corrected);
    }

    private Map<String, Double> improvementMetrics(
            ModuleSamplingEvidence baseline,
            ModuleSamplingEvidence refined,
            LocalGridRefinement localGrid
    ) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("samplingConfidenceBefore", confidenceMean(baseline));
        metrics.put("samplingConfidenceAfter", confidenceMean(refined));
        metrics.put("readableModulesBefore", (double) baseline.readableModuleCount());
        metrics.put("readableModulesAfter", (double) refined.readableModuleCount());
        metrics.put("decodedTilesBefore", (double) baseline.acceptedPayloadCount());
        metrics.put("decodedTilesAfter", (double) refined.acceptedPayloadCount());
        metrics.put("tileDecodeAttemptsBefore", (double) baseline.tileDecodeAttemptCount());
        metrics.put("tileDecodeAttemptsAfter", (double) refined.tileDecodeAttemptCount());
        metrics.put("problemModulesBefore", (double) problemModuleCount(baseline));
        metrics.put("problemModulesAfter", (double) problemModuleCount(refined));
        metrics.put("residualMeanBeforeModules", residualMean(localGrid.controls(), false));
        metrics.put("residualMeanAfterModules", residualMean(correctedControlPoints(localGrid), true));
        metrics.put("localGeometricDriftLikely", localGrid.maxDisplacementModules() > 0.0d ? 1.0d : 0.0d);
        metrics.put("colorPrimaryBlockerLikely", 0.0d);
        metrics.put("ambiguousResidualPattern", 0.0d);
        metrics.put("overfitRisk", 0.0d);
        return Map.copyOf(metrics);
    }

    private boolean measurableImprovement(ModuleSamplingEvidence baseline, ModuleSamplingEvidence refined) {
        return refined.acceptedPayloadCount() > baseline.acceptedPayloadCount()
                || refined.tileDecodeAttemptCount() > baseline.tileDecodeAttemptCount()
                || refined.readableModuleCount() > baseline.readableModuleCount()
                || problemModuleCount(refined) < problemModuleCount(baseline)
                || confidenceMean(refined) >= confidenceMean(baseline) + CONFIDENCE_IMPROVEMENT_THRESHOLD;
    }

    private Optional<ModuleSamplingEvidence> selectedSamplingEvidence(List<ModuleSamplingEvidence> evidence) {
        return evidence.stream()
                .filter(candidate -> candidate.status() == ModuleSamplingStatus.SAMPLED)
                .findFirst()
                .or(() -> evidence.stream().findFirst());
    }

    private int problemModuleCount(ModuleSamplingEvidence evidence) {
        return evidence.ambiguousModuleCount()
                + evidence.unreadableModuleCount()
                + evidence.clippedModuleCount()
                + evidence.outOfBoundsModuleCount();
    }

    private double confidenceMean(ModuleSamplingEvidence evidence) {
        return evidence.confidenceMarginSummary().getOrDefault("mean", 0.0d);
    }

    private double residualMean(List<LocalControlPointEvidence> controls, boolean after) {
        if (controls.isEmpty()) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (LocalControlPointEvidence control : controls) {
            sum += after ? control.residualAfterModules() : control.residualBeforeModules();
        }
        return sum / controls.size();
    }

    private double moduleSizePixels(LocalControlPointEvidence control) {
        if (control.residualBeforeModules() <= 0.000001d) {
            return 1.0d;
        }
        return control.residualBeforePixels() / control.residualBeforeModules();
    }

    private double distance(SourcePoint first, SourcePoint second) {
        double deltaX = first.x() - second.x();
        double deltaY = first.y() - second.y();
        return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
    }

    private List<CaptureMediaEvidenceReasonCode> deduplicated(
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return List.copyOf(new LinkedHashSet<>(reasonCodes));
    }

    /**
     * One local-refinement attempt tied to a baseline source-space sampling candidate.
     *
     * @param diagnostics diagnostic local-refinement evidence before applied sampling is measured
     * @param baselineSamplingEvidence baseline source-space sampling evidence
     * @param localGrid bounded local-grid correction, when safe enough to sample
     */
    public record LocalRefinementAttempt(
            LocalRefinementEvidence diagnostics,
            ModuleSamplingEvidence baselineSamplingEvidence,
            Optional<LocalGridRefinement> localGrid
    ) {

        /**
         * Creates an immutable local-refinement attempt.
         *
         * @param diagnostics diagnostic evidence
         * @param baselineSamplingEvidence baseline sampling evidence
         * @param localGrid optional local-grid correction
         */
        public LocalRefinementAttempt {
            Objects.requireNonNull(diagnostics, "diagnostics must not be null");
            Objects.requireNonNull(baselineSamplingEvidence, "baselineSamplingEvidence must not be null");
            localGrid = Objects.requireNonNull(localGrid, "localGrid must not be null");
        }
    }

    /**
     * Selected source-space validation sample with the local-refinement evidence that explains that selection.
     *
     * @param validationSample baseline or refined source-space validation sample
     * @param evidence final local-refinement evidence
     */
    public record RefinementResult(
            SourceSpaceValidationSample validationSample,
            LocalRefinementEvidence evidence
    ) {

        /**
         * Creates an immutable refinement result.
         *
         * @param validationSample selected validation sample
         * @param evidence final local-refinement evidence
         */
        public RefinementResult {
            Objects.requireNonNull(validationSample, "validationSample must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
        }
    }
}
