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
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;
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
    private static final double HIGH_VARIANCE_THRESHOLD = 900.0d;
    private static final double LOW_DISTANCE_MARGIN_THRESHOLD = 16.0d;
    private static final String CENTRAL_REGION_POLICY = "canonical-center-shrink-v1:grid7:min9";

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final TileCodecProfile tileCodecProfile;
    private final ModuleLatticeProjector latticeProjector;
    private final CaptureMediaPaletteSampler paletteSampler;
    private final FixedLayoutPlanner layoutPlanner;
    private final CaptureMediaLogicalTileValidator logicalTileValidator;
    private final boolean sourceSpaceSamplingEnabled;
    private final boolean localRefinementEnabled;
    private final CaptureMediaLocalLatticeRefiner localLatticeRefiner;

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
                new CaptureMediaLocalLatticeRefiner()
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
                new CaptureMediaLocalLatticeRefiner()
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
        this(
                new CaptureRenderedLayoutCatalog(),
                TileCodecProfiles.balancedV1(),
                new ModuleLatticeProjector(),
                new CaptureMediaPaletteSampler(),
                new FixedLayoutPlanner(),
                new CaptureMediaLogicalTileValidator(),
                sourceSpaceSamplingEnabled,
                localRefinementEnabled,
                new CaptureMediaLocalLatticeRefiner()
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
                new CaptureMediaLocalLatticeRefiner()
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
        this.localLatticeRefiner = Objects.requireNonNull(
                localLatticeRefiner,
                "localLatticeRefiner must not be null"
        );
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

        CaptureMediaPaletteModel paletteModel = paletteSampler.calibratePalette(List.of()).model();
        List<ModuleEvidence> modules = new ArrayList<>(cells.size());
        for (ProjectedModuleCell cell : cells) {
            modules.add(sampleModule(sourceFrame, geometryCandidate, paletteModel, cell));
        }

        Counts counts = Counts.from(modules);
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(layoutProfile);
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
        reasonCodes.addAll(validation.reasonCodes());
        CaptureMediaCandidateId samplingCandidateId = CaptureMediaCandidateId.samplingCandidate(
                geometryCandidate.candidateId(),
                scalePercent(centralScale),
                variantRank
        );
        ModuleSamplingEvidence evidence = new ModuleSamplingEvidence(
                SCHEMA_VERSION,
                localRefinement.isPresent()
                        ? CaptureMediaCandidateId.refinementCandidate(samplingCandidateId)
                        : samplingCandidateId,
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
                validation.tileDecodeAttempted(),
                validation.attemptCount(),
                validation.acceptedPayloads().size(),
                validation.failureStages(),
                modules,
                deduplicated(reasonCodes)
        );
        return new VariantSample(evidence, validation.acceptedPayloads());
    }

    private ModuleEvidence sampleModule(
            MediaInputFrame sourceFrame,
            GeometryCandidateEvidence geometryCandidate,
            CaptureMediaPaletteModel paletteModel,
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
        if (sampledArgb.isEmpty()) {
            return moduleEvidence(
                    cell,
                    0,
                    0x00000000,
                    0.0d,
                    OptionalInt.empty(),
                    new PaletteDistances(0, 0.0d, 0.0d, 0.0d),
                    ModuleSampleStatus.OUT_OF_BOUNDS,
                    List.of(CaptureMediaEvidenceReasonCode.MODULE_REGION_OUT_OF_BOUNDS)
            );
        }

        AggregatedColor aggregated = aggregate(sampledArgb);
        PaletteDistances distances = paletteDistances(aggregated.argb(), paletteModel);
        CaptureMediaPaletteSample paletteSample = paletteSampler.tolerantPaletteSample(aggregated.argb(), paletteModel);
        ModuleSampleStatus status;
        OptionalInt assignedPaletteIndex = OptionalInt.empty();
        if (clipped) {
            status = ModuleSampleStatus.CLIPPED;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.MODULE_REGION_CLIPPED);
        } else if (sampledArgb.size() < MIN_READABLE_SAMPLE_COUNT) {
            status = ModuleSampleStatus.UNREADABLE;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.INSUFFICIENT_SAMPLE_COUNT);
        } else if (aggregated.variance() > HIGH_VARIANCE_THRESHOLD) {
            status = ModuleSampleStatus.AMBIGUOUS;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.HIGH_COLOR_VARIANCE);
        } else if (paletteSample.status() == CaptureMediaPaletteSampleStatus.REJECTED) {
            status = ModuleSampleStatus.UNREADABLE;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_COLOR_MARGIN);
        } else if (paletteSample.status() == CaptureMediaPaletteSampleStatus.LOW_CONFIDENCE
                || distances.margin() < LOW_DISTANCE_MARGIN_THRESHOLD) {
            status = ModuleSampleStatus.AMBIGUOUS;
            reasonCodes.add(CaptureMediaEvidenceReasonCode.LOW_COLOR_MARGIN);
        } else {
            status = ModuleSampleStatus.READABLE;
            assignedPaletteIndex = OptionalInt.of(paletteSample.paletteIndex());
        }

        return moduleEvidence(
                cell,
                sampledArgb.size(),
                aggregated.argb(),
                aggregated.variance(),
                assignedPaletteIndex,
                distances,
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
            if (candidate.isEmpty()) {
                continue;
            }
            attemptCount++;
            ValidationAttempt attempt = logicalTileValidator.validate(
                    layoutPlan,
                    tileIndex,
                    candidate.orElseThrow()
            );
            if (attempt.payload().isPresent()) {
                acceptedPayloads.add(attempt.payload().orElseThrow());
                continue;
            }
            FailureStage stage = attempt.failureStage().orElseThrow();
            failureStages.add(stage);
            reasonCodes.add(reasonCode(stage));
        }
        if (attemptCount == 0) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.TILE_DECODE_NOT_ATTEMPTED);
        }
        return new VariantValidation(
                attemptCount,
                acceptedPayloads,
                deduplicatedStages(failureStages),
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
                        || module.assignedPaletteIndex().isEmpty()) {
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
        return new ModuleSamplingEvidence(
                SCHEMA_VERSION,
                CaptureMediaCandidateId.samplingCandidate(geometryId, scalePercent(CENTRAL_SCALES.get(0)), 1),
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
                false,
                0,
                0,
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

    private PaletteDistances paletteDistances(int argb, CaptureMediaPaletteModel paletteModel) {
        List<PaletteDistance> distances = paletteModel.colors()
                .stream()
                .map(color -> new PaletteDistance(color.paletteIndex(), rgbDistance(argb, color.modelArgb())))
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
            failureStages = List.copyOf(Objects.requireNonNull(failureStages, "failureStages must not be null"));
            reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes must not be null"));
            if (acceptedPayloads.stream().anyMatch(Objects::isNull)
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

    private record PaletteDistance(int paletteIndex, double distance) {
    }

    private record PaletteDistances(int paletteIndex, double bestDistance, double secondBestDistance, double margin) {
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
