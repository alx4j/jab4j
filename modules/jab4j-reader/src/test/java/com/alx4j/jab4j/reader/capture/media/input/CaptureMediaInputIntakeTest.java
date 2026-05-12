package com.alx4j.jab4j.reader.capture.media.input;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;

@DisplayName("Capture media still-image intake")
class CaptureMediaInputIntakeTest {

    private final CaptureMediaInputIntake intake = new CaptureMediaInputIntake();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("PNG still-image intake preserves source metadata, dimensions, format, and pixel hash")
    void pngStillImageIntakePreservesSourceMetadataDimensionsFormatAndPixelHash() throws Exception {
        int[] pixels = {
                0xFF000000, 0xFFFFFFFF,
                0xFFFF0000, 0xFF00FF00
        };
        Path source = tempDir.resolve("frame.PNG");
        writePng(source, 2, 2, pixels);

        MediaIntakeResult result = intake.read(source);

        MediaInputFrame frame = result.readableFrames().get(0);
        assertAll(
                () -> assertEquals(1, result.submittedSourceCount()),
                () -> assertEquals(1, result.readableFrames().size()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals(source.toAbsolutePath().normalize().toString(), frame.sourceId()),
                () -> assertEquals(CaptureMediaSourceKind.STILL_IMAGE_FILE, frame.sourceKind()),
                () -> assertEquals(0, frame.callerOrder()),
                () -> assertEquals(2, frame.widthPixels()),
                () -> assertEquals(2, frame.heightPixels()),
                () -> assertEquals("png", frame.formatName()),
                () -> assertEquals(sha256Hex(pixels), frame.pixelSha256()),
                () -> assertEquals(0xFFFF0000, frame.argbPixelAt(1, 0))
        );
    }

    @Test
    @DisplayName("Folder intake traverses regular files by file name and reports stable unsupported diagnostics")
    void folderIntakeTraversesRegularFilesByFileNameAndReportsStableUnsupportedDiagnostics() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDir.resolve("media"));
        writePng(sourceDirectory.resolve("b.png"), 1, 1, new int[] { 0xFF000000 });
        writePng(sourceDirectory.resolve("a.png"), 1, 1, new int[] { 0xFFFFFFFF });
        Files.writeString(sourceDirectory.resolve("c.heic"), "unsupported heic");
        Files.writeString(sourceDirectory.resolve("d.mp4"), "unsupported video");
        Files.createDirectories(sourceDirectory.resolve("nested"));

        MediaIntakeResult result = intake.read(sourceDirectory);

        List<String> readableNames = result.readableFrames().stream()
                .map(frame -> Path.of(frame.sourceId()).getFileName().toString())
                .toList();
        List<CaptureMediaDiagnosticCode> diagnosticCodes = result.diagnostics().stream()
                .map(CaptureMediaDiagnostic::code)
                .toList();
        assertAll(
                () -> assertEquals(4, result.submittedSourceCount()),
                () -> assertEquals(List.of("a.png", "b.png"), readableNames),
                () -> assertEquals(List.of(0, 1), result.readableFrames().stream().map(MediaInputFrame::callerOrder).toList()),
                () -> assertEquals(2, result.diagnostics().size()),
                () -> assertEquals(List.of(
                        CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER
                ), diagnosticCodes),
                () -> assertTrue(result.diagnostics().stream().allMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertEquals(List.of(2, 3), result.diagnostics().stream()
                        .map(diagnostic -> diagnostic.callerOrder().orElseThrow())
                        .toList())
        );
    }

    @Test
    @DisplayName("Direct video request classifies MOV and MP4 as stable unsupported containers")
    void directVideoRequestClassifiesMovAndMp4AsStableUnsupportedContainers() throws Exception {
        Path mov = tempDir.resolve("capture.mov");
        Path mp4 = tempDir.resolve("capture.mp4");
        Files.writeString(mov, "unsupported mov");
        Files.writeString(mp4, "unsupported mp4");

        MediaIntakeResult result = intake.read(CaptureMediaReceiverRequest.evaluateVideoFiles(List.of(mov, mp4)));

        assertAll(
                () -> assertEquals(2, result.submittedSourceCount()),
                () -> assertTrue(result.readableFrames().isEmpty()),
                () -> assertEquals(List.of(
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER
                ), result.diagnostics().stream().map(CaptureMediaDiagnostic::code).toList()),
                () -> assertTrue(result.diagnostics().stream()
                        .allMatch(diagnostic -> diagnostic.sourceKind().orElseThrow() == CaptureMediaSourceKind.VIDEO_FILE))
        );
    }

    @Test
    @DisplayName("Invalid PNG and empty folder produce blocking diagnostics")
    void invalidPngAndEmptyFolderProduceBlockingDiagnostics() throws Exception {
        Path invalidPng = tempDir.resolve("invalid.png");
        Files.writeString(invalidPng, "not an image");
        Path emptyFolder = Files.createDirectories(tempDir.resolve("empty"));

        MediaIntakeResult invalidResult = intake.read(invalidPng);
        MediaIntakeResult emptyResult = intake.read(emptyFolder);

        assertAll(
                () -> assertEquals(1, invalidResult.submittedSourceCount()),
                () -> assertTrue(invalidResult.readableFrames().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        invalidResult.diagnostics().get(0).code()),
                () -> assertEquals(0, invalidResult.diagnostics().get(0).callerOrder().orElseThrow()),
                () -> assertEquals(0, emptyResult.submittedSourceCount()),
                () -> assertTrue(emptyResult.readableFrames().isEmpty()),
                () -> assertEquals(CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                        emptyResult.diagnostics().get(0).code()),
                () -> assertTrue(emptyResult.diagnostics().get(0).callerOrder().isEmpty())
        );
    }

    private void writePng(Path output, int width, int height, int[] pixels) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(image, "png", output.toFile());
    }

    private String sha256Hex(int[] pixels) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(buffer.array()));
    }
}
