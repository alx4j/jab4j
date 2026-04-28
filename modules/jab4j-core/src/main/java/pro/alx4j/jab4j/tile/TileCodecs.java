package pro.alx4j.jab4j.tile;

import pro.alx4j.jab4j.tile.internal.DeferredTileDecoder;
import pro.alx4j.jab4j.tile.internal.DeterministicTileEncoder;

/**
 * Public factory for the default codec implementations shipped by the runtime library.
 */
public final class TileCodecs {

    private TileCodecs() {
    }

    /**
     * Creates the default deterministic encoder for the supported subset.
     *
     * @return default encoder implementation
     */
    public static TileEncoder defaultEncoder() {
        return new DeterministicTileEncoder();
    }

    /**
     * Creates the default deterministic decoder for the supported subset.
     *
     * @return default decoder implementation
     */
    public static TileDecoder defaultDecoder() {
        return new DeferredTileDecoder();
    }
}
