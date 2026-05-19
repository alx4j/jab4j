package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSampleStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingAggregationMethod;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteClassificationMode;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteCenterSource;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteColorEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteColorMethod;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteSafetyDecision;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;
import com.alx4j.jab4j.reader.capture.media.evidence.WeakTileEvidence;
import com.alx4j.jab4j.reader.capture.media.geometry.SupportedTileFinderEvaluator;
import com.alx4j.jab4j.reader.capture.media.geometry.SupportedTileFinderEvaluator.FinderWindow;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaLocalLatticeRefiner;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaLocalLatticeRefiner.LocalRefinementAttempt;
import com.alx4j.jab4j.reader.capture.media.geometry.LocalGridRefinement;
import com.alx4j.jab4j.reader.capture.media.geometry.ModuleLatticeProjector;
import com.alx4j.jab4j.reader.capture.media.geometry.ModuleLatticeProjector.ProjectedModuleCell;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaLogicalTileValidator.FailureStage;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaLogicalTileValidator.ValidationAttempt;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;

/**
 * Samples diagnostic module evidence from retained source-image pixels through accepted geometry candidates.
 */
public final class CaptureMediaSourceSpaceModuleSampler {

    public static final int SCHEMA_VERSION = 1;

    private static final List<Double> CENTRAL_SCALES = List.of(0.50d, 0.40d, 0.60d);
    private static final int SAMPLE_GRID_SIZE = 7;
    private static final int MIN_READABLE_SAMPLE_COUNT = 9;
    private static final int GOOD_SAMPLE_COUNT = 25;
    private static final double HIGH_VARIANCE_THRESHOLD = 900.0d;
    private static final double GOOD_COLOR_MARGIN = 64.0d;
    private static final double LOW_DISTANCE_MARGIN_THRESHOLD = 16.0d;
    private static final double GOOD_INNER_FOOTPRINT_AREA_PX = 4.0d;
    private static final double GOOD_MINIMUM_FOOTPRINT_EDGE_PX = 2.0d;
    private static final double MIN_READABLE_MODULE_CONFIDENCE = 0.20d;
    private static final double MIN_READABLE_FOOTPRINT_QUALITY = 0.25d;
    private static final String CENTRAL_REGION_POLICY = "canonical-center-shrink-v1:grid7:min9";
    private static final String EXACT_PALETTE_SOURCE = "exact-rendered-palette-v1";
    private static final String EXPECTED_INDEX_REFERENCE_SOURCE = "expected-index-finder-modules-v1";
    private static final String PALETTE_THRESHOLD_VERSION = "mvp10-palette-thresholds-v1";

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final TileCodecProfile tileCodecProfile;
    private final ModuleLatticeProjector latticeProjector;
    private final CaptureMediaPaletteSampler paletteSampler;
    private final FixedLayoutPlanner layoutPlanner;
    private final CaptureMediaLogicalTileValidator logicalTileValidator;
    private final boolean sourceSpaceSamplingEnabled;
    private final boolean localRefinementEnabled;
    private final boolean observedPaletteClassificationEnabled;
    private final CaptureMediaLocalLatticeRefiner localLatticeRefiner;
    private final SupportedTileFinderEvaluator tileFinderEvaluator;

    /**
     * Creates a source-space sampler using current reader-owned layout and palette defaults.
     */
    public CaptureMediaSourceSpaceModuleSampler() {
        this(
                new CaptureRenderedLayoutCatalog(),
                TileCodecProfiles.balancedV1(),
                new ModuleLatticeProjector(),
                new CaptureMediaPaletteSampler(),
                new FixedLayoutPlanner(),
                new CaptureMediaLogicalTileValidator(),
                true,
                true,
                new CaptureMediaLocalLatticeRefiner(),
                new SupportedTileFinderEvaluator()
        );
    }

    /**
     * Creates a source-space sampler with explicit collaborators for focused tests.
     *
     * @param layoutCatalog rendered-layout catalog used to resolve the normalized candidate profile
     * @param tileCodecProfile tile profile used to build the diagnostic lattice
     * @param latticeProjector projector used to map canonical cells into source space
     * @param paletteSampler palette sampler that owns color classification thresholds
     */
    public CaptureMediaSourceSpaceModuleSampler(
            CaptureRenderedLayoutCatalog layoutCatalog,
            TileCodecProfile tileCodecProfile,
            ModuleLatticeProjector latticeProjector,
            CaptureMediaPaletteSampler paletteSampler
    ) {
        this(
                layoutCatalog,
                tileCodecProfile,
                latticeProjector,
                paletteSampler,
                new FixedLayoutPlanner(),
                new CaptureMediaLogicalTileValidator(),
                true,
                true,
                new CaptureMediaLocalLatticeRefiner(),
                new SupportedTileFinderEvaluator()
        );
    }

    /**
     * Creates a source-space sampler with explicit internal enablement for rollback tests.
     *
     * @param sourceSpaceSamplingEnabled true when source-space sampling should run
     */
    public CaptureMediaSourceSpaceModuleSampler(boolean sourceSpaceSamplingEnabled) {
        this(sourceSpaceSamplingEnabled, true);
    }

    /**
     * Creates a source-space sampler with explicit internal enablement for rollback tests.
     *
     * @param sourceSpaceSamplingEnabled true when source-space sampling should run
     * @param localRefinementEnabled true when bounded local-grid refinement may run after baseline sampling
     */
    public CaptureMediaSourceSpaceModuleSampler(
            boolean sourceSpaceSamplingEnabled,
            boolean localRefinementEnabled
    ) {
        this(sourceSpaceSamplingEnabled, localRefinementEnabled, true);
    }

    /**
     * Creates a source-space sampler with explicit internal enablement for rollback tests.
     *
     * @param sourceSpaceSamplingEnabled true when source-space sampling should run
     * @param localRefinementEnabled true when bounded local-grid refinement may run after baseline sampling
     * @param observedPaletteClassificationEnabled true when safe observed palettes may classify modules
     */
    public CaptureMediaSourceSpaceModuleSampler(
            boolean sourceSpaceSamplingEnabled,
            boolean localRefinementEnabled,
            boolean observedPaletteClassificationEnabled
    ) {
        this(
                new CaptureRenderedLayoutCatalog(),
                TileCodecProfiles.balancedV1(),
                new ModuleLatticeProjector(),
                new CaptureMediaPaletteSampler(),
                new FixedLayoutPlanner(),
                new CaptureMediaLogicalTileValidator(),
                sourceSpaceSamplingEnabled,
                localRefinementEnabled,
                new CaptureMediaLocalLatticeRefiner(),
                new SupportedTileFinderEvaluator(),
                observedPaletteClassificationEnabled
        );
    }

    /**
     * Creates a source-space sampler with explicit validation collaborators for focused tests.
     *
     * @param layoutCatalog rendered-layout catalog used to resolve the normalized candidate profile
     * @param tileCodecProfile tile profile used to build the diagnostic lattice
     * @param latticeProjector projector used to map canonical cells into source space
     * @param paletteSampler palette sampler that owns color classification thresholds
     * @param layoutPlanner fixed layout planner used for slot validation
     * @param logicalTileValidator existing tile/envelope/slot validator
     */
    CaptureMediaSourceSpaceModuleSampler(
            CaptureRenderedLayoutCatalog layoutCatalog,
            TileCodecProfile tileCodecProfile,
            ModuleLatticeProjector latticeProjector,
            CaptureMediaPaletteSampler paletteSampler,
            FixedLayoutPlanner layoutPlanner,
            CaptureMediaLogicalTileValidator logicalTileValidator,
            boolean sourceSpaceSamplingEnabled
    ) {
        this(
                layoutCatalog,
                tileCodecProfile,
                latticeProjector,
                paletteSampler,
                layoutPlanner,
                logicalTileValidator,
                sourceSpaceSamplingEnabled,
                true,
                new CaptureMediaLocalLatticeRefiner(),
                new SupportedTileFinderEvaluator(),
                true
        );
    }

    /**
     * Creates a source-space sampler with explicit validation and local-refinement collaborators for focused tests.
     *
     * @param layoutCatalog rendered-layout catalog used to resolve the normalized candidate profile
     * @param tileCodecProfile tile profile used to build the diagnostic lattice
     * @param latticeProjector projector used to map canonical cells into source space
     * @param paletteSampler palette sampler that owns color classification thresholds
     * @param layoutPlanner fixed layout planner used for slot validation
     * @param logicalTileValidator existing tile/envelope/slot validator
     * @param sourceSpaceSamplingEnabled true when source-space sampling should run
     * @param localRefinementEnabled true when bounded local-grid refinement may run
     * @param localLatticeRefiner local lattice refiner used for correction planning and evidence
     */
    CaptureMediaSourceSpaceModuleSampler(
            CaptureRenderedLayoutCatalog layoutCatalog,
            TileCodecProfile tileCodecProfile,
            ModuleLatticeProjector latticeProjector,
            CaptureMediaPaletteSampler paletteSampler,
            FixedLayoutPlanner layoutPlanner,
            CaptureMediaLogicalTileValidator logicalTileValidator,
            boolean sourceSpaceSamplingEnabled,
            boolean localRefinementEnabled,
            CaptureMediaLocalLatticeRefiner localLatticeRefiner
    ) {
        this(
                layoutCatalog,
                tileCodecProfile,
                latticeProjector,
                paletteSampler,
                layoutPlanner,
                logicalTileValidator,
                sourceSpaceSamplingEnabled,
                localRefinementEnabled,
                localLatticeRefiner,
                new SupportedTileFinderEvaluator(),
                true
        );
    }

