package com.alx4j.jab4j.reader.capture.media.sample;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.tile.TileDecoder;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;
import com.alx4j.jab4j.transfer.TransportException;

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
    private static final double CAMERA_MAX_SPARSE_REJECTED_RATIO = 0.06d;
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
    private static final int FINDER_SIZE_MODULES = 3;
    private static final int CAMERA_MIN_RECOVERABLE_FINDER_COUNT = 3;
    private static final int CAMERA_MIN_MATCHES_PER_RECOVERABLE_FINDER = 6;
    private static final double MIN_CAMERA_FALLBACK_RECOVERABLE_FINDER_AVERAGE_CONFIDENCE = 0.30d;
    private static final double MAX_PROPORTIONAL_LAYOUT_SCALE_ERROR = 0.01d;

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
        boolean partialAcceptanceAllowed = cameraDerived(frame);
        List<TilePayload> payloads = new ArrayList<>();
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>();
        List<CaptureMediaDiagnostic> blockingDiagnostics = new ArrayList<>();
        PaletteSampleAccumulator acceptedConfidence = new PaletteSampleAccumulator();
        boolean failedSlotEncountered = false;
        Optional<PaletteConfidenceSummary> failedSlotConfidence = Optional.empty();
        int partialRejectedSlotCount = 0;
        int partialUndecodableSlotCount = 0;
        List<TilePlacement> placements = layoutPlan.tilePlacements();
        for (int tileIndex = 0; tileIndex < placements.size(); tileIndex++) {
            SlotSample slotSample = sampleSlot(frame, layoutPlan, placements.get(tileIndex), tileIndex, samplingEvidence);
            if (slotSample.status() == SlotSampleStatus.EMPTY
                    || slotSample.status() == SlotSampleStatus.NO_SIGNATURE_CONTENT) {
                continue;
            }
            if (slotSample.status() == SlotSampleStatus.REJECTED) {
                PaletteConfidenceSummary confidence = slotSample.paletteConfidence().orElseThrow();
                CaptureMediaDiagnostic diagnostic = colorDiagnostic(
                        frame,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        confidence,
                        "Media tile samples are outside the supported color/compression threshold"
                );
                if (!partialAcceptanceAllowed) {
                    diagnostics.add(diagnostic);
                    return FrameSample.rejected(List.of(), diagnostics, Optional.of(confidence));
                }
                if (!failedSlotEncountered) {
                    failedSlotEncountered = true;
                    failedSlotConfidence = Optional.of(confidence);
                }
                blockingDiagnostics.add(diagnostic);
                partialRejectedSlotCount++;
                continue;
            }
            if (slotSample.status() == SlotSampleStatus.UNDECODABLE) {
                Optional<PaletteConfidenceSummary> confidence = slotSample.paletteConfidence();
                CaptureMediaDiagnostic diagnostic = colorDiagnostic(
                        frame,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        confidence.orElse(null),
                        "Media tile content passed palette sampling but failed tile decode or envelope CRC validation"
                );
                if (!partialAcceptanceAllowed) {
                    diagnostics.add(diagnostic);
                    return FrameSample.rejected(List.of(), diagnostics, confidence);
                }
                if (!failedSlotEncountered) {
                    failedSlotEncountered = true;
                    failedSlotConfidence = confidence;
                }
                blockingDiagnostics.add(diagnostic);
                partialUndecodableSlotCount++;
                continue;
            }
            payloads.add(slotSample.payload().orElseThrow());
            acceptedConfidence.add(slotSample.paletteConfidence().orElseThrow());
        }

        if (payloads.isEmpty()) {
            if (!blockingDiagnostics.isEmpty()) {
                return FrameSample.rejected(List.of(), blockingDiagnostics, failedSlotConfidence);
            }
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
        if (partialRejectedSlotCount > 0 || partialUndecodableSlotCount > 0) {
            diagnostics.add(partialAcceptanceDiagnostic(
                    frame,
                    payloads.size(),
                    partialRejectedSlotCount,
                    partialUndecodableSlotCount,
                    placements.size()
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
                    0,
                    List.of(),
                    Optional.empty(),
                    ProfileSelectionSource.NONE
            );
        }

        List<ProfileAttemptInspection> profileAttempts = new ArrayList<>();
        FrameInspection selectedInspection = null;
        FrameInspection bestInspection = null;
        for (LayoutProfile layout : layouts) {
            FrameInspection inspection = inspect(frame, layoutPlanner.plan(layout));
            profileAttempts.add(profileAttemptInspection(layout, inspection));
            if (inspection.decodedPayloadCount() > 0) {
                if (selectedInspection == null) {
                    selectedInspection = inspection;
                }
            }
            bestInspection = betterInspection(bestInspection, inspection);
        }
        FrameInspection selectedOrBest = selectedInspection == null ? bestInspection : selectedInspection;
        Optional<String> selectedLayoutProfileId = selectedPayloadLayoutProfileId(profileAttempts);
        ProfileSelectionSource selectionSource = selectedLayoutProfileId.isPresent()
                ? ProfileSelectionSource.DECODED_PAYLOAD
                : ProfileSelectionSource.NONE;
        return selectedOrBest.withProfileAttempts(profileAttempts, selectedLayoutProfileId, selectionSource);
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
        double scaleX = (double) frameWidthPx / profile.frameWidthPx();
        double scaleY = (double) frameHeightPx / profile.frameHeightPx();
        if (!proportionalScale(scaleX, scaleY)) {
            return Optional.empty();
        }
        return Optional.of(new LayoutProfile(
                profile.profileId(),
                profile.rows(),
                profile.cols(),
                frameWidthPx,
                frameHeightPx,
                scaledPixels(profile.tileGapPx(), scaleX),
                scaledPixels(profile.outerMarginPx(), scaleX),
                profile.separatorStyle(),
                scaledPixels(profile.topSyncBandPx(), scaleX),
                scaledPixels(profile.metadataBandPx(), scaleX),
                profile.backgroundStyle(),
                profile.fitPolicy()
        ));
    }

    private boolean proportionalScale(double scaleX, double scaleY) {
        if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY) || scaleX <= 0.0d || scaleY <= 0.0d) {
            return false;
        }
        double error = Math.abs(scaleX - scaleY) / Math.max(scaleX, scaleY);
        return error <= MAX_PROPORTIONAL_LAYOUT_SCALE_ERROR;
    }

    private int scaledPixels(int pixels, double scale) {
        return Math.max(0, (int) Math.round(pixels * scale));
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

    private ProfileAttemptInspection profileAttemptInspection(LayoutProfile layout, FrameInspection inspection) {
        int tileDecodeAttemptCount = tileDecodeAttemptCount(inspection);
        int layoutProfileMismatchCount = slotValidationMismatchCount(
                inspection,
                "slotValidation.layoutProfileMismatch"
        );
        int tileIndexMismatchCount = slotValidationMismatchCount(inspection, "slotValidation.tileIndexMismatch");
        int totalTilesMismatchCount = slotValidationMismatchCount(inspection, "slotValidation.totalTilesMismatch");
        return new ProfileAttemptInspection(
                layout.profileId(),
                layout.rows(),
                layout.cols(),
                profileAttemptStatus(inspection, tileDecodeAttemptCount),
                inspection.decodedPayloadCount(),
                tileDecodeAttemptCount,
                layoutProfileMismatchCount,
                tileIndexMismatchCount,
                totalTilesMismatchCount,
                decodedPayloadLayoutProfileId(inspection)
        );
    }

    private ProfileAttemptInspectionStatus profileAttemptStatus(
            FrameInspection inspection,
            int tileDecodeAttemptCount
    ) {
        if (inspection.decodedPayloadCount() > 0) {
            return ProfileAttemptInspectionStatus.ACCEPTED;
        }
        if (tileDecodeAttemptCount > 0
                || inspection.candidateAttemptCount() > 0
                || inspection.noFinderAttemptCount() > 0
                || inspection.paletteRejectedAttemptCount() > 0
                || inspection.slots().stream().anyMatch(this::hasRejectedProfileAttemptEvidence)) {
            return ProfileAttemptInspectionStatus.REJECTED;
        }
        return ProfileAttemptInspectionStatus.EMPTY;
    }

    private boolean hasRejectedProfileAttemptEvidence(SlotInspection slot) {
        return slot.interiorContent()
                || slot.borderStatus() == BorderInspectionStatus.SIGNATURE
                || slot.borderStatus() == BorderInspectionStatus.PALETTE_REJECTED;
    }

    private int tileDecodeAttemptCount(FrameInspection inspection) {
        int attemptCount = 0;
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.decodeStatus() != DecodeInspectionStatus.NOT_ATTEMPTED) {
                    attemptCount++;
                }
            }
        }
        return attemptCount;
    }

    private int slotValidationMismatchCount(FrameInspection inspection, String mismatchDiagnosticName) {
        int count = 0;
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if ("true".equals(candidate.decodeDiagnostics().get(mismatchDiagnosticName))) {
                    count++;
                }
            }
        }
        return count;
    }

    private Optional<String> decodedPayloadLayoutProfileId(FrameInspection inspection) {
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.decodedPayloadLayoutProfileId().isPresent()) {
                    return candidate.decodedPayloadLayoutProfileId();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> selectedPayloadLayoutProfileId(List<ProfileAttemptInspection> attempts) {
        for (ProfileAttemptInspection attempt : attempts) {
            if (attempt.decodedPayloadLayoutProfileId().isPresent()) {
                return attempt.decodedPayloadLayoutProfileId();
            }
        }
        return Optional.empty();
    }

    private SlotSample sampleSlot(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
        if (!cameraDerived(frame)) {
            TileAlignment alignment = TileAlignment.of(
                    placement,
                    placement,
                    TileAlignmentInspectionSource.NOMINAL,
                    TileEvidenceAlignmentStatus.NOT_CAMERA_DERIVED
            );
            return sampleSlotWithAlignment(frame, layoutPlan, tileIndex, samplingEvidence, alignment, false);
        }

        EvidenceAlignment evidenceAlignment = evidenceAlignedPlacement(frame, layoutPlan, placement, tileIndex, samplingEvidence);
        if (evidenceAlignment.alignment().isPresent()) {
            TileAlignment alignment = evidenceAlignment.alignment().orElseThrow();
            boolean finderAssisted = alignment.samplingEvidenceAlignmentStatus()
                    == TileEvidenceAlignmentStatus.BORDER_SIGNATURE_FAILED;
            SlotSample evidenceSample = sampleSlotWithAlignment(
                    frame,
                    layoutPlan,
                    tileIndex,
                    samplingEvidence,
                    finderAssisted
                            ? alignment.withSamplingEvidenceAlignmentStatus(TileEvidenceAlignmentStatus.USED_FINDER_EVIDENCE)
                            : alignment,
                    finderAssisted
            );
            if (!finderAssisted || evidenceSample.status() == SlotSampleStatus.DECODED) {
                return evidenceSample;
            }
        }

        TileAlignment legacyAlignment = legacyAlignedTilePlacement(
                frame,
                layoutPlan,
                placement,
                evidenceAlignment.status()
        );
        return sampleSlotWithAlignment(frame, layoutPlan, tileIndex, samplingEvidence, legacyAlignment, false);
    }

    private SlotSample sampleSlotWithAlignment(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence,
            TileAlignment alignment,
            boolean allowFinderAssistedEvidence
    ) {
        TilePlacement effectivePlacement = alignment.placement();
        BorderSample borderSample = sampleRenderedTileBorder(frame, layoutPlan, effectivePlacement);
        if (borderSample.status() != BorderSampleStatus.SIGNATURE) {
            boolean interiorContent = hasInteriorContent(frame, layoutPlan, effectivePlacement);
            if (allowFinderAssistedEvidence && interiorContent) {
                SlotSample finderAssistedSample = sampleSignedContent(
                        frame,
                        layoutPlan,
                        effectivePlacement,
                        tileIndex,
                        samplingEvidence
                );
                if (finderAssistedSample.status() == SlotSampleStatus.DECODED) {
                    return finderAssistedSample;
                }
            }
            if (borderSample.status() == BorderSampleStatus.REJECTED
                    && interiorContent) {
                return SlotSample.rejected(borderSample.paletteConfidence().orElseThrow());
            }
            return interiorContent
                    ? SlotSample.noSignatureContent()
                    : SlotSample.empty();
        }

        return sampleSignedContent(frame, layoutPlan, effectivePlacement, tileIndex, samplingEvidence);
    }

    private SlotSample sampleSignedContent(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement effectivePlacement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
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
        if (!cameraDerived(frame)) {
            TileAlignment alignment = TileAlignment.of(
                    placement,
                    placement,
                    TileAlignmentInspectionSource.NOMINAL,
                    TileEvidenceAlignmentStatus.NOT_CAMERA_DERIVED
            );
            return inspectSlotWithAlignment(frame, layoutPlan, tileIndex, samplingEvidence, alignment, false);
        }

        EvidenceAlignment evidenceAlignment = evidenceAlignedPlacement(frame, layoutPlan, placement, tileIndex, samplingEvidence);
        if (evidenceAlignment.alignment().isPresent()) {
            TileAlignment alignment = evidenceAlignment.alignment().orElseThrow();
            boolean finderAssisted = alignment.samplingEvidenceAlignmentStatus()
                    == TileEvidenceAlignmentStatus.BORDER_SIGNATURE_FAILED;
            SlotInspection evidenceInspection = inspectSlotWithAlignment(
                    frame,
                    layoutPlan,
                    tileIndex,
                    samplingEvidence,
                    finderAssisted
                            ? alignment.withSamplingEvidenceAlignmentStatus(TileEvidenceAlignmentStatus.USED_FINDER_EVIDENCE)
                            : alignment,
                    finderAssisted
            );
            if (!finderAssisted || hasAcceptedPayload(evidenceInspection)) {
                return evidenceInspection;
            }
        }

        TileAlignment legacyAlignment = legacyAlignedTilePlacement(
                frame,
                layoutPlan,
                placement,
                evidenceAlignment.status()
        );
        return inspectSlotWithAlignment(frame, layoutPlan, tileIndex, samplingEvidence, legacyAlignment, false);
    }

    private SlotInspection inspectSlotWithAlignment(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence,
            TileAlignment alignment,
            boolean allowFinderAssistedEvidence
    ) {
        TilePlacement effectivePlacement = alignment.placement();
        BorderSample borderSample = sampleRenderedTileBorder(frame, layoutPlan, effectivePlacement);
        boolean interiorContent = hasInteriorContent(frame, layoutPlan, effectivePlacement);
        if (borderSample.status() != BorderSampleStatus.SIGNATURE) {
            if (allowFinderAssistedEvidence && interiorContent) {
                List<CandidateInspection> candidates = inspectSignedContent(
                        frame,
                        layoutPlan,
                        effectivePlacement,
                        tileIndex,
                        samplingEvidence
                );
                if (candidates.stream()
                        .anyMatch(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)) {
                    return new SlotInspection(
                            tileIndex,
                            inspectionStatus(borderSample.status()),
                            interiorContent,
                            alignment.shiftXPx(),
                            alignment.shiftYPx(),
                            alignment.source(),
                            alignment.samplingEvidenceAlignmentStatus(),
                            candidates
                    );
                }
            }
            return new SlotInspection(
                    tileIndex,
                    inspectionStatus(borderSample.status()),
                    interiorContent,
                    alignment.shiftXPx(),
                    alignment.shiftYPx(),
                    alignment.source(),
                    alignment.samplingEvidenceAlignmentStatus(),
                    List.of()
            );
        }

        List<CandidateInspection> candidates = inspectSignedContent(
                frame,
                layoutPlan,
                effectivePlacement,
                tileIndex,
                samplingEvidence
        );
        return new SlotInspection(
                tileIndex,
                inspectionStatus(borderSample.status()),
                interiorContent,
                alignment.shiftXPx(),
                alignment.shiftYPx(),
                alignment.source(),
                alignment.samplingEvidenceAlignmentStatus(),
                candidates
        );
    }

    private boolean hasAcceptedPayload(SlotInspection inspection) {
        return inspection.candidates().stream()
                .anyMatch(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD);
    }

    private List<CandidateInspection> inspectSignedContent(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement effectivePlacement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
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
        return List.copyOf(candidates);
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

    private TileAlignment legacyAlignedTilePlacement(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            TileEvidenceAlignmentStatus evidenceAlignmentStatus
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
        return TileAlignment.of(
                placement,
                bestPlacement,
                TileAlignmentInspectionSource.LEGACY_BORDER_SCAN,
                evidenceAlignmentStatus
        );
    }

    private EvidenceAlignment evidenceAlignedPlacement(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            Optional<CvSamplingEvidence> samplingEvidence
    ) {
        if (samplingEvidence.isEmpty()) {
            return EvidenceAlignment.unavailable(TileEvidenceAlignmentStatus.NO_SAMPLING_EVIDENCE);
        }
        CvSamplingEvidence evidence = samplingEvidence.orElseThrow();
        if (!usableSamplingEvidence(evidence)) {
            return EvidenceAlignment.unavailable(TileEvidenceAlignmentStatus.CONFIDENCE_BELOW_THRESHOLD);
        }
        TileAlignment firstInBoundsAlignment = null;
        boolean outOfBounds = false;
        for (AlignmentOffset offset : evidenceAlignmentOffsets(evidence, tileIndex)) {
            Optional<TilePlacement> shiftedPlacement = shiftedPlacement(frame, placement, offset.xPx(), offset.yPx());
            if (shiftedPlacement.isEmpty()) {
                outOfBounds = true;
                continue;
            }
            TileAlignment alignment = TileAlignment.of(
                    placement,
                    shiftedPlacement.orElseThrow(),
                    TileAlignmentInspectionSource.SAMPLING_EVIDENCE,
                    TileEvidenceAlignmentStatus.BORDER_SIGNATURE_FAILED
            );
            if (firstInBoundsAlignment == null) {
                firstInBoundsAlignment = alignment;
            }
            BorderEvidence evidenceBorder = sampleBorderEvidence(
                    frame,
                    alignment.placement(),
                    layoutPlan.separatorThicknessPx(),
                    CAMERA_SLOT_ALIGNMENT_SAMPLE_STRIDE_PX
            );
            if (evidenceBorder.cameraSignature()) {
                return EvidenceAlignment.available(
                        alignment.withSamplingEvidenceAlignmentStatus(TileEvidenceAlignmentStatus.USED_BORDER_SIGNATURE)
                );
            }
        }
        if (firstInBoundsAlignment != null) {
            return EvidenceAlignment.available(firstInBoundsAlignment);
        }
        return EvidenceAlignment.unavailable(outOfBounds
                ? TileEvidenceAlignmentStatus.OUT_OF_BOUNDS
                : TileEvidenceAlignmentStatus.NO_SAMPLING_EVIDENCE);
    }

    private List<AlignmentOffset> evidenceAlignmentOffsets(CvSamplingEvidence evidence, int tileIndex) {
        List<AlignmentOffset> offsets = new ArrayList<>();
        evidence.tileEvidence(tileIndex)
                .filter(this::usableTileEvidence)
                .ifPresent(tileEvidence -> addAlignmentOffset(
                        offsets,
                        tileEvidence.moduleCenterOffsetXPx(),
                        tileEvidence.moduleCenterOffsetYPx()
                ));
        evidence.gridPhase()
                .filter(this::usableGridPhase)
                .ifPresent(gridPhase -> addAlignmentOffset(
                        offsets,
                        gridPhase.offsetXPx(),
                        gridPhase.offsetYPx()
                ));
        return List.copyOf(offsets);
    }

    private void addAlignmentOffset(List<AlignmentOffset> offsets, double offsetX, double offsetY) {
        AlignmentOffset offset = new AlignmentOffset(
                roundedAlignmentOffset(offsetX),
                roundedAlignmentOffset(offsetY)
        );
        if (!offsets.contains(offset)) {
            offsets.add(offset);
        }
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
        Optional<String> tileOrEnvelopeRejectedReason = Optional.empty();
        Map<String, String> tileOrEnvelopeRejectedDiagnostics = Map.of();
        CandidateSample rejected = null;
        CandidateSample noFinder = null;
        for (CandidateSample attempt : attempts) {
            if (attempt.status() == CandidateSampleStatus.CANDIDATE) {
                DecodeAttempt decodeAttempt = decodeAttempt(layoutPlan, tileIndex, attempt.logicalTile().orElseThrow());
                if (decodeAttempt.payload().isPresent()) {
                    TilePayload payload = decodeAttempt.payload().orElseThrow();
                    return candidateInspection(
                            attempt,
                            DecodeInspectionStatus.ACCEPTED_PAYLOAD,
                            Optional.empty(),
                            decodeAttempt.diagnostics(),
                            Optional.of(payload.layoutProfileId())
                    );
                }
                if (tileOrEnvelopeRejected == null) {
                    tileOrEnvelopeRejected = attempt;
                    tileOrEnvelopeRejectedReason = decodeAttempt.rejectionReason();
                    tileOrEnvelopeRejectedDiagnostics = decodeAttempt.diagnostics();
                }
            } else if (attempt.status() == CandidateSampleStatus.REJECTED) {
                rejected = lowerConfidence(rejected, attempt);
            } else if (noFinder == null) {
                noFinder = attempt;
            }
        }
        if (tileOrEnvelopeRejected != null) {
            return candidateInspection(
                    tileOrEnvelopeRejected,
                    DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE,
                    tileOrEnvelopeRejectedReason,
                    tileOrEnvelopeRejectedDiagnostics
            );
        }
        if (rejected != null) {
            return candidateInspection(rejected, DecodeInspectionStatus.NOT_ATTEMPTED);
        }
        return candidateInspection(noFinder == null ? attempts.get(0) : noFinder, DecodeInspectionStatus.NOT_ATTEMPTED);
    }

    private CandidateInspection candidateInspection(CandidateSample candidate, DecodeInspectionStatus decodeStatus) {
        return candidateInspection(candidate, decodeStatus, Optional.empty(), Map.of(), Optional.empty());
    }

    private CandidateInspection candidateInspection(
            CandidateSample candidate,
            DecodeInspectionStatus decodeStatus,
            Optional<String> decodeFailureReason
    ) {
        return candidateInspection(candidate, decodeStatus, decodeFailureReason, Map.of(), Optional.empty());
    }

    private CandidateInspection candidateInspection(
            CandidateSample candidate,
            DecodeInspectionStatus decodeStatus,
            Optional<String> decodeFailureReason,
            Map<String, String> decodeDiagnostics
    ) {
        return candidateInspection(candidate, decodeStatus, decodeFailureReason, decodeDiagnostics, Optional.empty());
    }

    private CandidateInspection candidateInspection(
            CandidateSample candidate,
            DecodeInspectionStatus decodeStatus,
            Optional<String> decodeFailureReason,
            Map<String, String> decodeDiagnostics,
            Optional<String> decodedPayloadLayoutProfileId
    ) {
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
                candidate.optionalPaletteConfidence(),
                candidate.logicalTile()
                        .map(LogicalTile::diagnostics)
                        .orElse(Map.of()),
                decodeFailureReason,
                decodeDiagnostics,
                decodedPayloadLayoutProfileId
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
        boolean cameraDerivedCandidate = cameraDerived(frame);
        FinderPatternSummary finderSummary = finderPatternSummary(moduleColors, dimension);
        if (!finderSummary.supported(cameraDerivedCandidate)) {
            return CandidateSample.noFinder(Optional.of(summary), geometry);
        }
        if (!credibleRecoverableFinderCandidate(
                cameraDerivedCandidate,
                finderSummary,
                summary,
                geometry
        )) {
            return CandidateSample.noFinder(Optional.of(summary), geometry);
        }
        boolean finderCanonicalized = cameraDerivedCandidate && !finderSummary.exact();
        if (finderCanonicalized) {
            moduleColors = canonicalizedFinderPatterns(moduleColors, dimension);
        }

        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sampledSideVersion", Integer.toString(geometry.sideVersion()));
        diagnostics.put("sampledDimension", Integer.toString(dimension));
        diagnostics.put("layoutProfileId", layoutPlan.profile().profileId());
        diagnostics.put("tileIndex", Integer.toString(tileIndex));
        diagnostics.put("finderExact", Boolean.toString(finderSummary.exact()));
        diagnostics.put("finderRecoverableCount", Integer.toString(finderSummary.recoverableCount()));
        diagnostics.put("finderCanonicalized", Boolean.toString(finderCanonicalized));
        diagnostics.put("minimumPaletteConfidence", formatMetric(summary.minimumConfidence()));
        diagnostics.put("averagePaletteConfidence", formatMetric(summary.averageConfidence()));
        diagnostics.put("maximumPaletteRgbDistance", formatMetric(summary.maximumRgbDistance()));
        diagnostics.put("lowConfidenceSampleCount", Integer.toString(summary.lowConfidenceSampleCount()));
        diagnostics.put("sparseCameraOutliersAccepted", Boolean.toString(sparseCameraOutliersAccepted));
        diagnostics.put("rejectedSampleCount", Integer.toString(summary.rejectedSampleCount()));
        diagnostics.put("sampledMatrixSha256", sha256Hex(moduleColorBytes(moduleColors)));
        diagnostics.put("sampledMatrixPrefix", moduleColorPrefix(moduleColors, 64));
        diagnostics.put("sampledMatrixHistogram", moduleColorHistogram(moduleColors));
        return CandidateSample.candidate(new LogicalTile(
                dimension,
                dimension,
                tileCodecProfile.quietZoneModules(),
                tileCodecProfile.profileId(),
                moduleColors,
                diagnostics
        ), summary, geometry);
    }

    private boolean credibleRecoverableFinderCandidate(
            boolean cameraDerivedCandidate,
            FinderPatternSummary finderSummary,
            PaletteConfidenceSummary summary,
            CandidateSamplingGeometry geometry
    ) {
        if (!cameraDerivedCandidate
                || finderSummary.exact()
                || geometry.moduleSamplingOffsetSource() != ModuleSamplingInspectionSource.FALLBACK_SEARCH) {
            return true;
        }
        return summary.averageConfidence() >= MIN_CAMERA_FALLBACK_RECOVERABLE_FINDER_AVERAGE_CONFIDENCE;
    }

    private byte[] moduleColorBytes(List<Integer> moduleColors) {
        byte[] bytes = new byte[moduleColors.size()];
        for (int index = 0; index < moduleColors.size(); index++) {
            bytes[index] = moduleColors.get(index).byteValue();
        }
        return bytes;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String moduleColorPrefix(List<Integer> moduleColors, int limit) {
        StringBuilder builder = new StringBuilder(Math.min(moduleColors.size(), limit));
        for (int index = 0; index < moduleColors.size() && index < limit; index++) {
            builder.append(Integer.toHexString(moduleColors.get(index)));
        }
        if (moduleColors.size() > limit) {
            builder.append("...");
        }
        return builder.toString();
    }

    private String moduleColorHistogram(List<Integer> moduleColors) {
        int[] counts = new int[paletteArgb.size()];
        for (Integer moduleColor : moduleColors) {
            if (moduleColor >= 0 && moduleColor < counts.length) {
                counts[moduleColor]++;
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < counts.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(index).append(':').append(counts[index]);
        }
        return builder.toString();
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
        CandidateSamplingGeometry compactGeometry = geometry.withModuleSize(
                geometry.moduleSizePx() - (2 * moduleSizeStep),
                tileCodecProfile.quietZoneModules()
        );
        addFallbackFinderPhaseGeometries(geometries, compactGeometry);
        return List.copyOf(geometries);
    }

    private void addFallbackFinderPhaseGeometries(
            List<CandidateSamplingGeometry> geometries,
            CandidateSamplingGeometry base
    ) {
        if (base.moduleSizePx() < MIN_MODULE_SIZE_PX) {
            return;
        }
        int horizontalOffset = Math.max(1, base.moduleSizePx());
        int moderateVerticalOffset = 2 * base.moduleSizePx();
        int wideVerticalOffset = 3 * base.moduleSizePx();
        addFallbackFinderPhaseGeometries(geometries, base, horizontalOffset, moderateVerticalOffset);
        addFallbackFinderPhaseGeometries(geometries, base, horizontalOffset, -moderateVerticalOffset);
        addFallbackFinderPhaseGeometries(geometries, base, horizontalOffset, wideVerticalOffset);
        addFallbackFinderPhaseGeometries(geometries, base, horizontalOffset, -wideVerticalOffset);
    }

    private void addFallbackFinderPhaseGeometries(
            List<CandidateSamplingGeometry> geometries,
            CandidateSamplingGeometry base,
            int horizontalOffset,
            int verticalOffset
    ) {
        addFallbackSamplingGeometry(geometries, base, 0, verticalOffset);
        addFallbackSamplingGeometry(geometries, base, -horizontalOffset, verticalOffset);
        addFallbackSamplingGeometry(geometries, base, horizontalOffset, verticalOffset);
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
        Optional<CvSamplingEvidence> frameEvidence = frame.samplingEvidence()
                .filter(CvSamplingEvidence::available);
        if (frameEvidence.isPresent()) {
            return frameEvidence;
        }
        try {
            return samplingEvidenceProvider.evidenceFor(frame, layoutPlan);
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

    private FinderPatternSummary finderPatternSummary(List<Integer> moduleColors, int dimension) {
        FinderMatch topLeft = finderMatch(moduleColors, dimension, 0, 0, 0);
        FinderMatch topRight = finderMatch(moduleColors, dimension, 0, dimension - FINDER_SIZE_MODULES, 0);
        FinderMatch bottomLeft = finderMatch(moduleColors, dimension, dimension - FINDER_SIZE_MODULES, 0, 6);
        FinderMatch bottomRight = finderMatch(
                moduleColors,
                dimension,
                dimension - FINDER_SIZE_MODULES,
                dimension - FINDER_SIZE_MODULES,
                3
        );
        return new FinderPatternSummary(topLeft, topRight, bottomLeft, bottomRight);
    }

    private FinderMatch finderMatch(
            List<Integer> moduleColors,
            int dimension,
            int startRow,
            int startCol,
            int expectedColor
    ) {
        int matches = 0;
        for (int row = startRow; row < startRow + 3; row++) {
            for (int col = startCol; col < startCol + 3; col++) {
                if (moduleColors.get((row * dimension) + col) == expectedColor) {
                    matches++;
                }
            }
        }
        return new FinderMatch(matches);
    }

    private TilePayload decodeCandidate(FixedLayoutPlan layoutPlan, int tileIndex, LogicalTile candidate) {
        return decodeAttempt(layoutPlan, tileIndex, candidate).payload().orElse(null);
    }

    private DecodeAttempt decodeAttempt(FixedLayoutPlan layoutPlan, int tileIndex, LogicalTile candidate) {
        try {
            byte[] envelope = tileDecoder.decode(candidate, tileCodecProfile);
            TilePayload payload = envelopeCodec.parse(envelope, SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION);
            SlotValidationResult slotValidation = slotValidationResult(layoutPlan, tileIndex, payload);
            return slotValidation.rejectionReason()
                    .map(reason -> DecodeAttempt.rejected(reason, slotValidation.diagnostics()))
                    .orElseGet(() -> DecodeAttempt.accepted(payload));
        } catch (TileCodecException exception) {
            String reason = "tileDecode: " + exception.getMessage();
            return DecodeAttempt.rejected(
                    reason,
                    CaptureMediaTileDecodeDiagnostics.inspect(candidate, tileCodecProfile, Optional.of(reason))
            );
        } catch (TransportException exception) {
            return DecodeAttempt.rejected(
                    "envelopeValidation: " + exception.getMessage(),
                    Map.of(
                            "envelopeValidation.stage", "ENVELOPE_VALIDATION",
                            "envelopeValidation.reason", exception.getMessage()
                    )
            );
        } catch (RuntimeException exception) {
            return DecodeAttempt.rejected(
                    "unexpected: " + exception.getClass().getSimpleName(),
                    Map.of(
                            "tileDecode.unexpectedStage", "UNEXPECTED",
                            "tileDecode.unexpectedExceptionClass", exception.getClass().getSimpleName()
                    )
            );
        }
    }

    private boolean validPayloadForSlot(FixedLayoutPlan layoutPlan, int tileIndex, TilePayload payload) {
        return slotValidationResult(layoutPlan, tileIndex, payload).isAccepted();
    }

    private SlotValidationResult slotValidationResult(FixedLayoutPlan layoutPlan, int tileIndex, TilePayload payload) {
        String expectedLayoutProfileId = layoutPlan.profile().profileId();
        int expectedTotalTiles = layoutPlan.profile().rows() * layoutPlan.profile().cols();
        Map<String, String> diagnostics = slotValidationDiagnostics(
                expectedLayoutProfileId,
                payload.layoutProfileId(),
                tileIndex,
                payload.tileIndex().value(),
                expectedTotalTiles,
                payload.totalTilesInFrame()
        );
        if (!expectedLayoutProfileId.equals(payload.layoutProfileId())) {
            return SlotValidationResult.rejected(
                    "slotValidation: layout profile mismatch expected "
                            + expectedLayoutProfileId
                            + " actual "
                            + payload.layoutProfileId(),
                    diagnostics
            );
        }
        if (payload.tileIndex().value() != tileIndex) {
            return SlotValidationResult.rejected(
                    "slotValidation: tile index mismatch expected "
                            + tileIndex
                            + " actual "
                            + payload.tileIndex().value(),
                    diagnostics
            );
        }
        if (payload.totalTilesInFrame() != expectedTotalTiles) {
            return SlotValidationResult.rejected(
                    "slotValidation: total tile count mismatch expected "
                            + expectedTotalTiles
                            + " actual "
                            + payload.totalTilesInFrame(),
                    diagnostics
            );
        }
        if (payload.payloadKind() == PayloadKind.SESSION_END && payload.body().length == 0) {
            return SlotValidationResult.rejected("slotValidation: empty session-end payload body", diagnostics);
        }
        return SlotValidationResult.accepted();
    }

    private Map<String, String> slotValidationDiagnostics(
            String expectedLayoutProfileId,
            String actualLayoutProfileId,
            int expectedTileIndex,
            int actualTileIndex,
            int expectedTotalTiles,
            int actualTotalTiles
    ) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("slotValidation.stage", "SLOT_VALIDATION");
        diagnostics.put("slotValidation.expectedLayoutProfileId", expectedLayoutProfileId);
        diagnostics.put("slotValidation.actualLayoutProfileId", actualLayoutProfileId);
        diagnostics.put("slotValidation.layoutProfileMismatch",
                Boolean.toString(!expectedLayoutProfileId.equals(actualLayoutProfileId)));
        diagnostics.put("slotValidation.expectedTileIndex", Integer.toString(expectedTileIndex));
        diagnostics.put("slotValidation.actualTileIndex", Integer.toString(actualTileIndex));
        diagnostics.put("slotValidation.tileIndexMismatch", Boolean.toString(expectedTileIndex != actualTileIndex));
        diagnostics.put("slotValidation.expectedTotalTiles", Integer.toString(expectedTotalTiles));
        diagnostics.put("slotValidation.actualTotalTiles", Integer.toString(actualTotalTiles));
        diagnostics.put("slotValidation.totalTilesMismatch", Boolean.toString(expectedTotalTiles != actualTotalTiles));
        return Map.copyOf(diagnostics);
    }

    private List<Integer> canonicalizedFinderPatterns(List<Integer> moduleColors, int dimension) {
        List<Integer> canonicalized = new ArrayList<>(moduleColors);
        canonicalizeFinderPattern(canonicalized, dimension, 0, 0, 0);
        canonicalizeFinderPattern(canonicalized, dimension, 0, dimension - FINDER_SIZE_MODULES, 0);
        canonicalizeFinderPattern(canonicalized, dimension, dimension - FINDER_SIZE_MODULES, 0, 6);
        canonicalizeFinderPattern(
                canonicalized,
                dimension,
                dimension - FINDER_SIZE_MODULES,
                dimension - FINDER_SIZE_MODULES,
                3
        );
        return List.copyOf(canonicalized);
    }

    private void canonicalizeFinderPattern(
            List<Integer> moduleColors,
            int dimension,
            int startRow,
            int startCol,
            int expectedColor
    ) {
        for (int row = startRow; row < startRow + FINDER_SIZE_MODULES; row++) {
            for (int col = startCol; col < startCol + FINDER_SIZE_MODULES; col++) {
                moduleColors.set((row * dimension) + col, expectedColor);
            }
        }
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

    private CaptureMediaDiagnostic partialAcceptanceDiagnostic(
            NormalizedCaptureFrame frame,
            int decodedPayloadCount,
            int partialRejectedSlotCount,
            int partialUndecodableSlotCount,
            int totalTileSlotCount
    ) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("decodedPayloadCount", (double) decodedPayloadCount);
        values.put("partialRejectedSlotCount", (double) partialRejectedSlotCount);
        values.put("partialUndecodableSlotCount", (double) partialUndecodableSlotCount);
        values.put("totalTileSlotCount", (double) totalTileSlotCount);
        values.put("partialAccepted", 1.0d);
        return sourceDiagnostic(
                frame,
                CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                CaptureMediaDiagnosticSeverity.WARNING,
                Map.copyOf(values),
                "Camera-derived media frame accepted validated payloads while sibling tile slots failed validation"
        );
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
     * @param profileAttempts all layout profile attempts evaluated for this normalized frame
     * @param selectedLayoutProfileId payload-derived selected layout profile id, when any attempt decoded
     * @param profileSelectionSource source used to select the layout profile
     */
    public record FrameInspection(
            String sourceId,
            String layoutProfileId,
            Optional<CvSamplingEvidence> samplingEvidence,
            List<SlotInspection> slots,
            int candidateAttemptCount,
            int noFinderAttemptCount,
            int paletteRejectedAttemptCount,
            int decodedPayloadCount,
            List<ProfileAttemptInspection> profileAttempts,
            Optional<String> selectedLayoutProfileId,
            ProfileSelectionSource profileSelectionSource
    ) {

        /**
         * Creates a frame inspection for one active layout attempt.
         *
         * @param sourceId source identifier of the normalized frame
         * @param layoutProfileId active layout profile id
         * @param samplingEvidence backend-neutral grid-phase and tile sampling evidence used by the sampler
         * @param slots per-slot sampler evidence
         * @param candidateAttemptCount side-version attempts across all signed slots
         * @param noFinderAttemptCount attempts that failed finder-pattern checks
         * @param paletteRejectedAttemptCount attempts blocked by palette tolerance
         * @param decodedPayloadCount accepted payload count after tile and envelope validation
         */
        public FrameInspection(
                String sourceId,
                String layoutProfileId,
                Optional<CvSamplingEvidence> samplingEvidence,
                List<SlotInspection> slots,
                int candidateAttemptCount,
                int noFinderAttemptCount,
                int paletteRejectedAttemptCount,
                int decodedPayloadCount
        ) {
            this(
                    sourceId,
                    layoutProfileId,
                    samplingEvidence,
                    slots,
                    candidateAttemptCount,
                    noFinderAttemptCount,
                    paletteRejectedAttemptCount,
                    decodedPayloadCount,
                    List.of(),
                    decodedPayloadCount > 0 ? Optional.of(layoutProfileId) : Optional.empty(),
                    decodedPayloadCount > 0 ? ProfileSelectionSource.DECODED_PAYLOAD : ProfileSelectionSource.NONE
            );
        }

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
            profileAttempts = List.copyOf(Objects.requireNonNull(
                    profileAttempts,
                    "profileAttempts must not be null"
            ));
            if (profileAttempts.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("profileAttempts must not contain null values");
            }
            selectedLayoutProfileId = Objects.requireNonNull(
                    selectedLayoutProfileId,
                    "selectedLayoutProfileId must not be null"
            );
            selectedLayoutProfileId.ifPresent(value -> {
                if (value.isBlank()) {
                    throw new IllegalArgumentException("selectedLayoutProfileId must not be blank when present");
                }
            });
            Objects.requireNonNull(profileSelectionSource, "profileSelectionSource must not be null");
        }

        private FrameInspection withProfileAttempts(
                List<ProfileAttemptInspection> newProfileAttempts,
                Optional<String> newSelectedLayoutProfileId,
                ProfileSelectionSource newProfileSelectionSource
        ) {
            return new FrameInspection(
                    sourceId,
                    layoutProfileId,
                    samplingEvidence,
                    slots,
                    candidateAttemptCount,
                    noFinderAttemptCount,
                    paletteRejectedAttemptCount,
                    decodedPayloadCount,
                    newProfileAttempts,
                    newSelectedLayoutProfileId,
                    newProfileSelectionSource
            );
        }
    }

    /**
     * Diagnostic sampler evidence for one attempted layout profile.
     *
     * @param layoutProfileId attempted layout profile id
     * @param rows attempted layout row count
     * @param cols attempted layout column count
     * @param status attempt outcome
     * @param decodedPayloadCount accepted payload count for this attempt
     * @param tileDecodeAttemptCount tile-decode attempts for this profile attempt
     * @param slotValidationLayoutProfileMismatchCount decoded payloads rejected by layout-profile mismatch
     * @param slotValidationTileIndexMismatchCount decoded payloads rejected by tile-index mismatch
     * @param slotValidationTotalTilesMismatchCount decoded payloads rejected by total-tile-count mismatch
     * @param decodedPayloadLayoutProfileId payload-derived layout profile id when this attempt accepted payloads
     */
    public record ProfileAttemptInspection(
            String layoutProfileId,
            int rows,
            int cols,
            ProfileAttemptInspectionStatus status,
            int decodedPayloadCount,
            int tileDecodeAttemptCount,
            int slotValidationLayoutProfileMismatchCount,
            int slotValidationTileIndexMismatchCount,
            int slotValidationTotalTilesMismatchCount,
            Optional<String> decodedPayloadLayoutProfileId
    ) {

        /**
         * Creates a validated immutable profile-attempt inspection.
         */
        public ProfileAttemptInspection {
            if (layoutProfileId == null || layoutProfileId.isBlank()) {
                throw new IllegalArgumentException("layoutProfileId must not be blank");
            }
            if (rows <= 0 || cols <= 0) {
                throw new IllegalArgumentException("layout dimensions must be positive");
            }
            Objects.requireNonNull(status, "status must not be null");
            if (decodedPayloadCount < 0
                    || tileDecodeAttemptCount < 0
                    || slotValidationLayoutProfileMismatchCount < 0
                    || slotValidationTileIndexMismatchCount < 0
                    || slotValidationTotalTilesMismatchCount < 0) {
                throw new IllegalArgumentException("profile attempt counts must be non-negative");
            }
            decodedPayloadLayoutProfileId = Objects.requireNonNull(
                    decodedPayloadLayoutProfileId,
                    "decodedPayloadLayoutProfileId must not be null"
            );
            decodedPayloadLayoutProfileId.ifPresent(value -> {
                if (value.isBlank()) {
                    throw new IllegalArgumentException("decodedPayloadLayoutProfileId must not be blank when present");
                }
            });
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
     * @param samplingEvidenceAlignmentStatus diagnostic outcome of sampling-evidence placement selection
     * @param candidates per-side-version candidate evidence
     */
    public record SlotInspection(
            int tileIndex,
            BorderInspectionStatus borderStatus,
            boolean interiorContent,
            int effectiveTileShiftXPx,
            int effectiveTileShiftYPx,
            TileAlignmentInspectionSource effectiveTilePlacementSource,
            TileEvidenceAlignmentStatus samplingEvidenceAlignmentStatus,
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
            Objects.requireNonNull(samplingEvidenceAlignmentStatus, "samplingEvidenceAlignmentStatus must not be null");
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
     * @param samplingDiagnostics sampled logical-tile diagnostics, when a tile was built
     * @param decodeFailureReason stable tile, envelope, or slot-validation reason when decode was attempted and rejected
     * @param decodeDiagnostics compact tile-decode diagnostics when decode was attempted and rejected
     * @param decodedPayloadLayoutProfileId payload-derived layout profile id when the attempt decoded successfully
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
            Optional<PaletteConfidenceSummary> paletteConfidence,
            Map<String, String> samplingDiagnostics,
            Optional<String> decodeFailureReason,
            Map<String, String> decodeDiagnostics,
            Optional<String> decodedPayloadLayoutProfileId
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
            samplingDiagnostics = Map.copyOf(Objects.requireNonNull(
                    samplingDiagnostics,
                    "samplingDiagnostics must not be null"
            ));
            Objects.requireNonNull(decodeFailureReason, "decodeFailureReason must not be null");
            decodeDiagnostics = Map.copyOf(Objects.requireNonNull(
                    decodeDiagnostics,
                    "decodeDiagnostics must not be null"
            ));
            decodedPayloadLayoutProfileId = Objects.requireNonNull(
                    decodedPayloadLayoutProfileId,
                    "decodedPayloadLayoutProfileId must not be null"
            );
            decodedPayloadLayoutProfileId.ifPresent(value -> {
                if (value.isBlank()) {
                    throw new IllegalArgumentException("decodedPayloadLayoutProfileId must not be blank when present");
                }
            });
        }
    }

    /**
     * Diagnostic status for a layout profile attempt.
     */
    public enum ProfileAttemptInspectionStatus {
        /**
         * At least one payload decoded and passed strict slot validation.
         */
        ACCEPTED,

        /**
         * Sampling or strict protocol validation found evidence but did not accept payloads.
         */
        REJECTED,

        /**
         * No signed tile content was found for this profile attempt.
         */
        EMPTY
    }

    /**
     * Source used to choose the selected layout profile.
     */
    public enum ProfileSelectionSource {
        /**
         * The selected profile came from a validated decoded tile payload.
         */
        DECODED_PAYLOAD("decodedPayload"),

        /**
         * No profile was selected because no validated payload decoded.
         */
        NONE("none");

        private final String sidecarValue;

        ProfileSelectionSource(String sidecarValue) {
            this.sidecarValue = sidecarValue;
        }

        /**
         * Returns the compact sidecar value for this selection source.
         *
         * @return sidecar value
         */
        public String sidecarValue() {
            return sidecarValue;
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
     * Diagnostic outcome for using backend-neutral sampling evidence before legacy tile alignment.
     */
    public enum TileEvidenceAlignmentStatus {
        /**
         * Sampling evidence is not used for exact or non-camera-derived frames.
         */
        NOT_CAMERA_DERIVED,

        /**
         * No sampling evidence was supplied for the active layout attempt.
         */
        NO_SAMPLING_EVIDENCE,

        /**
         * Sampling evidence was supplied but did not meet the confidence threshold.
         */
        CONFIDENCE_BELOW_THRESHOLD,

        /**
         * The evidence-derived shifted tile placement would leave the normalized frame bounds.
         */
        OUT_OF_BOUNDS,

        /**
         * The evidence-derived placement stayed in bounds but did not pass the border signature check.
         */
        BORDER_SIGNATURE_FAILED,

        /**
         * Sampling evidence selected the placement because its border signature passed.
         */
        USED_BORDER_SIGNATURE,

        /**
         * Sampling evidence selected the placement because finder and strict tile validation succeeded.
         */
        USED_FINDER_EVIDENCE
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

    private record AlignmentOffset(int xPx, int yPx) {
    }

    private record EvidenceAlignment(Optional<TileAlignment> alignment, TileEvidenceAlignmentStatus status) {

        private EvidenceAlignment {
            Objects.requireNonNull(alignment, "alignment must not be null");
            Objects.requireNonNull(status, "status must not be null");
        }

        private static EvidenceAlignment available(TileAlignment alignment) {
            Objects.requireNonNull(alignment, "alignment must not be null");
            return new EvidenceAlignment(Optional.of(alignment), alignment.samplingEvidenceAlignmentStatus());
        }

        private static EvidenceAlignment unavailable(TileEvidenceAlignmentStatus status) {
            return new EvidenceAlignment(Optional.empty(), status);
        }
    }

    private record TileAlignment(
            TilePlacement placement,
            int shiftXPx,
            int shiftYPx,
            TileAlignmentInspectionSource source,
            TileEvidenceAlignmentStatus samplingEvidenceAlignmentStatus
    ) {

        private TileAlignment {
            Objects.requireNonNull(placement, "placement must not be null");
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(
                    samplingEvidenceAlignmentStatus,
                    "samplingEvidenceAlignmentStatus must not be null"
            );
        }

        private static TileAlignment of(
                TilePlacement originalPlacement,
                TilePlacement effectivePlacement,
                TileAlignmentInspectionSource source,
                TileEvidenceAlignmentStatus samplingEvidenceAlignmentStatus
        ) {
            Objects.requireNonNull(originalPlacement, "originalPlacement must not be null");
            Objects.requireNonNull(effectivePlacement, "effectivePlacement must not be null");
            return new TileAlignment(
                    effectivePlacement,
                    effectivePlacement.xPx() - originalPlacement.xPx(),
                    effectivePlacement.yPx() - originalPlacement.yPx(),
                    source,
                    samplingEvidenceAlignmentStatus
            );
        }

        private TileAlignment withSamplingEvidenceAlignmentStatus(TileEvidenceAlignmentStatus status) {
            return new TileAlignment(placement, shiftXPx, shiftYPx, source, status);
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

    private record FinderMatch(int matchedModules) {

        private boolean exact() {
            return matchedModules == FINDER_SIZE_MODULES * FINDER_SIZE_MODULES;
        }

        private boolean recoverable() {
            return matchedModules >= CAMERA_MIN_MATCHES_PER_RECOVERABLE_FINDER;
        }
    }

    private record FinderPatternSummary(
            FinderMatch topLeft,
            FinderMatch topRight,
            FinderMatch bottomLeft,
            FinderMatch bottomRight
    ) {

        private FinderPatternSummary {
            Objects.requireNonNull(topLeft, "topLeft must not be null");
            Objects.requireNonNull(topRight, "topRight must not be null");
            Objects.requireNonNull(bottomLeft, "bottomLeft must not be null");
            Objects.requireNonNull(bottomRight, "bottomRight must not be null");
        }

        private boolean exact() {
            return topLeft.exact() && topRight.exact() && bottomLeft.exact() && bottomRight.exact();
        }

        private boolean supported(boolean cameraDerived) {
            return exact()
                    || (cameraDerived && recoverableCount() >= CAMERA_MIN_RECOVERABLE_FINDER_COUNT);
        }

        private int recoverableCount() {
            int count = 0;
            count += topLeft.recoverable() ? 1 : 0;
            count += topRight.recoverable() ? 1 : 0;
            count += bottomLeft.recoverable() ? 1 : 0;
            count += bottomRight.recoverable() ? 1 : 0;
            return count;
        }
    }

    private record DecodeAttempt(
            Optional<TilePayload> payload,
            Optional<String> rejectionReason,
            Map<String, String> diagnostics
    ) {

        private DecodeAttempt {
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
            diagnostics = Map.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        }

        private static DecodeAttempt accepted(TilePayload payload) {
            Objects.requireNonNull(payload, "payload must not be null");
            return new DecodeAttempt(Optional.of(payload), Optional.empty(), Map.of(
                    "decodedPayload.layoutProfileId", payload.layoutProfileId(),
                    "decodedPayload.tileIndex", Integer.toString(payload.tileIndex().value()),
                    "decodedPayload.totalTiles", Integer.toString(payload.totalTilesInFrame())
            ));
        }

        private static DecodeAttempt rejected(String reason, Map<String, String> diagnostics) {
            return new DecodeAttempt(Optional.empty(), Optional.of(reason), diagnostics);
        }
    }

    private record SlotValidationResult(Optional<String> rejectionReason, Map<String, String> diagnostics) {

        private SlotValidationResult {
            Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
            diagnostics = Map.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        }

        private static SlotValidationResult accepted() {
            return new SlotValidationResult(Optional.empty(), Map.of());
        }

        private static SlotValidationResult rejected(String reason, Map<String, String> diagnostics) {
            return new SlotValidationResult(Optional.of(reason), diagnostics);
        }

        private boolean isAccepted() {
            return rejectionReason.isEmpty();
        }
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
