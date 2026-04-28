package pro.alx4j.jab4j.transfer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.alx4j.jab4j.api.model.CodecProfile;
import pro.alx4j.jab4j.api.model.LayoutProfile;
import pro.alx4j.jab4j.api.model.Manifest;
import pro.alx4j.jab4j.api.model.ParityGroupSizingStrategy;
import pro.alx4j.jab4j.api.model.PlaybackProfile;
import pro.alx4j.jab4j.api.model.ProtocolVersion;
import pro.alx4j.jab4j.api.model.SessionId;
import pro.alx4j.jab4j.api.model.SessionProfile;
import pro.alx4j.jab4j.api.model.TransferSession;
import pro.alx4j.jab4j.api.model.TransportProfile;
import pro.alx4j.jab4j.catalog.DeclaredInputRoot;
import pro.alx4j.jab4j.catalog.DeterministicPackager;
import pro.alx4j.jab4j.catalog.PackagingResult;

@DisplayName("Transport session planning")
class TransportSessionPlannerTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-03-22T18:00:00Z");
    private static final ProtocolVersion FIXED_PROTOCOL_VERSION = new ProtocolVersion("1.0", 1);

    private final DeterministicPackager packager = new DeterministicPackager();
    private final TransportSessionPlanner planner = new TransportSessionPlanner();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Chunk plans and logical records remain deterministic")
    void buildsDeterministicChunkPlanAndLogicalRecords() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("payload-root"));
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("alpha.txt"), "alpha");
        Files.write(root.resolve("nested/beta.bin"), new byte[] {9, 8, 7, 6});

        PackagingResult packagingResult = packager.buildPackagingResult(List.of(new DeclaredInputRoot(root, "payload")));
        TransferSession draftSession = draftSession(packagingResult.manifest(), 2, 3, 1, 5, 10, 15, 2, 2);

        TransportSessionPlan first = planner.plan(draftSession, packagingResult);
        TransportSessionPlan second = planner.plan(draftSession, packagingResult);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertFalse(first.finalSessionDigest().isBlank()),
                () -> assertEquals(3, first.manifestSummary().totalFileCount()),
                () -> assertEquals(5, first.manifestSummary().totalLogicalChunkCount()),
                () -> assertEquals(5, first.chunkPayloads().size()),
                () -> assertEquals(List.of(
                        new ParityGroupPlan(0, 3, 1, ParityGroupSizingStrategy.SHORT_LAST_GROUP, 0, 3, 3),
                        new ParityGroupPlan(1, 3, 1, ParityGroupSizingStrategy.SHORT_LAST_GROUP, 3, 5, 2)
                ), first.parityPlan()),
                () -> assertEquals(List.of(
                        TransportRecordCategory.SESSION_HEADER_RECORD,
                        TransportRecordCategory.MANIFEST_RECORD,
                        TransportRecordCategory.FILE_HEADER_RECORD,
                        TransportRecordCategory.FILE_CHUNK_RECORD,
                        TransportRecordCategory.FILE_CHUNK_RECORD,
                        TransportRecordCategory.FILE_CHUNK_RECORD,
                        TransportRecordCategory.FILE_HEADER_RECORD,
                        TransportRecordCategory.FILE_HEADER_RECORD,
                        TransportRecordCategory.FILE_CHUNK_RECORD,
                        TransportRecordCategory.FILE_CHUNK_RECORD,
                        TransportRecordCategory.PARITY_RECORD,
                        TransportRecordCategory.PARITY_RECORD,
                        TransportRecordCategory.SESSION_END_RECORD
                ), recordCategories(first)),
                () -> assertEquals(List.of("al", "ph", "a", "\t\b", "\u0007\u0006"), chunkPayloadTexts(first)),
                () -> assertEquals(List.of(
                        "0:SYNC",
                        "1:SYNC",
                        "2:SESSION_HEADER",
                        "3:SESSION_HEADER",
                        "4:MANIFEST",
                        "5:DATA",
                        "6:SYNC",
                        "7:PARITY",
                        "8:DATA",
                        "9:PARITY",
                        "10:END",
                        "11:END"
                ), frameTimeline(first))
        );
    }

    @Test
    @DisplayName("Manifest mismatches between the session and packaging are rejected")
    void rejectsManifestMismatchBetweenSessionAndPackaging() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("mismatch-root"));
        Files.writeString(root.resolve("alpha.txt"), "alpha");

        PackagingResult packagingResult = packager.buildPackagingResult(List.of(new DeclaredInputRoot(root, "payload")));
        Manifest manifest = packagingResult.manifest();
        Manifest mismatchedManifest = new Manifest(
                manifest.files(),
                manifest.rootAliases(),
                manifest.totalSizeBytes(),
                "other-fingerprint"
        );
        TransferSession draftSession = draftSession(mismatchedManifest, 2);

        TransportException exception = assertThrows(
                TransportException.class,
                () -> planner.plan(draftSession, packagingResult)
        );

        assertEquals("Packaging result manifest must match the transfer session manifest", exception.getMessage());
    }

    @Test
    @DisplayName("Sync and metadata anchors replay at the configured cadence")
    void replaysSyncAndMetadataAnchorsAtConfiguredCadence() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("cadence-root"));
        Files.write(root.resolve("payload.bin"), new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8});

        PackagingResult packagingResult = packager.buildPackagingResult(List.of(new DeclaredInputRoot(root, "payload")));
        TransferSession draftSession = draftSession(packagingResult.manifest(), 1, 2, 1, 2, 3, 4, 2, 2);

        TransportSessionPlan plan = planner.plan(draftSession, packagingResult);

        assertTrue(countFrames(plan, "SYNC") > 1);
        assertTrue(countFrames(plan, "SESSION_HEADER") > 1);
        assertTrue(countFrames(plan, "MANIFEST") > 1);
        assertTrue(maxFrameGap(plan, "SYNC") <= 2);
        assertTrue(maxFrameGap(plan, "SESSION_HEADER") <= 3);
        assertTrue(maxFrameGap(plan, "MANIFEST") <= 4);
    }

    @Test
    @DisplayName("Large manifests fragment into multiple deterministic records")
    void fragmentsLargeManifestsIntoMultipleDeterministicRecords() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("manifest-fragment-root"));
        for (int index = 0; index < 18; index++) {
            Files.writeString(root.resolve("file-%02d.txt".formatted(index)), "x");
        }

        PackagingResult packagingResult = packager.buildPackagingResult(List.of(new DeclaredInputRoot(root, "payload")));
        TransportSessionPlan plan = planner.plan(draftSession(packagingResult.manifest(), 32), packagingResult);

        long manifestRecordCount = plan.records().stream()
                .filter(record -> record instanceof ManifestRecord)
                .count();

        assertTrue(manifestRecordCount > 1);
        assertTrue(plan.frameDescriptors().stream()
                .filter(frame -> frame.frameType().name().equals("MANIFEST"))
                .flatMap(frame -> frame.tiles().stream())
                .allMatch(tile -> tile.body().length <= 768));
    }

    private TransferSession draftSession(Manifest manifest, int chunkBytes) {
        return draftSession(manifest, chunkBytes, 3, 1, 5, 10, 15, 6, 6);
    }

    private TransferSession draftSession(
            Manifest manifest,
            int chunkBytes,
            int dataShardsPerGroup,
            int parityShardsPerGroup,
            int syncEveryFrames,
            int sessionHeaderRepeatEveryFrames,
            int manifestRepeatEveryFrames,
            int warmupSyncFrames,
            int endFrames
    ) {
        return new TransferSession(
                FIXED_SESSION_ID,
                FIXED_CREATED_AT,
                FIXED_PROTOCOL_VERSION,
                "build-transport-1",
                new SessionProfile(
                        "desktop-safe",
                        new LayoutProfile("desktop-1080p-safe", 2, 2, 1920, 1080, 24, 48, "solidWhite", 64, 32, "black", "preserveAspect"),
                        new CodecProfile("balanced-v1", "binary", true),
                        new TransportProfile(
                                "safe-v1",
                                FIXED_PROTOCOL_VERSION,
                                chunkBytes,
                                dataShardsPerGroup,
                                parityShardsPerGroup,
                                ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                                syncEveryFrames,
                                sessionHeaderRepeatEveryFrames,
                                manifestRepeatEveryFrames
                        ),
                        new PlaybackProfile("desktop-safe", 8, 1, warmupSyncFrames, endFrames, true)
                ),
                manifest,
                manifest.files()
        );
    }

    private List<TransportRecordCategory> recordCategories(TransportSessionPlan plan) {
        return plan.records().stream()
                .map(TransportRecord::category)
                .toList();
    }

    private List<String> chunkPayloadTexts(TransportSessionPlan plan) {
        return plan.chunkPayloads().stream()
                .map(chunkPayload -> new String(chunkPayload.payload(), StandardCharsets.ISO_8859_1))
                .toList();
    }

    private List<String> frameTimeline(TransportSessionPlan plan) {
        return plan.frameDescriptors().stream()
                .map(frame -> frame.frameIndex() + ":" + frame.frameType())
                .toList();
    }

    private long maxFrameGap(TransportSessionPlan plan, String frameType) {
        long previous = -1;
        long maxGap = 0;
        for (var frame : plan.frameDescriptors()) {
            if (!frame.frameType().name().equals(frameType)) {
                continue;
            }
            if (previous >= 0) {
                maxGap = Math.max(maxGap, frame.frameIndex() - previous);
            }
            previous = frame.frameIndex();
        }
        return maxGap;
    }

    private long countFrames(TransportSessionPlan plan, String frameType) {
        return plan.frameDescriptors().stream()
                .filter(frame -> frame.frameType().name().equals(frameType))
                .count();
    }
}
