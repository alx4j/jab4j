package com.alx4j.jab4j.tile.internal;

import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;

/**
 * Registry for the codec profiles supported by the milestone-one deterministic subset.
 */
public final class SupportedTileCodecProfiles {

    private static final int FINDER_COUNT = 4;
    private static final int FINDER_SIZE = 3;

    /**
     * Stable hash for the deterministic codec profile constants used by reproducibility metadata.
     */
    public static final String CODEC_PROFILE_HASH = "c39c547cf8d855082c55e51c075e88c44ea7fff0c2002d281d137307e73fe288";

    static final int DEFAULT_COLOR_NUMBER = 8;
    static final int DEFAULT_MASKING_REFERENCE = 7;
    static final int NUMBER_OF_MASK_PATTERNS = 8;
    static final int INTERLEAVE_SEED = 226_759;
    static final int LDPC_MESSAGE_SEED = 785_465;
    static final int HEADER_BYTES = 7;

    private static final TileCodecProfile BALANCED_V1 = new TileCodecProfile(
            "balanced-v1",
            "binary",
            DEFAULT_COLOR_NUMBER,
            1,
            1,
            8,
            8,
            NUMBER_OF_MASK_PATTERNS,
            DEFAULT_MASKING_REFERENCE
    );

    private SupportedTileCodecProfiles() {
    }

    /**
     * Returns the supported balanced milestone-one profile.
     *
     * @return supported codec profile
     */
    public static TileCodecProfile balancedV1() {
        return BALANCED_V1;
    }

    /**
     * Resolves a supported profile by id.
     *
     * @param profileId profile identifier
     * @return supported codec profile
     */
    public static TileCodecProfile resolve(String profileId) {
        if (BALANCED_V1.profileId().equals(profileId)) {
            return BALANCED_V1;
        }
        throw new TileCodecException("Unsupported tile codec profile: " + profileId);
    }

    /**
     * Returns the maximum raw payload size, in bytes, that the supported encoder can accept for the supplied profile.
     *
     * @param profile supported codec profile
     * @return maximum payload size before deterministic framing and parity are applied
     */
    public static int maxPayloadBytes(TileCodecProfile profile) {
        TileCodecProfile supportedProfile = resolve(profile.profileId());
        if (!supportedProfile.equals(profile)) {
            throw new TileCodecException("Unsupported profile parameters for profile " + profile.profileId());
        }

        int maxDimension = supportedProfile.dimensionForSideVersion(supportedProfile.maxSideVersion());
        int reservedModules = FINDER_COUNT * FINDER_SIZE * FINDER_SIZE;
        int dataModules = (maxDimension * maxDimension) - reservedModules;
        int maxEncodedBytes = (dataModules * supportedProfile.bitsPerModule()) / Byte.SIZE;
        return maxEncodedBytes - HEADER_BYTES - supportedProfile.parityBytes();
    }
}
