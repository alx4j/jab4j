package com.alx4j.jab4j.reader.capture.media.debug;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.cv.CvTileSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

@DisplayName("Capture media candidate debug exporter")
class CaptureMediaCandidateDebugExporterTest {

    private static final int FRAME_WIDTH = 1280;
    private static final int FRAME_HEIGHT = 720;
    private static final LayoutProfile CAPTURE_LAYOUT = new LayoutProfile(
            "debug-low-density",
            1,
            2,
            FRAME_WIDTH,
            FRAME_HEIGHT,
            16,
            40,
            "solidWhite",
            48,
            24,
            "black",
            "preserveAspect"
    );
    private static final FixedLayoutPlan LAYOUT_PLAN = new FixedLayoutPlanner().plan(CAPTURE_LAYOUT);

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Sidecar includes backend, candidate, sampling evidence, and diagnostic fields")
    void sidecarIncludesBackendCandidateSamplingEvidenceAndDiagnosticFields() throws Exception {
        CaptureMediaCandidateDebugExporter exporter = new CaptureMediaCandidateDebugExporter(
                new CaptureMediaTilePayloadSampler((frame, layoutPlan) -> Optional.of(samplingEvidence()))
        );
        NormalizedCaptureFrame frame = normalizedFrame();

        CaptureMediaCandidateDebugExporter.CandidateDebugExport exported = exporter.export(
                frame,
                tempDir,
                "boofcv-test",
                "1.2.3"
        );

        String metadata = Files.readString(exported.metadataPath());
        assertAll(
                () -> assertTrue(metadata.contains("cv.backendId=boofcv-test")),
                () -> assertTrue(metadata.contains("cv.backendVersion=1.2.3")),
                () -> assertTrue(metadata.contains("candidate.rank=1")),
                () -> assertTrue(metadata.contains("candidate.normalizationLayoutProfileId=debug-low-density")),
                () -> assertTrue(metadata.contains("candidate.sourceRegionRank=1")),
                () -> assertTrue(metadata.contains("candidate.profileAlternativeRank=1")),
                () -> assertTrue(metadata.contains("candidate.profileAlternativeCount=1")),
                () -> assertTrue(metadata.contains("candidate.sourceBounds.rightExclusivePx=1280.0")),
                () -> assertTrue(metadata.contains("candidate.corners.bottomRightY=720.0")),
                () -> assertTrue(metadata.contains("perspective.frameCoverageRatio=1.0")),
                () -> assertTrue(metadata.contains("sampler.evidence.available=true")),
                () -> assertTrue(metadata.contains("sampler.evidence.backendId=boofcv-test")),
                () -> assertTrue(metadata.contains("sampler.gridPhase.available=true")),
                () -> assertTrue(metadata.contains("sampler.gridPhase.offsetXPx=1.25")),
                () -> assertTrue(metadata.contains("sampler.evidence.tile.0.moduleCenterOffsetXPx=0.5")),
                () -> assertTrue(metadata.contains("sampler.evidence.metric.gridSharpness=0.73")),
                () -> assertTrue(metadata.contains("sampler.tileDecode.attemptCount=")),
                () -> assertTrue(metadata.contains("sampler.layoutProfileId=debug-low-density")),
                () -> assertTrue(metadata.contains("sampler.profileAttemptCount=1")),
                () -> assertTrue(metadata.contains("sampler.profileAttempt.0.layoutProfileId=debug-low-density")),
                () -> assertTrue(metadata.contains("sampler.profileAttempt.0.rows=1")),
                () -> assertTrue(metadata.contains("sampler.profileAttempt.0.cols=2")),
                () -> assertTrue(metadata.contains("sampler.profileAttempt.0.status=EMPTY")),
                () -> assertTrue(metadata.contains("sampler.profileAttempt.0.decodedPayloadCount=0")),
                () -> assertTrue(metadata.contains("sampler.profileAttempt.0.tileDecodeAttemptCount=0")),
                () -> assertTrue(metadata.contains(
                        "sampler.profileAttempt.0.slotValidation.layoutProfileMismatchCount=0")),
                () -> assertTrue(metadata.contains("sampler.profileAttempt.0.decodedPayloadLayoutProfileId=")),
                () -> assertTrue(metadata.contains("sampler.selectedLayoutProfileId=")),
                () -> assertTrue(metadata.contains("sampler.profileSelectionSource=none")),
                () -> assertTrue(metadata.contains("sampler.envelope.rejectedAttemptCount=")),
                () -> assertTrue(metadata.contains("sampler.reason.borderNoSignatureSlotCount=2")),
                () -> assertTrue(metadata.contains("sampler.reason.finderCandidateAttemptCount=0")),
                () -> assertTrue(metadata.contains("sampler.slot.0.effectiveTileShiftXPx=0")),
                () -> assertTrue(metadata.contains("sampler.slot.0.effectiveTileShiftYPx=0")),
                () -> assertTrue(metadata.contains("sampler.slot.0.effectiveTilePlacementSource=NOMINAL")),
                () -> assertTrue(metadata.contains(
                        "sampler.slot.0.samplingEvidenceAlignmentStatus=NOT_CAMERA_DERIVED")),
                () -> assertTrue(metadata.contains("sampler.slot.0.reason.noFinderAttemptCount=0")),
                () -> assertTrue(metadata.contains("diagnostic.selectedPublicCode=SCREEN_OR_FRAME_NOT_FOUND"))
        );
    }

