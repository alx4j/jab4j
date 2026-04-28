package com.alx4j.jab4j.render.frame;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.render.tile.RenderedTile;

@DisplayName("Frame raster rendering")
class FrameRasterRendererTest {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DARK_GRAY = 0xFF202020;
    private static final SessionId FIXTURE_SESSION_ID =
            new SessionId(UUID.fromString("12345678-1234-5678-1234-567812345678"));
    private static final Map<FrameType, String> EXPECTED_FRAME_HASHES = Map.of(
            FrameType.SYNC, "62578b791974e7d463adc0e0ba741bb3a611f22340d37cfc772c139aa216e11a",
            FrameType.SESSION_HEADER, "89c90cba8080c9eefb34a68cc0aaa0b2acb5ee49f3af7eed5fba7d4e531096a1",
            FrameType.MANIFEST, "01f2ebad503f70817891b16c8bbf1146838ceb5e30dc4d3524c92878776c001b",
            FrameType.DATA, "1e65d8086c60539a3bd18a8abec4b580881757502f88e185dd9e9b19faa0ede4",
            FrameType.PARITY, "1cfef5b7aa7215e468935b2606b71e5e14157c3b594e224d90d5b384cff29eae",
            FrameType.END, "23f3afbc951f5b0054cc6c37d110132d95db685e7bdd6046062b5a00c80b8871"
    );

    private final FixedLayoutPlanner planner = new FixedLayoutPlanner();
    private final FrameRasterRenderer renderer = new FrameRasterRenderer();

    @Test
    @DisplayName("Required frame types produce deterministic snapshot hashes")
    void requiredFrameTypesProduceDeterministicSnapshotHashes() {
        FixedLayoutPlan layoutPlan = planner.plan(defaultProfile());
        for (Map.Entry<FrameType, List<Integer>> entry : requiredTileIndexesByType().entrySet()) {
            FrameDescriptor descriptor = frameDescriptor(entry.getKey(), 17L + entry.getKey().ordinal(), entry.getValue());
            List<RenderedTile> tiles = renderedTiles(layoutPlan, entry.getValue());

            RenderedFrame first = renderer.render(descriptor, tiles);
            RenderedFrame second = renderer.render(descriptor, tiles);

            assertAll(
                    entry.getKey().name(),
                    () -> assertEquals(first, second),
                    () -> assertEquals(layoutPlan.profile().frameWidthPx(), first.widthPixels()),
                    () -> assertEquals(layoutPlan.profile().frameHeightPx(), first.heightPixels()),
                    () -> assertEquals(EXPECTED_FRAME_HASHES.get(entry.getKey()), first.diagnostics().get("pixelSha256"))
            );
        }
    }

    @Test
    @DisplayName("Tile indexes map to the expected grid slots without filling empty slots")
    void mapsTileIndexesToExpectedGridSlotsWithoutFillingEmptySlots() {
        FixedLayoutPlan layoutPlan = planner.plan(defaultProfile());
        FrameDescriptor descriptor = frameDescriptor(FrameType.DATA, 42L, List.of(1, 2));
        List<RenderedTile> tiles = List.of(
                solidTile(layoutPlan, 0xFFAA0000, "tile-1"),
                solidTile(layoutPlan, 0xFF00AA00, "tile-2")
        );

        RenderedFrame frame = renderer.render(descriptor, tiles);

        TilePlacement topRight = layoutPlan.tilePlacements().get(1);
        TilePlacement bottomLeft = layoutPlan.tilePlacements().get(2);
        TilePlacement topLeft = layoutPlan.tilePlacements().get(0);
        TilePlacement bottomRight = layoutPlan.tilePlacements().get(3);

        assertEquals(0xFFAA0000, pixelAt(frame, centerY(topRight), centerX(topRight)));
        assertEquals(0xFF00AA00, pixelAt(frame, centerY(bottomLeft), centerX(bottomLeft)));
        assertEquals(BLACK, pixelAt(frame, centerY(topLeft), centerX(topLeft)));
        assertEquals(BLACK, pixelAt(frame, centerY(bottomRight), centerX(bottomRight)));
        assertEquals(WHITE, pixelAt(frame, centerY(topLeft), topLeft.xPx() + topLeft.widthPx()));
    }

