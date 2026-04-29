package com.alx4j.jab4j.reader.app;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;

/**
 * Reader-owned catalog of writer-rendered fixed layouts supported by exact PNG frame decoding.
 */
final class SupportedRenderedLayoutCatalog {

    private final List<LayoutProfile> profiles;

    /**
     * Creates the catalog for the current writer-supported fixed-layout profiles.
     */
    SupportedRenderedLayoutCatalog() {
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
                        56,
                        20,
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
                        40,
                        16,
                        "solidWhite",
                        48,
                        24,
                        "black",
                        "preserveAspect"
                )
        ));
    }

    /**
     * Creates a catalog with explicit profiles, primarily for focused ambiguity tests.
     *
     * @param profiles supported layout profiles
     */
    SupportedRenderedLayoutCatalog(List<LayoutProfile> profiles) {
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
     * @return matching layout profile
     */
    LayoutProfile resolve(int widthPixels, int heightPixels) {
        List<LayoutProfile> matches = profiles.stream()
                .filter(profile -> profile.frameWidthPx() == widthPixels && profile.frameHeightPx() == heightPixels)
                .toList();
        if (matches.isEmpty()) {
            throw new ReaderContentDecodeException(
                    ReaderDecodeStatus.UNSUPPORTED_LAYOUT,
                    "Unsupported rendered frame dimensions: " + widthPixels + "x" + heightPixels
            );
        }
        if (matches.size() > 1) {
            throw new ReaderContentDecodeException(
                    ReaderDecodeStatus.UNSUPPORTED_LAYOUT,
                    "Ambiguous rendered frame dimensions "
                            + widthPixels
                            + "x"
                            + heightPixels
                            + " match "
                            + matches.size()
                            + " supported layout profiles"
            );
        }
        return matches.get(0);
    }
}
