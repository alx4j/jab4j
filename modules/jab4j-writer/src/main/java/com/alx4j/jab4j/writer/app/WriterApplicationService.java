package com.alx4j.jab4j.writer.app;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.writer.config.EffectiveConfigSerializer;
import com.alx4j.jab4j.writer.config.RuntimeConfig;
import com.alx4j.jab4j.writer.config.RuntimeConfigPatch;
import com.alx4j.jab4j.writer.config.RuntimeConfigResolver;
import com.alx4j.jab4j.api.model.CodecProfile;
import com.alx4j.jab4j.api.model.FileType;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.ParityGroupSizingStrategy;
import com.alx4j.jab4j.api.model.PlaybackProfile;
import com.alx4j.jab4j.api.model.ProtocolVersion;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.SessionProfile;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.api.model.TransferSession;
import com.alx4j.jab4j.api.model.TransportProfile;
import com.alx4j.jab4j.output.ExportArtifacts;
import com.alx4j.jab4j.output.ExportException;
import com.alx4j.jab4j.output.ExportMode;
import com.alx4j.jab4j.output.PreparedFrameExporter;
import com.alx4j.jab4j.render.frame.FrameRasterRenderer;
import com.alx4j.jab4j.render.frame.FrameRenderOptions;
import com.alx4j.jab4j.render.frame.RenderedFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.catalog.DeclaredInputRoot;
import com.alx4j.jab4j.catalog.InputCatalogBuilder;
import com.alx4j.jab4j.catalog.PackagingResult;
import com.alx4j.jab4j.player.core.DeterministicPlaybackEngine;
import com.alx4j.jab4j.player.core.PlaybackControl;
import com.alx4j.jab4j.player.core.PlaybackObserver;
import com.alx4j.jab4j.player.core.PlaybackProgress;
import com.alx4j.jab4j.player.core.PlaybackResult;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.tile.TileEncoder;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;
import com.alx4j.jab4j.transfer.TransferPlanner;
import com.alx4j.jab4j.transfer.TransportSessionPlan;

/**
 * Orchestrates one end-to-end writer run from resolved config through playback or dry-run execution.
 */
