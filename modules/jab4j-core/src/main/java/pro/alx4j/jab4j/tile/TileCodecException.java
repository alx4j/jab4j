package pro.alx4j.jab4j.tile;

/**
 * Raised when a tile codec request violates the supported subset or cannot be encoded safely.
 */
public final class TileCodecException extends IllegalArgumentException {

    /**
     * Creates a tile codec failure with a human-readable summary.
     *
     * @param message failure summary
     */
    public TileCodecException(String message) {
        super(message);
    }
}