    @Test
    @DisplayName("Wrapper bands and the outer border render in the expected regions")
    void rendersWrapperBandsAndOuterBorderInExpectedRegions() {
        LayoutProfile profile = defaultProfile();
        FixedLayoutPlan layoutPlan = planner.plan(profile);
        FrameDescriptor descriptor = frameDescriptor(FrameType.SYNC, 5L, List.of(0, 1, 2, 3));

        RenderedFrame frame = renderer.render(descriptor, renderedTiles(layoutPlan, List.of(0, 1, 2, 3)));

        assertAll(
                () -> assertEquals(WHITE, pixelAt(frame, 0, 0)),
                () -> assertEquals(BLACK, pixelAt(frame, 24, 24)),
                () -> assertEquals(WHITE, pixelAt(frame, profile.outerMarginPx() + 4, profile.outerMarginPx() + 4)),
                () -> assertEquals(DARK_GRAY, pixelAt(
                        frame,
                        profile.outerMarginPx() + profile.topSyncBandPx() + (profile.metadataBandPx() / 2),
                        profile.frameWidthPx() - profile.outerMarginPx() - 8
                ))
        );
    }

    @Test
    @DisplayName("Debug overlays only affect non-tile regions when enabled")
    void debugOverlayOnlyTouchesNonTileRegionsWhenEnabled() {
        LayoutProfile profile = defaultProfile();
        FixedLayoutPlan layoutPlan = planner.plan(profile);
        FrameDescriptor descriptor = frameDescriptor(FrameType.MANIFEST, 9L, List.of(0, 1, 2, 3));
        List<RenderedTile> tiles = renderedTiles(layoutPlan, List.of(0, 1, 2, 3));

        RenderedFrame base = renderer.render(descriptor, tiles);
        RenderedFrame debug = renderer.render(descriptor, tiles, FrameRenderOptions.debugOverlay());

        assertAll(
                () -> assertNotEquals(base.diagnostics().get("pixelSha256"), debug.diagnostics().get("pixelSha256")),
                () -> assertEquals(WHITE, pixelAt(
                        debug,
                        profile.outerMarginPx() + profile.topSyncBandPx() + 2,
                        profile.frameWidthPx() - profile.outerMarginPx() - 12
                ))
        );
        assertTileRegionsEqual(base, debug, layoutPlan.tilePlacements());
    }

    @Test
    @DisplayName("Mismatched tile counts fail clearly")
    void mismatchedTileCountFailsClearly() {
        FrameDescriptor descriptor = frameDescriptor(FrameType.END, 12L, List.of(0, 1));
        FrameRenderException exception = assertThrows(
                FrameRenderException.class,
                () -> renderer.render(descriptor, List.of())
        );

        assertEquals("renderedTiles count must match frameDescriptor tiles count", exception.getMessage());
    }

