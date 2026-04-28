package pro.alx4j.jab4j.tile.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.alx4j.jab4j.tile.LogicalTile;
import pro.alx4j.jab4j.tile.TileCodecException;
import pro.alx4j.jab4j.tile.TileCodecProfile;

@DisplayName("Deterministic tile decoding")
class DeferredTileDecoderTest {

    private static final TileCodecProfile BALANCED_V1_PROFILE = SupportedTileCodecProfiles.balancedV1();

    private final DeterministicTileEncoder encoder = new DeterministicTileEncoder();
    private final DeferredTileDecoder decoder = new DeferredTileDecoder();

    @Test
    @DisplayName("Supported payloads round-trip through the encoder and decoder")
    void supportedPayloadsRoundTripThroughTheEncoderAndDecoder() {
        assertRoundTrip("empty payload", new byte[0]);
        assertRoundTrip("single zero byte", new byte[]{0x00});
        assertRoundTrip("short text payload", "HELLO".getBytes(StandardCharsets.UTF_8));
        assertRoundTrip("larger text payload", "A".repeat(150).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Tiles without diagnostics fall back to supported-subset search")
    void decodesTilesWithoutDiagnosticsByFallingBackToSupportedSubsetSearch() {
        byte[] payload = "fixture-decode".getBytes(StandardCharsets.UTF_8);
        LogicalTile encoded = encoder.encode(payload, BALANCED_V1_PROFILE);
        LogicalTile withoutDiagnostics = copyWithoutDiagnostics(encoded);

        assertArrayEquals(payload, decoder.decode(withoutDiagnostics, BALANCED_V1_PROFILE));
    }

    @Test
    @DisplayName("Corrupted finder patterns fail with a specific message")
    void rejectsCorruptedFinderPatternsWithASpecificMessage() {
        LogicalTile encoded = encoder.encode("HELLO".getBytes(StandardCharsets.UTF_8), BALANCED_V1_PROFILE);
        LogicalTile corrupted = withMutatedColor(encoded, 0, 7);

        TileCodecException exception = assertThrows(
                TileCodecException.class,
                () -> decoder.decode(corrupted, BALANCED_V1_PROFILE)
        );

        assertEquals("Logical tile does not match the supported finder patterns", exception.getMessage());
    }

    @Test
    @DisplayName("Unsupported profiles fail with a specific message")
    void rejectsUnsupportedProfilesWithASpecificMessage() {
        LogicalTile encoded = encoder.encode(new byte[]{1, 2, 3}, BALANCED_V1_PROFILE);
        TileCodecProfile unsupported = new TileCodecProfile("unsupported", "binary", 8, 1, 1, 1, 8, 8, 7);

        TileCodecException exception = assertThrows(TileCodecException.class, () -> decoder.decode(encoded, unsupported));

        assertEquals("Unsupported tile codec profile: unsupported", exception.getMessage());
    }

    @Test
    @DisplayName("Corrupted data modules fail with a specific message")
    void rejectsCorruptedDataModulesWithASpecificMessage() {
        LogicalTile encoded = encoder.encode("HELLO".getBytes(StandardCharsets.UTF_8), BALANCED_V1_PROFILE);
        LogicalTile corrupted = mutateFirstDataModule(encoded);

        TileCodecException exception = assertThrows(
                TileCodecException.class,
                () -> decoder.decode(corrupted, BALANCED_V1_PROFILE)
        );

        assertEquals("Logical tile parity bytes do not match the supported deterministic parity stage", exception.getMessage());
    }

    private void assertRoundTrip(String fixtureName, byte[] payload) {
        LogicalTile encoded = encoder.encode(payload, BALANCED_V1_PROFILE);
        assertArrayEquals(payload, decoder.decode(encoded, BALANCED_V1_PROFILE), fixtureName);
    }

    private LogicalTile copyWithoutDiagnostics(LogicalTile tile) {
        return new LogicalTile(
                tile.widthModules(),
                tile.heightModules(),
                tile.quietZoneModules(),
                tile.profileId(),
                tile.moduleColors(),
                Map.of()
        );
    }

    private LogicalTile withMutatedColor(LogicalTile tile, int moduleIndex, int replacementColor) {
        List<Integer> colors = new ArrayList<>(tile.moduleColors());
        colors.set(moduleIndex, replacementColor);
        return new LogicalTile(
                tile.widthModules(),
                tile.heightModules(),
                tile.quietZoneModules(),
                tile.profileId(),
                colors,
                tile.diagnostics()
        );
    }

    private LogicalTile mutateFirstDataModule(LogicalTile tile) {
        List<Integer> colors = new ArrayList<>(tile.moduleColors());
        int index = (3 * tile.widthModules()) + 3;
        colors.set(index, (colors.get(index) + 1) % SupportedTileCodecProfiles.DEFAULT_COLOR_NUMBER);
        return new LogicalTile(
                tile.widthModules(),
                tile.heightModules(),
                tile.quietZoneModules(),
                tile.profileId(),
                colors,
                tile.diagnostics()
        );
    }
}
