package com.alx4j.jab4j.reader.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alx4j.jab4j.reader.frame.ReaderFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfile;

/**
 * Reconstructs logical tile candidates from lossless writer-rendered tile slots.
 */
final class RenderedTileSlotSampler {

    private static final int BLACK = 0xFF000000;
    private static final int MIN_MODULE_SIZE_PX = 4;
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
     * Samples one rendered tile slot into logical tile candidates, or reports that the slot is empty.
     *
     * @param frame accepted full-frame raster
     * @param layoutPlan fixed layout plan for the frame dimensions
     * @param placement slot placement to sample
     * @param tileIndex row-major tile slot index
     * @param profile supported logical tile codec profile
     * @return sampled empty or occupied slot
     */
    SampledSlot sample(ReaderFrame frame, FixedLayoutPlan layoutPlan, TilePlacement placement, int tileIndex, TileCodecProfile profile) {
        if (!hasRenderedTileBorderSignature(frame, layoutPlan, placement)) {
            if (hasInteriorContent(frame, layoutPlan, placement)) {
                throw new ReaderContentDecodeException(
                        ReaderDecodeStatus.CONTENT_CORRUPTED,
                        "Tile slot "
                                + tileIndex
                                + " in frame "
                                + frame.frameIndex()
                                + " has content but is missing the current rendered-tile border signature"
                );
            }
            return SampledSlot.emptySlot();
        }

        List<LogicalTile> candidates = new ArrayList<>();
        for (int sideVersion = profile.minSideVersion(); sideVersion <= profile.maxSideVersion(); sideVersion++) {
            LogicalTile candidate = sampleCandidate(frame, layoutPlan, placement, tileIndex, profile, sideVersion);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            throw new ReaderContentDecodeException(
                    ReaderDecodeStatus.CONTENT_CORRUPTED,
                    "Tile slot "
                            + tileIndex
                            + " in frame "
                            + frame.frameIndex()
                            + " is non-empty but cannot be sampled with the balanced-v1 palette"
            );
        }
        return SampledSlot.occupiedSlot(candidates);
    }

    private boolean hasRenderedTileBorderSignature(ReaderFrame frame, FixedLayoutPlan layoutPlan, TilePlacement placement) {
        int border = layoutPlan.separatorThicknessPx();
        for (int row = 0; row < placement.heightPx(); row++) {
            for (int col = 0; col < placement.widthPx(); col++) {
                if (row < border || row >= placement.heightPx() - border
                        || col < border || col >= placement.widthPx() - border) {
                    if (pixelAt(frame, placement.yPx() + row, placement.xPx() + col) != PALETTE.get(PALETTE.size() - 1)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean hasInteriorContent(ReaderFrame frame, FixedLayoutPlan layoutPlan, TilePlacement placement) {
        int border = layoutPlan.separatorThicknessPx();
        for (int row = border; row < placement.heightPx() - border; row++) {
            for (int col = border; col < placement.widthPx() - border; col++) {
                if (pixelAt(frame, placement.yPx() + row, placement.xPx() + col) != BLACK) {
                    return true;
                }
            }
        }
        return false;
    }

    private LogicalTile sampleCandidate(
            ReaderFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            TileCodecProfile profile,
            int sideVersion
    ) {
        int dimension = profile.dimensionForSideVersion(sideVersion);
        int border = layoutPlan.separatorThicknessPx();
        int innerWidth = layoutPlan.tileSlotWidthPx() - (2 * border);
        int innerHeight = layoutPlan.tileSlotHeightPx() - (2 * border);
        int logicalSide = dimension + (2 * profile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        if (moduleSize < MIN_MODULE_SIZE_PX) {
            return null;
        }

        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        List<Integer> moduleColors = new ArrayList<>(dimension * dimension);
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                int sampleX = placement.xPx()
                        + offsetX
                        + ((col + profile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2);
                int sampleY = placement.yPx()
                        + offsetY
                        + ((row + profile.quietZoneModules()) * moduleSize)
                        + (moduleSize / 2);
                int paletteIndex = PALETTE.indexOf(pixelAt(frame, sampleY, sampleX));
                if (paletteIndex < 0) {
                    return null;
                }
                moduleColors.add(paletteIndex);
            }
        }

        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sampledSideVersion", Integer.toString(sideVersion));
        diagnostics.put("sampledDimension", Integer.toString(dimension));
        diagnostics.put("layoutProfileId", layoutPlan.profile().profileId());
        diagnostics.put("tileIndex", Integer.toString(tileIndex));
        if (!hasSupportedFinderPatterns(moduleColors, dimension)) {
            return null;
        }
        return new LogicalTile(
                dimension,
                dimension,
                profile.quietZoneModules(),
                profile.profileId(),
                moduleColors,
                diagnostics
        );
    }

    private boolean hasSupportedFinderPatterns(List<Integer> moduleColors, int dimension) {
        return hasFinder(moduleColors, dimension, 0, 0, 0)
                && hasFinder(moduleColors, dimension, 0, dimension - 3, 0)
                && hasFinder(moduleColors, dimension, dimension - 3, 0, 6)
                && hasFinder(moduleColors, dimension, dimension - 3, dimension - 3, 3);
    }

    private boolean hasFinder(List<Integer> moduleColors, int dimension, int startRow, int startCol, int expectedColor) {
        for (int row = startRow; row < startRow + 3; row++) {
            for (int col = startCol; col < startCol + 3; col++) {
                if (moduleColors.get((row * dimension) + col) != expectedColor) {
                    return false;
                }
            }
        }
        return true;
    }

    private int pixelAt(ReaderFrame frame, int row, int col) {
        return frame.argbPixelAt(row, col);
    }

    /**
     * Result of sampling one rendered tile slot.
     *
     * @param empty true when the slot contains no rendered tile
     * @param candidates logical tile candidates for occupied slots
     */
    record SampledSlot(boolean empty, List<LogicalTile> candidates) {

        /**
         * Creates a sampled slot result.
         *
         * @param empty true when the slot is empty
         * @param candidates logical tile candidates
         */
        SampledSlot {
            candidates = List.copyOf(candidates);
        }

        /**
         * Creates an empty slot result.
         *
         * @return empty slot result
         */
        static SampledSlot emptySlot() {
            return new SampledSlot(true, List.of());
        }

        /**
         * Creates an occupied slot result with logical tile candidates.
         *
         * @param candidates logical tile candidates
         * @return occupied slot result
         */
        static SampledSlot occupiedSlot(List<LogicalTile> candidates) {
            return new SampledSlot(false, candidates);
        }
    }
}
