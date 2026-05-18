package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic bounded module phase search for capture-media sampling variants.
 *
 * <p>The search generates nominal, high-confidence evidence, bounded center-shift, and bounded module-scale variants.
 * Ranking is based only on reader-owned attempt evidence supplied by the caller; CV evidence can add candidates but does
 * not receive ranking preference after inclusion.</p>
 */
public final class CaptureMediaModulePhaseSearch {

    private static final double ROUNDING_FACTOR = 1_000_000.0d;

    /**
     * Evaluates one generated phase candidate and returns reader-owned evidence for ranking.
     */
    @FunctionalInterface
    public interface AttemptEvaluator {

        /**
         * Evaluates one generated phase candidate.
         *
         * @param candidate generated phase candidate
         * @return attempt evidence for the candidate
         */
        CaptureMediaModulePhaseAttempt evaluate(CaptureMediaModulePhaseCandidate candidate);
    }

    /**
     * Generates candidates, evaluates them, and returns attempts ranked by reader-owned evidence.
     *
     * @param request phase-search input geometry and limits
     * @param evaluator evaluator for generated candidates
     * @return ranked phase-search result
     */
    public CaptureMediaModulePhaseSearchResult search(
            CaptureMediaModulePhaseSearchRequest request,
            AttemptEvaluator evaluator
    ) {
        Objects.requireNonNull(evaluator, "evaluator must not be null");
        CandidatePlan plan = candidatePlan(request);
        return search(plan.candidates(), request.bounds().maxVariantCount(), plan.capReached(), evaluator);
    }

    /**
     * Evaluates an explicit deterministic candidate list and ranks attempts by reader-owned evidence.
     *
     * <p>This overload lets sampler integration preserve an existing bounded fallback generation order as the final
     * tie-breaker while moving selection to the common phase ranker.</p>
     *
     * @param candidates generated phase candidates in deterministic tie-break order
     * @param variantCap maximum phase variants allowed by the caller
     * @param capReached true when candidate generation was truncated before all variants were included
     * @param evaluator evaluator for generated candidates
     * @return ranked phase-search result
     */
    public CaptureMediaModulePhaseSearchResult search(
            List<CaptureMediaModulePhaseCandidate> candidates,
            int variantCap,
            boolean capReached,
            AttemptEvaluator evaluator
    ) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(evaluator, "evaluator must not be null");
        List<CaptureMediaModulePhaseCandidate> candidateList = List.copyOf(candidates);
        if (candidateList.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        if (candidateList.size() > variantCap) {
            throw new IllegalArgumentException("candidates must not exceed variantCap");
        }
        List<CaptureMediaModulePhaseAttempt> attempts = new ArrayList<>(candidateList.size());
        for (CaptureMediaModulePhaseCandidate candidate : candidateList) {
            CaptureMediaModulePhaseAttempt attempt = Objects.requireNonNull(
                    evaluator.evaluate(candidate),
                    "evaluator must not return null"
            );
            if (!attempt.candidate().equals(candidate)) {
                throw new IllegalArgumentException("evaluator must return an attempt for the supplied candidate");
            }
            attempts.add(attempt);
        }
        attempts.sort(CaptureMediaModulePhaseSearch::compareAttempts);
        return new CaptureMediaModulePhaseSearchResult(
                attempts,
                variantCap,
                capReached
        );
    }

    /**
     * Generates the capped deterministic candidate list without evaluating attempts.
     *
     * @param request phase-search input geometry and limits
     * @return capped phase candidates in evaluation order
     */
    public List<CaptureMediaModulePhaseCandidate> candidates(CaptureMediaModulePhaseSearchRequest request) {
        return candidatePlan(request).candidates();
    }

    private CandidatePlan candidatePlan(CaptureMediaModulePhaseSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<CaptureMediaModulePhaseCandidate> candidates = new ArrayList<>();
        Set<GeometryKey> seen = new HashSet<>();
        List<BaseOffset> bases = baseOffsets(request);

        for (BaseOffset base : bases) {
            CaptureMediaModulePhaseCandidateSource source = base.evidence().isPresent()
                    ? CaptureMediaModulePhaseCandidateSource.CV_EVIDENCE
                    : CaptureMediaModulePhaseCandidateSource.NOMINAL;
            boolean continued = addCandidate(
                    request,
                    candidates,
                    seen,
                    source,
                    base.evidence(),
                    base.offsetXPx(),
                    base.offsetYPx(),
                    1.0d
            );
            if (!continued) {
                return new CandidatePlan(candidates, true);
            }
        }

        CaptureMediaModulePhaseBounds bounds = request.bounds();
        List<Double> xShifts = boundedOffsets(bounds.maxCenterShiftXPx(), bounds.centerShiftStepPx());
        List<Double> yShifts = boundedOffsets(bounds.maxCenterShiftYPx(), bounds.centerShiftStepPx());
        List<Double> scaleDeltas = boundedOffsets(bounds.maxModuleScaleDelta(), bounds.moduleScaleStep());
        for (BaseOffset base : bases) {
            for (double scaleDelta : scaleDeltas) {
                double scale = rounded(1.0d + scaleDelta);
                for (double yShift : yShifts) {
                    for (double xShift : xShifts) {
                        if (scale == 1.0d && xShift == 0.0d && yShift == 0.0d) {
                            continue;
                        }
                        boolean continued = addCandidate(
                                request,
                                candidates,
                                seen,
                                CaptureMediaModulePhaseCandidateSource.BOUNDED_SEARCH,
                                base.evidence(),
                                rounded(base.offsetXPx() + xShift),
                                rounded(base.offsetYPx() + yShift),
                                scale
                        );
                        if (!continued) {
                            return new CandidatePlan(candidates, true);
                        }
                    }
                }
            }
        }
        return new CandidatePlan(candidates, false);
    }

