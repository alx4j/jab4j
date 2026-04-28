package pro.alx4j.jab4j.render.layout;

/**
 * One fixed tile-slot placement inside a frame layout.
 *
 * @param row zero-based tile row
 * @param col zero-based tile column
 * @param xPx left pixel coordinate
 * @param yPx top pixel coordinate
 * @param widthPx slot width in pixels
 * @param heightPx slot height in pixels
 */
public record TilePlacement(int row, int col, int xPx, int yPx, int widthPx, int heightPx) {

    /**
     * Creates a validated tile placement.
     *
     * @param row zero-based tile row
     * @param col zero-based tile column
     * @param xPx left pixel coordinate
     * @param yPx top pixel coordinate
     * @param widthPx slot width in pixels
     * @param heightPx slot height in pixels
     */
    public TilePlacement {
        if (row < 0 || col < 0) {
            throw new LayoutValidationException("tile placement row and col must be non-negative");
        }
        if (xPx < 0 || yPx < 0) {
            throw new LayoutValidationException("tile placement coordinates must be non-negative");
        }
        if (widthPx <= 0 || heightPx <= 0) {
            throw new LayoutValidationException("tile placement dimensions must be positive");
        }
    }
}
