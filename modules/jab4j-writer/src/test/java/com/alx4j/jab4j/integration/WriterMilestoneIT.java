package com.alx4j.jab4j.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.writer.config.RuntimeConfig;
import com.alx4j.jab4j.writer.config.RuntimeConfigPatch;
import com.alx4j.jab4j.writer.app.WriterApplicationService;
import com.alx4j.jab4j.writer.app.WriterJobObserver;
import com.alx4j.jab4j.writer.app.WriterRunRequest;
import com.alx4j.jab4j.writer.app.WriterRunResult;

@DisplayName("Writer milestone integration scenarios")
class WriterMilestoneIT {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-22T20:00:00Z");
    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final String SAFE_PROFILE = "desktop-1080p-safe";
    private static final String DEBUG_PROFILE = "debug-low-density";
    private static final int DEFAULT_CHUNK_BYTES = 4;
    private static final int UNPACED_TEST_FPS = 10_000;

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeCorpora")
    @DisplayName("Representative corpora replay deterministically end to end")
    void representativeCorporaReplayDeterministicallyEndToEnd(CorpusCase corpus) throws Exception {
        Path inputRoot = createCorpus(tempDir.resolve(corpus.name()), corpus);
        WriterApplicationService service = service(tempDir.resolve("diagnostics-" + corpus.name()));
        WriterRunRequest request = request(inputRoot, SAFE_PROFILE);

        WriterRunResult first = service.run(request, WriterJobObserver.noOp());
        WriterRunResult second = service.run(request, WriterJobObserver.noOp());

        assertAll(corpus.name(),
                () -> assertEquals(first.effectiveConfigJson(), second.effectiveConfigJson()),
                () -> assertEquals(first.finalSessionDigest(), second.finalSessionDigest()),
                () -> assertEquals(first.renderedFrameHashes(), second.renderedFrameHashes()),
                () -> assertEquals(first.reproducibilityMetadata(), second.reproducibilityMetadata()),
                () -> assertEquals(corpus.expectedFilesScanned(), first.metrics().filesScanned()),
                () -> assertEquals(corpus.expectedBytesScanned(), first.metrics().bytesScanned()),
                () -> assertTrue(first.metrics().chunksCreated() > 0),
                () -> assertTrue(first.metrics().parityShardsCreated() > 0),
                () -> assertEquals(first.renderedFrameHashes().size(), first.metrics().framesRendered()),
                () -> assertEquals(0L, first.metrics().validationFailures()),
                () -> assertTrue(Files.exists(first.artifacts().artifactFiles().get("effectiveConfig"))),
                () -> assertTrue(Files.exists(first.artifacts().artifactFiles().get("manifestDump"))),
                () -> assertTrue(Files.exists(first.artifacts().artifactFiles().get("sessionPlan"))),
                () -> assertTrue(Files.exists(first.artifacts().artifactFiles().get("frameMetadataLog")))
        );
    }

    private static Stream<CorpusCase> representativeCorpora() {
        return Stream.of(
                new CorpusCase("empty-and-one-byte", 2L, 1L, root -> {
                    Files.write(root.resolve("empty.bin"), new byte[0]);
                    Files.write(root.resolve("one-byte.bin"), new byte[] {0x5A});
                }),
                new CorpusCase("chunk-boundary", 1L, 4L, root ->
                        Files.write(root.resolve("chunk-boundary.bin"), repeatedBytes(4, (byte) 0x11))),
                new CorpusCase("spanning", 1L, 16L, root ->
                        Files.write(root.resolve("spanning.bin"), repeatedBytes(16, (byte) 0x33)))
        );
    }

