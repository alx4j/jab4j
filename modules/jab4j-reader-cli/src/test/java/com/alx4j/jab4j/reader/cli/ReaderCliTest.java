package com.alx4j.jab4j.reader.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.FrameType;

@DisplayName("Reader CLI execution")
class ReaderCliTest {

    private static final UUID FIXED_SESSION_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String FIXED_DIGEST = "digest-123";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Valid imageSequence input prints an accepted summary")
    void validImageSequenceInputPrintsAcceptedSummary() {
        Path imageSequence = writeImageSequence("valid-export", 0xFF112233);
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", imageSequence.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(0, exitCode),
                () -> assertTrue(stdoutText.contains("ACCEPTED")),
                () -> assertTrue(stdoutText.contains("sessionId=" + FIXED_SESSION_ID)),
                () -> assertTrue(stdoutText.contains("frames=1")),
                () -> assertTrue(stdoutText.contains("warnings=0")),
                () -> assertEquals("", stderr.toString(StandardCharsets.UTF_8))
        );
    }

    @Test
    @DisplayName("Rejected reader input returns a runtime failure")
    void rejectedReaderInputReturnsRuntimeFailure() {
        Path frameSequence = tempDir.resolve("metadata-only").resolve("frameSequence");
        createDirectories(frameSequence);
        writeMetadata(frameSequence, pixelSha256(0xFF112233));
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", frameSequence.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("REJECTED PNG frames are required"))
        );
    }

    @Test
    @DisplayName("Usage errors return a usage exit code")
    void usageErrorsReturnUsageExitCode() {
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[0],
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        assertAll(
                () -> assertEquals(2, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("One --input path is required")),
                () -> assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Usage: jab4j-reader-cli"))
        );
    }

    private Path writeImageSequence(String parentName, int color) {
        Path imageSequence = tempDir.resolve(parentName).resolve("imageSequence");
        createDirectories(imageSequence);
        writeMetadata(imageSequence, pixelSha256(color));
        writePng(imageSequence.resolve("frame-0000-sync.png"), color);
        return imageSequence;
    }

    private void writeMetadata(Path directory, String pixelSha256) {
        writeString(directory.resolve("frame-sequence.txt"), """
                sessionId=%s
                finalSessionDigest=%s
                frameCount=1
                frame=0\t%s\t%s
                """.formatted(FIXED_SESSION_ID, FIXED_DIGEST, FrameType.SYNC, pixelSha256));
    }

    private void writePng(Path path, int color) {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                image.setRGB(col, row, color);
            }
        }
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write test PNG " + path, exception);
        }
    }

    private String pixelSha256(int color) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * Integer.BYTES);
        for (int index = 0; index < 4; index++) {
            buffer.putInt(color);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(buffer.array()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
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
}
