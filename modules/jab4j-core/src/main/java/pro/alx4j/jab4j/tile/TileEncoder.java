package pro.alx4j.jab4j.tile;

/**
 * Encodes a binary payload into one independent logical tile.
 */
public interface TileEncoder {

    /**
     * Encodes the given payload into a deterministic logical tile for the supplied profile.
     *
     * @param payload binary payload bytes
     * @param profile supported tile codec profile
     * @return deterministic logical tile
     */
    LogicalTile encode(byte[] payload, TileCodecProfile profile);
}
