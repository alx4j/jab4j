package com.alx4j.jab4j.reader.capture.media.sample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.zip.CRC32C;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.cv.CvTileSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameSample;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameSampleStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.BorderInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.DecodeInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.ModuleSamplingInspectionSource;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.PhaseInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.ProfileSelectionSource;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.SlotInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.TileAlignmentInspectionSource;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.TileEvidenceAlignmentStatus;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

@DisplayName("Capture media tile payload sampler")
class CaptureMediaTilePayloadSamplerTest {

    private static final List<Integer> PALETTE = List.of(
            0xFF000000,
            0xFF0000FF,
            0xFF00FF00,
            0xFF00FFFF,
            0xFFFF0000,
            0xFFFF00FF,
            0xFFFFFF00,
            0xFFFFFFFF
    );
    private static final LayoutProfile CAPTURE_LAYOUT = new LayoutProfile(
            "debug-low-density",
            1,
            2,
            1280,
            720,
            16,
            40,
            "solidWhite",
            48,
            24,
            "black",
            "preserveAspect"
    );
    private static final LayoutProfile DESKTOP_1080P_SAFE_LAYOUT = new LayoutProfile(
            "desktop-1080p-safe",
            2,
            2,
            1920,
            1080,
            24,
            48,
            "solidWhite",
            64,
            32,
            "black",
            "preserveAspect"
    );
    private static final FixedLayoutPlan LAYOUT_PLAN = new FixedLayoutPlanner().plan(CAPTURE_LAYOUT);
    private static final TileCodecProfile TILE_PROFILE = TileCodecProfiles.balancedV1();

    private final CaptureMediaTilePayloadSampler sampler = new CaptureMediaTilePayloadSampler();
    private final TilePayloadEnvelopeCodec envelopeCodec = new TilePayloadEnvelopeCodec();