    @Test
    @DisplayName("Export writes deterministic grid overlay artifact and compact overlay sidecar counts")
    void exportWritesDeterministicGridOverlayArtifactAndCompactOverlaySidecarCounts() throws Exception {
        CaptureMediaCandidateDebugExporter exporter = new CaptureMediaCandidateDebugExporter(
                new CaptureMediaTilePayloadSampler((frame, layoutPlan) -> Optional.of(samplingEvidence()))
        );
        NormalizedCaptureFrame frame = signedCameraDerivedFrame();

        CaptureMediaCandidateDebugExporter.CandidateDebugExport exported = exporter.export(frame, tempDir);

        Path overlayPath = tempDir.resolve("candidate-0000-grid-overlay.png");
        BufferedImage candidateImage = ImageIO.read(exported.imagePath().toFile());
        BufferedImage overlayImage = ImageIO.read(overlayPath.toFile());
        String metadata = Files.readString(exported.metadataPath());
        assertAll(
                () -> assertEquals(Optional.of(overlayPath), exported.overlayPath()),
                () -> assertTrue(Files.isRegularFile(exported.imagePath())),
                () -> assertTrue(Files.isRegularFile(exported.metadataPath())),
                () -> assertTrue(Files.isRegularFile(overlayPath)),
                () -> assertTrue(Files.size(overlayPath) > 0L),
                () -> assertNotNull(candidateImage),
                () -> assertNotNull(overlayImage),
                () -> assertEquals(FRAME_WIDTH, overlayImage.getWidth()),
                () -> assertEquals(FRAME_HEIGHT, overlayImage.getHeight()),
                () -> assertTrue(imagesDiffer(candidateImage, overlayImage)),
                () -> assertTrue(metadata.contains("debug.gridOverlayPath=candidate-0000-grid-overlay.png")),
                () -> assertTrue(metadata.contains("overlay.tileSlotCount=2")),
                () -> assertTrue(metadata.contains("overlay.sideVersionAttemptCount=")),
                () -> assertTrue(metadata.contains("overlay.candidateAttemptCount=")),
                () -> assertTrue(metadata.contains("overlay.noFinderAttemptCount=")),
                () -> assertTrue(metadata.contains("overlay.paletteRejectedAttemptCount=")),
                () -> assertTrue(metadata.contains("overlay.finderCandidateAttemptCount=")),
                () -> assertTrue(metadata.contains("overlay.tileDecodeAttemptCount=")),
                () -> assertTrue(metadata.contains("overlay.envelope.acceptedPayloadCount=")),
                () -> assertTrue(metadata.contains("overlay.envelope.rejectedAttemptCount="))
        );
    }

