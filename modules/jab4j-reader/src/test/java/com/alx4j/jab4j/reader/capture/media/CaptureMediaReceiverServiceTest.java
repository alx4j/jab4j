package com.alx4j.jab4j.reader.capture.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.decode.CaptureFrameSetAssembler;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.debug.CaptureMediaCandidateDebugExporter;
import com.alx4j.jab4j.reader.capture.media.decode.CaptureMediaFrameDecoder;
import com.alx4j.jab4j.reader.capture.media.input.CaptureMediaInputIntake;
import com.alx4j.jab4j.reader.capture.media.input.ImageIoCaptureMediaStillImageDecoder;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.RetainedMediaInputFrameBatch;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameSourceAdapter;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoLimits;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.reader.restore.ReaderRestoreService;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;

@DisplayName("Capture media receiver service")
class CaptureMediaReceiverServiceTest {

    private static final LayoutProfile DEBUG_LAYOUT = new LayoutProfile(
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
    private static final int SOURCE_PROBE_PIXEL = 0xFF123456;
    private static final int NORMALIZED_PROBE_PIXEL = 0xFF010203;

    private final CaptureMediaReceiverService service = new CaptureMediaReceiverService();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Direct video input returns stable unsupported diagnostics without requiring a readable file")
    void directVideoInputReturnsStableUnsupportedDiagnosticsWithoutReadableFile() {
        Path mov = tempDir.resolve("phone-capture.mov");
        Path mp4 = tempDir.resolve("phone-capture.mp4");

        CaptureMediaReceiverResult result =
                service.evaluate(CaptureMediaReceiverRequest.evaluateVideoFiles(List.of(mov, mp4)));
        List<String> expectedSourceIds = List.of(mov, mp4).stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .toList();

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertTrue(result.failed()),
                () -> assertEquals(2, result.summary().submittedMediaCount()),
                () -> assertEquals(0, result.summary().readableMediaCount()),
                () -> assertEquals(2, result.summary().rejectedCandidateCount()),
                () -> assertEquals(2, result.diagnostics().size()),
                () -> assertEquals(List.of(
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER
                ), result.diagnostics().stream().map(CaptureMediaDiagnostic::code).toList()),
                () -> assertTrue(result.diagnostics().stream().allMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertTrue(result.diagnostics().stream()
                        .allMatch(diagnostic -> diagnostic.sourceKind().orElseThrow() == CaptureMediaSourceKind.VIDEO_FILE)),
                () -> assertEquals(expectedSourceIds, result.diagnostics().stream()
                        .map(diagnostic -> diagnostic.sourceId().orElseThrow())
                        .toList()),
                () -> assertTrue(result.message().contains(".mov/.mp4"))
        );
    }

    @Test
    @DisplayName("HEIC still input returns a stable unsupported image diagnostic before ImageIO reads")
    void heicStillInputReturnsStableUnsupportedImageDiagnosticBeforeImageIoReads() {
        Path image = tempDir.resolve("phone-photo.heic");

        CaptureMediaReceiverResult result =
                imageIoOnlyService().evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(image)));

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertEquals(1, result.summary().submittedMediaCount()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                        result.diagnostics().get(0).code()),
                () -> assertTrue(result.message().contains("HEIC/HEIF"))
        );
    }

    @Test
    @DisplayName("Corrupted still images return unreadable media diagnostics")
    void corruptedStillImagesReturnUnreadableMediaDiagnostics() throws Exception {
        Path png = tempDir.resolve("corrupted.png");
        Path jpg = tempDir.resolve("corrupted.jpg");
        Files.writeString(png, "not a png");
        Files.writeString(jpg, "not a jpeg");

        CaptureMediaReceiverResult result =
                service.evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(png, jpg)));

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertTrue(result.failed()),
                () -> assertEquals(2, result.summary().submittedMediaCount()),
                () -> assertEquals(0, result.summary().readableMediaCount()),
                () -> assertEquals(2, result.summary().rejectedCandidateCount()),
                () -> assertEquals(2, result.diagnostics().size()),
                () -> assertEquals(List.of(
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA
                ), result.diagnostics().stream().map(CaptureMediaDiagnostic::code).toList()),
                () -> assertTrue(result.diagnostics().stream().allMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertTrue(result.diagnostics().stream()
                        .allMatch(diagnostic -> diagnostic.sourceKind().orElseThrow()
                                == CaptureMediaSourceKind.STILL_IMAGE_FILE)),
                () -> assertEquals(List.of(0, 1), result.diagnostics().stream()
                        .map(diagnostic -> diagnostic.callerOrder().orElseThrow())
                        .toList()),
                () -> assertTrue(result.message().contains("could not be read"))
        );
    }

    @Test
    @DisplayName("Readable PNG with unsupported dimensions is rejected as no recoverable frame")
    void readablePngWithUnsupportedDimensionsIsRejectedAsNoRecoverableFrame() throws Exception {
        Path image = tempDir.resolve("uncropped-photo.png");
        writePng(image, 320, 240);

        CaptureMediaReceiverResult result =
                service.evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(image)));

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertEquals(1, result.summary().submittedMediaCount()),
                () -> assertEquals(1, result.summary().readableMediaCount()),
                () -> assertEquals(0, result.summary().acceptedCandidateCount()),
                () -> assertEquals(1, result.summary().rejectedCandidateCount()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        result.diagnostics().get(0).code())
        );
    }

    @Test
    @DisplayName("Readable JPEG with unsupported dimensions is rejected as no recoverable frame")
    void readableJpegWithUnsupportedDimensionsIsRejectedAsNoRecoverableFrame() throws Exception {
        Path image = tempDir.resolve("uncropped-photo.JPG");
        writeJpeg(image);

        CaptureMediaReceiverResult result =
                service.evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(image)));

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertEquals(1, result.summary().submittedMediaCount()),
                () -> assertEquals(1, result.summary().readableMediaCount()),
                () -> assertEquals(0, result.summary().acceptedCandidateCount()),
                () -> assertEquals(1, result.summary().rejectedCandidateCount()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        result.diagnostics().get(0).code())
        );
    }

    @Test
    @DisplayName("Debug output writes normalized candidate PNG and sampler metadata before decode rejects content")
    void debugOutputWritesNormalizedCandidatePngAndSamplerMetadataBeforeDecodeRejectsContent() throws Exception {
        Path image = tempDir.resolve("candidate.png");
        Path debugOutput = tempDir.resolve("capture-media-debug");
        writePng(image, 1280, 720);

        CaptureMediaReceiverResult result = service.evaluate(
                CaptureMediaReceiverRequest.evaluateStillImages(List.of(image))
                        .withDebugOutputDirectory(debugOutput)
        );

        Path candidateImage = debugOutput.resolve("candidate-0000.png");
        Path candidateMetadata = debugOutput.resolve("candidate-0000.txt");
        String metadata = Files.readString(candidateMetadata);
        assertAll(
                () -> assertTrue(result.failed()),
                () -> assertTrue(result.summary().readableMediaCount() > 0),
                () -> assertFalse(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.DEBUG_EXPORT_FAILURE)),
                () -> assertTrue(Files.isRegularFile(candidateImage)),
                () -> assertTrue(Files.isRegularFile(candidateMetadata)),
                () -> assertTrue(metadata.contains("sourceId=" + image.toAbsolutePath().normalize())),
                () -> assertTrue(metadata.contains("cv.backendId=legacy")),
                () -> assertTrue(metadata.contains("candidate.rank=1")),
                () -> assertTrue(metadata.contains("candidate.sourceBounds.leftPx=0.0")),
                () -> assertTrue(metadata.contains("candidate.corners.bottomRightX=1280.0")),
                () -> assertTrue(metadata.contains("perspective.skewScore=0.0")),
                () -> assertTrue(metadata.contains("layoutProfileId=debug-low-density")),
                () -> assertTrue(metadata.contains("sampler.candidateAttemptCount=")),
                () -> assertTrue(metadata.contains("sampler.decodedPayloadCount=")),
                () -> assertTrue(metadata.contains("sampler.tileDecode.attemptCount=")),
                () -> assertTrue(metadata.contains("sampler.envelope.acceptedPayloadCount=")),
                () -> assertTrue(metadata.contains("diagnostic.selectedPublicCode=SCREEN_OR_FRAME_NOT_FOUND"))
        );
    }

    @Test
    @DisplayName("CV-normalized debug output uses reader-owned sampling evidence")
    void cvNormalizedDebugOutputUsesReaderOwnedSamplingEvidence() throws Exception {
        Path image = tempDir.resolve("phone-photo.png");
        Path debugOutput = tempDir.resolve("cv-normalized-debug");
        writePng(image, 320, 240);

        CaptureMediaReceiverResult result = cameraDerivedCvService().evaluate(
                CaptureMediaReceiverRequest.evaluateStillImages(List.of(image))
                        .withDebugOutputDirectory(debugOutput)
        );

        String metadata = Files.readString(debugOutput.resolve("candidate-0000.txt"));
        assertAll(
                () -> assertTrue(result.failed()),
                () -> assertTrue(metadata.contains("sampler.evidence.available=true")),
                () -> assertTrue(metadata.contains("sampler.evidence.backendId=reader-normalized-argb")),
                () -> assertTrue(metadata.contains("sampler.gridPhase.available=true")),
                () -> assertTrue(metadata.contains("sampler.evidence.metric.readerSamplingEvidenceConfidence="))
        );
    }

    @Test
    @DisplayName("Decoded tile progress without complete content does not publish restored files")
    void decodedTileProgressWithoutCompleteContentDoesNotPublishRestoredFiles() throws Exception {
        Path image = tempDir.resolve("decoded-incomplete.png");
        Path outputDirectory = tempDir.resolve("restore-decoded-incomplete");
        TilePayload payload = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                0L,
                0,
                PayloadKind.FILE_CHUNK,
                "decoded-but-incomplete"
        );
        CaptureMediaTestFrames.writeRenderedPng(image, payload);

        CaptureMediaReceiverResult result = service.restore(
                CaptureMediaReceiverRequest.restoreStillImages(List.of(image), outputDirectory)
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.INCOMPLETE, result.status()),
                () -> assertTrue(result.failed()),
                () -> assertFalse(result.restored()),
                () -> assertEquals(1, result.summary().acceptedCandidateCount()),
                () -> assertEquals(1, result.summary().recoveredUniqueFrameCount()),
                () -> assertTrue(result.summary().decodedTileCount() > 0),
                () -> assertEquals(0, result.summary().restoredFileCount()),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME
                                && diagnostic.blocking())),
                () -> assertTrue(result.restoreResult().isEmpty()),
                () -> assertFalse(Files.exists(outputDirectory))
        );
    }

    @Test
    @DisplayName("Source pixels stay available through post-normalization processing and release afterward")
    void sourcePixelsStayAvailableThroughPostNormalizationProcessingAndReleaseAfterward() throws Exception {
        Path image = tempDir.resolve("source-lifetime.png");
        writePng(image, 2, 2, SOURCE_PROBE_PIXEL);
        AtomicReference<MediaInputFrame> sourceAtBoundary = new AtomicReference<>();
        AtomicReference<NormalizedCaptureFrame> normalizedAtBoundary = new AtomicReference<>();
        AtomicBoolean sourcePixelsAccessible = new AtomicBoolean();
        AtomicBoolean normalizedPixelsAccessible = new AtomicBoolean();

        CaptureMediaReceiverService service = lifecycleProbeService(
                acceptedCvNormalizer(),
                new CaptureMediaReceiverService.SourcePixelLifecycleObserver() {
                    @Override
                    public void afterNormalization(RetainedMediaInputFrameBatch retainedSources) {
                        MediaInputFrame sourceFrame = retainedSources.retainedSourceFrames().get(0).sourceFrame();
                        NormalizedCaptureFrame normalizedFrame = retainedSources.normalizedFrames().get(0);
                        sourceAtBoundary.set(sourceFrame);
                        normalizedAtBoundary.set(normalizedFrame);
                        sourcePixelsAccessible.set(sourceFrame.argbPixelAt(0, 0) == SOURCE_PROBE_PIXEL);
                        normalizedPixelsAccessible.set(normalizedFrame.argbPixelAt(0, 0) == NORMALIZED_PROBE_PIXEL);
                    }
                }
        );

        CaptureMediaReceiverResult result =
                service.evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(image)));

        assertAll(
                () -> assertTrue(result.failed()),
                () -> assertNotNull(sourceAtBoundary.get()),
                () -> assertNotNull(normalizedAtBoundary.get()),
                () -> assertTrue(sourcePixelsAccessible.get()),
                () -> assertTrue(normalizedPixelsAccessible.get()),
                () -> assertThrows(IllegalStateException.class, () -> sourceAtBoundary.get().argbPixelAt(0, 0)),
                () -> assertThrows(IllegalStateException.class,
                        () -> normalizedAtBoundary.get().argbPixelAt(0, 0))
        );
    }

    @Test
    @DisplayName("Source pixels release after normalization rejection")
    void sourcePixelsReleaseAfterNormalizationRejection() throws Exception {
        Path image = tempDir.resolve("normalization-rejected.png");
        writePng(image, 2, 2, SOURCE_PROBE_PIXEL);
        AtomicReference<MediaInputFrame> sourceDuringNormalization = new AtomicReference<>();
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(frame -> {
            sourceDuringNormalization.set(frame);
            assertEquals(SOURCE_PROBE_PIXEL, frame.argbPixelAt(0, 0));
            return CvDetectionResult.rejected(
                    CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                    Map.of(),
                    "Test CV backend rejected the frame"
            );
        });

        CaptureMediaReceiverResult result = lifecycleProbeService(
                normalizer,
                CaptureMediaReceiverService.SourcePixelLifecycleObserver.noOp()
        ).evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(image)));

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertNotNull(sourceDuringNormalization.get()),
                () -> assertThrows(IllegalStateException.class,
                        () -> sourceDuringNormalization.get().argbPixelAt(0, 0))
        );
    }

    @Test
    @DisplayName("Source pixels release when normalization throws")
    void sourcePixelsReleaseWhenNormalizationThrows() throws Exception {
        Path image = tempDir.resolve("normalization-throws.png");
        writePng(image, 2, 2, SOURCE_PROBE_PIXEL);
        AtomicReference<MediaInputFrame> retainedSource = new AtomicReference<>();
        CaptureMediaFrameNormalizer throwingNormalizer = new CaptureMediaFrameNormalizer(
                new CaptureRenderedLayoutCatalog(List.of(new LayoutProfile(
                        "invalid-lifecycle-layout",
                        1,
                        1,
                        2,
                        2,
                        1,
                        1,
                        "unsupportedSeparator",
                        1,
                        1,
                        "black",
                        "preserveAspect"
                ))),
                new FixedLayoutPlanner()
        );

        CaptureMediaReceiverService service = lifecycleProbeService(
                throwingNormalizer,
                new CaptureMediaReceiverService.SourcePixelLifecycleObserver() {
                    @Override
                    public void sourceRetained(MediaInputFrame frame) {
                        retainedSource.set(frame);
                    }
                }
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(image))));
        assertAll(
                () -> assertNotNull(retainedSource.get()),
                () -> assertThrows(IllegalStateException.class, () -> retainedSource.get().argbPixelAt(0, 0))
        );
    }

    @Test
    @DisplayName("Source and normalized pixels release after debug export failure")
    void sourceAndNormalizedPixelsReleaseAfterDebugExportFailure() throws Exception {
        Path image = tempDir.resolve("debug-export-source.png");
        Path debugOutputFile = tempDir.resolve("debug-output-file");
        writePng(image, 2, 2, SOURCE_PROBE_PIXEL);
        Files.writeString(debugOutputFile, "not a directory");
        AtomicReference<MediaInputFrame> sourceAtBoundary = new AtomicReference<>();
        AtomicReference<NormalizedCaptureFrame> normalizedAtBoundary = new AtomicReference<>();

        CaptureMediaReceiverService service = lifecycleProbeService(
                acceptedCvNormalizer(),
                new CaptureMediaReceiverService.SourcePixelLifecycleObserver() {
                    @Override
                    public void afterNormalization(RetainedMediaInputFrameBatch retainedSources) {
                        sourceAtBoundary.set(retainedSources.retainedSourceFrames().get(0).sourceFrame());
                        normalizedAtBoundary.set(retainedSources.normalizedFrames().get(0));
                    }
                }
        );

        CaptureMediaReceiverResult result = service.evaluate(
                CaptureMediaReceiverRequest.evaluateStillImages(List.of(image))
                        .withDebugOutputDirectory(debugOutputFile)
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertTrue(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.DEBUG_EXPORT_FAILURE)),
                () -> assertNotNull(sourceAtBoundary.get()),
                () -> assertNotNull(normalizedAtBoundary.get()),
                () -> assertThrows(IllegalStateException.class, () -> sourceAtBoundary.get().argbPixelAt(0, 0)),
                () -> assertThrows(IllegalStateException.class,
                        () -> normalizedAtBoundary.get().argbPixelAt(0, 0))
        );
    }

    private void writePng(Path output, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", output.toFile());
    }

    private void writePng(Path output, int width, int height, int argb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                image.setRGB(col, row, argb);
            }
        }
        ImageIO.write(image, "png", output.toFile());
    }

    private void writeJpeg(Path output) throws Exception {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        if (!ImageIO.write(image, "jpeg", output.toFile())) {
            throw new IllegalStateException("No JPEG ImageIO writer is available");
        }
    }

    private CaptureMediaReceiverService cameraDerivedCvService() {
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(frame ->
                CvDetectionResult.acceptedNormalizedFrames(List.of(new CvNormalizedFrame(
                        DEBUG_LAYOUT,
                        new FrameCorners(50.0d, 60.0d, 1250.0d, 80.0d, 1240.0d, 700.0d, 40.0d, 680.0d),
                        CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                        cameraDerivedPixels()
                )))
        );
        return new CaptureMediaReceiverService(
                new CaptureMediaInputIntake(),
                normalizer,
                new CaptureMediaFrameDecoder(),
                new CaptureFrameSetAssembler(),
                new ReaderRestoreService()
        );
    }

    private int[] cameraDerivedPixels() {
        int[] pixels = new int[DEBUG_LAYOUT.frameWidthPx() * DEBUG_LAYOUT.frameHeightPx()];
        int cellWidth = Math.max(8, DEBUG_LAYOUT.tileGapPx());
        int top = DEBUG_LAYOUT.outerMarginPx();
        int bottomExclusive = top + DEBUG_LAYOUT.topSyncBandPx();
        int left = DEBUG_LAYOUT.outerMarginPx();
        int rightExclusive = DEBUG_LAYOUT.frameWidthPx() - DEBUG_LAYOUT.outerMarginPx();
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = left; col < rightExclusive; col++) {
                int segment = (col - left) / cellWidth;
                pixels[(row * DEBUG_LAYOUT.frameWidthPx()) + col] =
                        segment % 2 == 0 ? 0xFFFFFFFF : 0xFF000000;
            }
        }
        return pixels;
    }

    private CaptureMediaReceiverService imageIoOnlyService() {
        return new CaptureMediaReceiverService(
                new CaptureMediaInputIntake(
                        new ImageIoCaptureMediaStillImageDecoder(),
                        CaptureMediaVideoFrameSourceAdapter.unsupported(),
                        CaptureMediaVideoLimits.conservativeDefaults()
                ),
                new CaptureMediaFrameNormalizer(),
                new CaptureMediaFrameDecoder(),
                new CaptureFrameSetAssembler(),
                new ReaderRestoreService()
        );
    }

    private CaptureMediaReceiverService lifecycleProbeService(
            CaptureMediaFrameNormalizer normalizer,
            CaptureMediaReceiverService.SourcePixelLifecycleObserver sourcePixelLifecycleObserver
    ) {
        return new CaptureMediaReceiverService(
                new CaptureMediaInputIntake(
                        new ImageIoCaptureMediaStillImageDecoder(),
                        CaptureMediaVideoFrameSourceAdapter.unsupported(),
                        CaptureMediaVideoLimits.conservativeDefaults()
                ),
                normalizer,
                new CaptureMediaFrameDecoder(),
                new CaptureFrameSetAssembler(),
                new ReaderRestoreService(),
                new CaptureMediaCandidateDebugExporter(),
                sourcePixelLifecycleObserver
        );
    }

    private CaptureMediaFrameNormalizer acceptedCvNormalizer() {
        return new CaptureMediaFrameNormalizer(frame -> CvDetectionResult.acceptedNormalizedFrames(List.of(
                new CvNormalizedFrame(
                        DEBUG_LAYOUT,
                        FrameCorners.exactFrame(DEBUG_LAYOUT.frameWidthPx(), DEBUG_LAYOUT.frameHeightPx()),
                        CaptureMediaQualityMetrics.perspectiveCorrected(0.50d, 0.05d),
                        filledNormalizedPixels(),
                        Optional.empty(),
                        Optional.of("test-lifecycle"),
                        1,
                        1,
                        1
                )
        )));
    }

    private int[] filledNormalizedPixels() {
        int[] pixels = new int[DEBUG_LAYOUT.frameWidthPx() * DEBUG_LAYOUT.frameHeightPx()];
        Arrays.fill(pixels, NORMALIZED_PROBE_PIXEL);
        return pixels;
    }
}