    @Test
    @DisplayName("Exact rendered tile payloads decode without color-shift diagnostics")
    void exactRenderedTilePayloadsDecodeWithoutColorShiftDiagnostics() {
        RenderedTileFixture fixture = renderedTileFixture(0);

        FrameSample sample = sampler.sample(fixture.frame());
        FrameInspection inspection = sampler.inspect(fixture.frame());
        SlotInspection slot = inspection.slots().get(0);
        CandidateInspection acceptedCandidate = slot.candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();
        PhaseInspection selectedPhase = acceptedCandidate.selectedPhase();

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertTrue(sample.diagnostics().isEmpty()),
                () -> assertEquals(1.0d, sample.paletteConfidence().orElseThrow().minimumConfidence()),
                () -> assertEquals(0, sample.paletteConfidence().orElseThrow().lowConfidenceSampleCount()),
                () -> assertEquals(1, inspection.decodedPayloadCount()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, slot.borderStatus()),
                () -> assertTrue(slot.interiorContent()),
                () -> assertEquals(0, slot.effectiveTileShiftXPx()),
                () -> assertEquals(0, slot.effectiveTileShiftYPx()),
                () -> assertEquals(TileAlignmentInspectionSource.NOMINAL, slot.effectiveTilePlacementSource()),
                () -> assertEquals(0, acceptedCandidate.moduleCenterOffsetXPx()),
                () -> assertEquals(0, acceptedCandidate.moduleCenterOffsetYPx()),
                () -> assertEquals(ModuleSamplingInspectionSource.NONE,
                        acceptedCandidate.moduleSamplingOffsetSource()),
                () -> assertEquals(0, acceptedCandidate.areaSampleRadiusPx()),
                () -> assertEquals(1, selectedPhase.rank()),
                () -> assertEquals(CaptureMediaModulePhaseCandidateSource.NOMINAL, selectedPhase.source()),
                () -> assertEquals(CaptureMediaModulePhaseOutcome.ACCEPTED_PAYLOAD, selectedPhase.outcome()),
                () -> assertEquals(1, selectedPhase.attemptedVariantCount()),
                () -> assertTrue(acceptedCandidate.runnerUpPhase().isEmpty()),
                () -> assertFalse(inspection.paletteCalibration().enabled()),
                () -> assertEquals(1.0d, inspection.paletteCalibration().confidence()),
                () -> assertEquals(0, inspection.paletteCalibration().observedColorCount()),
                () -> assertEquals(0.0d, inspection.paletteCalibration().maximumObservedRgbDistance()),
                () -> assertTrue(inspection.paletteCalibration().fallbackReason().isEmpty()),
                () -> assertTrue(inspection.paletteCalibration().observedColors().isEmpty())
        );
    }

    @Test
    @DisplayName("Camera-derived frames accept a validated tile when a later sibling slot is undecodable")
    void cameraDerivedFramesAcceptValidatedTileWhenLaterSiblingSlotIsUndecodable() {
        PartialFrameFixture fixture = partialFrameFixture(0, 1);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.exactFrame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        CaptureMediaDiagnostic partialDiagnostic = partialAcceptanceDiagnostic(sample);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.acceptedPayload()), sample.payloads()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, partialDiagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.WARNING, partialDiagnostic.severity()),
                () -> assertFalse(partialDiagnostic.blocking()),
                () -> assertEquals(1.0d, partialDiagnostic.metrics().get("decodedPayloadCount")),
                () -> assertEquals(0.0d, partialDiagnostic.metrics().get("partialRejectedSlotCount")),
                () -> assertEquals(1.0d, partialDiagnostic.metrics().get("partialUndecodableSlotCount")),
                () -> assertEquals(2.0d, partialDiagnostic.metrics().get("totalTileSlotCount")),
                () -> assertEquals(1.0d, partialDiagnostic.metrics().get("partialAccepted"))
        );
    }

    @Test
    @DisplayName("Camera-derived frames accept a validated tile when an earlier sibling slot is undecodable")
    void cameraDerivedFramesAcceptValidatedTileWhenEarlierSiblingSlotIsUndecodable() {
        PartialFrameFixture fixture = partialFrameFixture(1, 0);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.exactFrame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        CaptureMediaDiagnostic partialDiagnostic = partialAcceptanceDiagnostic(sample);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.acceptedPayload()), sample.payloads()),
                () -> assertTrue(sample.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertEquals(1.0d, partialDiagnostic.metrics().get("decodedPayloadCount")),
                () -> assertEquals(1.0d, partialDiagnostic.metrics().get("partialUndecodableSlotCount"))
        );
    }

    @Test
    @DisplayName("Exact frames keep fail-fast rejection when a sibling slot is undecodable")
    void exactFramesKeepFailFastRejectionWhenSiblingSlotIsUndecodable() {
        PartialFrameFixture fixture = partialFrameFixture(0, 1);

        FrameSample sample = sampler.sample(fixture.exactFrame());
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking())
        );
    }

    @Test
    @DisplayName("Controlled shifted tile payloads decode with warning diagnostics")
    void controlledShiftedTilePayloadsDecodeWithWarningDiagnostics() {
        RenderedTileFixture fixture = renderedTileFixture(18);

        FrameSample sample = sampler.sample(fixture.frame());
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.WARNING, diagnostic.severity()),
                () -> assertFalse(diagnostic.blocking()),
                () -> assertEquals("tile-source.png", diagnostic.sourceId().orElseThrow()),
                () -> assertTrue(diagnostic.metrics().containsKey("minimumPaletteConfidence")),
                () -> assertTrue(sample.paletteConfidence().orElseThrow().lowConfidenceSampleCount() > 0)
        );
    }

    @Test
    @DisplayName("Sparse off-palette border pixels do not block otherwise decodable media tiles")
    void sparseOffPaletteBorderPixelsDoNotBlockOtherwiseDecodableMediaTiles() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] pixels = fixture.frame().copyArgbPixels();
        mutateSparseTileBorderPixels(pixels, 17);
        NormalizedCaptureFrame frame = frame(pixels);

        FrameSample sample = sampler.sample(frame);
        FrameInspection inspection = sampler.inspect(frame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, inspection.slots().get(0).borderStatus()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Below-threshold color shifts block decoded content")
    void belowThresholdColorShiftsBlockDecodedContent() {
        RenderedTileFixture fixture = renderedTileFixture(50);

        FrameSample sample = sampler.sample(fixture.frame());
        FrameInspection inspection = sampler.inspect(fixture.frame());
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertTrue(diagnostic.metrics().get("rejectedSampleCount") > 0.0d),
                () -> assertEquals(BorderInspectionStatus.PALETTE_REJECTED, inspection.slots().get(0).borderStatus()),
                () -> assertEquals(0, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived candidates decode with calibrated shifted palette")
    void cameraDerivedCandidatesDecodeWithCalibratedShiftedPalette() {
        RenderedTileFixture fixture = renderedTileFixture(50);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertTrue(sample.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertEquals(0, sample.paletteConfidence().orElseThrow().lowConfidenceSampleCount()),
                () -> assertEquals(0, sample.paletteConfidence().orElseThrow().rejectedSampleCount()),
                () -> assertEquals(1, inspection.decodedPayloadCount()),
                () -> assertTrue(inspection.paletteCalibration().enabled()),
                () -> assertTrue(inspection.paletteCalibration().confidence() > 0.75d),
                () -> assertTrue(inspection.paletteCalibration().observedColorCount() >= 2),
                () -> assertTrue(inspection.paletteCalibration().maximumObservedRgbDistance() > 0.0d),
                () -> assertTrue(inspection.paletteCalibration().fallbackReason().isEmpty()),
                () -> assertFalse(inspection.paletteCalibration().observedColors().isEmpty())
        );
    }

    @Test
    @DisplayName("Low-confidence camera calibration does not globally broaden thresholds")
    void lowConfidenceCameraCalibrationDoesNotGloballyBroadenThresholds() {
        RenderedTileFixture fixture = renderedTileFixture(90);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertEquals(BorderInspectionStatus.PALETTE_REJECTED, inspection.slots().get(0).borderStatus()),
                () -> assertEquals(0, inspection.decodedPayloadCount()),
                () -> assertFalse(inspection.paletteCalibration().enabled()),
                () -> assertEquals(0.0d, inspection.paletteCalibration().confidence()),
                () -> assertTrue(inspection.paletteCalibration().fallbackReason().isPresent()),
                () -> assertTrue(inspection.paletteCalibration().observedColors().isEmpty())
        );
    }

    @Test
    @DisplayName("Camera-derived sparse rejected module samples can continue through validation")
    void cameraDerivedSparseRejectedModuleSamplesCanContinueThroughValidation() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] pixels = fixture.frame().copyArgbPixels();
        mutateSparseLogicalModuleCentersBeyondCameraThreshold(pixels, fixture.logicalTile(), 29);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(pixels);

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);
        CandidateInspection acceptedCandidate = inspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.WARNING, sample.diagnostics().get(0).severity()),
                () -> assertTrue(sample.paletteConfidence().orElseThrow().rejectedSampleCount() > 0),
                () -> assertTrue(acceptedCandidate.paletteConfidence().orElseThrow().rejectedSampleCount() > 0),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived sparse rejected module samples can pass the observed phone-photo boundary")
    void cameraDerivedSparseRejectedModuleSamplesCanPassObservedPhonePhotoBoundary() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] pixels = fixture.frame().copyArgbPixels();
        mutateSparseLogicalModuleCentersBeyondCameraThreshold(pixels, fixture.logicalTile(), 20);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(pixels);

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);
        CandidateInspection acceptedCandidate = inspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.WARNING, sample.diagnostics().get(0).severity()),
                () -> assertEquals(23, acceptedCandidate.paletteConfidence().orElseThrow().rejectedSampleCount()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Exact sparse rejected module samples remain rejected")
    void exactSparseRejectedModuleSamplesRemainRejected() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] pixels = fixture.frame().copyArgbPixels();
        mutateSparseLogicalModuleCentersBeyondCameraThreshold(pixels, fixture.logicalTile(), 29);
        NormalizedCaptureFrame exactFrame = frame(pixels);

        FrameSample sample = sampler.sample(exactFrame);
        FrameInspection inspection = sampler.inspect(exactFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertFalse(sample.diagnostics().isEmpty()),
                () -> assertEquals(0, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("JPEG-compressed camera-derived tile payloads decode with shifted palette metrics")
    void jpegCompressedCameraDerivedTilePayloadsDecodeWithShiftedPaletteMetrics() throws Exception {
        RenderedTileFixture fixture = renderedTileFixture(0);
        NormalizedCaptureFrame jpegFrame = cameraDerivedFrame(jpegRoundTrip(fixture.frame().copyArgbPixels()));

        FrameSample sample = sampler.sample(jpegFrame);
        FrameInspection inspection = sampler.inspect(jpegFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertTrue(sample.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertTrue(sample.paletteConfidence().orElseThrow().shiftedSampleCount() > 0),
                () -> assertTrue(sample.paletteConfidence().orElseThrow().maximumRgbDistance() > 0.0d),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Local white-balance drift decodes with calibrated palette")
    void localWhiteBalanceDriftDecodesWithCalibratedPalette() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(whiteBalancedPixels(fixture.frame().copyArgbPixels()));

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertTrue(sample.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertEquals(0, sample.paletteConfidence().orElseThrow().rejectedSampleCount()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, inspection.slots().get(0).borderStatus()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Low-contrast tile evidence is rejected before tile decode")
    void lowContrastTileEvidenceIsRejectedBeforeTileDecode() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        NormalizedCaptureFrame lowContrastFrame = frame(lowContrastPixels(fixture.frame().copyArgbPixels()));

        FrameSample sample = sampler.sample(lowContrastFrame);
        FrameInspection inspection = sampler.inspect(lowContrastFrame);
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertEquals(BorderInspectionStatus.PALETTE_REJECTED, inspection.slots().get(0).borderStatus()),
                () -> assertEquals(0, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived shifted tile slots are aligned before border sampling")
    void cameraDerivedShiftedTileSlotsAreAlignedBeforeBorderSampling() {
        RenderedTileFixture fixture = renderedTileFixture(18, 36, -40);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);
        SlotInspection slot = inspection.slots().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, slot.borderStatus()),
                () -> assertEquals(36, slot.effectiveTileShiftXPx()),
                () -> assertEquals(-40, slot.effectiveTileShiftYPx()),
                () -> assertEquals(TileAlignmentInspectionSource.LEGACY_BORDER_SCAN,
                        slot.effectiveTilePlacementSource()),
                () -> assertEquals(TileEvidenceAlignmentStatus.NO_SAMPLING_EVIDENCE,
                        slot.samplingEvidenceAlignmentStatus()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived shifted tile slots are aligned beyond the original narrow scan radius")
    void cameraDerivedWideShiftedTileSlotsAreAlignedBeforeBorderSampling() {
        RenderedTileFixture fixture = renderedTileFixture(18, 76, -72);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);
        SlotInspection slot = inspection.slots().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, slot.borderStatus()),
                () -> assertEquals(76, slot.effectiveTileShiftXPx()),
                () -> assertEquals(-72, slot.effectiveTileShiftYPx()),
                () -> assertEquals(TileAlignmentInspectionSource.LEGACY_BORDER_SCAN,
                        slot.effectiveTilePlacementSource()),
                () -> assertEquals(TileEvidenceAlignmentStatus.NO_SAMPLING_EVIDENCE,
                        slot.samplingEvidenceAlignmentStatus()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived phase search recovers shifted inner content")
    void cameraDerivedPhaseSearchRecoversShiftedInnerContent() {
        RenderedTileFixture fixture = finderPhaseShiftedTileFixture();
        NormalizedCaptureFrame exactFrame = frame(fixture.frame().copyArgbPixels());
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample exactSample = sampler.sample(exactFrame);
        FrameSample cameraSample = sampler.sample(cameraFrame);
        FrameInspection exactInspection = sampler.inspect(exactFrame);
        FrameInspection cameraInspection = sampler.inspect(cameraFrame);
        CandidateInspection acceptedCandidate = cameraInspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();
        PhaseInspection selectedPhase = acceptedCandidate.selectedPhase();

        assertAll(
                () -> assertTrue(exactSample.payloads().isEmpty()),
                () -> assertTrue(exactInspection.slots().get(0).candidates().stream()
                        .noneMatch(candidate ->
                                candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE)),
                () -> assertEquals(FrameSampleStatus.ACCEPTED, cameraSample.status()),
                () -> assertEquals(List.of(fixture.payload()), cameraSample.payloads()),
                () -> assertEquals(ModuleSamplingInspectionSource.FALLBACK_SEARCH,
                        acceptedCandidate.moduleSamplingOffsetSource()),
                () -> assertEquals(CaptureMediaModulePhaseCandidateSource.BOUNDED_SEARCH, selectedPhase.source()),
                () -> assertEquals(CaptureMediaModulePhaseOutcome.ACCEPTED_PAYLOAD, selectedPhase.outcome()),
                () -> assertTrue(selectedPhase.attemptedVariantCount() > 1),
                () -> assertTrue(acceptedCandidate.runnerUpPhase().isPresent()),
                () -> assertEquals(1, cameraInspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived recoverable finder evidence canonicalizes finder modules before decode")
    void cameraDerivedRecoverableFinderEvidenceCanonicalizesFinderModulesBeforeDecode() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] pixels = fixture.frame().copyArgbPixels();
        int lastFinderStart = fixture.logicalTile().widthModules() - 3;
        mutateFinder(pixels, fixture.logicalTile(), lastFinderStart, lastFinderStart, 1);
        NormalizedCaptureFrame exactFrame = frame(pixels);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(pixels);

        FrameSample cameraSample = sampler.sample(cameraFrame);
        FrameInspection exactInspection = sampler.inspect(exactFrame);
        FrameInspection cameraInspection = sampler.inspect(cameraFrame);
        CandidateInspection acceptedCandidate = cameraInspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, cameraSample.status()),
                () -> assertEquals(List.of(fixture.payload()), cameraSample.payloads()),
                () -> assertTrue(exactInspection.slots().get(0).candidates().stream()
                        .noneMatch(candidate ->
                                candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE)),
                () -> assertEquals(CandidateInspectionStatus.FINDER_CANDIDATE, acceptedCandidate.status()),
                () -> assertEquals("true", acceptedCandidate.samplingDiagnostics().get("finderCanonicalized")),
                () -> assertEquals("3", acceptedCandidate.samplingDiagnostics().get("finderRecoverableCount")),
                () -> assertEquals(1, cameraInspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived scaled display frames can use the encoded layout profile")
    void cameraDerivedScaledDisplayFramesCanUseEncodedLayoutProfile() {
        RenderedTileFixture fixture = renderedTileFixture(18);
        NormalizedCaptureFrame scaledFrame = cameraDerivedFrame(
                scaleNearest(fixture.frame().copyArgbPixels(), 1280, 720, 2),
                2560,
                1440,
                "desktop-1440p-balanced"
        );

        FrameSample sample = sampler.sample(scaledFrame);
        FrameInspection inspection = sampler.inspect(scaledFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals("debug-low-density", inspection.layoutProfileId()),
                () -> assertEquals("debug-low-density", inspection.selectedLayoutProfileId().orElseThrow()),
                () -> assertEquals(ProfileSelectionSource.DECODED_PAYLOAD, inspection.profileSelectionSource()),
                () -> assertEquals(3, inspection.profileAttempts().size()),
                () -> assertEquals("debug-low-density",
                        inspection.profileAttempts().get(2).decodedPayloadLayoutProfileId().orElseThrow()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived proportionally scaled display frames can use the encoded layout profile")
    void cameraDerivedProportionallyScaledDisplayFramesCanUseEncodedLayoutProfile() {
        RenderedTileFixture fixture = renderedTileFixture(DESKTOP_1080P_SAFE_LAYOUT, 0, 18);
        NormalizedCaptureFrame scaledFrame = cameraDerivedFrame(
                scaleNearestTo(
                        fixture.frame().copyArgbPixels(),
                        DESKTOP_1080P_SAFE_LAYOUT.frameWidthPx(),
                        DESKTOP_1080P_SAFE_LAYOUT.frameHeightPx(),
                        2560,
                        1440
                ),
                2560,
                1440,
                "desktop-1440p-balanced"
        );

        FrameSample sample = sampler.sample(scaledFrame);
        FrameInspection inspection = sampler.inspect(scaledFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals("desktop-1080p-safe", inspection.layoutProfileId()),
                () -> assertEquals("desktop-1080p-safe", inspection.selectedLayoutProfileId().orElseThrow()),
                () -> assertEquals(ProfileSelectionSource.DECODED_PAYLOAD, inspection.profileSelectionSource()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Decoded payloads with wrong layout profile remain rejected with slot diagnostics")
    void decodedPayloadsWithWrongLayoutProfileRemainRejectedWithSlotDiagnostics() {
        TilePayload mismatchedPayload = payload(
                DESKTOP_1080P_SAFE_LAYOUT,
                0,
                CAPTURE_LAYOUT.profileId(),
                CAPTURE_LAYOUT.rows() * CAPTURE_LAYOUT.cols()
        );
        FixedLayoutPlan layoutPlan = new FixedLayoutPlanner().plan(DESKTOP_1080P_SAFE_LAYOUT);
        byte[] envelope = envelopeCodec.serialize(mismatchedPayload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TILE_PROFILE);
        RenderedTile renderedTile = new TileRasterRenderer().render(logicalTile, layoutPlan);
        int[] framePixels = new int[DESKTOP_1080P_SAFE_LAYOUT.frameWidthPx()
                * DESKTOP_1080P_SAFE_LAYOUT.frameHeightPx()];
        Arrays.fill(framePixels, 0xFF000000);
        pasteTile(framePixels, DESKTOP_1080P_SAFE_LAYOUT.frameWidthPx(),
                layoutPlan.tilePlacements().get(0), renderedTile, 0);
        NormalizedCaptureFrame frame = frame(framePixels, DESKTOP_1080P_SAFE_LAYOUT);

        FrameSample sample = sampler.sample(frame);
        FrameInspection inspection = sampler.inspect(frame);
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);
        CandidateInspection rejectedCandidate = inspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertEquals(CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE, diagnostic.code()),
                () -> assertEquals(ProfileSelectionSource.NONE, inspection.profileSelectionSource()),
                () -> assertTrue(inspection.selectedLayoutProfileId().isEmpty()),
                () -> assertEquals(1, inspection.profileAttempts().size()),
                () -> assertTrue(inspection.profileAttempts().get(0).slotValidationLayoutProfileMismatchCount() > 0),
                () -> assertEquals("SLOT_VALIDATION", rejectedCandidate.decodeDiagnostics()
                        .get("postPalette.failureStage")),
                () -> assertEquals("desktop-1080p-safe",
                        rejectedCandidate.decodeDiagnostics().get("slotValidation.expectedLayoutProfileId")),
                () -> assertEquals("debug-low-density",
                        rejectedCandidate.decodeDiagnostics().get("slotValidation.actualLayoutProfileId")),
                () -> assertEquals("0", rejectedCandidate.decodeDiagnostics().get("slotValidation.expectedTileIndex")),
                () -> assertEquals("0", rejectedCandidate.decodeDiagnostics().get("slotValidation.actualTileIndex")),
                () -> assertEquals("4", rejectedCandidate.decodeDiagnostics().get("slotValidation.expectedTotalTiles")),
                () -> assertEquals("2", rejectedCandidate.decodeDiagnostics().get("slotValidation.actualTotalTiles"))
        );
    }

    @Test
    @DisplayName("Explicit sampling evidence aligns camera-derived tile slots")
    void explicitSamplingEvidenceAlignsCameraDerivedTileSlots() {
        RenderedTileFixture fixture = renderedTileFixture(18, 36, -40);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());
        CaptureMediaTilePayloadSampler evidenceSampler = new CaptureMediaTilePayloadSampler(
                (frame, layoutPlan) -> Optional.of(samplingEvidence(36, -40, 0, 0, 0.92d))
        );

        FrameSample sample = evidenceSampler.sample(cameraFrame);
        FrameInspection inspection = evidenceSampler.inspect(cameraFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, inspection.slots().get(0).borderStatus()),
                () -> assertEquals(TileAlignmentInspectionSource.SAMPLING_EVIDENCE,
                        inspection.slots().get(0).effectiveTilePlacementSource()),
                () -> assertEquals(TileEvidenceAlignmentStatus.USED_BORDER_SIGNATURE,
                        inspection.slots().get(0).samplingEvidenceAlignmentStatus()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Low-confidence sampling evidence reports why legacy border scan was used")
    void lowConfidenceSamplingEvidenceReportsWhyLegacyBorderScanWasUsed() {
        RenderedTileFixture fixture = renderedTileFixture(18, 36, -40);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());
        CaptureMediaTilePayloadSampler evidenceSampler = new CaptureMediaTilePayloadSampler(
                (frame, layoutPlan) -> Optional.of(samplingEvidence(36, -40, 0, 0, 0.54d))
        );

        FrameSample sample = evidenceSampler.sample(cameraFrame);
        SlotInspection slot = evidenceSampler.inspect(cameraFrame).slots().get(0);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(TileAlignmentInspectionSource.LEGACY_BORDER_SCAN,
                        slot.effectiveTilePlacementSource()),
                () -> assertEquals(TileEvidenceAlignmentStatus.CONFIDENCE_BELOW_THRESHOLD,
                        slot.samplingEvidenceAlignmentStatus()),
                () -> assertEquals(36, slot.effectiveTileShiftXPx()),
                () -> assertEquals(-40, slot.effectiveTileShiftYPx())
        );
    }

    @Test
    @DisplayName("High-confidence sampling evidence can validate a finder-aligned tile with eroded border")
    void highConfidenceSamplingEvidenceCanValidateFinderAlignedTileWithErodedBorder() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] pixels = fixture.frame().copyArgbPixels();
        paintTileBorderColor(pixels, 0xFF000000);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(pixels);
        CaptureMediaTilePayloadSampler evidenceSampler = new CaptureMediaTilePayloadSampler(
                (frame, layoutPlan) -> Optional.of(samplingEvidence(0, 0, 0, 0, 0.92d))
        );

        FrameSample sample = evidenceSampler.sample(cameraFrame);
        FrameInspection inspection = evidenceSampler.inspect(cameraFrame);
        SlotInspection slot = inspection.slots().get(0);
        CandidateInspection acceptedCandidate = slot.candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertTrue(sample.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertEquals(BorderInspectionStatus.NO_SIGNATURE, slot.borderStatus()),
                () -> assertEquals(TileAlignmentInspectionSource.SAMPLING_EVIDENCE,
                        slot.effectiveTilePlacementSource()),
                () -> assertEquals(TileEvidenceAlignmentStatus.USED_FINDER_EVIDENCE,
                        slot.samplingEvidenceAlignmentStatus()),
                () -> assertEquals(DecodeInspectionStatus.ACCEPTED_PAYLOAD, acceptedCandidate.decodeStatus()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived fallback and explicit sampling evidence handle noisy module centers")
    void cameraDerivedFallbackAndExplicitSamplingEvidenceHandleNoisyModuleCenters() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] noisyPixels = fixture.frame().copyArgbPixels();
        mutateLogicalModuleCenters(noisyPixels, fixture.logicalTile(), 0xFF808080);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(noisyPixels);
        CaptureMediaTilePayloadSampler evidenceSampler = new CaptureMediaTilePayloadSampler(
                (frame, layoutPlan) -> Optional.of(samplingEvidence(0, 0, 0, 0, 0.88d))
        );

        FrameSample legacySample = sampler.sample(cameraFrame);
        FrameSample evidenceSample = evidenceSampler.sample(cameraFrame);
        FrameInspection legacyInspection = sampler.inspect(cameraFrame);
        FrameInspection evidenceInspection = evidenceSampler.inspect(cameraFrame);
        CandidateInspection fallbackCandidate = legacyInspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();
        CandidateInspection acceptedCandidate = evidenceInspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, legacySample.status()),
                () -> assertEquals(List.of(fixture.payload()), legacySample.payloads()),
                () -> assertEquals(ModuleSamplingInspectionSource.FALLBACK_SEARCH,
                        fallbackCandidate.moduleSamplingOffsetSource()),
                () -> assertEquals(FrameSampleStatus.ACCEPTED, evidenceSample.status()),
                () -> assertEquals(List.of(fixture.payload()), evidenceSample.payloads()),
                () -> assertEquals(0, acceptedCandidate.moduleCenterOffsetXPx()),
                () -> assertEquals(0, acceptedCandidate.moduleCenterOffsetYPx()),
                () -> assertEquals(ModuleSamplingInspectionSource.TILE_EVIDENCE,
                        acceptedCandidate.moduleSamplingOffsetSource()),
                () -> assertTrue(acceptedCandidate.areaSampleRadiusPx() > 0),
                () -> assertEquals(1, evidenceInspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Sample and inspect paths select equivalent phase evidence")
    void sampleAndInspectPathsSelectEquivalentPhaseEvidence() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] noisyPixels = fixture.frame().copyArgbPixels();
        mutateLogicalModuleCenters(noisyPixels, fixture.logicalTile(), 0xFF808080);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(noisyPixels);

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);
        CandidateInspection acceptedCandidate = inspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD)
                .findFirst()
                .orElseThrow();
        PhaseInspection selectedPhase = acceptedCandidate.selectedPhase();

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(1, inspection.decodedPayloadCount()),
                () -> assertEquals(CaptureMediaModulePhaseOutcome.ACCEPTED_PAYLOAD, selectedPhase.outcome()),
                () -> assertEquals(CaptureMediaModulePhaseCandidateSource.BOUNDED_SEARCH, selectedPhase.source()),
                () -> assertEquals(acceptedCandidate.moduleCenterOffsetXPx(),
                        (int) selectedPhase.moduleCenterOffsetXPx()),
                () -> assertEquals(acceptedCandidate.moduleCenterOffsetYPx(),
                        (int) selectedPhase.moduleCenterOffsetYPx()),
                () -> assertEquals(acceptedCandidate.moduleSizePx(), (int) selectedPhase.moduleSizePx()),
                () -> assertTrue(selectedPhase.attemptedVariantCount() > 1),
                () -> assertTrue(acceptedCandidate.runnerUpPhase().isPresent())
        );
    }

    @Test
    @DisplayName("Palette-sampled content is not accepted when tile or envelope validation fails")
    void paletteSampledContentIsNotAcceptedWhenTileOrEnvelopeValidationFails() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] corruptedPixels = fixture.frame().copyArgbPixels();
        mutateDataModule(corruptedPixels, fixture.logicalTile(), 3, 3);
        NormalizedCaptureFrame corruptedFrame = frame(corruptedPixels);

        FrameSample sample = sampler.sample(corruptedFrame);
        FrameInspection inspection = sampler.inspect(corruptedFrame);
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);
        CandidateInspection rejectedCandidate = inspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertTrue(rejectedCandidate.decodeFailureReason().orElseThrow().startsWith("tileDecode: ")),
                () -> assertEquals("TILE_DECODE", rejectedCandidate.decodeDiagnostics()
                        .get("postPalette.failureStage")),
                () -> assertTrue(rejectedCandidate.decodeDiagnostics().containsKey("failureStage")),
                () -> assertTrue(rejectedCandidate.decodeDiagnostics().containsKey("encodedBytes")),
                () -> assertTrue(rejectedCandidate.decodeDiagnostics().containsKey("parsedHeaderHex")),
                () -> assertTrue(rejectedCandidate.samplingDiagnostics().containsKey("sampledMatrixSha256")),
                () -> assertTrue(rejectedCandidate.samplingDiagnostics().containsKey("sampledMatrixPrefix")),
                () -> assertTrue(rejectedCandidate.samplingDiagnostics().containsKey("sampledMatrixHistogram"))
        );
    }

    @Test
    @DisplayName("Envelope CRC failures after tile decode report post-palette diagnostics")
    void envelopeCrcFailuresAfterTileDecodeReportPostPaletteDiagnostics() {
        RenderedTileFixture fixture = renderedTileFixtureWithCorruptedEnvelope();

        FrameSample sample = sampler.sample(fixture.frame());
        FrameInspection inspection = sampler.inspect(fixture.frame());
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);
        CandidateInspection rejectedCandidate = inspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE)
                .findFirst()
                .orElseThrow();
        PhaseInspection selectedPhase = rejectedCandidate.selectedPhase();

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertTrue(rejectedCandidate.decodeFailureReason().orElseThrow()
                        .startsWith("envelopeValidation: ")),
                () -> assertEquals("ENVELOPE_VALIDATION", rejectedCandidate.decodeDiagnostics()
                        .get("postPalette.failureStage"))
        );
    }

    @Test
    @DisplayName("Calibrated camera-derived envelope CRC failures remain rejected")
    void calibratedCameraDerivedEnvelopeCrcFailuresRemainRejected() {
        RenderedTileFixture fixture = renderedTileFixtureWithCorruptedEnvelope(50);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);
        CaptureMediaDiagnostic diagnostic = sample.diagnostics().get(0);
        CandidateInspection rejectedCandidate = inspection.slots().get(0).candidates().stream()
                .filter(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE)
                .findFirst()
                .orElseThrow();
        PhaseInspection selectedPhase = rejectedCandidate.selectedPhase();

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertTrue(rejectedCandidate.decodeFailureReason().orElseThrow()
                        .startsWith("envelopeValidation: ")),
                () -> assertEquals("ENVELOPE_VALIDATION", rejectedCandidate.decodeDiagnostics()
                        .get("postPalette.failureStage")),
                () -> assertEquals(0, rejectedCandidate.paletteConfidence().orElseThrow().rejectedSampleCount()),
                () -> assertEquals(CaptureMediaModulePhaseOutcome.ENVELOPE_VALIDATION_FAILURE,
                        selectedPhase.outcome()),
                () -> assertEquals("ENVELOPE_VALIDATION", selectedPhase.failureStage().orElseThrow()),
                () -> assertTrue(selectedPhase.attemptedVariantCount() > 1),
                () -> assertTrue(rejectedCandidate.runnerUpPhase().isPresent())
        );
    }

    private RenderedTileFixture renderedTileFixture(int colorShift) {
        return renderedTileFixture(colorShift, 0, 0);
    }

    private RenderedTileFixture renderedTileFixture(int colorShift, int offsetX, int offsetY) {
        TilePayload payload = payload();
        byte[] envelope = envelopeCodec.serialize(payload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TILE_PROFILE);
        RenderedTile renderedTile = new TileRasterRenderer().render(logicalTile, LAYOUT_PLAN);
        int[] framePixels = new int[CAPTURE_LAYOUT.frameWidthPx() * CAPTURE_LAYOUT.frameHeightPx()];
        Arrays.fill(framePixels, 0xFF000000);
        pasteTile(framePixels, renderedTile, colorShift, offsetX, offsetY);
        return new RenderedTileFixture(frame(framePixels), payload, logicalTile);
    }

    private RenderedTileFixture renderedTileFixtureWithCorruptedEnvelope() {
        return renderedTileFixtureWithCorruptedEnvelope(0);
    }

    private RenderedTileFixture renderedTileFixtureWithCorruptedEnvelope(int colorShift) {
        TilePayload payload = payload();
        byte[] envelope = envelopeCodec.serialize(payload);
        envelope[envelope.length - 1] = (byte) (envelope[envelope.length - 1] ^ 0x01);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TILE_PROFILE);
        RenderedTile renderedTile = new TileRasterRenderer().render(logicalTile, LAYOUT_PLAN);
        int[] framePixels = new int[CAPTURE_LAYOUT.frameWidthPx() * CAPTURE_LAYOUT.frameHeightPx()];
        Arrays.fill(framePixels, 0xFF000000);
        pasteTile(framePixels, renderedTile, colorShift, 0, 0);
        return new RenderedTileFixture(frame(framePixels), payload, logicalTile);
    }

    private RenderedTileFixture renderedTileFixture(LayoutProfile layoutProfile, int tileIndex, int colorShift) {
        FixedLayoutPlan layoutPlan = new FixedLayoutPlanner().plan(layoutProfile);
        TilePayload payload = payload(layoutProfile, tileIndex);
        byte[] envelope = envelopeCodec.serialize(payload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TILE_PROFILE);
        RenderedTile renderedTile = new TileRasterRenderer().render(logicalTile, layoutPlan);
        int[] framePixels = new int[layoutProfile.frameWidthPx() * layoutProfile.frameHeightPx()];
        Arrays.fill(framePixels, 0xFF000000);
        pasteTile(framePixels, layoutProfile.frameWidthPx(), layoutPlan.tilePlacements().get(tileIndex), renderedTile, colorShift);
        return new RenderedTileFixture(frame(framePixels, layoutProfile), payload, logicalTile);
    }

    private RenderedTileFixture finderPhaseShiftedTileFixture() {
        TilePayload payload = payload();
        byte[] envelope = envelopeCodec.serialize(payload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TILE_PROFILE);
        int[] framePixels = new int[CAPTURE_LAYOUT.frameWidthPx() * CAPTURE_LAYOUT.frameHeightPx()];
        Arrays.fill(framePixels, 0xFF000000);
        int baseModuleSize = renderedModuleSize(logicalTile);
        int moduleSizeStep = Math.max(1, baseModuleSize / 12);
        int compactModuleSize = baseModuleSize - (2 * moduleSizeStep);
        paintTileBorder(framePixels);
        paintShiftedTileContent(
                framePixels,
                logicalTile,
                compactModuleSize,
                -compactModuleSize,
                2 * compactModuleSize
        );
        return new RenderedTileFixture(frame(framePixels), payload, logicalTile);
    }

    private PartialFrameFixture partialFrameFixture(int acceptedTileIndex, int undecodableTileIndex) {
        TilePayload acceptedPayload = payload(CAPTURE_LAYOUT, acceptedTileIndex);
        TilePayload undecodablePayload = payload(CAPTURE_LAYOUT, undecodableTileIndex);
        int[] framePixels = new int[CAPTURE_LAYOUT.frameWidthPx() * CAPTURE_LAYOUT.frameHeightPx()];
        Arrays.fill(framePixels, 0xFF000000);
        pasteEncodedTile(framePixels, acceptedPayload, false);
        pasteEncodedTile(framePixels, undecodablePayload, true);
        return new PartialFrameFixture(frame(framePixels), acceptedPayload);
    }

    private void pasteEncodedTile(int[] framePixels, TilePayload payload, boolean corruptLogicalTile) {
        LogicalTile logicalTile = logicalTile(payload);
        if (corruptLogicalTile) {
            logicalTile = corruptedDataModule(logicalTile);
        }
        RenderedTile renderedTile = new TileRasterRenderer().render(logicalTile, LAYOUT_PLAN);
        pasteTile(
                framePixels,
                CAPTURE_LAYOUT.frameWidthPx(),
                LAYOUT_PLAN.tilePlacements().get(payload.tileIndex().value()),
                renderedTile,
                0
        );
    }

    private LogicalTile logicalTile(TilePayload payload) {
        return TileCodecs.defaultEncoder().encode(envelopeCodec.serialize(payload), TILE_PROFILE);
    }

    private LogicalTile corruptedDataModule(LogicalTile tile) {
        List<Integer> colors = new ArrayList<>(tile.moduleColors());
        int index = (3 * tile.widthModules()) + 3;
        colors.set(index, (colors.get(index) + 1) % PALETTE.size());
        return new LogicalTile(
                tile.widthModules(),
                tile.heightModules(),
                tile.quietZoneModules(),
                tile.profileId(),
                colors,
                tile.diagnostics()
        );
    }

    private CaptureMediaDiagnostic partialAcceptanceDiagnostic(FrameSample sample) {
        return sample.diagnostics().stream()
                .filter(diagnostic -> diagnostic.metrics().containsKey("partialAccepted"))
                .findFirst()
                .orElseThrow();
    }

    private TilePayload payload() {
        return payload(CAPTURE_LAYOUT, 0);
    }

    private TilePayload payload(LayoutProfile layoutProfile, int tileIndex) {
        return payload(layoutProfile, tileIndex, layoutProfile.profileId(), layoutProfile.rows() * layoutProfile.cols());
    }

    private TilePayload payload(
            LayoutProfile layoutProfile,
            int tileIndex,
            String layoutProfileId,
            int totalTiles
    ) {
        byte[] body = "media-tile-payload".getBytes(StandardCharsets.UTF_8);
        return new TilePayload(
                1,
                new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")),
                FrameType.DATA,
                0,
                new TileIndex(tileIndex),
                totalTiles,
                layoutProfileId,
                PayloadKind.FILE_CHUNK,
                0,
                body.length,
                crc32c(body),
                0,
                body
        );
    }

    private int crc32c(byte[] body) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(body, 0, body.length);
        return (int) crc32c.getValue();
    }

    private void pasteTile(
            int[] framePixels,
            RenderedTile renderedTile,
            int colorShift,
            int offsetX,
            int offsetY
    ) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        pasteTile(
                framePixels,
                CAPTURE_LAYOUT.frameWidthPx(),
                new TilePlacement(
                        placement.row(),
                        placement.col(),
                        placement.xPx() + offsetX,
                        placement.yPx() + offsetY,
                        placement.widthPx(),
                        placement.heightPx()
                ),
                renderedTile,
                colorShift
        );
    }

    private void pasteTile(
            int[] framePixels,
            int frameWidthPx,
            TilePlacement placement,
            RenderedTile renderedTile,
            int colorShift
    ) {
        for (int row = 0; row < renderedTile.heightPixels(); row++) {
            for (int col = 0; col < renderedTile.widthPixels(); col++) {
                int source = renderedTile.argbPixels().get((row * renderedTile.widthPixels()) + col);
                framePixels[((placement.yPx() + row) * frameWidthPx) + placement.xPx() + col] =
                        shiftPaletteColor(source, colorShift);
            }
        }
    }

    private void paintTileBorder(int[] framePixels) {
        paintTileBorderColor(framePixels, 0xFFFFFFFF);
    }

    private void paintTileBorderColor(int[] framePixels, int argb) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int border = LAYOUT_PLAN.separatorThicknessPx();
        for (int row = 0; row < placement.heightPx(); row++) {
            for (int col = 0; col < placement.widthPx(); col++) {
                if (row < border || row >= placement.heightPx() - border
                        || col < border || col >= placement.widthPx() - border) {
                    framePixels[((placement.yPx() + row) * CAPTURE_LAYOUT.frameWidthPx()) + placement.xPx() + col] =
                            argb;
                }
            }
        }
    }

    private void paintShiftedTileContent(
            int[] framePixels,
            LogicalTile logicalTile,
            int moduleSize,
            int offsetX,
            int offsetY
    ) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int[] contentOffset = compactContentOffset(logicalTile, moduleSize);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        fillRect(
                framePixels,
                placement.xPx() + contentOffset[0] + offsetX,
                placement.yPx() + contentOffset[1] + offsetY,
                logicalSide * moduleSize,
                logicalSide * moduleSize,
                0xFFFFFFFF
        );
        for (int row = 0; row < logicalTile.heightModules(); row++) {
            for (int col = 0; col < logicalTile.widthModules(); col++) {
                fillRect(
                        framePixels,
                        placement.xPx()
                                + contentOffset[0]
                                + offsetX
                                + ((col + logicalTile.quietZoneModules()) * moduleSize),
                        placement.yPx()
                                + contentOffset[1]
                                + offsetY
                                + ((row + logicalTile.quietZoneModules()) * moduleSize),
                        moduleSize,
                        moduleSize,
                        PALETTE.get(logicalTile.moduleColorAt(row, col))
                );
            }
        }
    }

    private int[] compactContentOffset(LogicalTile logicalTile, int moduleSize) {
        int baseModuleSize = renderedModuleSize(logicalTile);
        int[] baseOffset = centeredContentOffset(logicalTile, baseModuleSize);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        double contentCenterX = baseOffset[0] + ((double) logicalSide * baseModuleSize / 2.0d);
        double contentCenterY = baseOffset[1] + ((double) logicalSide * baseModuleSize / 2.0d);
        return new int[] {
                Math.max(0, (int) Math.round(contentCenterX - ((double) logicalSide * moduleSize / 2.0d))),
                Math.max(0, (int) Math.round(contentCenterY - ((double) logicalSide * moduleSize / 2.0d)))
        };
    }

    private int renderedModuleSize(LogicalTile logicalTile) {
        int border = LAYOUT_PLAN.separatorThicknessPx();
        int innerWidth = LAYOUT_PLAN.tileSlotWidthPx() - (2 * border);
        int innerHeight = LAYOUT_PLAN.tileSlotHeightPx() - (2 * border);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        return Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
    }

    private int[] centeredContentOffset(LogicalTile logicalTile, int moduleSize) {
        int border = LAYOUT_PLAN.separatorThicknessPx();
        int innerWidth = LAYOUT_PLAN.tileSlotWidthPx() - (2 * border);
        int innerHeight = LAYOUT_PLAN.tileSlotHeightPx() - (2 * border);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        return new int[] {
                border + ((innerWidth - contentWidth) / 2),
                border + ((innerHeight - contentHeight) / 2)
        };
    }

    private void fillRect(int[] framePixels, int startX, int startY, int width, int height, int color) {
        int left = Math.max(0, startX);
        int top = Math.max(0, startY);
        int rightExclusive = Math.min(CAPTURE_LAYOUT.frameWidthPx(), startX + width);
        int bottomExclusive = Math.min(CAPTURE_LAYOUT.frameHeightPx(), startY + height);
        for (int y = top; y < bottomExclusive; y++) {
            for (int x = left; x < rightExclusive; x++) {
                framePixels[(y * CAPTURE_LAYOUT.frameWidthPx()) + x] = color;
            }
        }
    }

    private int shiftPaletteColor(int argb, int colorShift) {
        if (colorShift == 0) {
            return argb;
        }
        int red = shiftedChannel((argb >>> 16) & 0xFF, colorShift);
        int green = shiftedChannel((argb >>> 8) & 0xFF, colorShift);
        int blue = shiftedChannel(argb & 0xFF, colorShift);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private int shiftedChannel(int value, int colorShift) {
        return value < 128
                ? Math.min(255, value + colorShift)
                : Math.max(0, value - colorShift);
    }

    private void mutateDataModule(int[] framePixels, LogicalTile logicalTile, int moduleRow, int moduleCol) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int border = LAYOUT_PLAN.separatorThicknessPx();
        int innerWidth = LAYOUT_PLAN.tileSlotWidthPx() - (2 * border);
        int innerHeight = LAYOUT_PLAN.tileSlotHeightPx() - (2 * border);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        int replacementColor = PALETTE.get((logicalTile.moduleColorAt(moduleRow, moduleCol) + 1) % PALETTE.size());
        int startX = placement.xPx()
                + offsetX
                + ((moduleCol + logicalTile.quietZoneModules()) * moduleSize);
        int startY = placement.yPx()
                + offsetY
                + ((moduleRow + logicalTile.quietZoneModules()) * moduleSize);
        for (int y = startY; y < startY + moduleSize; y++) {
            for (int x = startX; x < startX + moduleSize; x++) {
                framePixels[(y * CAPTURE_LAYOUT.frameWidthPx()) + x] = replacementColor;
            }
        }
    }

    private void mutateFinder(
            int[] framePixels,
            LogicalTile logicalTile,
            int startModuleRow,
            int startModuleCol,
            int replacementColorIndex
    ) {
        for (int row = startModuleRow; row < startModuleRow + 3; row++) {
            for (int col = startModuleCol; col < startModuleCol + 3; col++) {
                paintLogicalModule(framePixels, logicalTile, row, col, PALETTE.get(replacementColorIndex));
            }
        }
    }

    private void paintLogicalModule(
            int[] framePixels,
            LogicalTile logicalTile,
            int moduleRow,
            int moduleCol,
            int color
    ) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int moduleSize = renderedModuleSize(logicalTile);
        int[] contentOffset = centeredContentOffset(logicalTile, moduleSize);
        fillRect(
                framePixels,
                placement.xPx()
                        + contentOffset[0]
                        + ((moduleCol + logicalTile.quietZoneModules()) * moduleSize),
                placement.yPx()
                        + contentOffset[1]
                        + ((moduleRow + logicalTile.quietZoneModules()) * moduleSize),
                moduleSize,
                moduleSize,
                color
        );
    }

    private void mutateLogicalModuleCenters(int[] framePixels, LogicalTile logicalTile, int replacementColor) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int border = LAYOUT_PLAN.separatorThicknessPx();
        int innerWidth = LAYOUT_PLAN.tileSlotWidthPx() - (2 * border);
        int innerHeight = LAYOUT_PLAN.tileSlotHeightPx() - (2 * border);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        for (int row = 0; row < logicalTile.heightModules(); row++) {
            for (int col = 0; col < logicalTile.widthModules(); col++) {
                int centerX = placement.xPx()
                        + offsetX
                        + ((col + logicalTile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2);
                int centerY = placement.yPx()
                        + offsetY
                        + ((row + logicalTile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2);
                framePixels[(centerY * CAPTURE_LAYOUT.frameWidthPx()) + centerX] = replacementColor;
            }
        }
    }

    private void mutateSparseLogicalModuleCentersBeyondCameraThreshold(
            int[] framePixels,
            LogicalTile logicalTile,
            int interval
    ) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int border = LAYOUT_PLAN.separatorThicknessPx();
        int innerWidth = LAYOUT_PLAN.tileSlotWidthPx() - (2 * border);
        int innerHeight = LAYOUT_PLAN.tileSlotHeightPx() - (2 * border);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        int visited = 0;
        for (int row = 0; row < logicalTile.heightModules(); row++) {
            for (int col = 0; col < logicalTile.widthModules(); col++) {
                if (visited % interval == 0) {
                    int centerX = placement.xPx()
                            + offsetX
                            + ((col + logicalTile.quietZoneModules()) * moduleSize)
                            + (moduleSize / 2);
                    int centerY = placement.yPx()
                            + offsetY
                            + ((row + logicalTile.quietZoneModules()) * moduleSize)
                            + (moduleSize / 2);
                    int index = (centerY * CAPTURE_LAYOUT.frameWidthPx()) + centerX;
                    framePixels[index] = offThresholdNearestPaletteColor(framePixels[index]);
                }
                visited++;
            }
        }
    }

    private int offThresholdNearestPaletteColor(int argb) {
        int red = shiftedTowardNeutral((argb >>> 16) & 0xFF);
        int green = shiftedTowardNeutral((argb >>> 8) & 0xFF);
        int blue = shiftedTowardNeutral(argb & 0xFF);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private int shiftedTowardNeutral(int value) {
        return value < 128 ? Math.min(255, value + 110) : Math.max(0, value - 110);
    }

    private void mutateSparseTileBorderPixels(int[] framePixels, int interval) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int border = LAYOUT_PLAN.separatorThicknessPx();
        int visited = 0;
        for (int row = 0; row < placement.heightPx(); row++) {
            for (int col = 0; col < placement.widthPx(); col++) {
                if (row < border || row >= placement.heightPx() - border
                        || col < border || col >= placement.widthPx() - border) {
                    if (visited % interval == 0) {
                        framePixels[((placement.yPx() + row) * CAPTURE_LAYOUT.frameWidthPx()) + placement.xPx() + col] =
                                0xFF7F7F7F;
                    }
                    visited++;
                }
            }
        }
    }

    private int[] jpegRoundTrip(int[] pixels) throws Exception {
        BufferedImage source = new BufferedImage(
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                BufferedImage.TYPE_INT_RGB
        );
        source.setRGB(
                0,
                0,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                pixels,
                0,
                CAPTURE_LAYOUT.frameWidthPx()
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "jpeg", output)) {
            throw new IllegalStateException("No JPEG ImageIO writer is available");
        }
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
        return decoded.getRGB(
                0,
                0,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                null,
                0,
                CAPTURE_LAYOUT.frameWidthPx()
        );
    }

    private int[] whiteBalancedPixels(int[] pixels) {
        int[] shifted = Arrays.copyOf(pixels, pixels.length);
        for (int index = 0; index < shifted.length; index++) {
            shifted[index] = whiteBalancedColor(shifted[index]);
        }
        return shifted;
    }

    private int whiteBalancedColor(int argb) {
        int red = whiteBalanceChannel((argb >>> 16) & 0xFF, 32, -12);
        int green = whiteBalanceChannel((argb >>> 8) & 0xFF, 22, -7);
        int blue = whiteBalanceChannel(argb & 0xFF, 12, 10);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private int whiteBalanceChannel(int value, int floorLift, int brightShift) {
        return value < 128
                ? clamp(value + floorLift, 0, 255)
                : clamp(value + brightShift, 0, 255);
    }

    private int[] lowContrastPixels(int[] pixels) {
        int[] shifted = Arrays.copyOf(pixels, pixels.length);
        for (int index = 0; index < shifted.length; index++) {
            int argb = shifted[index];
            int red = lowContrastChannel((argb >>> 16) & 0xFF);
            int green = lowContrastChannel((argb >>> 8) & 0xFF);
            int blue = lowContrastChannel(argb & 0xFF);
            shifted[index] = 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
        return shifted;
    }

    private int lowContrastChannel(int value) {
        return clamp(96 + (value / 4), 0, 255);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private NormalizedCaptureFrame frame(int[] pixels) {
        return frame(pixels, CAPTURE_LAYOUT);
    }

    private NormalizedCaptureFrame frame(int[] pixels, LayoutProfile layoutProfile) {
        return new NormalizedCaptureFrame(
                "tile-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                "png",
                "abc123",
                layoutProfile.profileId(),
                FrameCorners.exactFrame(layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx()),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private NormalizedCaptureFrame cameraDerivedFrame(int[] pixels) {
        return cameraDerivedFrame(
                pixels,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                CAPTURE_LAYOUT.profileId()
        );
    }

    private NormalizedCaptureFrame cameraDerivedFrame(
            int[] pixels,
            int widthPixels,
            int heightPixels,
            String layoutProfileId
    ) {
        return new NormalizedCaptureFrame(
                "phone-tile-source.jpeg",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                widthPixels,
                heightPixels,
                widthPixels,
                heightPixels,
                "jpeg",
                "abc123",
                layoutProfileId,
                FrameCorners.exactFrame(widthPixels, heightPixels),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                pixels
        );
    }

    private int[] scaleNearest(int[] sourcePixels, int sourceWidth, int sourceHeight, int scale) {
        int[] scaledPixels = new int[sourcePixels.length * scale * scale];
        int scaledWidth = sourceWidth * scale;
        for (int sourceY = 0; sourceY < sourceHeight; sourceY++) {
            for (int sourceX = 0; sourceX < sourceWidth; sourceX++) {
                int argb = sourcePixels[(sourceY * sourceWidth) + sourceX];
                int targetBaseY = sourceY * scale;
                int targetBaseX = sourceX * scale;
                for (int offsetY = 0; offsetY < scale; offsetY++) {
                    for (int offsetX = 0; offsetX < scale; offsetX++) {
                        scaledPixels[((targetBaseY + offsetY) * scaledWidth) + targetBaseX + offsetX] = argb;
                    }
                }
            }
        }
        return scaledPixels;
    }

    private int[] scaleNearestTo(
            int[] sourcePixels,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        int[] scaledPixels = new int[targetWidth * targetHeight];
        for (int targetY = 0; targetY < targetHeight; targetY++) {
            int sourceY = (int) (((long) targetY * sourceHeight) / targetHeight);
            for (int targetX = 0; targetX < targetWidth; targetX++) {
                int sourceX = (int) (((long) targetX * sourceWidth) / targetWidth);
                scaledPixels[(targetY * targetWidth) + targetX] = sourcePixels[(sourceY * sourceWidth) + sourceX];
            }
        }
        return scaledPixels;
    }

    private CvSamplingEvidence samplingEvidence(
            double gridPhaseOffsetX,
            double gridPhaseOffsetY,
            double moduleCenterOffsetX,
            double moduleCenterOffsetY,
            double confidence
    ) {
        return new CvSamplingEvidence(
                "test",
                Optional.of(new CvGridPhase(
                        gridPhaseOffsetX,
                        gridPhaseOffsetY,
                        confidence,
                        1.0d,
                        OptionalInt.of(0xFFFFFFFF),
                        OptionalInt.of(0xFF000000)
                )),
                List.of(new CvTileSamplingEvidence(
                        0,
                        moduleCenterOffsetX,
                        moduleCenterOffsetY,
                        confidence,
                        1.0d,
                        OptionalInt.of(0xFFFFFFFF),
                        OptionalInt.of(0xFF000000),
                        Map.of("testTileSamplingEvidence", 1.0d)
                )),
                Map.of("testSamplingEvidence", 1.0d)
        );
    }

    private record RenderedTileFixture(
            NormalizedCaptureFrame frame,
            TilePayload payload,
            LogicalTile logicalTile
    ) {
    }

    private record PartialFrameFixture(
            NormalizedCaptureFrame exactFrame,
            TilePayload acceptedPayload
    ) {
    }
}
