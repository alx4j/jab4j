package com.alx4j.jab4j.api.model;

/**
 * Supported codec profile metadata.
 *
 * @param profileId stable profile identifier
 * @param payloadMode payload mode identifier
 * @param conservativeDefaults whether the profile favors conservative settings
 */
public record CodecProfile(String profileId, String payloadMode, boolean conservativeDefaults) {

    /**
     * Creates a validated codec profile.
     *
     * @param profileId stable profile identifier
     * @param payloadMode payload mode identifier
     * @param conservativeDefaults conservative-defaults flag
     */
    public CodecProfile {
        requireText(profileId, "profileId");
        requireText(payloadMode, "payloadMode");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
