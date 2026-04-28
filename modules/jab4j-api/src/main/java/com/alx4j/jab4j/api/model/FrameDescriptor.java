package com.alx4j.jab4j.api.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable logical frame description before playback.
 *
 * @param frameIndex zero-based frame index
 * @param frameType frame type
 * @param layout layout profile used for rendering
 * @param tiles ordered tile payloads for the frame
 */
public record FrameDescriptor(long frameIndex, FrameType frameType, LayoutProfile layout, List<TilePayload> tiles) {

    /**
     * Creates a validated frame descriptor.
     *
     * @param frameIndex zero-based frame index
     * @param frameType frame type
     * @param layout layout profile
     * @param tiles ordered tile payloads
     */
    public FrameDescriptor {
        if (frameIndex < 0) {
            throw new IllegalArgumentException("frameIndex must be non-negative");
        }
        Objects.requireNonNull(frameType, "frameType must not be null");
        Objects.requireNonNull(layout, "layout must not be null");
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles must not be null"));
        if (tiles.isEmpty()) {
            throw new IllegalArgumentException("tiles must not be empty");
        }

        int totalTilesInFrame = Math.multiplyExact(layout.rows(), layout.cols());
        SessionId sessionId = null;
        int previousTileIndex = -1;
        HashSet<Integer> seenTileIndexes = new HashSet<>();
        for (TilePayload tile : tiles) {
            Objects.requireNonNull(tile, "tiles must not contain null elements");
            if (tile.frameType() != frameType) {
                throw new IllegalArgumentException("tile frameType must match frameType");
            }
            if (tile.frameIndex() != frameIndex) {
                throw new IllegalArgumentException("tile frameIndex must match frameIndex");
            }
            if (!layout.profileId().equals(tile.layoutProfileId())) {
                throw new IllegalArgumentException("tile layoutProfileId must match the frame layout");
            }
            if (tile.totalTilesInFrame() != totalTilesInFrame) {
                throw new IllegalArgumentException("tile totalTilesInFrame must match the layout capacity");
            }

            int tileIndex = tile.tileIndex().value();
            if (tileIndex >= totalTilesInFrame) {
                throw new IllegalArgumentException("tile index must stay within the frame layout capacity");
            }
            if (!seenTileIndexes.add(tileIndex)) {
                throw new IllegalArgumentException("tile indexes must be unique within a frame");
            }
            if (tileIndex <= previousTileIndex) {
                throw new IllegalArgumentException("tiles must stay ordered by tile index");
            }
            previousTileIndex = tileIndex;

            if (sessionId == null) {
                sessionId = tile.sessionId();
            } else if (!sessionId.equals(tile.sessionId())) {
                throw new IllegalArgumentException("all tiles in one frame must share the same sessionId");
            }
        }
    }
}
