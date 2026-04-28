package pro.alx4j.jab4j.api.model;

import java.util.Objects;

/**
 * Aggregated profile selection for a transfer session.
 *
 * @param profileId stable session profile identifier
 * @param layout layout profile
 * @param codec codec profile
 * @param transport transport profile
 * @param playback playback profile
 */
public record SessionProfile(
        String profileId,
        LayoutProfile layout,
        CodecProfile codec,
        TransportProfile transport,
        PlaybackProfile playback
) {

    /**
     * Creates a validated aggregate session profile.
     *
     * @param profileId stable session profile identifier
     * @param layout layout profile
     * @param codec codec profile
     * @param transport transport profile
     * @param playback playback profile
     */
    public SessionProfile {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        Objects.requireNonNull(layout, "layout must not be null");
        Objects.requireNonNull(codec, "codec must not be null");
        Objects.requireNonNull(transport, "transport must not be null");
        Objects.requireNonNull(playback, "playback must not be null");
    }
}
