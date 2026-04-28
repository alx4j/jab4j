package pro.alx4j.jab4j.tile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tile codec API model")
class TileCodecApiTest {

    private static final TileCodecProfile BALANCED_V1_PROFILE =
            new TileCodecProfile("balanced-v1", "binary", 8, 1, 1, 8, 8, 8, 7);

    @Test
    @DisplayName("Balanced profiles use the deterministic version formula")
    void balancedProfileUsesDeterministicVersionFormula() {
        assertAll(
                () -> assertEquals(21, BALANCED_V1_PROFILE.dimensionForSideVersion(1)),
                () -> assertEquals(49, BALANCED_V1_PROFILE.dimensionForSideVersion(8)),
                () -> assertEquals(3, BALANCED_V1_PROFILE.bitsPerModule())
        );
    }

    @Test
    @DisplayName("Logical tiles reject module color counts that do not match dimensions")
    void logicalTileRejectsModuleColorCountsThatDoNotMatchDimensions() {
        TileCodecException exception = assertThrows(TileCodecException.class, () -> new LogicalTile(
                2,
                2,
                1,
                BALANCED_V1_PROFILE.profileId(),
                List.of(0, 1, 2),
                Map.of("key", "value")
        ));

        assertEquals("moduleColors size must equal widthModules * heightModules", exception.getMessage());
    }

    @Test
    @DisplayName("Public codec factories expose the default encoder and decoder")
    void publicCodecFactoriesExposeDefaultImplementations() {
        assertAll(
                () -> assertNotNull(TileCodecs.defaultEncoder()),
                () -> assertNotNull(TileCodecs.defaultDecoder()),
                () -> assertEquals(BALANCED_V1_PROFILE, TileCodecProfiles.balancedV1()),
                () -> assertEquals(BALANCED_V1_PROFILE, TileCodecProfiles.resolve("balanced-v1")),
                () -> assertEquals(
                        "c39c547cf8d855082c55e51c075e88c44ea7fff0c2002d281d137307e73fe288",
                        TileCodecProfiles.codecProfileHash()
                )
        );
    }
}
