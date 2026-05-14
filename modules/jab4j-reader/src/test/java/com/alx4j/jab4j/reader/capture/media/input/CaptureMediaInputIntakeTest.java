package com.alx4j.jab4j.reader.capture.media.input;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrame;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameReadRequest;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameReadResult;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameSourceAdapter;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoLimits;

@DisplayName("Capture media still-image intake")
class CaptureMediaInputIntakeTest {

    private final CaptureMediaInputIntake intake = new CaptureMediaInputIntake(
            new ImageIoCaptureMediaStillImageDecoder(),
            CaptureMediaVideoFrameSourceAdapter.unsupported(),
            CaptureMediaVideoLimits.conservativeDefaults()
    );

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
    @DisplayName("JPEG still-image intake accepts JPG and JPEG extensions")
    void jpegStillImageIntakeAcceptsJpgAndJpegExtensions() throws Exception {
        Path jpg = tempDir.resolve("frame-001.jpg");
        Path jpeg = tempDir.resolve("frame-002.jpeg");
        writeJpeg(jpg, 2, 1, new int[] { 0xFF336699, 0xFF663399 });
        writeJpeg(jpeg, 1, 2, new int[] { 0xFF112233, 0xFF445566 });

        MediaIntakeResult result = intake.read(List.of(jpg, jpeg));

        assertAll(
                () -> assertEquals(2, result.submittedSourceCount()),
                () -> assertEquals(2, result.readableFrames().size()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals(List.of("frame-001.jpg", "frame-002.jpeg"), result.readableFrames().stream()
                        .map(frame -> Path.of(frame.sourceId()).getFileName().toString())
                        .toList()),
                () -> assertEquals(List.of("jpeg", "jpeg"), result.readableFrames().stream()
                        .map(MediaInputFrame::formatName)
                        .toList()),
                () -> assertEquals(2, result.readableFrames().get(0).widthPixels()),
                () -> assertEquals(1, result.readableFrames().get(0).heightPixels()),
                () -> assertEquals(1, result.readableFrames().get(1).widthPixels()),
                () -> assertEquals(2, result.readableFrames().get(1).heightPixels())
        );
    }

    @Test
    @DisplayName("Uppercase JPG extension is accepted")
    void uppercaseJpgExtensionIsAccepted() throws Exception {
        Path source = tempDir.resolve("FRAME.JPG");
        writeJpeg(source, 1, 1, new int[] { 0xFF778899 });

        MediaIntakeResult result = intake.read(source);

        assertAll(
                () -> assertEquals(1, result.submittedSourceCount()),
                () -> assertEquals(1, result.readableFrames().size()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals(source.toAbsolutePath().normalize().toString(),
                        result.readableFrames().get(0).sourceId()),
                () -> assertEquals("jpeg", result.readableFrames().get(0).formatName())
        );
    }

    @Test
    @DisplayName("Folder intake traverses mixed still images by file name and reports stable unsupported diagnostics")
    void folderIntakeTraversesMixedStillImagesByFileNameAndReportsStableUnsupportedDiagnostics() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDir.resolve("media"));
        writeJpeg(sourceDirectory.resolve("a.jpeg"), 1, 1, new int[] { 0xFFFFFFFF });
        writePng(sourceDirectory.resolve("b.png"), 1, 1, new int[] { 0xFF000000 });
        writeJpeg(sourceDirectory.resolve("c.jpg"), 1, 1, new int[] { 0xFF336699 });
        Files.writeString(sourceDirectory.resolve("d.heic"), "unsupported heic");
        Files.writeString(sourceDirectory.resolve("e.mp4"), "unsupported video");
        Files.createDirectories(sourceDirectory.resolve("nested"));

        MediaIntakeResult result = intake.read(sourceDirectory);

        List<String> readableNames = result.readableFrames().stream()
                .map(frame -> Path.of(frame.sourceId()).getFileName().toString())
                .toList();
        List<CaptureMediaDiagnosticCode> diagnosticCodes = result.diagnostics().stream()
                .map(CaptureMediaDiagnostic::code)
                .toList();
        assertAll(
                () -> assertEquals(5, result.submittedSourceCount()),
                () -> assertEquals(List.of("a.jpeg", "b.png", "c.jpg"), readableNames),
                () -> assertEquals(List.of("jpeg", "png", "jpeg"), result.readableFrames().stream()
                        .map(MediaInputFrame::formatName)
                        .toList()),
                () -> assertEquals(List.of(0, 1, 2), result.readableFrames().stream()
                        .map(MediaInputFrame::callerOrder)
                        .toList()),
                () -> assertEquals(2, result.diagnostics().size()),
                () -> assertEquals(List.of(
                        CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER
                ), diagnosticCodes),
                () -> assertTrue(result.diagnostics().get(0).message().contains("libheif")),
                () -> assertTrue(result.diagnostics().stream().allMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertEquals(List.of(3, 4), result.diagnostics().stream()
                        .map(diagnostic -> diagnostic.callerOrder().orElseThrow())
                        .toList())
        );
    }

    @Test
    @DisplayName("Configured HEIC decoder accepts HEIC and HEIF still images")
    void configuredHeicDecoderAcceptsHeicAndHeifStillImages() throws Exception {
        Path heic = tempDir.resolve("frame-001.HEIC");
        Path heif = tempDir.resolve("frame-002.heif");
        Files.writeString(heic, "fake heic handled by test decoder");
        Files.writeString(heif, "fake heif handled by test decoder");
        CaptureMediaInputIntake configuredIntake = new CaptureMediaInputIntake(
                new FakeHeifStillImageDecoder(false),
                CaptureMediaVideoFrameSourceAdapter.unsupported(),
                CaptureMediaVideoLimits.conservativeDefaults()
        );

        MediaIntakeResult result = configuredIntake.read(List.of(heic, heif));

        assertAll(
                () -> assertEquals(2, result.submittedSourceCount()),
                () -> assertEquals(2, result.readableFrames().size()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals(List.of("frame-001.HEIC", "frame-002.heif"), result.readableFrames().stream()
                        .map(frame -> Path.of(frame.sourceId()).getFileName().toString())
                        .toList()),
                () -> assertEquals(List.of("heic", "heif"), result.readableFrames().stream()
                        .map(MediaInputFrame::formatName)
                        .toList()),
                () -> assertEquals(0xFF445566, result.readableFrames().get(0).argbPixelAt(0, 0)),
                () -> assertEquals(0xFF778899, result.readableFrames().get(1).argbPixelAt(0, 0))
        );
    }

    @Test
    @DisplayName("Configured HEIC decoder failures produce unreadable-media diagnostics")
    void configuredHeicDecoderFailuresProduceUnreadableMediaDiagnostics() throws Exception {
        Path heic = tempDir.resolve("broken.heic");
        Files.writeString(heic, "bad heic");
        CaptureMediaInputIntake configuredIntake = new CaptureMediaInputIntake(
                new FakeHeifStillImageDecoder(true),
                CaptureMediaVideoFrameSourceAdapter.unsupported(),
                CaptureMediaVideoLimits.conservativeDefaults()
        );

        MediaIntakeResult result = configuredIntake.read(heic);

        assertAll(
                () -> assertEquals(1, result.submittedSourceCount()),
                () -> assertTrue(result.readableFrames().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        result.diagnostics().get(0).code()),
                () -> assertTrue(result.diagnostics().get(0).message().contains("libheif")),
                () -> assertEquals(0, result.diagnostics().get(0).callerOrder().orElseThrow())
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
    @DisplayName("Configured direct-video adapter yields ordered media input frames")
    void configuredDirectVideoAdapterYieldsOrderedMediaInputFrames() throws Exception {
        Path video = tempDir.resolve("capture.mp4");
        Files.writeString(video, "adapter-owned video fixture");
        CaptureMediaVideoLimits limits = new CaptureMediaVideoLimits(1024L, 1000L, 2, 2, 10L, 2, 100L);
        AtomicReference<CaptureMediaVideoFrameReadRequest> adapterRequest = new AtomicReference<>();
        CaptureMediaInputIntake configuredIntake = new CaptureMediaInputIntake(request -> {
            adapterRequest.set(request);
            return CaptureMediaVideoFrameReadResult.fromFrames(List.of(
                    videoFrame("capture.mp4#frame-0", 0, 0L, 0L, 0xFF000000),
                    videoFrame("capture.mp4#frame-3", 1, 100L, 3L, 0xFFFFFFFF)
            ), List.of());
        }, limits);

        MediaIntakeResult result = configuredIntake.read(CaptureMediaReceiverRequest.evaluateVideoFiles(List.of(video)));

        assertAll(
                () -> assertEquals(2, result.submittedSourceCount()),
                () -> assertEquals(2, result.readableFrames().size()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals(video.toAbsolutePath().normalize(), adapterRequest.get().sourceFile()),
                () -> assertEquals(0, adapterRequest.get().callerOrder()),
                () -> assertEquals(limits, adapterRequest.get().limits()),
                () -> assertEquals(List.of(0, 1), result.readableFrames().stream()
                        .map(MediaInputFrame::callerOrder)
                        .toList()),
                () -> assertEquals(List.of("capture.mp4#frame-0", "capture.mp4#frame-3"), result.readableFrames()
                        .stream()
                        .map(MediaInputFrame::sourceId)
                        .toList()),
                () -> assertEquals(CaptureMediaSourceKind.VIDEO_FILE, result.readableFrames().get(0).sourceKind()),
                () -> assertEquals("argb", result.readableFrames().get(0).formatName()),
                () -> assertEquals(0L, result.readableFrames().get(0).timestampMillis().orElseThrow()),
                () -> assertEquals(3L, result.readableFrames().get(1).frameNumber().orElseThrow()),
                () -> assertEquals(0xFFFFFFFF, result.readableFrames().get(1).argbPixelAt(0, 0))
        );
    }

    @Test
    @DisplayName("Media input frame releases full-frame pixels while preserving source metadata")
    void mediaInputFrameReleasesFullFramePixelsWhilePreservingSourceMetadata() {
        MediaInputFrame frame = new MediaInputFrame(
                "capture.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                2,
                2,
                2,
                "png",
                "pixel-hash",
                new int[] {
                        0xFF000000, 0xFFFFFFFF,
                        0xFFFF0000, 0xFF00FF00
                }
        );
        int[] copiedRow = new int[3];

        frame.copyArgbRow(1, 0, copiedRow, 1, 2);
        frame.releaseArgbPixels();
        frame.releaseArgbPixels();

        assertAll(
                () -> assertArrayEquals(new int[] { 0, 0xFFFF0000, 0xFF00FF00 }, copiedRow),
                () -> assertEquals("capture.png", frame.sourceId()),
                () -> assertEquals(2, frame.callerOrder()),
                () -> assertEquals("pixel-hash", frame.pixelSha256()),
                () -> assertThrows(IllegalStateException.class, () -> frame.argbPixelAt(0, 0)),
                () -> assertThrows(IllegalStateException.class, frame::copyArgbPixels),
                () -> assertThrows(IllegalStateException.class,
                        () -> frame.copyArgbRow(0, 0, new int[1], 0, 1))
        );
    }

    @Test
    @DisplayName("Corrupt or mislabeled JPEG files produce unreadable-media diagnostics")
    void corruptOrMislabeledJpegFilesProduceUnreadableMediaDiagnostics() throws Exception {
        Path corruptJpg = tempDir.resolve("corrupt.jpg");
        Path mislabeledJpeg = tempDir.resolve("mislabeled.jpeg");
        Files.writeString(corruptJpg, "not a jpeg");
        Files.writeString(mislabeledJpeg, "not a jpeg either");

        MediaIntakeResult result = intake.read(List.of(corruptJpg, mislabeledJpeg));

        assertAll(
                () -> assertEquals(2, result.submittedSourceCount()),
                () -> assertTrue(result.readableFrames().isEmpty()),
                () -> assertEquals(List.of(
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA
                ), result.diagnostics().stream().map(CaptureMediaDiagnostic::code).toList()),
                () -> assertEquals(List.of(0, 1), result.diagnostics().stream()
                        .map(diagnostic -> diagnostic.callerOrder().orElseThrow())
                        .toList())
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

    private void writeJpeg(Path output, int width, int height, int[] pixels) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        if (!ImageIO.write(image, "jpeg", output.toFile())) {
            throw new IllegalStateException("No JPEG ImageIO writer is available");
        }
    }

    private CaptureMediaVideoFrame videoFrame(
            String sourceId,
            int callerOrder,
            long timestampMillis,
            long frameNumber,
            int pixel
    ) {
        return new CaptureMediaVideoFrame(
                sourceId,
                callerOrder,
                timestampMillis,
                frameNumber,
                1,
                1,
                "argb",
                new int[] { pixel }
        );
    }

    private String sha256Hex(int[] pixels) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(buffer.array()));
    }

    private static final class FakeHeifStillImageDecoder implements CaptureMediaStillImageDecoder {

        private final boolean failDecode;

        private FakeHeifStillImageDecoder(boolean failDecode) {
            this.failDecode = failDecode;
        }

        @Override
        public boolean supportsExtension(String extension) {
            return switch (CaptureMediaStillImageDecoder.normalizedExtension(extension)) {
                case "heic", "heif" -> true;
                default -> false;
            };
        }

        @Override
        public CaptureMediaStillImageDecoder.DecodedStillImage decode(Path sourceFile, String extension)
                throws IOException {
            if (failDecode) {
                throw new StillImageDecodeException(
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        "HEIC/HEIF media source could not be decoded by the configured libheif HEIC tool"
                );
            }
            String normalizedExtension = CaptureMediaStillImageDecoder.normalizedExtension(extension);
            int pixel = "heif".equals(normalizedExtension) ? 0xFF778899 : 0xFF445566;
            return new CaptureMediaStillImageDecoder.DecodedStillImage(
                    1,
                    1,
                    normalizedExtension,
                    new int[] { pixel }
            );
        }
    }
}
