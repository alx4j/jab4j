package pro.alx4j.jab4j.tile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pixel-independent logical tile produced by the codec.
 *
 * @param widthModules logical width in modules
 * @param heightModules logical height in modules
 * @param quietZoneModules quiet-zone width outside the logical matrix
 * @param profileId stable codec profile identifier
 * @param moduleColors row-major per-module color values
 * @param diagnostics deterministic diagnostic metadata for tests and fixtures
 */
public record LogicalTile(
        int widthModules,
        int heightModules,
        int quietZoneModules,
        String profileId,
        List<Integer> moduleColors,
        Map<String, String> diagnostics
) {

    /**
     * Creates a validated logical tile.
     *
     * @param widthModules logical width in modules
     * @param heightModules logical height in modules
     * @param quietZoneModules quiet-zone width
     * @param profileId stable codec profile identifier
     * @param moduleColors row-major per-module colors
     * @param diagnostics deterministic diagnostic metadata
     */
    public LogicalTile {
        if (widthModules <= 0) {
            throw new TileCodecException("widthModules must be positive");
        }
        if (heightModules <= 0) {
            throw new TileCodecException("heightModules must be positive");
        }
        if (quietZoneModules < 0) {
            throw new TileCodecException("quietZoneModules must be non-negative");
        }
        requireText(profileId, "profileId");
        moduleColors = List.copyOf(Objects.requireNonNull(moduleColors, "moduleColors must not be null"));
        if (moduleColors.size() != widthModules * heightModules) {
            throw new TileCodecException("moduleColors size must equal widthModules * heightModules");
        }
        for (Integer moduleColor : moduleColors) {
            if (moduleColor == null || moduleColor < 0) {
                throw new TileCodecException("moduleColors must contain non-null non-negative values");
            }
        }
        diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(diagnostics, "diagnostics must not be null")));
        diagnostics.forEach(LogicalTile::requireDiagnosticEntry);
    }

    /**
     * Returns the color value for the given logical module coordinate.
     *
     * @param row zero-based row index
     * @param col zero-based column index
     * @return row-major module color value
     */
    public int moduleColorAt(int row, int col) {
        if (row < 0 || row >= heightModules) {
            throw new TileCodecException("row out of bounds: " + row);
        }
        if (col < 0 || col >= widthModules) {
            throw new TileCodecException("col out of bounds: " + col);
        }
        return moduleColors.get((row * widthModules) + col);
    }

    private static void requireDiagnosticEntry(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new TileCodecException("diagnostics must contain non-blank keys and values");
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new TileCodecException(field + " must not be blank");
        }
    }
}
