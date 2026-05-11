package com.alx4j.jab4j.reader.capture.qualify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.input.CaptureInputFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfile;

/**
 * Reconstructs logical tile candidates from clean capture PNG slots using exact writer-rendered colors.
 */
public final class CaptureTileSlotSampler {

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
     * Samples one rendered tile slot into logical tile candidates.
     *
     * @param frame decoded capture frame
     * @param layoutPlan fixed layout plan for the frame dimensions
     * @param placement slot placement to sample
     * @param tileIndex row-major tile slot index
     * @param profile supported logical tile codec profile
     * @return sampled slot result
     */
    public SampledSlot sample(
            CaptureInputFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            int tileIndex,
            TileCodecProfile profile
    ) {
        if (!hasRenderedTileBorderSignature(frame, layoutPlan, placement)) {
            return hasInteriorContent(frame, layoutPlan, placement)
                    ? SampledSlot.noSignatureContent()
                    : SampledSlot.emptySlot();
        }

        List<LogicalTile> candidates = new ArrayList<>();
        for (int sideVersion = profile.minSideVersion(); sideVersion <= profile.maxSideVersion(); sideVersion++) {
            LogicalTile candidate = sampleCandidate(frame, layoutPlan, placement, tileIndex, profile, sideVersion);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return SampledSlot.unsampledContent();
        }
        return SampledSlot.occupiedSlot(candidates);
    }

    private boolean hasRenderedTileBorderSignature(
            CaptureInputFrame frame,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement
    ) {
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

    private boolean hasInteriorContent(CaptureInputFrame frame, FixedLayoutPlan layoutPlan, TilePlacement placement) {
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
            CaptureInputFrame frame,
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

        if (!hasSupportedFinderPatterns(moduleColors, dimension)) {
            return null;
        }
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sampledSideVersion", Integer.toString(sideVersion));
        diagnostics.put("sampledDimension", Integer.toString(dimension));
        diagnostics.put("layoutProfileId", layoutPlan.profile().profileId());
        diagnostics.put("tileIndex", Integer.toString(tileIndex));
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

    private int pixelAt(CaptureInputFrame frame, int row, int col) {
        return frame.argbPixelAt(row, col);
    }

    /**
     * Result of sampling one capture tile slot.
     *
     * @param status sampled slot status
     * @param candidates logical tile candidates for occupied slots
     */
    public record SampledSlot(SampledSlotStatus status, List<LogicalTile> candidates) {

        /**
         * Creates an immutable sampled slot result.
         *
         * @param status sampled slot status
         * @param candidates logical tile candidates
         */
        public SampledSlot {
            Objects.requireNonNull(status, "status must not be null");
            candidates = List.copyOf(candidates);
        }

        /**
         * Creates an empty slot result.
         *
         * @return empty slot result
         */
        public static SampledSlot emptySlot() {
            return new SampledSlot(SampledSlotStatus.EMPTY, List.of());
        }

        /**
         * Creates a slot result for content without the supported rendered-tile signature.
         *
         * @return no-signature content result
         */
        public static SampledSlot noSignatureContent() {
            return new SampledSlot(SampledSlotStatus.NO_SIGNATURE_CONTENT, List.of());
        }

        /**
         * Creates a slot result for signed tile content that could not be sampled.
         *
         * @return unsampled content result
         */
        public static SampledSlot unsampledContent() {
            return new SampledSlot(SampledSlotStatus.UNSAMPLED_CONTENT, List.of());
        }

        /**
         * Creates an occupied slot result with logical tile candidates.
         *
         * @param candidates logical tile candidates
         * @return occupied slot result
         */
        public static SampledSlot occupiedSlot(List<LogicalTile> candidates) {
            return new SampledSlot(SampledSlotStatus.OCCUPIED, candidates);
        }
    }

    /**
     * Coarse status for one sampled capture tile slot.
     */
    public enum SampledSlotStatus {
        EMPTY,
        NO_SIGNATURE_CONTENT,
        UNSAMPLED_CONTENT,
        OCCUPIED
    }
}
