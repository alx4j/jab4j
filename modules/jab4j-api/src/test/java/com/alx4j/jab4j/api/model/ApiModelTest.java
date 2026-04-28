package com.alx4j.jab4j.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("API model invariants")
class ApiModelTest {

    @Test
    @DisplayName("Regular files require a digest")
    void fileRecordRequiresDigestForRegularFiles() {
        assertThrows(IllegalArgumentException.class, () -> new FileRecord(
                "root-001",
                "file.txt",
                FileType.REGULAR_FILE,
                1L,
                null,
                List.of()
        ));
    }

    @Test
    @DisplayName("Directories remain chunk-free and zero-sized")
    void directoriesRemainChunkFreeAndZeroSized() {
        assertThrows(IllegalArgumentException.class, () -> new FileRecord(
                "root-001",
                "nested",
                FileType.DIRECTORY,
                10L,
                null,
                List.of()
        ));
    }

    @Test
    @DisplayName("Layout profiles validate positive dimensions")
    void layoutProfileValidatesPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new LayoutProfile(
                "desktop-1080p-safe",
                0,
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
        ));
    }

    @Test
    @DisplayName("Tile payloads defensively copy body bytes")
    void tilePayloadDefensivelyCopiesBody() {
        byte[] payloadBytes = new byte[] {1, 2, 3};
        TilePayload payload = new TilePayload(
                1,
                SessionId.random(),
                FrameType.DATA,
                5,
                new TileIndex(0),
                4,
                "desktop-1080p-safe",
                PayloadKind.FILE_CHUNK,
                7,
                3,
                123,
                0,
                payloadBytes
        );

        payloadBytes[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, payload.body());
    }

    @Test
    @DisplayName("Tile payloads require a positive compatibility version")
    void tilePayloadRequiresPositiveCompatibilityVersion() {
        assertThrows(IllegalArgumentException.class, () -> new TilePayload(
                0,
                SessionId.random(),
                FrameType.DATA,
                0,
                new TileIndex(0),
                1,
                "desktop-1080p-safe",
                PayloadKind.FILE_CHUNK,
                0,
                0,
                0,
                0,
                new byte[0]
        ));
    }

    @Test
    @DisplayName("Frame descriptors reject mismatched tile metadata")
    void frameDescriptorRejectsMismatchedTileMetadata() {
        SessionId sessionId = SessionId.random();
        LayoutProfile layout = defaultLayoutProfile();

        TilePayload validTile = new TilePayload(
                1,
                sessionId,
                FrameType.DATA,
                3,
                new TileIndex(0),
                4,
                layout.profileId(),
                PayloadKind.FILE_CHUNK,
                7,
                0,
                0,
                0,
                new byte[0]
        );
        TilePayload mismatchedTile = new TilePayload(
                1,
                sessionId,
                FrameType.PARITY,
                3,
                new TileIndex(1),
                4,
                layout.profileId(),
                PayloadKind.PARITY_SHARD,
                8,
                0,
                0,
                0,
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> new FrameDescriptor(
                3,
                FrameType.DATA,
                layout,
                List.of(validTile, mismatchedTile)
        ));
    }

    @Test
    @DisplayName("Transfer sessions copy the file list defensively")
    void transferSessionCopiesFileList() {
        FileRecord fileRecord = sampleRegularFileRecord();
        List<FileRecord> files = new ArrayList<>(List.of(fileRecord));
        TransferSession session = new TransferSession(
                SessionId.random(),
                Instant.parse("2026-03-22T00:00:00Z"),
                new ProtocolVersion("1.0", 1),
                "build-1",
                defaultSessionProfile(),
                new Manifest(List.of(fileRecord), List.of("root-001"), 3, "manifest-1"),
                files
        );

        files.clear();

        assertAll(
                () -> assertEquals(1, session.files().size()),
                () -> assertEquals(fileRecord, session.files().get(0))
        );
    }

    private static FileRecord sampleRegularFileRecord() {
        return new FileRecord(
                "root-001",
                "file.txt",
                FileType.REGULAR_FILE,
                3,
                "abc",
                List.of(new FileChunk(0, 0, 0, 3, 10))
        );
    }

    private static SessionProfile defaultSessionProfile() {
        return new SessionProfile(
                "desktop-1080p-safe",
                defaultLayoutProfile(),
                new CodecProfile("balanced-v1", "binary", true),
                new TransportProfile(
                        "safe-v1",
                        new ProtocolVersion("1.0", 1),
                        1536,
                        20,
                        6,
                        ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                        10,
                        30,
                        60
                ),
                new PlaybackProfile("desktop-1080p-safe", 8, 1, 6, 6, true)
        );
    }

    private static LayoutProfile defaultLayoutProfile() {
        return new LayoutProfile(
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
    }
}
