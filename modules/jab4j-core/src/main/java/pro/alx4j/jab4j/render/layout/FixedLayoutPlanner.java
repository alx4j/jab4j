package pro.alx4j.jab4j.render.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.alx4j.jab4j.api.model.LayoutProfile;

/**
 * Builds deterministic fixed-layout geometry and enforces conservative milestone-one layout safety rules.
 */
public final class FixedLayoutPlanner {

    static final int MIN_TILE_SLOT_WIDTH_PX = 64;
    static final int MIN_TILE_SLOT_HEIGHT_PX = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedLayoutPlanner.class);

    /**
     * Resolves a fixed layout profile into renderable tile-slot geometry.
     *
     * @param profile layout profile
     * @return deterministic fixed-layout plan
     */
    public FixedLayoutPlan plan(LayoutProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        try {
            validateSupportedStyles(profile);

            int usableGridWidth = profile.frameWidthPx()
                    - (2 * profile.outerMarginPx())
                    - ((profile.cols() - 1) * profile.tileGapPx());
            int usableGridHeight = profile.frameHeightPx()
                    - (2 * profile.outerMarginPx())
                    - profile.topSyncBandPx()
                    - profile.metadataBandPx()
                    - ((profile.rows() - 1) * profile.tileGapPx());
            if (usableGridWidth <= 0 || usableGridHeight <= 0) {
                throw new LayoutValidationException("layout usable tile region must remain positive");
            }

            int tileSlotWidth = usableGridWidth / profile.cols();
            int tileSlotHeight = usableGridHeight / profile.rows();
            if (tileSlotWidth < MIN_TILE_SLOT_WIDTH_PX || tileSlotHeight < MIN_TILE_SLOT_HEIGHT_PX) {
                throw new LayoutValidationException("layout tile slots must remain at least "
                        + MIN_TILE_SLOT_WIDTH_PX + "x" + MIN_TILE_SLOT_HEIGHT_PX + " pixels");
            }

            int remainingWidth = usableGridWidth - (tileSlotWidth * profile.cols());
            int remainingHeight = usableGridHeight - (tileSlotHeight * profile.rows());
            int gridOriginX = profile.outerMarginPx() + (remainingWidth / 2);
            int gridOriginY = profile.outerMarginPx() + profile.topSyncBandPx() + profile.metadataBandPx() + (remainingHeight / 2);
            int separatorThickness = Math.max(1, profile.tileGapPx() / 2);

            List<TilePlacement> placements = new ArrayList<>(profile.rows() * profile.cols());
            for (int row = 0; row < profile.rows(); row++) {
                for (int col = 0; col < profile.cols(); col++) {
                    int x = gridOriginX + (col * (tileSlotWidth + profile.tileGapPx()));
                    int y = gridOriginY + (row * (tileSlotHeight + profile.tileGapPx()));
                    if ((x + tileSlotWidth) > profile.frameWidthPx() || (y + tileSlotHeight) > profile.frameHeightPx()) {
                        throw new LayoutValidationException("layout tile placements must remain inside the frame");
                    }
                    placements.add(new TilePlacement(row, col, x, y, tileSlotWidth, tileSlotHeight));
                }
            }

            return new FixedLayoutPlan(
                    profile,
                    gridOriginX,
                    gridOriginY,
                    usableGridWidth,
                    usableGridHeight,
                    tileSlotWidth,
                    tileSlotHeight,
                    separatorThickness,
                    placements
            );
        } catch (LayoutValidationException exception) {
            LOGGER.warn(
                    "Rejected layout profile {} (grid={}x{}, frame={}x{}, gapPx={}, marginPx={}, topSyncBandPx={}, metadataBandPx={}): {}",
                    profile.profileId(),
                    profile.rows(),
                    profile.cols(),
                    profile.frameWidthPx(),
                    profile.frameHeightPx(),
                    profile.tileGapPx(),
                    profile.outerMarginPx(),
                    profile.topSyncBandPx(),
                    profile.metadataBandPx(),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private void validateSupportedStyles(LayoutProfile profile) {
        if (!"solidWhite".equals(profile.separatorStyle())) {
            throw new LayoutValidationException("Unsupported separatorStyle: " + profile.separatorStyle());
        }
        if (!"black".equals(profile.backgroundStyle())) {
            throw new LayoutValidationException("Unsupported backgroundStyle: " + profile.backgroundStyle());
        }
        if (!"preserveAspect".equals(profile.fitPolicy())) {
            throw new LayoutValidationException("Unsupported fitPolicy: " + profile.fitPolicy());
        }
    }
}