    @Test
    @DisplayName("Sidecar includes deterministic sampler geometry for camera-derived signed candidates")
    void sidecarIncludesDeterministicSamplerGeometryForCameraDerivedSignedCandidates() throws Exception {
        CaptureMediaCandidateDebugExporter exporter = new CaptureMediaCandidateDebugExporter(
                new CaptureMediaTilePayloadSampler((frame, layoutPlan) -> Optional.of(samplingEvidence()))
        );
        NormalizedCaptureFrame frame = signedCameraDerivedFrame();

        CaptureMediaCandidateDebugExporter.CandidateDebugExport exported = exporter.export(frame, tempDir);

        String metadata = Files.readString(exported.metadataPath());
        assertAll(
                () -> assertTrue(metadata.contains("sampler.reason.borderSignatureSlotCount=1")),
                () -> assertTrue(metadata.contains("sampler.slot.0.effectiveTileShiftXPx=1")),
                () -> assertTrue(metadata.contains("sampler.slot.0.effectiveTileShiftYPx=0")),
                () -> assertTrue(metadata.contains("sampler.slot.0.effectiveTilePlacementSource=SAMPLING_EVIDENCE")),
                () -> assertTrue(metadata.contains(
                        "sampler.slot.0.samplingEvidenceAlignmentStatus=USED_BORDER_SIGNATURE")),
                () -> assertTrue(metadata.contains("sampler.slot.0.reason.noFinderAttemptCount=")),
                () -> assertTrue(metadata.contains(".moduleSampling.offsetXPx=1")),
                () -> assertTrue(metadata.contains(".moduleSampling.offsetYPx=0")),
                () -> assertTrue(metadata.contains(".moduleSampling.offsetSource=TILE_EVIDENCE")),
                () -> assertTrue(metadata.contains(".moduleSampling.areaSampleRadiusPx=3")),
                () -> assertTrue(metadata.contains("diagnostic.selectedPublicCode=COLOR_OR_COMPRESSION_SHIFT"))
        );
    }

    @Test
    @DisplayName("Default sidecar includes reader-owned evidence for camera-derived candidates")
    void defaultSidecarIncludesReaderOwnedEvidenceForCameraDerivedCandidates() throws Exception {
        CaptureMediaCandidateDebugExporter exporter = new CaptureMediaCandidateDebugExporter();
        NormalizedCaptureFrame frame = cameraDerivedNormalizedFrame();

        CaptureMediaCandidateDebugExporter.CandidateDebugExport exported = exporter.export(
                frame,
                tempDir,
                "boofcv-test",
                "1.2.3"
        );

        String metadata = Files.readString(exported.metadataPath());
        assertAll(
                () -> assertTrue(metadata.contains("sampler.evidence.available=true")),
                () -> assertTrue(metadata.contains("sampler.evidence.backendId=reader-normalized-argb")),
                () -> assertTrue(metadata.contains("sampler.gridPhase.available=true")),
                () -> assertTrue(metadata.contains("sampler.evidence.metric.readerSamplingEvidenceConfidence="))
        );
    }

    @Test
    @DisplayName("Sidecar includes tile decode diagnostics for finder candidates rejected by tile decode")
    void sidecarIncludesTileDecodeDiagnosticsForRejectedFinderCandidates() throws Exception {
        CaptureMediaCandidateDebugExporter exporter = new CaptureMediaCandidateDebugExporter();
        NormalizedCaptureFrame frame = tileDecodeRejectedFrame();

        CaptureMediaCandidateDebugExporter.CandidateDebugExport exported = exporter.export(frame, tempDir);

        String metadata = Files.readString(exported.metadataPath());
        assertAll(
                () -> assertTrue(metadata.contains(".decodeFailureReason=tileDecode: ")),
                () -> assertTrue(metadata.contains(".tileDecode.attempted=true")),
                () -> assertTrue(metadata.contains(".tileDecode.failureReason=tileDecode: ")),
                () -> assertTrue(metadata.contains(".tileDecode.failureStage=")),
                () -> assertTrue(metadata.contains(".tileDecode.encodedBytes=")),
                () -> assertTrue(metadata.contains(".tileDecode.logicalCapacityBytes=")),
                () -> assertTrue(metadata.contains(".tileDecode.headerBytes=")),
                () -> assertTrue(metadata.contains(".tileDecode.parsedHeaderHex=")),
                () -> assertTrue(metadata.contains(".tileDecode.headerPayloadLength=")),
                () -> assertTrue(metadata.contains(".tileDecode.expectedEncodedBytes=")),
                () -> assertTrue(metadata.contains(".tileDecode.encodedLengthDelta=")),
                () -> assertTrue(metadata.contains(".tileDecode.logicalTile.sha256=")),
                () -> assertTrue(metadata.contains(".tileDecode.logicalTile.prefix=")),
                () -> assertTrue(metadata.contains(".tileDecode.logicalTile.histogram=")),
                () -> assertTrue(metadata.contains(".finder.exact=true")),
                () -> assertTrue(metadata.contains(".finder.canonicalized=false")),
                () -> assertTrue(metadata.contains(".diagnostic.sampledMatrixSha256=")),
                () -> assertTrue(metadata.contains(".diagnostic.sampledMatrixPrefix=")),
                () -> assertTrue(metadata.contains(".diagnostic.sampledMatrixHistogram="))
        );
    }

