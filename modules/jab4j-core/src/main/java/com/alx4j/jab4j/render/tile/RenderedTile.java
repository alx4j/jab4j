package com.alx4j.jab4j.render.tile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.alx4j.jab4j.render.internal.ArgbPixelList;
import com.alx4j.jab4j.tile.TileCodecException;

/**
 * Rasterized tile output kept separate from logical tile encoding.
 *
 * @param widthPixels rendered width in pixels
 * @param heightPixels rendered height in pixels
 * @param argbPixels row-major ARGB pixels
 * @param paletteArgb palette metadata for the rendered tile
 * @param diagnostics deterministic rendering diagnostics
 */
public record RenderedTile(
        int widthPixels,
        int heightPixels,
        List<Integer> argbPixels,
        List<Integer> paletteArgb,
        Map<String, String> diagnostics
) {

    /**
     * Creates a validated rendered tile.
     *
     * @param widthPixels rendered width in pixels
     * @param heightPixels rendered height in pixels
     * @param argbPixels row-major ARGB pixels
     * @param paletteArgb palette metadata
     * @param diagnostics deterministic rendering diagnostics
     */
    public RenderedTile {
        if (widthPixels <= 0) {
            throw new TileCodecException("widthPixels must be positive");
        }
        if (heightPixels <= 0) {
            throw new TileCodecException("heightPixels must be positive");
        }
        argbPixels = ArgbPixelList.copyOf(Objects.requireNonNull(argbPixels, "argbPixels must not be null"));
        if (argbPixels.size() != widthPixels * heightPixels) {
            throw new TileCodecException("argbPixels size must equal widthPixels * heightPixels");
        }
        paletteArgb = List.copyOf(Objects.requireNonNull(paletteArgb, "paletteArgb must not be null"));
        diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(diagnostics, "diagnostics must not be null")));
    }
}
