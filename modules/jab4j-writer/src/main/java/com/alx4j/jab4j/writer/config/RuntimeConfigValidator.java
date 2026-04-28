package com.alx4j.jab4j.writer.config;

import java.util.Objects;

/**
 * Validates resolved runtime configuration against mandatory architectural constraints.
 */
public final class RuntimeConfigValidator {

    static final int MIN_FRAME_WIDTH_PX = 320;
    static final int MIN_FRAME_HEIGHT_PX = 240;

    private final BuiltInProfiles builtInProfiles;

    /**
     * Creates a validator with the built-in profile catalog.
     */
    public RuntimeConfigValidator() {
        this(new BuiltInProfiles());
    }

    RuntimeConfigValidator(BuiltInProfiles builtInProfiles) {
        this.builtInProfiles = Objects.requireNonNull(builtInProfiles, "builtInProfiles must not be null");
    }

    /**
     * Validates a resolved config.
     *
     * @param config resolved config
     */
    public void validate(RuntimeConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        if (config.layout().mode() != RuntimeConfig.LayoutMode.FIXED) {
            throw new ConfigValidationException("layout.mode autoFit is recognized but not yet supported");
        }
        if (config.layout().frameWidthPx() < MIN_FRAME_WIDTH_PX) {
            throw new ConfigValidationException("layout.frameWidthPx must be at least " + MIN_FRAME_WIDTH_PX);
        }
        if (config.layout().frameHeightPx() < MIN_FRAME_HEIGHT_PX) {
            throw new ConfigValidationException("layout.frameHeightPx must be at least " + MIN_FRAME_HEIGHT_PX);
        }
        if (config.layout().tileGapPx() < 1) {
            throw new ConfigValidationException("layout.tileGapPx must be at least 1 to provide explicit separators");
        }
        if (!builtInProfiles.supportsLayoutProfile(config.layout().profileId())) {
            throw new ConfigValidationException("Unsupported layout.profileId: " + config.layout().profileId());
        }
        if (!builtInProfiles.supportsCodecProfile(config.codec().profileId())) {
            throw new ConfigValidationException("Unsupported codec.profileId: " + config.codec().profileId());
        }

        int usableWidth = config.layout().frameWidthPx()
                - (2 * config.layout().outerMarginPx())
                - ((config.layout().cols() - 1) * config.layout().tileGapPx());
        int usableHeight = config.layout().frameHeightPx()
                - (2 * config.layout().outerMarginPx())
                - config.layout().topSyncBandPx()
                - config.layout().metadataBandPx()
                - ((config.layout().rows() - 1) * config.layout().tileGapPx());

        if (usableWidth <= 0 || usableHeight <= 0) {
            throw new ConfigValidationException("layout usable tile render region must remain positive");
        }
    }
}
