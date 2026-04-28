package pro.alx4j.jab4j.transfer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.alx4j.jab4j.api.model.FrameType;
import pro.alx4j.jab4j.api.model.PayloadKind;
import pro.alx4j.jab4j.api.model.SessionId;
import pro.alx4j.jab4j.api.model.TileIndex;
import pro.alx4j.jab4j.api.model.TilePayload;
import pro.alx4j.jab4j.support.ChecksumUtils;

@DisplayName("Tile payload envelope codec")
class TilePayloadEnvelopeCodecTest {

    private static final String EXPECTED_ENVELOPE_HEX =
            "4a34544c0000000104040000000000000000000c00000002000000040000000000000021000000750000000203f89f520123456789abcdef0fedcba9876543210000001470726f66696c652d6465736b746f702d736166650102";

    private final TilePayloadEnvelopeCodec codec = new TilePayloadEnvelopeCodec();

    @Test
    @DisplayName("Envelope bytes round-trip deterministically")
    void roundTripsStableEnvelopeBytes() {
        TilePayload payload = samplePayload();

        byte[] first = codec.serialize(payload);
        byte[] second = codec.serialize(payload);
        TilePayload parsed = codec.parse(first, 1);

        assertAll(
                () -> assertArrayEquals(first, second),
                () -> assertEquals(payload, parsed),
                () -> assertEquals(EXPECTED_ENVELOPE_HEX, HexFormat.of().formatHex(first))
        );
    }

    @Test
    @DisplayName("Unsupported compatibility versions are rejected")
    void rejectsUnsupportedCompatibilityVersion() {
        byte[] envelope = codec.serialize(samplePayload());

        TransportException exception = assertThrows(TransportException.class, () -> codec.parse(envelope, 2));

        assertEquals("Unsupported protocol compatibility version: 1", exception.getMessage());
    }

    @Test
    @DisplayName("CRC mismatches are rejected")
    void rejectsCrcMismatch() {
        byte[] envelope = codec.serialize(samplePayload());
        envelope[envelope.length - 1] ^= 0x01;

        TransportException exception = assertThrows(TransportException.class, () -> codec.parse(envelope, 1));

        assertEquals("Envelope payload CRC32C does not match the payload bytes", exception.getMessage());
    }

    @Test
    @DisplayName("Truncated envelopes are rejected")
    void rejectsTruncatedEnvelope() {
        byte[] envelope = codec.serialize(samplePayload());
        byte[] truncated = java.util.Arrays.copyOf(envelope, envelope.length - 1);

        TransportException exception = assertThrows(TransportException.class, () -> codec.parse(truncated, 1));

        assertEquals("Envelope length does not match the declared payload and layout lengths", exception.getMessage());
    }

    private TilePayload samplePayload() {
        byte[] body = new byte[] {1, 2};
        return new TilePayload(
                1,
                new SessionId(UUID.fromString("01234567-89ab-cdef-0fed-cba987654321")),
                FrameType.DATA,
                12,
                new TileIndex(2),
                4,
                "profile-desktop-safe",
                PayloadKind.FILE_CHUNK,
                33,
                body.length,
                ChecksumUtils.crc32c(body),
                117,
                body
        );
    }
}
