package com.alx4j.jab4j.render.layout;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;

/**
 * Deterministic fixed-layout geometry derived from one {@link LayoutProfile}.
 *
 * @param profile source layout profile
 * @param gridOriginXPx left pixel coordinate of the tile grid
 * @param gridOriginYPx top pixel coordinate of the tile grid
 * @param usableGridWidthPx usable grid width in pixels
 * @param usableGridHeightPx usable grid height in pixels
 * @param tileSlotWidthPx slot width for each tile
 * @param tileSlotHeightPx slot height for each tile
 * @param separatorThicknessPx separator or border thickness used by baseline rendering
 * @param tilePlacements row-major tile placements
 */
public record FixedLayoutPlan(
        LayoutProfile profile,
        int gridOriginXPx,
        int gridOriginYPx,
        int usableGridWidthPx,
        int usableGridHeightPx,
        int tileSlotWidthPx,
        int tileSlotHeightPx,
        int separatorThicknessPx,
        List<TilePlacement> tilePlacements
) {

    /**
     * Creates a validated fixed-layout plan.
     *
     * @param profile source layout profile
     * @param gridOriginXPx left grid coordinate
     * @param gridOriginYPx top grid coordinate
     * @param usableGridWidthPx usable grid width
     * @param usableGridHeightPx usable grid height
     * @param tileSlotWidthPx tile slot width
     * @param tileSlotHeightPx tile slot height
     * @param separatorThicknessPx separator thickness
     * @param tilePlacements row-major tile placements
     */
    public FixedLayoutPlan {
        Objects.requireNonNull(profile, "profile must not be null");
        if (gridOriginXPx < 0 || gridOriginYPx < 0) {
            throw new LayoutValidationException("grid origins must be non-negative");
        }
        if (usableGridWidthPx <= 0 || usableGridHeightPx <= 0) {
            throw new LayoutValidationException("usable grid dimensions must be positive");
        }
        if (tileSlotWidthPx <= 0 || tileSlotHeightPx <= 0) {
            throw new LayoutValidationException("tile slot dimensions must be positive");
        }
        if (separatorThicknessPx <= 0) {
            throw new LayoutValidationException("separatorThicknessPx must be positive");
        }
        tilePlacements = List.copyOf(Objects.requireNonNull(tilePlacements, "tilePlacements must not be null"));
        if (tilePlacements.size() != profile.rows() * profile.cols()) {
            throw new LayoutValidationException("tilePlacements must cover the full grid");
        }
    }
}
