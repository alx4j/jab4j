package com.alx4j.jab4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.writer.config.RuntimeConfig;
import com.alx4j.jab4j.writer.config.RuntimeConfigPatch;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;
import com.alx4j.jab4j.transfer.TransportException;
import com.alx4j.jab4j.writer.app.WriterApplicationService;
import com.alx4j.jab4j.writer.app.WriterJobException;
import com.alx4j.jab4j.writer.app.WriterJobObserver;
import com.alx4j.jab4j.writer.app.WriterJobStatus;
import com.alx4j.jab4j.writer.app.WriterRunRequest;
import com.alx4j.jab4j.cli.WriterCli;

@DisplayName("Writer negative integration scenarios")
class WriterNegativeIT {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-22T20:00:00Z");
    private static final SessionId PRIMARY_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final SessionId SECONDARY_SESSION_ID =
            new SessionId(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"));
    private static final LayoutProfile DEFAULT_LAYOUT = new LayoutProfile(
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
    private static final byte[] SAMPLE_BODY = new byte[] {1, 2, 3, 4};
    private static final int SMALL_CHUNK_BYTES = 32;
    private static final int DEFAULT_FPS = 4;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Corrupted envelopes surface CRC and frame-type validation failures")
    void corruptedEnvelopeCrcAndInvalidFrameTypeFailClearly() {
        TilePayloadEnvelopeCodec codec = new TilePayloadEnvelopeCodec();
        TilePayload payload = payload(PRIMARY_SESSION_ID);
        byte[] serialized = codec.serialize(payload);

        byte[] corruptedPayload = serialized.clone();
        corruptedPayload[corruptedPayload.length - 1] ^= 0x01;
        TransportException crcException = assertThrows(TransportException.class, () -> codec.parse(corruptedPayload, 1));

        byte[] invalidFrameType = serialized.clone();
        invalidFrameType[8] = 99;
        TransportException frameTypeException = assertThrows(TransportException.class, () -> codec.parse(invalidFrameType, 1));

        assertAll(
                () -> assertEquals("Envelope payload CRC32C does not match the payload bytes", crcException.getMessage()),
                () -> assertEquals("Unsupported frame type code: 99", frameTypeException.getMessage())
        );
    }

    @Test
    @DisplayName("Frame construction rejects mixed session identifiers")
    void mismatchedSessionIdsAreRejectedAtFrameBoundary() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new FrameDescriptor(
                        0L,
                        FrameType.DATA,
                        DEFAULT_LAYOUT,
                        List.of(
                                payload(PRIMARY_SESSION_ID),
                                new TilePayload(
                                        1,
                                        SECONDARY_SESSION_ID,
                                        FrameType.DATA,
                                        0L,
                                        new TileIndex(1),
                                        4,
                                        DEFAULT_LAYOUT.profileId(),
                                        PayloadKind.FILE_CHUNK,
                                        2L,
                                        4,
                                        crc32c(SAMPLE_BODY),
                                        0,
                                        SAMPLE_BODY
                                )
                        )
                )
        );

        assertEquals("all tiles in one frame must share the same sessionId", exception.getMessage());
    }

    @Test
    @DisplayName("Unsupported codec profiles and invalid layout overrides fail during config resolution")
    void unsupportedCodecProfileAndInvalidLayoutFailFast() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("negative-input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-negative");
        WriterApplicationService service = service();

        WriterJobException unsupportedProfile = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        request(inputRoot, new RuntimeConfigPatch.CodecPatch("unsupported-v9", null, null), null),
                        WriterJobObserver.noOp()
                )
        );

        WriterJobException invalidLayout = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        request(
                                inputRoot,
                                null,
                                new RuntimeConfigPatch.LayoutPatch(null, null, 0, null, null, null, null, null, null, null, null, null, null)
                        ),
                        WriterJobObserver.noOp()
                )
        );

        assertConfigResolutionFailure(unsupportedProfile);
        assertConfigResolutionFailure(invalidLayout);
    }

    @Test
    @DisplayName("Invalid CLI overrides return usage details with exit code 2")
    void brokenCliOverridesReturnUsageError() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new WriterCli().run(
                new String[] {"--input", "/tmp/demo", "--grid", "bad"},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(2, exitCode),
                () -> assertTrue(stderrText.contains("Invalid value for --grid: bad")),
                () -> assertTrue(stderrText.contains("Usage: jab4j-cli"))
        );
    }

    private WriterApplicationService service() {
        return new WriterApplicationService(
                () -> FIXED_NOW,
                () -> PRIMARY_SESSION_ID,
                tempDir.resolve("diagnostics")
        );
    }

    private WriterRunRequest request(
            Path inputRoot,
            RuntimeConfigPatch.CodecPatch codecPatch,
            RuntimeConfigPatch.LayoutPatch layoutPatch
    ) {
        return new WriterRunRequest(
                new RuntimeConfigPatch(
                        null,
                        new RuntimeConfigPatch.InputPatch(List.of(new RuntimeConfig.InputRootConfig(inputRoot.toString(), null))),
                        layoutPatch,
                        codecPatch,
                        new RuntimeConfigPatch.TransportPatch(null, SMALL_CHUNK_BYTES, null, null, null, null, null),
                        new RuntimeConfigPatch.PlaybackPatch(DEFAULT_FPS, 0, 1, 1, false),
                        null,
                        new RuntimeConfigPatch.DiagnosticsPatch(false, false, false)
                ),
                true
        );
    }

    private TilePayload payload(SessionId sessionId) {
        return new TilePayload(
                1,
                sessionId,
                FrameType.DATA,
                0L,
                new TileIndex(0),
                4,
                DEFAULT_LAYOUT.profileId(),
                PayloadKind.FILE_CHUNK,
                1L,
                SAMPLE_BODY.length,
                crc32c(SAMPLE_BODY),
                0,
                SAMPLE_BODY
        );
    }

    private void assertConfigResolutionFailure(WriterJobException exception) {
        assertAll(
                () -> assertEquals(WriterJobStatus.RESOLVING_CONFIG, exception.status()),
                () -> assertTrue(exception.getMessage().contains("Failed to resolve effective runtime config"))
        );
    }

    private int crc32c(byte[] bytes) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }
}
