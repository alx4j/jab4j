package pro.alx4j.jab4j.api.model;

/**
 * Profile-driven layout definition for frame rendering.
 *
 * @param profileId stable layout profile identifier
 * @param rows number of tile rows
 * @param cols number of tile columns
 * @param frameWidthPx full frame width in pixels
 * @param frameHeightPx full frame height in pixels
 * @param tileGapPx pixel gap between tiles
 * @param outerMarginPx outer frame margin in pixels
 * @param separatorStyle separator style identifier
 * @param topSyncBandPx top sync-band height in pixels
 * @param metadataBandPx metadata-band height in pixels
 * @param backgroundStyle background style identifier
 * @param fitPolicy layout fit-policy identifier
 */
public record LayoutProfile(
        String profileId,
        int rows,
        int cols,
        int frameWidthPx,
        int frameHeightPx,
        int tileGapPx,
        int outerMarginPx,
        String separatorStyle,
        int topSyncBandPx,
        int metadataBandPx,
        String backgroundStyle,
        String fitPolicy
) {

    /**
     * Creates a validated layout profile.
     *
     * @param profileId stable layout profile identifier
     * @param rows number of tile rows
     * @param cols number of tile columns
     * @param frameWidthPx frame width in pixels
     * @param frameHeightPx frame height in pixels
     * @param tileGapPx tile gap in pixels
     * @param outerMarginPx outer margin in pixels
     * @param separatorStyle separator style identifier
     * @param topSyncBandPx sync-band height in pixels
     * @param metadataBandPx metadata-band height in pixels
     * @param backgroundStyle background style identifier
     * @param fitPolicy fit-policy identifier
     */
    public LayoutProfile {
        requireText(profileId, "profileId");
        requireText(separatorStyle, "separatorStyle");
        requireText(backgroundStyle, "backgroundStyle");
        requireText(fitPolicy, "fitPolicy");
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("rows and cols must be positive");
        }
        if (frameWidthPx <= 0 || frameHeightPx <= 0) {
            throw new IllegalArgumentException("frame dimensions must be positive");
        }
        if (tileGapPx < 0 || outerMarginPx < 0 || topSyncBandPx < 0 || metadataBandPx < 0) {
            throw new IllegalArgumentException("layout pixel measurements must be non-negative");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
