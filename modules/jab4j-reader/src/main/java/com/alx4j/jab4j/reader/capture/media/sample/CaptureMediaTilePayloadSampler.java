package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidenceProvider;
import com.alx4j.jab4j.reader.capture.media.cv.CvTileSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.tile.TileDecoder;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

/**
 * Samples normalized media frames with bounded palette tolerance and accepts content only after tile and envelope
 * validation.
 *
 * <p>This class is a media-owned MVP-3 sampling boundary. It does not assemble frames or restore files. A sampled tile
 * payload is accepted only when nearest-palette sampling produces a logical tile, {@link TileDecoder} decodes it, and
 * {@link TilePayloadEnvelopeCodec} validates the payload envelope and CRC.</p>
 */
public final class CaptureMediaTilePayloadSampler {

    private static final int BLACK_INDEX = 0;
    private static final int WHITE_INDEX = 7;
    private static final int MIN_MODULE_SIZE_PX = 4;
    private static final int SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION = 1;
    private static final double MAX_RGB_DISTANCE = Math.sqrt(3.0d * 255.0d * 255.0d);
    private static final double CAMERA_MAX_ACCEPTED_RGB_DISTANCE = 170.0d;
    private static final double CAMERA_MAX_SPARSE_REJECTED_RGB_DISTANCE = 224.0d;
    private static final double CAMERA_MAX_SPARSE_REJECTED_RATIO = 0.05d;
    private static final int CAMERA_MAX_SPARSE_REJECTED_SAMPLE_COUNT = 128;
    private static final double MIN_BORDER_SIGNATURE_WHITE_RATIO = 0.60d;
    private static final double MAX_BORDER_SIGNATURE_NON_WHITE_RATIO = 0.10d;
    private static final double MAX_BORDER_SIGNATURE_REJECTED_RATIO = 0.40d;
    private static final int CAMERA_SLOT_ALIGNMENT_RADIUS_PX = 96;
    private static final int CAMERA_SLOT_ALIGNMENT_STEP_PX = 4;
    private static final int CAMERA_SLOT_ALIGNMENT_SAMPLE_STRIDE_PX = 4;
    private static final double MIN_CAMERA_BORDER_WHITE_RATIO = 0.54d;
    private static final double MAX_CAMERA_BORDER_REJECTED_RATIO = 0.08d;
    private static final double MIN_CAMERA_BORDER_STRONG_SIDE_RATIO = 0.68d;
    private static final double MIN_CAMERA_BORDER_MODERATE_SIDE_RATIO = 0.42d;
    private static final double MIN_SAMPLING_EVIDENCE_CONFIDENCE = 0.55d;
    private static final int MAX_EVIDENCE_MODULE_CENTER_OFFSET_PX = 8;
    private static final int MAX_AREA_SAMPLE_RADIUS_PX = 3;

    private final CaptureMediaPaletteSampler paletteSampler;
    private final List<Integer> paletteArgb;
    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final TileDecoder tileDecoder;
    private final TileCodecProfile tileCodecProfile;
    private final TilePayloadEnvelopeCodec envelopeCodec;
    private final CvSamplingEvidenceProvider samplingEvidenceProvider;

    /**
     * Creates a sampler using current rendered-layout, balanced-v1 tile, and envelope validation defaults.
     */
    public CaptureMediaTilePayloadSampler() {
        this(
                new CaptureMediaPaletteSampler(),
                new CaptureRenderedLayoutCatalog(),
                new FixedLayoutPlanner(),
                TileCodecs.defaultDecoder(),
                TileCodecProfiles.balancedV1(),
                new TilePayloadEnvelopeCodec(),
                new CaptureMediaSamplingEvidenceProvider()
        );
    }

    /**
     * Creates a sampler with optional backend-neutral sampling evidence.
     *
     * @param samplingEvidenceProvider provider for grid-phase and local sampling evidence
     */
    public CaptureMediaTilePayloadSampler(CvSamplingEvidenceProvider samplingEvidenceProvider) {
        this(
                new CaptureMediaPaletteSampler(),
                new CaptureRenderedLayoutCatalog(),
                new FixedLayoutPlanner(),
                TileCodecs.defaultDecoder(),
                TileCodecProfiles.balancedV1(),
                new TilePayloadEnvelopeCodec(),
                samplingEvidenceProvider
        );
    }

    /**
     * Creates a sampler with explicit collaborators for focused tests.
     *
     * @param paletteSampler bounded nearest-palette sampler
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner
     * @param tileDecoder logical tile decoder
     * @param tileCodecProfile supported logical tile profile
     * @param envelopeCodec tile payload envelope codec
     */
    public CaptureMediaTilePayloadSampler(
            CaptureMediaPaletteSampler paletteSampler,
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            TileDecoder tileDecoder,
            TileCodecProfile tileCodecProfile,
            TilePayloadEnvelopeCodec envelopeCodec
    ) {
        this(
                paletteSampler,
                layoutCatalog,
                layoutPlanner,
                tileDecoder,
                tileCodecProfile,
                envelopeCodec,
                CvSamplingEvidenceProvider.none()
        );
    }

    /**
     * Creates a sampler with explicit collaborators and optional backend-neutral sampling evidence.
     *
     * @param paletteSampler bounded nearest-palette sampler
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner
     * @param tileDecoder logical tile decoder
     * @param tileCodecProfile supported logical tile profile
     * @param envelopeCodec tile payload envelope codec
     * @param samplingEvidenceProvider provider for grid-phase and local sampling evidence
     */
    public CaptureMediaTilePayloadSampler(
            CaptureMediaPaletteSampler paletteSampler,
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            TileDecoder tileDecoder,
            TileCodecProfile tileCodecProfile,
            TilePayloadEnvelopeCodec envelopeCodec,
            CvSamplingEvidenceProvider samplingEvidenceProvider
    ) {
        this.paletteSampler = Objects.requireNonNull(paletteSampler, "paletteSampler must not be null");
        this.paletteArgb = this.paletteSampler.paletteArgb();
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.tileDecoder = Objects.requireNonNull(tileDecoder, "tileDecoder must not be null");
        this.tileCodecProfile = Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec must not be null");
        this.samplingEvidenceProvider = Objects.requireNonNull(
                samplingEvidenceProvider,
                "samplingEvidenceProvider must not be null"
        );
    }

    /**
     * Samples and validates tile payloads from one normalized media frame.
     *
     * @param frame normalized media frame
     * @return frame sample result with accepted payloads or blocking diagnostics
     */
    public FrameSample sample(NormalizedCaptureFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        List<LayoutProfile> layouts = resolvedLayouts(frame);
        if (layouts.isEmpty()) {
            return FrameSample.rejected(
                    List.of(),
                    List.of(sourceDiagnostic(
                            frame,
                            CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                            CaptureMediaDiagnosticSeverity.ERROR,
                            Map.of(),
                            "Normalized media frame does not match a supported rendered layout profile"
                    )),
                    Optional.empty()
            );
        }

        FrameSample firstRejected = null;
        for (LayoutProfile layout : layouts) {
            FrameSample sample = sample(frame, layoutPlanner.plan(layout));
            if (sample.status() == FrameSampleStatus.ACCEPTED) {
                return sample;
            }
            if (sample.status() == FrameSampleStatus.REJECTED && firstRejected == null) {
                firstRejected = sample;
            }
        }
        return firstRejected == null ? FrameSample.empty() : firstRejected;
    }

    private FrameSample sample(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan) {
        Optional<CvSamplingEvidence> samplingEvidence = samplingEvidence(frame, layoutPlan);
        List<TilePayload> payloads = new ArrayList<>();
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>();
        PaletteSampleAccumulator acceptedConfidence = new PaletteSampleAccumulator();
        List<TilePlacement> placements = layoutPlan.tilePlacements();
        for (int tileIndex = 0; tileIndex < placements.size(); tileIndex++) {
            SlotSample slotSample = sampleSlot(frame, layoutPlan, placements.get(tileIndex), tileIndex, samplingEvidence);
            if (slotSample.status() == SlotSampleStatus.EMPTY
                    || slotSample.status() == SlotSampleStatus.NO_SIGNATURE_CONTENT) {
                continue;
            }
            if (slotSample.status() == SlotSampleStatus.REJECTED) {
                PaletteConfidenceSummary confidence = slotSample.paletteConfidence().orElseThrow();
                diagnostics.add(colorDiagnostic(
                        frame,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        confidence,
                        "Media tile samples are outside the supported color/compression threshold"
                ));
                return FrameSample.rejected(List.of(), diagnostics, Optional.of(confidence));
            }
            if (slotSample.status() == SlotSampleStatus.UNDECODABLE) {
                Optional<PaletteConfidenceSummary> confidence = slotSample.paletteConfidence();
                diagnostics.add(colorDiagnostic(
                        frame,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        confidence.orElse(null),
                        "Media tile content passed palette sampling but failed tile decode or envelope CRC validation"
                ));
                return FrameSample.rejected(List.of(), diagnostics, confidence);
            }
            payloads.add(slotSample.payload().orElseThrow());
            acceptedConfidence.add(slotSample.paletteConfidence().orElseThrow());
        }

        if (payloads.isEmpty()) {
            return FrameSample.empty();
        }
        PaletteConfidenceSummary confidence = acceptedConfidence.summary();
        if (confidence.lowConfidenceSampleCount() > 0 || confidence.rejectedSampleCount() > 0) {
            diagnostics.add(colorDiagnostic(
                    frame,
                    CaptureMediaDiagnosticSeverity.WARNING,
                    confidence,
                    "Media tile content decoded with low palette confidence"
            ));
        }
        return FrameSample.accepted(payloads, diagnostics, confidence);
    }