    @Test
    @DisplayName("Switching profiles keeps deterministic replays but changes outputs")
    void switchingProfilesKeepsDeterminismButChangesArtifacts() throws Exception {
        Path inputRoot = createCorpus(tempDir.resolve("profile-corpus"),
                new CorpusCase("profile", 1L, 4L, root ->
                        Files.write(root.resolve("boundary.bin"), repeatedBytes(4, (byte) 0x22))));
        WriterApplicationService defaultService = service(tempDir.resolve("diagnostics-default"));
        WriterApplicationService debugService = service(tempDir.resolve("diagnostics-debug"));

        WriterRunResult defaultRun = defaultService.run(request(inputRoot, SAFE_PROFILE), WriterJobObserver.noOp());
        WriterRunResult debugFirst = debugService.run(request(inputRoot, DEBUG_PROFILE), WriterJobObserver.noOp());
        WriterRunResult debugSecond = debugService.run(request(inputRoot, DEBUG_PROFILE), WriterJobObserver.noOp());

        assertAll(
                () -> assertEquals(debugFirst.finalSessionDigest(), debugSecond.finalSessionDigest()),
                () -> assertEquals(debugFirst.renderedFrameHashes(), debugSecond.renderedFrameHashes()),
                () -> assertNotEquals(defaultRun.finalSessionDigest(), debugFirst.finalSessionDigest()),
                () -> assertNotEquals(defaultRun.renderedFrameHashes(), debugFirst.renderedFrameHashes())
        );
    }

    @Test
    @DisplayName("Image-sequence exports preserve logical session artifacts")
    void imageSequenceExportPreservesLogicalSessionArtifacts() throws Exception {
        Path inputRoot = createCorpus(tempDir.resolve("export-corpus"),
                new CorpusCase("export", 1L, 4L, root ->
                        Files.write(root.resolve("export.bin"), repeatedBytes(4, (byte) 0x44))));
        WriterApplicationService baselineService = service(
                tempDir.resolve("diagnostics-export-baseline"),
                tempDir.resolve("exports-baseline")
        );
        WriterApplicationService exportService = service(
                tempDir.resolve("diagnostics-export-enabled"),
                tempDir.resolve("exports-enabled")
        );

        WriterRunResult baseline = baselineService.run(
                request(inputRoot, SAFE_PROFILE),
                WriterJobObserver.noOp()
        );
        WriterRunResult firstExport = exportService.run(
                request(inputRoot, SAFE_PROFILE, true, "imageSequence"),
                WriterJobObserver.noOp()
        );

        assertAll(
                () -> assertTrue(baseline.exportArtifacts().exportedFiles().isEmpty()),
                () -> assertEquals(baseline.finalSessionDigest(), firstExport.finalSessionDigest()),
                () -> assertEquals(baseline.renderedFrameHashes(), firstExport.renderedFrameHashes()),
                () -> assertTrue(Files.exists(firstExport.exportArtifacts().exportedFiles().get("frameSequence"))),
                () -> assertTrue(
                        firstExport.exportArtifacts().exportedFiles().keySet().stream().anyMatch(key -> key.startsWith("image-"))
                ),
                () -> assertEquals(
                        firstExport.renderedFrameHashes().size() + 1,
                        firstExport.exportArtifacts().exportedFiles().size()
                ),
                () -> assertTrue(firstExport.exportArtifacts().exportedFiles().values().stream().allMatch(Files::exists))
        );
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

    private WriterRunRequest request(Path inputRoot, String profileId) {
        return request(inputRoot, profileId, null, null);
    }

    private WriterRunRequest request(
            Path inputRoot,
            String profileId,
            Boolean exportEnabled,
            String exportMode
    ) {
        return new WriterRunRequest(
                new RuntimeConfigPatch(
                        new RuntimeConfigPatch.AppPatch(profileId, null, null),
                        new RuntimeConfigPatch.InputPatch(List.of(new RuntimeConfig.InputRootConfig(inputRoot.toString(), null))),
                        null,
                        null,
                        new RuntimeConfigPatch.TransportPatch(null, DEFAULT_CHUNK_BYTES, null, null, null, null, null),
                        new RuntimeConfigPatch.PlaybackPatch(UNPACED_TEST_FPS, 0, 0, 0, false),
                        (exportEnabled == null && exportMode == null)
                                ? null
                                : new RuntimeConfigPatch.ExportPatch(exportEnabled, exportMode),
                        new RuntimeConfigPatch.DiagnosticsPatch(false, true, true)
                ),
                true
        );
    }

    private Path createCorpus(Path root, CorpusCase corpus) throws Exception {
        Files.createDirectories(root);
        corpus.write(root);
        return root;
    }

    private static byte[] repeatedBytes(int length, byte value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private record CorpusCase(
            String name,
            long expectedFilesScanned,
            long expectedBytesScanned,
            CorpusWriter writer
    ) {

        void write(Path root) throws Exception {
            writer.write(root);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @FunctionalInterface
    private interface CorpusWriter {

        void write(Path root) throws Exception;
    }
}
