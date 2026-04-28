package pro.alx4j.jab4j.tile;

import java.util.Objects;

/**
 * Profile parameters that define one supported logical tile subset.
 *
 * @param profileId stable profile identifier
 * @param payloadMode payload mode token
 * @param colorCount supported color count
 * @param quietZoneModules quiet-zone width in logical modules
 * @param minSideVersion minimum supported side version
 * @param maxSideVersion maximum supported side version
 * @param parityBytes parity-byte budget appended by the supported encode path
 * @param maskPatternCount number of candidate mask patterns
 * @param defaultMaskReference preferred mask reference used as the tie-break baseline
 */
public record TileCodecProfile(
        String profileId,
        String payloadMode,
        int colorCount,
        int quietZoneModules,
        int minSideVersion,
        int maxSideVersion,
        int parityBytes,
        int maskPatternCount,
        int defaultMaskReference
) {

    /**
     * Creates a validated tile codec profile.
     *
     * @param profileId stable profile identifier
     * @param payloadMode payload mode token
     * @param colorCount supported color count
     * @param quietZoneModules quiet-zone width in logical modules
     * @param minSideVersion minimum supported side version
     * @param maxSideVersion maximum supported side version
     * @param parityBytes parity-byte budget
     * @param maskPatternCount number of candidate mask patterns
     * @param defaultMaskReference preferred mask reference tie-break value
     */
    public TileCodecProfile {
        requireText(profileId, "profileId");
        requireText(payloadMode, "payloadMode");
        if (colorCount < 2 || Integer.bitCount(colorCount) != 1) {
            throw new TileCodecException("colorCount must be a power of two greater than one");
        }
        if (quietZoneModules < 0) {
            throw new TileCodecException("quietZoneModules must be non-negative");
        }
        if (minSideVersion <= 0) {
            throw new TileCodecException("minSideVersion must be positive");
        }
        if (maxSideVersion < minSideVersion) {
            throw new TileCodecException("maxSideVersion must be greater than or equal to minSideVersion");
        }
        if (parityBytes < 0) {
            throw new TileCodecException("parityBytes must be non-negative");
        }
        if (maskPatternCount <= 0) {
            throw new TileCodecException("maskPatternCount must be positive");
        }
        if (defaultMaskReference < 0 || defaultMaskReference >= maskPatternCount) {
            throw new TileCodecException("defaultMaskReference must identify one of the configured mask patterns");
        }
    }

    /**
     * Returns the logical symbol side length for a JAB-style side version.
     *
     * @param sideVersion supported side version
     * @return logical symbol width and height in modules
     */
    public int dimensionForSideVersion(int sideVersion) {
        if (sideVersion < minSideVersion || sideVersion > maxSideVersion) {
            throw new TileCodecException("Unsupported sideVersion " + sideVersion + " for profile " + profileId);
        }
        return (sideVersion * 4) + 17;
    }

    /**
     * Returns how many payload bits each logical module color can carry.
     *
     * @return payload bits per module
     */
    public int bitsPerModule() {
        return Integer.numberOfTrailingZeros(colorCount);
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new TileCodecException(field + " must not be blank");
        }
    }
}
