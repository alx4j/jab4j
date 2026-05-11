package com.alx4j.jab4j.reader.capture.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.CaptureReceiverRequest;
import com.alx4j.jab4j.reader.capture.CaptureReceiverResult;
import com.alx4j.jab4j.reader.capture.CaptureReceiverService;
import com.alx4j.jab4j.reader.capture.CaptureReceiverStatus;

@DisplayName("Capture receiver service corpus behavior")
class CaptureReceiverServiceCorpusTest {

    private final CaptureReceiverService service = new CaptureReceiverService();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Clean extracted PNG frames evaluate eligible and restore without frame-sequence metadata")
    void cleanExtractedPngFramesEvaluateEligibleAndRestoreWithoutFrameSequenceMetadata() throws Exception {
        GeneratedCaptureFixture fixture = CaptureReceiverCorpusFixtures.cleanExtractedPng(tempDir);
        CaptureReceiverResult evaluated = service.evaluate(
                CaptureReceiverRequest.evaluateOnly(List.of(fixture.captureFramesDirectory()))
        );
        Path outputDirectory = tempDir.resolve("restore-clean");
        CaptureReceiverResult restored = service.receive(
                CaptureReceiverRequest.restore(List.of(fixture.captureFramesDirectory()), outputDirectory)
        );

        assertAll(
                () -> assertFalse(Files.exists(fixture.captureFramesDirectory().resolve("frame-sequence.txt"))),
                () -> assertEquals(CaptureReceiverStatus.ELIGIBLE, evaluated.status()),
                () -> assertTrue(evaluated.eligibleForRestore()),
                () -> assertEquals(fixture.sessionPlan().frameDescriptors().size(), evaluated.summary().acceptedCandidateCount()),
                () -> assertTrue(evaluated.summary().decodedTileCount() > 0),
                () -> assertEquals(CaptureReceiverStatus.RESTORED, restored.status()),
                () -> assertTrue(restored.restored()),
                () -> assertEquals(2, restored.summary().restoredFileCount()),
                () -> assertArrayEquals(
                        Files.readAllBytes(fixture.sourceRoot().resolve("docs").resolve("message.txt")),
                        Files.readAllBytes(outputDirectory.resolve("payload").resolve("docs").resolve("message.txt"))
                ),
                () -> assertEquals(0, Files.size(outputDirectory.resolve("payload").resolve("empty.bin")))
        );
    }

    @Test
    @DisplayName("Missing capture frame content is incomplete and does not publish restored output")
    void missingCaptureFrameContentIsIncompleteAndDoesNotPublishRestoredOutput() throws Exception {
        GeneratedCaptureFixture fixture = CaptureReceiverCorpusFixtures.missingFrameContent(tempDir);
        Path outputDirectory = tempDir.resolve("restore-missing");

        CaptureReceiverResult result = service.receive(
                CaptureReceiverRequest.restore(List.of(fixture.captureFramesDirectory()), outputDirectory)
        );

        assertAll(
                () -> assertEquals(CaptureReceiverStatus.INCOMPLETE, result.status()),
                () -> assertDiagnostic(result, CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT),
                () -> assertEquals(0, result.summary().restoredFileCount()),
                () -> assertFalse(Files.exists(outputDirectory.resolve("payload")))
        );
    }

    @Test
    @DisplayName("Equivalent duplicate capture frames are accepted with a duplicate diagnostic")
    void equivalentDuplicateCaptureFramesAreAcceptedWithDuplicateDiagnostic() throws Exception {
        GeneratedCaptureFixture fixture = CaptureReceiverCorpusFixtures.duplicateEquivalentFrame(tempDir);

        CaptureReceiverResult result = service.evaluate(
                CaptureReceiverRequest.evaluateOnly(List.of(fixture.captureFramesDirectory()))
        );

        assertAll(
                () -> assertEquals(CaptureReceiverStatus.ELIGIBLE, result.status()),
                () -> assertDiagnostic(result, CaptureDiagnosticCode.DUPLICATE_EQUIVALENT_FRAME),
                () -> assertEquals(1, result.summary().duplicateFrameCount()),
                () -> assertEquals(fixture.sessionPlan().frameDescriptors().size(), result.summary().acceptedCandidateCount())
        );
    }

