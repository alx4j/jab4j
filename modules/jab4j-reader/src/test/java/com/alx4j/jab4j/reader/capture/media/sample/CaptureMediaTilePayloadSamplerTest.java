package com.alx4j.jab4j.reader.capture.media.sample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.zip.CRC32C;
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
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.DecodeInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameInspection;
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

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertTrue(sample.diagnostics().isEmpty()),
                () -> assertEquals(1.0d, sample.paletteConfidence().orElseThrow().minimumConfidence()),
                () -> assertEquals(0, sample.paletteConfidence().orElseThrow().lowConfidenceSampleCount()),
                () -> assertEquals(1, inspection.decodedPayloadCount()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, inspection.slots().get(0).borderStatus()),
                () -> assertTrue(inspection.slots().get(0).interiorContent()),
                () -> assertTrue(inspection.slots().get(0).candidates().stream()
                        .anyMatch(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD))
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
    @DisplayName("Camera-derived candidates can decode with low-confidence palette expansion")
    void cameraDerivedCandidatesCanDecodeWithLowConfidencePaletteExpansion() {
        RenderedTileFixture fixture = renderedTileFixture(50);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                        sample.diagnostics().get(0).code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.WARNING, sample.diagnostics().get(0).severity()),
                () -> assertTrue(sample.paletteConfidence().orElseThrow().lowConfidenceSampleCount() > 0),
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Camera-derived shifted tile slots are aligned before border sampling")
    void cameraDerivedShiftedTileSlotsAreAlignedBeforeBorderSampling() {
        RenderedTileFixture fixture = renderedTileFixture(18, 36, -40);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(fixture.frame().copyArgbPixels());

        FrameSample sample = sampler.sample(cameraFrame);
        FrameInspection inspection = sampler.inspect(cameraFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.ACCEPTED, sample.status()),
                () -> assertEquals(List.of(fixture.payload()), sample.payloads()),
                () -> assertEquals(BorderInspectionStatus.SIGNATURE, inspection.slots().get(0).borderStatus()),
                () -> assertEquals(1, inspection.decodedPayloadCount())
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
                () -> assertEquals(1, inspection.decodedPayloadCount())
        );
    }

    @Test
    @DisplayName("Explicit sampling evidence enables area sampling for noisy module centers")
    void explicitSamplingEvidenceEnablesAreaSamplingForNoisyModuleCenters() {
        RenderedTileFixture fixture = renderedTileFixture(0);
        int[] noisyPixels = fixture.frame().copyArgbPixels();
        mutateLogicalModuleCenters(noisyPixels, fixture.logicalTile(), 0xFF808080);
        NormalizedCaptureFrame cameraFrame = cameraDerivedFrame(noisyPixels);
        CaptureMediaTilePayloadSampler evidenceSampler = new CaptureMediaTilePayloadSampler(
                (frame, layoutPlan) -> Optional.of(samplingEvidence(0, 0, 0, 0, 0.88d))
        );

        FrameSample legacySample = sampler.sample(cameraFrame);
        FrameSample evidenceSample = evidenceSampler.sample(cameraFrame);
        FrameInspection evidenceInspection = evidenceSampler.inspect(cameraFrame);

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, legacySample.status()),
                () -> assertEquals(FrameSampleStatus.ACCEPTED, evidenceSample.status()),
                () -> assertEquals(List.of(fixture.payload()), evidenceSample.payloads()),
                () -> assertEquals(1, evidenceInspection.decodedPayloadCount())
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

        assertAll(
                () -> assertEquals(FrameSampleStatus.REJECTED, sample.status()),
                () -> assertTrue(sample.payloads().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertTrue(inspection.slots().get(0).candidates().stream()
                        .anyMatch(candidate ->
                                candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE))
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

    private TilePayload payload() {
        byte[] body = "media-tile-payload".getBytes(StandardCharsets.UTF_8);
        return new TilePayload(
                1,
                new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")),
                FrameType.DATA,
                0,
                new TileIndex(0),
                CAPTURE_LAYOUT.rows() * CAPTURE_LAYOUT.cols(),
                CAPTURE_LAYOUT.profileId(),
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
        int destinationX = placement.xPx() + offsetX;
        int destinationY = placement.yPx() + offsetY;
        for (int row = 0; row < renderedTile.heightPixels(); row++) {
            for (int col = 0; col < renderedTile.widthPixels(); col++) {
                int source = renderedTile.argbPixels().get((row * renderedTile.widthPixels()) + col);
                framePixels[((destinationY + row) * CAPTURE_LAYOUT.frameWidthPx()) + destinationX + col] =
                        shiftPaletteColor(source, colorShift);
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

    private NormalizedCaptureFrame frame(int[] pixels) {
        return new NormalizedCaptureFrame(
                "tile-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                "png",
                "abc123",
                CAPTURE_LAYOUT.profileId(),
                FrameCorners.exactFrame(CAPTURE_LAYOUT.frameWidthPx(), CAPTURE_LAYOUT.frameHeightPx()),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private NormalizedCaptureFrame cameraDerivedFrame(int[] pixels) {
        return new NormalizedCaptureFrame(
                "phone-tile-source.jpeg",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                "jpeg",
                "abc123",
                CAPTURE_LAYOUT.profileId(),
                FrameCorners.exactFrame(CAPTURE_LAYOUT.frameWidthPx(), CAPTURE_LAYOUT.frameHeightPx()),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                pixels
        );
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
}