    /**
     * Inspects sampler decisions for one normalized frame without changing restore behavior.
     *
     * <p>This diagnostic path is intended for MVP-3 media tuning. It exposes the same tile-slot, side-version,
     * palette, finder, tile-decode, and envelope-validation decisions used by {@link #sample(NormalizedCaptureFrame)}
     * so exported normalized candidates can be analyzed without returning to the original phone photo.</p>
     *
     * @param frame normalized media frame
     * @return deterministic sampler inspection result
     */
    public FrameInspection inspect(NormalizedCaptureFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        List<LayoutProfile> layouts = resolvedLayouts(frame);
        if (layouts.isEmpty()) {
            return new FrameInspection(
                    frame.sourceId(),
                    frame.layoutProfileId(),
                    Optional.empty(),
                    List.of(),
                    0,
                    0,
                    0,
                    0
            );
        }

        FrameInspection bestInspection = null;
        for (LayoutProfile layout : layouts) {
            FrameInspection inspection = inspect(frame, layoutPlanner.plan(layout));
            if (inspection.decodedPayloadCount() > 0) {
                return inspection;
            }
            bestInspection = betterInspection(bestInspection, inspection);
        }
        return bestInspection;
    }

    private FrameInspection inspect(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan) {
        Optional<CvSamplingEvidence> samplingEvidence = samplingEvidence(frame, layoutPlan);
        List<SlotInspection> slots = new ArrayList<>();
        int candidateAttemptCount = 0;
        int paletteRejectedAttemptCount = 0;
        int noFinderAttemptCount = 0;
        int decodedPayloadCount = 0;
        List<TilePlacement> placements = layoutPlan.tilePlacements();
        for (int tileIndex = 0; tileIndex < placements.size(); tileIndex++) {
            SlotInspection slot = inspectSlot(frame, layoutPlan, placements.get(tileIndex), tileIndex, samplingEvidence);
            slots.add(slot);
            for (CandidateInspection candidate : slot.candidates()) {
                candidateAttemptCount++;
                if (candidate.status() == CandidateInspectionStatus.PALETTE_REJECTED) {
                    paletteRejectedAttemptCount++;
                } else if (candidate.status() == CandidateInspectionStatus.NO_FINDER) {
                    noFinderAttemptCount++;
                }
                if (candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD) {
                    decodedPayloadCount++;
                }
            }
        }
        return new FrameInspection(
                frame.sourceId(),
                layoutPlan.profile().profileId(),
                samplingEvidence,
                slots,
                candidateAttemptCount,
                noFinderAttemptCount,
                paletteRejectedAttemptCount,
                decodedPayloadCount
        );
    }

    private List<LayoutProfile> resolvedLayouts(NormalizedCaptureFrame frame) {
        List<LayoutProfile> layouts = new ArrayList<>();
        layoutCatalog
                .resolve(frame.normalizedWidthPixels(), frame.normalizedHeightPixels())
                .filter(profile -> profile.profileId().equals(frame.layoutProfileId()))
                .ifPresent(layouts::add);
        if (!cameraDerived(frame)) {
            return List.copyOf(layouts);
        }
        for (LayoutProfile profile : layoutCatalog.profiles()) {
            scaledLayout(profile, frame.normalizedWidthPixels(), frame.normalizedHeightPixels())
                    .ifPresent(layout -> addUniqueLayout(layouts, layout));
        }
        return List.copyOf(layouts);
    }

    private Optional<LayoutProfile> scaledLayout(LayoutProfile profile, int frameWidthPx, int frameHeightPx) {
        if (profile.frameWidthPx() == frameWidthPx && profile.frameHeightPx() == frameHeightPx) {
            return Optional.empty();
        }
        if (frameWidthPx % profile.frameWidthPx() != 0 || frameHeightPx % profile.frameHeightPx() != 0) {
            return Optional.empty();
        }
        int scaleX = frameWidthPx / profile.frameWidthPx();
        int scaleY = frameHeightPx / profile.frameHeightPx();
        if (scaleX != scaleY || scaleX <= 1) {
            return Optional.empty();
        }
        return Optional.of(new LayoutProfile(
                profile.profileId(),
                profile.rows(),
                profile.cols(),
                frameWidthPx,
                frameHeightPx,
                profile.tileGapPx() * scaleX,
                profile.outerMarginPx() * scaleX,
                profile.separatorStyle(),
                profile.topSyncBandPx() * scaleX,
                profile.metadataBandPx() * scaleX,
                profile.backgroundStyle(),
                profile.fitPolicy()
        ));
    }

    private void addUniqueLayout(List<LayoutProfile> layouts, LayoutProfile candidate) {
        boolean exists = layouts.stream().anyMatch(layout ->
                layout.profileId().equals(candidate.profileId())
                        && layout.frameWidthPx() == candidate.frameWidthPx()
                        && layout.frameHeightPx() == candidate.frameHeightPx()
                        && layout.rows() == candidate.rows()
                        && layout.cols() == candidate.cols());
        if (!exists) {
            layouts.add(candidate);
        }
    }

    private FrameInspection betterInspection(FrameInspection current, FrameInspection candidate) {
        if (current == null) {
            return candidate;
        }
        int currentScore = inspectionScore(current);
        int candidateScore = inspectionScore(candidate);
        return candidateScore > currentScore ? candidate : current;
    }

