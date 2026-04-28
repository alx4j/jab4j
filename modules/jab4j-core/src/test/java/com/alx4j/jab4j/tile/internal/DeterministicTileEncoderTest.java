package com.alx4j.jab4j.tile.internal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;

@DisplayName("Deterministic tile encoding")
class DeterministicTileEncoderTest {

    private static final TileCodecProfile BALANCED_V1_PROFILE = SupportedTileCodecProfiles.balancedV1();
    private static final String EXPECTED_HELLO_TILE_ROWS = """
            000164541252225705000
            000124056317346111000
            000010231647527010000
            620657400644315323013
            165443430372727467201
            654655541652575515111
            125517276450221653035
            213107226503260313702
            106466040726507262445
            655247355431273363305
            420356227575307575326
            721210417473717543374
            373302167071363114175
            200365213724403153476
            211100725653514616405
            057762767413355414051
            555507713452174041543
            033716014262553743476
            666230720250443414333
            666746527404315413333
            666524146430634275333""";

    private final DeterministicTileEncoder encoder = new DeterministicTileEncoder();

    @Test
    @DisplayName("Supported profiles produce a deterministic logical tile snapshot")
    void supportedProfileProducesADeterministicLogicalTileSnapshot() {
        byte[] payload = "HELLO".getBytes(StandardCharsets.UTF_8);

        LogicalTile first = encoder.encode(payload, BALANCED_V1_PROFILE);
        LogicalTile second = encoder.encode(payload, BALANCED_V1_PROFILE);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals("balanced-v1", first.profileId()),
                () -> assertEquals(21, first.widthModules()),
                () -> assertEquals(21, first.heightModules()),
                () -> assertEquals(1, first.quietZoneModules()),
                () -> assertEquals("0", first.diagnostics().get("maskPattern")),
                () -> assertEquals("5", first.diagnostics().get("payloadLength")),
                () -> assertEquals(SupportedTileCodecProfiles.CODEC_PROFILE_HASH, first.diagnostics().get("codecProfileHash")),
                () -> assertEquals(EXPECTED_HELLO_TILE_ROWS, rows(first))
        );
    }

    @Test
    @DisplayName("Larger payloads select a higher side version")
    void largerPayloadSelectsAHigherSideVersion() {
        byte[] payload = "A".repeat(150).getBytes(StandardCharsets.UTF_8);

        LogicalTile tile = encoder.encode(payload, BALANCED_V1_PROFILE);

        assertTrue(tile.widthModules() > 21);
        assertEquals(tile.widthModules(), tile.heightModules());
    }

    @Test
    @DisplayName("The maximum supported payload bytes match the encoder boundary")
    void maxSupportedPayloadBytesMatchesTheEncoderBoundary() {
        int maxPayloadBytes = SupportedTileCodecProfiles.maxPayloadBytes(BALANCED_V1_PROFILE);

        encoder.encode(new byte[maxPayloadBytes], BALANCED_V1_PROFILE);

        TileCodecException exception = assertThrows(
                TileCodecException.class,
                () -> encoder.encode(new byte[maxPayloadBytes + 1], BALANCED_V1_PROFILE)
        );

        assertAll(
                () -> assertEquals(871, maxPayloadBytes),
                () -> assertEquals("Payload exceeds supported subset capacity for profile balanced-v1", exception.getMessage())
        );
    }

    @Test
    @DisplayName("Unsupported profiles fail with a specific message")
    void unsupportedProfilesFailWithASpecificMessage() {
        TileCodecProfile unsupported = new TileCodecProfile("unsupported", "binary", 8, 1, 1, 1, 8, 8, 7);

        TileCodecException exception = assertThrows(TileCodecException.class,
                () -> encoder.encode(new byte[]{1, 2, 3}, unsupported));

        assertEquals("Unsupported tile codec profile: unsupported", exception.getMessage());
    }

    @Test
    @DisplayName("Mutated parameters for a supported profile id fail with a specific message")
    void supportedProfileIdsWithMutatedParametersFailWithASpecificMessage() {
        TileCodecProfile mutated = new TileCodecProfile("balanced-v1", "binary", 8, 2, 1, 8, 8, 8, 7);

        TileCodecException exception = assertThrows(TileCodecException.class,
                () -> encoder.encode(new byte[]{1, 2, 3}, mutated));

        assertEquals("Unsupported profile parameters for profile balanced-v1", exception.getMessage());
    }

    private String rows(LogicalTile tile) {
        StringBuilder builder = new StringBuilder();
        for (int row = 0; row < tile.heightModules(); row++) {
            if (row > 0) {
                builder.append('\n');
            }
            for (int col = 0; col < tile.widthModules(); col++) {
                builder.append(tile.moduleColorAt(row, col));
            }
        }
        return builder.toString();
    }
}
