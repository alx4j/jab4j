package pro.alx4j.jab4j.writer.config;

import java.util.List;
import java.util.Objects;

/**
 * Fully resolved runtime configuration after defaults, profile values, file settings, and overrides are merged.
 *
 * @param app application-level configuration and resource limits
 * @param input input root configuration
 * @param layout frame layout configuration
 * @param codec tile codec configuration
 * @param transport transport cadence and chunking configuration
 * @param playback deterministic playback configuration
 * @param export export configuration
 * @param diagnostics diagnostics and artifact controls
 */
public record RuntimeConfig(
        AppConfig app,
        InputConfig input,
        LayoutConfig layout,
        CodecConfig codec,
        TransportConfig transport,
        PlaybackConfig playback,
        ExportConfig export,
        DiagnosticsConfig diagnostics
) {

    /**
     * Creates a validated resolved runtime configuration.
     *
     * @param app application-level config
     * @param input input config
     * @param layout layout config
     * @param codec codec config
     * @param transport transport config
     * @param playback playback config
     * @param export export config
     * @param diagnostics diagnostics config
     */
    public RuntimeConfig {
        Objects.requireNonNull(app, "app must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(layout, "layout must not be null");
        Objects.requireNonNull(codec, "codec must not be null");
        Objects.requireNonNull(transport, "transport must not be null");
        Objects.requireNonNull(playback, "playback must not be null");
        Objects.requireNonNull(export, "export must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    /**
     * Application-level runtime configuration.
     *
     * @param profile selected built-in profile id
     * @param strictValidation whether strict validation should remain enabled
     * @param resourceLimits resource-limit definitions for later runtime enforcement
     */
    public record AppConfig(
            String profile,
            boolean strictValidation,
            ResourceLimitsConfig resourceLimits
    ) {

        /**
         * Creates a validated application config.
         *
         * @param profile selected built-in profile id
         * @param strictValidation strict-validation flag
         * @param resourceLimits resource-limit definitions
         */
        public AppConfig {
            requireText(profile, "profile");
            Objects.requireNonNull(resourceLimits, "resourceLimits must not be null");
        }
    }

    /**
     * Resource-limit definitions that later runtime tasks will enforce.
     *
     * @param maxFileCount maximum file count per run
     * @param maxTotalBytes maximum total input bytes per run
     * @param maxManifestBytes maximum manifest bytes per run
     * @param maxFrameCount maximum frame count per run
     * @param maxInMemoryBuffers maximum buffered frame or payload structures in memory
     */
    public record ResourceLimitsConfig(
            int maxFileCount,
            long maxTotalBytes,
            int maxManifestBytes,
            int maxFrameCount,
            int maxInMemoryBuffers
    ) {

        /**
         * Creates validated resource-limit definitions.
         *
         * @param maxFileCount maximum file count
         * @param maxTotalBytes maximum total bytes
         * @param maxManifestBytes maximum manifest bytes
         * @param maxFrameCount maximum frame count
         * @param maxInMemoryBuffers maximum in-memory buffers
         */
        public ResourceLimitsConfig {
            if (maxFileCount <= 0) {
                throw new ConfigValidationException("app.resourceLimits.maxFileCount must be positive");
            }
            if (maxTotalBytes <= 0) {
                throw new ConfigValidationException("app.resourceLimits.maxTotalBytes must be positive");
            }
            if (maxManifestBytes <= 0) {
                throw new ConfigValidationException("app.resourceLimits.maxManifestBytes must be positive");
            }
            if (maxFrameCount <= 0) {
                throw new ConfigValidationException("app.resourceLimits.maxFrameCount must be positive");
            }
            if (maxInMemoryBuffers <= 0) {
                throw new ConfigValidationException("app.resourceLimits.maxInMemoryBuffers must be positive");
            }
        }
    }

    /**
     * Input-root configuration for a run.
     *
     * @param roots ordered input roots
     */
    public record InputConfig(List<InputRootConfig> roots) {

        /**
         * Creates a validated input-root configuration.
         *
         * @param roots ordered input roots
         */
        public InputConfig {
            roots = List.copyOf(Objects.requireNonNullElse(roots, List.of()));
            if (roots.stream().anyMatch(Objects::isNull)) {
                throw new ConfigValidationException("input.roots must not contain null entries");
            }
        }
    }

    /**
     * One configured input root.
     *
     * @param path filesystem path string
     * @param alias optional stable alias
     */
    public record InputRootConfig(String path, String alias) {

        /**
         * Creates a validated input root.
         *
         * @param path filesystem path string
         * @param alias optional stable alias
         */
        public InputRootConfig {
            requireText(path, "path");
            if (alias != null && alias.isBlank()) {
                throw new ConfigValidationException("input.roots.alias must not be blank when provided");
            }
        }
    }

    /**
     * Supported layout modes.
     */
    public enum LayoutMode {
        FIXED("fixed"),
        AUTO_FIT("autoFit");

        private final String wireValue;

        LayoutMode(String wireValue) {
            this.wireValue = wireValue;
        }

        /**
         * Returns the wire-format value for config files and artifacts.
         *
         * @return external config token
         */
        public String wireValue() {
            return wireValue;
        }

        /**
         * Resolves a config token into a known layout mode.
         *
         * @param value external config token
         * @return parsed layout mode
         */
        public static LayoutMode fromValue(String value) {
            for (LayoutMode mode : values()) {
                if (mode.wireValue.equals(value)) {
                    return mode;
                }
            }
            throw new ConfigValidationException("layout.mode must be one of: fixed, autoFit");
        }
    }

    /**
     * Layout configuration for frame composition.
     *
     * @param mode configured layout mode
     * @param profileId layout profile id
     * @param rows number of tile rows
     * @param cols number of tile columns
     * @param frameWidthPx frame width in pixels
     * @param frameHeightPx frame height in pixels
     * @param outerMarginPx outer margin in pixels
     * @param tileGapPx tile gap in pixels
     * @param separatorStyle separator style identifier
     * @param topSyncBandPx sync-band height in pixels
     * @param metadataBandPx metadata-band height in pixels
     * @param backgroundStyle background style identifier
     * @param fitPolicy fit policy identifier
     */
    public record LayoutConfig(
            LayoutMode mode,
            String profileId,
            int rows,
            int cols,
            int frameWidthPx,
            int frameHeightPx,
            int outerMarginPx,
            int tileGapPx,
            String separatorStyle,
            int topSyncBandPx,
            int metadataBandPx,
            String backgroundStyle,
            String fitPolicy
    ) {

        /**
         * Creates a validated layout config.
         *
         * @param mode configured layout mode
         * @param profileId layout profile id
         * @param rows number of tile rows
         * @param cols number of tile columns
         * @param frameWidthPx frame width in pixels
         * @param frameHeightPx frame height in pixels
         * @param outerMarginPx outer margin in pixels
         * @param tileGapPx tile gap in pixels
         * @param separatorStyle separator style identifier
         * @param topSyncBandPx sync-band height in pixels
         * @param metadataBandPx metadata-band height in pixels
         * @param backgroundStyle background style identifier
         * @param fitPolicy fit policy identifier
         */
        public LayoutConfig {
            Objects.requireNonNull(mode, "mode must not be null");
            requireText(profileId, "profileId");
            requireText(separatorStyle, "separatorStyle");
            requireText(backgroundStyle, "backgroundStyle");
            requireText(fitPolicy, "fitPolicy");
            if (rows <= 0) {
                throw new ConfigValidationException("layout.rows must be positive");
            }
            if (cols <= 0) {
                throw new ConfigValidationException("layout.cols must be positive");
            }
            if (frameWidthPx <= 0) {
                throw new ConfigValidationException("layout.frameWidthPx must be positive");
            }
            if (frameHeightPx <= 0) {
                throw new ConfigValidationException("layout.frameHeightPx must be positive");
            }
            if (outerMarginPx < 0 || tileGapPx < 0 || topSyncBandPx < 0 || metadataBandPx < 0) {
                throw new ConfigValidationException("layout pixel measurements must be non-negative");
            }
        }
    }

    /**
     * Codec configuration for logical tile generation.
     *
     * @param profileId codec profile id
     * @param payloadMode payload mode identifier
     * @param conservativeDefaults whether conservative codec defaults remain enabled
     */
    public record CodecConfig(
            String profileId,
            String payloadMode,
            boolean conservativeDefaults
    ) {

        /**
         * Creates a validated codec config.
         *
         * @param profileId codec profile id
         * @param payloadMode payload mode identifier
         * @param conservativeDefaults conservative-defaults flag
         */
        public CodecConfig {
            requireText(profileId, "profileId");
            requireText(payloadMode, "payloadMode");
        }
    }

    /**
     * Transport cadence and chunking configuration.
     *
     * @param protocolVersion protocol compatibility version
     * @param chunkBytes configured chunk size
     * @param dataShardsPerGroup data shard count per parity group
     * @param parityShardsPerGroup parity shard count per parity group
     * @param syncEveryFrames sync-frame cadence
     * @param sessionHeaderRepeatEveryFrames session-header replay cadence
     * @param manifestRepeatEveryFrames manifest replay cadence
     */
    public record TransportConfig(
            int protocolVersion,
            int chunkBytes,
            int dataShardsPerGroup,
            int parityShardsPerGroup,
            int syncEveryFrames,
            int sessionHeaderRepeatEveryFrames,
            int manifestRepeatEveryFrames
    ) {

        /**
         * Creates a validated transport config.
         *
         * @param protocolVersion protocol compatibility version
         * @param chunkBytes configured chunk size
         * @param dataShardsPerGroup data shard count per parity group
         * @param parityShardsPerGroup parity shard count per parity group
         * @param syncEveryFrames sync-frame cadence
         * @param sessionHeaderRepeatEveryFrames session-header replay cadence
         * @param manifestRepeatEveryFrames manifest replay cadence
         */
        public TransportConfig {
            if (protocolVersion <= 0) {
                throw new ConfigValidationException("transport.protocolVersion must be positive");
            }
            if (chunkBytes <= 0) {
                throw new ConfigValidationException("transport.chunkBytes must be positive");
            }
            if (dataShardsPerGroup <= 0) {
                throw new ConfigValidationException("transport.dataShardsPerGroup must be positive");
            }
            if (parityShardsPerGroup < 0) {
                throw new ConfigValidationException("transport.parityShardsPerGroup must be non-negative");
            }
            if (syncEveryFrames <= 0) {
                throw new ConfigValidationException("transport.syncEveryFrames must be positive");
            }
            if (sessionHeaderRepeatEveryFrames <= 0) {
                throw new ConfigValidationException("transport.sessionHeaderRepeatEveryFrames must be positive");
            }
            if (manifestRepeatEveryFrames <= 0) {
                throw new ConfigValidationException("transport.manifestRepeatEveryFrames must be positive");
            }
        }
    }

    /**
     * Deterministic playback configuration.
     *
     * @param fps frames per second
     * @param holdFrames hold count per logical frame
     * @param warmupSyncFrames sync frames shown before payload frames
     * @param endFrames terminal frames appended at session end
     * @param fullscreen whether fullscreen playback is requested
     */
    public record PlaybackConfig(
            int fps,
            int holdFrames,
            int warmupSyncFrames,
            int endFrames,
            boolean fullscreen
    ) {

        /**
         * Creates a validated playback config.
         *
         * @param fps frames per second
         * @param holdFrames hold count per logical frame
         * @param warmupSyncFrames sync frames shown before payload frames
         * @param endFrames terminal frames appended at session end
         * @param fullscreen whether fullscreen playback is requested
         */
        public PlaybackConfig {
            if (fps <= 0) {
                throw new ConfigValidationException("playback.fps must be positive");
            }
            if (holdFrames < 0) {
                throw new ConfigValidationException("playback.holdFrames must be non-negative");
            }
            if (warmupSyncFrames < 0) {
                throw new ConfigValidationException("playback.warmupSyncFrames must be non-negative");
            }
            if (endFrames < 0) {
                throw new ConfigValidationException("playback.endFrames must be non-negative");
            }
        }
    }

    /**
     * Export configuration.
     *
     * @param enabled whether export is enabled
     * @param mode export mode identifier
     */
    public record ExportConfig(boolean enabled, String mode) {

        /**
         * Creates a validated export config.
         *
         * @param enabled export-enabled flag
         * @param mode export mode identifier
         */
        public ExportConfig {
            requireText(mode, "mode");
        }
    }

    /**
     * Diagnostic artifact and overlay controls.
     *
     * @param showOverlay whether debug overlays should be rendered
     * @param writeFrameMetadataLog whether frame metadata logs should be written
     * @param writeSessionPlan whether a session plan artifact should be written
     */
    public record DiagnosticsConfig(
            boolean showOverlay,
            boolean writeFrameMetadataLog,
            boolean writeSessionPlan
    ) {
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(field + " must not be blank");
        }
    }
}
