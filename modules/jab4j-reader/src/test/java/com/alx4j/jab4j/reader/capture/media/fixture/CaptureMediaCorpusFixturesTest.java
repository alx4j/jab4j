package com.alx4j.jab4j.reader.capture.media.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverResult;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverService;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverStatus;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;

@DisplayName("Capture media corpus fixtures")
class CaptureMediaCorpusFixturesTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Generated exact PNG and uncropped fixtures create readable media at test time")
    void generatedExactPngAndUncroppedFixturesCreateReadableMediaAtTestTime() throws Exception {
        GeneratedCaptureMediaFixture exact = CaptureMediaCorpusFixtures.generatedExactPng(tempDir);
        GeneratedCaptureMediaFixture uncropped = CaptureMediaCorpusFixtures.generatedUncroppedInset(tempDir);

        BufferedImage exactImage = ImageIO.read(exact.mediaFiles().get(0).toFile());
        BufferedImage uncroppedImage = ImageIO.read(uncropped.mediaFiles().get(0).toFile());

        assertAll(
                () -> assertEquals(CaptureMediaCorpusFixtures.GENERATED_EXACT_PNG, exact.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.GENERATED_UNCROPPED_INSET, uncropped.scenarioId()),
                () -> assertTrue(Files.isDirectory(exact.mediaDirectory())),
                () -> assertTrue(Files.isDirectory(uncropped.mediaDirectory())),
                () -> assertTrue(exact.mediaFiles().stream().allMatch(path -> path.getFileName().toString().endsWith(".png"))),
                () -> assertTrue(uncropped.mediaFiles().stream().allMatch(path -> path.getFileName().toString().endsWith(".png"))),
                () -> assertNotNull(exactImage),
                () -> assertNotNull(uncroppedImage),
                () -> assertEquals(exact.expectedUniqueFrameCount(), exact.mediaFiles().size()),
                () -> assertEquals(uncropped.expectedUniqueFrameCount(), uncropped.mediaFiles().size()),
                () -> assertTrue(uncroppedImage.getWidth() > exactImage.getWidth()),
                () -> assertTrue(uncroppedImage.getHeight() > exactImage.getHeight())
        );
    }

    @Test
    @DisplayName("Generated camera-like PNG and JPEG monitor fixtures create detectable candidates")
    void generatedCameraLikePngAndJpegMonitorFixturesCreateDetectableCandidates() throws Exception {
        GeneratedCaptureMediaFixture png = CaptureMediaCorpusFixtures.generatedCameraLikeMonitorPng(tempDir);
        GeneratedCaptureMediaFixture jpeg = CaptureMediaCorpusFixtures.generatedCameraLikeMonitorJpeg(tempDir);
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer();

        MediaNormalizationResult normalizedPng = normalizer.normalize(mediaInputFrame(png.mediaFiles().get(0), "png"));
        MediaNormalizationResult normalizedJpeg = normalizer.normalize(mediaInputFrame(jpeg.mediaFiles().get(0), "jpeg"));

        assertAll(
                () -> assertEquals(CaptureMediaCorpusFixtures.GENERATED_CAMERA_LIKE_MONITOR_PNG, png.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.GENERATED_CAMERA_LIKE_MONITOR_JPEG, jpeg.scenarioId()),
                () -> assertTrue(png.mediaFiles().stream()
                        .allMatch(path -> path.getFileName().toString().endsWith(".png"))),
                () -> assertTrue(jpeg.mediaFiles().stream()
                        .allMatch(path -> path.getFileName().toString().endsWith(".jpeg"))),
                () -> assertNotNull(ImageIO.read(png.mediaFiles().get(0).toFile())),
                () -> assertNotNull(ImageIO.read(jpeg.mediaFiles().get(0).toFile())),
                () -> assertTrue(normalizedPng.accepted(), () -> normalizedPng.diagnostics().toString()),
                () -> assertTrue(normalizedJpeg.accepted(), () -> normalizedJpeg.diagnostics().toString()),
                () -> assertEquals("debug-low-density", normalizedPng.frame().orElseThrow().layoutProfileId()),
                () -> assertEquals("debug-low-density", normalizedJpeg.frame().orElseThrow().layoutProfileId())
        );
    }

    @Test
    @DisplayName("Generated video-frame scenarios model duplicates and missing unique frames")
    void generatedVideoFrameScenariosModelDuplicatesAndMissingUniqueFrames() throws Exception {
        GeneratedCaptureMediaFixture duplicate = CaptureMediaCorpusFixtures.duplicateFrames(tempDir);
        GeneratedCaptureMediaFixture missing = CaptureMediaCorpusFixtures.missingUniqueFrames(tempDir);
        GeneratedCaptureMediaFixture extracted = CaptureMediaCorpusFixtures.extractedVideoFrames(tempDir);
        GeneratedCaptureMediaFixture qualityMix = CaptureMediaCorpusFixtures.extractedVideoFramesWithQualityMix(tempDir);

        assertAll(
                () -> assertEquals(CaptureMediaCorpusFixtures.DUPLICATE_FRAMES, duplicate.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.MISSING_UNIQUE_FRAMES, missing.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.EXTRACTED_VIDEO_FRAMES, extracted.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.EXTRACTED_VIDEO_QUALITY_MIX, qualityMix.scenarioId()),
                () -> assertEquals(duplicate.expectedUniqueFrameCount() + 1, duplicate.mediaFiles().size()),
                () -> assertEquals(missing.expectedUniqueFrameCount() - 1, missing.mediaFiles().size()),
                () -> assertEquals(extracted.expectedUniqueFrameCount(), extracted.mediaFiles().size()),
                () -> assertEquals(qualityMix.expectedUniqueFrameCount() + 3, qualityMix.mediaFiles().size()),
                () -> assertArrayEquals(
                        Files.readAllBytes(duplicate.mediaFiles().get(0)),
                        Files.readAllBytes(duplicate.mediaFiles().get(duplicate.mediaFiles().size() - 1))
                ),
                () -> assertTrue(extracted.mediaFiles().stream()
                        .allMatch(path -> path.getFileName().toString().startsWith("extracted-video-frame-"))),
                () -> assertTrue(qualityMix.mediaFiles().stream()
                        .anyMatch(path -> path.getFileName().toString().contains("compression-shift"))),
                () -> assertTrue(qualityMix.mediaFiles().stream()
                        .anyMatch(path -> path.getFileName().toString().contains("overexposed"))),
                () -> assertTrue(qualityMix.mediaFiles().stream()
                        .anyMatch(path -> path.getFileName().toString().contains("blurred"))),
                () -> assertNotNull(ImageIO.read(extracted.mediaFiles().get(0).toFile()))
        );
    }

    @Test
    @DisplayName("Generated exact PNG fixture evaluates through the media receiver facade")
    void generatedExactPngFixtureEvaluatesThroughMediaReceiverFacade() throws Exception {
        GeneratedCaptureMediaFixture exact = CaptureMediaCorpusFixtures.generatedExactPng(tempDir);

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().evaluate(
                CaptureMediaReceiverRequest.evaluateExtractedFrameFolders(List.of(exact.mediaDirectory()))
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.ELIGIBLE, result.status()),
                () -> assertEquals(exact.mediaFiles().size(), result.summary().submittedMediaCount()),
                () -> assertEquals(exact.mediaFiles().size(), result.summary().readableMediaCount()),
                () -> assertEquals(exact.expectedUniqueFrameCount(), result.summary().acceptedCandidateCount()),
                () -> assertEquals(exact.expectedUniqueFrameCount(), result.summary().recoveredUniqueFrameCount()),
                () -> assertTrue(result.summary().decodedTileCount() > 0),
                () -> assertTrue(result.eligibleForRestore())
        );
    }

    @Test
    @DisplayName("Generated uncropped inset fixture evaluates eligible and restores through media receiver")
    void generatedUncroppedInsetFixtureEvaluatesEligibleAndRestoresThroughMediaReceiver() throws Exception {
        GeneratedCaptureMediaFixture uncropped = CaptureMediaCorpusFixtures.generatedUncroppedInset(tempDir);
        CaptureMediaReceiverService service = new CaptureMediaReceiverService();
        Path outputDirectory = tempDir.resolve("restore-uncropped");

        CaptureMediaReceiverResult evaluated = service.evaluate(
                CaptureMediaReceiverRequest.evaluateStillImages(List.of(uncropped.mediaDirectory()))
        );
        CaptureMediaReceiverResult restored = service.restore(
                CaptureMediaReceiverRequest.restoreStillImages(List.of(uncropped.mediaDirectory()), outputDirectory)
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.ELIGIBLE, evaluated.status()),
                () -> assertTrue(evaluated.eligibleForRestore()),
                () -> assertEquals(uncropped.mediaFiles().size(), evaluated.summary().submittedMediaCount()),
                () -> assertEquals(uncropped.mediaFiles().size(), evaluated.summary().readableMediaCount()),
                () -> assertEquals(uncropped.expectedUniqueFrameCount(), evaluated.summary().acceptedCandidateCount()),
                () -> assertEquals(uncropped.expectedUniqueFrameCount(), evaluated.summary().recoveredUniqueFrameCount()),
                () -> assertTrue(evaluated.summary().decodedTileCount() > 0),
                () -> assertEquals(CaptureMediaReceiverStatus.RESTORED, restored.status()),
                () -> assertTrue(restored.restored()),
                () -> assertEquals(2, restored.summary().restoredFileCount()),
                () -> assertArrayEquals(
                        Files.readAllBytes(uncropped.scenarioDirectory().resolve("source").resolve("docs").resolve("message.txt")),
                        Files.readAllBytes(outputDirectory.resolve("payload").resolve("docs").resolve("message.txt"))
                ),
                () -> assertEquals(0, Files.size(outputDirectory.resolve("payload").resolve("empty.bin")))
        );
    }

    @Test
    @DisplayName("Generated duplicate extracted frames evaluate eligible with duplicate diagnostics")
    void generatedDuplicateExtractedFramesEvaluateEligibleWithDuplicateDiagnostics() throws Exception {
        GeneratedCaptureMediaFixture duplicate = CaptureMediaCorpusFixtures.duplicateFrames(tempDir);

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().evaluate(
                CaptureMediaReceiverRequest.evaluateExtractedFrameFolders(List.of(duplicate.mediaDirectory()))
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.ELIGIBLE, result.status()),
                () -> assertTrue(result.eligibleForRestore()),
                () -> assertEquals(duplicate.mediaFiles().size(), result.summary().submittedMediaCount()),
                () -> assertEquals(duplicate.mediaFiles().size(), result.summary().readableMediaCount()),
                () -> assertEquals(duplicate.expectedUniqueFrameCount(), result.summary().recoveredUniqueFrameCount()),
                () -> assertEquals(1, result.summary().duplicateMediaFrameCount()),
                () -> assertEquals(
                        result.summary().recoveredUniqueFrameCount() + result.summary().duplicateMediaFrameCount(),
                        result.summary().acceptedCandidateCount()
                ),
                () -> assertTrue(result.summary().decodedTileCount() > 0),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.DUPLICATE_MEDIA_FRAME
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING
                                && !diagnostic.blocking()))
        );
    }

    @Test
    @DisplayName("Generated extracted video quality mix uses recoverable frames and reports rejected sources")
    void generatedExtractedVideoQualityMixUsesRecoverableFramesAndReportsRejectedSources() throws Exception {
        GeneratedCaptureMediaFixture fixture = CaptureMediaCorpusFixtures.extractedVideoFramesWithQualityMix(tempDir);

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().evaluate(
                CaptureMediaReceiverRequest.evaluateExtractedFrameFolders(List.of(fixture.mediaDirectory()))
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.ELIGIBLE, result.status()),
                () -> assertTrue(result.eligibleForRestore()),
                () -> assertEquals(fixture.mediaFiles().size(), result.summary().submittedMediaCount()),
                () -> assertEquals(fixture.mediaFiles().size(), result.summary().readableMediaCount()),
                () -> assertEquals(fixture.expectedUniqueFrameCount(), result.summary().recoveredUniqueFrameCount()),
                () -> assertEquals(1, result.summary().duplicateMediaFrameCount()),
                () -> assertEquals(
                        result.summary().recoveredUniqueFrameCount() + result.summary().duplicateMediaFrameCount(),
                        result.summary().acceptedCandidateCount()
                ),
                () -> assertTrue(result.summary().rejectedCandidateCount() >= 1),
                () -> assertTrue(result.summary().decodedTileCount() > 0),
                () -> assertTrue(result.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING
                                && !diagnostic.blocking())),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.GLARE_OR_OVEREXPOSURE
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING
                                && !diagnostic.blocking()
                                && diagnostic.sourceId().orElseThrow().contains("overexposed"))),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.BLUR
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING
                                && !diagnostic.blocking()
                                && diagnostic.sourceId().orElseThrow().contains("blurred")))
        );
    }

    @Test
    @DisplayName("Generated missing media frames are incomplete and do not publish restore output")
    void generatedMissingMediaFramesAreIncompleteAndDoNotPublishRestoreOutput() throws Exception {
        GeneratedCaptureMediaFixture missing = CaptureMediaCorpusFixtures.missingUniqueFrames(tempDir);
        Path outputDirectory = tempDir.resolve("restore-missing-media");

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().restore(
                CaptureMediaReceiverRequest.restoreExtractedFrameFolders(List.of(missing.mediaDirectory()), outputDirectory)
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.INCOMPLETE, result.status()),
                () -> assertTrue(result.failed()),
                () -> assertFalse(result.restored()),
                () -> assertEquals(0, result.summary().restoredFileCount()),
                () -> assertEquals(missing.mediaFiles().size(), result.summary().submittedMediaCount()),
                () -> assertEquals(missing.mediaFiles().size(), result.summary().readableMediaCount()),
                () -> assertEquals(missing.expectedUniqueFrameCount() - 1, result.summary().acceptedCandidateCount()),
                () -> assertEquals(missing.expectedUniqueFrameCount() - 1, result.summary().recoveredUniqueFrameCount()),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME
                                && diagnostic.blocking())),
                () -> assertTrue(result.restoreResult().isEmpty()),
                () -> assertFalse(Files.exists(outputDirectory.resolve("payload")))
        );
    }

    @Test
    @DisplayName("Generated quality-mix extracted frames stay eligible when enough unique frames remain")
    void generatedQualityMixExtractedFramesStayEligibleWhenEnoughUniqueFramesRemain() throws Exception {
        GeneratedCaptureMediaFixture qualityMix = CaptureMediaCorpusFixtures.extractedVideoFramesWithQualityMix(tempDir);

        CaptureMediaReceiverResult result = new CaptureMediaReceiverService().evaluate(
                CaptureMediaReceiverRequest.evaluateExtractedFrameFolders(List.of(qualityMix.mediaDirectory()))
        );

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.ELIGIBLE, result.status()),
                () -> assertTrue(result.eligibleForRestore()),
                () -> assertEquals(qualityMix.mediaFiles().size(), result.summary().submittedMediaCount()),
                () -> assertEquals(qualityMix.mediaFiles().size(), result.summary().readableMediaCount()),
                () -> assertEquals(qualityMix.expectedUniqueFrameCount(), result.summary().recoveredUniqueFrameCount()),
                () -> assertTrue(result.summary().duplicateMediaFrameCount() >= 1),
                () -> assertEquals(
                        result.summary().recoveredUniqueFrameCount() + result.summary().duplicateMediaFrameCount(),
                        result.summary().acceptedCandidateCount()
                ),
                () -> assertTrue(result.summary().rejectedCandidateCount() >= 2),
                () -> assertTrue(result.summary().decodedTileCount() > 0),
                () -> assertTrue(result.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.DUPLICATE_MEDIA_FRAME
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING)),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING)),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.GLARE_OR_OVEREXPOSURE
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING)),
                () -> assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code() == CaptureMediaDiagnosticCode.BLUR
                                && diagnostic.severity() == CaptureMediaDiagnosticSeverity.WARNING))
        );
    }

    @Test
    @DisplayName("Generated diagnostics-only scenarios create small placeholders")
    void generatedDiagnosticsOnlyScenariosCreateSmallPlaceholders() throws Exception {
        GeneratedCaptureMediaFixture noJabFrame = CaptureMediaCorpusFixtures.noJabFrame(tempDir);
        GeneratedCaptureMediaFixture heic = CaptureMediaCorpusFixtures.unsupportedHeicPlaceholder(tempDir);
        GeneratedCaptureMediaFixture corrupted = CaptureMediaCorpusFixtures.corruptedUnreadableImage(tempDir);
        GeneratedCaptureMediaFixture video = CaptureMediaCorpusFixtures.futureDirectVideoPlaceholders(tempDir);
        PlannedCaptureMediaScenario external = CaptureMediaCorpusFixtures.externalIphoneStillsScenario();

        assertAll(
                () -> assertEquals(CaptureMediaCorpusFixtures.NO_JAB_FRAME, noJabFrame.scenarioId()),
                () -> assertNotNull(ImageIO.read(noJabFrame.mediaFiles().get(0).toFile())),
                () -> assertEquals(0, noJabFrame.expectedUniqueFrameCount()),
                () -> assertEquals(CaptureMediaCorpusFixtures.UNSUPPORTED_HEIC_PLACEHOLDER, heic.scenarioId()),
                () -> assertTrue(heic.mediaFiles().get(0).getFileName().toString().endsWith(".heic")),
                () -> assertTrue(Files.readString(heic.mediaFiles().get(0), StandardCharsets.UTF_8).contains("unsupported HEIC")),
                () -> assertEquals(CaptureMediaCorpusFixtures.CORRUPTED_UNREADABLE_IMAGE, corrupted.scenarioId()),
                () -> assertTrue(corrupted.mediaFiles().get(0).getFileName().toString().endsWith(".png")),
                () -> assertNull(ImageIO.read(corrupted.mediaFiles().get(0).toFile())),
                () -> assertEquals(CaptureMediaCorpusFixtures.FUTURE_DIRECT_VIDEO, video.scenarioId()),
                () -> assertEquals(2, video.mediaFiles().size()),
                () -> assertTrue(video.mediaFiles().stream().anyMatch(path -> path.getFileName().toString().endsWith(".mov"))),
                () -> assertTrue(video.mediaFiles().stream().anyMatch(path -> path.getFileName().toString().endsWith(".mp4"))),
                () -> assertEquals(CaptureMediaCorpusFixtures.EXTERNAL_IPHONE_STILLS, external.scenarioId()),
                () -> assertEquals("external_private", external.assetAvailability())
        );
    }

    @Test
    @DisplayName("Generated false-positive fixtures remain rejected by media normalization")
    void generatedFalsePositiveFixturesRemainRejectedByMediaNormalization() throws Exception {
        GeneratedCaptureMediaFixture brightMonitor = CaptureMediaCorpusFixtures.brightMonitorWithoutJab(tempDir);
        GeneratedCaptureMediaFixture uiChrome = CaptureMediaCorpusFixtures.uiChromeWithoutJab(tempDir);
        GeneratedCaptureMediaFixture stripes = CaptureMediaCorpusFixtures.repeatedStripesWithoutJab(tempDir);
        GeneratedCaptureMediaFixture partial = CaptureMediaCorpusFixtures.partialCroppedFrame(tempDir);
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer();

        MediaNormalizationResult brightResult =
                normalizer.normalize(mediaInputFrame(brightMonitor.mediaFiles().get(0), "png"));
        MediaNormalizationResult uiResult =
                normalizer.normalize(mediaInputFrame(uiChrome.mediaFiles().get(0), "png"));
        MediaNormalizationResult stripesResult =
                normalizer.normalize(mediaInputFrame(stripes.mediaFiles().get(0), "png"));
        MediaNormalizationResult partialResult =
                normalizer.normalize(mediaInputFrame(partial.mediaFiles().get(0), "png"));

        assertAll(
                () -> assertEquals(CaptureMediaCorpusFixtures.BRIGHT_MONITOR_WITHOUT_JAB,
                        brightMonitor.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.UI_CHROME_WITHOUT_JAB, uiChrome.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.REPEATED_STRIPES_WITHOUT_JAB, stripes.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.PARTIAL_CROPPED_FRAME, partial.scenarioId()),
                () -> assertNotNull(ImageIO.read(brightMonitor.mediaFiles().get(0).toFile())),
                () -> assertNotNull(ImageIO.read(uiChrome.mediaFiles().get(0).toFile())),
                () -> assertNotNull(ImageIO.read(stripes.mediaFiles().get(0).toFile())),
                () -> assertNotNull(ImageIO.read(partial.mediaFiles().get(0).toFile())),
                () -> assertFalse(brightResult.accepted()),
                () -> assertFalse(uiResult.accepted()),
                () -> assertFalse(stripesResult.accepted()),
                () -> assertFalse(partialResult.accepted()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        brightResult.diagnostics().get(0).code()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        uiResult.diagnostics().get(0).code()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        stripesResult.diagnostics().get(0).code()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        partialResult.diagnostics().get(0).code())
        );
    }

    private MediaInputFrame mediaInputFrame(Path source, String formatName) throws Exception {
        BufferedImage image = ImageIO.read(source.toFile());
        int width = image.getWidth();
        int height = image.getHeight();
        return new MediaInputFrame(
                source.toString(),
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                width,
                height,
                formatName,
                "source-hash",
                image.getRGB(0, 0, width, height, null, 0, width)
        );
    }
}