    @Test
    @DisplayName("Sidecar includes partial acceptance summary fields")
    void sidecarIncludesPartialAcceptanceSummaryFields() throws Exception {
        CaptureMediaCandidateDebugExporter exporter = new CaptureMediaCandidateDebugExporter();
        NormalizedCaptureFrame frame = partialAcceptedCameraFrame();

        CaptureMediaCandidateDebugExporter.CandidateDebugExport exported = exporter.export(frame, tempDir);

        String metadata = Files.readString(exported.metadataPath());
        assertAll(
                () -> assertTrue(metadata.contains("sampler.partialAccepted=true")),
                () -> assertTrue(metadata.contains("sampler.partialRejectedSlotCount=0")),
                () -> assertTrue(metadata.contains("sampler.partialUndecodableSlotCount=1")),
                () -> assertTrue(metadata.contains("sampler.partialAcceptedPayloadCount=1")),
                () -> assertTrue(metadata.contains("sampler.partialWarningCount=1")),
                () -> assertTrue(metadata.contains("sampler.slot.0.effectiveTilePlacementSource=")),
                () -> assertTrue(metadata.contains("sampler.slot.1.reason.tileOrEnvelopeRejectedAttemptCount="))
        );
    }

    private boolean imagesDiffer(BufferedImage first, BufferedImage second) {
        if (first == null || second == null
                || first.getWidth() != second.getWidth()
                || first.getHeight() != second.getHeight()) {
            return false;
        }
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private NormalizedCaptureFrame normalizedFrame() {
        int[] pixels = new int[FRAME_WIDTH * FRAME_HEIGHT];
        Arrays.fill(pixels, 0xFF000000);
        return new NormalizedCaptureFrame(
                "candidate-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                "abc123",
                "debug-low-density",
                FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private NormalizedCaptureFrame signedCameraDerivedFrame() {
        int[] pixels = new int[FRAME_WIDTH * FRAME_HEIGHT];
        Arrays.fill(pixels, 0xFF000000);
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        int border = LAYOUT_PLAN.separatorThicknessPx();
        for (int row = 0; row < placement.heightPx(); row++) {
            for (int col = 0; col < placement.widthPx(); col++) {
                if (row < border || row >= placement.heightPx() - border
                        || col < border || col >= placement.widthPx() - border) {
                    pixels[((placement.yPx() + row) * FRAME_WIDTH) + placement.xPx() + col] = 0xFFFFFFFF;
                }
            }
        }
        return new NormalizedCaptureFrame(
                "camera-candidate.jpeg",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "jpeg",
                "def456",
                CAPTURE_LAYOUT.profileId(),
                FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                pixels
        );
    }

    private NormalizedCaptureFrame cameraDerivedNormalizedFrame() {
        int[] pixels = new int[FRAME_WIDTH * FRAME_HEIGHT];
        Arrays.fill(pixels, 0xFF000000);
        drawSyncBand(pixels);
        return new NormalizedCaptureFrame(
                "candidate-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                1600,
                1000,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                "abc123",
                "debug-low-density",
                new FrameCorners(50.0d, 60.0d, 1250.0d, 80.0d, 1240.0d, 700.0d, 40.0d, 680.0d),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                pixels
        );
    }

    private NormalizedCaptureFrame tileDecodeRejectedFrame() {
        int[] pixels = new int[FRAME_WIDTH * FRAME_HEIGHT];
        Arrays.fill(pixels, 0xFF000000);
        TilePayload payload = payload();
        byte[] envelope = new TilePayloadEnvelopeCodec().serialize(payload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
        LogicalTile corrupted = corruptedDataModule(logicalTile);
        RenderedTile renderedTile = new TileRasterRenderer().render(corrupted, LAYOUT_PLAN);
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(0);
        for (int row = 0; row < renderedTile.heightPixels(); row++) {
            for (int col = 0; col < renderedTile.widthPixels(); col++) {
                pixels[((placement.yPx() + row) * FRAME_WIDTH) + placement.xPx() + col] =
                        renderedTile.argbPixels().get((row * renderedTile.widthPixels()) + col);
            }
        }
        return new NormalizedCaptureFrame(
                "tile-decode-rejected.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                "feedface",
                CAPTURE_LAYOUT.profileId(),
                FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private NormalizedCaptureFrame partialAcceptedCameraFrame() {
        int[] pixels = new int[FRAME_WIDTH * FRAME_HEIGHT];
        Arrays.fill(pixels, 0xFF000000);
        pasteRenderedTile(pixels, 0, renderedTile(payload(0), false));
        pasteRenderedTile(pixels, 1, renderedTile(payload(1), true));
        return new NormalizedCaptureFrame(
                "partial-camera-candidate.jpeg",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "jpeg",
                "facefeed",
                CAPTURE_LAYOUT.profileId(),
                FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                pixels
        );
    }

    private RenderedTile renderedTile(TilePayload payload, boolean corruptLogicalTile) {
        byte[] envelope = new TilePayloadEnvelopeCodec().serialize(payload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
        if (corruptLogicalTile) {
            logicalTile = corruptedDataModule(logicalTile);
        }
        return new TileRasterRenderer().render(logicalTile, LAYOUT_PLAN);
    }

    private void pasteRenderedTile(int[] pixels, int tileIndex, RenderedTile renderedTile) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(tileIndex);
        for (int row = 0; row < renderedTile.heightPixels(); row++) {
            for (int col = 0; col < renderedTile.widthPixels(); col++) {
                pixels[((placement.yPx() + row) * FRAME_WIDTH) + placement.xPx() + col] =
                        renderedTile.argbPixels().get((row * renderedTile.widthPixels()) + col);
            }
        }
    }

    private LogicalTile corruptedDataModule(LogicalTile tile) {
        List<Integer> colors = new ArrayList<>(tile.moduleColors());
        int index = (3 * tile.widthModules()) + 3;
        colors.set(index, (colors.get(index) + 1) % 8);
        return new LogicalTile(
                tile.widthModules(),
                tile.heightModules(),
                tile.quietZoneModules(),
                tile.profileId(),
                colors,
                tile.diagnostics()
        );
    }

    private TilePayload payload() {
        return payload(0);
    }

    private TilePayload payload(int tileIndex) {
        byte[] body = "debug-sidecar".getBytes(StandardCharsets.UTF_8);
        return new TilePayload(
                1,
                new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")),
                FrameType.DATA,
                0,
                new TileIndex(tileIndex),
                CAPTURE_LAYOUT.rows() * CAPTURE_LAYOUT.cols(),
                CAPTURE_LAYOUT.profileId(),
                PayloadKind.FILE_CHUNK,
                tileIndex,
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

    private void drawSyncBand(int[] pixels) {
        int outerMarginPx = 40;
        int topSyncBandPx = 48;
        int cellWidth = 16;
        int rightExclusive = FRAME_WIDTH - outerMarginPx;
        for (int row = outerMarginPx; row < outerMarginPx + topSyncBandPx; row++) {
            for (int col = outerMarginPx; col < rightExclusive; col++) {
                int segment = (col - outerMarginPx) / cellWidth;
                pixels[(row * FRAME_WIDTH) + col] = segment % 2 == 0 ? 0xFFFFFFFF : 0xFF000000;
            }
        }
    }

    private CvSamplingEvidence samplingEvidence() {
        return new CvSamplingEvidence(
                "boofcv-test",
                Optional.of(new CvGridPhase(
                        1.25d,
                        -0.75d,
                        0.91d,
                        0.86d,
                        OptionalInt.of(0xFFFFFFFF),
                        OptionalInt.of(0xFF000000)
                )),
                List.of(new CvTileSamplingEvidence(
                        0,
                        0.50d,
                        -0.25d,
                        0.93d,
                        0.88d,
                        OptionalInt.of(0xFFFFFFFF),
                        OptionalInt.of(0xFF000000),
                        Map.of("tileSharpness", 0.81d)
                )),
                Map.of("gridSharpness", 0.73d)
        );
    }
}
