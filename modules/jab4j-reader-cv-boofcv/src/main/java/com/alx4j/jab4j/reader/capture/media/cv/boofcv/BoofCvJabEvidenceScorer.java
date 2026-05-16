package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.cv.CvCandidateScore;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;

/**
 * Scores BoofCV-proposed regions against supported JAB layout and rendered-frame evidence.
 */
final class BoofCvJabEvidenceScorer {

    static final double MIN_FRAME_COVERAGE_RATIO = 0.20d;

    private static final double MIN_ASPECT_SCORE = 0.70d;
    private static final double MIN_SIZE_SCORE = 0.45d;
    private static final double MIN_TOTAL_SCORE = 0.395d;
    private static final double MIN_BORDER_SCORE = 0.20d;
    private static final double MIN_SYNC_SCORE = 0.395d;
    private static final double MIN_GRID_SCORE = 0.20d;
    private static final double MIN_PLAUSIBLE_ASPECT_SCORE = 0.80d;
    private static final double MIN_PLAUSIBLE_TOTAL_SCORE = 0.325d;
    private static final double MIN_PLAUSIBLE_BORDER_SCORE = 0.12d;
    private static final double MIN_PLAUSIBLE_SYNC_SCORE = 0.20d;
    private static final double MIN_PLAUSIBLE_GRID_SCORE = 0.115d;
    private static final double MIN_PLAUSIBLE_STRONG_EVIDENCE_SCORE = 0.28d;
    private static final double MAX_PLAUSIBLE_SKEW_SCORE = 0.65d;
    private static final int MIN_PLAUSIBLE_SOURCE_SHORT_EDGE_PX = 120;
    private static final int MIN_PLAUSIBLE_EVIDENCE_FAMILIES = 3;
    private static final double SAME_REGION_IOU = 0.80d;
    private static final int MAX_SYNC_SAMPLES = 48;
    private static final int LIGHT_CONFIDENCE_FLOOR = 160;
    private static final int DARK_CONFIDENCE_CEILING = 125;
    private static final int REQUIRED_CONTRAST_DELTA = 44;

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;

