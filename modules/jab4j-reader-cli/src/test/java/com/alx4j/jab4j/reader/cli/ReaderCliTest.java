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
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32C;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.output.ExportMode;
import com.alx4j.jab4j.output.PreparedFrameExporter;
import com.alx4j.jab4j.render.frame.FrameRasterRenderer;
import com.alx4j.jab4j.render.frame.RenderedFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

@DisplayName("Reader CLI execution")
class ReaderCliTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final String FIXED_DIGEST = "digest-123";
    private static final LayoutProfile DEFAULT_PROFILE = new LayoutProfile(
            "desktop-1080p-safe",
            2,
            2,
            1920,
            1080,
            24,
            48,
            "solidWhite",
            64,
            32,
            "black",
            "preserveAspect"
    );

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Valid imageSequence input prints a decoded summary")
    void validImageSequenceInputPrintsDecodedSummary() {
        Path imageSequence = writeDecodedImageSequence("valid-export");
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
                () -> assertTrue(stdoutText.contains("CONTENT_DECODED")),
                () -> assertTrue(stdoutText.contains("sessionId=" + FIXED_SESSION_ID)),
                () -> assertTrue(stdoutText.contains("frames=1")),
                () -> assertTrue(stdoutText.contains("decodedTiles=1")),
                () -> assertTrue(stdoutText.contains("layoutProfileId=desktop-1080p-safe")),
                () -> assertTrue(stdoutText.contains("FRAME frameIndex=0 frameType=DATA")),
                () -> assertTrue(stdoutText.contains("TILE frameIndex=0 tileIndex=0")),
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
                () -> assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("INPUT_REJECTED PNG frames are required"))
        );
    }

    @Test
    @DisplayName("Content decode failures print the structured failure status")
    void contentDecodeFailuresPrintTheStructuredFailureStatus() {
        Path imageSequence = writeUnsupportedLayoutImageSequence("unsupported-layout", 0xFF112233);
        ReaderCli cli = new ReaderCli();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--input", imageSequence.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        assertAll(
                () -> assertEquals(1, exitCode),
                () -> assertEquals("", stdout.toString(StandardCharsets.UTF_8)),
                () -> assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("UNSUPPORTED_LAYOUT Unsupported rendered frame dimensions"))
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

    private Path writeDecodedImageSequence(String parentName) {
        RenderedFrame frame = renderedPayloadFrame();
        return new PreparedFrameExporter().export(
                ExportMode.IMAGE_SEQUENCE,
                FIXED_SESSION_ID,
                FIXED_DIGEST,
                List.of(frame),
                tempDir.resolve(parentName)
        ).exportDirectory();
    }

    private RenderedFrame renderedPayloadFrame() {
        byte[] body = "cli".getBytes(StandardCharsets.UTF_8);
        TilePayload payload = new TilePayload(
                1,
                FIXED_SESSION_ID,
                FrameType.DATA,
                0L,
                new TileIndex(0),
                DEFAULT_PROFILE.rows() * DEFAULT_PROFILE.cols(),
                DEFAULT_PROFILE.profileId(),
                PayloadKind.FILE_CHUNK,
                0L,
                body.length,
                crc32c(body),
                0,
                body
        );
        byte[] envelope = new TilePayloadEnvelopeCodec().serialize(payload);
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
        FixedLayoutPlan layoutPlan = new FixedLayoutPlanner().plan(DEFAULT_PROFILE);
        RenderedTile renderedTile = new TileRasterRenderer().render(logicalTile, layoutPlan);
        FrameDescriptor descriptor = new FrameDescriptor(0L, FrameType.DATA, DEFAULT_PROFILE, List.of(payload));
        return new FrameRasterRenderer().render(descriptor, List.of(renderedTile));
    }

    private Path writeUnsupportedLayoutImageSequence(String parentName, int color) {
        Path imageSequence = tempDir.resolve(parentName).resolve("imageSequence");
        createDirectories(imageSequence);
        writeMetadata(imageSequence, pixelSha256(color));
        writePng(imageSequence.resolve("frame-0000-sync.png"), color);
        return imageSequence;
    }

    private int crc32c(byte[] bytes) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }

    private void writeMetadata(Path directory, String pixelSha256) {
        writeString(directory.resolve("frame-sequence.txt"), """
                sessionId=%s
                finalSessionDigest=%s
                frameCount=1
                frame=0\t%s\t%s
                """.formatted(FIXED_SESSION_ID, FIXED_DIGEST, FrameType.SYNC, pixelSha256));
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
