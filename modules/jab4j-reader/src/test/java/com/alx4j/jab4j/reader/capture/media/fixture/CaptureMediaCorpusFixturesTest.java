package com.alx4j.jab4j.reader.capture.media.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverResult;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverService;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverStatus;

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
    @DisplayName("Generated video-frame scenarios model duplicates and missing unique frames")
    void generatedVideoFrameScenariosModelDuplicatesAndMissingUniqueFrames() throws Exception {
        GeneratedCaptureMediaFixture duplicate = CaptureMediaCorpusFixtures.duplicateFrames(tempDir);
        GeneratedCaptureMediaFixture missing = CaptureMediaCorpusFixtures.missingUniqueFrames(tempDir);
        GeneratedCaptureMediaFixture extracted = CaptureMediaCorpusFixtures.extractedVideoFrames(tempDir);

        assertAll(
                () -> assertEquals(CaptureMediaCorpusFixtures.DUPLICATE_FRAMES, duplicate.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.MISSING_UNIQUE_FRAMES, missing.scenarioId()),
                () -> assertEquals(CaptureMediaCorpusFixtures.EXTRACTED_VIDEO_FRAMES, extracted.scenarioId()),
                () -> assertEquals(duplicate.expectedUniqueFrameCount() + 1, duplicate.mediaFiles().size()),
                () -> assertEquals(missing.expectedUniqueFrameCount() - 1, missing.mediaFiles().size()),
                () -> assertEquals(extracted.expectedUniqueFrameCount(), extracted.mediaFiles().size()),
                () -> assertArrayEquals(
                        Files.readAllBytes(duplicate.mediaFiles().get(0)),
                        Files.readAllBytes(duplicate.mediaFiles().get(duplicate.mediaFiles().size() - 1))
                ),
                () -> assertTrue(extracted.mediaFiles().stream()
                        .allMatch(path -> path.getFileName().toString().startsWith("extracted-video-frame-"))),
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
}
