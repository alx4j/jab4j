package com.alx4j.jab4j.tile;

import com.alx4j.jab4j.tile.internal.SupportedTileCodecProfiles;

/**
 * Public access point for the supported tile codec profiles exposed by the runtime library.
 */
public final class TileCodecProfiles {

    private TileCodecProfiles() {
    }

    /**
     * Returns the supported balanced milestone-one profile.
     *
     * @return supported codec profile
     */
    public static TileCodecProfile balancedV1() {
        return SupportedTileCodecProfiles.balancedV1();
    }

    /**
     * Resolves a supported profile by id.
     *
     * @param profileId profile identifier
     * @return supported codec profile
     */
    public static TileCodecProfile resolve(String profileId) {
        return SupportedTileCodecProfiles.resolve(profileId);
    }

    /**
     * Returns the maximum raw payload size, in bytes, that the supported encoder can accept.
     *
     * @param profile supported codec profile
     * @return maximum payload size before deterministic framing and parity are applied
     */
    public static int maxPayloadBytes(TileCodecProfile profile) {
        return SupportedTileCodecProfiles.maxPayloadBytes(profile);
    }

    /**
     * Returns the immutable codec profile hash recorded for the current codec support set.
     *
     * @return normalized codec profile hash
     */
    public static String codecProfileHash() {
        return SupportedTileCodecProfiles.CODEC_PROFILE_HASH;
    }
}
