package com.alx4j.jab4j.reader.capture.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Capture media receiver service")
class CaptureMediaReceiverServiceTest {

    private final CaptureMediaReceiverService service = new CaptureMediaReceiverService();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Direct video input returns stable unsupported diagnostics without requiring a readable file")
    void directVideoInputReturnsStableUnsupportedDiagnosticsWithoutReadableFile() {
        Path video = tempDir.resolve("phone-capture.mov");

        CaptureMediaReceiverResult result =
                service.evaluate(CaptureMediaReceiverRequest.evaluateVideoFiles(List.of(video)));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertTrue(result.failed()),
                () -> assertEquals(1, result.summary().submittedMediaCount()),
                () -> assertEquals(0, result.summary().readableMediaCount()),
                () -> assertEquals(1, result.summary().rejectedCandidateCount()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertEquals(CaptureMediaSourceKind.VIDEO_FILE, diagnostic.sourceKind().orElseThrow()),
                () -> assertTrue(result.message().contains(".mov/.mp4"))
        );
    }

    @Test
    @DisplayName("HEIC still input returns a stable unsupported image diagnostic before ImageIO reads")
    void heicStillInputReturnsStableUnsupportedImageDiagnosticBeforeImageIoReads() {
        Path image = tempDir.resolve("phone-photo.heic");

        CaptureMediaReceiverResult result =
                service.evaluate(CaptureMediaReceiverRequest.evaluateStillImages(List.of(image)));

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                () -> assertEquals(1, result.summary().submittedMediaCount()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                        result.diagnostics().get(0).code()),
                () -> assertTrue(result.message().contains("HEIC/HEIF"))
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

    private void writePng(Path output, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", output.toFile());
    }
}
