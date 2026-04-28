package com.alx4j.jab4j.writer.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.writer.config.RuntimeConfig;
import com.alx4j.jab4j.writer.config.RuntimeConfigPatch;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.tile.TileCodecProfiles;

@DisplayName("Writer application service scenarios")
class WriterApplicationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-22T19:40:00Z");
    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final String FIXED_SESSION_ID_TEXT = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final int TEST_CHUNK_BYTES = 32;
    private static final int TEST_FPS = 4;
    private static final int UNPACED_TEST_FPS = 10_000;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Dry runs stay deterministic and report lifecycle milestones")
    void runsDeterministicDryRunPipelineAndReportsLifecycle() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("input-root"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-writer");

        WriterApplicationService service = service(tempDir.resolve("diagnostics"));
        List<WriterJobEvent> events = new ArrayList<>();
        AtomicReference<WriterJobException> failure = new AtomicReference<>();
        WriterJobObserver observer = capturingObserver(events, failure);

        WriterRunRequest request = unpacedDryRunRequest(inputRoot);

        WriterRunResult first = service.run(request, observer);
        WriterRunResult second = service.run(request, WriterJobObserver.noOp());
        List<WriterJobStatus> statuses = events.stream()
                .map(WriterJobEvent::status)
                .toList();

        assertAll(
                () -> assertEquals(first.effectiveConfigJson(), second.effectiveConfigJson()),
                () -> assertEquals(first.finalSessionDigest(), second.finalSessionDigest()),
                () -> assertEquals(first.renderedFrameHashes(), second.renderedFrameHashes()),
                () -> assertEquals(FIXED_SESSION_ID_TEXT, first.sessionId().toString()),
                () -> assertTrue(first.effectiveConfigJson().contains("\"chunkBytes\" : 32")),
                () -> assertTrue(first.effectiveConfigJson().contains("\"fps\" : 10000")),
                () -> assertFalse(first.renderedFrameHashes().isEmpty()),
                () -> assertEquals(first.renderedFrameHashes().size(), first.playbackResult().sourceFrameCount()),
                () -> assertTrue(statuses.contains(WriterJobStatus.CONFIG_RESOLVED)),
                () -> assertTrue(statuses.contains(WriterJobStatus.PACKAGING_INPUTS)),
                () -> assertTrue(statuses.contains(WriterJobStatus.SESSION_PLANNED)),
                () -> assertTrue(statuses.contains(WriterJobStatus.FRAMES_RENDERED)),
                () -> assertTrue(statuses.contains(WriterJobStatus.PLAYBACK_PROGRESS)),
                () -> assertEquals(WriterJobStatus.COMPLETED, statuses.get(statuses.size() - 1)),
                () -> assertNull(failure.get())
        );
    }

    @Test
    @DisplayName("Diagnostics include artifacts, metrics, and reproducibility metadata")
    void writesDiagnosticsArtifactsAndCapturesMetricsAndReproducibilityMetadata() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("diagnostics-input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-diagnostics");
        Path diagnosticsRoot = tempDir.resolve("artifacts");

        WriterApplicationService service = service(diagnosticsRoot);

        WriterRunResult result = service.run(
                unpacedDryRunRequest(inputRoot),
                WriterJobObserver.noOp()
        );

        assertAll(
                () -> assertEquals(1L, result.metrics().filesScanned()),
                () -> assertEquals("hello-diagnostics".length(), result.metrics().bytesScanned()),
                () -> assertTrue(result.metrics().chunksCreated() > 0),
                () -> assertTrue(result.metrics().parityShardsCreated() > 0),
                () -> assertTrue(result.metrics().tilesEncoded() > 0),
                () -> assertEquals(result.renderedFrameHashes().size(), result.metrics().framesRendered()),
                () -> assertTrue(result.metrics().averageFrameRenderNanos() >= 0),
                () -> assertEquals(result.playbackResult().underrunCount(), result.metrics().playbackUnderruns()),
                () -> assertEquals(0L, result.metrics().validationFailures()),
                () -> assertEquals("jab4j-writer-local", result.reproducibilityMetadata().writerBuildId()),
                () -> assertEquals("1.0", result.reproducibilityMetadata().protocolVersionDisplay()),
                () -> assertEquals(1, result.reproducibilityMetadata().protocolCompatibilityVersion()),
                () -> assertEquals(
                        TileCodecProfiles.codecProfileHash(),
                        result.reproducibilityMetadata().codecProfileHash()
                ),
                () -> assertTrue(Files.isDirectory(result.artifacts().artifactDirectory())),
                () -> assertTrue(result.artifacts().artifactFiles().containsKey("effectiveConfig")),
                () -> assertTrue(result.artifacts().artifactFiles().containsKey("manifestDump")),
                () -> assertTrue(result.artifacts().artifactFiles().containsKey("sessionPlan")),
                () -> assertTrue(result.artifacts().artifactFiles().containsKey("frameMetadataLog")),
                () -> assertTrue(result.artifacts().artifactFiles().containsKey("reproducibilityMetadata")),
                () -> assertTrue(result.artifacts().artifactFiles().keySet().stream().anyMatch(key -> key.startsWith("framePreview-")))
        );
    }

    @Test
    @DisplayName("Successful runs emit operational session-summary logs")
    @ResourceLock("logback")
    void successfulRunsEmitOperationalSessionSummaryLogs() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("logging-input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-logging");

        WriterApplicationService service =
                service(tempDir.resolve("diagnostics-logging"), tempDir.resolve("exports-logging"));

        try (LogCapture logCapture = captureLogs()) {
            WriterRunResult result = service.run(
                    exportImageSequenceRequest(inputRoot),
                    WriterJobObserver.noOp()
            );

            assertAll(
                    () -> assertTrue(logCapture.contains(Level.INFO, "Resolved writer config selectedProfile=desktop-1080p-safe")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "chunkBytes=32")),
                    () -> assertTrue(logCapture.contains(
                            Level.INFO,
                            "Validated writer input roots inputRoots=[" + inputRoot.toAbsolutePath().normalize()
                    )),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Packaged writer input manifestFingerprint=")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Planned writer session sessionId=" + FIXED_SESSION_ID_TEXT)),
                    () -> assertTrue(logCapture.contains(Level.INFO, "chunkCount=")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "parityPlan={groupCount=")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Rendering writer frames sessionId=" + FIXED_SESSION_ID_TEXT)),
                    () -> assertTrue(logCapture.contains(Level.INFO, "frameCountsByType=")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Starting writer playback sessionId=" + FIXED_SESSION_ID_TEXT)),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Wrote writer diagnostics sessionId=" + FIXED_SESSION_ID_TEXT)),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Completed writer run sessionId=" + FIXED_SESSION_ID_TEXT)),
                    () -> assertTrue(logCapture.contains(Level.INFO, "playbackDurationNanos=")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "diagnosticsDirectory=" + result.artifacts().artifactDirectory())),
                    () -> assertTrue(logCapture.contains(Level.INFO, "exportDirectory=" + result.exportArtifacts().exportDirectory()))
            );
        }
    }

    @Test
    @DisplayName("Image-sequence exports preserve playback artifacts and stay opt-in")
    void exportIsDisabledByDefaultAndImageSequenceExportPreservesPlaybackArtifacts() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("export-input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-export");

        WriterApplicationService baselineService =
                service(tempDir.resolve("diagnostics-baseline"), tempDir.resolve("exports-baseline"));
        WriterApplicationService exportService =
                service(tempDir.resolve("diagnostics-export"), tempDir.resolve("exports-enabled"));

        WriterRunResult baseline = baselineService.run(unpacedDryRunRequest(inputRoot), WriterJobObserver.noOp());
        WriterRunResult firstExport = exportService.run(
                exportImageSequenceRequest(inputRoot),
                WriterJobObserver.noOp()
        );

        assertAll(
                () -> assertTrue(baseline.exportArtifacts().exportedFiles().isEmpty()),
                () -> assertEquals(baseline.finalSessionDigest(), firstExport.finalSessionDigest()),
                () -> assertEquals(baseline.renderedFrameHashes(), firstExport.renderedFrameHashes()),
                () -> assertTrue(firstExport.exportArtifacts().exportedFiles().containsKey("frameSequence")),
                () -> assertTrue(firstExport.exportArtifacts().exportedFiles().keySet().stream().anyMatch(key -> key.startsWith("image-"))),
                () -> assertEquals(
                        firstExport.renderedFrameHashes().size() + 1,
                        firstExport.exportArtifacts().exportedFiles().size()
                ),
                () -> assertTrue(firstExport.exportArtifacts().exportedFiles().values().stream().allMatch(Files::exists))
        );
    }

    @Test
    @DisplayName("Unreadable inputs fail before packaging begins")
    void unreadableInputRootFailsBeforePackagingStarts() {
        WriterApplicationService service = service(tempDir.resolve("diagnostics"));
        AtomicReference<WriterJobException> failure = new AtomicReference<>();
        WriterJobObserver observer = capturingObserver(new ArrayList<>(), failure);

        WriterJobException exception = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        new WriterRunRequest(cliOverrides(tempDir.resolve("missing"), 32, 4, false), true),
                        observer
                )
        );

        assertEquals(WriterJobStatus.VALIDATING_INPUTS, exception.status());
        assertNotNull(failure.get());
        assertTrue(exception.getMessage().contains("Input root is not a readable directory"));
    }

    @Test
    @DisplayName("Validation failures emit actionable warning logs")
    @ResourceLock("logback")
    void validationFailuresEmitActionableWarningLogs() {
        WriterApplicationService service = service(tempDir.resolve("diagnostics"));

        try (LogCapture logCapture = captureLogs()) {
            WriterJobException exception = assertThrows(
                    WriterJobException.class,
                    () -> service.run(
                            new WriterRunRequest(cliOverrides(tempDir.resolve("missing"), 32, 4, false), true),
                            WriterJobObserver.noOp()
                    )
            );

            assertAll(
                    () -> assertEquals(WriterJobStatus.VALIDATING_INPUTS, exception.status()),
                    () -> assertTrue(exception.getMessage().contains("Input root is not a readable directory")),
                    () -> assertTrue(logCapture.contains(
                            Level.WARN,
                            "Writer run failed status=VALIDATING_INPUTS message=Input root is not a readable directory"
                    ))
            );
        }
    }

    @Test
    @DisplayName("File-count limits fail fast during packaging")
    void maxFileCountLimitFailsFastDuringPackaging() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("file-limit-input"));
        Files.writeString(inputRoot.resolve("a.txt"), "a");
        Files.writeString(inputRoot.resolve("b.txt"), "b");

        WriterApplicationService service = service(tempDir.resolve("diagnostics"));

        WriterJobException exception = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        new WriterRunRequest(
                                cliOverrides(inputRoot, TEST_CHUNK_BYTES, TEST_FPS, false, 1, null, null, null, null, null, null),
                                true
                        ),
                        WriterJobObserver.noOp()
                )
        );

        assertEquals(WriterJobStatus.PACKAGING_INPUTS, exception.status());
        assertTrue(exception.getMessage().contains("maxFileCount"));
    }

    @Test
    @DisplayName("Total-byte and manifest-byte limits fail fast during packaging")
    void maxTotalBytesAndManifestBytesLimitsFailFastDuringPackaging() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("size-limit-input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-writer");

        WriterApplicationService service = service(tempDir.resolve("diagnostics"));

        WriterJobException totalBytesException = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        new WriterRunRequest(
                                cliOverrides(
                                        inputRoot,
                                        TEST_CHUNK_BYTES,
                                        TEST_FPS,
                                        false,
                                        null,
                                        4L,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                true
                        ),
                        WriterJobObserver.noOp()
                )
        );
        assertEquals(WriterJobStatus.PACKAGING_INPUTS, totalBytesException.status());
        assertTrue(totalBytesException.getMessage().contains("maxTotalBytes"));

        WriterJobException manifestBytesException = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        new WriterRunRequest(
                                cliOverrides(
                                        inputRoot,
                                        TEST_CHUNK_BYTES,
                                        TEST_FPS,
                                        false,
                                        null,
                                        null,
                                        8,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                true
                        ),
                        WriterJobObserver.noOp()
                )
        );
        assertEquals(WriterJobStatus.PACKAGING_INPUTS, manifestBytesException.status());
        assertTrue(manifestBytesException.getMessage().contains("maxManifestBytes"));
    }

    @Test
    @DisplayName("Frame-count and buffer limits fail after planning or rendering")
    void maxFrameCountAndInMemoryBufferLimitsFailFastAfterSessionPlanning() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("frame-limit-input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-writer");

        WriterApplicationService service = service(tempDir.resolve("diagnostics"));

        WriterJobException maxFrameCountException = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        new WriterRunRequest(
                                cliOverrides(
                                        inputRoot,
                                        TEST_CHUNK_BYTES,
                                        TEST_FPS,
                                        false,
                                        null,
                                        null,
                                        null,
                                        1,
                                        null,
                                        null,
                                        null
                                ),
                                true
                        ),
                        WriterJobObserver.noOp()
                )
        );
        assertEquals(WriterJobStatus.SESSION_PLANNED, maxFrameCountException.status());
        assertTrue(maxFrameCountException.getMessage().contains("maxFrameCount"));

        WriterJobException maxBuffersException = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        new WriterRunRequest(
                                cliOverrides(
                                        inputRoot,
                                        TEST_CHUNK_BYTES,
                                        TEST_FPS,
                                        false,
                                        null,
                                        null,
                                        null,
                                        null,
                                        1,
                                        null,
                                        null
                                ),
                                true
                        ),
                        WriterJobObserver.noOp()
                )
        );
        assertEquals(WriterJobStatus.FRAMES_RENDERED, maxBuffersException.status());
        assertTrue(maxBuffersException.getMessage().contains("maxInMemoryBuffers"));
    }

    @Test
    @DisplayName("Default transport profiles handle medium inputs without CLI chunk overrides")
    void defaultTransportProfileHandlesMediumInputsWithoutCliChunkOverride() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("medium-input"));
        Files.write(inputRoot.resolve("alpha.bin"), new byte[9_216]);
        Files.write(inputRoot.resolve("beta.bin"), new byte[8_192]);

        WriterApplicationService service = service(tempDir.resolve("diagnostics"));

        WriterRunResult result = service.run(
                new WriterRunRequest(defaultTransportOverrides(inputRoot, UNPACED_TEST_FPS, false), true),
                WriterJobObserver.noOp()
        );

        assertTrue(result.effectiveConfigJson().contains("\"chunkBytes\" : 512"));
        assertTrue(result.metrics().chunksCreated() > 0);
        assertFalse(result.renderedFrameHashes().isEmpty());
    }

    @Test
    @DisplayName("Oversized chunk overrides fail early with an actionable capacity message")
    void oversizedChunkOverrideFailsFastWithActionableCapacityMessage() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("oversized-transport-input"));
        Files.write(inputRoot.resolve("payload.bin"), new byte[2_048]);

        WriterApplicationService service = service(tempDir.resolve("diagnostics"));

        WriterJobException exception = assertThrows(
                WriterJobException.class,
                () -> service.run(
                        new WriterRunRequest(cliOverrides(inputRoot, 1536, TEST_FPS, false), true),
                        WriterJobObserver.noOp()
                )
        );

        assertEquals(WriterJobStatus.SESSION_PLANNED, exception.status());
        assertTrue(exception.getMessage().contains("payload exceeds supported subset capacity"));
        assertTrue(exception.getMessage().contains("transport.chunkBytes"));
    }

    private WriterApplicationService service(Path diagnosticsRoot) {
        return service(diagnosticsRoot, tempDir.resolve("exports-default"));
    }

    private WriterApplicationService service(Path diagnosticsRoot, Path exportRoot) {
        return new WriterApplicationService(
                () -> FIXED_NOW,
                () -> FIXED_SESSION_ID,
                diagnosticsRoot,
                exportRoot
        );
    }

    private WriterRunRequest dryRunRequest(Path inputRoot) {
        return new WriterRunRequest(cliOverrides(inputRoot, TEST_CHUNK_BYTES, TEST_FPS, false), true);
    }

    private WriterRunRequest unpacedDryRunRequest(Path inputRoot) {
        return new WriterRunRequest(exportFocusedOverrides(inputRoot, null, null), true);
    }

    private WriterRunRequest exportImageSequenceRequest(Path inputRoot) {
        return new WriterRunRequest(exportFocusedOverrides(inputRoot, true, "imageSequence"), true);
    }

    private RuntimeConfigPatch exportFocusedOverrides(Path inputRoot, Boolean exportEnabled, String exportMode) {
        return new RuntimeConfigPatch(
                null,
                new RuntimeConfigPatch.InputPatch(List.of(new RuntimeConfig.InputRootConfig(inputRoot.toString(), null))),
                null,
                null,
                new RuntimeConfigPatch.TransportPatch(null, TEST_CHUNK_BYTES, null, null, null, null, null),
                new RuntimeConfigPatch.PlaybackPatch(UNPACED_TEST_FPS, 0, 0, 0, false),
                (exportEnabled == null && exportMode == null)
                        ? null
                        : new RuntimeConfigPatch.ExportPatch(exportEnabled, exportMode),
                null
        );
    }

    private RuntimeConfigPatch cliOverrides(Path inputRoot, int chunkBytes, int fps, boolean fullscreen) {
        return cliOverrides(inputRoot, chunkBytes, fps, fullscreen, null, null, null, null, null, null, null);
    }

    private WriterJobObserver capturingObserver(
            List<WriterJobEvent> events,
            AtomicReference<WriterJobException> failure
    ) {
        return (WriterJobObserver) Proxy.newProxyInstance(
                WriterJobObserver.class.getClassLoader(),
                new Class<?>[] {WriterJobObserver.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "CapturingWriterJobObserver";
                            default -> null;
                        };
                    }
                    return switch (method.getName()) {
                        case "onEvent" -> {
                            events.add((WriterJobEvent) args[0]);
                            yield null;
                        }
                        case "onFailure" -> {
                            failure.set((WriterJobException) args[0]);
                            yield null;
                        }
                        default -> null;
                    };
                }
        );
    }

    private RuntimeConfigPatch defaultTransportOverrides(Path inputRoot, int fps, boolean fullscreen) {
        return new RuntimeConfigPatch(
                null,
                new RuntimeConfigPatch.InputPatch(List.of(new RuntimeConfig.InputRootConfig(inputRoot.toString(), null))),
                null,
                null,
                null,
                new RuntimeConfigPatch.PlaybackPatch(fps, 0, 0, 0, fullscreen),
                null,
                null
        );
    }

    private RuntimeConfigPatch cliOverrides(
            Path inputRoot,
            int chunkBytes,
            int fps,
            boolean fullscreen,
            Integer maxFileCount,
            Long maxTotalBytes,
            Integer maxManifestBytes,
            Integer maxFrameCount,
            Integer maxInMemoryBuffers,
            Boolean exportEnabled,
            String exportMode
    ) {
        return new RuntimeConfigPatch(
                new RuntimeConfigPatch.AppPatch(
                        null,
                        null,
                        (maxFileCount == null
                                && maxTotalBytes == null
                                && maxManifestBytes == null
                                && maxFrameCount == null
                                && maxInMemoryBuffers == null)
                                ? null
                                : new RuntimeConfigPatch.ResourceLimitsPatch(
                                        maxFileCount,
                                        maxTotalBytes,
                                        maxManifestBytes,
                                        maxFrameCount,
                                        maxInMemoryBuffers
                                )
                ),
                new RuntimeConfigPatch.InputPatch(List.of(new RuntimeConfig.InputRootConfig(inputRoot.toString(), null))),
                null,
                null,
                new RuntimeConfigPatch.TransportPatch(null, chunkBytes, null, null, null, null, null),
                new RuntimeConfigPatch.PlaybackPatch(fps, null, null, null, fullscreen),
                (exportEnabled == null && exportMode == null)
                        ? null
                        : new RuntimeConfigPatch.ExportPatch(exportEnabled, exportMode),
                null
        );
    }

    private LogCapture captureLogs() {
        return new LogCapture(WriterApplicationService.class);
    }

    private static final class LogCapture implements AutoCloseable {

        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender;
        private final Level previousLevel;
        private final boolean previousAdditive;

        private LogCapture(Class<?> loggerType) {
            this.logger = (Logger) LoggerFactory.getLogger(loggerType);
            this.previousLevel = logger.getLevel();
            this.previousAdditive = logger.isAdditive();
            this.appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
        }

        private boolean contains(Level level, String messageFragment) {
            return appender.list.stream().anyMatch(event ->
                    event.getLevel() == level && event.getFormattedMessage().contains(messageFragment)
            );
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
    }
}
