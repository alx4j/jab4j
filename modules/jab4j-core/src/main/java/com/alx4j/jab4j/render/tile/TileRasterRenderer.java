package com.alx4j.jab4j.render.tile;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.render.internal.ArgbPixelList;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.LayoutValidationException;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.support.HashingUtils;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;

/**
 * Renders one logical tile into a deterministic slot-sized ARGB raster.
 */
public final class TileRasterRenderer {

    static final int MIN_MODULE_SIZE_PX = 4;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final Logger LOGGER = LoggerFactory.getLogger(TileRasterRenderer.class);
    private static final List<Integer> PALETTE = List.of(
            0xFF000000,
            0xFF0000FF,
            0xFF00FF00,
            0xFF00FFFF,
            0xFFFF0000,
            0xFFFF00FF,
            0xFFFFFF00,
            0xFFFFFFFF
    );

    /**
     * Renders a logical tile into one validated tile slot from a fixed-layout plan.
     *
     * @param tile logical tile to render
     * @param layoutPlan fixed layout plan that supplies slot geometry
     * @return deterministic rendered tile raster
     */
    public RenderedTile render(LogicalTile tile, FixedLayoutPlan layoutPlan) {
        try {
            if (tile == null) {
                throw new TileCodecException("tile must not be null");
            }
            if (layoutPlan == null) {
                throw new LayoutValidationException("layoutPlan must not be null");
            }

            LayoutProfile profile = layoutPlan.profile();
            int border = layoutPlan.separatorThicknessPx();
            int innerWidth = layoutPlan.tileSlotWidthPx() - (2 * border);
            int innerHeight = layoutPlan.tileSlotHeightPx() - (2 * border);
            if (innerWidth <= 0 || innerHeight <= 0) {
                throw new LayoutValidationException("tile slot must leave a positive inner render area");
            }

            int logicalWidth = tile.widthModules() + (2 * tile.quietZoneModules());
            int logicalHeight = tile.heightModules() + (2 * tile.quietZoneModules());
            int moduleSize = Math.min(innerWidth / logicalWidth, innerHeight / logicalHeight);
            if (moduleSize < MIN_MODULE_SIZE_PX) {
                throw new LayoutValidationException("tile module size must remain at least " + MIN_MODULE_SIZE_PX + " pixels");
            }

            int contentWidth = logicalWidth * moduleSize;
            int contentHeight = logicalHeight * moduleSize;
            int offsetX = border + ((innerWidth - contentWidth) / 2);
            int offsetY = border + ((innerHeight - contentHeight) / 2);

            int[] pixels = new int[layoutPlan.tileSlotWidthPx() * layoutPlan.tileSlotHeightPx()];
            fill(pixels, BLACK);
            paintBorder(pixels, layoutPlan.tileSlotWidthPx(), layoutPlan.tileSlotHeightPx(), border);
            paintQuietZone(pixels, layoutPlan.tileSlotWidthPx(), offsetX, offsetY, contentWidth, contentHeight);
            paintModules(pixels, layoutPlan.tileSlotWidthPx(), tile, offsetX, offsetY, moduleSize);

            Map<String, String> diagnostics = new LinkedHashMap<>();
            diagnostics.put("layoutProfileId", profile.profileId());
            diagnostics.put("tileSlotWidthPx", Integer.toString(layoutPlan.tileSlotWidthPx()));
            diagnostics.put("tileSlotHeightPx", Integer.toString(layoutPlan.tileSlotHeightPx()));
            diagnostics.put("moduleSizePx", Integer.toString(moduleSize));
            diagnostics.put("contentOffsetXPx", Integer.toString(offsetX));
            diagnostics.put("contentOffsetYPx", Integer.toString(offsetY));
            diagnostics.put("pixelSha256", HashingUtils.sha256Hex(toBytes(pixels)));

            LOGGER.debug(
                    "Rendered tile raster tileProfileId={} layoutProfileId={} tileModules={}x{} slotPixels={}x{} moduleSizePx={}",
                    tile.profileId(),
                    profile.profileId(),
                    tile.widthModules(),
                    tile.heightModules(),
                    layoutPlan.tileSlotWidthPx(),
                    layoutPlan.tileSlotHeightPx(),
                    moduleSize
            );
            return new RenderedTile(
                    layoutPlan.tileSlotWidthPx(),
                    layoutPlan.tileSlotHeightPx(),
                    ArgbPixelList.copyOf(pixels),
                    PALETTE,
                    diagnostics
            );
        } catch (RuntimeException exception) {
            if (exception instanceof TileCodecException || exception instanceof LayoutValidationException) {
                LOGGER.warn(
                        "Tile raster rendering failed tileProfileId={} layoutProfileId={} tileModules={}x{} message={}",
                        safeTileProfileId(tile),
                        safeLayoutProfileId(layoutPlan),
                        safeTileWidth(tile),
                        safeTileHeight(tile),
                        exception.getMessage()
                );
            } else {
                LOGGER.error(
                        "Tile raster rendering failed tileProfileId={} layoutProfileId={} tileModules={}x{}",
                        safeTileProfileId(tile),
                        safeLayoutProfileId(layoutPlan),
                        safeTileWidth(tile),
                        safeTileHeight(tile),
                        exception
                );
            }
            throw exception;
        }
    }

    private String safeTileProfileId(LogicalTile tile) {
        return tile == null ? null : tile.profileId();
    }

    private String safeLayoutProfileId(FixedLayoutPlan layoutPlan) {
        return layoutPlan == null ? null : layoutPlan.profile().profileId();
    }

    private Integer safeTileWidth(LogicalTile tile) {
        return tile == null ? null : tile.widthModules();
    }

    private Integer safeTileHeight(LogicalTile tile) {
        return tile == null ? null : tile.heightModules();
    }

    private void paintBorder(int[] pixels, int width, int height, int thickness) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x < thickness || x >= (width - thickness) || y < thickness || y >= (height - thickness)) {
                    pixels[(y * width) + x] = WHITE;
                }
            }
        }
    }

    private void paintQuietZone(int[] pixels, int width, int offsetX, int offsetY, int contentWidth, int contentHeight) {
        for (int y = offsetY; y < offsetY + contentHeight; y++) {
            for (int x = offsetX; x < offsetX + contentWidth; x++) {
                pixels[(y * width) + x] = WHITE;
            }
        }
    }

    private void paintModules(int[] pixels, int width, LogicalTile tile, int offsetX, int offsetY, int moduleSize) {
        for (int row = 0; row < tile.heightModules(); row++) {
            for (int col = 0; col < tile.widthModules(); col++) {
                int colorIndex = tile.moduleColorAt(row, col);
                if (colorIndex >= PALETTE.size()) {
                    throw new TileCodecException("Unsupported module color index for baseline renderer: " + colorIndex);
                }
                int color = PALETTE.get(colorIndex);
                int startX = offsetX + ((col + tile.quietZoneModules()) * moduleSize);
                int startY = offsetY + ((row + tile.quietZoneModules()) * moduleSize);
                for (int y = startY; y < startY + moduleSize; y++) {
                    for (int x = startX; x < startX + moduleSize; x++) {
                        pixels[(y * width) + x] = color;
                    }
                }
            }
        }
    }

    private void fill(int[] pixels, int color) {
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = color;
        }
    }

    private byte[] toBytes(int[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        return buffer.array();
    }
}
