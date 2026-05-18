package com.alx4j.jab4j.reader.capture.media.geometry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPolygon;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.CoordinateObservationSource;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidenceStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureType;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;
import com.alx4j.jab4j.reader.capture.media.cv.PerspectiveTransform;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaPaletteSample;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaPaletteSampler;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;

/**
 * Detects reader-supported finder pattern evidence for one capture-media candidate.
 *
 * <p>The detector is diagnostic-only for MVP-9. Retained source pixels are preferred when available; otherwise the
 * detector preserves the normalized-candidate fallback. In both cases it evaluates the current balanced-v1 finder
 * contract without opening any decode or restore path.</p>
 */
public final class CaptureMediaPatternEvidenceDetector {

    public static final int SCHEMA_VERSION = 1;

    private static final double MIN_DOMINANCE_MARGIN = 0.10d;
    private static final int MIN_MODULE_SIZE_PX = 4;
    private static final int INVALID_PALETTE_INDEX = Integer.MAX_VALUE;

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final TileCodecProfile tileCodecProfile;
    private final CaptureMediaPaletteSampler paletteSampler;
    private final SupportedTileFinderEvaluator finderEvaluator;

    /**
     * Creates a detector using current rendered-layout and balanced-v1 tile-profile defaults.
     */
    public CaptureMediaPatternEvidenceDetector() {
        this(
                new CaptureRenderedLayoutCatalog(),
                new FixedLayoutPlanner(),
                TileCodecProfiles.balancedV1(),
                new CaptureMediaPaletteSampler(),
                new SupportedTileFinderEvaluator()
        );
    }

