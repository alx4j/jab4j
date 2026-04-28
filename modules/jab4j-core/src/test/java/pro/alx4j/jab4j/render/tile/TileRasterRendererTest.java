package pro.alx4j.jab4j.render.tile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.alx4j.jab4j.api.model.LayoutProfile;
import pro.alx4j.jab4j.render.layout.FixedLayoutPlan;
import pro.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import pro.alx4j.jab4j.render.layout.LayoutValidationException;
import pro.alx4j.jab4j.tile.LogicalTile;
import pro.alx4j.jab4j.render.tile.RenderedTile;

@DisplayName("Tile raster rendering")
class TileRasterRendererTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int BLUE = 0xFF0000FF;

    private final FixedLayoutPlanner planner = new FixedLayoutPlanner();
    private final TileRasterRenderer renderer = new TileRasterRenderer();

    @Test
    @DisplayName("Rendering produces deterministic rasters and stable pixel anchors")
    void renderProducesDeterministicRasterAndStablePixelAnchors() {
        FixedLayoutPlan plan = planner.plan(defaultDesktopProfile());
        LogicalTile tile = sampleTile();

        RenderedTile first = renderer.render(tile, plan);
        RenderedTile second = renderer.render(tile, plan);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(900, first.widthPixels()),
                () -> assertEquals(432, first.heightPixels()),
                () -> assertEquals("58", first.diagnostics().get("moduleSizePx")),
                () -> assertEquals("desktop-1080p-safe", first.diagnostics().get("layoutProfileId")),
                () -> assertTrue(first.diagnostics().get("pixelSha256") != null && !first.diagnostics().get("pixelSha256").isBlank()),
                () -> assertEquals(WHITE, first.argbPixels().get(0)),
                () -> assertEquals(BLACK, pixelAt(first, 20, 20)),
                () -> assertEquals(WHITE, pixelInQuietZone(first)),
                () -> assertEquals(BLACK, pixelInModule(first, tile.quietZoneModules(), 0, 0)),
                () -> assertEquals(BLUE, pixelInModule(first, tile.quietZoneModules(), 0, 1))
        );
    }

    @Test
    @DisplayName("Tiny module sizes fail with a specific message")
    void tinyModuleSizesFailWithASpecificMessage() {
        LayoutProfile profile = new LayoutProfile(
                "tight",
                1,
                1,
                120,
                120,
                8,
                8,
                "solidWhite",
                8,
                8,
                "black",
                "preserveAspect"
        );
        FixedLayoutPlan plan = planner.plan(profile);
        LogicalTile tile = new LogicalTile(
                21,
                21,
                1,
                "balanced-v1",
                List.copyOf(java.util.Collections.nCopies(21 * 21, 0)),
                Map.of("kind", "large")
        );

        LayoutValidationException exception = assertThrows(LayoutValidationException.class, () -> renderer.render(tile, plan));

        assertEquals("tile module size must remain at least 4 pixels", exception.getMessage());
    }

    private LayoutProfile defaultDesktopProfile() {
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

    private LogicalTile sampleTile() {
        return new LogicalTile(
                5,
                5,
                1,
                "balanced-v1",
                List.of(
                        0, 1, 2, 3, 4,
                        5, 6, 7, 0, 1,
                        2, 3, 4, 5, 6,
                        7, 0, 1, 2, 3,
                        4, 5, 6, 7, 0
                ),
                Map.of("fixture", "sample")
        );
    }

    private int pixelAt(RenderedTile tile, int row, int col) {
        return tile.argbPixels().get((row * tile.widthPixels()) + col);
    }

    private int pixelInQuietZone(RenderedTile tile) {
        int offsetY = Integer.parseInt(tile.diagnostics().get("contentOffsetYPx"));
        int offsetX = Integer.parseInt(tile.diagnostics().get("contentOffsetXPx"));
        int moduleSize = Integer.parseInt(tile.diagnostics().get("moduleSizePx"));
        return pixelAt(tile, offsetY + (moduleSize / 2), offsetX + (moduleSize / 2));
    }

    private int pixelInModule(RenderedTile tile, int quietZoneModules, int moduleRow, int moduleCol) {
        int offsetY = Integer.parseInt(tile.diagnostics().get("contentOffsetYPx"));
        int offsetX = Integer.parseInt(tile.diagnostics().get("contentOffsetXPx"));
        int moduleSize = Integer.parseInt(tile.diagnostics().get("moduleSizePx"));
        int row = offsetY + ((quietZoneModules + moduleRow) * moduleSize) + (moduleSize / 2);
        int col = offsetX + ((quietZoneModules + moduleCol) * moduleSize) + (moduleSize / 2);
        return pixelAt(tile, row, col);
    }
}
