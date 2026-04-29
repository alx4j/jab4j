package com.alx4j.jab4j.reader.restore;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.CodecProfile;
import com.alx4j.jab4j.api.model.FileRecord;
import com.alx4j.jab4j.api.model.FileType;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.Manifest;
import com.alx4j.jab4j.api.model.ParityGroupSizingStrategy;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.PlaybackProfile;
import com.alx4j.jab4j.api.model.ProtocolVersion;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.SessionProfile;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.api.model.TransferSession;
import com.alx4j.jab4j.api.model.TransportProfile;
import com.alx4j.jab4j.catalog.DeclaredInputRoot;
import com.alx4j.jab4j.catalog.DeterministicPackager;
import com.alx4j.jab4j.catalog.PackagingResult;
import com.alx4j.jab4j.reader.content.DecodedFrameContent;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.transfer.TransportSessionPlan;
import com.alx4j.jab4j.transfer.TransportSessionPlanner;

@DisplayName("Reader restore service")
class ReaderRestoreServiceTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-03-22T18:00:00Z");
    private static final ProtocolVersion FIXED_PROTOCOL_VERSION = new ProtocolVersion("1.0", 1);
    private static final LayoutProfile DEFAULT_LAYOUT = new LayoutProfile(
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
    );

    private final ReaderRestoreService restoreService = new ReaderRestoreService();
    private final DeterministicPackager packager = new DeterministicPackager();
    private final TransportSessionPlanner planner = new TransportSessionPlanner();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Writer-produced decoded payloads restore directories, non-empty files, and empty files")
    void writerProducedDecodedPayloadsRestoreCompletePackage() throws IOException {
        Path sourceRoot = sourceTree("success-source");
        RestoreFixture fixture = writerFixture(sourceRoot, "payload", 5);
        Path output = tempDir.resolve("restore-success");

        ReaderRestoreResult result = restore(fixture, output);

        assertAll(
                () -> assertEquals(ReaderRestoreStatus.RESTORED, result.status()),
                () -> assertTrue(result.restored()),
                () -> assertEquals(FIXED_SESSION_ID, result.sessionId()),
                () -> assertEquals(output.toAbsolutePath().normalize(), result.outputDirectory()),
                () -> assertEquals(2, result.restoredFileCount()),
                () -> assertEquals(2, result.restoredDirectoryCount()),
                () -> assertEquals(Files.size(sourceRoot.resolve("docs/alpha.txt")), result.totalRestoredBytes()),
                () -> assertTrue(hasRepeatedBody(fixture.content(), PayloadKind.SESSION_HEADER, 1)),
                () -> assertTrue(hasRepeatedBody(fixture.content(), PayloadKind.MANIFEST_FRAGMENT, DEFAULT_LAYOUT.rows() * DEFAULT_LAYOUT.cols())),
                () -> assertTrue(Files.isDirectory(output.resolve("payload/docs"))),
                () -> assertTrue(Files.isDirectory(output.resolve("payload/empty-dir"))),
                () -> assertArrayEquals(
                        Files.readAllBytes(sourceRoot.resolve("docs/alpha.txt")),
                        Files.readAllBytes(output.resolve("payload/docs/alpha.txt"))
                ),
                () -> assertEquals(0, Files.size(output.resolve("payload/empty.bin")))
        );
    }

    @Test
    @DisplayName("Parity shard payload bodies are ignored for complete-input restore")
    void corruptedParityPayloadsAreIgnored() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("parity-source"), "payload", 5);
        DecodedFrameSetContent content = transformPayloads(fixture.content(), payload -> {
            if (payload.payloadKind() == PayloadKind.PARITY_SHARD) {
                return withBody(payload, bytes("corrupted parity payload"));
            }
            return payload;
        });

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), tempDir.resolve("restore-parity"));

        assertEquals(ReaderRestoreStatus.RESTORED, result.status());
    }

    @Test
    @DisplayName("Missing manifest fragments fail before publishing output")
    void missingManifestFragmentFails() throws IOException {
        RestoreFixture fixture = writerFixture(largeManifestSource("fragment-source"), "payload", 8);
        assertTrue(manifestFragmentIndexes(fixture.content()).size() > 1);
        DecodedFrameSetContent content = filterPayloads(
                fixture.content(),
                payload -> payload.payloadKind() != PayloadKind.MANIFEST_FRAGMENT
                        || !text(payload.body()).contains("fragmentIndex=1\n")
        );
        Path output = tempDir.resolve("restore-missing-fragment");

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), output);

        assertAll(
                () -> assertEquals(ReaderRestoreStatus.INCOMPLETE_CONTENT, result.status()),
                () -> assertFalse(result.restored()),
                () -> assertFalse(Files.exists(output.resolve("payload")))
        );
    }

    @Test
    @DisplayName("Missing declared file chunks fail before publishing output")
    void missingDeclaredChunkFails() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("missing-chunk-source"), "payload", 5);
        ChunkIdentity firstChunk = firstChunk(fixture.plan().session().manifest());
        DecodedFrameSetContent content = filterPayloads(
                fixture.content(),
                payload -> payload.payloadKind() != PayloadKind.FILE_CHUNK || !isChunk(payload, firstChunk)
        );
        Path output = tempDir.resolve("restore-missing-chunk");

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), output);

        assertAll(
                () -> assertEquals(ReaderRestoreStatus.INCOMPLETE_CONTENT, result.status()),
                () -> assertFalse(Files.exists(output.resolve("payload")))
        );
    }

    @Test
    @DisplayName("File chunk headers must match manifest chunk metadata")
    void chunkHeaderMismatchFails() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("header-mismatch-source"), "payload", 5);
        ChunkIdentity firstChunk = firstChunk(fixture.plan().session().manifest());
        boolean[] changed = {false};
        DecodedFrameSetContent content = transformPayloads(fixture.content(), payload -> {
            if (!changed[0] && payload.payloadKind() == PayloadKind.FILE_CHUNK && isChunk(payload, firstChunk)) {
                changed[0] = true;
                return withBody(payload, replaceChunkHeader(payload.body(), "offset=0", "offset=1"));
            }
            return payload;
        });

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), tempDir.resolve("restore-header-mismatch"));

        assertEquals(ReaderRestoreStatus.INCONSISTENT_CONTENT, result.status());
    }

    @Test
    @DisplayName("File chunk CRC32C must match raw payload bytes")
    void chunkCrcMismatchFails() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("crc-mismatch-source"), "payload", 5);
        ChunkIdentity firstChunk = firstChunk(fixture.plan().session().manifest());
        boolean[] changed = {false};
        DecodedFrameSetContent content = transformPayloads(fixture.content(), payload -> {
            if (!changed[0] && payload.payloadKind() == PayloadKind.FILE_CHUNK && isChunk(payload, firstChunk)) {
                changed[0] = true;
                return withBody(payload, corruptFirstChunkPayloadByte(payload.body()));
            }
            return payload;
        });

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), tempDir.resolve("restore-crc-mismatch"));

        assertEquals(ReaderRestoreStatus.INCONSISTENT_CONTENT, result.status());
    }

    @Test
    @DisplayName("Undeclared file chunks are rejected")
    void undeclaredFileChunkFails() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("extra-chunk-source"), "payload", 5);
        ChunkIdentity firstChunk = firstChunk(fixture.plan().session().manifest());
        boolean[] changed = {false};
        DecodedFrameSetContent content = transformPayloads(fixture.content(), payload -> {
            if (!changed[0] && payload.payloadKind() == PayloadKind.FILE_CHUNK && isChunk(payload, firstChunk)) {
                changed[0] = true;
                return withBody(
                        payload,
                        replaceChunkHeader(payload.body(), "fileIndex=" + firstChunk.fileIndex(), "fileIndex=99")
                );
            }
            return payload;
        });

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), tempDir.resolve("restore-extra-chunk"));

        assertEquals(ReaderRestoreStatus.INCONSISTENT_CONTENT, result.status());
    }

    @Test
    @DisplayName("Unsafe manifest paths are rejected")
    void unsafeManifestPathFails() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("unsafe-source"), "payload", 5);
        DecodedFrameSetContent unsafeContent = rewriteManifest(
                fixture,
                files -> files.stream()
                        .map(file -> "docs/alpha.txt".equals(file.relativePath())
                                ? new FileRecord(file.rootAlias(), "../evil.txt", file.fileType(), file.sizeBytes(), file.sha256(), file.chunks())
                                : file)
                        .toList(),
                text -> text.replace("\tdocs/alpha.txt\t", "\t../evil.txt\t")
        );

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), unsafeContent), tempDir.resolve("restore-unsafe"));

        assertEquals(ReaderRestoreStatus.UNSAFE_PATH, result.status());
    }

    @Test
    @DisplayName("Existing final output paths are treated as conflicts")
    void outputConflictFailsWithoutOverwrite() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("conflict-source"), "payload", 5);
        Path output = tempDir.resolve("restore-conflict");
        Files.createDirectories(output.resolve("payload"));
        Files.writeString(output.resolve("payload/existing.txt"), "keep");

        ReaderRestoreResult result = restore(fixture, output);

        assertAll(
                () -> assertEquals(ReaderRestoreStatus.OUTPUT_CONFLICT, result.status()),
                () -> assertEquals("keep", Files.readString(output.resolve("payload/existing.txt")))
        );
    }

    @Test
    @DisplayName("SESSION_END finalSessionDigest must match the accepted frame-set digest")
    void sessionEndDigestMismatchFails() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("digest-source"), "payload", 5);
        DecodedFrameSetContent content = transformPayloads(fixture.content(), payload -> {
            if (payload.payloadKind() == PayloadKind.SESSION_END) {
                String updated = text(payload.body()).replace(
                        "finalSessionDigest=" + fixture.plan().finalSessionDigest(),
                        "finalSessionDigest=bad-digest"
                );
                return withBody(payload, bytes(updated));
            }
            return payload;
        });

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), tempDir.resolve("restore-digest"));

        assertEquals(ReaderRestoreStatus.INCONSISTENT_CONTENT, result.status());
    }

    @Test
    @DisplayName("Staging validation failures do not publish final output")
    void stagingValidationFailureDoesNotPublishOutput() throws IOException {
        RestoreFixture fixture = writerFixture(sourceTree("staging-source"), "payload", 5);
        String badSha256 = "0".repeat(64);
        DecodedFrameSetContent content = rewriteManifest(
                fixture,
                files -> files.stream()
                        .map(file -> "docs/alpha.txt".equals(file.relativePath())
                                ? new FileRecord(file.rootAlias(), file.relativePath(), file.fileType(), file.sizeBytes(), badSha256, file.chunks())
                                : file)
                        .toList(),
                text -> text.replace(shaFor(fixture.plan().session().manifest(), "docs/alpha.txt"), badSha256)
        );
        Path output = tempDir.resolve("restore-staging-failure");

        ReaderRestoreResult result = restore(new RestoreFixture(fixture.plan(), content), output);

        assertAll(
                () -> assertEquals(ReaderRestoreStatus.INCONSISTENT_CONTENT, result.status()),
                () -> assertFalse(result.restored()),
                () -> assertFalse(Files.exists(output.resolve("payload"))),
                () -> assertFalse(Files.exists(output))
        );
    }

    private ReaderRestoreResult restore(RestoreFixture fixture, Path output) {
        return restoreService.restore(new ReaderRestoreRequest(
                FIXED_SESSION_ID,
                fixture.plan().finalSessionDigest(),
                fixture.content(),
                output
        ));
    }

    private Path sourceTree(String directoryName) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve(directoryName));
        Files.createDirectories(root.resolve("docs"));
        Files.createDirectories(root.resolve("empty-dir"));
        Files.write(root.resolve("docs/alpha.txt"), bytes("alpha beta gamma"));
        Files.write(root.resolve("empty.bin"), new byte[0]);
        return root;
    }

    private Path largeManifestSource(String directoryName) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve(directoryName));
        for (int index = 0; index < 32; index++) {
            Files.writeString(root.resolve("file-with-a-long-name-%02d.txt".formatted(index)), "x");
        }
        return root;
    }

    private RestoreFixture writerFixture(Path root, String alias, int chunkBytes) {
        PackagingResult packagingResult = packager.buildPackagingResult(List.of(new DeclaredInputRoot(root, alias)));
        TransportSessionPlan plan = planner.plan(draftSession(packagingResult.manifest(), chunkBytes), packagingResult);
        return new RestoreFixture(plan, decodedContent(plan));
    }

    private TransferSession draftSession(Manifest manifest, int chunkBytes) {
        return new TransferSession(
                FIXED_SESSION_ID,
                FIXED_CREATED_AT,
                FIXED_PROTOCOL_VERSION,
                "build-reader-restore-test",
                new SessionProfile(
                        "desktop-safe",
                        DEFAULT_LAYOUT,
                        new CodecProfile("balanced-v1", "binary", true),
                        new TransportProfile(
                                "safe-v1",
                                FIXED_PROTOCOL_VERSION,
                                chunkBytes,
                                2,
                                1,
                                ParityGroupSizingStrategy.SHORT_LAST_GROUP,
                                2,
                                3,
                                2
                        ),
                        new PlaybackProfile("desktop-safe", 8, 1, 2, 2, true)
                ),
                manifest,
                manifest.files()
        );
    }

    private DecodedFrameSetContent decodedContent(TransportSessionPlan plan) {
        List<DecodedFrameContent> frames = plan.frameDescriptors().stream()
                .map(this::decodedFrame)
                .toList();
        return new DecodedFrameSetContent(FIXED_SESSION_ID, DEFAULT_LAYOUT.profileId(), frames);
    }

    private DecodedFrameContent decodedFrame(FrameDescriptor frame) {
        return new DecodedFrameContent(
                frame.frameIndex(),
                frame.frameType(),
                DEFAULT_LAYOUT.profileId(),
                frame.tiles()
        );
    }

    private DecodedFrameSetContent filterPayloads(DecodedFrameSetContent content, Predicate<TilePayload> predicate) {
        List<DecodedFrameContent> frames = content.frames().stream()
                .map(frame -> new DecodedFrameContent(
                        frame.frameIndex(),
                        frame.frameType(),
                        frame.layoutProfileId(),
                        frame.tilePayloads().stream().filter(predicate).toList()
                ))
                .toList();
        return new DecodedFrameSetContent(content.sessionId(), content.layoutProfileId(), frames);
    }

    private DecodedFrameSetContent transformPayloads(DecodedFrameSetContent content, UnaryOperator<TilePayload> transformer) {
        List<DecodedFrameContent> frames = content.frames().stream()
                .map(frame -> new DecodedFrameContent(
                        frame.frameIndex(),
                        frame.frameType(),
                        frame.layoutProfileId(),
                        frame.tilePayloads().stream().map(transformer).toList()
                ))
                .toList();
        return new DecodedFrameSetContent(content.sessionId(), content.layoutProfileId(), frames);
    }

    private DecodedFrameSetContent rewriteManifest(
            RestoreFixture fixture,
            UnaryOperator<List<FileRecord>> fileMutator,
            UnaryOperator<String> manifestTextMutator
    ) {
        Manifest manifest = fixture.plan().session().manifest();
        List<FileRecord> mutatedFiles = fileMutator.apply(manifest.files());
        String oldFingerprint = manifest.manifestFingerprint();
        String newFingerprint = manifestFingerprint(manifest.rootAliases(), mutatedFiles);
        return transformPayloads(fixture.content(), payload -> {
            if (payload.payloadKind() == PayloadKind.SYNC_METADATA || payload.payloadKind() == PayloadKind.SESSION_HEADER) {
                return withBody(payload, bytes(text(payload.body()).replace(oldFingerprint, newFingerprint)));
            }
            if (payload.payloadKind() == PayloadKind.MANIFEST_FRAGMENT) {
                String updated = manifestTextMutator.apply(text(payload.body())).replace(oldFingerprint, newFingerprint);
                return withBody(payload, bytes(updated));
            }
            return payload;
        });
    }

    private boolean hasRepeatedBody(DecodedFrameSetContent content, PayloadKind payloadKind, int threshold) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DecodedFrameContent frame : content.frames()) {
            for (TilePayload payload : frame.tilePayloads()) {
                if (payload.payloadKind() == payloadKind) {
                    String body = text(payload.body());
                    counts.put(body, counts.getOrDefault(body, 0) + 1);
                }
            }
        }
        return counts.values().stream().anyMatch(count -> count > threshold);
    }

    private List<Integer> manifestFragmentIndexes(DecodedFrameSetContent content) {
        return content.frames().stream()
                .flatMap(frame -> frame.tilePayloads().stream())
                .filter(payload -> payload.payloadKind() == PayloadKind.MANIFEST_FRAGMENT)
                .map(payload -> text(payload.body()))
                .map(body -> body.lines()
                        .filter(line -> line.startsWith("fragmentIndex="))
                        .findFirst()
                        .orElseThrow()
                        .substring("fragmentIndex=".length()))
                .map(Integer::parseInt)
                .distinct()
                .sorted()
                .toList();
    }

    private ChunkIdentity firstChunk(Manifest manifest) {
        return manifest.files().stream()
                .filter(file -> file.fileType() == FileType.REGULAR_FILE)
                .flatMap(file -> file.chunks().stream())
                .findFirst()
                .map(chunk -> new ChunkIdentity(chunk.fileIndex(), chunk.chunkIndex()))
                .orElseThrow();
    }

    private boolean isChunk(TilePayload payload, ChunkIdentity chunk) {
        String header = chunkHeader(payload.body());
        return header.contains("fileIndex=" + chunk.fileIndex() + "\n")
                && header.contains("chunkIndex=" + chunk.chunkIndex() + "\n");
    }

    private byte[] replaceChunkHeader(byte[] body, String expected, String replacement) {
        int separator = headerSeparator(body);
        String header = text(Arrays.copyOfRange(body, 0, separator));
        String updatedHeader = header.replace(expected, replacement);
        byte[] payload = Arrays.copyOfRange(body, separator + 2, body.length);
        byte[] updatedHeaderBytes = bytes(updatedHeader + "\n\n");
        byte[] updated = new byte[updatedHeaderBytes.length + payload.length];
        System.arraycopy(updatedHeaderBytes, 0, updated, 0, updatedHeaderBytes.length);
        System.arraycopy(payload, 0, updated, updatedHeaderBytes.length, payload.length);
        return updated;
    }

    private byte[] corruptFirstChunkPayloadByte(byte[] body) {
        int separator = headerSeparator(body);
        byte[] updated = body.clone();
        int payloadStart = separator + 2;
        updated[payloadStart] = (byte) (updated[payloadStart] ^ 0x01);
        return updated;
    }

    private String chunkHeader(byte[] body) {
        return text(Arrays.copyOfRange(body, 0, headerSeparator(body)));
    }

    private int headerSeparator(byte[] body) {
        for (int index = 0; index < body.length - 1; index++) {
            if (body[index] == '\n' && body[index + 1] == '\n') {
                return index;
            }
        }
        throw new AssertionError("chunk body is missing header separator");
    }

    private TilePayload withBody(TilePayload payload, byte[] body) {
        return new TilePayload(
                payload.protocolCompatibilityVersion(),
                payload.sessionId(),
                payload.frameType(),
                payload.frameIndex(),
                payload.tileIndex(),
                payload.totalTilesInFrame(),
                payload.layoutProfileId(),
                payload.payloadKind(),
                payload.payloadSequenceNumber(),
                body.length,
                crc32c(body),
                payload.flags(),
                body
        );
    }

    private String shaFor(Manifest manifest, String relativePath) {
        return manifest.files().stream()
                .filter(file -> relativePath.equals(file.relativePath()))
                .findFirst()
                .orElseThrow()
                .sha256();
    }

    private String manifestFingerprint(List<String> rootAliases, List<FileRecord> files) {
        StringBuilder builder = new StringBuilder();
        for (String rootAlias : rootAliases) {
            builder.append("root\t").append(rootAlias).append('\n');
        }
        for (FileRecord file : files) {
            builder.append(file.rootAlias()).append('\t')
                    .append(file.relativePath()).append('\t')
                    .append(file.fileType().name()).append('\t')
                    .append(file.sizeBytes()).append('\t')
                    .append(file.sha256() == null ? "-" : file.sha256())
                    .append('\n');
        }
        return sha256Hex(bytes(builder.toString()));
    }

    private int crc32c(byte[] bytes) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record RestoreFixture(TransportSessionPlan plan, DecodedFrameSetContent content) {
    }

    private record ChunkIdentity(long fileIndex, long chunkIndex) {
    }
}
