package pro.alx4j.jab4j.writer.config;

import java.util.List;
import java.util.Objects;

/**
 * Partial runtime configuration used for built-in defaults, selected profiles, file config, and CLI overrides.
 *
 * @param app partial app section
 * @param input partial input section
 * @param layout partial layout section
 * @param codec partial codec section
 * @param transport partial transport section
 * @param playback partial playback section
 * @param export partial export section
 * @param diagnostics partial diagnostics section
 */
public record RuntimeConfigPatch(
        AppPatch app,
        InputPatch input,
        LayoutPatch layout,
        CodecPatch codec,
        TransportPatch transport,
        PlaybackPatch playback,
        ExportPatch export,
        DiagnosticsPatch diagnostics
) {

    private static final RuntimeConfigPatch EMPTY = new RuntimeConfigPatch(null, null, null, null, null, null, null, null);

    /**
     * Returns an empty config patch.
     *
     * @return empty config patch
     */
    public static RuntimeConfigPatch empty() {
        return EMPTY;
    }

    /**
     * Merges another patch on top of this one.
     *
     * @param override later, higher-precedence patch
     * @return merged patch
     */
    public RuntimeConfigPatch merge(RuntimeConfigPatch override) {
        if (override == null) {
            return this;
        }
        return new RuntimeConfigPatch(
                AppPatch.merge(app, override.app),
                InputPatch.merge(input, override.input),
                LayoutPatch.merge(layout, override.layout),
                CodecPatch.merge(codec, override.codec),
                TransportPatch.merge(transport, override.transport),
                PlaybackPatch.merge(playback, override.playback),
                ExportPatch.merge(export, override.export),
                DiagnosticsPatch.merge(diagnostics, override.diagnostics)
        );
    }

    /**
     * Materializes the merged patch into a fully populated runtime config.
     *
     * @return resolved runtime config
     */
    public RuntimeConfig materialize() {
        return new RuntimeConfig(
                requireApp(app),
                input == null ? new RuntimeConfig.InputConfig(List.of()) : input.materialize(),
                requireLayout(layout),
                requireCodec(codec),
                requireTransport(transport),
                requirePlayback(playback),
                requireExport(export),
                requireDiagnostics(diagnostics)
        );
    }

    private static RuntimeConfig.AppConfig requireApp(AppPatch value) {
        if (value == null) {
            throw new ConfigValidationException("Missing required section: app");
        }
        return value.materialize();
    }

    private static RuntimeConfig.LayoutConfig requireLayout(LayoutPatch value) {
        if (value == null) {
            throw new ConfigValidationException("Missing required section: layout");
        }
        return value.materialize();
    }

    private static RuntimeConfig.CodecConfig requireCodec(CodecPatch value) {
        if (value == null) {
            throw new ConfigValidationException("Missing required section: codec");
        }
        return value.materialize();
    }

    private static RuntimeConfig.TransportConfig requireTransport(TransportPatch value) {
        if (value == null) {
            throw new ConfigValidationException("Missing required section: transport");
        }
        return value.materialize();
    }

    private static RuntimeConfig.PlaybackConfig requirePlayback(PlaybackPatch value) {
        if (value == null) {
            throw new ConfigValidationException("Missing required section: playback");
        }
        return value.materialize();
    }

    private static RuntimeConfig.ExportConfig requireExport(ExportPatch value) {
        if (value == null) {
            throw new ConfigValidationException("Missing required section: export");
        }
        return value.materialize();
    }

    private static RuntimeConfig.DiagnosticsConfig requireDiagnostics(DiagnosticsPatch value) {
        if (value == null) {
            throw new ConfigValidationException("Missing required section: diagnostics");
        }
        return value.materialize();
    }

    private static <T> T choose(T base, T override) {
        return override != null ? override : base;
    }

    private static int requireInt(Integer value, String path) {
        if (value == null) {
            throw new ConfigValidationException(path + " is required");
        }
        return value;
    }

    private static long requireLong(Long value, String path) {
        if (value == null) {
            throw new ConfigValidationException(path + " is required");
        }
        return value;
    }

    private static boolean requireBoolean(Boolean value, String path) {
        if (value == null) {
            throw new ConfigValidationException(path + " is required");
        }
        return value;
    }

    private static String requireText(String value, String path) {
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(path + " is required");
        }
        return value;
    }

    /**
     * Partial app section.
     *
     * @param profile selected built-in profile id
     * @param strictValidation strict-validation flag
     * @param resourceLimits partial resource-limit config
     */
    public record AppPatch(
            String profile,
            Boolean strictValidation,
            ResourceLimitsPatch resourceLimits
    ) {

        /**
         * Merges two partial app sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged app section
         */
        public static AppPatch merge(AppPatch base, AppPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new AppPatch(
                    choose(base.profile, override.profile),
                    choose(base.strictValidation, override.strictValidation),
                    ResourceLimitsPatch.merge(base.resourceLimits, override.resourceLimits)
            );
        }

        RuntimeConfig.AppConfig materialize() {
            return new RuntimeConfig.AppConfig(
                    requireText(profile, "app.profile"),
                    requireBoolean(strictValidation, "app.strictValidation"),
                    resourceLimits == null
                            ? throwMissing("app.resourceLimits")
                            : resourceLimits.materialize()
            );
        }

        private static RuntimeConfig.ResourceLimitsConfig throwMissing(String path) {
            throw new ConfigValidationException(path + " is required");
        }
    }

    /**
     * Partial resource-limit section.
     *
     * @param maxFileCount maximum file count
     * @param maxTotalBytes maximum total bytes
     * @param maxManifestBytes maximum manifest bytes
     * @param maxFrameCount maximum frame count
     * @param maxInMemoryBuffers maximum in-memory buffers
     */
    public record ResourceLimitsPatch(
            Integer maxFileCount,
            Long maxTotalBytes,
            Integer maxManifestBytes,
            Integer maxFrameCount,
            Integer maxInMemoryBuffers
    ) {

        /**
         * Merges two partial resource-limit sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged resource-limit section
         */
        public static ResourceLimitsPatch merge(ResourceLimitsPatch base, ResourceLimitsPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new ResourceLimitsPatch(
                    choose(base.maxFileCount, override.maxFileCount),
                    choose(base.maxTotalBytes, override.maxTotalBytes),
                    choose(base.maxManifestBytes, override.maxManifestBytes),
                    choose(base.maxFrameCount, override.maxFrameCount),
                    choose(base.maxInMemoryBuffers, override.maxInMemoryBuffers)
            );
        }

        RuntimeConfig.ResourceLimitsConfig materialize() {
            return new RuntimeConfig.ResourceLimitsConfig(
                    requireInt(maxFileCount, "app.resourceLimits.maxFileCount"),
                    requireLong(maxTotalBytes, "app.resourceLimits.maxTotalBytes"),
                    requireInt(maxManifestBytes, "app.resourceLimits.maxManifestBytes"),
                    requireInt(maxFrameCount, "app.resourceLimits.maxFrameCount"),
                    requireInt(maxInMemoryBuffers, "app.resourceLimits.maxInMemoryBuffers")
            );
        }
    }

    /**
     * Partial input section.
     *
     * @param roots input roots
     */
    public record InputPatch(List<RuntimeConfig.InputRootConfig> roots) {

        /**
         * Creates a partial input section.
         *
         * @param roots input roots
         */
        public InputPatch {
            roots = roots == null ? null : List.copyOf(roots);
        }

        /**
         * Merges two partial input sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged input section
         */
        public static InputPatch merge(InputPatch base, InputPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new InputPatch(override.roots != null ? override.roots : base.roots);
        }

        RuntimeConfig.InputConfig materialize() {
            return new RuntimeConfig.InputConfig(roots == null ? List.of() : roots);
        }
    }

    /**
     * Partial layout section.
     *
     * @param mode layout mode token
     * @param profileId layout profile id
     * @param rows row count
     * @param cols column count
     * @param frameWidthPx frame width
     * @param frameHeightPx frame height
     * @param outerMarginPx outer margin
     * @param tileGapPx tile gap
     * @param separatorStyle separator style
     * @param topSyncBandPx top sync band
     * @param metadataBandPx metadata band
     * @param backgroundStyle background style
     * @param fitPolicy fit policy
     */
    public record LayoutPatch(
            String mode,
            String profileId,
            Integer rows,
            Integer cols,
            Integer frameWidthPx,
            Integer frameHeightPx,
            Integer outerMarginPx,
            Integer tileGapPx,
            String separatorStyle,
            Integer topSyncBandPx,
            Integer metadataBandPx,
            String backgroundStyle,
            String fitPolicy
    ) {

        /**
         * Merges two partial layout sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged layout section
         */
        public static LayoutPatch merge(LayoutPatch base, LayoutPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new LayoutPatch(
                    choose(base.mode, override.mode),
                    choose(base.profileId, override.profileId),
                    choose(base.rows, override.rows),
                    choose(base.cols, override.cols),
                    choose(base.frameWidthPx, override.frameWidthPx),
                    choose(base.frameHeightPx, override.frameHeightPx),
                    choose(base.outerMarginPx, override.outerMarginPx),
                    choose(base.tileGapPx, override.tileGapPx),
                    choose(base.separatorStyle, override.separatorStyle),
                    choose(base.topSyncBandPx, override.topSyncBandPx),
                    choose(base.metadataBandPx, override.metadataBandPx),
                    choose(base.backgroundStyle, override.backgroundStyle),
                    choose(base.fitPolicy, override.fitPolicy)
            );
        }

        RuntimeConfig.LayoutConfig materialize() {
            return new RuntimeConfig.LayoutConfig(
                    RuntimeConfig.LayoutMode.fromValue(requireText(mode, "layout.mode")),
                    requireText(profileId, "layout.profileId"),
                    requireInt(rows, "layout.rows"),
                    requireInt(cols, "layout.cols"),
                    requireInt(frameWidthPx, "layout.frameWidthPx"),
                    requireInt(frameHeightPx, "layout.frameHeightPx"),
                    requireInt(outerMarginPx, "layout.outerMarginPx"),
                    requireInt(tileGapPx, "layout.tileGapPx"),
                    requireText(separatorStyle, "layout.separatorStyle"),
                    requireInt(topSyncBandPx, "layout.topSyncBandPx"),
                    requireInt(metadataBandPx, "layout.metadataBandPx"),
                    requireText(backgroundStyle, "layout.backgroundStyle"),
                    requireText(fitPolicy, "layout.fitPolicy")
            );
        }
    }

    /**
     * Partial codec section.
     *
     * @param profileId codec profile id
     * @param payloadMode payload mode
     * @param conservativeDefaults conservative-defaults flag
     */
    public record CodecPatch(
            String profileId,
            String payloadMode,
            Boolean conservativeDefaults
    ) {

        /**
         * Merges two partial codec sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged codec section
         */
        public static CodecPatch merge(CodecPatch base, CodecPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new CodecPatch(
                    choose(base.profileId, override.profileId),
                    choose(base.payloadMode, override.payloadMode),
                    choose(base.conservativeDefaults, override.conservativeDefaults)
            );
        }

        RuntimeConfig.CodecConfig materialize() {
            return new RuntimeConfig.CodecConfig(
                    requireText(profileId, "codec.profileId"),
                    requireText(payloadMode, "codec.payloadMode"),
                    requireBoolean(conservativeDefaults, "codec.conservativeDefaults")
            );
        }
    }

    /**
     * Partial transport section.
     *
     * @param protocolVersion protocol compatibility version
     * @param chunkBytes chunk size
     * @param dataShardsPerGroup data shards per group
     * @param parityShardsPerGroup parity shards per group
     * @param syncEveryFrames sync-frame cadence
     * @param sessionHeaderRepeatEveryFrames session-header replay cadence
     * @param manifestRepeatEveryFrames manifest replay cadence
     */
    public record TransportPatch(
            Integer protocolVersion,
            Integer chunkBytes,
            Integer dataShardsPerGroup,
            Integer parityShardsPerGroup,
            Integer syncEveryFrames,
            Integer sessionHeaderRepeatEveryFrames,
            Integer manifestRepeatEveryFrames
    ) {

        /**
         * Merges two partial transport sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged transport section
         */
        public static TransportPatch merge(TransportPatch base, TransportPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new TransportPatch(
                    choose(base.protocolVersion, override.protocolVersion),
                    choose(base.chunkBytes, override.chunkBytes),
                    choose(base.dataShardsPerGroup, override.dataShardsPerGroup),
                    choose(base.parityShardsPerGroup, override.parityShardsPerGroup),
                    choose(base.syncEveryFrames, override.syncEveryFrames),
                    choose(base.sessionHeaderRepeatEveryFrames, override.sessionHeaderRepeatEveryFrames),
                    choose(base.manifestRepeatEveryFrames, override.manifestRepeatEveryFrames)
            );
        }

        RuntimeConfig.TransportConfig materialize() {
            return new RuntimeConfig.TransportConfig(
                    requireInt(protocolVersion, "transport.protocolVersion"),
                    requireInt(chunkBytes, "transport.chunkBytes"),
                    requireInt(dataShardsPerGroup, "transport.dataShardsPerGroup"),
                    requireInt(parityShardsPerGroup, "transport.parityShardsPerGroup"),
                    requireInt(syncEveryFrames, "transport.syncEveryFrames"),
                    requireInt(sessionHeaderRepeatEveryFrames, "transport.sessionHeaderRepeatEveryFrames"),
                    requireInt(manifestRepeatEveryFrames, "transport.manifestRepeatEveryFrames")
            );
        }
    }

    /**
     * Partial playback section.
     *
     * @param fps frames per second
     * @param holdFrames hold count
     * @param warmupSyncFrames warmup sync count
     * @param endFrames terminal frame count
     * @param fullscreen fullscreen flag
     */
    public record PlaybackPatch(
            Integer fps,
            Integer holdFrames,
            Integer warmupSyncFrames,
            Integer endFrames,
            Boolean fullscreen
    ) {

        /**
         * Merges two partial playback sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged playback section
         */
        public static PlaybackPatch merge(PlaybackPatch base, PlaybackPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new PlaybackPatch(
                    choose(base.fps, override.fps),
                    choose(base.holdFrames, override.holdFrames),
                    choose(base.warmupSyncFrames, override.warmupSyncFrames),
                    choose(base.endFrames, override.endFrames),
                    choose(base.fullscreen, override.fullscreen)
            );
        }

        RuntimeConfig.PlaybackConfig materialize() {
            return new RuntimeConfig.PlaybackConfig(
                    requireInt(fps, "playback.fps"),
                    requireInt(holdFrames, "playback.holdFrames"),
                    requireInt(warmupSyncFrames, "playback.warmupSyncFrames"),
                    requireInt(endFrames, "playback.endFrames"),
                    requireBoolean(fullscreen, "playback.fullscreen")
            );
        }
    }

    /**
     * Partial export section.
     *
     * @param enabled export-enabled flag
     * @param mode export mode
     */
    public record ExportPatch(Boolean enabled, String mode) {

        /**
         * Merges two partial export sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged export section
         */
        public static ExportPatch merge(ExportPatch base, ExportPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new ExportPatch(
                    choose(base.enabled, override.enabled),
                    choose(base.mode, override.mode)
            );
        }

        RuntimeConfig.ExportConfig materialize() {
            return new RuntimeConfig.ExportConfig(
                    requireBoolean(enabled, "export.enabled"),
                    requireText(mode, "export.mode")
            );
        }
    }

    /**
     * Partial diagnostics section.
     *
     * @param showOverlay overlay flag
     * @param writeFrameMetadataLog frame-metadata log flag
     * @param writeSessionPlan session-plan artifact flag
     */
    public record DiagnosticsPatch(
            Boolean showOverlay,
            Boolean writeFrameMetadataLog,
            Boolean writeSessionPlan
    ) {

        /**
         * Merges two partial diagnostics sections.
         *
         * @param base lower-precedence section
         * @param override higher-precedence section
         * @return merged diagnostics section
         */
        public static DiagnosticsPatch merge(DiagnosticsPatch base, DiagnosticsPatch override) {
            if (base == null) {
                return override;
            }
            if (override == null) {
                return base;
            }
            return new DiagnosticsPatch(
                    choose(base.showOverlay, override.showOverlay),
                    choose(base.writeFrameMetadataLog, override.writeFrameMetadataLog),
                    choose(base.writeSessionPlan, override.writeSessionPlan)
            );
        }

        RuntimeConfig.DiagnosticsConfig materialize() {
            return new RuntimeConfig.DiagnosticsConfig(
                    requireBoolean(showOverlay, "diagnostics.showOverlay"),
                    requireBoolean(writeFrameMetadataLog, "diagnostics.writeFrameMetadataLog"),
                    requireBoolean(writeSessionPlan, "diagnostics.writeSessionPlan")
            );
        }
    }
}
