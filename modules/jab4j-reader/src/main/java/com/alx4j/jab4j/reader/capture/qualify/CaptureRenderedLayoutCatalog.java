package com.alx4j.jab4j.reader.capture.qualify;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;

/**
 * Capture-owned catalog of rendered frame layouts supported by MVP-2 exact PNG sampling.
 */
public final class CaptureRenderedLayoutCatalog {

    private final List<LayoutProfile> profiles;

    /**
     * Creates the catalog for current writer-rendered fixed-layout profiles.
     */
    public CaptureRenderedLayoutCatalog() {
        this(List.of(
                new LayoutProfile(
                        "desktop-1080p-safe",
                        2,
                        2,
                        1920,
                        1080,
                        24,
                        48,
                        "solidWhite",
                        64,
                        32,
                        "black",
                        "preserveAspect"
                ),
                new LayoutProfile(
                        "desktop-1440p-balanced",
                        2,
                        3,
                        2560,
                        1440,
                        20,
                        56,
                        "solidWhite",
                        72,
                        36,
                        "black",
                        "preserveAspect"
                ),
                new LayoutProfile(
                        "debug-low-density",
                        1,
                        2,
                        1280,
                        720,
                        16,
                        40,
                        "solidWhite",
                        48,
                        24,
                        "black",
                        "preserveAspect"
                )
        ));
    }

    /**
     * Creates a catalog with explicit profiles.
     *
     * @param profiles supported layout profiles
     */
    public CaptureRenderedLayoutCatalog(List<LayoutProfile> profiles) {
        this.profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles must not be null"));
        if (this.profiles.isEmpty()) {
            throw new IllegalArgumentException("profiles must not be empty");
        }
        if (this.profiles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("profiles must not contain null values");
        }
    }

    /**
     * Resolves a supported rendered layout by exact frame dimensions.
     *
     * @param widthPixels frame width in pixels
     * @param heightPixels frame height in pixels
     * @return matching layout profile, or empty when unsupported or ambiguous
     */
    public Optional<LayoutProfile> resolve(int widthPixels, int heightPixels) {
        List<LayoutProfile> matches = profiles.stream()
                .filter(profile -> profile.frameWidthPx() == widthPixels && profile.frameHeightPx() == heightPixels)
                .toList();
        if (matches.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }
}
