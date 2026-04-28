package pro.alx4j.jab4j.writer.config;

import java.util.Map;
import java.util.Set;

final class BuiltInProfiles {

    static final String DEFAULT_PROFILE_ID = "desktop-1080p-safe";

    private static final RuntimeConfigPatch DEFAULTS = new RuntimeConfigPatch(
            new RuntimeConfigPatch.AppPatch(
                    DEFAULT_PROFILE_ID,
                    Boolean.TRUE,
                    new RuntimeConfigPatch.ResourceLimitsPatch(
                            100_000,
                            10L * 1024L * 1024L * 1024L,
                            4 * 1024 * 1024,
                            100_000,
                            256
                    )
            ),
            new RuntimeConfigPatch.InputPatch(java.util.List.of()),
            new RuntimeConfigPatch.LayoutPatch(
                    RuntimeConfig.LayoutMode.FIXED.wireValue(),
                    DEFAULT_PROFILE_ID,
                    2,
                    2,
                    1920,
                    1080,
                    48,
                    24,
                    "solidWhite",
                    64,
                    32,
                    "black",
                    "preserveAspect"
            ),
            new RuntimeConfigPatch.CodecPatch("balanced-v1", "binary", Boolean.TRUE),
            new RuntimeConfigPatch.TransportPatch(1, 512, 4, 2, 10, 30, 60),
            new RuntimeConfigPatch.PlaybackPatch(8, 1, 6, 6, Boolean.TRUE),
            new RuntimeConfigPatch.ExportPatch(Boolean.FALSE, "none"),
            new RuntimeConfigPatch.DiagnosticsPatch(Boolean.FALSE, Boolean.TRUE, Boolean.TRUE)
    );

    private static final Map<String, RuntimeConfigPatch> PROFILES = Map.of(
            DEFAULT_PROFILE_ID, DEFAULTS,
            "desktop-1440p-balanced", new RuntimeConfigPatch(
                    new RuntimeConfigPatch.AppPatch("desktop-1440p-balanced", Boolean.TRUE, null),
                    null,
                    new RuntimeConfigPatch.LayoutPatch(
                            RuntimeConfig.LayoutMode.FIXED.wireValue(),
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
                    new RuntimeConfigPatch.CodecPatch("balanced-v1", "binary", Boolean.FALSE),
                    new RuntimeConfigPatch.TransportPatch(1, 512, 6, 2, 10, 30, 60),
                    new RuntimeConfigPatch.PlaybackPatch(10, 1, 6, 6, Boolean.TRUE),
                    null,
                    new RuntimeConfigPatch.DiagnosticsPatch(Boolean.FALSE, Boolean.TRUE, Boolean.TRUE)
            ),
            "debug-low-density", new RuntimeConfigPatch(
                    new RuntimeConfigPatch.AppPatch("debug-low-density", Boolean.TRUE, null),
                    null,
                    new RuntimeConfigPatch.LayoutPatch(
                            RuntimeConfig.LayoutMode.FIXED.wireValue(),
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
                    ),
                    new RuntimeConfigPatch.CodecPatch("balanced-v1", "binary", Boolean.TRUE),
                    new RuntimeConfigPatch.TransportPatch(1, 256, 2, 1, 8, 24, 48),
                    new RuntimeConfigPatch.PlaybackPatch(4, 1, 4, 4, Boolean.TRUE),
                    null,
                    new RuntimeConfigPatch.DiagnosticsPatch(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE)
            )
    );

    private static final Set<String> SUPPORTED_LAYOUT_PROFILE_IDS = Set.of(
            DEFAULT_PROFILE_ID,
            "desktop-1440p-balanced",
            "debug-low-density"
    );

    private static final Set<String> SUPPORTED_CODEC_PROFILE_IDS = Set.of("balanced-v1");

    RuntimeConfigPatch defaults() {
        return DEFAULTS;
    }

    String defaultProfileId() {
        return DEFAULT_PROFILE_ID;
    }

    RuntimeConfigPatch profile(String profileId) {
        RuntimeConfigPatch patch = PROFILES.get(profileId);
        if (patch == null) {
            throw new ConfigValidationException("Unsupported app.profile: " + profileId);
        }
        return patch;
    }

    boolean supportsLayoutProfile(String profileId) {
        return SUPPORTED_LAYOUT_PROFILE_IDS.contains(profileId);
    }

    boolean supportsCodecProfile(String profileId) {
        return SUPPORTED_CODEC_PROFILE_IDS.contains(profileId);
    }
}
