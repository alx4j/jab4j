package pro.alx4j.jab4j.render.frame;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.alx4j.jab4j.api.model.FrameDescriptor;
import pro.alx4j.jab4j.api.model.LayoutProfile;
import pro.alx4j.jab4j.api.model.TilePayload;
import pro.alx4j.jab4j.support.HashingUtils;
import pro.alx4j.jab4j.render.layout.FixedLayoutPlan;
import pro.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import pro.alx4j.jab4j.render.layout.TilePlacement;
import pro.alx4j.jab4j.render.tile.RenderedTile;

/**
 * Composes deterministic full-frame rasters from transport frame descriptors and slot-sized rendered tiles.
 */
public final class FrameRasterRenderer {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DARK_GRAY = 0xFF202020;
    private static final Logger LOGGER = LoggerFactory.getLogger(FrameRasterRenderer.class);

    private final FixedLayoutPlanner layoutPlanner = new FixedLayoutPlanner();

    /**
     * Renders a full frame using transport-safe defaults.
     *
     * @param frameDescriptor frame metadata and tile placement metadata
     * @param renderedTiles ordered rendered tiles aligned to {@code frameDescriptor.tiles()}
     * @return deterministic full-frame raster
     */
    public RenderedFrame render(FrameDescriptor frameDescriptor, List<RenderedTile> renderedTiles) {
        return render(frameDescriptor, renderedTiles, FrameRenderOptions.transportSafeDefaults());
    }

    /**
     * Renders a full frame using explicit rendering options.
     *
     * @param frameDescriptor frame metadata and tile placement metadata
     * @param renderedTiles ordered rendered tiles aligned to {@code frameDescriptor.tiles()}
     * @param options rendering options
     * @return deterministic full-frame raster
     */
    public RenderedFrame render(
            FrameDescriptor frameDescriptor,
            List<RenderedTile> renderedTiles,
            FrameRenderOptions options
    ) {
        Objects.requireNonNull(frameDescriptor, "frameDescriptor must not be null");
        renderedTiles = List.copyOf(Objects.requireNonNull(renderedTiles, "renderedTiles must not be null"));
        options = Objects.requireNonNull(options, "options must not be null");
        try {
            if (renderedTiles.size() != frameDescriptor.tiles().size()) {
                throw new FrameRenderException("renderedTiles count must match frameDescriptor tiles count");
            }

            FixedLayoutPlan layoutPlan = layoutPlanner.plan(frameDescriptor.layout());
            LayoutProfile profile = layoutPlan.profile();
            int[] pixels = new int[profile.frameWidthPx() * profile.frameHeightPx()];
            fill(pixels, BLACK);

            if (options.outerBorderEnabled()) {
                paintOuterBorder(pixels, profile.frameWidthPx(), profile.frameHeightPx(), layoutPlan.separatorThicknessPx());
            }
            paintSyncBand(pixels, profile.frameWidthPx(), layoutPlan);
            paintMetadataBand(pixels, profile.frameWidthPx(), layoutPlan, frameDescriptor);
            paintSeparatorBands(pixels, profile.frameWidthPx(), layoutPlan);
            blitTiles(pixels, profile.frameWidthPx(), layoutPlan, frameDescriptor.tiles(), renderedTiles);
            if (options.debugOverlayEnabled()) {
                paintDebugOverlay(pixels, profile.frameWidthPx(), layoutPlan);
            }

            Map<String, String> diagnostics = new LinkedHashMap<>();
            diagnostics.put("frameIndex", Long.toString(frameDescriptor.frameIndex()));
            diagnostics.put("frameType", frameDescriptor.frameType().name());
            diagnostics.put("layoutProfileId", profile.profileId());
            diagnostics.put("outerBorderEnabled", Boolean.toString(options.outerBorderEnabled()));
            diagnostics.put("debugOverlayEnabled", Boolean.toString(options.debugOverlayEnabled()));
            diagnostics.put("occupiedTileCount", Integer.toString(frameDescriptor.tiles().size()));
            diagnostics.put("syncBandStartYPx", Integer.toString(profile.outerMarginPx()));
            diagnostics.put("metadataBandStartYPx", Integer.toString(profile.outerMarginPx() + profile.topSyncBandPx()));
            diagnostics.put("gridOriginXPx", Integer.toString(layoutPlan.gridOriginXPx()));
            diagnostics.put("gridOriginYPx", Integer.toString(layoutPlan.gridOriginYPx()));
            diagnostics.put("pixelSha256", HashingUtils.sha256Hex(toBytes(pixels)));

            return new RenderedFrame(
                    frameDescriptor.frameIndex(),
                    frameDescriptor.frameType(),
                    profile.frameWidthPx(),
                    profile.frameHeightPx(),
                    toList(pixels),
                    diagnostics
            );
        } catch (FrameRenderException exception) {
            LOGGER.warn(
                    "Failed to render frame frameIndex={} frameType={} layoutProfileId={} payloadTiles={} renderedTiles={} outerBorderEnabled={} debugOverlayEnabled={} message={}",
                    frameDescriptor.frameIndex(),
                    frameDescriptor.frameType(),
                    frameDescriptor.layout().profileId(),
                    frameDescriptor.tiles().size(),
                    renderedTiles.size(),
                    options.outerBorderEnabled(),
                    options.debugOverlayEnabled(),
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unexpected frame rendering failure frameIndex={} frameType={} layoutProfileId={} payloadTiles={} renderedTiles={} outerBorderEnabled={} debugOverlayEnabled={}",
                    frameDescriptor.frameIndex(),
                    frameDescriptor.frameType(),
                    frameDescriptor.layout().profileId(),
                    frameDescriptor.tiles().size(),
                    renderedTiles.size(),
                    options.outerBorderEnabled(),
                    options.debugOverlayEnabled(),
                    exception
            );
            throw exception;
        }
    }

