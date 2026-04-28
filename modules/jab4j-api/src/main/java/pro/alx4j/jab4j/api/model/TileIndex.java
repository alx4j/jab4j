package pro.alx4j.jab4j.api.model;

/**
 * Zero-based tile index within a rendered frame.
 *
 * @param value zero-based tile index
 */
public record TileIndex(int value) {

    /**
     * Creates a validated tile index.
     *
     * @param value zero-based tile index
     */
    public TileIndex {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }
}
