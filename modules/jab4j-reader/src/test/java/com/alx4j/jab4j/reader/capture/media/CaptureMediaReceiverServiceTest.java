package com.alx4j.jab4j.reader.capture.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.decode.CaptureFrameSetAssembler;
import com.alx4j.jab4j.reader.capture.media.decode.CaptureMediaFrameDecoder;
import com.alx4j.jab4j.reader.capture.media.input.CaptureMediaInputIntake;
import com.alx4j.jab4j.reader.capture.media.input.ImageIoCaptureMediaStillImageDecoder;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameSourceAdapter;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoLimits;
import com.alx4j.jab4j.reader.restore.ReaderRestoreService;

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
        writeJpeg(image, 320, 240);

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
                () -> assertTrue(metadata.contains("layoutProfileId=debug-low-density")),
                () -> assertTrue(metadata.contains("sampler.candidateAttemptCount=")),
                () -> assertTrue(metadata.contains("sampler.decodedPayloadCount="))
        );
    }

    private void writePng(Path output, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", output.toFile());
    }

    private void writeJpeg(Path output, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        if (!ImageIO.write(image, "jpeg", output.toFile())) {
            throw new IllegalStateException("No JPEG ImageIO writer is available");
        }
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
}