    private void paintOuterBorder(int[] pixels, int frameWidth, int frameHeight, int thickness) {
        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                if (x < thickness || x >= frameWidth - thickness || y < thickness || y >= frameHeight - thickness) {
                    pixels[(y * frameWidth) + x] = WHITE;
                }
            }
        }
    }

    private void paintSyncBand(int[] pixels, int frameWidth, FixedLayoutPlan layoutPlan) {
        LayoutProfile profile = layoutPlan.profile();
        int left = profile.outerMarginPx();
        int top = profile.outerMarginPx();
        int rightExclusive = frameWidth - profile.outerMarginPx();
        int bottomExclusive = top + profile.topSyncBandPx();
        int cellWidth = Math.max(8, layoutPlan.separatorThicknessPx() * 2);
        for (int y = top; y < bottomExclusive; y++) {
            for (int x = left; x < rightExclusive; x++) {
                int segmentIndex = (x - left) / cellWidth;
                pixels[(y * frameWidth) + x] = segmentIndex % 2 == 0 ? WHITE : BLACK;
            }
        }
    }

    private void paintMetadataBand(int[] pixels, int frameWidth, FixedLayoutPlan layoutPlan, FrameDescriptor frameDescriptor) {
        LayoutProfile profile = layoutPlan.profile();
        int left = profile.outerMarginPx();
        int top = profile.outerMarginPx() + profile.topSyncBandPx();
        int rightExclusive = frameWidth - profile.outerMarginPx();
        int bottomExclusive = top + profile.metadataBandPx();
        fillRect(pixels, frameWidth, left, top, rightExclusive, bottomExclusive, DARK_GRAY);
        if (profile.metadataBandPx() == 0) {
            return;
        }

        int gap = Math.max(4, layoutPlan.separatorThicknessPx());
        int blockWidth = Math.max(12, layoutPlan.separatorThicknessPx() * 3);
        int blockTop = top + Math.min(gap, Math.max(1, profile.metadataBandPx() / 4));
        int blockBottom = bottomExclusive - Math.min(gap, Math.max(1, profile.metadataBandPx() / 4));
        int blockCount = frameDescriptor.frameType().ordinal() + 1;
        int startX = left + gap;
        for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
            int blockLeft = startX + (blockIndex * (blockWidth + gap));
            int blockRight = Math.min(blockLeft + blockWidth, rightExclusive);
            if (blockLeft >= rightExclusive) {
                break;
            }
            fillRect(pixels, frameWidth, blockLeft, blockTop, blockRight, blockBottom, WHITE);
        }

        long frameIndex = frameDescriptor.frameIndex();
        int bitStride = Math.max(3, layoutPlan.separatorThicknessPx());
        int bitTop = bottomExclusive - Math.max(3, profile.metadataBandPx() / 5);
        for (int bitIndex = 0; bitIndex < Long.SIZE && (left + gap + (bitIndex * bitStride)) < rightExclusive / 2; bitIndex++) {
            if (((frameIndex >> bitIndex) & 1L) == 0L) {
                continue;
            }
            int x = left + gap + (bitIndex * bitStride);
            fillRect(pixels, frameWidth, x, bitTop, Math.min(x + 2, rightExclusive), bottomExclusive - 1, WHITE);
        }
    }

    private void paintSeparatorBands(int[] pixels, int frameWidth, FixedLayoutPlan layoutPlan) {
        LayoutProfile profile = layoutPlan.profile();
        List<TilePlacement> placements = layoutPlan.tilePlacements();
        int gridLeft = layoutPlan.gridOriginXPx();
        int gridTop = layoutPlan.gridOriginYPx();
        int gridRightExclusive = gridLeft + (profile.cols() * layoutPlan.tileSlotWidthPx())
                + ((profile.cols() - 1) * profile.tileGapPx());
        int gridBottomExclusive = gridTop + (profile.rows() * layoutPlan.tileSlotHeightPx())
                + ((profile.rows() - 1) * profile.tileGapPx());

        for (int col = 0; col < profile.cols() - 1; col++) {
            TilePlacement leftPlacement = placements.get(col);
            TilePlacement rightPlacement = placements.get(col + 1);
            fillRect(
                    pixels,
                    frameWidth,
                    leftPlacement.xPx() + leftPlacement.widthPx(),
                    gridTop,
                    rightPlacement.xPx(),
                    gridBottomExclusive,
                    WHITE
            );
        }

        for (int row = 0; row < profile.rows() - 1; row++) {
            TilePlacement upperPlacement = placements.get(row * profile.cols());
            TilePlacement lowerPlacement = placements.get((row + 1) * profile.cols());
            fillRect(
                    pixels,
                    frameWidth,
                    gridLeft,
                    upperPlacement.yPx() + upperPlacement.heightPx(),
                    gridRightExclusive,
                    lowerPlacement.yPx(),
                    WHITE
            );
        }
    }

    private void blitTiles(
            int[] pixels,
            int frameWidth,
            FixedLayoutPlan layoutPlan,
            List<TilePayload> tilePayloads,
            List<RenderedTile> renderedTiles
    ) {
        for (int index = 0; index < tilePayloads.size(); index++) {
            TilePayload tilePayload = tilePayloads.get(index);
            RenderedTile renderedTile = renderedTiles.get(index);
            validateRenderedTileDimensions(renderedTile, layoutPlan);
            TilePlacement placement = layoutPlan.tilePlacements().get(tilePayload.tileIndex().value());
            blitRenderedTile(pixels, frameWidth, placement, renderedTile);
        }
    }

    private void validateRenderedTileDimensions(RenderedTile renderedTile, FixedLayoutPlan layoutPlan) {
        if (renderedTile.widthPixels() != layoutPlan.tileSlotWidthPx()
                || renderedTile.heightPixels() != layoutPlan.tileSlotHeightPx()) {
            throw new FrameRenderException("rendered tiles must match the fixed-layout slot dimensions");
        }
    }

    private void blitRenderedTile(int[] pixels, int frameWidth, TilePlacement placement, RenderedTile renderedTile) {
        List<Integer> tilePixels = renderedTile.argbPixels();
        int tileWidth = renderedTile.widthPixels();
        for (int row = 0; row < renderedTile.heightPixels(); row++) {
            int destinationStart = ((placement.yPx() + row) * frameWidth) + placement.xPx();
            int sourceStart = row * tileWidth;
            for (int col = 0; col < tileWidth; col++) {
                pixels[destinationStart + col] = tilePixels.get(sourceStart + col);
            }
        }
    }

    private void paintDebugOverlay(int[] pixels, int frameWidth, FixedLayoutPlan layoutPlan) {
        LayoutProfile profile = layoutPlan.profile();
        int overlayTop = profile.outerMarginPx() + profile.topSyncBandPx() + 2;
        int overlayBottomExclusive = Math.min(
                overlayTop + Math.max(8, layoutPlan.separatorThicknessPx()),
                profile.outerMarginPx() + profile.topSyncBandPx() + profile.metadataBandPx()
        );
        if (overlayTop >= overlayBottomExclusive) {
            return;
        }

        int overlayRightExclusive = profile.frameWidthPx() - profile.outerMarginPx() - 4;
        int overlayLeft = Math.max(profile.outerMarginPx(), overlayRightExclusive - Math.max(8, layoutPlan.separatorThicknessPx()));
        fillRect(pixels, frameWidth, overlayLeft, overlayTop, overlayRightExclusive, overlayBottomExclusive, WHITE);
    }

    private void fillRect(
            int[] pixels,
            int frameWidth,
            int left,
            int top,
            int rightExclusive,
            int bottomExclusive,
            int color
    ) {
        for (int y = top; y < bottomExclusive; y++) {
            for (int x = left; x < rightExclusive; x++) {
                pixels[(y * frameWidth) + x] = color;
            }
        }
    }

    private void fill(int[] pixels, int color) {
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = color;
        }
    }

    private List<Integer> toList(int[] pixels) {
        List<Integer> values = new ArrayList<>(pixels.length);
        for (int pixel : pixels) {
            values.add(pixel);
        }
        return values;
    }

    private byte[] toBytes(int[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        return buffer.array();
    }
}
