package com.alx4j.jab4j.reader.capture.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Capture receiver corpus fixtures")
class CaptureReceiverCorpusFixturesTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Clean capture fixture exports temporary PNG frames without writer metadata")
    void cleanCaptureFixtureExportsTemporaryPngFramesWithoutWriterMetadata() throws Exception {
        GeneratedCaptureFixture fixture = CaptureReceiverCorpusFixtures.cleanExtractedPng(tempDir);

        assertAll(
                () -> assertEquals(CaptureReceiverCorpusFixtures.CLEAN_EXTRACTED_PNG, fixture.scenarioId()),
                () -> assertTrue(Files.isDirectory(fixture.sourceRoot())),
                () -> assertTrue(Files.exists(fixture.writerImageSequenceDirectory().resolve("frame-sequence.txt"))),
                () -> assertTrue(Files.isDirectory(fixture.captureFramesDirectory())),
                () -> assertFalse(Files.exists(fixture.captureFramesDirectory().resolve("frame-sequence.txt"))),
                () -> assertEquals(fixture.sessionPlan().frameDescriptors().size(), fixture.captureFrameFiles().size()),
                () -> assertEquals(fixture.sessionPlan().frameDescriptors().size(), fixture.renderedFrames().size()),
                () -> assertFalse(fixture.sessionPlan().finalSessionDigest().isBlank()),
                () -> assertTrue(fixture.captureFrameFiles().stream()
                        .allMatch(path -> path.getFileName().toString().startsWith("capture-frame-"))),
                () -> assertNotNull(ImageIO.read(fixture.captureFrameFiles().get(0).toFile()))
        );
    }

    @Test
    @DisplayName("Negative fixture folders model planned capture corpus inputs")
    void negativeFixtureFoldersModelPlannedCaptureCorpusInputs() throws Exception {
        GeneratedCaptureFixture clean = CaptureReceiverCorpusFixtures.cleanExtractedPng(tempDir);
        GeneratedCaptureFixture missing = CaptureReceiverCorpusFixtures.missingFrameContent(tempDir);
        GeneratedCaptureFixture duplicate = CaptureReceiverCorpusFixtures.duplicateEquivalentFrame(tempDir);
        GeneratedCaptureFixture corrupted = CaptureReceiverCorpusFixtures.corruptedTileContent(tempDir);
        GeneratedImageFolder unrelated = CaptureReceiverCorpusFixtures.unrelatedImage(tempDir);
        GeneratedImageFolder unreadable = CaptureReceiverCorpusFixtures.unreadableImage(tempDir);
        GeneratedImageFolder unsupported = CaptureReceiverCorpusFixtures.unsupportedDimensions(tempDir);

        BufferedImage unsupportedImage = ImageIO.read(unsupported.captureFrameFiles().get(0).toFile());

        assertAll(
                () -> assertEquals(clean.captureFrameFiles().size() - 1, missing.captureFrameFiles().size()),
                () -> assertEquals(clean.captureFrameFiles().size() + 1, duplicate.captureFrameFiles().size()),
                () -> assertArrayEquals(
                        Files.readAllBytes(duplicate.captureFrameFiles().get(0)),
                        Files.readAllBytes(duplicate.captureFrameFiles().get(duplicate.captureFrameFiles().size() - 1))
                ),
                () -> assertEquals(clean.captureFrameFiles().size(), corrupted.captureFrameFiles().size()),
                () -> assertTrue(hasDifferentFrameBytes(clean, corrupted)),
                () -> assertNotNull(ImageIO.read(unrelated.captureFrameFiles().get(0).toFile())),
                () -> assertNull(ImageIO.read(unreadable.captureFrameFiles().get(0).toFile())),
                () -> assertEquals(321, unsupportedImage.getWidth()),
                () -> assertEquals(241, unsupportedImage.getHeight())
        );
    }

    @Test
    @DisplayName("Corpus manifest lists all planned fixture scenario ids")
    void corpusManifestListsAllPlannedFixtureScenarioIds() throws Exception {
        URL manifestResource = getClass().getClassLoader().getResource("capture-receiver-corpus/manifest.tsv");
        assertNotNull(manifestResource);
        Path manifest = Path.of(manifestResource.toURI());

        Set<String> scenarioIds = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .skip(1)
                .map(line -> line.split("\t", -1)[0])
                .collect(Collectors.toSet());

        List<String> expectedScenarioIds = List.of(
                CaptureReceiverCorpusFixtures.EXACT_PNG_BASELINE,
                CaptureReceiverCorpusFixtures.CLEAN_EXTRACTED_PNG,
                CaptureReceiverCorpusFixtures.MISSING_FRAME_CONTENT,
                CaptureReceiverCorpusFixtures.DUPLICATE_EQUIVALENT_FRAME,
                CaptureReceiverCorpusFixtures.UNRELATED_IMAGE,
                CaptureReceiverCorpusFixtures.UNREADABLE_IMAGE,
                CaptureReceiverCorpusFixtures.UNSUPPORTED_DIMENSIONS,
                CaptureReceiverCorpusFixtures.CORRUPTED_TILE_CONTENT
        );

        assertTrue(scenarioIds.containsAll(expectedScenarioIds));
    }

    private boolean hasDifferentFrameBytes(GeneratedCaptureFixture left, GeneratedCaptureFixture right) throws Exception {
        int sharedCount = Math.min(left.captureFrameFiles().size(), right.captureFrameFiles().size());
        for (int index = 0; index < sharedCount; index++) {
            if (Files.mismatch(left.captureFrameFiles().get(index), right.captureFrameFiles().get(index)) != -1L) {
                return true;
            }
        }
        return false;
    }
}
