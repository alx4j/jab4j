package pro.alx4j.jab4j.tile;

/**
 * Decodes one logical tile back into a binary payload.
 */
public interface TileDecoder {

    /**
     * Decodes a logical tile with the supplied profile.
     *
     * @param tile logical tile to decode
     * @param profile supported tile codec profile
     * @return decoded binary payload
     */
    byte[] decode(LogicalTile tile, TileCodecProfile profile);
}