    private int inspectionScore(FrameInspection inspection) {
        int score = inspection.decodedPayloadCount() * 1_000_000;
        for (SlotInspection slot : inspection.slots()) {
            if (slot.borderStatus() == BorderInspectionStatus.SIGNATURE) {
                score += 10_000;
            }
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE) {
                    score += 1_000;
                } else if (candidate.status() == CandidateInspectionStatus.NO_FINDER) {
                    score += 10;
                } else if (candidate.status() == CandidateInspectionStatus.PALETTE_REJECTED) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private SlotSample sampleSlot(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
        TileAlignment alignment = alignedTilePlacement(frame, layoutPlan, placement, tileIndex, samplingEvidence);
        TilePlacement effectivePlacement = alignment.placement();
        BorderSample borderSample = sampleRenderedTileBorder(frame, layoutPlan, effectivePlacement);
        if (borderSample.status() != BorderSampleStatus.SIGNATURE) {
            if (borderSample.status() == BorderSampleStatus.REJECTED
                    && hasInteriorContent(frame, layoutPlan, effectivePlacement)) {
                return SlotSample.rejected(borderSample.paletteConfidence().orElseThrow());
            }
            return hasInteriorContent(frame, layoutPlan, effectivePlacement)
                    ? SlotSample.noSignatureContent()
                    : SlotSample.empty();
        }

        List<CandidateSample> candidates = new ArrayList<>();
        CandidateSample rejectedCandidate = null;
        for (int sideVersion = tileCodecProfile.minSideVersion(); sideVersion <= tileCodecProfile.maxSideVersion(); sideVersion++) {
            CandidateSamplingGeometry geometry = candidateSamplingGeometry(
                    layoutPlan,
                    tileIndex,
                    sideVersion,
                    samplingEvidence
            );
            for (CandidateSample candidate : sampleCandidates(
                    frame,
                    layoutPlan,
                    effectivePlacement,
                    tileIndex,
                    geometry
            )) {
                if (candidate.status() == CandidateSampleStatus.REJECTED) {
                    rejectedCandidate = lowerConfidence(rejectedCandidate, candidate);
                } else if (candidate.status() == CandidateSampleStatus.CANDIDATE) {
                    candidates.add(candidate);
                }
            }
        }

        for (CandidateSample candidate : candidates) {
            TilePayload payload = decodeCandidate(layoutPlan, tileIndex, candidate.logicalTile().orElseThrow());
            if (payload != null) {
                return SlotSample.decoded(payload, candidate.paletteConfidence());
            }
        }
        if (rejectedCandidate != null) {
            return SlotSample.rejected(rejectedCandidate.paletteConfidence());
        }
        if (!candidates.isEmpty()) {
            return SlotSample.undecodable(Optional.of(candidates.get(0).paletteConfidence()));
        }
        return SlotSample.undecodable(Optional.empty());
    }

    private SlotInspection inspectSlot(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
        TileAlignment alignment = alignedTilePlacement(frame, layoutPlan, placement, tileIndex, samplingEvidence);
        TilePlacement effectivePlacement = alignment.placement();
        BorderSample borderSample = sampleRenderedTileBorder(frame, layoutPlan, effectivePlacement);
        boolean interiorContent = hasInteriorContent(frame, layoutPlan, effectivePlacement);
        if (borderSample.status() != BorderSampleStatus.SIGNATURE) {
            return new SlotInspection(
                    tileIndex,
                    inspectionStatus(borderSample.status()),
                    interiorContent,
                    alignment.shiftXPx(),
                    alignment.shiftYPx(),
                    alignment.source(),
                    List.of()
            );
        }

        List<CandidateInspection> candidates = new ArrayList<>();
        for (int sideVersion = tileCodecProfile.minSideVersion(); sideVersion <= tileCodecProfile.maxSideVersion(); sideVersion++) {
            CandidateSamplingGeometry geometry = candidateSamplingGeometry(
                    layoutPlan,
                    tileIndex,
                    sideVersion,
                    samplingEvidence
            );
            CandidateInspection candidate = inspectCandidate(
                    frame,
                    layoutPlan,
                    effectivePlacement,
                    tileIndex,
                    geometry
            );
            candidates.add(candidate);
        }
        return new SlotInspection(
                tileIndex,
                inspectionStatus(borderSample.status()),
                interiorContent,
                alignment.shiftXPx(),
                alignment.shiftYPx(),
                alignment.source(),
                candidates
        );
    }

    private BorderInspectionStatus inspectionStatus(BorderSampleStatus status) {
        return switch (status) {
            case SIGNATURE -> BorderInspectionStatus.SIGNATURE;
            case NO_SIGNATURE -> BorderInspectionStatus.NO_SIGNATURE;
            case REJECTED -> BorderInspectionStatus.PALETTE_REJECTED;
        };
    }

    private CandidateInspectionStatus inspectionStatus(CandidateSampleStatus status) {
        return switch (status) {
            case NO_FINDER -> CandidateInspectionStatus.NO_FINDER;
            case REJECTED -> CandidateInspectionStatus.PALETTE_REJECTED;
            case CANDIDATE -> CandidateInspectionStatus.FINDER_CANDIDATE;
        };
    }

    private TileAlignment alignedTilePlacement(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
        if (!cameraDerived(frame)) {
            return TileAlignment.of(placement, placement, TileAlignmentInspectionSource.NOMINAL);
        }
        Optional<TileAlignment> evidenceAlignment = evidenceAlignedPlacement(frame, placement, tileIndex, samplingEvidence);
        if (evidenceAlignment.isPresent()) {
            BorderEvidence evidenceBorder = sampleBorderEvidence(
                    frame,
                    evidenceAlignment.orElseThrow().placement(),
                    layoutPlan.separatorThicknessPx(),
                    CAMERA_SLOT_ALIGNMENT_SAMPLE_STRIDE_PX
            );
            if (evidenceBorder.cameraSignature()) {
                return evidenceAlignment.orElseThrow();
            }
        }
        return legacyAlignedTilePlacement(frame, layoutPlan, placement);
    }

    private TileAlignment legacyAlignedTilePlacement(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement
    ) {
        int border = layoutPlan.separatorThicknessPx();
        TilePlacement bestPlacement = placement;
        BorderEvidence bestEvidence = sampleBorderEvidence(
                frame,
                placement,
                border,
                CAMERA_SLOT_ALIGNMENT_SAMPLE_STRIDE_PX
        );
        for (int offsetY = -CAMERA_SLOT_ALIGNMENT_RADIUS_PX;
                offsetY <= CAMERA_SLOT_ALIGNMENT_RADIUS_PX;
                offsetY += CAMERA_SLOT_ALIGNMENT_STEP_PX) {
            for (int offsetX = -CAMERA_SLOT_ALIGNMENT_RADIUS_PX;
                    offsetX <= CAMERA_SLOT_ALIGNMENT_RADIUS_PX;
                    offsetX += CAMERA_SLOT_ALIGNMENT_STEP_PX) {
                Optional<TilePlacement> shiftedPlacement = shiftedPlacement(frame, placement, offsetX, offsetY);
                if (shiftedPlacement.isEmpty()) {
                    continue;
                }
                BorderEvidence evidence = sampleBorderEvidence(
                        frame,
                        shiftedPlacement.orElseThrow(),
                        border,
                        CAMERA_SLOT_ALIGNMENT_SAMPLE_STRIDE_PX
                );
                if (evidence.alignmentScore() > bestEvidence.alignmentScore()) {
                    bestEvidence = evidence;
                    bestPlacement = shiftedPlacement.orElseThrow();
                }
            }
        }
        return TileAlignment.of(placement, bestPlacement, TileAlignmentInspectionSource.LEGACY_BORDER_SCAN);
    }

    private Optional<TileAlignment> evidenceAlignedPlacement(
            NormalizedCaptureFrame frame,
            TilePlacement placement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
        if (samplingEvidence.filter(this::usableSamplingEvidence).isEmpty()) {
            return Optional.empty();
        }
        CvSamplingEvidence evidence = samplingEvidence.orElseThrow();
        int offsetX = roundedAlignmentOffset(alignmentOffsetXPx(evidence, tileIndex));
        int offsetY = roundedAlignmentOffset(alignmentOffsetYPx(evidence, tileIndex));
        return shiftedPlacement(frame, placement, offsetX, offsetY)
                .map(effectivePlacement -> TileAlignment.of(
                        placement,
                        effectivePlacement,
                        TileAlignmentInspectionSource.SAMPLING_EVIDENCE
                ));
    }

    private Optional<TilePlacement> shiftedPlacement(
            NormalizedCaptureFrame frame,
            TilePlacement placement,
            int offsetX,
            int offsetY
    ) {
        int shiftedX = placement.xPx() + offsetX;
        int shiftedY = placement.yPx() + offsetY;
        if (shiftedX < 0
                || shiftedY < 0
                || shiftedX + placement.widthPx() > frame.normalizedWidthPixels()
                || shiftedY + placement.heightPx() > frame.normalizedHeightPixels()) {
            return Optional.empty();
        }
        return Optional.of(new TilePlacement(
                placement.row(),
                placement.col(),
                shiftedX,
                shiftedY,
                placement.widthPx(),
                placement.heightPx()
        ));
    }

    private CandidateSample lowerConfidence(CandidateSample current, CandidateSample candidate) {
        if (current == null) {
            return candidate;
        }
        double currentMinimum = current.paletteConfidence().minimumConfidence();
        double candidateMinimum = candidate.paletteConfidence().minimumConfidence();
        return candidateMinimum < currentMinimum ? candidate : current;
    }

    private BorderSample sampleRenderedTileBorder(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement
    ) {
        int border = layoutPlan.separatorThicknessPx();
        BorderEvidence evidence = sampleBorderEvidence(frame, placement, border, 1);
        if (evidence.sampleCount() == 0) {
            return BorderSample.noSignature();
        }

        if (evidence.exactLikeSignature() || (cameraDerived(frame) && evidence.cameraSignature())) {
            return BorderSample.signature();
        }
        if (evidence.rejectedSamples() > 0) {
            return BorderSample.rejected(evidence.paletteConfidence());
        }
        return BorderSample.noSignature();
    }

    private BorderEvidence sampleBorderEvidence(
            NormalizedCaptureFrame frame,
            TilePlacement placement,
            int border,
            int sampleStride
    ) {
        BorderEvidenceBuilder evidence = new BorderEvidenceBuilder();
        for (int row = 0; row < placement.heightPx(); row += sampleStride) {
            for (int col = 0; col < placement.widthPx(); col += sampleStride) {
                boolean top = row < border;
                boolean bottom = row >= placement.heightPx() - border;
                boolean left = col < border;
                boolean right = col >= placement.widthPx() - border;
                if (!top && !bottom && !left && !right) {
                    continue;
                }
                CaptureMediaPaletteSample sample = sampleTolerantPalette(
                        frame,
                        placement.yPx() + row,
                        placement.xPx() + col
                );
                evidence.add(sample, top, bottom, left, right);
            }
        }
        return evidence.toEvidence();
    }

    private boolean hasInteriorContent(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan, TilePlacement placement) {
        int border = layoutPlan.separatorThicknessPx();
        for (int row = border; row < placement.heightPx() - border; row++) {
            for (int col = border; col < placement.widthPx() - border; col++) {
                CaptureMediaPaletteSample sample = sampleTolerantPalette(
                        frame,
                        placement.yPx() + row,
                        placement.xPx() + col
                );
                if (!sample.accepted() || sample.paletteIndex() != BLACK_INDEX) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<CandidateSample> sampleCandidates(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            CandidateSamplingGeometry geometry
    ) {
        CandidateSample base = sampleCandidate(frame, layoutPlan, placement, tileIndex, geometry);
        if (!cameraDerived(frame) || base.status() == CandidateSampleStatus.CANDIDATE) {
            return List.of(base);
        }

        List<CandidateSample> attempts = new ArrayList<>();
        attempts.add(base);
        for (CandidateSamplingGeometry fallbackGeometry : fallbackSamplingGeometries(geometry)) {
            CandidateSample fallback = sampleCandidate(frame, layoutPlan, placement, tileIndex, fallbackGeometry);
            attempts.add(fallback);
        }
        return List.copyOf(attempts);
    }

    private CandidateInspection inspectCandidate(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            CandidateSamplingGeometry geometry
    ) {
        List<CandidateSample> attempts = sampleCandidates(frame, layoutPlan, placement, tileIndex, geometry);
        CandidateSample tileOrEnvelopeRejected = null;
        CandidateSample rejected = null;
        CandidateSample noFinder = null;
        for (CandidateSample attempt : attempts) {
            if (attempt.status() == CandidateSampleStatus.CANDIDATE) {
                TilePayload payload = decodeCandidate(layoutPlan, tileIndex, attempt.logicalTile().orElseThrow());
                if (payload != null) {
                    return candidateInspection(attempt, DecodeInspectionStatus.ACCEPTED_PAYLOAD);
                }
                if (tileOrEnvelopeRejected == null) {
                    tileOrEnvelopeRejected = attempt;
                }
            } else if (attempt.status() == CandidateSampleStatus.REJECTED) {
                rejected = lowerConfidence(rejected, attempt);
            } else if (noFinder == null) {
                noFinder = attempt;
            }
        }
        if (tileOrEnvelopeRejected != null) {
            return candidateInspection(tileOrEnvelopeRejected, DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE);
        }
        if (rejected != null) {
            return candidateInspection(rejected, DecodeInspectionStatus.NOT_ATTEMPTED);
        }
        return candidateInspection(noFinder == null ? attempts.get(0) : noFinder, DecodeInspectionStatus.NOT_ATTEMPTED);
    }

    private CandidateInspection candidateInspection(CandidateSample candidate, DecodeInspectionStatus decodeStatus) {
        CandidateSamplingGeometry geometry = candidate.geometry();
        return new CandidateInspection(
                geometry.sideVersion(),
                geometry.dimension(),
                geometry.moduleSizePx(),
                geometry.moduleCenterOffsetXPx(),
                geometry.moduleCenterOffsetYPx(),
                geometry.moduleSamplingOffsetSource(),
                geometry.areaSampleRadiusPx(),
                inspectionStatus(candidate.status()),
                decodeStatus,
                candidate.optionalPaletteConfidence()
        );
    }

    private CandidateSample sampleCandidate(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            CandidateSamplingGeometry geometry
    ) {
        int dimension = geometry.dimension();
        int moduleSize = geometry.moduleSizePx();
        if (moduleSize < MIN_MODULE_SIZE_PX) {
            return CandidateSample.noFinder(Optional.empty(), geometry);
        }

        List<Integer> moduleColors = new ArrayList<>(dimension * dimension);
        PaletteSampleAccumulator confidence = new PaletteSampleAccumulator();
        boolean rejected = false;
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                int sampleX = placement.xPx()
                        + geometry.contentOffsetXPx()
                        + ((col + tileCodecProfile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2)
                        + geometry.moduleCenterOffsetXPx();
                int sampleY = placement.yPx()
                        + geometry.contentOffsetYPx()
                        + ((row + tileCodecProfile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2)
                        + geometry.moduleCenterOffsetYPx();
                CaptureMediaPaletteSample sample = sampleTolerantPalette(
                        frame,
                        sampleY,
                        sampleX,
                        geometry.areaSampleRadiusPx()
                );
                confidence.add(sample);
                if (!sample.accepted()) {
                    rejected = true;
                }
                moduleColors.add(sample.paletteIndex());
            }
        }

        PaletteConfidenceSummary summary = confidence.summary();
        boolean sparseCameraOutliersAccepted = rejected && sparseCameraOutliersRecoverable(frame, summary);
        if (rejected && !sparseCameraOutliersAccepted) {
            return CandidateSample.rejected(summary, geometry);
        }
        if (!hasSupportedFinderPatterns(moduleColors, dimension)) {
            return CandidateSample.noFinder(Optional.of(summary), geometry);
        }

        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sampledSideVersion", Integer.toString(geometry.sideVersion()));
        diagnostics.put("sampledDimension", Integer.toString(dimension));
        diagnostics.put("layoutProfileId", layoutPlan.profile().profileId());
        diagnostics.put("tileIndex", Integer.toString(tileIndex));
        diagnostics.put("minimumPaletteConfidence", formatMetric(summary.minimumConfidence()));
        diagnostics.put("averagePaletteConfidence", formatMetric(summary.averageConfidence()));
        diagnostics.put("maximumPaletteRgbDistance", formatMetric(summary.maximumRgbDistance()));
        diagnostics.put("lowConfidenceSampleCount", Integer.toString(summary.lowConfidenceSampleCount()));
        diagnostics.put("sparseCameraOutliersAccepted", Boolean.toString(sparseCameraOutliersAccepted));
        diagnostics.put("rejectedSampleCount", Integer.toString(summary.rejectedSampleCount()));
        return CandidateSample.candidate(new LogicalTile(
                dimension,
                dimension,
                tileCodecProfile.quietZoneModules(),
                tileCodecProfile.profileId(),
                moduleColors,
                diagnostics
        ), summary, geometry);
    }

    private boolean sparseCameraOutliersRecoverable(
            NormalizedCaptureFrame frame,
            PaletteConfidenceSummary summary
    ) {
        if (!cameraDerived(frame) || summary.rejectedSampleCount() <= 0) {
            return false;
        }
        double rejectedRatio = (double) summary.rejectedSampleCount() / summary.sampledModuleCount();
        return rejectedRatio <= CAMERA_MAX_SPARSE_REJECTED_RATIO
                && summary.rejectedSampleCount() <= CAMERA_MAX_SPARSE_REJECTED_SAMPLE_COUNT
                && summary.maximumRgbDistance() <= CAMERA_MAX_SPARSE_REJECTED_RGB_DISTANCE;
    }

    private CandidateSamplingGeometry candidateSamplingGeometry(
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            int sideVersion,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
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
        return new CandidateSamplingGeometry(
                sideVersion,
                dimension,
                moduleSize,
                offsetX,
                offsetY,
                moduleCenterOffsetPx(samplingEvidence, tileIndex, true),
                moduleCenterOffsetPx(samplingEvidence, tileIndex, false),
                moduleSamplingOffsetSource(samplingEvidence, tileIndex),
                areaSampleRadiusPx(samplingEvidence, moduleSize)
        );
    }

    private List<CandidateSamplingGeometry> fallbackSamplingGeometries(CandidateSamplingGeometry geometry) {
        int coarseOffset = Math.max(2, Math.min(6, geometry.moduleSizePx() / 4));
        int fineOffset = Math.max(1, coarseOffset / 2);
        int moduleSizeStep = Math.max(1, geometry.moduleSizePx() / 12);
        List<CandidateSamplingGeometry> geometries = new ArrayList<>();
        addFallbackModuleSizeGeometry(geometries, geometry, -moduleSizeStep);
        addFallbackModuleSizeGeometry(geometries, geometry, moduleSizeStep);
        addFallbackModuleSizeGeometry(geometries, geometry, -(2 * moduleSizeStep));
        addFallbackModuleSizeGeometry(geometries, geometry, 2 * moduleSizeStep);
        addFallbackSamplingGeometry(geometries, geometry, fineOffset, 0);
        addFallbackSamplingGeometry(geometries, geometry, -fineOffset, 0);
        addFallbackSamplingGeometry(geometries, geometry, 0, fineOffset);
        addFallbackSamplingGeometry(geometries, geometry, 0, -fineOffset);
        addFallbackSamplingGeometry(geometries, geometry, coarseOffset, 0);
        addFallbackSamplingGeometry(geometries, geometry, -coarseOffset, 0);
        addFallbackSamplingGeometry(geometries, geometry, 0, coarseOffset);
        addFallbackSamplingGeometry(geometries, geometry, 0, -coarseOffset);
        addFallbackSamplingGeometry(geometries, geometry, fineOffset, fineOffset);
        addFallbackSamplingGeometry(geometries, geometry, -fineOffset, fineOffset);
        addFallbackSamplingGeometry(geometries, geometry, fineOffset, -fineOffset);
        addFallbackSamplingGeometry(geometries, geometry, -fineOffset, -fineOffset);
        return List.copyOf(geometries);
    }

    private void addFallbackModuleSizeGeometry(
            List<CandidateSamplingGeometry> geometries,
            CandidateSamplingGeometry base,
            int moduleSizeDeltaPx
    ) {
        int moduleSizePx = base.moduleSizePx() + moduleSizeDeltaPx;
        if (moduleSizePx < MIN_MODULE_SIZE_PX || moduleSizePx == base.moduleSizePx()) {
            return;
        }
        geometries.add(base.withModuleSize(moduleSizePx, tileCodecProfile.quietZoneModules()));
    }

    private void addFallbackSamplingGeometry(
            List<CandidateSamplingGeometry> geometries,
            CandidateSamplingGeometry base,
            int deltaX,
            int deltaY
    ) {
        int offsetX = base.moduleCenterOffsetXPx() + deltaX;
        int offsetY = base.moduleCenterOffsetYPx() + deltaY;
        if (offsetX == base.moduleCenterOffsetXPx() && offsetY == base.moduleCenterOffsetYPx()) {
            return;
        }
        geometries.add(base.withModuleCenterOffset(offsetX, offsetY, ModuleSamplingInspectionSource.FALLBACK_SEARCH));
    }

    private CaptureMediaPaletteSample sampleTolerantPalette(NormalizedCaptureFrame frame, int row, int col) {
        return sampleTolerantPalette(frame, row, col, 0);
    }

    private CaptureMediaPaletteSample sampleTolerantPalette(
            NormalizedCaptureFrame frame,
            int row,
            int col,
            int areaSampleRadius
    ) {
        int safeRow = clamp(row, 0, frame.normalizedHeightPixels() - 1);
        int safeCol = clamp(col, 0, frame.normalizedWidthPixels() - 1);
        CaptureMediaPaletteSample standardSample = areaSampleRadius > 0
                ? paletteSampler.sampleTolerantPaletteArea(frame, safeRow, safeCol, areaSampleRadius)
                : paletteSampler.sampleTolerantPalette(frame, safeRow, safeCol);
        if (standardSample.accepted() || !cameraDerived(frame)) {
            return standardSample;
        }

        NearestPaletteColor nearest = nearestPaletteColor(standardSample.sourceArgb());
        if (nearest.rgbDistance() > CAMERA_MAX_ACCEPTED_RGB_DISTANCE) {
            return standardSample;
        }
        return new CaptureMediaPaletteSample(
                standardSample.sourceArgb(),
                nearest.paletteIndex(),
                nearest.paletteArgb(),
                nearest.rgbDistance(),
                nearest.rgbDistance() / MAX_RGB_DISTANCE,
                1.0d - (nearest.rgbDistance() / CAMERA_MAX_ACCEPTED_RGB_DISTANCE),
                CaptureMediaPaletteSampleStatus.LOW_CONFIDENCE
        );
    }

    private Optional<CvSamplingEvidence> samplingEvidence(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan
    ) {
        try {
            return samplingEvidenceProvider.evidenceFor(frame, layoutPlan)
                    .filter(this::usableSamplingEvidence);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean usableSamplingEvidence(CvSamplingEvidence evidence) {
        return evidence.confidence() >= MIN_SAMPLING_EVIDENCE_CONFIDENCE;
    }

    private int moduleCenterOffsetPx(
            Optional<CvSamplingEvidence> samplingEvidence,
            int tileIndex,
            boolean horizontal
    ) {
        if (samplingEvidence.filter(this::usableSamplingEvidence).isEmpty()) {
            return 0;
        }
        CvSamplingEvidence evidence = samplingEvidence.orElseThrow();
        double offset = horizontal ? moduleCenterOffsetXPx(evidence, tileIndex) : moduleCenterOffsetYPx(evidence, tileIndex);
        return clamp(
                (int) Math.round(offset),
                -MAX_EVIDENCE_MODULE_CENTER_OFFSET_PX,
                MAX_EVIDENCE_MODULE_CENTER_OFFSET_PX
        );
    }

    private int areaSampleRadiusPx(Optional<CvSamplingEvidence> samplingEvidence, int moduleSize) {
        if (moduleSize <= 2 || samplingEvidence.filter(this::usableSamplingEvidence).isEmpty()) {
            return 0;
        }
        return Math.min(MAX_AREA_SAMPLE_RADIUS_PX, Math.max(1, moduleSize / 6));
    }

    private ModuleSamplingInspectionSource moduleSamplingOffsetSource(
            Optional<CvSamplingEvidence> samplingEvidence,
            int tileIndex
    ) {
        if (samplingEvidence.filter(this::usableSamplingEvidence).isEmpty()) {
            return ModuleSamplingInspectionSource.NONE;
        }
        CvSamplingEvidence evidence = samplingEvidence.orElseThrow();
        return evidence.tileEvidence(tileIndex)
                .filter(this::usableTileEvidence)
                .isPresent()
                        ? ModuleSamplingInspectionSource.TILE_EVIDENCE
                        : ModuleSamplingInspectionSource.FRAME_EVIDENCE;
    }

    private double alignmentOffsetXPx(CvSamplingEvidence evidence, int tileIndex) {
        return evidence.tileEvidence(tileIndex)
                .filter(this::usableTileEvidence)
                .map(CvTileSamplingEvidence::moduleCenterOffsetXPx)
                .orElseGet(() -> evidence.gridPhase()
                        .filter(this::usableGridPhase)
                        .map(CvGridPhase::offsetXPx)
                        .orElse(0.0d));
    }

    private double alignmentOffsetYPx(CvSamplingEvidence evidence, int tileIndex) {
        return evidence.tileEvidence(tileIndex)
                .filter(this::usableTileEvidence)
                .map(CvTileSamplingEvidence::moduleCenterOffsetYPx)
                .orElseGet(() -> evidence.gridPhase()
                        .filter(this::usableGridPhase)
                        .map(CvGridPhase::offsetYPx)
                        .orElse(0.0d));
    }

    private double moduleCenterOffsetXPx(CvSamplingEvidence evidence, int tileIndex) {
        return evidence.tileEvidence(tileIndex)
                .filter(this::usableTileEvidence)
                .map(CvTileSamplingEvidence::moduleCenterOffsetXPx)
                .orElse(evidence.moduleCenterOffsetXPx());
    }

    private double moduleCenterOffsetYPx(CvSamplingEvidence evidence, int tileIndex) {
        return evidence.tileEvidence(tileIndex)
                .filter(this::usableTileEvidence)
                .map(CvTileSamplingEvidence::moduleCenterOffsetYPx)
                .orElse(evidence.moduleCenterOffsetYPx());
    }

    private boolean usableTileEvidence(CvTileSamplingEvidence evidence) {
        return evidence.confidence() >= MIN_SAMPLING_EVIDENCE_CONFIDENCE;
    }

    private boolean usableGridPhase(CvGridPhase gridPhase) {
        return gridPhase.confidence() >= MIN_SAMPLING_EVIDENCE_CONFIDENCE;
    }

    private int roundedAlignmentOffset(double offset) {
        return clamp(
                (int) Math.round(offset),
                -CAMERA_SLOT_ALIGNMENT_RADIUS_PX,
                CAMERA_SLOT_ALIGNMENT_RADIUS_PX
        );
    }

    private boolean cameraDerived(NormalizedCaptureFrame frame) {
        return frame.qualityMetrics().measured(frame.qualityMetrics().frameCoverageRatio())
                && frame.qualityMetrics().frameCoverageRatio() < 0.99d;
    }

    private NearestPaletteColor nearestPaletteColor(int argb) {
        int nearestIndex = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < paletteArgb.size(); index++) {
            double distance = rgbDistance(argb, paletteArgb.get(index));
            if (distance < nearestDistance) {
                nearestIndex = index;
                nearestDistance = distance;
            }
        }
        return new NearestPaletteColor(nearestIndex, paletteArgb.get(nearestIndex), nearestDistance);
    }

    private double rgbDistance(int firstArgb, int secondArgb) {
        int redDelta = red(firstArgb) - red(secondArgb);
        int greenDelta = green(firstArgb) - green(secondArgb);
        int blueDelta = blue(firstArgb) - blue(secondArgb);
        return Math.sqrt(
                (redDelta * redDelta)
                        + (greenDelta * greenDelta)
                        + (blueDelta * blueDelta)
        );
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatMetric(double value) {
        return "%.6f".formatted(value);
    }

    private boolean hasSupportedFinderPatterns(List<Integer> moduleColors, int dimension) {
        return hasFinder(moduleColors, dimension, 0, 0, 0)
                && hasFinder(moduleColors, dimension, 0, dimension - 3, 0)
                && hasFinder(moduleColors, dimension, dimension - 3, 0, 6)
                && hasFinder(moduleColors, dimension, dimension - 3, dimension - 3, 3);
    }

    private boolean hasFinder(List<Integer> moduleColors, int dimension, int startRow, int startCol, int expectedColor) {
        for (int row = startRow; row < startRow + 3; row++) {
            for (int col = startCol; col < startCol + 3; col++) {
                if (moduleColors.get((row * dimension) + col) != expectedColor) {
                    return false;
                }
            }
        }
        return true;
    }

    private TilePayload decodeCandidate(FixedLayoutPlan layoutPlan, int tileIndex, LogicalTile candidate) {
        try {
            byte[] envelope = tileDecoder.decode(candidate, tileCodecProfile);
            TilePayload payload = envelopeCodec.parse(envelope, SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION);
            return validPayloadForSlot(layoutPlan, tileIndex, payload) ? payload : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean validPayloadForSlot(FixedLayoutPlan layoutPlan, int tileIndex, TilePayload payload) {
        return layoutPlan.profile().profileId().equals(payload.layoutProfileId())
                && payload.tileIndex().value() == tileIndex
                && payload.totalTilesInFrame() == layoutPlan.profile().rows() * layoutPlan.profile().cols()
                && !(payload.payloadKind() == PayloadKind.SESSION_END && payload.body().length == 0);
    }

    private CaptureMediaDiagnostic colorDiagnostic(
            NormalizedCaptureFrame frame,
            CaptureMediaDiagnosticSeverity severity,
            PaletteConfidenceSummary confidence,
            String message
    ) {
        return sourceDiagnostic(
                frame,
                CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                severity,
                confidence == null ? Map.of() : metrics(confidence),
                message
        );
    }

    private CaptureMediaDiagnostic sourceDiagnostic(
            NormalizedCaptureFrame frame,
            CaptureMediaDiagnosticCode code,
            CaptureMediaDiagnosticSeverity severity,
            Map<String, Double> metrics,
            String message
    ) {
        return new CaptureMediaDiagnostic(
                code,
                severity,
                severity.blocksRestore(),
                Optional.of(frame.sourceKind()),
                Optional.of(frame.sourceId()),
                Optional.of(frame.callerOrder()),
                Optional.empty(),
                Optional.empty(),
                metrics,
                message
        );
    }

    private Map<String, Double> metrics(PaletteConfidenceSummary confidence) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("sampledModuleCount", (double) confidence.sampledModuleCount());
        values.put("shiftedSampleCount", (double) confidence.shiftedSampleCount());
        values.put("lowConfidenceSampleCount", (double) confidence.lowConfidenceSampleCount());
        values.put("rejectedSampleCount", (double) confidence.rejectedSampleCount());
        values.put("minimumPaletteConfidence", confidence.minimumConfidence());
        values.put("averagePaletteConfidence", confidence.averageConfidence());
        values.put("maximumPaletteRgbDistance", confidence.maximumRgbDistance());
        return Map.copyOf(values);
    }

    /**
     * Result of sampling one normalized media frame into validated tile payloads.
     *
     * @param status coarse frame sample status
     * @param payloads validated tile payloads
     * @param diagnostics media diagnostics emitted by sampling and validation
     * @param paletteConfidence aggregate confidence for sampled tile modules when available
     */
    public record FrameSample(
            FrameSampleStatus status,
            List<TilePayload> payloads,
            List<CaptureMediaDiagnostic> diagnostics,
            Optional<PaletteConfidenceSummary> paletteConfidence
    ) {

        /**
         * Creates an immutable frame sample result.
         *
         * @param status coarse frame sample status
         * @param payloads validated tile payloads
         * @param diagnostics media diagnostics
         * @param paletteConfidence aggregate palette confidence
         */
        public FrameSample {
            Objects.requireNonNull(status, "status must not be null");
            payloads = List.copyOf(Objects.requireNonNull(payloads, "payloads must not be null"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
            Objects.requireNonNull(paletteConfidence, "paletteConfidence must not be null");
            if (payloads.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("payloads must not contain null values");
            }
            if (diagnostics.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("diagnostics must not contain null values");
            }
        }

        private static FrameSample accepted(
                List<TilePayload> payloads,
                List<CaptureMediaDiagnostic> diagnostics,
                PaletteConfidenceSummary confidence
        ) {
            return new FrameSample(FrameSampleStatus.ACCEPTED, payloads, diagnostics, Optional.of(confidence));
        }

        private static FrameSample empty() {
            return new FrameSample(FrameSampleStatus.EMPTY, List.of(), List.of(), Optional.empty());
        }

        private static FrameSample rejected(
                List<TilePayload> payloads,
                List<CaptureMediaDiagnostic> diagnostics,
                Optional<PaletteConfidenceSummary> confidence
        ) {
            return new FrameSample(FrameSampleStatus.REJECTED, payloads, diagnostics, confidence);
        }
    }

    /**
     * Coarse status for one normalized media frame sample.
     */
    public enum FrameSampleStatus {
        /**
         * At least one tile payload was accepted after tile and envelope validation.
         */
        ACCEPTED,

        /**
         * The frame contained no signed tile content.
         */
        EMPTY,

        /**
         * The frame contained content but sampling or validation blocked acceptance.
         */
        REJECTED
    }

    /**
     * Diagnostic sampler evidence for one normalized media frame.
     *
     * @param sourceId source identifier of the normalized frame
     * @param layoutProfileId normalized layout profile id
     * @param samplingEvidence backend-neutral grid-phase and tile sampling evidence used by the sampler, when available
     * @param slots per-slot sampler evidence
     * @param candidateAttemptCount side-version attempts across all signed slots
     * @param noFinderAttemptCount attempts that failed finder-pattern checks
     * @param paletteRejectedAttemptCount attempts blocked by palette tolerance
     * @param decodedPayloadCount accepted payload count after tile and envelope validation
     */
    public record FrameInspection(
            String sourceId,
            String layoutProfileId,
            Optional<CvSamplingEvidence> samplingEvidence,
            List<SlotInspection> slots,
            int candidateAttemptCount,
            int noFinderAttemptCount,
            int paletteRejectedAttemptCount,
            int decodedPayloadCount
    ) {

        /**
         * Creates a validated immutable frame inspection.
         */
        public FrameInspection {
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("sourceId must not be blank");
            }
            if (layoutProfileId == null || layoutProfileId.isBlank()) {
                throw new IllegalArgumentException("layoutProfileId must not be blank");
            }
            Objects.requireNonNull(samplingEvidence, "samplingEvidence must not be null");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
            if (slots.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("slots must not contain null values");
            }
            if (candidateAttemptCount < 0
                    || noFinderAttemptCount < 0
                    || paletteRejectedAttemptCount < 0
                    || decodedPayloadCount < 0) {
                throw new IllegalArgumentException("inspection counts must be non-negative");
            }
        }
    }

    /**
     * Diagnostic sampler evidence for one rendered tile slot.
     *
     * @param tileIndex zero-based tile slot index
     * @param borderStatus border signature status
     * @param interiorContent true when the tile interior has non-empty content evidence
     * @param effectiveTileShiftXPx horizontal shift applied to the nominal tile slot before sampling
     * @param effectiveTileShiftYPx vertical shift applied to the nominal tile slot before sampling
     * @param effectiveTilePlacementSource source of the effective tile placement used for sampling
     * @param candidates per-side-version candidate evidence
     */
    public record SlotInspection(
            int tileIndex,
            BorderInspectionStatus borderStatus,
            boolean interiorContent,
            int effectiveTileShiftXPx,
            int effectiveTileShiftYPx,
            TileAlignmentInspectionSource effectiveTilePlacementSource,
            List<CandidateInspection> candidates
    ) {

        /**
         * Creates a validated immutable slot inspection.
         */
        public SlotInspection {
            if (tileIndex < 0) {
                throw new IllegalArgumentException("tileIndex must be non-negative");
            }
            Objects.requireNonNull(borderStatus, "borderStatus must not be null");
            Objects.requireNonNull(effectiveTilePlacementSource, "effectiveTilePlacementSource must not be null");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
            if (candidates.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("candidates must not contain null values");
            }
        }
    }

    /**
     * Diagnostic sampler evidence for one side-version attempt in one tile slot.
     *
     * @param sideVersion tile codec side version attempted
     * @param dimension logical tile module dimension for the side version
     * @param moduleSizePx rendered module size used for this side-version attempt
     * @param moduleCenterOffsetXPx horizontal module-center offset applied before palette sampling
     * @param moduleCenterOffsetYPx vertical module-center offset applied before palette sampling
     * @param moduleSamplingOffsetSource source of the module-center offset
     * @param areaSampleRadiusPx radius used for area palette sampling, or zero for center-only sampling
     * @param status finder and palette status for this attempt
     * @param decodeStatus tile/envelope validation status when a finder candidate existed
     * @param paletteConfidence aggregate palette confidence for sampled modules, when available
     */
    public record CandidateInspection(
            int sideVersion,
            int dimension,
            int moduleSizePx,
            int moduleCenterOffsetXPx,
            int moduleCenterOffsetYPx,
            ModuleSamplingInspectionSource moduleSamplingOffsetSource,
            int areaSampleRadiusPx,
            CandidateInspectionStatus status,
            DecodeInspectionStatus decodeStatus,
            Optional<PaletteConfidenceSummary> paletteConfidence
    ) {

        /**
         * Creates a validated immutable candidate inspection.
         */
        public CandidateInspection {
            if (sideVersion < 0) {
                throw new IllegalArgumentException("sideVersion must be non-negative");
            }
            if (dimension <= 0) {
                throw new IllegalArgumentException("dimension must be positive");
            }
            if (moduleSizePx < 0 || areaSampleRadiusPx < 0) {
                throw new IllegalArgumentException("sampling geometry values must be non-negative");
            }
            Objects.requireNonNull(moduleSamplingOffsetSource, "moduleSamplingOffsetSource must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(decodeStatus, "decodeStatus must not be null");
            Objects.requireNonNull(paletteConfidence, "paletteConfidence must not be null");
        }
    }

    /**
     * Diagnostic source of the tile placement used before border and module sampling.
     */
    public enum TileAlignmentInspectionSource {
        /**
         * Nominal fixed-layout placement was used without camera-derived alignment.
         */
        NOMINAL,

        /**
         * Backend-neutral sampling evidence selected the effective tile placement.
         */
        SAMPLING_EVIDENCE,

        /**
         * The camera-derived border scan selected the effective tile placement.
         */
        LEGACY_BORDER_SCAN
    }

    /**
     * Diagnostic source of module-center offsets used during palette sampling.
     */
    public enum ModuleSamplingInspectionSource {
        /**
         * No module-center offset was available or used.
         */
        NONE,

        /**
         * Per-tile sampling evidence supplied the module-center offset.
         */
        TILE_EVIDENCE,

        /**
         * Frame-level sampling evidence supplied the module-center offset.
         */
        FRAME_EVIDENCE,

        /**
         * A bounded camera-derived fallback search supplied the module-center offset.
         */
        FALLBACK_SEARCH
    }

    /**
     * Border-signature status for a tile slot inspection.
     */
    public enum BorderInspectionStatus {
        /**
         * The slot border matched the expected white tile signature.
         */
        SIGNATURE,

        /**
         * The slot border did not match the tile signature.
         */
        NO_SIGNATURE,

        /**
         * The slot border had samples outside the supported palette tolerance.
         */
        PALETTE_REJECTED
    }

    /**
     * Side-version candidate status before tile/envelope validation.
     */
    public enum CandidateInspectionStatus {
        /**
         * Sampled modules did not contain all expected finder patterns.
         */
        NO_FINDER,

        /**
         * Sampled modules contained one or more rejected palette samples.
         */
        PALETTE_REJECTED,

        /**
         * Sampled modules passed palette and finder checks and were passed to tile decode.
         */
        FINDER_CANDIDATE
    }

    /**
     * Tile/envelope validation status for a finder candidate.
     */
    public enum DecodeInspectionStatus {
        /**
         * Decode was not attempted because the side-version sample was not a finder candidate.
         */
        NOT_ATTEMPTED,

        /**
         * Tile decode and envelope validation accepted a payload for this slot.
         */
        ACCEPTED_PAYLOAD,

        /**
         * Tile decode, envelope CRC validation, or slot identity validation rejected this candidate.
         */
        REJECTED_BY_TILE_OR_ENVELOPE
    }

    /**
     * Aggregate confidence metrics for sampled palette modules.
     *
     * @param sampledModuleCount number of sampled modules
     * @param shiftedSampleCount number of non-exact palette samples
     * @param lowConfidenceSampleCount number of accepted samples that require warning diagnostics
     * @param rejectedSampleCount number of samples below the acceptance threshold
     * @param minimumConfidence lowest sample confidence
     * @param averageConfidence mean sample confidence
     * @param maximumRgbDistance largest RGB distance to the nearest palette color
     */
    public record PaletteConfidenceSummary(
            int sampledModuleCount,
            int shiftedSampleCount,
            int lowConfidenceSampleCount,
            int rejectedSampleCount,
            double minimumConfidence,
            double averageConfidence,
            double maximumRgbDistance
    ) {

        /**
         * Creates validated palette confidence metrics.
         *
         * @param sampledModuleCount sampled module count
         * @param shiftedSampleCount shifted sample count
         * @param lowConfidenceSampleCount low-confidence sample count
         * @param rejectedSampleCount rejected sample count
         * @param minimumConfidence minimum confidence
         * @param averageConfidence average confidence
         * @param maximumRgbDistance maximum RGB distance
         */
        public PaletteConfidenceSummary {
            if (sampledModuleCount <= 0) {
                throw new IllegalArgumentException("sampledModuleCount must be positive");
            }
            if (shiftedSampleCount < 0 || lowConfidenceSampleCount < 0 || rejectedSampleCount < 0) {
                throw new IllegalArgumentException("sample counts must be non-negative");
            }
            if (shiftedSampleCount > sampledModuleCount
                    || lowConfidenceSampleCount > sampledModuleCount
                    || rejectedSampleCount > sampledModuleCount) {
                throw new IllegalArgumentException("sample counts must not exceed sampledModuleCount");
            }
            requireUnitScore(minimumConfidence, "minimumConfidence");
            requireUnitScore(averageConfidence, "averageConfidence");
            if (!Double.isFinite(maximumRgbDistance) || maximumRgbDistance < 0.0d) {
                throw new IllegalArgumentException("maximumRgbDistance must be finite and non-negative");
            }
        }

        private static void requireUnitScore(double value, String fieldName) {
            if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
                throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
            }
        }
    }

    private enum SlotSampleStatus {
        EMPTY,
        NO_SIGNATURE_CONTENT,
        REJECTED,
        UNDECODABLE,
        DECODED
    }

    private record SlotSample(
            SlotSampleStatus status,
            Optional<TilePayload> payload,
            Optional<PaletteConfidenceSummary> paletteConfidence
    ) {

        private SlotSample {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(paletteConfidence, "paletteConfidence must not be null");
        }

        private static SlotSample empty() {
            return new SlotSample(SlotSampleStatus.EMPTY, Optional.empty(), Optional.empty());
        }

        private static SlotSample noSignatureContent() {
            return new SlotSample(SlotSampleStatus.NO_SIGNATURE_CONTENT, Optional.empty(), Optional.empty());
        }

        private static SlotSample rejected(PaletteConfidenceSummary confidence) {
            return new SlotSample(SlotSampleStatus.REJECTED, Optional.empty(), Optional.of(confidence));
        }

        private static SlotSample undecodable(Optional<PaletteConfidenceSummary> confidence) {
            return new SlotSample(SlotSampleStatus.UNDECODABLE, Optional.empty(), confidence);
        }

        private static SlotSample decoded(TilePayload payload, PaletteConfidenceSummary confidence) {
            return new SlotSample(SlotSampleStatus.DECODED, Optional.of(payload), Optional.of(confidence));
        }
    }

    private enum CandidateSampleStatus {
        NO_FINDER,
        REJECTED,
        CANDIDATE
    }

    private record TileAlignment(
            TilePlacement placement,
            int shiftXPx,
            int shiftYPx,
            TileAlignmentInspectionSource source
    ) {

        private TileAlignment {
            Objects.requireNonNull(placement, "placement must not be null");
            Objects.requireNonNull(source, "source must not be null");
        }

        private static TileAlignment of(
                TilePlacement originalPlacement,
                TilePlacement effectivePlacement,
                TileAlignmentInspectionSource source
        ) {
            Objects.requireNonNull(originalPlacement, "originalPlacement must not be null");
            Objects.requireNonNull(effectivePlacement, "effectivePlacement must not be null");
            return new TileAlignment(
                    effectivePlacement,
                    effectivePlacement.xPx() - originalPlacement.xPx(),
                    effectivePlacement.yPx() - originalPlacement.yPx(),
                    source
            );
        }
    }

    private record CandidateSamplingGeometry(
            int sideVersion,
            int dimension,
            int moduleSizePx,
            int contentOffsetXPx,
            int contentOffsetYPx,
            int moduleCenterOffsetXPx,
            int moduleCenterOffsetYPx,
            ModuleSamplingInspectionSource moduleSamplingOffsetSource,
            int areaSampleRadiusPx
    ) {

        private CandidateSamplingGeometry {
            if (sideVersion < 0) {
                throw new IllegalArgumentException("sideVersion must be non-negative");
            }
            if (dimension <= 0) {
                throw new IllegalArgumentException("dimension must be positive");
            }
            if (moduleSizePx < 0 || contentOffsetXPx < 0 || contentOffsetYPx < 0 || areaSampleRadiusPx < 0) {
                throw new IllegalArgumentException("sampling geometry values must be non-negative");
            }
            Objects.requireNonNull(moduleSamplingOffsetSource, "moduleSamplingOffsetSource must not be null");
        }

        private CandidateSamplingGeometry withModuleCenterOffset(
                int offsetXPx,
                int offsetYPx,
                ModuleSamplingInspectionSource offsetSource
        ) {
            return new CandidateSamplingGeometry(
                    sideVersion,
                    dimension,
                    moduleSizePx,
                    contentOffsetXPx,
                    contentOffsetYPx,
                    offsetXPx,
                    offsetYPx,
                    offsetSource,
                    areaSampleRadiusPx
            );
        }

        private CandidateSamplingGeometry withModuleSize(int newModuleSizePx, int quietZoneModules) {
            int logicalSide = dimension + (2 * quietZoneModules);
            double contentCenterXPx = contentOffsetXPx + ((double) logicalSide * moduleSizePx / 2.0d);
            double contentCenterYPx = contentOffsetYPx + ((double) logicalSide * moduleSizePx / 2.0d);
            int newContentOffsetXPx = Math.max(
                    0,
                    (int) Math.round(contentCenterXPx - ((double) logicalSide * newModuleSizePx / 2.0d))
            );
            int newContentOffsetYPx = Math.max(
                    0,
                    (int) Math.round(contentCenterYPx - ((double) logicalSide * newModuleSizePx / 2.0d))
            );
            return new CandidateSamplingGeometry(
                    sideVersion,
                    dimension,
                    newModuleSizePx,
                    newContentOffsetXPx,
                    newContentOffsetYPx,
                    moduleCenterOffsetXPx,
                    moduleCenterOffsetYPx,
                    ModuleSamplingInspectionSource.FALLBACK_SEARCH,
                    areaSampleRadiusPx
            );
        }
    }

    private record NearestPaletteColor(int paletteIndex, int paletteArgb, double rgbDistance) {
    }

    private record CandidateSample(
            CandidateSampleStatus status,
            Optional<LogicalTile> logicalTile,
            Optional<PaletteConfidenceSummary> optionalPaletteConfidence,
            CandidateSamplingGeometry geometry
    ) {

        private CandidateSample {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(logicalTile, "logicalTile must not be null");
            Objects.requireNonNull(
                    optionalPaletteConfidence,
                    "optionalPaletteConfidence must not be null"
            );
            Objects.requireNonNull(geometry, "geometry must not be null");
        }

        private PaletteConfidenceSummary paletteConfidence() {
            return optionalPaletteConfidence.orElseThrow();
        }

        private static CandidateSample noFinder(
                Optional<PaletteConfidenceSummary> confidence,
                CandidateSamplingGeometry geometry
        ) {
            return new CandidateSample(CandidateSampleStatus.NO_FINDER, Optional.empty(), confidence, geometry);
        }

        private static CandidateSample rejected(PaletteConfidenceSummary confidence, CandidateSamplingGeometry geometry) {
            return new CandidateSample(CandidateSampleStatus.REJECTED, Optional.empty(), Optional.of(confidence), geometry);
        }

        private static CandidateSample candidate(
                LogicalTile tile,
                PaletteConfidenceSummary confidence,
                CandidateSamplingGeometry geometry
        ) {
            return new CandidateSample(CandidateSampleStatus.CANDIDATE, Optional.of(tile), Optional.of(confidence), geometry);
        }
    }

    private enum BorderSampleStatus {
        SIGNATURE,
        NO_SIGNATURE,
        REJECTED
    }

    private record BorderEvidence(
            int sampleCount,
            int whiteSamples,
            int nonWhiteSamples,
            int rejectedSamples,
            BorderSideEvidence top,
            BorderSideEvidence bottom,
            BorderSideEvidence left,
            BorderSideEvidence right,
            PaletteSampleAccumulator rejectedConfidence
    ) {

        private BorderEvidence {
            if (sampleCount < 0 || whiteSamples < 0 || nonWhiteSamples < 0 || rejectedSamples < 0) {
                throw new IllegalArgumentException("border evidence counts must be non-negative");
            }
            Objects.requireNonNull(top, "top must not be null");
            Objects.requireNonNull(bottom, "bottom must not be null");
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
            Objects.requireNonNull(rejectedConfidence, "rejectedConfidence must not be null");
        }

        private double whiteRatio() {
            return ratio(whiteSamples, sampleCount);
        }

        private double nonWhiteRatio() {
            return ratio(nonWhiteSamples, sampleCount);
        }

        private double rejectedRatio() {
            return ratio(rejectedSamples, sampleCount);
        }

        private boolean exactLikeSignature() {
            return whiteRatio() >= MIN_BORDER_SIGNATURE_WHITE_RATIO
                    && nonWhiteRatio() <= MAX_BORDER_SIGNATURE_NON_WHITE_RATIO
                    && rejectedRatio() <= MAX_BORDER_SIGNATURE_REJECTED_RATIO;
        }

        private boolean cameraSignature() {
            return rejectedRatio() <= MAX_CAMERA_BORDER_REJECTED_RATIO
                    && whiteRatio() >= MIN_CAMERA_BORDER_WHITE_RATIO
                    && strongSideCount() >= 2;
        }

        private double alignmentScore() {
            return whiteRatio()
                    - (0.45d * nonWhiteRatio())
                    - (0.90d * rejectedRatio())
                    + (0.08d * strongSideCount())
                    + (0.03d * moderateSideCount());
        }

        private int strongSideCount() {
            return sideCountAtLeast(MIN_CAMERA_BORDER_STRONG_SIDE_RATIO);
        }

        private int moderateSideCount() {
            return sideCountAtLeast(MIN_CAMERA_BORDER_MODERATE_SIDE_RATIO);
        }

        private int sideCountAtLeast(double minimumWhiteRatio) {
            int count = 0;
            if (top.whiteRatio() >= minimumWhiteRatio) {
                count++;
            }
            if (bottom.whiteRatio() >= minimumWhiteRatio) {
                count++;
            }
            if (left.whiteRatio() >= minimumWhiteRatio) {
                count++;
            }
            if (right.whiteRatio() >= minimumWhiteRatio) {
                count++;
            }
            return count;
        }

        private PaletteConfidenceSummary paletteConfidence() {
            return rejectedConfidence.summary();
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 0.0d : (double) numerator / denominator;
        }
    }

    private record BorderSideEvidence(int sampleCount, int whiteSamples) {

        private BorderSideEvidence {
            if (sampleCount < 0 || whiteSamples < 0) {
                throw new IllegalArgumentException("border side counts must be non-negative");
            }
        }

        private double whiteRatio() {
            return sampleCount == 0 ? 0.0d : (double) whiteSamples / sampleCount;
        }
    }

    private static final class BorderEvidenceBuilder {

        private int sampleCount;
        private int whiteSamples;
        private int nonWhiteSamples;
        private int rejectedSamples;
        private int topSamples;
        private int topWhiteSamples;
        private int bottomSamples;
        private int bottomWhiteSamples;
        private int leftSamples;
        private int leftWhiteSamples;
        private int rightSamples;
        private int rightWhiteSamples;
        private final PaletteSampleAccumulator rejectedConfidence = new PaletteSampleAccumulator();

        private void add(CaptureMediaPaletteSample sample, boolean top, boolean bottom, boolean left, boolean right) {
            Objects.requireNonNull(sample, "sample must not be null");
            sampleCount++;
            boolean white = sample.accepted() && sample.paletteIndex() == WHITE_INDEX;
            if (white) {
                whiteSamples++;
            } else if (sample.accepted()) {
                nonWhiteSamples++;
            } else {
                rejectedSamples++;
                rejectedConfidence.add(sample);
            }
            addSideEvidence(top, bottom, left, right, white);
        }

        private void addSideEvidence(boolean top, boolean bottom, boolean left, boolean right, boolean white) {
            if (top) {
                topSamples++;
                topWhiteSamples += white ? 1 : 0;
            }
            if (bottom) {
                bottomSamples++;
                bottomWhiteSamples += white ? 1 : 0;
            }
            if (left) {
                leftSamples++;
                leftWhiteSamples += white ? 1 : 0;
            }
            if (right) {
                rightSamples++;
                rightWhiteSamples += white ? 1 : 0;
            }
        }

        private BorderEvidence toEvidence() {
            return new BorderEvidence(
                    sampleCount,
                    whiteSamples,
                    nonWhiteSamples,
                    rejectedSamples,
                    new BorderSideEvidence(topSamples, topWhiteSamples),
                    new BorderSideEvidence(bottomSamples, bottomWhiteSamples),
                    new BorderSideEvidence(leftSamples, leftWhiteSamples),
                    new BorderSideEvidence(rightSamples, rightWhiteSamples),
                    rejectedConfidence
            );
        }
    }

    private record BorderSample(BorderSampleStatus status, Optional<PaletteConfidenceSummary> paletteConfidence) {

        private BorderSample {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(paletteConfidence, "paletteConfidence must not be null");
        }

        private static BorderSample signature() {
            return new BorderSample(BorderSampleStatus.SIGNATURE, Optional.empty());
        }

        private static BorderSample noSignature() {
            return new BorderSample(BorderSampleStatus.NO_SIGNATURE, Optional.empty());
        }

        private static BorderSample rejected(PaletteConfidenceSummary confidence) {
            return new BorderSample(BorderSampleStatus.REJECTED, Optional.of(confidence));
        }
    }

    private static final class PaletteSampleAccumulator {

        private int sampledModuleCount;
        private int shiftedSampleCount;
        private int lowConfidenceSampleCount;
        private int rejectedSampleCount;
        private double totalConfidence;
        private double minimumConfidence = 1.0d;
        private double maximumRgbDistance;

        private void add(CaptureMediaPaletteSample sample) {
            Objects.requireNonNull(sample, "sample must not be null");
            sampledModuleCount++;
            if (sample.rgbDistance() > 0.0d) {
                shiftedSampleCount++;
            }
            if (sample.status() == CaptureMediaPaletteSampleStatus.LOW_CONFIDENCE) {
                lowConfidenceSampleCount++;
            }
            if (!sample.accepted()) {
                rejectedSampleCount++;
            }
            totalConfidence += sample.confidence();
            minimumConfidence = Math.min(minimumConfidence, sample.confidence());
            maximumRgbDistance = Math.max(maximumRgbDistance, sample.rgbDistance());
        }

        private void add(PaletteConfidenceSummary summary) {
            Objects.requireNonNull(summary, "summary must not be null");
            sampledModuleCount += summary.sampledModuleCount();
            shiftedSampleCount += summary.shiftedSampleCount();
            lowConfidenceSampleCount += summary.lowConfidenceSampleCount();
            rejectedSampleCount += summary.rejectedSampleCount();
            totalConfidence += summary.averageConfidence() * summary.sampledModuleCount();
            minimumConfidence = Math.min(minimumConfidence, summary.minimumConfidence());
            maximumRgbDistance = Math.max(maximumRgbDistance, summary.maximumRgbDistance());
        }

        private PaletteConfidenceSummary summary() {
            if (sampledModuleCount <= 0) {
                throw new IllegalStateException("palette confidence requires at least one sample");
            }
            return new PaletteConfidenceSummary(
                    sampledModuleCount,
                    shiftedSampleCount,
                    lowConfidenceSampleCount,
                    rejectedSampleCount,
                    minimumConfidence,
                    totalConfidence / sampledModuleCount,
                    maximumRgbDistance
            );
        }
    }
}