    private LayoutProfile defaultProfile() {
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

    private Map<FrameType, List<Integer>> requiredTileIndexesByType() {
        Map<FrameType, List<Integer>> tileIndexesByType = new EnumMap<>(FrameType.class);
        tileIndexesByType.put(FrameType.SYNC, List.of(0, 1, 2, 3));
        tileIndexesByType.put(FrameType.SESSION_HEADER, List.of(0, 1, 2, 3));
        tileIndexesByType.put(FrameType.MANIFEST, List.of(0, 1, 2, 3));
        tileIndexesByType.put(FrameType.DATA, List.of(0, 2));
        tileIndexesByType.put(FrameType.PARITY, List.of(1, 3));
        tileIndexesByType.put(FrameType.END, List.of(0, 1, 2, 3));
        return tileIndexesByType;
    }

    private FrameDescriptor frameDescriptor(FrameType frameType, long frameIndex, List<Integer> tileIndexes) {
        LayoutProfile profile = defaultProfile();
        List<TilePayload> tiles = new ArrayList<>(tileIndexes.size());
        long payloadSequenceNumber = 100 + frameType.ordinal();
        for (int tileIndex : tileIndexes) {
            byte[] body = ("payload-" + frameType + "-" + tileIndex).getBytes(StandardCharsets.UTF_8);
            tiles.add(new TilePayload(
                    1,
                    FIXTURE_SESSION_ID,
                    frameType,
                    frameIndex,
                    new TileIndex(tileIndex),
                    profile.rows() * profile.cols(),
                    profile.profileId(),
                    payloadKindFor(frameType),
                    payloadSequenceNumber++,
                    body.length,
                    0,
                    0,
                    body
            ));
        }
        return new FrameDescriptor(frameIndex, frameType, profile, tiles);
    }

    private PayloadKind payloadKindFor(FrameType frameType) {
        return switch (frameType) {
            case SYNC -> PayloadKind.SYNC_METADATA;
            case SESSION_HEADER -> PayloadKind.SESSION_HEADER;
            case MANIFEST -> PayloadKind.MANIFEST_FRAGMENT;
            case DATA -> PayloadKind.FILE_CHUNK;
            case PARITY -> PayloadKind.PARITY_SHARD;
            case END -> PayloadKind.SESSION_END;
        };
    }

    private List<RenderedTile> renderedTiles(FixedLayoutPlan layoutPlan, List<Integer> tileIndexes) {
        List<RenderedTile> tiles = new ArrayList<>(tileIndexes.size());
        for (int tileIndex : tileIndexes) {
            tiles.add(solidTile(layoutPlan, tileColor(tileIndex), "tile-" + tileIndex));
        }
        return tiles;
    }

    private RenderedTile solidTile(FixedLayoutPlan layoutPlan, int color, String id) {
        int width = layoutPlan.tileSlotWidthPx();
        int height = layoutPlan.tileSlotHeightPx();
        return new RenderedTile(
                width,
                height,
                List.copyOf(java.util.Collections.nCopies(width * height, color)),
                List.of(color),
                Map.of("fixtureId", id)
        );
    }

    private int tileColor(int tileIndex) {
        return switch (tileIndex) {
            case 0 -> 0xFF334455;
            case 1 -> 0xFFAA5500;
            case 2 -> 0xFF0066AA;
            case 3 -> 0xFF228833;
            default -> throw new IllegalArgumentException("Unsupported tileIndex fixture: " + tileIndex);
        };
    }

    private void assertTileRegionsEqual(RenderedFrame first, RenderedFrame second, List<TilePlacement> placements) {
        for (TilePlacement placement : placements) {
            for (int row = 0; row < placement.heightPx(); row++) {
                int y = placement.yPx() + row;
                int start = (y * first.widthPixels()) + placement.xPx();
                int end = start + placement.widthPx();
                int[] firstSlice = first.argbPixels().subList(start, end).stream().mapToInt(Integer::intValue).toArray();
                int[] secondSlice = second.argbPixels().subList(start, end).stream().mapToInt(Integer::intValue).toArray();
                assertArrayEquals(firstSlice, secondSlice, "placement " + placement + " row " + row);
            }
        }
    }

    private int pixelAt(RenderedFrame frame, int row, int col) {
        return frame.argbPixels().get((row * frame.widthPixels()) + col);
    }

    private int centerX(TilePlacement placement) {
        return placement.xPx() + (placement.widthPx() / 2);
    }

    private int centerY(TilePlacement placement) {
        return placement.yPx() + (placement.heightPx() / 2);
    }
}