    private List<BaseOffset> baseOffsets(CaptureMediaModulePhaseSearchRequest request) {
        List<BaseOffset> bases = new ArrayList<>();
        bases.add(new BaseOffset(0.0d, 0.0d, Optional.empty()));
        request.evidence()
                .filter(evidence -> evidence.highConfidence(request.bounds().minimumEvidenceConfidence()))
                .ifPresent(evidence -> bases.add(new BaseOffset(
                        rounded(evidence.offsetXPx()),
                        rounded(evidence.offsetYPx()),
                        Optional.of(evidence)
                )));
        return bases;
    }

    private boolean addCandidate(
            CaptureMediaModulePhaseSearchRequest request,
            List<CaptureMediaModulePhaseCandidate> candidates,
            Set<GeometryKey> seen,
            CaptureMediaModulePhaseCandidateSource source,
            Optional<CaptureMediaModulePhaseEvidence> evidence,
            double centerOffsetXPx,
            double centerOffsetYPx,
            double moduleSizeScale
    ) {
        GeometryKey key = GeometryKey.of(centerOffsetXPx, centerOffsetYPx, moduleSizeScale);
        if (!seen.add(key)) {
            return true;
        }
        if (candidates.size() >= request.bounds().maxVariantCount()) {
            return false;
        }
        double centerXPx = rounded(request.nominalCenterXPx() + centerOffsetXPx);
        double centerYPx = rounded(request.nominalCenterYPx() + centerOffsetYPx);
        double moduleSizePx = rounded(request.nominalModuleSizePx() * moduleSizeScale);
        CaptureMediaModulePhaseGeometry geometry = new CaptureMediaModulePhaseGeometry(
                centerXPx,
                centerYPx,
                moduleSizePx,
                centerOffsetXPx,
                centerOffsetYPx,
                moduleSizeScale
        );
        candidates.add(new CaptureMediaModulePhaseCandidate(
                candidates.size(),
                request.slotIndex(),
                request.sideVersion(),
                source,
                geometry,
                evidence
        ));
        return true;
    }

    private static List<Double> boundedOffsets(double maxOffset, double step) {
        List<Double> offsets = new ArrayList<>();
        offsets.add(0.0d);
        int stepCount = (int) Math.floor((maxOffset / step) + 0.000_001d);
        for (int stepIndex = 1; stepIndex <= stepCount; stepIndex++) {
            double offset = rounded(step * stepIndex);
            offsets.add(-offset);
            offsets.add(offset);
        }
        return offsets;
    }

    private static int compareAttempts(
            CaptureMediaModulePhaseAttempt left,
            CaptureMediaModulePhaseAttempt right
    ) {
        if (left.acceptedPayload() && right.acceptedPayload()) {
            return Integer.compare(left.candidate().ordinal(), right.candidate().ordinal());
        }
        int accepted = Boolean.compare(right.acceptedPayload(), left.acceptedPayload());
        if (accepted != 0) {
            return accepted;
        }
        int progress = Integer.compare(right.protocolProgressRank(), left.protocolProgressRank());
        if (progress != 0) {
            return progress;
        }
        int detail = Boolean.compare(right.specificFailureDetail(), left.specificFailureDetail());
        if (detail != 0) {
            return detail;
        }
        int finderCount = Integer.compare(right.finderCandidateCount(), left.finderCandidateCount());
        if (finderCount != 0) {
            return finderCount;
        }
        int finderExact = Boolean.compare(right.finderExact(), left.finderExact());
        if (finderExact != 0) {
            return finderExact;
        }
        int border = Double.compare(right.borderStrength(), left.borderStrength());
        if (border != 0) {
            return border;
        }
        int palette = Double.compare(right.paletteCalibrationConfidence(), left.paletteCalibrationConfidence());
        if (palette != 0) {
            return palette;
        }
        int rejectedSamples = Integer.compare(left.rejectedSampleCount(), right.rejectedSampleCount());
        if (rejectedSamples != 0) {
            return rejectedSamples;
        }
        return Integer.compare(left.candidate().ordinal(), right.candidate().ordinal());
    }

    private static double rounded(double value) {
        return Math.rint(value * ROUNDING_FACTOR) / ROUNDING_FACTOR;
    }

    private record BaseOffset(
            double offsetXPx,
            double offsetYPx,
            Optional<CaptureMediaModulePhaseEvidence> evidence
    ) {
    }

    private record CandidatePlan(List<CaptureMediaModulePhaseCandidate> candidates, boolean capReached) {

        private CandidatePlan {
            candidates = List.copyOf(candidates);
        }
    }

    private record GeometryKey(long offsetXMicros, long offsetYMicros, long scaleMicros) {

        private static GeometryKey of(double offsetXPx, double offsetYPx, double moduleSizeScale) {
            return new GeometryKey(
                    Math.round(offsetXPx * ROUNDING_FACTOR),
                    Math.round(offsetYPx * ROUNDING_FACTOR),
                    Math.round(moduleSizeScale * ROUNDING_FACTOR)
            );
        }
    }
}
