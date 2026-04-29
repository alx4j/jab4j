package com.alx4j.jab4j.reader.app;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.output.ExportMode;
import com.alx4j.jab4j.output.PreparedFrameExporter;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarningCode;
import com.alx4j.jab4j.render.frame.RenderedFrame;

@DisplayName("Reader application service")
class ReaderApplicationServiceTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final String FIXED_DIGEST = "digest-123";

    @TempDir
    Path tempDir;

    private final ReaderApplicationService service = new ReaderApplicationService();

    @Test
    @DisplayName("Current writer imageSequence exports are accepted from the exact directory")
    void currentWriterImageSequenceExportsAreAcceptedFromExactDirectory() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF112233);
        TestFrame dataFrame = frame(1L, FrameType.DATA, 0xFF445566);
        Path imageSequence = exportWithPreparedFrameExporter(List.of(syncFrame, dataFrame));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        ReaderFrameSet frameSet = attempt.frameSet().orElseThrow();
        assertAll(
                () -> assertEquals(ReaderDecodeStatus.DECODE_ATTEMPT_STARTED, attempt.status()),
                () -> assertTrue(attempt.accepted()),
                () -> assertEquals(imageSequence.toAbsolutePath().normalize(), attempt.inputDirectory()),
                () -> assertEquals(FIXED_SESSION_ID, frameSet.sessionId()),
                () -> assertEquals(FIXED_DIGEST, frameSet.finalSessionDigest()),
                () -> assertEquals(List.of(0L, 1L), frameSet.frames().stream().map(frame -> frame.frameIndex()).toList()),
                () -> assertEquals(syncFrame.pixelSha256(), frameSet.frames().get(0).pixelSha256()),
                () -> assertEquals(dataFrame.pixelSha256(), frameSet.frames().get(1).pixelSha256()),
                () -> assertTrue(attempt.warnings().isEmpty())
        );
    }

    @Test
    @DisplayName("Parent session directories auto-detect one imageSequence child")
    void parentSessionDirectoriesAutoDetectOneImageSequenceChild() {
        Path imageSequence = writeImageSequence(
                "parent-session",
                List.of(frame(0L, FrameType.SYNC, 0xFF111111)),
                List.of(file(frame(0L, FrameType.SYNC, 0xFF111111)))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence.getParent());

        assertAll(
                () -> assertTrue(attempt.accepted()),
                () -> assertEquals(imageSequence.toAbsolutePath().normalize(), attempt.inputDirectory()),
                () -> assertEquals(1, attempt.frameSet().orElseThrow().frames().size())
        );
    }

    @Test
    @DisplayName("Broader roots are rejected instead of scanned recursively")
    void broaderRootsAreRejectedInsteadOfScannedRecursively() {
        Path imageSequence = writeImageSequence(
                "exports/" + FIXED_SESSION_ID,
                List.of(frame(0L, FrameType.SYNC, 0xFF111111)),
                List.of(file(frame(0L, FrameType.SYNC, 0xFF111111)))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence.getParent().getParent());

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertEquals(ReaderDecodeStatus.INPUT_REJECTED, attempt.status()),
                () -> assertTrue(attempt.message().contains("broader roots are not scanned"))
        );
    }

    @Test
    @DisplayName("Missing frame-sequence metadata is rejected clearly")
    void missingFrameSequenceMetadataIsRejectedClearly() {
        Path imageSequence = tempDir.resolve("missing-metadata").resolve("imageSequence");
        createDirectories(imageSequence);
        writePng(imageSequence.resolve("frame-0000-sync.png"), frame(0L, FrameType.SYNC, 0xFF111111));

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("require frame-sequence.txt"))
        );
    }

    @Test
    @DisplayName("Metadata-only frameSequence exports are rejected because PNG frames are required")
    void metadataOnlyFrameSequenceExportsAreRejectedBecausePngFramesAreRequired() {
        Path frameSequence = tempDir.resolve("metadata-only").resolve("frameSequence");
        createDirectories(frameSequence);
        writeMetadata(frameSequence, List.of(frame(0L, FrameType.SYNC, 0xFF111111)));

        ReaderDecodeAttempt attempt = service.startDecode(frameSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("PNG frames are required"))
        );
    }

    @Test
    @DisplayName("Support files and filesystem order do not control accepted frame order")
    void supportFilesAndFilesystemOrderDoNotControlAcceptedFrameOrder() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame dataFrame = frame(1L, FrameType.DATA, 0xFF222222);
        Path imageSequence = writeImageSequence(
                "support-files",
                List.of(syncFrame, dataFrame),
                List.of(file(dataFrame), file(syncFrame))
        );
        writeString(imageSequence.resolve("notes.txt"), "ignored");
        writeString(imageSequence.resolve("frame-sequence.json"), "{}");

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertTrue(attempt.accepted()),
                () -> assertEquals(
                        List.of(FrameType.SYNC, FrameType.DATA),
                        attempt.frameSet().orElseThrow().frames().stream().map(frame -> frame.frameType()).toList()
                )
        );
    }

    @Test
    @DisplayName("Folders with PNGs but no recognizable jab4j frames are rejected")
    void foldersWithPngsButNoRecognizableJab4jFramesAreRejected() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        Path imageSequence = writeImageSequence("no-recognizable-frames", List.of(syncFrame), List.of());
        writePng(imageSequence.resolve("preview.png"), syncFrame);

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("No recognizable jab4j PNG frame files"))
        );
    }

    @Test
    @DisplayName("Metadata and PNG pixel hash disagreement is rejected")
    void metadataAndPngPixelHashDisagreementIsRejected() {
        TestFrame metadataFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame pngFrame = frame(0L, FrameType.SYNC, 0xFF999999);
        Path imageSequence = writeImageSequence(
                "hash-mismatch",
                List.of(metadataFrame),
                List.of(file(pngFrame, metadataFrame.fileName()))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("does not match frame-sequence.txt hash"))
        );
    }

    @Test
    @DisplayName("Byte-identical duplicate frame identities are ignored with a warning")
    void byteIdenticalDuplicateFrameIdentitiesAreIgnoredWithWarning() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame dataFrame = frame(1L, FrameType.DATA, 0xFF222222);
        Path imageSequence = writeImageSequence(
                "duplicate-identical",
                List.of(syncFrame, dataFrame),
                List.of(file(syncFrame), file(dataFrame), file(dataFrame, "frame-0001-data-copy.png"))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertTrue(attempt.accepted()),
                () -> assertEquals(2, attempt.frameSet().orElseThrow().frames().size()),
                () -> assertEquals(1, attempt.warnings().size()),
                () -> assertEquals(ReaderWarningCode.DUPLICATE_FRAME_IGNORED, attempt.warnings().get(0).code())
        );
    }

    @Test
    @DisplayName("Conflicting duplicate frame identities are rejected")
    void conflictingDuplicateFrameIdentitiesAreRejected() {
        TestFrame syncFrame = frame(0L, FrameType.SYNC, 0xFF111111);
        TestFrame dataFrame = frame(1L, FrameType.DATA, 0xFF222222);
        TestFrame conflictingDataFrame = frame(1L, FrameType.DATA, 0xFF333333);
        Path imageSequence = writeImageSequence(
                "duplicate-conflicting",
                List.of(syncFrame, dataFrame),
                List.of(file(syncFrame), file(dataFrame), file(conflictingDataFrame, "frame-0001-data-copy.png"))
        );

        ReaderDecodeAttempt attempt = service.startDecode(imageSequence);

        assertAll(
                () -> assertFalse(attempt.accepted()),
                () -> assertTrue(attempt.message().contains("conflicting PNG bytes"))
        );
    }

    private Path exportWithPreparedFrameExporter(List<TestFrame> frames) {
        PreparedFrameExporter exporter = new PreparedFrameExporter();
        return exporter.export(
                ExportMode.IMAGE_SEQUENCE,
                FIXED_SESSION_ID,
                FIXED_DIGEST,
                frames.stream().map(TestFrame::toRenderedFrame).toList(),
                tempDir.resolve("writer-export")
        ).exportDirectory();
    }

    private Path writeImageSequence(String parentName, List<TestFrame> metadataFrames, List<FrameFile> frameFiles) {
        Path imageSequence = tempDir.resolve(parentName).resolve("imageSequence");
        createDirectories(imageSequence);
        writeMetadata(imageSequence, metadataFrames);
        for (FrameFile frameFile : frameFiles) {
            writePng(imageSequence.resolve(frameFile.fileName()), frameFile.frame());
        }
        return imageSequence;
    }

    private void writeMetadata(Path directory, List<TestFrame> frames) {
        StringBuilder builder = new StringBuilder();
        builder.append("sessionId=").append(FIXED_SESSION_ID).append('\n');
        builder.append("finalSessionDigest=").append(FIXED_DIGEST).append('\n');
        builder.append("frameCount=").append(frames.size()).append('\n');
        for (TestFrame frame : frames) {
            builder.append("frame=").append(frame.frameIndex())
                    .append('\t').append(frame.frameType())
                    .append('\t').append(frame.pixelSha256())
                    .append('\n');
        }
        writeString(directory.resolve("frame-sequence.txt"), builder.toString());
    }

    private FrameFile file(TestFrame frame) {
        return file(frame, frame.fileName());
    }

    private FrameFile file(TestFrame frame, String fileName) {
        return new FrameFile(frame, fileName);
    }

    private TestFrame frame(long frameIndex, FrameType frameType, int color) {
        return new TestFrame(frameIndex, frameType, 2, 2, List.of(color, color, color, color));
    }

    private void writePng(Path path, TestFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < frame.heightPixels(); row++) {
            for (int col = 0; col < frame.widthPixels(); col++) {
                image.setRGB(col, row, frame.argbPixels().get((row * frame.widthPixels()) + col));
            }
        }
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test PNG " + path, exception);
        }
    }

    private void writeString(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test file " + path, exception);
        }
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test directory " + path, exception);
        }
    }

    private record FrameFile(TestFrame frame, String fileName) {
    }

    private record TestFrame(
            long frameIndex,
            FrameType frameType,
            int widthPixels,
            int heightPixels,
            List<Integer> argbPixels
    ) {

        private String fileName() {
            return String.format(
                    Locale.ROOT,
                    "frame-%04d-%s.png",
                    frameIndex,
                    frameType.name().toLowerCase(Locale.ROOT)
            );
        }

        private String pixelSha256() {
            ByteBuffer buffer = ByteBuffer.allocate(argbPixels.size() * Integer.BYTES);
            for (int pixel : argbPixels) {
                buffer.putInt(pixel);
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(buffer.array()));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available", exception);
            }
        }

        private RenderedFrame toRenderedFrame() {
            return new RenderedFrame(
                    frameIndex,
                    frameType,
                    widthPixels,
                    heightPixels,
                    argbPixels,
                    Map.of("pixelSha256", pixelSha256())
            );
        }
    }
}