    /**
     * Creates a JAB evidence scorer from reader-owned layout collaborators.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for JAB geometry
     */
    BoofCvJabEvidenceScorer(CaptureRenderedLayoutCatalog layoutCatalog, FixedLayoutPlanner layoutPlanner) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
    }

    /**
     * Scores proposed regions and ranks them by total JAB evidence.
     *
     * @param frame decoded media input frame
     * @param regions proposed source-space regions
     * @return score-ranked source-space frame candidates
     */
    List<CvFrameCandidate> scoreRegions(
            MediaInputFrame frame,
            List<BoofCvCandidateRegionProposer.CandidateRegion> regions
    ) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(regions, "regions must not be null");
        List<CvFrameCandidate> scoredCandidates = new ArrayList<>();
        for (BoofCvCandidateRegionProposer.CandidateRegion region : regions) {
            Objects.requireNonNull(region, "regions must not contain null values");
            bestProfileMatch(region).ifPresent(match -> scoredCandidates.add(scoreRegion(
                    frame,
                    region,
                    match.profile(),
                    match.aspectScore()
            )));
        }
        scoredCandidates.sort(Comparator.comparingDouble((CvFrameCandidate candidate) ->
                candidate.score().totalScore()).reversed());
        return List.copyOf(scoredCandidates);
    }

    /**
     * Filters candidates to distinct regions that pass conservative JAB evidence thresholds.
     *
     * @param candidates score-ranked source-space candidates
     * @return distinct candidates with enough JAB evidence
     */
    List<CvFrameCandidate> distinctEvidenceCandidates(List<CvFrameCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        return distinctRegions(candidates.stream()
                .filter(this::isStrictEvidence)
                .toList());
    }

    /**
     * Filters candidates to distinct near-miss regions worth validating downstream.
     *
     * @param candidates score-ranked source-space candidates
     * @param strictCandidates already selected strict regions used for IoU suppression
     * @param limit maximum plausible candidates to return
     * @return distinct plausible validation candidates
     */
    List<CvFrameCandidate> distinctPlausibleValidationCandidates(
            List<CvFrameCandidate> candidates,
            List<CvFrameCandidate> strictCandidates,
            int limit
    ) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(strictCandidates, "strictCandidates must not be null");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        List<CvFrameCandidate> distinct = new ArrayList<>();
        for (CvFrameCandidate candidate : candidates) {
            if (distinct.size() >= limit) {
                break;
            }
            if (!isPlausibleValidation(candidate)) {
                continue;
            }
            if (overlapsAny(candidate, strictCandidates) || overlapsAny(candidate, distinct)) {
                continue;
            }
            distinct.add(candidate);
        }
        return List.copyOf(distinct);
    }

    /**
     * Classifies a scored source-space candidate into the BoofCV admission taxonomy.
     *
     * @param candidate scored source-space candidate
     * @return admission band used for diagnostics and candidate selection
     */
    AdmissionBand admissionBand(CvFrameCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (isStrictEvidence(candidate)) {
            return AdmissionBand.STRICT_EVIDENCE;
        }
        if (isPlausibleValidation(candidate)) {
            return AdmissionBand.PLAUSIBLE_VALIDATION;
        }
        return AdmissionBand.REJECTED;
    }

    /**
     * Returns the primary deterministic rejection reason for one scored candidate.
     *
     * @param candidate scored source-space candidate
     * @return rejection reason, or {@link RejectionReason#NONE} when the candidate is admitted
     */
    RejectionReason rejectionReason(CvFrameCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        CvCandidateScore score = candidate.score();
        if (score.frameCoverageRatio() < MIN_FRAME_COVERAGE_RATIO
                || Math.min(candidate.widthPx(), candidate.heightPx()) < MIN_PLAUSIBLE_SOURCE_SHORT_EDGE_PX) {
            return RejectionReason.TOO_SMALL;
        }
        if (score.layoutAspectScore() < MIN_PLAUSIBLE_ASPECT_SCORE) {
            return RejectionReason.BAD_ASPECT;
        }
        if (score.skewScore() > MAX_PLAUSIBLE_SKEW_SCORE) {
            return RejectionReason.BAD_PERSPECTIVE;
        }
        if (admissionBand(candidate) != AdmissionBand.REJECTED) {
            return RejectionReason.NONE;
        }
        return weakestEvidenceReason(score);
    }

    private Optional<ProfileMatch> bestProfileMatch(BoofCvCandidateRegionProposer.CandidateRegion region) {
        ProfileMatch best = null;
        for (LayoutProfile profile : layoutCatalog.profiles()) {
            double aspectScore = aspectScore(region, profile);
            double sizeScore = sizeScore(region, profile);
            if (aspectScore < MIN_ASPECT_SCORE || sizeScore < MIN_SIZE_SCORE) {
                continue;
            }
            ProfileMatch candidate = new ProfileMatch(profile, aspectScore, aspectScore * sizeScore);
            if (best == null || candidate.combinedScore() > best.combinedScore()) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private CvFrameCandidate scoreRegion(
            MediaInputFrame frame,
            BoofCvCandidateRegionProposer.CandidateRegion region,
            LayoutProfile profile,
            double aspectScore
    ) {
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
        double borderScore = scoreBorderAndQuietZone(frame, region, profile, layoutPlan);
        SyncScore syncScore = scoreSyncBand(frame, region, profile, layoutPlan);
        double gridScore = scoreTileGrid(frame, region, profile, layoutPlan);
        double coverageRatio = region.areaPx() / ((double) frame.widthPixels() * frame.heightPixels());
        double totalScore = clampScore(
                (0.30d * borderScore)
                        + (0.30d * syncScore.score())
                        + (0.25d * gridScore)
                        + (0.15d * aspectScore)
        );
        return new CvFrameCandidate(
                profile,
                region.corners(),
                region.leftPx(),
                region.topPx(),
                region.rightExclusivePx(),
                region.bottomExclusivePx(),
                new CvCandidateScore(
                        totalScore,
                        clampScore(coverageRatio),
                        region.skewScore(),
                        borderScore,
                        syncScore.score(),
                        gridScore,
                        aspectScore,
                        CvCandidateScore.NOT_MEASURED,
                        CvCandidateScore.NOT_MEASURED,
                        CvCandidateScore.NOT_MEASURED
                )
        );
    }

    private double scoreBorderAndQuietZone(
            MediaInputFrame frame,
            BoofCvCandidateRegionProposer.CandidateRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        int border = layoutPlan.separatorThicknessPx();
        double borderCenter = Math.max(0.5d, border / 2.0d);
        double innerOffset = Math.min(profile.outerMarginPx() - 1.0d, border + Math.max(4.0d, border / 2.0d));
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        int samplesPerSide = 14;
        for (int index = 0; index < samplesPerSide; index++) {
            double ratio = (index + 0.5d) / samplesPerSide;
            double horizontal = profile.outerMarginPx()
                    + (ratio * (profile.frameWidthPx() - (2.0d * profile.outerMarginPx())));
            double vertical = profile.outerMarginPx()
                    + (ratio * (profile.frameHeightPx() - (2.0d * profile.outerMarginPx())));
            evidence.addContrastingPair(
                    sample(frame, region, profile, horizontal, borderCenter),
                    sample(frame, region, profile, horizontal, innerOffset)
            );
            evidence.addContrastingPair(
                    sample(frame, region, profile, horizontal, profile.frameHeightPx() - 1.0d - borderCenter),
                    sample(frame, region, profile, horizontal, profile.frameHeightPx() - 1.0d - innerOffset)
            );
            evidence.addContrastingPair(
                    sample(frame, region, profile, borderCenter, vertical),
                    sample(frame, region, profile, innerOffset, vertical)
            );
            evidence.addContrastingPair(
                    sample(frame, region, profile, profile.frameWidthPx() - 1.0d - borderCenter, vertical),
                    sample(frame, region, profile, profile.frameWidthPx() - 1.0d - innerOffset, vertical)
            );
        }
        return evidence.score();
    }

    private SyncScore scoreSyncBand(
            MediaInputFrame frame,
            BoofCvCandidateRegionProposer.CandidateRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        int cellWidth = Math.max(8, layoutPlan.separatorThicknessPx() * 2);
        int cellCount = Math.max(1, (profile.frameWidthPx() - (2 * profile.outerMarginPx())) / cellWidth);
        int step = Math.max(1, cellCount / MAX_SYNC_SAMPLES);
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        int minimumLuminance = 255;
        int maximumLuminance = 0;
        int samples = 0;
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex += step) {
            double x = clampDouble(
                    profile.outerMarginPx() + (cellIndex * cellWidth) + (cellWidth / 2.0d),
                    profile.outerMarginPx(),
                    profile.frameWidthPx() - profile.outerMarginPx() - 1.0d
            );
            double y = profile.outerMarginPx() + (profile.topSyncBandPx() / 2.0d);
            int luminance = sample(frame, region, profile, x, y);
            if (cellIndex % 2 == 0) {
                evidence.addLight(luminance);
            } else {
                evidence.addDark(luminance);
            }
            minimumLuminance = Math.min(minimumLuminance, luminance);
            maximumLuminance = Math.max(maximumLuminance, luminance);
            samples++;
        }
        double contrastScore = samples == 0 ? 0.0d : contrastConfidence(maximumLuminance, minimumLuminance);
        return new SyncScore(clampScore((0.82d * evidence.score()) + (0.18d * contrastScore)), contrastScore);
    }

    private double scoreTileGrid(
            MediaInputFrame frame,
            BoofCvCandidateRegionProposer.CandidateRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        for (int col = 0; col < profile.cols() - 1; col++) {
            TilePlacement left = layoutPlan.tilePlacements().get(col);
            TilePlacement right = layoutPlan.tilePlacements().get(col + 1);
            double x = left.xPx() + left.widthPx() + ((right.xPx() - left.xPx() - left.widthPx()) / 2.0d);
            addVerticalGridSamples(frame, region, profile, layoutPlan, evidence, x);
        }
        for (TilePlacement placement : layoutPlan.tilePlacements()) {
            addTileBorderSamples(frame, region, profile, evidence, placement, layoutPlan.separatorThicknessPx());
        }
        return evidence.score();
    }

    private void addVerticalGridSamples(
            MediaInputFrame frame,
            BoofCvCandidateRegionProposer.CandidateRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            EvidenceAccumulator evidence,
            double x
    ) {
        int gridBottomExclusive = layoutPlan.gridOriginYPx()
                + (profile.rows() * layoutPlan.tileSlotHeightPx())
                + ((profile.rows() - 1) * profile.tileGapPx());
        int sampleCount = Math.max(4, profile.rows() * 4);
        for (int index = 0; index < sampleCount; index++) {
            double ratio = (index + 0.5d) / sampleCount;
            double y = layoutPlan.gridOriginYPx() + (ratio * (gridBottomExclusive - layoutPlan.gridOriginYPx()));
            evidence.addLight(sample(frame, region, profile, x, y));
        }
    }

    private void addTileBorderSamples(
            MediaInputFrame frame,
            BoofCvCandidateRegionProposer.CandidateRegion region,
            LayoutProfile profile,
            EvidenceAccumulator evidence,
            TilePlacement placement,
            int border
    ) {
        double centerY = placement.yPx() + (placement.heightPx() / 2.0d);
        double centerX = placement.xPx() + (placement.widthPx() / 2.0d);
        evidence.addLight(sample(frame, region, profile, centerX, placement.yPx() + (border / 2.0d)));
        evidence.addLight(sample(frame, region, profile, centerX,
                placement.yPx() + placement.heightPx() - 1.0d - (border / 2.0d)));
        evidence.addLight(sample(frame, region, profile, placement.xPx() + (border / 2.0d), centerY));
        evidence.addLight(sample(frame, region, profile,
                placement.xPx() + placement.widthPx() - 1.0d - (border / 2.0d), centerY));
    }

    private boolean isStrictEvidence(CvFrameCandidate candidate) {
        CvCandidateScore score = candidate.score();
        return score.totalScore() >= MIN_TOTAL_SCORE
                && score.borderContrastScore() >= MIN_BORDER_SCORE
                && score.syncBandScore() >= MIN_SYNC_SCORE
                && score.gridScore() >= MIN_GRID_SCORE
                && score.layoutAspectScore() >= MIN_ASPECT_SCORE;
    }

    private boolean isPlausibleValidation(CvFrameCandidate candidate) {
        if (isStrictEvidence(candidate)) {
            return false;
        }
        CvCandidateScore score = candidate.score();
        return score.totalScore() >= MIN_PLAUSIBLE_TOTAL_SCORE
                && score.frameCoverageRatio() >= MIN_FRAME_COVERAGE_RATIO
                && Math.min(candidate.widthPx(), candidate.heightPx()) >= MIN_PLAUSIBLE_SOURCE_SHORT_EDGE_PX
                && score.layoutAspectScore() >= MIN_PLAUSIBLE_ASPECT_SCORE
                && score.skewScore() <= MAX_PLAUSIBLE_SKEW_SCORE
                && plausibleEvidenceFamilyCount(score) >= MIN_PLAUSIBLE_EVIDENCE_FAMILIES
                && strongestEvidenceFamilyScore(score) >= MIN_PLAUSIBLE_STRONG_EVIDENCE_SCORE;
    }

    private int plausibleEvidenceFamilyCount(CvCandidateScore score) {
        int evidenceFamilies = 0;
        if (score.borderContrastScore() >= MIN_PLAUSIBLE_BORDER_SCORE) {
            evidenceFamilies++;
        }
        if (score.syncBandScore() >= MIN_PLAUSIBLE_SYNC_SCORE) {
            evidenceFamilies++;
        }
        if (score.gridScore() >= MIN_PLAUSIBLE_GRID_SCORE) {
            evidenceFamilies++;
        }
        return evidenceFamilies;
    }

    private double strongestEvidenceFamilyScore(CvCandidateScore score) {
        return Math.max(score.borderContrastScore(), Math.max(score.syncBandScore(), score.gridScore()));
    }

    private RejectionReason weakestEvidenceReason(CvCandidateScore score) {
        boolean weakBorder = score.borderContrastScore() < MIN_PLAUSIBLE_BORDER_SCORE;
        boolean weakSync = score.syncBandScore() < MIN_PLAUSIBLE_SYNC_SCORE;
        boolean weakGrid = score.gridScore() < MIN_PLAUSIBLE_GRID_SCORE;
        if (weakBorder && score.borderContrastScore() <= score.syncBandScore()
                && score.borderContrastScore() <= score.gridScore()) {
            return RejectionReason.WEAK_BORDER;
        }
        if (weakSync && score.syncBandScore() <= score.borderContrastScore()
                && score.syncBandScore() <= score.gridScore()) {
            return RejectionReason.WEAK_SYNC;
        }
        if (weakGrid) {
            return RejectionReason.WEAK_GRID;
        }
        if (score.syncBandScore() < MIN_SYNC_SCORE) {
            return RejectionReason.WEAK_SYNC;
        }
        if (score.gridScore() < MIN_GRID_SCORE) {
            return RejectionReason.WEAK_GRID;
        }
        return RejectionReason.WEAK_BORDER;
    }

    private List<CvFrameCandidate> distinctRegions(List<CvFrameCandidate> candidates) {
        List<CvFrameCandidate> distinct = new ArrayList<>();
        for (CvFrameCandidate candidate : candidates) {
            if (!overlapsAny(candidate, distinct)) {
                distinct.add(candidate);
            }
        }
        return List.copyOf(distinct);
    }

    private boolean overlapsAny(CvFrameCandidate candidate, List<CvFrameCandidate> existingCandidates) {
        return existingCandidates.stream()
                .anyMatch(existing -> intersectionOverUnion(existing, candidate) >= SAME_REGION_IOU);
    }

    private double intersectionOverUnion(CvFrameCandidate first, CvFrameCandidate second) {
        int left = Math.max(first.sourceLeftPx(), second.sourceLeftPx());
        int top = Math.max(first.sourceTopPx(), second.sourceTopPx());
        int right = Math.min(first.sourceRightExclusivePx(), second.sourceRightExclusivePx());
        int bottom = Math.min(first.sourceBottomExclusivePx(), second.sourceBottomExclusivePx());
        int intersectionWidth = Math.max(0, right - left);
        int intersectionHeight = Math.max(0, bottom - top);
        double intersection = (double) intersectionWidth * intersectionHeight;
        double firstArea = (double) first.widthPx() * first.heightPx();
        double secondArea = (double) second.widthPx() * second.heightPx();
        double union = firstArea + secondArea - intersection;
        return union <= 0.0d ? 0.0d : intersection / union;
    }

    private int sample(
            MediaInputFrame frame,
            BoofCvCandidateRegionProposer.CandidateRegion region,
            LayoutProfile profile,
            double profileX,
            double profileY
    ) {
        double xRatio = profile.frameWidthPx() <= 1 ? 0.0d : profileX / (profile.frameWidthPx() - 1.0d);
        double yRatio = profile.frameHeightPx() <= 1 ? 0.0d : profileY / (profile.frameHeightPx() - 1.0d);
        FrameCorners corners = region.corners();
        double topX = interpolate(corners.topLeftX(), corners.topRightX(), xRatio);
        double topY = interpolate(corners.topLeftY(), corners.topRightY(), xRatio);
        double bottomX = interpolate(corners.bottomLeftX(), corners.bottomRightX(), xRatio);
        double bottomY = interpolate(corners.bottomLeftY(), corners.bottomRightY(), xRatio);
        int sourceX = clamp(
                (int) Math.round(interpolate(topX, bottomX, yRatio)),
                0,
                frame.widthPixels() - 1
        );
        int sourceY = clamp(
                (int) Math.round(interpolate(topY, bottomY, yRatio)),
                0,
                frame.heightPixels() - 1
        );
        return luminance(frame.argbPixelAt(sourceY, sourceX));
    }

    private double interpolate(double start, double end, double ratio) {
        return start + ((end - start) * ratio);
    }

    private double aspectScore(BoofCvCandidateRegionProposer.CandidateRegion region, LayoutProfile profile) {
        double candidateAspect = (double) region.widthPx() / region.heightPx();
        double profileAspect = (double) profile.frameWidthPx() / profile.frameHeightPx();
        double normalizedError = Math.abs(candidateAspect - profileAspect) / profileAspect;
        return clampScore(1.0d - (normalizedError / (1.0d - MIN_ASPECT_SCORE)));
    }

    private double sizeScore(BoofCvCandidateRegionProposer.CandidateRegion region, LayoutProfile profile) {
        double widthRatio = Math.min(
                (double) region.widthPx() / profile.frameWidthPx(),
                (double) profile.frameWidthPx() / region.widthPx()
        );
        double heightRatio = Math.min(
                (double) region.heightPx() / profile.frameHeightPx(),
                (double) profile.frameHeightPx() / region.heightPx()
        );
        return clampScore((widthRatio + heightRatio) / 2.0d);
    }

    private int luminance(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (int) Math.round((0.2126d * red) + (0.7152d * green) + (0.0722d * blue));
    }

    private double lightConfidence(int luminance) {
        return clampScore((luminance - LIGHT_CONFIDENCE_FLOOR) / (255.0d - LIGHT_CONFIDENCE_FLOOR));
    }

    private double darkConfidence(int luminance) {
        return clampScore((DARK_CONFIDENCE_CEILING - luminance) / (double) DARK_CONFIDENCE_CEILING);
    }

    private double contrastConfidence(int lightLuminance, int darkLuminance) {
        return clampScore((lightLuminance - darkLuminance - REQUIRED_CONTRAST_DELTA) / 128.0d);
    }

    private double clampScore(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ProfileMatch(LayoutProfile profile, double aspectScore, double combinedScore) {
    }

    private record SyncScore(double score, double contrastScore) {
    }

    /**
     * Numeric BoofCV admission bands exposed through detection metrics.
     */
    enum AdmissionBand {
        REJECTED(0.0d),
        STRICT_EVIDENCE(1.0d),
        PLAUSIBLE_VALIDATION(2.0d);

        private final double code;

        AdmissionBand(double code) {
            this.code = code;
        }

        double code() {
            return code;
        }
    }

    /**
     * Numeric BoofCV candidate rejection reasons exposed through detection metrics.
     */
    enum RejectionReason {
        NONE(0.0d),
        TOO_SMALL(10.0d),
        WEAK_BORDER(20.0d),
        WEAK_SYNC(30.0d),
        WEAK_GRID(40.0d),
        BAD_ASPECT(50.0d),
        BAD_PERSPECTIVE(60.0d),
        DUPLICATE_REGION(70.0d),
        NO_PLAUSIBLE_PROPOSAL(80.0d);

        private final double code;

        RejectionReason(double code) {
            this.code = code;
        }

        double code() {
            return code;
        }
    }

    private final class EvidenceAccumulator {

        private int samples;
        private double totalScore;

        private void addLight(int luminance) {
            addScore(lightConfidence(luminance));
        }

        private void addDark(int luminance) {
            addScore(darkConfidence(luminance));
        }

        private void addContrastingPair(int lightLuminance, int darkLuminance) {
            addScore((0.40d * lightConfidence(lightLuminance))
                    + (0.40d * darkConfidence(darkLuminance))
                    + (0.20d * contrastConfidence(lightLuminance, darkLuminance)));
        }

        private void addScore(double score) {
            totalScore += clampScore(score);
            samples++;
        }

        private double score() {
            return samples == 0 ? 0.0d : clampScore(totalScore / samples);
        }
    }
}
