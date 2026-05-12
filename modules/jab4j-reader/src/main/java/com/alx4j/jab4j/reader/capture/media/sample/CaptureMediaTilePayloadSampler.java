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

    private final CaptureMediaPaletteSampler paletteSampler;
    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final TileDecoder tileDecoder;
    private final TileCodecProfile tileCodecProfile;
    private final TilePayloadEnvelopeCodec envelopeCodec;

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
                new TilePayloadEnvelopeCodec()
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
        this.paletteSampler = Objects.requireNonNull(paletteSampler, "paletteSampler must not be null");
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.tileDecoder = Objects.requireNonNull(tileDecoder, "tileDecoder must not be null");
        this.tileCodecProfile = Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec must not be null");
    }

    /**
     * Samples and validates tile payloads from one normalized media frame.
     *
     * @param frame normalized media frame
     * @return frame sample result with accepted payloads or blocking diagnostics
     */
    public FrameSample sample(NormalizedCaptureFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        Optional<LayoutProfile> resolvedLayout = layoutCatalog
                .resolve(frame.normalizedWidthPixels(), frame.normalizedHeightPixels())
                .filter(profile -> profile.profileId().equals(frame.layoutProfileId()));
        if (resolvedLayout.isEmpty()) {
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

        FixedLayoutPlan layoutPlan = layoutPlanner.plan(resolvedLayout.orElseThrow());
        List<TilePayload> payloads = new ArrayList<>();
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>();
        PaletteSampleAccumulator acceptedConfidence = new PaletteSampleAccumulator();
        List<TilePlacement> placements = layoutPlan.tilePlacements();
        for (int tileIndex = 0; tileIndex < placements.size(); tileIndex++) {
            SlotSample slotSample = sampleSlot(frame, layoutPlan, placements.get(tileIndex), tileIndex);
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
        if (confidence.lowConfidenceSampleCount() > 0) {
            diagnostics.add(colorDiagnostic(
                    frame,
                    CaptureMediaDiagnosticSeverity.WARNING,
                    confidence,
                    "Media tile content decoded with low palette confidence"
            ));
        }
        return FrameSample.accepted(payloads, diagnostics, confidence);
    }

    private SlotSample sampleSlot(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex
    ) {
        BorderSample borderSample = sampleRenderedTileBorder(frame, layoutPlan, placement);
        if (borderSample.status() != BorderSampleStatus.SIGNATURE) {
            if (borderSample.status() == BorderSampleStatus.REJECTED && hasInteriorContent(frame, layoutPlan, placement)) {
                return SlotSample.rejected(borderSample.paletteConfidence().orElseThrow());
            }
            return hasInteriorContent(frame, layoutPlan, placement)
                    ? SlotSample.noSignatureContent()
                    : SlotSample.empty();
        }

        List<CandidateSample> candidates = new ArrayList<>();
        CandidateSample rejectedCandidate = null;
        for (int sideVersion = tileCodecProfile.minSideVersion(); sideVersion <= tileCodecProfile.maxSideVersion(); sideVersion++) {
            CandidateSample candidate = sampleCandidate(frame, layoutPlan, placement, tileIndex, sideVersion);
            if (candidate.status() == CandidateSampleStatus.REJECTED) {
                rejectedCandidate = lowerConfidence(rejectedCandidate, candidate);
            } else if (candidate.status() == CandidateSampleStatus.CANDIDATE) {
                candidates.add(candidate);
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
        PaletteSampleAccumulator rejectedSamples = new PaletteSampleAccumulator();
        boolean rejected = false;
        for (int row = 0; row < placement.heightPx(); row++) {
            for (int col = 0; col < placement.widthPx(); col++) {
                if (row < border || row >= placement.heightPx() - border
                        || col < border || col >= placement.widthPx() - border) {
                    CaptureMediaPaletteSample sample = paletteSampler.sampleTolerantPalette(
                            frame,
                            placement.yPx() + row,
                            placement.xPx() + col
                    );
                    if (!sample.accepted()) {
                        rejectedSamples.add(sample);
                        rejected = true;
                    } else if (sample.paletteIndex() != WHITE_INDEX) {
                        return rejected
                                ? BorderSample.rejected(rejectedSamples.summary())
                                : BorderSample.noSignature();
                    }
                }
            }
        }
        return rejected
                ? BorderSample.rejected(rejectedSamples.summary())
                : BorderSample.signature();
    }

    private boolean hasInteriorContent(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan, TilePlacement placement) {
        int border = layoutPlan.separatorThicknessPx();
        for (int row = border; row < placement.heightPx() - border; row++) {
            for (int col = border; col < placement.widthPx() - border; col++) {
                CaptureMediaPaletteSample sample = paletteSampler.sampleTolerantPalette(
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

    private CandidateSample sampleCandidate(
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            int sideVersion
    ) {
        int dimension = tileCodecProfile.dimensionForSideVersion(sideVersion);
        int border = layoutPlan.separatorThicknessPx();
        int innerWidth = layoutPlan.tileSlotWidthPx() - (2 * border);
        int innerHeight = layoutPlan.tileSlotHeightPx() - (2 * border);
        int logicalSide = dimension + (2 * tileCodecProfile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        if (moduleSize < MIN_MODULE_SIZE_PX) {
            return CandidateSample.noFinder(Optional.empty());
        }

        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        List<Integer> moduleColors = new ArrayList<>(dimension * dimension);
        PaletteSampleAccumulator confidence = new PaletteSampleAccumulator();
        boolean rejected = false;
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                int sampleX = placement.xPx()
                        + offsetX
                        + ((col + tileCodecProfile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2);
                int sampleY = placement.yPx()
                        + offsetY
                        + ((row + tileCodecProfile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2);
                CaptureMediaPaletteSample sample = paletteSampler.sampleTolerantPalette(frame, sampleY, sampleX);
                confidence.add(sample);
                if (!sample.accepted()) {
                    rejected = true;
                }
                moduleColors.add(sample.paletteIndex());
            }
        }

        PaletteConfidenceSummary summary = confidence.summary();
        if (rejected) {
            return CandidateSample.rejected(summary);
        }
        if (!hasSupportedFinderPatterns(moduleColors, dimension)) {
            return CandidateSample.noFinder(Optional.of(summary));
        }

        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sampledSideVersion", Integer.toString(sideVersion));
        diagnostics.put("sampledDimension", Integer.toString(dimension));
        diagnostics.put("layoutProfileId", layoutPlan.profile().profileId());
        diagnostics.put("tileIndex", Integer.toString(tileIndex));
        diagnostics.put("minimumPaletteConfidence", formatMetric(summary.minimumConfidence()));
        diagnostics.put("averagePaletteConfidence", formatMetric(summary.averageConfidence()));
        diagnostics.put("maximumPaletteRgbDistance", formatMetric(summary.maximumRgbDistance()));
        diagnostics.put("lowConfidenceSampleCount", Integer.toString(summary.lowConfidenceSampleCount()));
        return CandidateSample.candidate(new LogicalTile(
                dimension,
                dimension,
                tileCodecProfile.quietZoneModules(),
                tileCodecProfile.profileId(),
                moduleColors,
                diagnostics
        ), summary);
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
        } catch (TileCodecException | TransportException exception) {
            return null;
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
            paletteConfidence = Objects.requireNonNull(paletteConfidence, "paletteConfidence must not be null");
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
            payload = Objects.requireNonNull(payload, "payload must not be null");
            paletteConfidence = Objects.requireNonNull(paletteConfidence, "paletteConfidence must not be null");
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

    private record CandidateSample(
            CandidateSampleStatus status,
            Optional<LogicalTile> logicalTile,
            Optional<PaletteConfidenceSummary> optionalPaletteConfidence
    ) {

        private CandidateSample {
            Objects.requireNonNull(status, "status must not be null");
            logicalTile = Objects.requireNonNull(logicalTile, "logicalTile must not be null");
            optionalPaletteConfidence = Objects.requireNonNull(
                    optionalPaletteConfidence,
                    "optionalPaletteConfidence must not be null"
            );
        }

        private PaletteConfidenceSummary paletteConfidence() {
            return optionalPaletteConfidence.orElseThrow();
        }

        private static CandidateSample noFinder(Optional<PaletteConfidenceSummary> confidence) {
            return new CandidateSample(CandidateSampleStatus.NO_FINDER, Optional.empty(), confidence);
        }

        private static CandidateSample rejected(PaletteConfidenceSummary confidence) {
            return new CandidateSample(CandidateSampleStatus.REJECTED, Optional.empty(), Optional.of(confidence));
        }

        private static CandidateSample candidate(LogicalTile tile, PaletteConfidenceSummary confidence) {
            return new CandidateSample(CandidateSampleStatus.CANDIDATE, Optional.of(tile), Optional.of(confidence));
        }
    }

    private enum BorderSampleStatus {
        SIGNATURE,
        NO_SIGNATURE,
        REJECTED
    }

    private record BorderSample(BorderSampleStatus status, Optional<PaletteConfidenceSummary> paletteConfidence) {

        private BorderSample {
            Objects.requireNonNull(status, "status must not be null");
            paletteConfidence = Objects.requireNonNull(paletteConfidence, "paletteConfidence must not be null");
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