    /**
     * Creates a detector with explicit collaborators for focused tests.
     *
     * @param layoutCatalog supported rendered-layout catalog
     * @param layoutPlanner fixed layout planner
     * @param tileCodecProfile supported tile codec profile
     * @param paletteSampler normalized-pixel palette sampler
     * @param finderEvaluator shared finder contract evaluator
     */
    public CaptureMediaPatternEvidenceDetector(
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            TileCodecProfile tileCodecProfile,
            CaptureMediaPaletteSampler paletteSampler,
            SupportedTileFinderEvaluator finderEvaluator
    ) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.tileCodecProfile = Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
        this.paletteSampler = Objects.requireNonNull(paletteSampler, "paletteSampler must not be null");
        this.finderEvaluator = Objects.requireNonNull(finderEvaluator, "finderEvaluator must not be null");
    }

    /**
     * Evaluates finder-pattern evidence from normalized candidate pixels.
     *
     * @param frame normalized capture-media candidate
     * @return deterministic candidate-scoped pattern evidence
     */
    public PatternEvidence detect(NormalizedCaptureFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        return detect(frame, ObservationContext.normalizedCandidate());
    }

    /**
     * Evaluates finder-pattern evidence from retained source pixels using the candidate source-space quadrilateral.
     *
     * <p>If source pixels have already been released or the candidate quadrilateral cannot be projected, the detector
     * falls back to normalized candidate pixels so older call paths keep their existing behavior.</p>
     *
     * @param sourceFrame retained source frame whose pixels are still available
     * @param frame normalized capture-media candidate derived from the source frame
     * @return deterministic candidate-scoped pattern evidence
     */
    public PatternEvidence detect(MediaInputFrame sourceFrame, NormalizedCaptureFrame frame) {
        Objects.requireNonNull(sourceFrame, "sourceFrame must not be null");
        Objects.requireNonNull(frame, "frame must not be null");
        requireSameSource(sourceFrame, frame);
        Optional<ObservationContext> sourceObservation = sourceObservationContext(sourceFrame, frame);
        return detect(frame, sourceObservation.orElseGet(ObservationContext::normalizedCandidate));
    }

    private PatternEvidence detect(NormalizedCaptureFrame frame, ObservationContext observationContext) {
        CaptureMediaCandidateId candidateId = CaptureMediaCandidateId.patternEvidence(proposalCandidateId(frame));
        List<LayoutProfile> profiles = matchingProfiles(frame);
        if (profiles.isEmpty()) {
            return evidence(
                    frame,
                    candidateId,
                    PatternEvidenceStatus.NOT_FOUND,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    List.of(
                            CaptureMediaEvidenceReasonCode.NOT_SUPPORTED_FOR_PROFILE,
                            CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE,
                            CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE,
                            CaptureMediaEvidenceReasonCode.ALIGNMENT_NOT_EXPECTED
                    ),
                    0.0d,
                    0.0d
            );
        }

        List<ArrangementEvaluation> arrangements = evaluateArrangements(frame, profiles, observationContext);
        Map<String, Double> orientationCandidates = orientationCandidates(arrangements);
        Map<String, Double> layoutProfileCandidates = layoutProfileCandidates(profiles, arrangements);
        Optional<ArrangementEvaluation> best = bestArrangement(arrangements);
        if (best.isEmpty()) {
            return evidence(
                    frame,
                    candidateId,
                    PatternEvidenceStatus.NOT_FOUND,
                    List.of(),
                    orientationCandidates,
                    layoutProfileCandidates,
                    List.of(
                            CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE,
                            CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE,
                            CaptureMediaEvidenceReasonCode.ALIGNMENT_NOT_EXPECTED
                    ),
                    0.0d,
                    0.0d
            );
        }

        ArrangementEvaluation selected = best.orElseThrow();
        List<PatternFeatureEvidence> features = selected.matchedModuleCount() == 0
                ? List.of()
                : featureEvidence(frame, selected, observationContext);
        List<ArrangementEvaluation> passing = competingArrangements(passingArrangements(arrangements));
        double confidence = selected.score();
        double dominanceMargin = dominanceMargin(passing, selected);
        List<CaptureMediaEvidenceReasonCode> reasonCodes = reasonCodes(selected, passing, dominanceMargin);
        PatternEvidenceStatus status = status(selected, passing, dominanceMargin);
        return evidence(
                frame,
                candidateId,
                status,
                features,
                orientationCandidates,
                layoutProfileCandidates,
                reasonCodes,
                confidence,
                dominanceMargin
        );
    }

    private CaptureMediaCandidateId proposalCandidateId(NormalizedCaptureFrame frame) {
        return CaptureMediaCandidateId.proposalCandidate(
                frame.sourceKind().name(),
                frame.callerOrder(),
                frame.sourceId(),
                candidatePixelHash(frame.pixelSha256()),
                frame.sourceRegionRank(),
                frame.profileAlternativeRank(),
                frame.profileAlternativeCount()
        );
    }

    private String candidatePixelHash(String pixelSha256) {
        if (validHashPrefix(pixelSha256)) {
            return pixelSha256;
        }
        return sha256Hex(pixelSha256);
    }

    private boolean validHashPrefix(String pixelSha256) {
        if (pixelSha256 == null || pixelSha256.length() < CaptureMediaCandidateId.PIXEL_HASH_PREFIX_LENGTH) {
            return false;
        }
        for (int index = 0; index < CaptureMediaCandidateId.PIXEL_HASH_PREFIX_LENGTH; index++) {
            char character = pixelSha256.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private PatternEvidence evidence(
            NormalizedCaptureFrame frame,
            CaptureMediaCandidateId candidateId,
            PatternEvidenceStatus status,
            List<PatternFeatureEvidence> features,
            Map<String, Double> orientationCandidates,
            Map<String, Double> layoutProfileCandidates,
            List<CaptureMediaEvidenceReasonCode> reasonCodes,
            double confidence,
            double dominanceMargin
    ) {
        return new PatternEvidence(
                SCHEMA_VERSION,
                candidateId,
                frame.layoutProfileId(),
                status,
                features,
                orientationCandidates,
                layoutProfileCandidates,
                false,
                deduplicated(reasonCodes),
                List.of(),
                clampUnit(confidence),
                clampUnit(dominanceMargin)
        );
    }

    private List<LayoutProfile> matchingProfiles(NormalizedCaptureFrame frame) {
        List<LayoutProfile> dimensionMatches = layoutCatalog.profiles()
                .stream()
                .filter(profile -> profile.frameWidthPx() == frame.normalizedWidthPixels())
                .filter(profile -> profile.frameHeightPx() == frame.normalizedHeightPixels())
                .toList();
        boolean currentProfileSupported = dimensionMatches.stream()
                .anyMatch(profile -> profile.profileId().equals(frame.layoutProfileId()));
        if (!currentProfileSupported) {
            return List.of();
        }
        return dimensionMatches;
    }

    private List<ArrangementEvaluation> evaluateArrangements(
            NormalizedCaptureFrame frame,
            List<LayoutProfile> profiles,
            ObservationContext observationContext
    ) {
        List<ArrangementEvaluation> arrangements = new ArrayList<>();
        boolean cameraDerivedCandidate = cameraDerived(frame);
        for (LayoutProfile profile : profiles) {
            FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
            for (OrientationCandidate orientation : OrientationCandidate.values()) {
                for (int sideVersion = tileCodecProfile.minSideVersion();
                        sideVersion <= tileCodecProfile.maxSideVersion();
                        sideVersion++) {
                    ArrangementEvaluation best = null;
                    for (int tileIndex = 0; tileIndex < layoutPlan.tilePlacements().size(); tileIndex++) {
                        TilePlacement placement = layoutPlan.tilePlacements().get(tileIndex);
                        Optional<ModuleObservation> observation = moduleObservation(
                                frame,
                                layoutPlan,
                                placement,
                                sideVersion,
                                orientation,
                                observationContext
                        );
                        if (observation.isEmpty()) {
                            continue;
                        }
                        ModuleObservation observed = observation.orElseThrow();
                        SupportedTileFinderEvaluator.Evaluation finderEvaluation =
                                finderEvaluator.evaluate(observed.canonicalModuleColors(), observed.geometry().dimension());
                        ArrangementEvaluation candidate = arrangementEvaluation(
                                profile,
                                orientation,
                                sideVersion,
                                tileIndex,
                                placement,
                                observed,
                                finderEvaluation,
                                cameraDerivedCandidate
                        );
                        if (betterArrangement(candidate, best)) {
                            best = candidate;
                        }
                    }
                    if (best != null) {
                        arrangements.add(best);
                    }
                }
            }
        }
        return List.copyOf(arrangements);
    }

    private Optional<ModuleObservation> moduleObservation(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int sideVersion,
            OrientationCandidate orientation,
            ObservationContext observationContext
    ) {
        SamplingGeometry geometry = samplingGeometry(layoutPlan, sideVersion);
        if (geometry.moduleSizePx() < MIN_MODULE_SIZE_PX) {
            return Optional.empty();
        }

        int dimension = geometry.dimension();
        int moduleCount = dimension * dimension;
        int[] observedColors = new int[moduleCount];
        double[] observedConfidences = new double[moduleCount];
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                CaptureMediaPaletteSample sample = sampleModule(
                        frame,
                        placement,
                        geometry,
                        row,
                        col,
                        observationContext
                );
                int index = moduleIndex(row, col, dimension);
                observedColors[index] = sample.accepted() ? sample.paletteIndex() : INVALID_PALETTE_INDEX;
                observedConfidences[index] = sample.confidence();
            }
        }

        List<Integer> canonicalModuleColors = new ArrayList<>(moduleCount);
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                ModuleCoordinate observed = orientation.toObserved(row, col, dimension);
                canonicalModuleColors.add(observedColors[moduleIndex(observed.row(), observed.col(), dimension)]);
            }
        }

        double finderConfidenceSum = 0.0d;
        int finderSampleCount = 0;
        for (SupportedTileFinderEvaluator.FinderWindow window : finderEvaluator.finderWindows(dimension)) {
            for (int row = window.startRow(); row < window.startRow() + window.sizeModules(); row++) {
                for (int col = window.startCol(); col < window.startCol() + window.sizeModules(); col++) {
                    ModuleCoordinate observed = orientation.toObserved(row, col, dimension);
                    finderConfidenceSum += observedConfidences[moduleIndex(observed.row(), observed.col(), dimension)];
                    finderSampleCount++;
                }
            }
        }
        double averageFinderSampleConfidence = finderSampleCount == 0
                ? 0.0d
                : finderConfidenceSum / finderSampleCount;
        return Optional.of(new ModuleObservation(
                geometry,
                canonicalModuleColors,
                averageFinderSampleConfidence
        ));
    }

    private SamplingGeometry samplingGeometry(FixedLayoutPlan layoutPlan, int sideVersion) {
        int dimension = tileCodecProfile.dimensionForSideVersion(sideVersion);
        int border = layoutPlan.separatorThicknessPx();
        int innerWidth = layoutPlan.tileSlotWidthPx() - (2 * border);
        int innerHeight = layoutPlan.tileSlotHeightPx() - (2 * border);
        int logicalSide = dimension + (2 * tileCodecProfile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        return new SamplingGeometry(sideVersion, dimension, moduleSize, offsetX, offsetY);
    }

    private CaptureMediaPaletteSample sampleModule(
            NormalizedCaptureFrame frame,
            TilePlacement placement,
            SamplingGeometry geometry,
            int moduleRow,
            int moduleCol,
            ObservationContext observationContext
    ) {
        double sampleX = moduleCenterX(placement, geometry, moduleCol);
        double sampleY = moduleCenterY(placement, geometry, moduleRow);
        if (observationContext.observationSource() == CoordinateObservationSource.SOURCE_SPACE) {
            return sampleSourcePixel(frame, sampleX, sampleY, observationContext);
        }
        return paletteSampler.sampleTolerantPalette(
                frame,
                clamp((int) Math.floor(sampleY), 0, frame.normalizedHeightPixels() - 1),
                clamp((int) Math.floor(sampleX), 0, frame.normalizedWidthPixels() - 1)
        );
    }

    private ArrangementEvaluation arrangementEvaluation(
            LayoutProfile profile,
            OrientationCandidate orientation,
            int sideVersion,
            int tileIndex,
            TilePlacement placement,
            ModuleObservation observation,
            SupportedTileFinderEvaluator.Evaluation finderEvaluation,
            boolean cameraDerivedCandidate
    ) {
        boolean exact = finderEvaluation.exact();
        boolean recoverable = cameraDerivedCandidate
                && finderEvaluation.supported(true)
                && finderEvaluation.recoverableWithAverageConfidence(observation.averageFinderSampleConfidence());
        boolean passed = exact || recoverable;
        return new ArrangementEvaluation(
                profile,
                orientation,
                sideVersion,
                tileIndex,
                placement,
                observation.geometry(),
                finderEvaluation,
                observation.averageFinderSampleConfidence(),
                finderEvaluation.aggregateConfidence(),
                passed
        );
    }

    private boolean betterArrangement(ArrangementEvaluation candidate, ArrangementEvaluation current) {
        if (current == null) {
            return true;
        }
        Comparator<ArrangementEvaluation> comparator = Comparator
                .comparingDouble(ArrangementEvaluation::score)
                .thenComparingInt(ArrangementEvaluation::matchedModuleCount)
                .thenComparing(ArrangementEvaluation::exact)
                .thenComparing(candidateEvaluation -> -candidateEvaluation.tileIndex())
                .thenComparing(candidateEvaluation -> -candidateEvaluation.sideVersion());
        return comparator.compare(candidate, current) > 0;
    }

    private Optional<ArrangementEvaluation> bestArrangement(List<ArrangementEvaluation> arrangements) {
        ArrangementEvaluation best = null;
        for (ArrangementEvaluation candidate : arrangements) {
            if (bestArrangementRank(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean bestArrangementRank(ArrangementEvaluation candidate, ArrangementEvaluation current) {
        if (current == null) {
            return true;
        }
        if (candidate.passed() != current.passed()) {
            return candidate.passed();
        }
        int scoreCompare = Double.compare(candidate.score(), current.score());
        if (scoreCompare != 0) {
            return scoreCompare > 0;
        }
        int matchedCompare = Integer.compare(candidate.matchedModuleCount(), current.matchedModuleCount());
        if (matchedCompare != 0) {
            return matchedCompare > 0;
        }
        int profileCompare = candidate.profile().profileId().compareTo(current.profile().profileId());
        if (profileCompare != 0) {
            return profileCompare < 0;
        }
        int orientationCompare = Integer.compare(candidate.orientation().degrees(), current.orientation().degrees());
        if (orientationCompare != 0) {
            return orientationCompare < 0;
        }
        int sideCompare = Integer.compare(candidate.sideVersion(), current.sideVersion());
        if (sideCompare != 0) {
            return sideCompare < 0;
        }
        return candidate.tileIndex() < current.tileIndex();
    }

    private Map<String, Double> orientationCandidates(List<ArrangementEvaluation> arrangements) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (OrientationCandidate orientation : OrientationCandidate.values()) {
            scores.put(orientation.sidecarKey(), 0.0d);
        }
        for (ArrangementEvaluation arrangement : arrangements) {
            scores.compute(
                    arrangement.orientation().sidecarKey(),
                    (key, value) -> Math.max(value == null ? 0.0d : value, arrangement.score())
            );
        }
        return scores;
    }

    private Map<String, Double> layoutProfileCandidates(
            List<LayoutProfile> profiles,
            List<ArrangementEvaluation> arrangements
    ) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (LayoutProfile profile : profiles) {
            scores.put(profile.profileId(), 0.0d);
        }
        for (ArrangementEvaluation arrangement : arrangements) {
            scores.compute(
                    arrangement.profile().profileId(),
                    (key, value) -> Math.max(value == null ? 0.0d : value, arrangement.score())
            );
        }
        return scores;
    }

    private List<ArrangementEvaluation> passingArrangements(List<ArrangementEvaluation> arrangements) {
        return arrangements.stream()
                .filter(ArrangementEvaluation::passed)
                .sorted(this::comparePassingArrangement)
                .toList();
    }

    private List<ArrangementEvaluation> competingArrangements(List<ArrangementEvaluation> passing) {
        Map<CompetitionKey, ArrangementEvaluation> competitors = new LinkedHashMap<>();
        for (ArrangementEvaluation arrangement : passing) {
            CompetitionKey key = new CompetitionKey(arrangement.profile().profileId(), arrangement.orientation());
            ArrangementEvaluation current = competitors.get(key);
            if (bestArrangementRank(arrangement, current)) {
                competitors.put(key, arrangement);
            }
        }
        return competitors.values()
                .stream()
                .sorted(this::comparePassingArrangement)
                .toList();
    }

    private int comparePassingArrangement(ArrangementEvaluation first, ArrangementEvaluation second) {
        int scoreCompare = Double.compare(second.score(), first.score());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        int profileCompare = first.profile().profileId().compareTo(second.profile().profileId());
        if (profileCompare != 0) {
            return profileCompare;
        }
        int orientationCompare = Integer.compare(first.orientation().degrees(), second.orientation().degrees());
        if (orientationCompare != 0) {
            return orientationCompare;
        }
        int sideCompare = Integer.compare(first.sideVersion(), second.sideVersion());
        if (sideCompare != 0) {
            return sideCompare;
        }
        return Integer.compare(first.tileIndex(), second.tileIndex());
    }

    private double dominanceMargin(List<ArrangementEvaluation> passing, ArrangementEvaluation selected) {
        if (!selected.passed() || passing.isEmpty()) {
            return 0.0d;
        }
        if (passing.size() == 1) {
            return selected.score();
        }
        return Math.max(0.0d, passing.get(0).score() - passing.get(1).score());
    }

    private PatternEvidenceStatus status(
            ArrangementEvaluation selected,
            List<ArrangementEvaluation> passing,
            double dominanceMargin
    ) {
        if (!selected.passed()) {
            return selected.matchedModuleCount() == 0
                    ? PatternEvidenceStatus.NOT_FOUND
                    : PatternEvidenceStatus.REJECTED;
        }
        if (passing.size() > 1 && dominanceMargin < MIN_DOMINANCE_MARGIN) {
            return PatternEvidenceStatus.AMBIGUOUS;
        }
        return PatternEvidenceStatus.DETECTED;
    }

    private List<CaptureMediaEvidenceReasonCode> reasonCodes(
            ArrangementEvaluation selected,
            List<ArrangementEvaluation> passing,
            double dominanceMargin
    ) {
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        reasonCodes.add(CaptureMediaEvidenceReasonCode.ALIGNMENT_NOT_EXPECTED);
        if (!selected.passed()) {
            if (selected.matchedModuleCount() == 0) {
                reasonCodes.add(CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE);
                reasonCodes.add(CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE);
            } else {
                reasonCodes.add(CaptureMediaEvidenceReasonCode.INSUFFICIENT_FINDER_MATCHES);
                reasonCodes.add(CaptureMediaEvidenceReasonCode.PARTIAL_FINDER_EVIDENCE);
            }
            return deduplicated(reasonCodes);
        }
        if (!selected.exact()) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.PARTIAL_FINDER_EVIDENCE);
        }
        if (passing.size() > 1 && dominanceMargin < MIN_DOMINANCE_MARGIN) {
            if (multipleOrientations(passing)) {
                reasonCodes.add(CaptureMediaEvidenceReasonCode.MULTIPLE_ORIENTATIONS);
            }
            if (multipleProfiles(passing)) {
                reasonCodes.add(CaptureMediaEvidenceReasonCode.MULTIPLE_PROFILE_CANDIDATES);
            }
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_DOMINANCE_MARGIN);
        }
        return deduplicated(reasonCodes);
    }

    private boolean multipleOrientations(List<ArrangementEvaluation> passing) {
        Set<OrientationCandidate> orientations = new LinkedHashSet<>();
        for (ArrangementEvaluation arrangement : passing) {
            orientations.add(arrangement.orientation());
        }
        return orientations.size() > 1;
    }

    private boolean multipleProfiles(List<ArrangementEvaluation> passing) {
        Set<String> profileIds = new LinkedHashSet<>();
        for (ArrangementEvaluation arrangement : passing) {
            profileIds.add(arrangement.profile().profileId());
        }
        return profileIds.size() > 1;
    }

    private List<PatternFeatureEvidence> featureEvidence(
            NormalizedCaptureFrame frame,
            ArrangementEvaluation arrangement,
            ObservationContext observationContext
    ) {
        List<PatternFeatureEvidence> features = new ArrayList<>();
        for (SupportedTileFinderEvaluator.FinderWindow window :
                finderEvaluator.finderWindows(arrangement.geometry().dimension())) {
            SupportedTileFinderEvaluator.FinderRoleEvaluation roleEvaluation =
                    arrangement.finderEvaluation().role(window.role());
            PolygonPair polygonPair = finderPolygon(frame, arrangement, window, observationContext);
            features.add(new PatternFeatureEvidence(
                    PatternFeatureType.FINDER,
                    Optional.of(window.role()),
                    OptionalInt.of(arrangement.tileIndex()),
                    tileCodecProfile.profileId() + "-side-" + arrangement.sideVersion(),
                    polygonPair.canonical(),
                    polygonPair.source(),
                    roleEvaluation.matchedModuleCount(),
                    roleEvaluation.expectedModuleCount(),
                    roleEvaluation.confidence(),
                    featureScale(polygonPair.source(), window.sizeModules() * arrangement.geometry().moduleSizePx()),
                    arrangement.orientation().degrees(),
                    observationContext.observationSource(),
                    featureReasonCodes(roleEvaluation)
            ));
        }
        return List.copyOf(features);
    }

    private List<CaptureMediaEvidenceReasonCode> featureReasonCodes(
            SupportedTileFinderEvaluator.FinderRoleEvaluation roleEvaluation
    ) {
        if (roleEvaluation.exact()) {
            return List.of();
        }
        if (roleEvaluation.matchedModuleCount() == 0) {
            return List.of(CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE);
        }
        if (roleEvaluation.recoverable()) {
            return List.of(CaptureMediaEvidenceReasonCode.PARTIAL_FINDER_EVIDENCE);
        }
        return List.of(
                CaptureMediaEvidenceReasonCode.INSUFFICIENT_FINDER_MATCHES,
                CaptureMediaEvidenceReasonCode.PARTIAL_FINDER_EVIDENCE
        );
    }

    private PolygonPair finderPolygon(
            NormalizedCaptureFrame frame,
            ArrangementEvaluation arrangement,
            SupportedTileFinderEvaluator.FinderWindow window,
            ObservationContext observationContext
    ) {
        ModuleBounds bounds = observedBounds(
                arrangement.orientation(),
                window,
                arrangement.geometry().dimension()
        );
        double left = moduleLeftX(arrangement.placement(), arrangement.geometry(), bounds.startCol());
        double top = moduleTopY(arrangement.placement(), arrangement.geometry(), bounds.startRow());
        double right = moduleLeftX(arrangement.placement(), arrangement.geometry(), bounds.endColExclusive());
        double bottom = moduleTopY(arrangement.placement(), arrangement.geometry(), bounds.endRowExclusive());
        CanonicalPolygon canonical = new CanonicalPolygon(List.of(
                new CanonicalPoint(left, top),
                new CanonicalPoint(right, top),
                new CanonicalPoint(right, bottom),
                new CanonicalPoint(left, bottom)
        ));
        SourcePolygon source = observationContext.sourcePolygon(frame, canonical);
        return new PolygonPair(canonical, source);
    }

    private ModuleBounds observedBounds(
            OrientationCandidate orientation,
            SupportedTileFinderEvaluator.FinderWindow window,
            int dimension
    ) {
        int startRow = Integer.MAX_VALUE;
        int startCol = Integer.MAX_VALUE;
        int endRow = Integer.MIN_VALUE;
        int endCol = Integer.MIN_VALUE;
        for (int row = window.startRow(); row < window.startRow() + window.sizeModules(); row++) {
            for (int col = window.startCol(); col < window.startCol() + window.sizeModules(); col++) {
                ModuleCoordinate observed = orientation.toObserved(row, col, dimension);
                startRow = Math.min(startRow, observed.row());
                startCol = Math.min(startCol, observed.col());
                endRow = Math.max(endRow, observed.row() + 1);
                endCol = Math.max(endCol, observed.col() + 1);
            }
        }
        return new ModuleBounds(startRow, startCol, endRow, endCol);
    }

    private int moduleIndex(int row, int col, int dimension) {
        return (row * dimension) + col;
    }

    private double moduleCenterX(TilePlacement placement, SamplingGeometry geometry, int moduleCol) {
        return moduleLeftX(placement, geometry, moduleCol) + (geometry.moduleSizePx() / 2.0d);
    }

    private double moduleCenterY(TilePlacement placement, SamplingGeometry geometry, int moduleRow) {
        return moduleTopY(placement, geometry, moduleRow) + (geometry.moduleSizePx() / 2.0d);
    }

    private double moduleLeftX(TilePlacement placement, SamplingGeometry geometry, int moduleCol) {
        return placement.xPx()
                + geometry.contentOffsetXPx()
                + ((moduleCol + tileCodecProfile.quietZoneModules()) * geometry.moduleSizePx());
    }

    private double moduleTopY(TilePlacement placement, SamplingGeometry geometry, int moduleRow) {
        return placement.yPx()
                + geometry.contentOffsetYPx()
                + ((moduleRow + tileCodecProfile.quietZoneModules()) * geometry.moduleSizePx());
    }

    private boolean cameraDerived(NormalizedCaptureFrame frame) {
        return frame.qualityMetrics().measured(frame.qualityMetrics().frameCoverageRatio())
                && frame.qualityMetrics().frameCoverageRatio() < 0.99d;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private CaptureMediaPaletteSample sampleSourcePixel(
            NormalizedCaptureFrame frame,
            double canonicalX,
            double canonicalY,
            ObservationContext observationContext
    ) {
        PerspectiveTransform.PerspectivePoint sourcePoint = observationContext.project(frame, canonicalX, canonicalY);
        MediaInputFrame sourceFrame = observationContext.sourceFrame().orElseThrow();
        int row = clamp((int) Math.round(sourcePoint.y()), 0, sourceFrame.heightPixels() - 1);
        int col = clamp((int) Math.round(sourcePoint.x()), 0, sourceFrame.widthPixels() - 1);
        return paletteSampler.tolerantPaletteSample(sourceFrame.argbPixelAt(row, col));
    }

    private Optional<ObservationContext> sourceObservationContext(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame frame
    ) {
        try {
            sourceFrame.argbPixelAt(0, 0);
            return Optional.of(ObservationContext.source(
                    sourceFrame,
                    PerspectiveTransform.fromUnitSquareTo(frame.frameCorners())
            ));
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void requireSameSource(MediaInputFrame sourceFrame, NormalizedCaptureFrame frame) {
        if (!sourceFrame.sourceId().equals(frame.sourceId())
                || sourceFrame.callerOrder() != frame.callerOrder()
                || sourceFrame.widthPixels() != frame.originalWidthPixels()
                || sourceFrame.heightPixels() != frame.originalHeightPixels()
                || !sourceFrame.pixelSha256().equals(frame.pixelSha256())) {
            throw new IllegalArgumentException("sourceFrame must match normalized candidate source metadata");
        }
    }

    private double featureScale(SourcePolygon source, double fallbackScale) {
        List<SourcePoint> vertices = source.vertices();
        if (vertices.size() < 2) {
            return fallbackScale;
        }
        double maximumSide = 0.0d;
        for (int index = 0; index < vertices.size(); index++) {
            maximumSide = Math.max(maximumSide, distance(vertices.get(index), vertices.get((index + 1) % vertices.size())));
        }
        return maximumSide > 0.0d ? maximumSide : fallbackScale;
    }

    private static double distance(SourcePoint first, SourcePoint second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private double clampUnit(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private List<CaptureMediaEvidenceReasonCode> deduplicated(
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return List.copyOf(new LinkedHashSet<>(reasonCodes));
    }

    private enum OrientationCandidate {
        ROTATION_0("rotation-0", 0),
        ROTATION_90("rotation-90", 90),
        ROTATION_180("rotation-180", 180),
        ROTATION_270("rotation-270", 270);

        private final String sidecarKey;
        private final int degrees;

        OrientationCandidate(String sidecarKey, int degrees) {
            this.sidecarKey = sidecarKey;
            this.degrees = degrees;
        }

        private String sidecarKey() {
            return sidecarKey;
        }

        private int degrees() {
            return degrees;
        }

        private ModuleCoordinate toObserved(int canonicalRow, int canonicalCol, int dimension) {
            return switch (this) {
                case ROTATION_0 -> new ModuleCoordinate(canonicalRow, canonicalCol);
                case ROTATION_90 -> new ModuleCoordinate(dimension - 1 - canonicalCol, canonicalRow);
                case ROTATION_180 -> new ModuleCoordinate(dimension - 1 - canonicalRow, dimension - 1 - canonicalCol);
                case ROTATION_270 -> new ModuleCoordinate(canonicalCol, dimension - 1 - canonicalRow);
            };
        }
    }

    private record ArrangementEvaluation(
            LayoutProfile profile,
            OrientationCandidate orientation,
            int sideVersion,
            int tileIndex,
            TilePlacement placement,
            SamplingGeometry geometry,
            SupportedTileFinderEvaluator.Evaluation finderEvaluation,
            double averageFinderSampleConfidence,
            double score,
            boolean passed
    ) {

        private ArrangementEvaluation {
            Objects.requireNonNull(profile, "profile must not be null");
            Objects.requireNonNull(orientation, "orientation must not be null");
            Objects.requireNonNull(placement, "placement must not be null");
            Objects.requireNonNull(geometry, "geometry must not be null");
            Objects.requireNonNull(finderEvaluation, "finderEvaluation must not be null");
        }

        private int matchedModuleCount() {
            return finderEvaluation.matchedModuleCount();
        }

        private boolean exact() {
            return finderEvaluation.exact();
        }
    }

    private record ModuleObservation(
            SamplingGeometry geometry,
            List<Integer> canonicalModuleColors,
            double averageFinderSampleConfidence
    ) {

        private ModuleObservation {
            Objects.requireNonNull(geometry, "geometry must not be null");
            canonicalModuleColors = List.copyOf(Objects.requireNonNull(
                    canonicalModuleColors,
                    "canonicalModuleColors must not be null"
            ));
        }
    }

    private record SamplingGeometry(
            int sideVersion,
            int dimension,
            int moduleSizePx,
            int contentOffsetXPx,
            int contentOffsetYPx
    ) {
    }

    private record ModuleCoordinate(int row, int col) {
    }

    private record ModuleBounds(int startRow, int startCol, int endRowExclusive, int endColExclusive) {
    }

    private record PolygonPair(CanonicalPolygon canonical, SourcePolygon source) {
    }

    private record CompetitionKey(String profileId, OrientationCandidate orientation) {
    }

    private record ObservationContext(
            Optional<MediaInputFrame> sourceFrame,
            Optional<PerspectiveTransform> normalizedToSource,
            CoordinateObservationSource observationSource
    ) {

        private ObservationContext {
            sourceFrame = Objects.requireNonNull(sourceFrame, "sourceFrame must not be null");
            normalizedToSource = Objects.requireNonNull(
                    normalizedToSource,
                    "normalizedToSource must not be null"
            );
            Objects.requireNonNull(observationSource, "observationSource must not be null");
            if (observationSource == CoordinateObservationSource.SOURCE_SPACE
                    && (sourceFrame.isEmpty() || normalizedToSource.isEmpty())) {
                throw new IllegalArgumentException("source-space observation requires source frame and transform");
            }
        }

        private static ObservationContext normalizedCandidate() {
            return new ObservationContext(
                    Optional.empty(),
                    Optional.empty(),
                    CoordinateObservationSource.NORMALIZED_CANDIDATE
            );
        }

        private static ObservationContext source(
                MediaInputFrame sourceFrame,
                PerspectiveTransform normalizedToSource
        ) {
            return new ObservationContext(
                    Optional.of(sourceFrame),
                    Optional.of(normalizedToSource),
                    CoordinateObservationSource.SOURCE_SPACE
            );
        }

        private PerspectiveTransform.PerspectivePoint project(
                NormalizedCaptureFrame frame,
                double canonicalX,
                double canonicalY
        ) {
            return normalizedToSource.orElseThrow().map(
                    canonicalX / frame.normalizedWidthPixels(),
                    canonicalY / frame.normalizedHeightPixels()
            );
        }

        private SourcePolygon sourcePolygon(NormalizedCaptureFrame frame, CanonicalPolygon canonical) {
            if (observationSource != CoordinateObservationSource.SOURCE_SPACE) {
                return new SourcePolygon(canonical.vertices()
                        .stream()
                        .map(point -> new SourcePoint(point.x(), point.y()))
                        .toList());
            }
            return new SourcePolygon(canonical.vertices()
                    .stream()
                    .map(point -> project(frame, point.x(), point.y()))
                    .map(point -> new SourcePoint(point.x(), point.y()))
                    .toList());
        }
    }
}