public final class WriterApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WriterApplicationService.class);
    private static final String WRITER_BUILD_ID = resolveWriterBuildId();
    private static final Path DEFAULT_DIAGNOSTICS_ROOT = Path.of("build", "jab4j-diagnostics");
    private static final Path DEFAULT_EXPORT_ROOT = Path.of("build", "jab4j-exports");

    private final RuntimeConfigResolver configResolver;
    private final EffectiveConfigSerializer effectiveConfigSerializer;
    private final InputCatalogBuilder catalogBuilder;
    private final TransferPlanner transferPlanner;
    private final TileEncoder tileEncoder;
    private final TilePayloadEnvelopeCodec envelopeCodec = new TilePayloadEnvelopeCodec();
    private final FixedLayoutPlanner fixedLayoutPlanner;
    private final TileRasterRenderer tileRasterRenderer;
    private final FrameRasterRenderer frameRasterRenderer;
    private final DeterministicPlaybackEngine playbackEngine;
    private final PreparedFrameExporter preparedFrameExporter;
    private final Supplier<Instant> currentTimeSupplier;
    private final Supplier<SessionId> sessionIdSupplier;
    private final Path diagnosticsRootDirectory;
    private final Path exportRootDirectory;

    /**
     * Creates a writer application service with the baseline runtime dependencies.
     */
    public WriterApplicationService() {
        this(
                new RuntimeConfigResolver(),
                new EffectiveConfigSerializer(),
                new InputCatalogBuilder(),
                new TransferPlanner(),
                TileCodecs.defaultEncoder(),
                new FixedLayoutPlanner(),
                new TileRasterRenderer(),
                new FrameRasterRenderer(),
                new DeterministicPlaybackEngine(),
                new PreparedFrameExporter(),
                Instant::now,
                SessionId::random,
                DEFAULT_DIAGNOSTICS_ROOT,
                DEFAULT_EXPORT_ROOT
        );
    }

    /**
     * Creates a writer application service with deterministic time and session-id suppliers.
     *
     * @param currentTimeSupplier time supplier used for session creation
     * @param sessionIdSupplier session-id supplier used for session creation
     */
    public WriterApplicationService(Supplier<Instant> currentTimeSupplier, Supplier<SessionId> sessionIdSupplier) {
        this(
                new RuntimeConfigResolver(),
                new EffectiveConfigSerializer(),
                new InputCatalogBuilder(),
                new TransferPlanner(),
                TileCodecs.defaultEncoder(),
                new FixedLayoutPlanner(),
                new TileRasterRenderer(),
                new FrameRasterRenderer(),
                new DeterministicPlaybackEngine(),
                new PreparedFrameExporter(),
                currentTimeSupplier,
                sessionIdSupplier,
                DEFAULT_DIAGNOSTICS_ROOT,
                DEFAULT_EXPORT_ROOT
        );
    }

    /**
     * Creates a writer application service with deterministic time, session-id, and diagnostics-root inputs.
     *
     * @param currentTimeSupplier time supplier used for session creation
     * @param sessionIdSupplier session-id supplier used for session creation
     * @param diagnosticsRootDirectory deterministic diagnostics-root directory
     */
    public WriterApplicationService(
            Supplier<Instant> currentTimeSupplier,
            Supplier<SessionId> sessionIdSupplier,
            Path diagnosticsRootDirectory
    ) {
        this(
                new RuntimeConfigResolver(),
                new EffectiveConfigSerializer(),
                new InputCatalogBuilder(),
                new TransferPlanner(),
                TileCodecs.defaultEncoder(),
                new FixedLayoutPlanner(),
                new TileRasterRenderer(),
                new FrameRasterRenderer(),
                new DeterministicPlaybackEngine(),
                new PreparedFrameExporter(),
                currentTimeSupplier,
                sessionIdSupplier,
                diagnosticsRootDirectory,
                DEFAULT_EXPORT_ROOT
        );
    }

    /**
     * Creates a writer application service with deterministic time, session-id, diagnostics-root, and export-root inputs.
     *
     * @param currentTimeSupplier time supplier used for session creation
     * @param sessionIdSupplier session-id supplier used for session creation
     * @param diagnosticsRootDirectory deterministic diagnostics-root directory
     * @param exportRootDirectory deterministic export-root directory
     */
    public WriterApplicationService(
            Supplier<Instant> currentTimeSupplier,
            Supplier<SessionId> sessionIdSupplier,
            Path diagnosticsRootDirectory,
            Path exportRootDirectory
    ) {
        this(
                new RuntimeConfigResolver(),
                new EffectiveConfigSerializer(),
                new InputCatalogBuilder(),
                new TransferPlanner(),
                TileCodecs.defaultEncoder(),
                new FixedLayoutPlanner(),
                new TileRasterRenderer(),
                new FrameRasterRenderer(),
                new DeterministicPlaybackEngine(),
                new PreparedFrameExporter(),
                currentTimeSupplier,
                sessionIdSupplier,
                diagnosticsRootDirectory,
                exportRootDirectory
        );
    }

    WriterApplicationService(
            RuntimeConfigResolver configResolver,
            EffectiveConfigSerializer effectiveConfigSerializer,
            InputCatalogBuilder catalogBuilder,
            TransferPlanner transferPlanner,
            TileEncoder tileEncoder,
            FixedLayoutPlanner fixedLayoutPlanner,
            TileRasterRenderer tileRasterRenderer,
            FrameRasterRenderer frameRasterRenderer,
            DeterministicPlaybackEngine playbackEngine,
            PreparedFrameExporter preparedFrameExporter,
            Supplier<Instant> currentTimeSupplier,
            Supplier<SessionId> sessionIdSupplier,
            Path diagnosticsRootDirectory,
            Path exportRootDirectory
    ) {
        this.configResolver = Objects.requireNonNull(configResolver, "configResolver must not be null");
        this.effectiveConfigSerializer = Objects.requireNonNull(
                effectiveConfigSerializer,
                "effectiveConfigSerializer must not be null"
        );
        this.catalogBuilder = Objects.requireNonNull(catalogBuilder, "catalogBuilder must not be null");
        this.transferPlanner = Objects.requireNonNull(
                transferPlanner,
                "transferPlanner must not be null"
        );
        this.tileEncoder = Objects.requireNonNull(tileEncoder, "tileEncoder must not be null");
        this.fixedLayoutPlanner = Objects.requireNonNull(fixedLayoutPlanner, "fixedLayoutPlanner must not be null");
        this.tileRasterRenderer = Objects.requireNonNull(tileRasterRenderer, "tileRasterRenderer must not be null");
        this.frameRasterRenderer = Objects.requireNonNull(frameRasterRenderer, "frameRasterRenderer must not be null");
        this.playbackEngine = Objects.requireNonNull(playbackEngine, "playbackEngine must not be null");
        this.preparedFrameExporter = Objects.requireNonNull(preparedFrameExporter, "preparedFrameExporter must not be null");
        this.currentTimeSupplier = Objects.requireNonNull(currentTimeSupplier, "currentTimeSupplier must not be null");
        this.sessionIdSupplier = Objects.requireNonNull(sessionIdSupplier, "sessionIdSupplier must not be null");
        this.diagnosticsRootDirectory = Objects.requireNonNull(
                diagnosticsRootDirectory,
                "diagnosticsRootDirectory must not be null"
        ).toAbsolutePath().normalize();
        this.exportRootDirectory = Objects.requireNonNull(exportRootDirectory, "exportRootDirectory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Executes one writer run from config resolution through playback or dry-run execution.
     *
     * @param request writer run request
     * @param observer lifecycle observer, or {@code null} for no callbacks
     * @return completed writer run summary
     */
    public WriterRunResult run(WriterRunRequest request, WriterJobObserver observer) {
        Objects.requireNonNull(request, "request must not be null");
        WriterJobObserver effectiveObserver = observer == null ? WriterJobObserver.noOp() : observer;
        LOGGER.info(
                "Starting writer run dryRun={} profileOverride={} inputRootCount={} fpsOverride={} chunkBytesOverride={} fullscreenOverride={} diagnosticsRoot={} exportRoot={} exportEnabled={} exportMode={}",
                request.dryRun(),
                request.cliOverrides().app() == null ? null : request.cliOverrides().app().profile(),
                countInputRoots(request.cliOverrides().input()),
                request.cliOverrides().playback() == null ? null : request.cliOverrides().playback().fps(),
                request.cliOverrides().transport() == null ? null : request.cliOverrides().transport().chunkBytes(),
                request.cliOverrides().playback() == null ? null : request.cliOverrides().playback().fullscreen(),
                diagnosticsRootDirectory,
                exportRootDirectory,
                request.cliOverrides().export() != null && Boolean.TRUE.equals(request.cliOverrides().export().enabled()),
                request.cliOverrides().export() == null ? null : request.cliOverrides().export().mode()
        );

        RuntimeConfig effectiveConfig = resolveConfig(request, effectiveObserver);
        String effectiveConfigJson = effectiveConfigSerializer.serialize(effectiveConfig);
        LOGGER.info(
                "Resolved writer config selectedProfile={} inputRootCount={} layoutProfileId={} grid={}x{} fps={} chunkBytes={} fullscreen={} exportEnabled={} exportMode={} writeSessionPlan={} writeFrameMetadataLog={} overlayEnabled={}",
                effectiveConfig.app().profile(),
                effectiveConfig.input().roots().size(),
                effectiveConfig.layout().profileId(),
                effectiveConfig.layout().rows(),
                effectiveConfig.layout().cols(),
                effectiveConfig.playback().fps(),
                effectiveConfig.transport().chunkBytes(),
                effectiveConfig.playback().fullscreen(),
                effectiveConfig.export().enabled(),
                effectiveConfig.export().mode(),
                effectiveConfig.diagnostics().writeSessionPlan(),
                effectiveConfig.diagnostics().writeFrameMetadataLog(),
                effectiveConfig.diagnostics().showOverlay()
        );
        emit(effectiveObserver, new WriterJobEvent(
                WriterJobStatus.CONFIG_RESOLVED,
                "selectedProfile=" + effectiveConfig.app().profile()
                        + " effectiveConfig=" + compactSingleLine(effectiveConfigJson),
                null,
                null
        ));

        List<DeclaredInputRoot> declaredRoots = validateInputRoots(effectiveConfig, effectiveObserver);
        PackagingResult packagingResult = packageInputs(effectiveConfig, declaredRoots, effectiveObserver);
        TransportSessionPlan sessionPlan = planSession(effectiveConfig, packagingResult, effectiveObserver);
        RenderedFramesResult renderedFramesResult = renderFrames(effectiveConfig, sessionPlan, effectiveObserver);
        WriterRunMetrics metricsBeforePlayback = buildMetricsBeforePlayback(
                packagingResult,
                sessionPlan,
                renderedFramesResult
        );
        LOGGER.info(
                "Starting writer playback sessionId={} dryRun={} fps={} fullscreen={} sourceFrames={} holdFrames={} warmupSyncFrames={} endFrames={}",
                sessionPlan.session().sessionId(),
                request.dryRun(),
                effectiveConfig.playback().fps(),
                effectiveConfig.playback().fullscreen(),
                renderedFramesResult.preparedFrames().size(),
                effectiveConfig.playback().holdFrames(),
                effectiveConfig.playback().warmupSyncFrames(),
                effectiveConfig.playback().endFrames()
        );
        PlaybackResult playbackResult = playFrames(
                effectiveConfig,
                request.dryRun(),
                renderedFramesResult.preparedFrames(),
                effectiveObserver
        );
        WriterRunMetrics metrics = new WriterRunMetrics(
                metricsBeforePlayback.filesScanned(),
                metricsBeforePlayback.bytesScanned(),
                metricsBeforePlayback.chunksCreated(),
                metricsBeforePlayback.parityShardsCreated(),
                metricsBeforePlayback.tilesEncoded(),
                metricsBeforePlayback.framesRendered(),
                metricsBeforePlayback.averageFrameRenderNanos(),
                playbackResult.underrunCount(),
                metricsBeforePlayback.validationFailures()
        );
        WriterReproducibilityMetadata reproducibilityMetadata = buildReproducibilityMetadata(
                effectiveConfig,
                effectiveConfigJson,
                sessionPlan
        );
        WriterDiagnosticsArtifacts artifacts = writeDiagnosticsArtifacts(
                effectiveConfig,
                effectiveConfigJson,
                sessionPlan,
                renderedFramesResult.preparedFrames(),
                reproducibilityMetadata,
                effectiveObserver
        );
        ExportArtifacts exportArtifacts = exportFrames(
                effectiveConfig,
                sessionPlan,
                renderedFramesResult.preparedFrames(),
                effectiveObserver
        );

        WriterRunResult result = new WriterRunResult(
                sessionPlan.session().sessionId(),
                effectiveConfig,
                effectiveConfigJson,
                sessionPlan.finalSessionDigest(),
                renderedFramesResult.preparedFrames().stream()
                        .map(frame -> frame.diagnostics().get("pixelSha256"))
                        .toList(),
                metrics,
                artifacts,
                exportArtifacts,
                reproducibilityMetadata,
                playbackResult,
                request.dryRun()
        );
        LOGGER.info(
                "Completed writer run sessionId={} selectedProfile={} dryRun={} displayedPresentations={} sourceFrames={} playbackDurationNanos={} underruns={} diagnosticsDirectory={} diagnosticsFiles={} exportEnabled={} exportDirectory={} exportedFiles={} finalSessionDigest={}",
                result.sessionId(),
                result.effectiveConfig().app().profile(),
                result.dryRun(),
                result.playbackResult().displayedPresentationCount(),
                result.playbackResult().sourceFrameCount(),
                result.playbackResult().totalDurationNanos(),
                result.playbackResult().underrunCount(),
                result.artifacts().artifactDirectory(),
                result.artifacts().artifactFiles().size(),
                result.effectiveConfig().export().enabled(),
                result.exportArtifacts().exportDirectory(),
                result.exportArtifacts().exportedFiles().size(),
                result.finalSessionDigest()
        );
        emit(effectiveObserver, new WriterJobEvent(
                WriterJobStatus.COMPLETED,
                "playbackNanos=" + playbackResult.totalDurationNanos()
                        + " underruns=" + playbackResult.underrunCount()
                        + " artifactDirectory=" + artifacts.artifactDirectory()
                        + " exportDirectory=" + exportArtifacts.exportDirectory()
                        + " finalSessionDigest=" + result.finalSessionDigest(),
                result.playbackResult().displayedPresentationCount(),
                result.playbackResult().displayedPresentationCount()
        ));
        return result;
    }

    private RuntimeConfig resolveConfig(WriterRunRequest request, WriterJobObserver observer) {
        try {
            emit(observer, new WriterJobEvent(WriterJobStatus.RESOLVING_CONFIG, "Resolving runtime config", null, null));
            return configResolver.resolve(RuntimeConfigPatch.empty(), request.cliOverrides());
        } catch (RuntimeException exception) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.RESOLVING_CONFIG,
                    "Failed to resolve effective runtime config",
                    exception
            ));
        }
    }

    private List<DeclaredInputRoot> validateInputRoots(RuntimeConfig config, WriterJobObserver observer) {
        emit(observer, new WriterJobEvent(WriterJobStatus.VALIDATING_INPUTS, "Validating input roots", null, null));
        List<RuntimeConfig.InputRootConfig> roots = config.input().roots();
        if (roots.isEmpty()) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.VALIDATING_INPUTS,
                    "At least one input root must be configured"
            ));
        }

        List<DeclaredInputRoot> declaredRoots = new ArrayList<>(roots.size());
        for (RuntimeConfig.InputRootConfig root : roots) {
            Path path;
            try {
                path = Path.of(root.path());
            } catch (InvalidPathException exception) {
                throw fail(observer, new WriterJobException(
                        WriterJobStatus.VALIDATING_INPUTS,
                        "Invalid input root path: " + root.path(),
                        exception
                ));
            }
            if (!Files.isDirectory(path)) {
                throw fail(observer, new WriterJobException(
                        WriterJobStatus.VALIDATING_INPUTS,
                        "Input root is not a readable directory: " + path
                ));
            }
            declaredRoots.add(new DeclaredInputRoot(path, root.alias()));
        }
        LOGGER.info(
                "Validated writer input roots inputRoots={}",
                formatDeclaredInputRoots(declaredRoots)
        );
        emit(observer, new WriterJobEvent(
                WriterJobStatus.VALIDATING_INPUTS,
                "validatedInputRoots=" + declaredRoots.stream()
                        .map(root -> root.path().toAbsolutePath().normalize().toString())
                        .toList(),
                (long) declaredRoots.size(),
                (long) declaredRoots.size()
        ));
        return List.copyOf(declaredRoots);
    }

    private PackagingResult packageInputs(
            RuntimeConfig effectiveConfig,
            List<DeclaredInputRoot> declaredRoots,
            WriterJobObserver observer
    ) {
        try {
            PackagingResult packagingResult = catalogBuilder.buildPackagingResult(declaredRoots);
            String manifestDump = serializeManifest(packagingResult);
            enforcePackagingLimits(effectiveConfig, packagingResult, manifestDump);
            LOGGER.info(
                    "Packaged writer input manifestFingerprint={} rootAliases={} manifestEntries={} regularFiles={} totalBytes={}",
                    packagingResult.manifest().manifestFingerprint(),
                    packagingResult.manifest().rootAliases(),
                    packagingResult.manifest().files().size(),
                    packagingResult.manifest().files().stream().filter(file -> file.fileType() == FileType.REGULAR_FILE).count(),
                    packagingResult.manifest().totalSizeBytes()
            );
            emit(observer, new WriterJobEvent(
                    WriterJobStatus.PACKAGING_INPUTS,
                    "manifestFingerprint=" + packagingResult.manifest().manifestFingerprint()
                            + " filesScanned=" + packagingResult.manifest().files().size()
                            + " bytesScanned=" + packagingResult.manifest().totalSizeBytes(),
                    (long) packagingResult.manifest().files().size(),
                    (long) packagingResult.manifest().files().size()
            ));
            return packagingResult;
        } catch (WriterJobException exception) {
            throw fail(observer, exception);
        } catch (RuntimeException exception) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.PACKAGING_INPUTS,
                    "Failed to package input roots",
                    exception
            ));
        }
    }

    private TransportSessionPlan planSession(
            RuntimeConfig effectiveConfig,
            PackagingResult packagingResult,
            WriterJobObserver observer
    ) {
        try {
            TransportSessionPlan sessionPlan = transferPlanner.plan(
                    draftSession(effectiveConfig, packagingResult),
                    packagingResult
            );
            enforcePlannedFrameLimit(effectiveConfig, sessionPlan);
            enforceTilePayloadCapacity(sessionPlan);
            LOGGER.info(
                    "Planned writer session sessionId={} chunkCount={} parityPlan={} frameCountsByType={} finalSessionDigest={}",
                    sessionPlan.session().sessionId(),
                    sessionPlan.chunkPayloads().size(),
                    formatParityPlan(effectiveConfig, sessionPlan),
                    formatFrameCounts(sessionPlan.frameDescriptors()),
                    sessionPlan.finalSessionDigest()
            );
            emit(observer, new WriterJobEvent(
                    WriterJobStatus.SESSION_PLANNED,
                    "chunkCount=" + sessionPlan.chunkPayloads().size()
                            + " parityShards=" + countParityShards(sessionPlan)
                            + " frameCountsByType=" + formatFrameCounts(sessionPlan.frameDescriptors()),
                    (long) sessionPlan.frameDescriptors().size(),
                    (long) sessionPlan.frameDescriptors().size()
            ));
            return sessionPlan;
        } catch (WriterJobException exception) {
            throw fail(observer, exception);
        } catch (RuntimeException exception) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.SESSION_PLANNED,
                    "Failed to plan transport session",
                    exception
            ));
        }
    }

    private RenderedFramesResult renderFrames(
            RuntimeConfig effectiveConfig,
            TransportSessionPlan sessionPlan,
            WriterJobObserver observer
    ) {
        try {
            SessionProfile sessionProfile = sessionPlan.session().profile();
            LayoutProfile layoutProfile = sessionProfile.layout();
            FixedLayoutPlan layoutPlan = fixedLayoutPlanner.plan(layoutProfile);
            TileCodecProfile codecProfile = TileCodecProfiles.resolve(sessionProfile.codec().profileId());
            FrameRenderOptions frameRenderOptions = new FrameRenderOptions(true, effectiveConfig.diagnostics().showOverlay());
            enforceInMemoryBufferLimit(effectiveConfig, sessionPlan.frameDescriptors().size());

            LOGGER.info(
                    "Rendering writer frames sessionId={} layoutProfileId={} codecProfileId={} frameCount={} frameSize={}x{} tileGrid={}x{} overlayEnabled={}",
                    sessionPlan.session().sessionId(),
                    layoutProfile.profileId(),
                    codecProfile.profileId(),
                    sessionPlan.frameDescriptors().size(),
                    layoutProfile.frameWidthPx(),
                    layoutProfile.frameHeightPx(),
                    layoutProfile.rows(),
                    layoutProfile.cols(),
                    effectiveConfig.diagnostics().showOverlay()
            );
            List<RenderedFrame> preparedFrames = new ArrayList<>(sessionPlan.frameDescriptors().size());
            long tilesEncoded = 0;
            long totalFrameRenderNanos = 0;
            for (FrameDescriptor frameDescriptor : sessionPlan.frameDescriptors()) {
                long startedAtNanos = System.nanoTime();
                preparedFrames.add(renderFrame(frameDescriptor, layoutPlan, codecProfile, frameRenderOptions));
                totalFrameRenderNanos += System.nanoTime() - startedAtNanos;
                tilesEncoded += frameDescriptor.tiles().size();
            }
            long averageFrameRenderNanos = preparedFrames.isEmpty() ? 0 : totalFrameRenderNanos / preparedFrames.size();
            LOGGER.info(
                    "Prepared writer frames sessionId={} framesRendered={} tilesEncoded={} averageFrameRenderNanos={}",
                    sessionPlan.session().sessionId(),
                    preparedFrames.size(),
                    tilesEncoded,
                    averageFrameRenderNanos
            );
            emit(observer, new WriterJobEvent(
                    WriterJobStatus.FRAMES_RENDERED,
                    "framesRendered=" + preparedFrames.size()
                            + " tilesEncoded=" + tilesEncoded
                            + " averageFrameRenderNanos=" + averageFrameRenderNanos,
                    (long) preparedFrames.size(),
                    (long) preparedFrames.size()
            ));
            return new RenderedFramesResult(List.copyOf(preparedFrames), tilesEncoded, averageFrameRenderNanos, 0);
        } catch (WriterJobException exception) {
            throw fail(observer, exception);
        } catch (RuntimeException exception) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.FRAMES_RENDERED,
                    "Failed to render prepared frames",
                    exception
            ));
        }
    }

    private PlaybackResult playFrames(
            RuntimeConfig effectiveConfig,
            boolean dryRun,
            List<RenderedFrame> preparedFrames,
            WriterJobObserver observer
    ) {
        emit(observer, new WriterJobEvent(
                WriterJobStatus.STARTING_PLAYBACK,
                dryRun ? "Starting dry-run playback" : "Starting playback",
                null,
                null
        ));
        try {
            return playbackEngine.play(
                    preparedFrames,
                    toPlaybackProfile(effectiveConfig),
                    dryRun,
                    new PlaybackObserver() {
                        @Override
                        public void onProgress(PlaybackProgress progress) {
                            emit(observer, new WriterJobEvent(
                                    WriterJobStatus.PLAYBACK_PROGRESS,
                                    "Presented " + progress.frameType() + " frame",
                                    progress.presentationIndex() + 1,
                                    progress.totalPresentations()
                            ));
                        }
                    },
                    new PlaybackControl()
            );
        } catch (RuntimeException exception) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.STARTING_PLAYBACK,
                    dryRun ? "Failed to execute deterministic dry-run playback" : "Failed to execute playback",
                    exception
            ));
        }
    }

    private ExportArtifacts exportFrames(
            RuntimeConfig effectiveConfig,
            TransportSessionPlan sessionPlan,
            List<RenderedFrame> preparedFrames,
            WriterJobObserver observer
    ) {
        Path disabledDirectory = exportRootDirectory
                .resolve(sessionPlan.session().sessionId().toString())
                .resolve(effectiveConfig.export().mode());
        if (!effectiveConfig.export().enabled()) {
            return ExportArtifacts.disabled(disabledDirectory);
        }

        emit(observer, new WriterJobEvent(
                WriterJobStatus.EXPORTING_FRAMES,
                "mode=" + effectiveConfig.export().mode() + " exportRoot=" + exportRootDirectory,
                null,
                null
        ));
        LOGGER.info(
                "Exporting writer frames sessionId={} mode={} exportRoot={}",
                sessionPlan.session().sessionId(),
                effectiveConfig.export().mode(),
                exportRootDirectory
        );
        try {
            return preparedFrameExporter.export(
                    ExportMode.fromValue(effectiveConfig.export().mode()),
                    sessionPlan.session().sessionId(),
                    sessionPlan.finalSessionDigest(),
                    preparedFrames,
                    exportRootDirectory
            );
        } catch (ExportException exception) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.EXPORTING_FRAMES,
                    "Failed to export prepared frames",
                    exception
            ));
        }
    }

    private RenderedFrame renderFrame(
            FrameDescriptor frameDescriptor,
            FixedLayoutPlan layoutPlan,
            TileCodecProfile codecProfile,
            FrameRenderOptions frameRenderOptions
    ) {
        List<RenderedTile> renderedTiles = new ArrayList<>(frameDescriptor.tiles().size());
        for (TilePayload tilePayload : frameDescriptor.tiles()) {
            try {
                LogicalTile logicalTile = tileEncoder.encode(envelopeCodec.serialize(tilePayload), codecProfile);
                renderedTiles.add(tileRasterRenderer.render(logicalTile, layoutPlan));
            } catch (RuntimeException exception) {
                throw new WriterJobException(
                        WriterJobStatus.FRAMES_RENDERED,
                        "Failed to encode or rasterize frame "
                                + frameDescriptor.frameIndex()
                                + " tile "
                                + tilePayload.tileIndex().value(),
                        exception
                );
            }
        }
        return frameRasterRenderer.render(frameDescriptor, renderedTiles, frameRenderOptions);
    }

    private TransferSession draftSession(RuntimeConfig effectiveConfig, PackagingResult packagingResult) {
        ProtocolVersion protocolVersion = toProtocolVersion(effectiveConfig.transport().protocolVersion());
        return new TransferSession(
                Objects.requireNonNull(sessionIdSupplier.get(), "sessionIdSupplier returned null"),
                Objects.requireNonNull(currentTimeSupplier.get(), "currentTimeSupplier returned null"),
                protocolVersion,
                WRITER_BUILD_ID,
                new SessionProfile(
                        effectiveConfig.app().profile(),
                        toLayoutProfile(effectiveConfig),
                        toCodecProfile(effectiveConfig),
                        toTransportProfile(effectiveConfig, protocolVersion),
                        toPlaybackProfile(effectiveConfig)
                ),
                packagingResult.manifest(),
                packagingResult.manifest().files()
        );
    }

    private LayoutProfile toLayoutProfile(RuntimeConfig effectiveConfig) {
        RuntimeConfig.LayoutConfig layout = effectiveConfig.layout();
        return new LayoutProfile(
                layout.profileId(),
                layout.rows(),
                layout.cols(),
                layout.frameWidthPx(),
                layout.frameHeightPx(),
                layout.tileGapPx(),
                layout.outerMarginPx(),
                layout.separatorStyle(),
                layout.topSyncBandPx(),
                layout.metadataBandPx(),
                layout.backgroundStyle(),
                layout.fitPolicy()
        );
    }

    private CodecProfile toCodecProfile(RuntimeConfig effectiveConfig) {
        RuntimeConfig.CodecConfig codec = effectiveConfig.codec();
        return new CodecProfile(codec.profileId(), codec.payloadMode(), codec.conservativeDefaults());
    }

    private TransportProfile toTransportProfile(RuntimeConfig effectiveConfig, ProtocolVersion protocolVersion) {
        RuntimeConfig.TransportConfig transport = effectiveConfig.transport();
        return new TransportProfile(
                effectiveConfig.app().profile() + "-transport",
                protocolVersion,
                transport.chunkBytes(),
                transport.dataShardsPerGroup(),
                transport.parityShardsPerGroup(),
                ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                transport.syncEveryFrames(),
                transport.sessionHeaderRepeatEveryFrames(),
                transport.manifestRepeatEveryFrames()
        );
    }

    private PlaybackProfile toPlaybackProfile(RuntimeConfig effectiveConfig) {
        RuntimeConfig.PlaybackConfig playback = effectiveConfig.playback();
        return new PlaybackProfile(
                effectiveConfig.app().profile(),
                playback.fps(),
                playback.holdFrames(),
                playback.warmupSyncFrames(),
                playback.endFrames(),
                playback.fullscreen()
        );
    }

    private ProtocolVersion toProtocolVersion(int compatibilityVersion) {
        return new ProtocolVersion(compatibilityVersion + ".0", compatibilityVersion);
    }

    private void enforcePackagingLimits(
            RuntimeConfig effectiveConfig,
            PackagingResult packagingResult,
            String manifestDump
    ) {
        RuntimeConfig.ResourceLimitsConfig limits = effectiveConfig.app().resourceLimits();
        if (packagingResult.manifest().files().size() > limits.maxFileCount()) {
            throw new WriterJobException(
                    WriterJobStatus.PACKAGING_INPUTS,
                    "Configured maxFileCount exceeded: "
                            + packagingResult.manifest().files().size()
                            + " > "
                            + limits.maxFileCount()
            );
        }
        if (packagingResult.manifest().totalSizeBytes() > limits.maxTotalBytes()) {
            throw new WriterJobException(
                    WriterJobStatus.PACKAGING_INPUTS,
                    "Configured maxTotalBytes exceeded: "
                            + packagingResult.manifest().totalSizeBytes()
                            + " > "
                            + limits.maxTotalBytes()
            );
        }
        int manifestBytes = manifestDump.getBytes(StandardCharsets.UTF_8).length;
        if (manifestBytes > limits.maxManifestBytes()) {
            throw new WriterJobException(
                    WriterJobStatus.PACKAGING_INPUTS,
                    "Configured maxManifestBytes exceeded: " + manifestBytes + " > " + limits.maxManifestBytes()
            );
        }
    }

    private void enforcePlannedFrameLimit(RuntimeConfig effectiveConfig, TransportSessionPlan sessionPlan) {
        int frameCount = sessionPlan.frameDescriptors().size();
        int limit = effectiveConfig.app().resourceLimits().maxFrameCount();
        if (frameCount > limit) {
            throw new WriterJobException(
                    WriterJobStatus.SESSION_PLANNED,
                    "Configured maxFrameCount exceeded: " + frameCount + " > " + limit
            );
        }
    }

    private void enforceTilePayloadCapacity(TransportSessionPlan sessionPlan) {
        TileCodecProfile codecProfile = TileCodecProfiles.resolve(sessionPlan.session().profile().codec().profileId());
        int maxPayloadBytes = TileCodecProfiles.maxPayloadBytes(codecProfile);

        for (FrameDescriptor frameDescriptor : sessionPlan.frameDescriptors()) {
            for (TilePayload tilePayload : frameDescriptor.tiles()) {
                if (tilePayload.payloadByteLength() <= maxPayloadBytes) {
                    continue;
                }
                throw new WriterJobException(
                        WriterJobStatus.SESSION_PLANNED,
                        oversizedPayloadMessage(codecProfile, maxPayloadBytes, frameDescriptor, tilePayload)
                );
            }
        }
    }

    private String oversizedPayloadMessage(
            TileCodecProfile codecProfile,
            int maxPayloadBytes,
            FrameDescriptor frameDescriptor,
            TilePayload tilePayload
    ) {
        String guidance = switch (tilePayload.payloadKind()) {
            case FILE_CHUNK -> "Lower transport.chunkBytes so each file-chunk payload fits in one tile.";
            case PARITY_SHARD -> "Lower transport.dataShardsPerGroup so each parity payload fits in one tile.";
            case MANIFEST_FRAGMENT -> "The manifest still exceeds one-tile capacity; reduce manifest size or split the input set.";
            default -> "Adjust the transport settings so each payload fits in one tile.";
        };
        return "Configured payload exceeds supported subset capacity for profile "
                + codecProfile.profileId()
                + ": frame="
                + frameDescriptor.frameIndex()
                + " type="
                + frameDescriptor.frameType()
                + " tile="
                + tilePayload.tileIndex().value()
                + " kind="
                + tilePayload.payloadKind()
                + " bodyBytes="
                + tilePayload.payloadByteLength()
                + " maxPayloadBytes="
                + maxPayloadBytes
                + ". "
                + guidance;
    }

    private void enforceInMemoryBufferLimit(RuntimeConfig effectiveConfig, int frameCount) {
        int limit = effectiveConfig.app().resourceLimits().maxInMemoryBuffers();
        if (frameCount > limit) {
            throw new WriterJobException(
                    WriterJobStatus.FRAMES_RENDERED,
                    "Configured maxInMemoryBuffers exceeded: " + frameCount + " > " + limit
            );
        }
    }

    private WriterRunMetrics buildMetricsBeforePlayback(
            PackagingResult packagingResult,
            TransportSessionPlan sessionPlan,
            RenderedFramesResult renderedFramesResult
    ) {
        return new WriterRunMetrics(
                packagingResult.manifest().files().stream().filter(file -> file.fileType() == FileType.REGULAR_FILE).count(),
                packagingResult.manifest().totalSizeBytes(),
                sessionPlan.chunkPayloads().size(),
                countParityShards(sessionPlan),
                renderedFramesResult.tilesEncoded(),
                renderedFramesResult.preparedFrames().size(),
                renderedFramesResult.averageFrameRenderNanos(),
                0,
                renderedFramesResult.validationFailures()
        );
    }

    private WriterReproducibilityMetadata buildReproducibilityMetadata(
            RuntimeConfig effectiveConfig,
            String effectiveConfigJson,
            TransportSessionPlan sessionPlan
    ) {
        ProtocolVersion protocolVersion = sessionPlan.session().protocolVersion();
        return new WriterReproducibilityMetadata(
                effectiveConfig.app().profile(),
                WRITER_BUILD_ID,
                protocolVersion.displayValue(),
                protocolVersion.compatibilityVersion(),
                sha256Hex(effectiveConfigJson.getBytes(StandardCharsets.UTF_8)),
                sessionPlan.session().manifest().manifestFingerprint(),
                sessionPlan.finalSessionDigest(),
                TileCodecProfiles.codecProfileHash()
        );
    }

    private static String sha256Hex(byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String resolveWriterBuildId() {
        String implementationVersion = WriterApplicationService.class.getPackage().getImplementationVersion();
        if (implementationVersion == null || implementationVersion.isBlank()) {
            return "jab4j-writer-local";
        }
        return "jab4j-writer-" + implementationVersion;
    }

    private WriterDiagnosticsArtifacts writeDiagnosticsArtifacts(
            RuntimeConfig effectiveConfig,
            String effectiveConfigJson,
            TransportSessionPlan sessionPlan,
            List<RenderedFrame> preparedFrames,
            WriterReproducibilityMetadata reproducibilityMetadata,
            WriterJobObserver observer
    ) {
        Path runArtifactDirectory = diagnosticsRootDirectory.resolve(sessionPlan.session().sessionId().toString());
        Map<String, Path> artifactFiles = new LinkedHashMap<>();
        if (!(effectiveConfig.diagnostics().writeSessionPlan() || effectiveConfig.diagnostics().writeFrameMetadataLog())) {
            return new WriterDiagnosticsArtifacts(runArtifactDirectory, artifactFiles);
        }

        emit(observer, new WriterJobEvent(
                WriterJobStatus.WRITING_DIAGNOSTICS,
                "artifactDirectory=" + runArtifactDirectory,
                null,
                null
        ));
        LOGGER.info(
                "Writing writer diagnostics sessionId={} artifactDirectory={} writeSessionPlan={} writeFrameMetadataLog={} previewFrameCount={}",
                sessionPlan.session().sessionId(),
                runArtifactDirectory,
                effectiveConfig.diagnostics().writeSessionPlan(),
                effectiveConfig.diagnostics().writeFrameMetadataLog(),
                effectiveConfig.diagnostics().writeFrameMetadataLog() ? Math.min(3, preparedFrames.size()) : 0
        );
        try {
            Files.createDirectories(runArtifactDirectory);
            artifactFiles.put(
                    "effectiveConfig",
                    writeStringArtifact(runArtifactDirectory.resolve("effective-config.json"), effectiveConfigJson)
            );
            artifactFiles.put(
                    "reproducibilityMetadata",
                    writeStringArtifact(
                            runArtifactDirectory.resolve("reproducibility-metadata.txt"),
                            serializeReproducibilityMetadata(reproducibilityMetadata)
                    )
            );
            if (effectiveConfig.diagnostics().writeSessionPlan()) {
                artifactFiles.put(
                        "manifestDump",
                        writeStringArtifact(
                                runArtifactDirectory.resolve("manifest.txt"),
                                serializeManifest(sessionPlan.session())
                        )
                );
                artifactFiles.put(
                        "sessionPlan",
                        writeStringArtifact(
                                runArtifactDirectory.resolve("session-plan.txt"),
                                serializeSessionPlan(sessionPlan)
                        )
                );
            }
            if (effectiveConfig.diagnostics().writeFrameMetadataLog()) {
                artifactFiles.put(
                        "frameMetadataLog",
                        writeStringArtifact(
                                runArtifactDirectory.resolve("frame-metadata.log"),
                                serializeFrameMetadata(preparedFrames)
                        )
                );
                int previewCount = Math.min(3, preparedFrames.size());
                for (int index = 0; index < previewCount; index++) {
                    RenderedFrame frame = preparedFrames.get(index);
                    String fileName = String.format(
                            Locale.ROOT,
                            "frame-preview-%04d-%s.png",
                            frame.frameIndex(),
                            frame.frameType().name().toLowerCase(Locale.ROOT)
                    );
                    artifactFiles.put(
                            "framePreview-" + index,
                            writePreviewPng(runArtifactDirectory.resolve(fileName), frame)
                    );
                }
            }
            LOGGER.info(
                    "Wrote writer diagnostics sessionId={} artifactDirectory={} artifactCount={}",
                    sessionPlan.session().sessionId(),
                    runArtifactDirectory,
                    artifactFiles.size()
            );
            return new WriterDiagnosticsArtifacts(runArtifactDirectory, artifactFiles);
        } catch (IOException | RuntimeException exception) {
            throw fail(observer, new WriterJobException(
                    WriterJobStatus.WRITING_DIAGNOSTICS,
                    "Failed to emit diagnostics artifacts",
                    exception
            ));
        }
    }

    private String serializeManifest(PackagingResult packagingResult) {
        return serializeManifest(packagingResult.manifest().files(), packagingResult.manifest().rootAliases(),
                packagingResult.manifest().totalSizeBytes(), packagingResult.manifest().manifestFingerprint());
    }

    private String serializeManifest(TransferSession session) {
        return serializeManifest(
                session.manifest().files(),
                session.manifest().rootAliases(),
                session.manifest().totalSizeBytes(),
                session.manifest().manifestFingerprint()
        );
    }

    private String serializeManifest(
            List<com.alx4j.jab4j.api.model.FileRecord> files,
            List<String> rootAliases,
            long totalSizeBytes,
            String manifestFingerprint
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("manifestFingerprint=").append(manifestFingerprint).append('\n');
        builder.append("totalSizeBytes=").append(totalSizeBytes).append('\n');
        for (String rootAlias : rootAliases) {
            builder.append("rootAlias=").append(rootAlias).append('\n');
        }
        for (com.alx4j.jab4j.api.model.FileRecord file : files) {
            builder.append("file=").append(file.rootAlias()).append('\t')
                    .append(file.relativePath()).append('\t')
                    .append(file.fileType()).append('\t')
                    .append(file.sizeBytes()).append('\t')
                    .append(file.sha256() == null ? "-" : file.sha256())
                    .append('\n');
            file.chunks().forEach(chunk -> builder.append("chunk=").append(chunk.fileIndex()).append('\t')
                    .append(chunk.chunkIndex()).append('\t')
                    .append(chunk.offset()).append('\t')
                    .append(chunk.payloadLength()).append('\t')
                    .append(chunk.crc32c()).append('\n'));
        }
        return builder.toString();
    }

    private String serializeSessionPlan(TransportSessionPlan sessionPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append("sessionId=").append(sessionPlan.session().sessionId()).append('\n');
        builder.append("createdAt=").append(sessionPlan.session().createdAt()).append('\n');
        builder.append("protocolVersion=").append(sessionPlan.session().protocolVersion().displayValue()).append('\n');
        builder.append("finalSessionDigest=").append(sessionPlan.finalSessionDigest()).append('\n');
        builder.append("manifestFingerprint=").append(sessionPlan.session().manifest().manifestFingerprint()).append('\n');
        builder.append("chunkCount=").append(sessionPlan.chunkPayloads().size()).append('\n');
        builder.append("parityShards=").append(countParityShards(sessionPlan)).append('\n');
        builder.append("frameCountsByType=").append(formatFrameCounts(sessionPlan.frameDescriptors())).append('\n');
        sessionPlan.parityPlan().forEach(parityGroup -> builder.append("parityGroup=").append(parityGroup.groupIndex())
                .append('\t').append(parityGroup.sourceChunkCount())
                .append('\t').append(parityGroup.parityShardCount()).append('\n'));
        sessionPlan.frameDescriptors().forEach(frame -> builder.append("frame=").append(frame.frameIndex())
                .append('\t').append(frame.frameType())
                .append('\t').append(frame.tiles().size()).append('\n'));
        return builder.toString();
    }

    private String serializeFrameMetadata(List<RenderedFrame> preparedFrames) {
        StringBuilder builder = new StringBuilder();
        for (RenderedFrame frame : preparedFrames) {
            builder.append("frame=").append(frame.frameIndex())
                    .append('\t').append(frame.frameType())
                    .append('\t').append(frame.widthPixels()).append('x').append(frame.heightPixels())
                    .append('\t').append(frame.diagnostics().get("pixelSha256")).append('\n');
        }
        return builder.toString();
    }

    private String serializeReproducibilityMetadata(WriterReproducibilityMetadata reproducibilityMetadata) {
        return "selectedProfile=" + reproducibilityMetadata.selectedProfile() + '\n'
                + "writerBuildId=" + reproducibilityMetadata.writerBuildId() + '\n'
                + "protocolVersionDisplay=" + reproducibilityMetadata.protocolVersionDisplay() + '\n'
                + "protocolCompatibilityVersion=" + reproducibilityMetadata.protocolCompatibilityVersion() + '\n'
                + "effectiveConfigSha256=" + reproducibilityMetadata.effectiveConfigSha256() + '\n'
                + "manifestFingerprint=" + reproducibilityMetadata.manifestFingerprint() + '\n'
                + "finalSessionDigest=" + reproducibilityMetadata.finalSessionDigest() + '\n'
                + "codecProfileHash=" + reproducibilityMetadata.codecProfileHash() + '\n';
    }

    private Path writeStringArtifact(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path;
        } catch (IOException exception) {
            throw new WriterJobException(
                    WriterJobStatus.WRITING_DIAGNOSTICS,
                    "Failed to write diagnostics artifact: " + path,
                    exception
            );
        }
    }

    private Path writePreviewPng(Path path, RenderedFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < frame.heightPixels(); row++) {
            for (int col = 0; col < frame.widthPixels(); col++) {
                image.setRGB(col, row, frame.argbPixels().get((row * frame.widthPixels()) + col));
            }
        }
        try {
            ImageIO.write(image, "png", path.toFile());
            return path;
        } catch (IOException exception) {
            throw new WriterJobException(
                    WriterJobStatus.WRITING_DIAGNOSTICS,
                    "Failed to write frame preview: " + path,
                    exception
            );
        }
    }

    private long countParityShards(TransportSessionPlan sessionPlan) {
        return sessionPlan.parityPlan().stream().mapToLong(com.alx4j.jab4j.transfer.ParityGroupPlan::parityShardCount).sum();
    }

    private String formatFrameCounts(List<FrameDescriptor> frameDescriptors) {
        EnumMap<FrameType, Long> counts = new EnumMap<>(FrameType.class);
        for (FrameType frameType : FrameType.values()) {
            counts.put(frameType, 0L);
        }
        for (FrameDescriptor frameDescriptor : frameDescriptors) {
            counts.compute(frameDescriptor.frameType(), (ignored, current) -> current + 1L);
        }
        return counts.toString();
    }

    private String formatDeclaredInputRoots(List<DeclaredInputRoot> declaredRoots) {
        return declaredRoots.stream()
                .map(root -> root.alias() == null
                        ? root.path().toAbsolutePath().normalize().toString()
                        : root.alias() + "=" + root.path().toAbsolutePath().normalize())
                .toList()
                .toString();
    }

    private String formatParityPlan(RuntimeConfig effectiveConfig, TransportSessionPlan sessionPlan) {
        RuntimeConfig.TransportConfig transport = effectiveConfig.transport();
        return "{groupCount=" + sessionPlan.parityPlan().size()
                + ", totalParityShards=" + countParityShards(sessionPlan)
                + ", dataShardsPerGroup=" + transport.dataShardsPerGroup()
                + ", parityShardsPerGroup=" + transport.parityShardsPerGroup()
                + ", syncEveryFrames=" + transport.syncEveryFrames()
                + ", sessionHeaderRepeatEveryFrames=" + transport.sessionHeaderRepeatEveryFrames()
                + ", manifestRepeatEveryFrames=" + transport.manifestRepeatEveryFrames()
                + "}";
    }

    private int countInputRoots(RuntimeConfigPatch.InputPatch inputPatch) {
        if (inputPatch == null || inputPatch.roots() == null) {
            return 0;
        }
        return inputPatch.roots().size();
    }

    private String compactSingleLine(String multiLine) {
        return multiLine.replace(System.lineSeparator(), " ").replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void emit(WriterJobObserver observer, WriterJobEvent event) {
        observer.onEvent(event);
    }

    private WriterJobException fail(WriterJobObserver observer, WriterJobException exception) {
        if (exception.getCause() == null) {
            LOGGER.warn("Writer run failed status={} message={}", exception.status(), exception.getMessage());
        } else {
            LOGGER.error(
                    "Writer run failed status={} message={} causeType={}",
                    exception.status(),
                    exception.getMessage(),
                    exception.getCause().getClass().getSimpleName(),
                    exception
            );
        }
        observer.onFailure(exception);
        return exception;
    }

    private record RenderedFramesResult(
            List<RenderedFrame> preparedFrames,
            long tilesEncoded,
            long averageFrameRenderNanos,
            long validationFailures
    ) {
    }
}