    @Test
    @DisplayName("Unreadable, unrelated, and unsupported image inputs are rejected with stable diagnostics")
    void unreadableUnrelatedAndUnsupportedInputsAreRejectedWithStableDiagnostics() throws Exception {
        GeneratedImageFolder unreadable = CaptureReceiverCorpusFixtures.unreadableImage(tempDir);
        GeneratedImageFolder unrelated = CaptureReceiverCorpusFixtures.unrelatedImage(tempDir);
        GeneratedImageFolder unsupported = CaptureReceiverCorpusFixtures.unsupportedDimensions(tempDir);

        CaptureReceiverResult unreadableResult = evaluate(unreadable);
        CaptureReceiverResult unrelatedResult = evaluate(unrelated);
        CaptureReceiverResult unsupportedResult = evaluate(unsupported);

        assertAll(
                () -> assertEquals(CaptureReceiverStatus.REJECTED, unreadableResult.status()),
                () -> assertDiagnostic(unreadableResult, CaptureDiagnosticCode.UNREADABLE_IMAGE),
                () -> assertEquals(CaptureReceiverStatus.REJECTED, unrelatedResult.status()),
                () -> assertDiagnostic(unrelatedResult, CaptureDiagnosticCode.NO_CANDIDATE_BARCODE_CONTENT),
                () -> assertEquals(CaptureReceiverStatus.REJECTED, unsupportedResult.status()),
                () -> assertDiagnostic(unsupportedResult, CaptureDiagnosticCode.UNSUPPORTED_DIMENSIONS)
        );
    }

    @Test
    @DisplayName("Non-PNG capture files are rejected before image decode")
    void nonPngCaptureFilesAreRejectedBeforeImageDecode() throws Exception {
        Path captureDirectory = Files.createDirectories(tempDir.resolve("unsupported-format"));
        Files.writeString(captureDirectory.resolve("frame-0000.jpg"), "not a supported capture frame", StandardCharsets.UTF_8);

        CaptureReceiverResult result = service.evaluate(CaptureReceiverRequest.evaluateOnly(List.of(captureDirectory)));

        assertAll(
                () -> assertEquals(CaptureReceiverStatus.REJECTED, result.status()),
                () -> assertDiagnostic(result, CaptureDiagnosticCode.UNSUPPORTED_FORMAT),
                () -> assertEquals(1, result.summary().submittedFrameCount()),
                () -> assertEquals(0, result.summary().readableFrameCount())
        );
    }

    @Test
    @DisplayName("Corrupted tile content is incomplete with corrupted-content and missing-content diagnostics")
    void corruptedTileContentIsIncompleteWithStableDiagnostics() throws Exception {
        GeneratedCaptureFixture fixture = CaptureReceiverCorpusFixtures.corruptedTileContent(tempDir);

        CaptureReceiverResult result = service.evaluate(
                CaptureReceiverRequest.evaluateOnly(List.of(fixture.captureFramesDirectory()))
        );

        assertAll(
                () -> assertEquals(CaptureReceiverStatus.INCOMPLETE, result.status()),
                () -> assertDiagnostic(result, CaptureDiagnosticCode.CORRUPTED_OR_UNREADABLE_TILE_CONTENT),
                () -> assertDiagnostic(result, CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT),
                () -> assertTrue(result.summary().acceptedCandidateCount() > 0),
                () -> assertTrue(result.summary().rejectedFrameCount() > 0)
        );
    }

    private CaptureReceiverResult evaluate(GeneratedImageFolder folder) {
        return service.evaluate(CaptureReceiverRequest.evaluateOnly(List.of(folder.captureFramesDirectory())));
    }

    private void assertDiagnostic(CaptureReceiverResult result, CaptureDiagnosticCode code) {
        assertTrue(
                result.diagnostics().stream().map(CaptureFrameDiagnostic::code).anyMatch(code::equals),
                "Expected diagnostic code " + code + " in " + result.diagnostics()
        );
    }
}