    CaptureMediaSourceSpaceModuleSampler(
            CaptureRenderedLayoutCatalog layoutCatalog,
            TileCodecProfile tileCodecProfile,
            ModuleLatticeProjector latticeProjector,
            CaptureMediaPaletteSampler paletteSampler,
            FixedLayoutPlanner layoutPlanner,
            CaptureMediaLogicalTileValidator logicalTileValidator,
            boolean sourceSpaceSamplingEnabled,
            boolean localRefinementEnabled,
            CaptureMediaLocalLatticeRefiner localLatticeRefiner,
            SupportedTileFinderEvaluator tileFinderEvaluator
    ) {
        this(
                layoutCatalog,
                tileCodecProfile,
                latticeProjector,
                paletteSampler,
                layoutPlanner,
                logicalTileValidator,
                sourceSpaceSamplingEnabled,
                localRefinementEnabled,
                localLatticeRefiner,
                tileFinderEvaluator,
                true
        );
    }

    CaptureMediaSourceSpaceModuleSampler(
            CaptureRenderedLayoutCatalog layoutCatalog,
            TileCodecProfile tileCodecProfile,
            ModuleLatticeProjector latticeProjector,
            CaptureMediaPaletteSampler paletteSampler,
            FixedLayoutPlanner layoutPlanner,
            CaptureMediaLogicalTileValidator logicalTileValidator,
            boolean sourceSpaceSamplingEnabled,
            boolean localRefinementEnabled,
            CaptureMediaLocalLatticeRefiner localLatticeRefiner,
            SupportedTileFinderEvaluator tileFinderEvaluator,
            boolean observedPaletteClassificationEnabled
    ) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.tileCodecProfile = Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
        this.latticeProjector = Objects.requireNonNull(latticeProjector, "latticeProjector must not be null");
        this.paletteSampler = Objects.requireNonNull(paletteSampler, "paletteSampler must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.logicalTileValidator = Objects.requireNonNull(
                logicalTileValidator,
                "logicalTileValidator must not be null"
        );
        this.sourceSpaceSamplingEnabled = sourceSpaceSamplingEnabled;
        this.localRefinementEnabled = localRefinementEnabled;
        this.observedPaletteClassificationEnabled = observedPaletteClassificationEnabled;
        this.localLatticeRefiner = Objects.requireNonNull(
                localLatticeRefiner,
                "localLatticeRefiner must not be null"
        );
        this.tileFinderEvaluator = Objects.requireNonNull(tileFinderEvaluator, "tileFinderEvaluator must not be null");
    }

    /**
     * Samples retained source pixels for accepted geometry candidates and bounded central-scale variants.
     *
     * <p>Readable tile candidates are also passed through the existing tile/envelope/slot validator. The returned
     * evidence does not retain source ARGB buffers.</p>
     *
     * @param sourceFrame retained source frame whose pixels are still available
     * @param normalizedFrame normalized candidate and layout context
     * @param geometryEvidence fitted geometry evidence for the normalized candidate
     * @return source-space module sampling evidence in deterministic candidate and variant order
     */
    public List<ModuleSamplingEvidence> sample(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame normalizedFrame,
            GeometryFitEvidence geometryEvidence
    ) {
        return sampleAndValidate(sourceFrame, normalizedFrame, geometryEvidence).evidence();
    }

    /**
     * Samples source-space modules and validates readable logical tile candidates through existing protocol gates.
     *
     * @param sourceFrame retained source frame whose pixels are still available
     * @param normalizedFrame normalized candidate and layout context
     * @param geometryEvidence fitted geometry evidence for the normalized candidate
     * @return source-space sampling evidence plus first accepted payload set, if any
     */
    public SourceSpaceValidationSample sampleAndValidate(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame normalizedFrame,
            GeometryFitEvidence geometryEvidence
    ) {
        return sampleAndValidate(sourceFrame, normalizedFrame, geometryEvidence, Optional.empty(), true);
    }

    /**
     * Samples source-space modules with an optional local-grid correction and validates readable logical tile
     * candidates through existing protocol gates.
     *
     * @param sourceFrame retained source frame whose pixels are still available
     * @param normalizedFrame normalized candidate and layout context
     * @param geometryEvidence fitted geometry evidence for the normalized candidate
     * @param localRefinement optional bounded local-grid correction
     * @return source-space sampling evidence plus first accepted payload set, if any
     */
    public SourceSpaceValidationSample sampleAndValidate(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame normalizedFrame,
            GeometryFitEvidence geometryEvidence,
            Optional<LocalGridRefinement> localRefinement
    ) {
        return sampleAndValidate(sourceFrame, normalizedFrame, geometryEvidence, localRefinement, false);
    }

    private SourceSpaceValidationSample sampleAndValidate(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame normalizedFrame,
            GeometryFitEvidence geometryEvidence,
            Optional<LocalGridRefinement> localRefinement,
            boolean automaticLocalRefinement
    ) {
        Objects.requireNonNull(sourceFrame, "sourceFrame must not be null");
        Objects.requireNonNull(normalizedFrame, "normalizedFrame must not be null");
        Objects.requireNonNull(geometryEvidence, "geometryEvidence must not be null");
        Objects.requireNonNull(localRefinement, "localRefinement must not be null");
        requireSameSource(sourceFrame, normalizedFrame);

        Optional<LayoutProfile> layoutProfile = layoutProfile(normalizedFrame);
        int sideVersion = selectedSideVersion();
        List<GeometryCandidateEvidence> acceptedCandidates = acceptedGeometryCandidates(geometryEvidence);
        Optional<GeometryCandidateEvidence> firstAccepted = acceptedCandidates.stream().findFirst();
        if (!sourceSpaceSamplingEnabled) {
            ModuleSamplingEvidence unavailable = unavailableEvidence(
                    normalizedFrame,
                    geometryEvidence,
                    firstAccepted,
                    layoutProfile,
                    sideVersion,
                    ModuleSamplingStatus.NOT_AVAILABLE,
                    CaptureMediaEvidenceReasonCode.SOURCE_SPACE_SAMPLING_DISABLED
            );
            return SourceSpaceValidationSample.of(List.of(unavailable), List.of(
                    localLatticeRefiner.diagnose(geometryEvidence, unavailable)
            ));
        }
        if (acceptedCandidates.isEmpty()) {
            ModuleSamplingEvidence unavailable = unavailableEvidence(
                    normalizedFrame,
                    geometryEvidence,
                    Optional.empty(),
                    layoutProfile,
                    sideVersion,
                    ModuleSamplingStatus.WITHHELD,
                    CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GEOMETRY
            );
            return SourceSpaceValidationSample.of(List.of(unavailable), List.of(
                    localLatticeRefiner.diagnose(geometryEvidence, unavailable)
            ));
        }
        if (!sourcePixelsAvailable(sourceFrame)) {
            ModuleSamplingEvidence unavailable = unavailableEvidence(
                    normalizedFrame,
                    geometryEvidence,
                    Optional.of(acceptedCandidates.get(0)),
                    layoutProfile,
                    sideVersion,
                    ModuleSamplingStatus.NOT_AVAILABLE,
                    CaptureMediaEvidenceReasonCode.SOURCE_PIXELS_UNAVAILABLE
            );
            return SourceSpaceValidationSample.of(List.of(unavailable), List.of(
                    localLatticeRefiner.diagnose(geometryEvidence, unavailable)
            ));
        }
        if (layoutProfile.isEmpty()) {
            ModuleSamplingEvidence unavailable = unavailableEvidence(
                    normalizedFrame,
                    geometryEvidence,
                    Optional.of(acceptedCandidates.get(0)),
                    Optional.empty(),
                    sideVersion,
                    ModuleSamplingStatus.NOT_AVAILABLE,
                    CaptureMediaEvidenceReasonCode.NOT_SUPPORTED_FOR_PROFILE
            );
            return SourceSpaceValidationSample.of(List.of(unavailable), List.of(
                    localLatticeRefiner.diagnose(geometryEvidence, unavailable)
            ));
        }

        List<Double> baselineScales = localRefinement.isPresent()
                ? List.of(CENTRAL_SCALES.get(0))
                : CENTRAL_SCALES;
        List<ModuleSamplingEvidence> evidence = new ArrayList<>(
                acceptedCandidates.size() * (baselineScales.size() + 1)
        );
        List<LocalRefinementEvidence> localRefinementEvidence = new ArrayList<>(acceptedCandidates.size());
        List<TilePayload> acceptedPayloads = List.of();
        for (GeometryCandidateEvidence candidate : acceptedCandidates) {
            VariantSample baselineVariant = null;
            for (int variantIndex = 0; variantIndex < baselineScales.size(); variantIndex++) {
                VariantSample variant = sampleVariant(
                        sourceFrame,
                        normalizedFrame,
                        layoutProfile.orElseThrow(),
                        sideVersion,
                        candidate,
                        baselineScales.get(variantIndex),
                        variantIndex + 1,
                        localRefinement
                );
                evidence.add(variant.evidence());
                if (variantIndex == 0) {
                    baselineVariant = variant;
                }
                if (acceptedPayloads.isEmpty() && !variant.acceptedPayloads().isEmpty()) {
                    acceptedPayloads = variant.acceptedPayloads();
                }
            }
            if (automaticLocalRefinement && localRefinement.isEmpty() && baselineVariant != null) {
                Optional<VariantSample> refinedVariant = Optional.empty();
                LocalRefinementAttempt attempt = localLatticeRefiner.attempt(
                        geometryEvidence,
                        baselineVariant.evidence()
                );
                if (localRefinementEnabled && attempt.localGrid().isPresent()) {
                    VariantSample candidateRefinedVariant = sampleVariant(
                            sourceFrame,
                            normalizedFrame,
                            layoutProfile.orElseThrow(),
                            sideVersion,
                            candidate,
                            CENTRAL_SCALES.get(0),
                            1,
                            attempt.localGrid()
                    );
                    refinedVariant = Optional.of(candidateRefinedVariant);
                }
                LocalRefinementEvidence completed = localRefinementEnabled
                        ? localLatticeRefiner.complete(attempt, refinedVariant.map(VariantSample::evidence))
                        : localLatticeRefiner.disabled(geometryEvidence, baselineVariant.evidence());
                localRefinementEvidence.add(completed);
                if (completed.status() == LocalRefinementStatus.APPLIED && refinedVariant.isPresent()) {
                    VariantSample applied = refinedVariant.orElseThrow();
                    evidence.add(applied.evidence());
                    if (applied.acceptedPayloads().size() > acceptedPayloads.size()) {
                        acceptedPayloads = applied.acceptedPayloads();
                    }
                }
            }
        }
        return new SourceSpaceValidationSample(evidence, acceptedPayloads, localRefinementEvidence);
    }

    private VariantSample sampleVariant(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame normalizedFrame,
            LayoutProfile layoutProfile,
            int sideVersion,
            GeometryCandidateEvidence geometryCandidate,
            double centralScale,
            int variantRank,
            Optional<LocalGridRefinement> localRefinement
    ) {
        int dimension = tileCodecProfile.dimensionForSideVersion(sideVersion);
        List<ProjectedModuleCell> cells;
        try {
            cells = latticeProjector.project(
                    layoutProfile,
                    tileCodecProfile,
                    sideVersion,
                    geometryCandidate,
                    centralScale,
                    localRefinement.orElse(null)
            );
        } catch (IllegalArgumentException exception) {
            return VariantSample.evidenceOnly(unavailableEvidence(
                    normalizedFrame,
                    null,
                    Optional.of(geometryCandidate),
                    Optional.of(layoutProfile),
                    sideVersion,
                    ModuleSamplingStatus.WITHHELD,
                    CaptureMediaEvidenceReasonCode.NON_INVERTIBLE_TRANSFORM
            ));
        }

        CaptureMediaPaletteModel exactPaletteModel = paletteSampler.exactPaletteModel();
        List<ModuleEvidence> modules = new ArrayList<>(cells.size());
        for (ProjectedModuleCell cell : cells) {
            modules.add(sampleModule(
                    sourceFrame,
                    geometryCandidate,
                    exactPaletteModel,
                    ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                    ObservedPaletteClassificationMode.EXACT,
                    EXACT_PALETTE_SOURCE,
                    cell
            ));
        }

        FixedLayoutPlan layoutPlan = layoutPlanner.plan(layoutProfile);
        CaptureMediaCandidateId samplingCandidateId = CaptureMediaCandidateId.samplingCandidate(
                geometryCandidate.candidateId(),
                scalePercent(centralScale),
                variantRank
        );
        CaptureMediaCandidateId evidenceCandidateId = localRefinement.isPresent()
                ? CaptureMediaCandidateId.refinementCandidate(samplingCandidateId)
                : samplingCandidateId;
        ObservedPaletteSelection paletteSelection = observedPaletteSelection(
                evidenceCandidateId,
                layoutPlan,
                sideVersion,
                modules
        );
        if (paletteSelection.classificationMode() != ObservedPaletteClassificationMode.EXACT) {
            modules = new ArrayList<>(cells.size());
            for (ProjectedModuleCell cell : cells) {
                modules.add(sampleModule(
                        sourceFrame,
                        geometryCandidate,
                        paletteSelection.paletteModel(),
                        paletteSelection.colorMethod(),
                        paletteSelection.classificationMode(),
                        paletteSelection.paletteModelSource(),
                        cell
                ));
            }
        }

        Counts counts = Counts.from(modules);
        VariantValidation validation = validateVariant(
                layoutPlan,
                sideVersion,
                geometryCandidate,
                centralScale,
                variantRank,
                modules
        );
        List<CaptureMediaEvidenceReasonCode> reasonCodes = aggregateReasonCodes(modules);
        reasonCodes.add(CaptureMediaEvidenceReasonCode.SIDE_VERSION_INFERRED_FROM_LAYOUT);
        reasonCodes.addAll(paletteSelection.evidence().reasonCodes());
        reasonCodes.addAll(validation.reasonCodes());
        ModuleSamplingEvidence evidence = new ModuleSamplingEvidence(
                SCHEMA_VERSION,
                evidenceCandidateId,
                samplingStatus(counts),
                geometryCandidate.candidateId().geometryCandidateId().orElseThrow(),
                layoutProfile.profileId(),
                layoutProfile.cols() * dimension,
                layoutProfile.rows() * dimension,
                geometryCandidate.canonicalCoordinateSystem(),
                localRefinement.isPresent()
                        ? "geometry-fit:" + geometryCandidate.fitModelType().name() + "+local-grid"
                        : "geometry-fit:" + geometryCandidate.fitModelType().name(),
                centralScale,
                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                CENTRAL_REGION_POLICY,
                counts.total(),
                counts.sampled(),
                counts.readable(),
                counts.ambiguous(),
                counts.unreadable(),
                counts.clipped(),
                counts.outOfBounds(),
                summary(modules.stream()
                        .filter(module -> module.sampleCount() > 0)
                        .map(ModuleEvidence::confidenceMargin)
                        .toList()),
                summary(modules.stream()
                        .filter(module -> module.sampleCount() > 0)
                        .map(ModuleEvidence::colorVariance)
                        .toList()),
                summary(modules.stream()
                        .filter(module -> module.sampleCount() > 0)
                        .map(ModuleEvidence::moduleConfidence)
                        .toList()),
                summary(modules.stream()
                        .map(ModuleEvidence::geometryFootprintQuality)
                        .toList()),
                weakModuleCount(modules),
                poorFootprintModuleCount(modules),
                paletteSelection.evidence(),
                validation.tileDecodeAttempted(),
                validation.attemptCount(),
                validation.acceptedPayloads().size(),
                validation.weakTileEvidence(),
                validation.failureStages(),
                modules,
                deduplicated(reasonCodes)
        );
        return new VariantSample(evidence, validation.acceptedPayloads());
    }

    private ObservedPaletteSelection observedPaletteSelection(
            CaptureMediaCandidateId candidateId,
            FixedLayoutPlan layoutPlan,
            int sideVersion,
            List<ModuleEvidence> modules
    ) {
        int dimension = tileCodecProfile.dimensionForSideVersion(sideVersion);
        List<CaptureMediaPaletteCalibrationSample> references = finderCalibrationReferences(
                layoutPlan,
                dimension,
                modules
        );
        CaptureMediaCalibratedPalette calibratedPalette = paletteSampler.calibratePalette(references);
        CaptureMediaPaletteModel model = calibratedPalette.model();
        ObservedPaletteEvidence evidence = observedPaletteEvidence(candidateId, model, references);
        if (!observedPaletteClassificationEnabled
                && evidence.status() == ObservedPaletteStatus.SAFE_FOR_CLASSIFICATION) {
            ObservedPaletteEvidence withheld = withheldObservedPaletteEvidence(candidateId, evidence.colors());
            return new ObservedPaletteSelection(
                    paletteSampler.exactPaletteModel(),
                    ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                    ObservedPaletteClassificationMode.EXACT,
                    EXACT_PALETTE_SOURCE,
                    withheld
            );
        }
        if (evidence.status() != ObservedPaletteStatus.SAFE_FOR_CLASSIFICATION) {
            return new ObservedPaletteSelection(
                    paletteSampler.exactPaletteModel(),
                    ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                    ObservedPaletteClassificationMode.EXACT,
                    EXACT_PALETTE_SOURCE,
                    evidence
            );
        }

        ObservedPaletteClassificationMode mode = model.observedColorCount() == model.size()
                ? ObservedPaletteClassificationMode.OBSERVED
                : ObservedPaletteClassificationMode.HYBRID_OBSERVED_EXACT;
        return new ObservedPaletteSelection(
                model,
                ObservedPaletteColorMethod.LINEAR_RGB_V1,
                mode,
                EXPECTED_INDEX_REFERENCE_SOURCE,
                evidence
        );
    }

    private List<CaptureMediaPaletteCalibrationSample> finderCalibrationReferences(
            FixedLayoutPlan layoutPlan,
            int dimension,
            List<ModuleEvidence> modules
    ) {
        Map<ModuleCoordinate, ModuleEvidence> modulesByCoordinate = modulesByCoordinate(modules);
        List<CaptureMediaPaletteCalibrationSample> references = new ArrayList<>();
        int tileCount = layoutPlan.profile().rows() * layoutPlan.profile().cols();
        for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
            int tileRow = tileIndex / layoutPlan.profile().cols();
            int tileCol = tileIndex % layoutPlan.profile().cols();
            int baseModuleX = tileCol * dimension;
            int baseModuleY = tileRow * dimension;
            for (FinderWindow window : tileFinderEvaluator.finderWindows(dimension)) {
                for (int row = window.startRow(); row < window.startRow() + window.sizeModules(); row++) {
                    for (int col = window.startCol(); col < window.startCol() + window.sizeModules(); col++) {
                        ModuleEvidence module = modulesByCoordinate.get(new ModuleCoordinate(
                                baseModuleX + col,
                                baseModuleY + row
                        ));
                        if (usablePaletteReference(module)) {
                            references.add(new CaptureMediaPaletteCalibrationSample(
                                    window.expectedColor(),
                                    module.aggregateArgb()
                            ));
                        }
                    }
                }
            }
        }
        return List.copyOf(references);
    }

    private boolean usablePaletteReference(ModuleEvidence module) {
        return module != null
                && module.sampleCount() >= MIN_READABLE_SAMPLE_COUNT
                && module.colorVariance() <= HIGH_VARIANCE_THRESHOLD
                && module.geometryFootprintQuality() >= MIN_READABLE_FOOTPRINT_QUALITY
                && module.status() != ModuleSampleStatus.CLIPPED
                && module.status() != ModuleSampleStatus.OUT_OF_BOUNDS;
    }

    private ObservedPaletteEvidence observedPaletteEvidence(
            CaptureMediaCandidateId candidateId,
            CaptureMediaPaletteModel model,
            List<CaptureMediaPaletteCalibrationSample> references
    ) {
        Map<Integer, List<Integer>> referencesByIndex = referencesByIndex(references);
        List<ObservedPaletteColorEvidence> colors = observedPaletteColors(model, referencesByIndex);
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        Optional<String> fallbackReason = model.fallbackReason();
        if (fallbackReason.isPresent()) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.EXACT_PALETTE_FALLBACK_SELECTED);
            reasonCodes.add(fallbackReasonCode(fallbackReason.orElseThrow()));
            return new ObservedPaletteEvidence(
                    SCHEMA_VERSION,
                    candidateId,
                    ObservedPaletteStatus.FALLBACK_EXACT,
                    EXPECTED_INDEX_REFERENCE_SOURCE,
                    ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                    ObservedPaletteClassificationMode.EXACT,
                    observedCoverageRatio(model),
                    separationScore(model),
                    weakestPalettePair(model),
                    model.confidence(),
                    PALETTE_THRESHOLD_VERSION,
                    fallbackReason,
                    ObservedPaletteSafetyDecision.FALLBACK_EXACT,
                    colors,
                    deduplicated(reasonCodes)
            );
        }

        ObservedPaletteClassificationMode mode = model.observedColorCount() == model.size()
                ? ObservedPaletteClassificationMode.OBSERVED
                : ObservedPaletteClassificationMode.HYBRID_OBSERVED_EXACT;
        reasonCodes.add(mode == ObservedPaletteClassificationMode.OBSERVED
                ? CaptureMediaEvidenceReasonCode.OBSERVED_PALETTE_SELECTED
                : CaptureMediaEvidenceReasonCode.HYBRID_OBSERVED_EXACT_SELECTED);
        return new ObservedPaletteEvidence(
                SCHEMA_VERSION,
                candidateId,
                ObservedPaletteStatus.SAFE_FOR_CLASSIFICATION,
                EXPECTED_INDEX_REFERENCE_SOURCE,
                ObservedPaletteColorMethod.LINEAR_RGB_V1,
                mode,
                observedCoverageRatio(model),
                separationScore(model),
                weakestPalettePair(model),
                model.confidence(),
                PALETTE_THRESHOLD_VERSION,
                Optional.empty(),
                ObservedPaletteSafetyDecision.SAFE_FOR_CLASSIFICATION,
                colors,
                deduplicated(reasonCodes)
        );
    }

    private ObservedPaletteEvidence fallbackObservedPaletteEvidence(
            CaptureMediaCandidateId candidateId,
            CaptureMediaEvidenceReasonCode reasonCode
    ) {
        return new ObservedPaletteEvidence(
                SCHEMA_VERSION,
                candidateId,
                ObservedPaletteStatus.FALLBACK_EXACT,
                EXACT_PALETTE_SOURCE,
                ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                ObservedPaletteClassificationMode.EXACT,
                0.0d,
                0.0d,
                Optional.empty(),
                0.0d,
                PALETTE_THRESHOLD_VERSION,
                Optional.of(reasonCode.name()),
                ObservedPaletteSafetyDecision.FALLBACK_EXACT,
                List.of(),
                deduplicated(List.of(
                        CaptureMediaEvidenceReasonCode.EXACT_PALETTE_FALLBACK_SELECTED,
                        reasonCode
                ))
        );
    }

    private ObservedPaletteEvidence withheldObservedPaletteEvidence(
            CaptureMediaCandidateId candidateId,
            List<ObservedPaletteColorEvidence> colors
    ) {
        return new ObservedPaletteEvidence(
                SCHEMA_VERSION,
                candidateId,
                ObservedPaletteStatus.WITHHELD,
                EXPECTED_INDEX_REFERENCE_SOURCE,
                ObservedPaletteColorMethod.SRGB_EUCLIDEAN_V1,
                ObservedPaletteClassificationMode.EXACT,
                0.0d,
                0.0d,
                Optional.empty(),
                0.0d,
                PALETTE_THRESHOLD_VERSION,
                Optional.of("observed palette classification disabled"),
                ObservedPaletteSafetyDecision.WITHHELD,
                colors,
                deduplicated(List.of(
                        CaptureMediaEvidenceReasonCode.OBSERVED_PALETTE_WITHHELD,
                        CaptureMediaEvidenceReasonCode.EXACT_PALETTE_FALLBACK_SELECTED
                ))
        );
    }

    private List<ObservedPaletteColorEvidence> observedPaletteColors(
            CaptureMediaPaletteModel model,
            Map<Integer, List<Integer>> referencesByIndex
    ) {
        List<ObservedPaletteColorEvidence> colors = new ArrayList<>(model.size());
        for (CaptureMediaPaletteModelColor color : model.colors()) {
            List<Integer> references = referencesByIndex.getOrDefault(color.paletteIndex(), List.of());
            colors.add(new ObservedPaletteColorEvidence(
                    color.paletteIndex(),
                    color.expectedArgb(),
                    color.modelArgb(),
                    color.sampleCount(),
                    maximumDistanceToModel(references, color.modelArgb()),
                    color.maximumRgbDistance(),
                    color.confidence(),
                    centerSource(color)
            ));
        }
        return List.copyOf(colors);
    }

    private ObservedPaletteCenterSource centerSource(CaptureMediaPaletteModelColor color) {
        if (color.calibrated()) {
            return ObservedPaletteCenterSource.OBSERVED;
        }
        return color.modelArgb() == color.expectedArgb()
                ? ObservedPaletteCenterSource.EXACT_FALLBACK
                : ObservedPaletteCenterSource.INFERRED_FROM_BLACK_WHITE;
    }

    private Map<Integer, List<Integer>> referencesByIndex(List<CaptureMediaPaletteCalibrationSample> references) {
        Map<Integer, List<Integer>> values = new LinkedHashMap<>();
        for (CaptureMediaPaletteCalibrationSample reference : references) {
            values.computeIfAbsent(reference.expectedPaletteIndex(), ignored -> new ArrayList<>())
                    .add(reference.observedArgb());
        }
        return Map.copyOf(values);
    }

    private ModuleEvidence sampleModule(
            MediaInputFrame sourceFrame,
            GeometryCandidateEvidence geometryCandidate,
            CaptureMediaPaletteModel paletteModel,
            ObservedPaletteColorMethod colorMethod,
            ObservedPaletteClassificationMode classificationMode,
            String paletteModelSource,
            ProjectedModuleCell cell
    ) {
        List<SourcePoint> samplePoints = latticeProjector.sourceSamplePoints(
                cell,
                geometryCandidate,
                SAMPLE_GRID_SIZE
        );
        List<Integer> sampledArgb = new ArrayList<>(samplePoints.size());
        LinkedHashSet<PixelCoordinate> sampledCoordinates = new LinkedHashSet<>();
        int rejectedPointCount = 0;
        for (SourcePoint point : samplePoints) {
            if (!insideSource(point, sourceFrame.widthPixels(), sourceFrame.heightPixels())) {
                rejectedPointCount++;
                continue;
            }
            int row = (int) Math.round(point.y());
            int col = (int) Math.round(point.x());
            if (row < 0 || row >= sourceFrame.heightPixels() || col < 0 || col >= sourceFrame.widthPixels()) {
                rejectedPointCount++;
                continue;
            }
            PixelCoordinate coordinate = new PixelCoordinate(row, col);
            if (sampledCoordinates.add(coordinate)) {
                sampledArgb.add(sourceFrame.argbPixelAt(row, col));
            }
        }

        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        boolean clipped = rejectedPointCount > 0
                || !insideSource(cell.sourcePolygon(), sourceFrame.widthPixels(), sourceFrame.heightPixels())
                || !insideSource(cell.innerSourcePolygon(), sourceFrame.widthPixels(), sourceFrame.heightPixels());
        FootprintMetrics footprint = footprintMetrics(cell, samplePoints.size(), rejectedPointCount, clipped);
        if (sampledArgb.isEmpty()) {
            return moduleEvidence(
                    cell,
                    0,
                    0x00000000,
                    0.0d,
                    OptionalInt.empty(),
                    new PaletteDistances(0, 0.0d, 0.0d, 0.0d),
                    0.0d,
                    footprint,
                    colorMethod,
                    classificationMode,
                    paletteModelSource,
                    ModuleSampleStatus.OUT_OF_BOUNDS,
                    List.of(CaptureMediaEvidenceReasonCode.MODULE_REGION_OUT_OF_BOUNDS)
            );
        }

        AggregatedColor aggregated = aggregate(sampledArgb);
        PaletteDistances distances = paletteDistances(aggregated.argb(), paletteModel, colorMethod);
        ModuleSampleStatus status;
        OptionalInt assignedPaletteIndex = OptionalInt.empty();
        double moduleConfidence = moduleConfidence(
                sampledArgb.size(),
                aggregated.variance(),
                distances,
                footprint,
                colorMethod
        );
        if (clipped) {
            status = ModuleSampleStatus.CLIPPED;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_REGION_CLIPPED);
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_FOOTPRINT_CLIPPED);
        } else if (sampledArgb.size() < MIN_READABLE_SAMPLE_COUNT) {
            status = ModuleSampleStatus.UNREADABLE;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.INSUFFICIENT_SAMPLE_COUNT);
        } else if (footprint.quality() < MIN_READABLE_FOOTPRINT_QUALITY) {
            status = ModuleSampleStatus.UNREADABLE;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_FOOTPRINT_QUALITY_BELOW_THRESHOLD);
            if (footprint.minimumEdgePx() < GOOD_MINIMUM_FOOTPRINT_EDGE_PX
                    || footprint.innerAreaPx() < GOOD_INNER_FOOTPRINT_AREA_PX) {
                reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_FOOTPRINT_TOO_SMALL);
            }
        } else if (aggregated.variance() > HIGH_VARIANCE_THRESHOLD) {
            status = ModuleSampleStatus.AMBIGUOUS;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.HIGH_COLOR_VARIANCE);
        } else if (paletteSampleStatus(distances.bestDistance(), colorMethod) == CaptureMediaPaletteSampleStatus.REJECTED) {
            status = ModuleSampleStatus.UNREADABLE;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_COLOR_MARGIN);
        } else if (paletteSampleStatus(distances.bestDistance(), colorMethod) == CaptureMediaPaletteSampleStatus.LOW_CONFIDENCE
                || distances.margin() < lowDistanceMarginThreshold(colorMethod)) {
            status = ModuleSampleStatus.AMBIGUOUS;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_COLOR_MARGIN);
        } else {
            status = ModuleSampleStatus.READABLE;
            assignedPaletteIndex = OptionalInt.of(distances.paletteIndex());
        }
        if (status == ModuleSampleStatus.READABLE && moduleConfidence < MIN_READABLE_MODULE_CONFIDENCE) {
            status = ModuleSampleStatus.AMBIGUOUS;
            assignedPaletteIndex = OptionalInt.empty();
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_CONFIDENCE_BELOW_THRESHOLD);
        }

        return moduleEvidence(
                cell,
                sampledArgb.size(),
                aggregated.argb(),
                aggregated.variance(),
                assignedPaletteIndex,
                distances,
                moduleConfidence,
                footprint,
                colorMethod,
                classificationMode,
                paletteModelSource,
                status,
                deduplicated(reasonCodes)
        );
    }

    private ModuleEvidence moduleEvidence(
            ProjectedModuleCell cell,
            int sampleCount,
            int aggregateArgb,
            double colorVariance,
            OptionalInt assignedPaletteIndex,
            PaletteDistances distances,
            double moduleConfidence,
            FootprintMetrics footprint,
            ObservedPaletteColorMethod colorMethod,
            ObservedPaletteClassificationMode classificationMode,
            String paletteModelSource,
            ModuleSampleStatus status,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {
        return new ModuleEvidence(
                cell.moduleX(),
                cell.moduleY(),
                cell.canonicalPolygon(),
                cell.sourcePolygon(),
                cell.innerSourcePolygon(),
                sampleCount,
                aggregateArgb,
                colorVariance,
                assignedPaletteIndex,
                distances.bestDistance(),
                distances.secondBestDistance(),
                distances.margin(),
                moduleConfidence,
                footprint.quality(),
                footprint.sourceAreaPx(),
                footprint.innerAreaPx(),
                footprint.minimumEdgePx(),
                footprint.aspectRatio(),
                footprint.clippedFraction(),
                colorMethod,
                classificationMode,
                paletteModelSource,
                status,
                reasonCodes
        );
    }

    private VariantValidation validateVariant(
            FixedLayoutPlan layoutPlan,
            int sideVersion,
            GeometryCandidateEvidence geometryCandidate,
            double centralScale,
            int variantRank,
            List<ModuleEvidence> modules
    ) {
        int dimension = tileCodecProfile.dimensionForSideVersion(sideVersion);
        int expectedTiles = layoutPlan.profile().rows() * layoutPlan.profile().cols();
        Map<ModuleCoordinate, ModuleEvidence> modulesByCoordinate = modulesByCoordinate(modules);
        List<TilePayload> acceptedPayloads = new ArrayList<>();
        List<FailureStage> failureStages = new ArrayList<>();
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        List<WeakTileEvidence> weakTileEvidence = new ArrayList<>(expectedTiles);
        int attemptCount = 0;
        for (int tileIndex = 0; tileIndex < expectedTiles; tileIndex++) {
            Optional<LogicalTile> candidate = logicalTileCandidate(
                    layoutPlan,
                    tileIndex,
                    dimension,
                    geometryCandidate,
                    centralScale,
                    variantRank,
                    modulesByCoordinate
            );
            boolean attempted = false;
            boolean accepted = false;
            List<String> tileFailureStages = new ArrayList<>();
            List<CaptureMediaEvidenceReasonCode> tileReasonCodes = new ArrayList<>();
            if (candidate.isEmpty()) {
                tileFailureStages.add("MODULE_EVIDENCE");
                tileReasonCodes.add(CaptureMediaEvidenceReasonCode.TILE_DECODE_NOT_ATTEMPTED);
                weakTileEvidence.add(weakTileEvidence(
                        layoutPlan,
                        tileIndex,
                        dimension,
                        modulesByCoordinate,
                        attempted,
                        accepted,
                        tileFailureStages,
                        tileReasonCodes
                ));
                continue;
            }
            attemptCount++;
            attempted = true;
            ValidationAttempt attempt = logicalTileValidator.validate(
                    layoutPlan,
                    tileIndex,
                    candidate.orElseThrow()
            );
            if (attempt.payload().isPresent()) {
                acceptedPayloads.add(attempt.payload().orElseThrow());
                accepted = true;
                weakTileEvidence.add(weakTileEvidence(
                        layoutPlan,
                        tileIndex,
                        dimension,
                        modulesByCoordinate,
                        attempted,
                        accepted,
                        tileFailureStages,
                        tileReasonCodes
                ));
                continue;
            }
            FailureStage stage = attempt.failureStage().orElseThrow();
            failureStages.add(stage);
            CaptureMediaEvidenceReasonCode stageReasonCode = reasonCode(stage);
            reasonCodes.add(stageReasonCode);
            tileFailureStages.add(stage.name());
            tileReasonCodes.add(stageReasonCode);
            weakTileEvidence.add(weakTileEvidence(
                    layoutPlan,
                    tileIndex,
                    dimension,
                    modulesByCoordinate,
                    attempted,
                    accepted,
                    tileFailureStages,
                    tileReasonCodes
            ));
        }
        if (attemptCount == 0) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.TILE_DECODE_NOT_ATTEMPTED);
        }
        return new VariantValidation(
                attemptCount,
                acceptedPayloads,
                weakTileEvidence,
                deduplicatedStages(failureStages),
                deduplicated(reasonCodes)
        );
    }

    private WeakTileEvidence weakTileEvidence(
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            int dimension,
            Map<ModuleCoordinate, ModuleEvidence> modulesByCoordinate,
            boolean tileDecodeAttempted,
            boolean acceptedPayload,
            List<String> failureStages,
            List<CaptureMediaEvidenceReasonCode> tileReasonCodes
    ) {
        int tileRow = tileIndex / layoutPlan.profile().cols();
        int tileCol = tileIndex % layoutPlan.profile().cols();
        int baseModuleX = tileCol * dimension;
        int baseModuleY = tileRow * dimension;
        int moduleCount = dimension * dimension;
        int weakCount = 0;
        int ambiguousCount = 0;
        int unreadableCount = 0;
        int clippedCount = 0;
        int outOfBoundsCount = 0;
        int poorFootprintCount = 0;
        double minimumConfidence = 1.0d;
        double minimumFootprintQuality = 1.0d;
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>(tileReasonCodes);
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                ModuleEvidence module = modulesByCoordinate.get(new ModuleCoordinate(
                        baseModuleX + col,
                        baseModuleY + row
                ));
                if (module == null) {
                    weakCount++;
                    unreadableCount++;
                    reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_REGION_OUT_OF_BOUNDS);
                    minimumConfidence = 0.0d;
                    minimumFootprintQuality = 0.0d;
                    continue;
                }
                minimumConfidence = Math.min(minimumConfidence, module.moduleConfidence());
                minimumFootprintQuality = Math.min(minimumFootprintQuality, module.geometryFootprintQuality());
                if (weakForTile(module)) {
                    weakCount++;
                    reasonCodes.addAll(module.reasonCodes());
                }
                if (module.status() == ModuleSampleStatus.AMBIGUOUS) {
                    ambiguousCount++;
                } else if (module.status() == ModuleSampleStatus.UNREADABLE) {
                    unreadableCount++;
                } else if (module.status() == ModuleSampleStatus.CLIPPED) {
                    clippedCount++;
                } else if (module.status() == ModuleSampleStatus.OUT_OF_BOUNDS) {
                    outOfBoundsCount++;
                }
                if (module.geometryFootprintQuality() < MIN_READABLE_FOOTPRINT_QUALITY) {
                    poorFootprintCount++;
                    reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_FOOTPRINT_QUALITY_BELOW_THRESHOLD);
                }
            }
        }
        return new WeakTileEvidence(
                tileIndex,
                baseModuleX,
                baseModuleY,
                dimension,
                dimension,
                moduleCount,
                weakCount,
                ambiguousCount,
                unreadableCount,
                clippedCount,
                outOfBoundsCount,
                poorFootprintCount,
                (double) weakCount / (double) moduleCount,
                minimumConfidence,
                minimumFootprintQuality,
                tileDecodeAttempted,
                acceptedPayload,
                failureStages.isEmpty() && !acceptedPayload ? List.of("NONE") : failureStages,
                deduplicated(reasonCodes)
        );
    }

    private Map<ModuleCoordinate, ModuleEvidence> modulesByCoordinate(List<ModuleEvidence> modules) {
        Map<ModuleCoordinate, ModuleEvidence> byCoordinate = new LinkedHashMap<>();
        for (ModuleEvidence module : modules) {
            ModuleCoordinate coordinate = new ModuleCoordinate(module.moduleX(), module.moduleY());
            if (byCoordinate.put(coordinate, module) != null) {
                throw new IllegalArgumentException("modules must not contain duplicate coordinates");
            }
        }
        return Map.copyOf(byCoordinate);
    }

    private Optional<LogicalTile> logicalTileCandidate(
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            int dimension,
            GeometryCandidateEvidence geometryCandidate,
            double centralScale,
            int variantRank,
            Map<ModuleCoordinate, ModuleEvidence> modulesByCoordinate
    ) {
        int tileRow = tileIndex / layoutPlan.profile().cols();
        int tileCol = tileIndex % layoutPlan.profile().cols();
        int baseModuleX = tileCol * dimension;
        int baseModuleY = tileRow * dimension;
        List<Integer> moduleColors = new ArrayList<>(dimension * dimension);
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                ModuleEvidence module = modulesByCoordinate.get(new ModuleCoordinate(
                        baseModuleX + col,
                        baseModuleY + row
                ));
                if (module == null
                        || module.status() != ModuleSampleStatus.READABLE
                        || module.assignedPaletteIndex().isEmpty()
                        || module.moduleConfidence() < MIN_READABLE_MODULE_CONFIDENCE
                        || module.geometryFootprintQuality() < MIN_READABLE_FOOTPRINT_QUALITY) {
                    return Optional.empty();
                }
                moduleColors.add(module.assignedPaletteIndex().orElseThrow());
            }
        }
        return Optional.of(new LogicalTile(
                dimension,
                dimension,
                tileCodecProfile.quietZoneModules(),
                tileCodecProfile.profileId(),
                moduleColors,
                Map.of(
                        "sourceSpace.geometryCandidateId",
                        geometryCandidate.candidateId().geometryCandidateId().orElseThrow(),
                        "sourceSpace.tileIndex",
                        Integer.toString(tileIndex),
                        "sourceSpace.centralScalePercent",
                        Integer.toString(scalePercent(centralScale)),
                        "sourceSpace.variantRank",
                        Integer.toString(variantRank)
                )
        ));
    }

    private CaptureMediaEvidenceReasonCode reasonCode(FailureStage stage) {
        return switch (stage) {
            case ENVELOPE_VALIDATION -> CaptureMediaEvidenceReasonCode.PAYLOAD_CRC_FAILED;
            case TILE_DECODE, SLOT_VALIDATION, UNEXPECTED ->
                    CaptureMediaEvidenceReasonCode.DOWNSTREAM_TILE_VALIDATION_FAILED;
        };
    }

    private CaptureMediaEvidenceReasonCode fallbackReasonCode(String fallbackReason) {
        String normalized = fallbackReason.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("insufficient")) {
            return CaptureMediaEvidenceReasonCode.INSUFFICIENT_EXPECTED_INDEX_OBSERVATIONS;
        }
        if (normalized.contains("contradictory")) {
            return CaptureMediaEvidenceReasonCode.CONTRADICTORY_PALETTE_OBSERVATIONS;
        }
        if (normalized.contains("confidence")) {
            return CaptureMediaEvidenceReasonCode.OBSERVED_CLASSIFICATION_WITHHELD;
        }
        return CaptureMediaEvidenceReasonCode.PALETTE_UNAVAILABLE;
    }

    private ModuleSamplingEvidence unavailableEvidence(
            NormalizedCaptureFrame normalizedFrame,
            GeometryFitEvidence geometryEvidence,
            Optional<GeometryCandidateEvidence> geometryCandidate,
            Optional<LayoutProfile> layoutProfile,
            int sideVersion,
            ModuleSamplingStatus status,
            CaptureMediaEvidenceReasonCode reasonCode
    ) {
        CaptureMediaCandidateId geometryId = geometryCandidate
                .map(GeometryCandidateEvidence::candidateId)
                .orElseGet(() -> placeholderGeometryCandidateId(geometryEvidence));
        int dimension = tileCodecProfile.dimensionForSideVersion(sideVersion);
        int moduleWidth = layoutProfile.map(profile -> profile.cols() * dimension).orElse(1);
        int moduleHeight = layoutProfile.map(profile -> profile.rows() * dimension).orElse(1);
        String geometryCandidateId = geometryId.geometryCandidateId().orElseThrow();
        CaptureMediaCandidateId samplingId = CaptureMediaCandidateId.samplingCandidate(
                geometryId,
                scalePercent(CENTRAL_SCALES.get(0)),
                1
        );
        return new ModuleSamplingEvidence(
                SCHEMA_VERSION,
                samplingId,
                status,
                geometryCandidateId,
                layoutProfile.map(LayoutProfile::profileId).orElse(normalizedFrame.layoutProfileId()),
                moduleWidth,
                moduleHeight,
                geometryCandidate
                        .map(GeometryCandidateEvidence::canonicalCoordinateSystem)
                        .orElse("pattern-feature-canonical-pixels:" + normalizedFrame.layoutProfileId()),
                geometryCandidate
                        .map(candidate -> "geometry-fit:" + candidate.fitModelType().name())
                        .orElse("not-available"),
                CENTRAL_SCALES.get(0),
                ModuleSamplingAggregationMethod.MEDIAN_RGB,
                CENTRAL_REGION_POLICY,
                moduleWidth * moduleHeight,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                0,
                0,
                fallbackObservedPaletteEvidence(samplingId, reasonCode),
                false,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                deduplicated(List.of(
                        reasonCode,
                        CaptureMediaEvidenceReasonCode.TILE_DECODE_NOT_ATTEMPTED
                ))
        );
    }

    private CaptureMediaCandidateId placeholderGeometryCandidateId(GeometryFitEvidence geometryEvidence) {
        CaptureMediaCandidateId base = geometryEvidence == null
                ? null
                : geometryEvidence.candidateId();
        if (base == null) {
            throw new IllegalArgumentException("geometryEvidence must be provided when no geometry candidate exists");
        }
        if (base.geometryCandidateId().isPresent()) {
            return base;
        }
        CaptureMediaCandidateId pattern = base.patternEvidenceId().isPresent()
                ? base
                : CaptureMediaCandidateId.patternEvidence(base);
        return CaptureMediaCandidateId.geometryCandidate(pattern, 1);
    }

    private Optional<LayoutProfile> layoutProfile(NormalizedCaptureFrame normalizedFrame) {
        List<LayoutProfile> matches = layoutCatalog.profiles()
                .stream()
                .filter(profile -> profile.profileId().equals(normalizedFrame.layoutProfileId()))
                .filter(profile -> profile.frameWidthPx() == normalizedFrame.normalizedWidthPixels())
                .filter(profile -> profile.frameHeightPx() == normalizedFrame.normalizedHeightPixels())
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private int selectedSideVersion() {
        return tileCodecProfile.minSideVersion();
    }

    private List<GeometryCandidateEvidence> acceptedGeometryCandidates(GeometryFitEvidence evidence) {
        return evidence.retainedCandidates()
                .stream()
                .filter(candidate -> candidate.status() == GeometryFitStatus.ACCEPTED)
                .filter(GeometryCandidateEvidence::invertible)
                .sorted(Comparator.comparingInt(GeometryCandidateEvidence::rank))
                .limit(CaptureMediaCandidateId.MAX_GEOMETRY_CANDIDATE_RANK)
                .toList();
    }

    private boolean sourcePixelsAvailable(MediaInputFrame sourceFrame) {
        try {
            sourceFrame.argbPixelAt(0, 0);
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private void requireSameSource(MediaInputFrame sourceFrame, NormalizedCaptureFrame normalizedFrame) {
        if (!sourceFrame.sourceId().equals(normalizedFrame.sourceId())
                || sourceFrame.callerOrder() != normalizedFrame.callerOrder()
                || sourceFrame.widthPixels() != normalizedFrame.originalWidthPixels()
                || sourceFrame.heightPixels() != normalizedFrame.originalHeightPixels()
                || !sourceFrame.pixelSha256().equals(normalizedFrame.pixelSha256())) {
            throw new IllegalArgumentException("sourceFrame must match normalizedFrame source metadata");
        }
    }

    private boolean insideSource(SourcePolygon polygon, int widthPixels, int heightPixels) {
        return polygon.vertices()
                .stream()
                .allMatch(point -> insideSource(point, widthPixels, heightPixels));
    }

    private boolean insideSource(SourcePoint point, int widthPixels, int heightPixels) {
        return point.x() >= 0.0d
                && point.y() >= 0.0d
                && point.x() <= widthPixels - 1.0d
                && point.y() <= heightPixels - 1.0d;
    }

    private boolean weakForTile(ModuleEvidence module) {
        return module.status() != ModuleSampleStatus.READABLE
                || module.assignedPaletteIndex().isEmpty()
                || module.moduleConfidence() < MIN_READABLE_MODULE_CONFIDENCE
                || module.geometryFootprintQuality() < MIN_READABLE_FOOTPRINT_QUALITY;
    }

    private AggregatedColor aggregate(List<Integer> argbValues) {
        int[] reds = new int[argbValues.size()];
        int[] greens = new int[argbValues.size()];
        int[] blues = new int[argbValues.size()];
        for (int index = 0; index < argbValues.size(); index++) {
            int argb = argbValues.get(index);
            reds[index] = red(argb);
            greens[index] = green(argb);
            blues[index] = blue(argb);
        }
        int aggregateArgb = 0xFF000000
                | (median(reds) << 16)
                | (median(greens) << 8)
                | median(blues);
        double variance = 0.0d;
        for (int argb : argbValues) {
            variance += squaredRgbDistance(argb, aggregateArgb);
        }
        return new AggregatedColor(aggregateArgb, variance / argbValues.size());
    }

    private FootprintMetrics footprintMetrics(
            ProjectedModuleCell cell,
            int projectedSampleCount,
            int rejectedPointCount,
            boolean clipped
    ) {
        double sourceArea = polygonArea(cell.sourcePolygon());
        double innerArea = polygonArea(cell.innerSourcePolygon());
        double minimumEdge = minimumEdge(cell.innerSourcePolygon());
        double maximumEdge = maximumEdge(cell.innerSourcePolygon());
        double aspectRatio = minimumEdge <= 0.0d ? 0.0d : maximumEdge / minimumEdge;
        double rejectedFraction = projectedSampleCount == 0
                ? 1.0d
                : Math.min(1.0d, Math.max(0.0d, rejectedPointCount / (double) projectedSampleCount));
        double clippedFraction = clipped && rejectedFraction == 0.0d ? 1.0d : rejectedFraction;
        double shapeQuality = aspectRatio <= 0.0d ? 0.0d : clamp01(1.0d / Math.max(aspectRatio, 1.0d));
        double areaQuality = clamp01(innerArea / GOOD_INNER_FOOTPRINT_AREA_PX);
        double edgeQuality = clamp01(minimumEdge / GOOD_MINIMUM_FOOTPRINT_EDGE_PX);
        double clipQuality = clamp01(1.0d - clippedFraction);
        double quality = Math.min(Math.min(areaQuality, edgeQuality), Math.min(clipQuality, shapeQuality));
        return new FootprintMetrics(
                sourceArea,
                innerArea,
                minimumEdge,
                aspectRatio,
                clippedFraction,
                quality
        );
    }

    private double moduleConfidence(
            int sampleCount,
            double colorVariance,
            PaletteDistances distances,
            FootprintMetrics footprint,
            ObservedPaletteColorMethod colorMethod
    ) {
        double marginQuality = clamp01(distances.margin() / goodColorMargin(colorMethod));
        double varianceQuality = clamp01(1.0d - (colorVariance / HIGH_VARIANCE_THRESHOLD));
        double sampleQuality = clamp01(sampleCount / (double) GOOD_SAMPLE_COUNT);
        return Math.min(Math.min(marginQuality, varianceQuality), Math.min(sampleQuality, footprint.quality()));
    }

    private PaletteDistances paletteDistances(
            int argb,
            CaptureMediaPaletteModel paletteModel,
            ObservedPaletteColorMethod colorMethod
    ) {
        List<PaletteDistance> distances = paletteModel.colors()
                .stream()
                .map(color -> new PaletteDistance(
                        color.paletteIndex(),
                        CaptureMediaColorDistance.distance(argb, color.modelArgb(), colorMethod)
                ))
                .sorted(Comparator.comparingDouble(PaletteDistance::distance)
                        .thenComparingInt(PaletteDistance::paletteIndex))
                .toList();
        PaletteDistance best = distances.get(0);
        PaletteDistance second = distances.get(1);
        return new PaletteDistances(
                best.paletteIndex(),
                best.distance(),
                second.distance(),
                Math.max(0.0d, second.distance() - best.distance())
        );
    }

    private CaptureMediaPaletteSampleStatus paletteSampleStatus(
            double distance,
            ObservedPaletteColorMethod colorMethod
    ) {
        if (distance <= lowConfidenceDistance(colorMethod)) {
            return CaptureMediaPaletteSampleStatus.TOLERANT;
        }
        if (distance <= maxAcceptedDistance(colorMethod)) {
            return CaptureMediaPaletteSampleStatus.LOW_CONFIDENCE;
        }
        return CaptureMediaPaletteSampleStatus.REJECTED;
    }

    private int weakModuleCount(List<ModuleEvidence> modules) {
        int count = 0;
        for (ModuleEvidence module : modules) {
            if (module.sampleCount() > 0
                    && (module.status() != ModuleSampleStatus.READABLE
                    || module.moduleConfidence() < MIN_READABLE_MODULE_CONFIDENCE
                    || module.geometryFootprintQuality() < MIN_READABLE_FOOTPRINT_QUALITY)) {
                count++;
            }
        }
        return count;
    }

    private int poorFootprintModuleCount(List<ModuleEvidence> modules) {
        int count = 0;
        for (ModuleEvidence module : modules) {
            if (module.geometryFootprintQuality() < MIN_READABLE_FOOTPRINT_QUALITY) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Double> summary(List<Double> values) {
        if (values.isEmpty()) {
            return Map.of();
        }
        List<Double> sorted = values.stream().sorted().toList();
        double sum = 0.0d;
        for (double value : sorted) {
            sum += value;
        }
        Map<String, Double> summary = new LinkedHashMap<>();
        summary.put("count", (double) sorted.size());
        summary.put("min", sorted.get(0));
        summary.put("median", sorted.get(sorted.size() / 2));
        summary.put("mean", sum / sorted.size());
        summary.put("max", sorted.get(sorted.size() - 1));
        return Map.copyOf(summary);
    }

    private List<CaptureMediaEvidenceReasonCode> aggregateReasonCodes(List<ModuleEvidence> modules) {
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        for (ModuleEvidence module : modules) {
            reasonCodes.addAll(module.reasonCodes());
        }
        return reasonCodes;
    }

    private ModuleSamplingStatus samplingStatus(Counts counts) {
        if (counts.sampled() == 0 || counts.outOfBounds() == counts.total()) {
            return ModuleSamplingStatus.REJECTED;
        }
        if (counts.readable() == counts.total()) {
            return ModuleSamplingStatus.SAMPLED;
        }
        if (counts.readable() > 0) {
            return ModuleSamplingStatus.PARTIAL;
        }
        return ModuleSamplingStatus.REJECTED;
    }

    private int scalePercent(double scale) {
        return (int) Math.round(scale * 100.0d);
    }

    private double rgbDistance(int firstArgb, int secondArgb) {
        return Math.sqrt(squaredRgbDistance(firstArgb, secondArgb));
    }

    private double maximumDistanceToModel(List<Integer> references, int modelArgb) {
        double maximum = 0.0d;
        for (int reference : references) {
            maximum = Math.max(maximum, rgbDistance(reference, modelArgb));
        }
        return maximum;
    }

    private double observedCoverageRatio(CaptureMediaPaletteModel model) {
        return model.size() == 0 ? 0.0d : model.observedColorCount() / (double) model.size();
    }

    private double separationScore(CaptureMediaPaletteModel model) {
        List<CaptureMediaPaletteModelColor> observed = model.observedColors();
        if (observed.size() < 2) {
            return 0.0d;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int first = 0; first < observed.size(); first++) {
            for (int second = first + 1; second < observed.size(); second++) {
                minimum = Math.min(
                        minimum,
                        rgbDistance(observed.get(first).modelArgb(), observed.get(second).modelArgb())
                );
            }
        }
        return Double.isFinite(minimum) ? clamp01(minimum / 96.0d) : 0.0d;
    }

    private Optional<String> weakestPalettePair(CaptureMediaPaletteModel model) {
        List<CaptureMediaPaletteModelColor> observed = model.observedColors();
        if (observed.size() < 2) {
            return Optional.empty();
        }
        double minimum = Double.POSITIVE_INFINITY;
        String pair = null;
        for (int first = 0; first < observed.size(); first++) {
            for (int second = first + 1; second < observed.size(); second++) {
                double distance = rgbDistance(observed.get(first).modelArgb(), observed.get(second).modelArgb());
                if (distance < minimum) {
                    minimum = distance;
                    pair = observed.get(first).paletteIndex() + "-" + observed.get(second).paletteIndex();
                }
            }
        }
        return Optional.ofNullable(pair);
    }

    private double lowConfidenceDistance(ObservedPaletteColorMethod colorMethod) {
        return switch (colorMethod) {
            case SRGB_EUCLIDEAN_V1 -> 24.0d;
            case LINEAR_RGB_V1 -> 0.08d;
            case CIE_LAB_V1 -> 8.0d;
        };
    }

    private double maxAcceptedDistance(ObservedPaletteColorMethod colorMethod) {
        return switch (colorMethod) {
            case SRGB_EUCLIDEAN_V1 -> 64.0d;
            case LINEAR_RGB_V1 -> 0.20d;
            case CIE_LAB_V1 -> 18.0d;
        };
    }

    private double lowDistanceMarginThreshold(ObservedPaletteColorMethod colorMethod) {
        return switch (colorMethod) {
            case SRGB_EUCLIDEAN_V1 -> LOW_DISTANCE_MARGIN_THRESHOLD;
            case LINEAR_RGB_V1 -> 0.05d;
            case CIE_LAB_V1 -> 5.0d;
        };
    }

    private double goodColorMargin(ObservedPaletteColorMethod colorMethod) {
        return switch (colorMethod) {
            case SRGB_EUCLIDEAN_V1 -> GOOD_COLOR_MARGIN;
            case LINEAR_RGB_V1 -> 0.15d;
            case CIE_LAB_V1 -> 16.0d;
        };
    }

    private double polygonArea(SourcePolygon polygon) {
        List<SourcePoint> vertices = polygon.vertices();
        double sum = 0.0d;
        for (int index = 0; index < vertices.size(); index++) {
            SourcePoint current = vertices.get(index);
            SourcePoint next = vertices.get((index + 1) % vertices.size());
            sum += (current.x() * next.y()) - (next.x() * current.y());
        }
        return Math.abs(sum) / 2.0d;
    }

    private double minimumEdge(SourcePolygon polygon) {
        List<SourcePoint> vertices = polygon.vertices();
        double minimum = Double.POSITIVE_INFINITY;
        for (int index = 0; index < vertices.size(); index++) {
            minimum = Math.min(minimum, distance(vertices.get(index), vertices.get((index + 1) % vertices.size())));
        }
        return Double.isFinite(minimum) ? minimum : 0.0d;
    }

    private double maximumEdge(SourcePolygon polygon) {
        List<SourcePoint> vertices = polygon.vertices();
        double maximum = 0.0d;
        for (int index = 0; index < vertices.size(); index++) {
            maximum = Math.max(maximum, distance(vertices.get(index), vertices.get((index + 1) % vertices.size())));
        }
        return maximum;
    }

    private double distance(SourcePoint first, SourcePoint second) {
        double deltaX = first.x() - second.x();
        double deltaY = first.y() - second.y();
        return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
    }

    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }

    private double squaredRgbDistance(int firstArgb, int secondArgb) {
        int redDelta = red(firstArgb) - red(secondArgb);
        int greenDelta = green(firstArgb) - green(secondArgb);
        int blueDelta = blue(firstArgb) - blue(secondArgb);
        return (redDelta * redDelta) + (greenDelta * greenDelta) + (blueDelta * blueDelta);
    }

    private int median(int[] values) {
        int[] sorted = java.util.Arrays.copyOf(values, values.length);
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private int blue(int argb) {
        return argb & 0xFF;
    }

    private List<CaptureMediaEvidenceReasonCode> deduplicated(List<CaptureMediaEvidenceReasonCode> reasonCodes) {
        return List.copyOf(new LinkedHashSet<>(reasonCodes));
    }

    private List<String> deduplicatedStages(List<FailureStage> stages) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (FailureStage stage : stages) {
            names.add(stage.name());
        }
        return List.copyOf(names);
    }

    /**
     * Source-space sampling validation result for one normalized candidate.
     *
     * @param evidence bounded source-space sampling evidence in deterministic variant order
     * @param acceptedPayloads accepted tile payloads from the first variant that passed all existing gates
     * @param localRefinementEvidence local-refinement evidence produced while evaluating this sample
     */
    public record SourceSpaceValidationSample(
            List<ModuleSamplingEvidence> evidence,
            List<TilePayload> acceptedPayloads,
            List<LocalRefinementEvidence> localRefinementEvidence
    ) {

        /**
         * Creates an immutable source-space validation sample without local-refinement evidence.
         *
         * @param evidence sampling evidence
         * @param acceptedPayloads accepted payloads
         */
        public SourceSpaceValidationSample(
                List<ModuleSamplingEvidence> evidence,
                List<TilePayload> acceptedPayloads
        ) {
            this(evidence, acceptedPayloads, List.of());
        }

        /**
         * Creates an immutable source-space validation sample.
         */
        public SourceSpaceValidationSample {
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
            acceptedPayloads = List.copyOf(Objects.requireNonNull(
                    acceptedPayloads,
                    "acceptedPayloads must not be null"
            ));
            localRefinementEvidence = List.copyOf(Objects.requireNonNull(
                    localRefinementEvidence,
                    "localRefinementEvidence must not be null"
            ));
            if (evidence.stream().anyMatch(Objects::isNull)
                    || acceptedPayloads.stream().anyMatch(Objects::isNull)
                    || localRefinementEvidence.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("source-space validation sample values must not contain nulls");
            }
        }

        private static SourceSpaceValidationSample of(List<ModuleSamplingEvidence> evidence) {
            return new SourceSpaceValidationSample(evidence, List.of());
        }

        private static SourceSpaceValidationSample of(
                List<ModuleSamplingEvidence> evidence,
                List<LocalRefinementEvidence> localRefinementEvidence
        ) {
            return new SourceSpaceValidationSample(evidence, List.of(), localRefinementEvidence);
        }
    }

    private record VariantSample(ModuleSamplingEvidence evidence, List<TilePayload> acceptedPayloads) {

        private VariantSample {
            Objects.requireNonNull(evidence, "evidence must not be null");
            acceptedPayloads = List.copyOf(Objects.requireNonNull(
                    acceptedPayloads,
                    "acceptedPayloads must not be null"
            ));
            if (acceptedPayloads.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("acceptedPayloads must not contain null values");
            }
        }

        private static VariantSample evidenceOnly(ModuleSamplingEvidence evidence) {
            return new VariantSample(evidence, List.of());
        }
    }

    private record VariantValidation(
            int attemptCount,
            List<TilePayload> acceptedPayloads,
            List<WeakTileEvidence> weakTileEvidence,
            List<String> failureStages,
            List<CaptureMediaEvidenceReasonCode> reasonCodes
    ) {

        private VariantValidation {
            if (attemptCount < 0) {
                throw new IllegalArgumentException("attemptCount must be non-negative");
            }
            acceptedPayloads = List.copyOf(Objects.requireNonNull(
                    acceptedPayloads,
                    "acceptedPayloads must not be null"
            ));
            weakTileEvidence = List.copyOf(Objects.requireNonNull(
                    weakTileEvidence,
                    "weakTileEvidence must not be null"
            ));
            failureStages = List.copyOf(Objects.requireNonNull(failureStages, "failureStages must not be null"));
            reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes must not be null"));
            if (acceptedPayloads.stream().anyMatch(Objects::isNull)
                    || weakTileEvidence.stream().anyMatch(Objects::isNull)
                    || failureStages.stream().anyMatch(stage -> stage == null || stage.isBlank())
                    || reasonCodes.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("variant validation values must not contain nulls or blanks");
            }
            if (acceptedPayloads.size() > attemptCount || failureStages.size() > attemptCount) {
                throw new IllegalArgumentException("variant validation counts must not exceed attemptCount");
            }
        }

        private boolean tileDecodeAttempted() {
            return attemptCount > 0;
        }
    }

    private record ModuleCoordinate(int moduleX, int moduleY) {
    }

    private record PixelCoordinate(int row, int col) {
    }

    private record AggregatedColor(int argb, double variance) {
    }

    private record FootprintMetrics(
            double sourceAreaPx,
            double innerAreaPx,
            double minimumEdgePx,
            double aspectRatio,
            double clippedFraction,
            double quality
    ) {
    }

    private record PaletteDistance(int paletteIndex, double distance) {
    }

    private record PaletteDistances(int paletteIndex, double bestDistance, double secondBestDistance, double margin) {
    }

    private record ObservedPaletteSelection(
            CaptureMediaPaletteModel paletteModel,
            ObservedPaletteColorMethod colorMethod,
            ObservedPaletteClassificationMode classificationMode,
            String paletteModelSource,
            ObservedPaletteEvidence evidence
    ) {

        private ObservedPaletteSelection {
            Objects.requireNonNull(paletteModel, "paletteModel must not be null");
            Objects.requireNonNull(colorMethod, "colorMethod must not be null");
            Objects.requireNonNull(classificationMode, "classificationMode must not be null");
            paletteModelSource = Objects.requireNonNull(paletteModelSource, "paletteModelSource must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
        }
    }

    private record Counts(
            int total,
            int sampled,
            int readable,
            int ambiguous,
            int unreadable,
            int clipped,
            int outOfBounds
    ) {

        private static Counts from(List<ModuleEvidence> modules) {
            int sampled = 0;
            int readable = 0;
            int ambiguous = 0;
            int unreadable = 0;
            int clipped = 0;
            int outOfBounds = 0;
            for (ModuleEvidence module : modules) {
                if (module.sampleCount() > 0) {
                    sampled++;
                }
                switch (module.status()) {
                    case READABLE -> readable++;
                    case AMBIGUOUS -> ambiguous++;
                    case UNREADABLE -> unreadable++;
                    case CLIPPED -> clipped++;
                    case OUT_OF_BOUNDS -> outOfBounds++;
                }
            }
            return new Counts(modules.size(), sampled, readable, ambiguous, unreadable, clipped, outOfBounds);
        }
    }
}
