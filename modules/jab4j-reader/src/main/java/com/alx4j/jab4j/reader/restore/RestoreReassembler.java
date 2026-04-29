package com.alx4j.jab4j.reader.restore;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import com.alx4j.jab4j.api.model.FileChunk;
import com.alx4j.jab4j.api.model.FileRecord;
import com.alx4j.jab4j.api.model.FileType;
import com.alx4j.jab4j.api.model.Manifest;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.content.DecodedFrameContent;

/**
 * Reassembles decoded transfer payload bodies into one validated restore plan.
 */
final class RestoreReassembler {

    private static final Pattern SHA_256_HEX_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> SYNC_KEYS = Set.of(
            "type",
            "sessionId",
            "protocol",
            "compatibilityVersion",
            "layoutProfileId",
            "transportProfileId",
            "manifestFingerprint"
    );
    private static final Set<String> SESSION_HEADER_KEYS = Set.of(
            "type",
            "sessionId",
            "createdAt",
            "protocol",
            "compatibilityVersion",
            "writerBuildId",
            "layoutProfileId",
            "codecProfileId",
            "transportProfileId",
            "totalFileCount",
            "totalLogicalChunkCount",
            "totalSizeBytes",
            "manifestFingerprint",
            "dataShardsPerGroup",
            "parityShardsPerGroup",
            "parityGroupSizingStrategy"
    );
    private static final Set<String> SESSION_END_KEYS = Set.of(
            "type",
            "totalDataRecords",
            "totalParityRecords",
            "finalSessionDigest"
    );

    /**
     * Parses and validates decoded payloads before any filesystem writes occur.
     *
     * @param request decoded restore request
     * @return validated restore plan
     */
    RestorePlan reassemble(ReaderRestoreRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!request.sessionId().equals(request.decodedContent().sessionId())) {
            throw failure(
                    ReaderRestoreStatus.INVALID_DECODED_CONTENT,
                    "Decoded content sessionId does not match accepted frame-set sessionId"
            );
        }

        ParsedMetadata syncMetadata = null;
        ParsedMetadata sessionHeader = null;
        ParsedMetadata sessionEnd = null;
        Map<Integer, ManifestFragment> manifestFragments = new LinkedHashMap<>();
        Map<ChunkKey, RestoredChunk> chunks = new LinkedHashMap<>();

        for (DecodedFrameContent frame : request.decodedContent().frames()) {
            for (TilePayload payload : frame.tilePayloads()) {
                validateDecodedPayload(request, frame, payload);
                PayloadKind payloadKind = payload.payloadKind();
                if (payloadKind == PayloadKind.PARITY_SHARD) {
                    continue;
                }
                if (payloadKind == PayloadKind.SYNC_METADATA) {
                    syncMetadata = mergeMetadata(
                            "SYNC_METADATA",
                            syncMetadata,
                            parseMetadata(payload.body(), "SYNC", SYNC_KEYS, SYNC_KEYS)
                    );
                } else if (payloadKind == PayloadKind.SESSION_HEADER) {
                    sessionHeader = mergeMetadata(
                            "SESSION_HEADER",
                            sessionHeader,
                            parseMetadata(payload.body(), "SESSION_HEADER", SESSION_HEADER_KEYS, SESSION_HEADER_KEYS)
                    );
                } else if (payloadKind == PayloadKind.MANIFEST_FRAGMENT) {
                    mergeManifestFragment(manifestFragments, parseManifestFragment(payload.body()));
                } else if (payloadKind == PayloadKind.FILE_CHUNK) {
                    mergeFileChunk(chunks, parseFileChunk(payload.body()));
                } else if (payloadKind == PayloadKind.SESSION_END) {
                    sessionEnd = mergeMetadata(
                            "SESSION_END",
                            sessionEnd,
                            parseMetadata(payload.body(), "END", SESSION_END_KEYS, SESSION_END_KEYS)
                    );
                }
            }
        }

        Manifest manifest = reconstructManifest(manifestFragments);
        validateMetadata(request, manifest, syncMetadata, sessionHeader, sessionEnd);
        validateChunks(manifest, chunks);
        return createRestorePlan(request, manifest, chunks);
    }

    private void validateDecodedPayload(ReaderRestoreRequest request, DecodedFrameContent frame, TilePayload payload) {
        if (!request.sessionId().equals(payload.sessionId())) {
            throw failure(
                    ReaderRestoreStatus.INVALID_DECODED_CONTENT,
                    "Decoded payload sessionId does not match accepted frame-set sessionId"
            );
        }
        if (payload.frameIndex() != frame.frameIndex() || payload.frameType() != frame.frameType()) {
            throw failure(
                    ReaderRestoreStatus.INVALID_DECODED_CONTENT,
                    "Decoded payload frame identity does not match decoded frame content"
            );
        }
        if (!request.decodedContent().layoutProfileId().equals(payload.layoutProfileId())) {
            throw failure(
                    ReaderRestoreStatus.INVALID_DECODED_CONTENT,
                    "Decoded payload layoutProfileId does not match decoded frame-set content"
            );
        }
        byte[] body = payload.body();
        if (RestoreChecksums.crc32c(body) != payload.payloadCrc32c()) {
            throw failure(
                    ReaderRestoreStatus.INVALID_DECODED_CONTENT,
                    "Decoded payload CRC32C does not match its body"
            );
        }
    }

    private ParsedMetadata mergeMetadata(String recordName, ParsedMetadata existing, ParsedMetadata candidate) {
        if (existing == null) {
            return candidate;
        }
        if (!existing.values().equals(candidate.values())) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    "Repeated " + recordName + " payloads are inconsistent"
            );
        }
        return existing;
    }

    private void mergeManifestFragment(Map<Integer, ManifestFragment> fragments, ManifestFragment candidate) {
        ManifestFragment existing = fragments.get(candidate.fragmentIndex());
        if (existing == null) {
            fragments.put(candidate.fragmentIndex(), candidate);
            return;
        }
        if (!existing.equals(candidate)) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    "Repeated manifest fragment " + candidate.fragmentIndex() + " is inconsistent"
            );
        }
    }

    private void mergeFileChunk(Map<ChunkKey, RestoredChunk> chunks, RestoredChunk candidate) {
        ChunkKey key = ChunkKey.from(candidate.metadata());
        RestoredChunk existing = chunks.get(key);
        if (existing == null) {
            chunks.put(key, candidate);
            return;
        }
        if (!existing.sameContent(candidate)) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    "Repeated file chunk " + key.fileIndex() + ":" + key.chunkIndex() + " is inconsistent"
            );
        }
    }

    private ParsedMetadata parseMetadata(byte[] body, String expectedType, Set<String> requiredKeys, Set<String> allowedKeys) {
        Map<String, String> values = parseKeyValueLines(decodeUtf8(body, expectedType + " payload"), allowedKeys);
        for (String requiredKey : requiredKeys) {
            if (!values.containsKey(requiredKey)) {
                throw failure(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        expectedType + " payload is missing required key " + requiredKey
                );
            }
        }
        if (!expectedType.equals(values.get("type"))) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    expectedType + " payload has unexpected type " + values.get("type")
            );
        }
        return new ParsedMetadata(values);
    }

    private ManifestFragment parseManifestFragment(byte[] body) {
        List<String> lines = logicalLines(decodeUtf8(body, "manifest fragment"));
        if (lines.size() < 5) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest fragment header is incomplete");
        }
        requireLine(lines.get(0), "type=MANIFEST", "Manifest fragment type is invalid");
        int fragmentIndex = parseNonNegativeInt(valueAfter(lines.get(1), "fragmentIndex="), "manifest fragmentIndex");
        int totalFragments = parsePositiveInt(valueAfter(lines.get(2), "totalFragments="), "manifest totalFragments");
        String manifestFingerprint = valueAfter(lines.get(3), "manifestFingerprint=");
        if (manifestFingerprint.isBlank()) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest fingerprint must not be blank");
        }
        long totalSizeBytes = parseNonNegativeLong(valueAfter(lines.get(4), "totalSizeBytes="), "manifest totalSizeBytes");
        if (fragmentIndex >= totalFragments) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest fragment index is outside totalFragments");
        }

        List<String> contentLines = new ArrayList<>();
        for (int index = 5; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.startsWith("rootAlias=") && !line.startsWith("file=") && !line.startsWith("chunk=")) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest content line is invalid: " + line);
            }
            contentLines.add(line);
        }
        return new ManifestFragment(fragmentIndex, totalFragments, manifestFingerprint, totalSizeBytes, List.copyOf(contentLines));
    }

    private RestoredChunk parseFileChunk(byte[] body) {
        int separatorIndex = findHeaderSeparator(body);
        if (separatorIndex < 0) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "File chunk payload is missing the blank-line separator");
        }

        byte[] headerBytes = Arrays.copyOfRange(body, 0, separatorIndex);
        byte[] payload = Arrays.copyOfRange(body, separatorIndex + 2, body.length);
        ParsedMetadata header = parseMetadata(headerBytes, "DATA", Set.of(
                "type",
                "fileIndex",
                "chunkIndex",
                "offset",
                "payloadLength",
                "crc32c"
        ), Set.of(
                "type",
                "fileIndex",
                "chunkIndex",
                "offset",
                "payloadLength",
                "crc32c"
        ));
        FileChunk chunk = new FileChunk(
                parseNonNegativeLong(header.value("fileIndex"), "chunk fileIndex"),
                parseNonNegativeLong(header.value("chunkIndex"), "chunk chunkIndex"),
                parseNonNegativeLong(header.value("offset"), "chunk offset"),
                parseNonNegativeInt(header.value("payloadLength"), "chunk payloadLength"),
                parseSignedInt(header.value("crc32c"), "chunk crc32c")
        );
        if (payload.length != chunk.payloadLength()) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    "File chunk payloadLength does not match raw payload bytes for "
                            + chunk.fileIndex()
                            + ":"
                            + chunk.chunkIndex()
            );
        }
        int actualCrc32c = RestoreChecksums.crc32c(payload);
        if (actualCrc32c != chunk.crc32c()) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    "File chunk CRC32C does not match raw payload bytes for "
                            + chunk.fileIndex()
                            + ":"
                            + chunk.chunkIndex()
            );
        }
        return new RestoredChunk(chunk, payload);
    }

    private Manifest reconstructManifest(Map<Integer, ManifestFragment> fragments) {
        if (fragments.isEmpty()) {
            throw failure(ReaderRestoreStatus.INCOMPLETE_CONTENT, "Decoded content does not contain any manifest fragments");
        }

        ManifestFragment first = fragments.values().iterator().next();
        int totalFragments = first.totalFragments();
        String manifestFingerprint = first.manifestFingerprint();
        long totalSizeBytes = first.totalSizeBytes();
        for (ManifestFragment fragment : fragments.values()) {
            if (fragment.totalFragments() != totalFragments
                    || !fragment.manifestFingerprint().equals(manifestFingerprint)
                    || fragment.totalSizeBytes() != totalSizeBytes) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest fragment metadata is inconsistent");
            }
        }

        List<String> manifestLines = new ArrayList<>();
        for (int fragmentIndex = 0; fragmentIndex < totalFragments; fragmentIndex++) {
            ManifestFragment fragment = fragments.get(fragmentIndex);
            if (fragment == null) {
                throw failure(
                        ReaderRestoreStatus.INCOMPLETE_CONTENT,
                        "Decoded content is missing manifest fragment " + fragmentIndex
                );
            }
            manifestLines.addAll(fragment.contentLines());
        }

        Manifest manifest = parseManifestContent(manifestLines, totalSizeBytes, manifestFingerprint);
        String computedFingerprint = manifestFingerprint(manifest.rootAliases(), manifest.files());
        if (!computedFingerprint.equals(manifestFingerprint)) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    "Manifest fingerprint does not match reconstructed manifest content"
            );
        }
        return manifest;
    }

    private Manifest parseManifestContent(List<String> manifestLines, long totalSizeBytes, String manifestFingerprint) {
        List<String> rootAliases = new ArrayList<>();
        Set<String> seenRootAliases = new LinkedHashSet<>();
        List<FileBuilder> fileBuilders = new ArrayList<>();
        boolean sawFile = false;
        FileBuilder currentFile = null;

        for (String line : manifestLines) {
            if (line.startsWith("rootAlias=")) {
                if (sawFile) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest root aliases must precede files");
                }
                String rootAlias = line.substring("rootAlias=".length());
                if (rootAlias.isBlank()) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest rootAlias must not be blank");
                }
                if (!seenRootAliases.add(rootAlias)) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest contains duplicate rootAlias " + rootAlias);
                }
                rootAliases.add(rootAlias);
            } else if (line.startsWith("file=")) {
                sawFile = true;
                currentFile = parseFileLine(line.substring("file=".length()), fileBuilders.size(), seenRootAliases);
                fileBuilders.add(currentFile);
            } else if (line.startsWith("chunk=")) {
                if (currentFile == null) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest chunk appears before a file entry");
                }
                FileChunk chunk = parseManifestChunk(line.substring("chunk=".length()));
                currentFile.addChunk(chunk);
            } else {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest content line is invalid: " + line);
            }
        }

        if (rootAliases.isEmpty()) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest must declare at least one root alias");
        }

        List<FileRecord> files = new ArrayList<>(fileBuilders.size());
        for (FileBuilder builder : fileBuilders) {
            files.add(builder.build());
        }
        long computedTotalSizeBytes = files.stream()
                .filter(file -> file.fileType() == FileType.REGULAR_FILE)
                .mapToLong(FileRecord::sizeBytes)
                .sum();
        if (computedTotalSizeBytes != totalSizeBytes) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest totalSizeBytes does not match file entries");
        }

        return new Manifest(List.copyOf(files), List.copyOf(rootAliases), totalSizeBytes, manifestFingerprint);
    }

    private FileBuilder parseFileLine(String body, int fileIndex, Set<String> rootAliases) {
        String[] parts = body.split("\t", -1);
        if (parts.length != 5) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest file line must have five tab-delimited fields");
        }
        String rootAlias = parts[0];
        if (!rootAliases.contains(rootAlias)) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest file references undeclared rootAlias " + rootAlias);
        }
        String relativePath = parts[1];
        if (relativePath.isBlank()) {
            throw failure(ReaderRestoreStatus.UNSAFE_PATH, "Manifest relative path is unsafe: " + relativePath);
        }
        FileType fileType = parseFileType(parts[2]);
        long sizeBytes = parseNonNegativeLong(parts[3], "manifest file sizeBytes");
        String sha256 = parts[4];
        if (fileType == FileType.DIRECTORY) {
            if (sizeBytes != 0) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest directory sizeBytes must be zero");
            }
            if (!"-".equals(sha256)) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest directory SHA-256 field must be -");
            }
            sha256 = null;
        } else if (!SHA_256_HEX_PATTERN.matcher(sha256).matches()) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest regular file SHA-256 is invalid");
        }
        return new FileBuilder(fileIndex, rootAlias, relativePath, fileType, sizeBytes, sha256);
    }

    private FileChunk parseManifestChunk(String body) {
        String[] parts = body.split("\t", -1);
        if (parts.length != 5) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest chunk line must have five tab-delimited fields");
        }
        return new FileChunk(
                parseNonNegativeLong(parts[0], "manifest chunk fileIndex"),
                parseNonNegativeLong(parts[1], "manifest chunk chunkIndex"),
                parseNonNegativeLong(parts[2], "manifest chunk offset"),
                parseNonNegativeInt(parts[3], "manifest chunk payloadLength"),
                parseSignedInt(parts[4], "manifest chunk crc32c")
        );
    }

    private void validateMetadata(
            ReaderRestoreRequest request,
            Manifest manifest,
            ParsedMetadata syncMetadata,
            ParsedMetadata sessionHeader,
            ParsedMetadata sessionEnd
    ) {
        if (syncMetadata != null) {
            validateSessionId(request.sessionId(), syncMetadata.value("sessionId"), "SYNC_METADATA");
            validateManifestFingerprint(manifest.manifestFingerprint(), syncMetadata.value("manifestFingerprint"), "SYNC_METADATA");
        }
        if (sessionHeader != null) {
            validateSessionId(request.sessionId(), sessionHeader.value("sessionId"), "SESSION_HEADER");
            validateManifestFingerprint(manifest.manifestFingerprint(), sessionHeader.value("manifestFingerprint"), "SESSION_HEADER");
            validateLongValue(manifest.files().size(), sessionHeader.value("totalFileCount"), "SESSION_HEADER totalFileCount");
            validateLongValue(totalLogicalChunkCount(manifest), sessionHeader.value("totalLogicalChunkCount"),
                    "SESSION_HEADER totalLogicalChunkCount");
            validateLongValue(manifest.totalSizeBytes(), sessionHeader.value("totalSizeBytes"), "SESSION_HEADER totalSizeBytes");
        }
        if (sessionEnd != null) {
            String finalSessionDigest = sessionEnd.value("finalSessionDigest");
            if (!request.finalSessionDigest().equals(finalSessionDigest)) {
                throw failure(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        "SESSION_END finalSessionDigest does not match accepted frame-set final digest"
                );
            }
            validateLongValue(totalLogicalChunkCount(manifest), sessionEnd.value("totalDataRecords"), "SESSION_END totalDataRecords");
        }
    }

    private void validateSessionId(SessionId expected, String actual, String recordName) {
        if (!expected.toString().equals(actual)) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    recordName + " sessionId does not match accepted frame-set sessionId"
            );
        }
    }

    private void validateManifestFingerprint(String expected, String actual, String recordName) {
        if (!expected.equals(actual)) {
            throw failure(
                    ReaderRestoreStatus.INCONSISTENT_CONTENT,
                    recordName + " manifestFingerprint does not match reconstructed manifest"
            );
        }
    }

    private void validateLongValue(long expected, String actual, String field) {
        long parsed = parseNonNegativeLong(actual, field);
        if (parsed != expected) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, field + " does not match reconstructed manifest");
        }
    }

    private void validateChunks(Manifest manifest, Map<ChunkKey, RestoredChunk> chunks) {
        Map<ChunkKey, FileChunk> declaredChunks = declaredChunks(manifest);
        for (ChunkKey key : chunks.keySet()) {
            if (!declaredChunks.containsKey(key)) {
                throw failure(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        "Decoded content contains undeclared file chunk " + key.fileIndex() + ":" + key.chunkIndex()
                );
            }
        }
        for (Map.Entry<ChunkKey, FileChunk> entry : declaredChunks.entrySet()) {
            RestoredChunk decodedChunk = chunks.get(entry.getKey());
            if (decodedChunk == null) {
                throw failure(
                        ReaderRestoreStatus.INCOMPLETE_CONTENT,
                        "Decoded content is missing file chunk "
                                + entry.getKey().fileIndex()
                                + ":"
                                + entry.getKey().chunkIndex()
                );
            }
            if (!entry.getValue().equals(decodedChunk.metadata())) {
                throw failure(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        "Decoded file chunk header does not match manifest chunk metadata for "
                                + entry.getKey().fileIndex()
                                + ":"
                                + entry.getKey().chunkIndex()
                );
            }
        }
    }

    private Map<ChunkKey, FileChunk> declaredChunks(Manifest manifest) {
        Map<ChunkKey, FileChunk> declaredChunks = new LinkedHashMap<>();
        for (FileRecord file : manifest.files()) {
            for (FileChunk chunk : file.chunks()) {
                declaredChunks.put(ChunkKey.from(chunk), chunk);
            }
        }
        return declaredChunks;
    }

    private RestorePlan createRestorePlan(ReaderRestoreRequest request, Manifest manifest, Map<ChunkKey, RestoredChunk> chunks) {
        Path outputDirectory = request.outputDirectory();
        Set<String> rootAliases = new LinkedHashSet<>();
        for (String rootAlias : manifest.rootAliases()) {
            validateRootAlias(rootAlias);
            Path rootPath = outputDirectory.resolve(rootAlias).normalize();
            validateInsideOutput(outputDirectory, rootPath, "root alias " + rootAlias);
            rootAliases.add(rootAlias);
        }

        List<RestoreEntry> entries = new ArrayList<>(manifest.files().size());
        Set<Path> finalPaths = new LinkedHashSet<>();
        Set<Path> regularFilePaths = new LinkedHashSet<>();
        for (int fileIndex = 0; fileIndex < manifest.files().size(); fileIndex++) {
            FileRecord file = manifest.files().get(fileIndex);
            validateRelativePath(file.relativePath());
            if (!rootAliases.contains(file.rootAlias())) {
                throw failure(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        "Manifest file references undeclared rootAlias " + file.rootAlias()
                );
            }

            Path finalPath = resolveFinalPath(outputDirectory, file.rootAlias(), file.relativePath());
            validateInsideOutput(outputDirectory, finalPath, file.rootAlias() + "/" + file.relativePath());
            if (!finalPaths.add(finalPath)) {
                throw failure(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        "Manifest maps multiple entries to " + finalPath
                );
            }
            entries.add(new RestoreEntry(fileIndex, file, finalPath));
            if (file.fileType() == FileType.REGULAR_FILE) {
                regularFilePaths.add(finalPath);
            }
        }
        validateParentPaths(entries, regularFilePaths);

        long restoredFileCount = manifest.files().stream()
                .filter(file -> file.fileType() == FileType.REGULAR_FILE)
                .count();
        long restoredDirectoryCount = manifest.files().stream()
                .filter(file -> file.fileType() == FileType.DIRECTORY)
                .count();
        return new RestorePlan(
                request,
                manifest,
                List.copyOf(entries),
                Set.copyOf(rootAliases),
                Map.copyOf(chunks),
                restoredFileCount,
                restoredDirectoryCount,
                manifest.totalSizeBytes()
        );
    }

    private Path resolveFinalPath(Path outputDirectory, String rootAlias, String relativePath) {
        Path path = outputDirectory.resolve(rootAlias);
        for (String segment : relativePath.split("/", -1)) {
            path = path.resolve(segment);
        }
        return path.normalize();
    }

    private void validateParentPaths(List<RestoreEntry> entries, Set<Path> regularFilePaths) {
        for (RestoreEntry entry : entries) {
            Path parent = entry.finalPath().getParent();
            while (parent != null && !parent.equals(entry.finalPath().getRoot())) {
                if (regularFilePaths.contains(parent)) {
                    throw failure(
                            ReaderRestoreStatus.INCONSISTENT_CONTENT,
                            "Manifest requires a directory below regular file path " + parent
                    );
                }
                parent = parent.getParent();
            }
        }
    }

    private void validateRootAlias(String rootAlias) {
        if (rootAlias == null
                || rootAlias.isBlank()
                || ".".equals(rootAlias)
                || "..".equals(rootAlias)
                || rootAlias.contains("..")
                || rootAlias.contains("/")
                || rootAlias.contains("\\")
                || rootAlias.matches("^[A-Za-z]:.*")
                || containsUnsafeControl(rootAlias)) {
            throw failure(ReaderRestoreStatus.UNSAFE_PATH, "Manifest rootAlias is unsafe: " + rootAlias);
        }
    }

    private void validateRelativePath(String relativePath) {
        if (relativePath == null
                || relativePath.isBlank()
                || relativePath.startsWith("/")
                || relativePath.contains("\\")
                || relativePath.matches("^[A-Za-z]:.*")
                || containsUnsafeControl(relativePath)) {
            throw failure(ReaderRestoreStatus.UNSAFE_PATH, "Manifest relative path is unsafe: " + relativePath);
        }
        String[] segments = relativePath.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw failure(ReaderRestoreStatus.UNSAFE_PATH, "Manifest relative path is unsafe: " + relativePath);
            }
        }
    }

    private boolean containsUnsafeControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n' || character == '\r' || character == '\t' || character == '\0') {
                return true;
            }
        }
        return false;
    }

    private void validateInsideOutput(Path outputDirectory, Path path, String description) {
        if (!path.normalize().startsWith(outputDirectory)) {
            throw failure(
                    ReaderRestoreStatus.UNSAFE_PATH,
                    "Manifest path escapes output directory for " + description
            );
        }
    }

    private long totalLogicalChunkCount(Manifest manifest) {
        return manifest.files().stream().mapToLong(file -> file.chunks().size()).sum();
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
        return RestoreChecksums.sha256Hex(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> parseKeyValueLines(String text, Set<String> allowedKeys) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : logicalLines(text)) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Metadata line is not key=value: " + line);
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!allowedKeys.contains(key)) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Metadata contains unsupported key " + key);
            }
            String previous = values.put(key, value);
            if (previous != null) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Metadata contains duplicate key " + key);
            }
        }
        return Map.copyOf(values);
    }

    private List<String> logicalLines(String text) {
        String[] rawLines = text.split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < rawLines.length; index++) {
            String line = rawLines[index];
            if (index == rawLines.length - 1 && line.isEmpty()) {
                continue;
            }
            if (line.isEmpty()) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Payload contains an unexpected blank line");
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    private String decodeUtf8(byte[] bytes, String description) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, description + " is not valid UTF-8");
        }
    }

    private int findHeaderSeparator(byte[] body) {
        for (int index = 0; index < body.length - 1; index++) {
            if (body[index] == '\n' && body[index + 1] == '\n') {
                return index;
            }
        }
        return -1;
    }

    private void requireLine(String actual, String expected, String message) {
        if (!expected.equals(actual)) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, message);
        }
    }

    private String valueAfter(String line, String prefix) {
        if (!line.startsWith(prefix)) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Expected line prefix " + prefix);
        }
        String value = line.substring(prefix.length());
        if (value.isBlank()) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Value for " + prefix + " must not be blank");
        }
        return value;
    }

    private FileType parseFileType(String value) {
        try {
            return FileType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest fileType is unsupported: " + value);
        }
    }

    private int parsePositiveInt(String value, String description) {
        int parsed = parseNonNegativeInt(value, description);
        if (parsed <= 0) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, description + " must be positive");
        }
        return parsed;
    }

    private int parseNonNegativeInt(String value, String description) {
        long parsed = parseNonNegativeLong(value, description);
        if (parsed > Integer.MAX_VALUE) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, description + " is too large");
        }
        return (int) parsed;
    }

    private long parseNonNegativeLong(String value, String description) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, description + " must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, description + " is not a valid integer");
        }
    }

    private int parseSignedInt(String value, String description) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, description + " is not a valid signed integer");
        }
    }

    private ReaderRestoreException failure(ReaderRestoreStatus status, String message) {
        return new ReaderRestoreException(status, message);
    }

    private record ParsedMetadata(Map<String, String> values) {

        String value(String key) {
            return values.get(key);
        }
    }

    private record ManifestFragment(
            int fragmentIndex,
            int totalFragments,
            String manifestFingerprint,
            long totalSizeBytes,
            List<String> contentLines
    ) {
    }

    private final class FileBuilder {

        private final int fileIndex;
        private final String rootAlias;
        private final String relativePath;
        private final FileType fileType;
        private final long sizeBytes;
        private final String sha256;
        private final Map<Long, FileChunk> chunks = new HashMap<>();

        private FileBuilder(
                int fileIndex,
                String rootAlias,
                String relativePath,
                FileType fileType,
                long sizeBytes,
                String sha256
        ) {
            this.fileIndex = fileIndex;
            this.rootAlias = rootAlias;
            this.relativePath = relativePath;
            this.fileType = fileType;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }

        private void addChunk(FileChunk chunk) {
            if (fileType != FileType.REGULAR_FILE) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest directory must not declare chunks");
            }
            if (chunk.fileIndex() != fileIndex) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest chunk fileIndex does not match current file");
            }
            FileChunk previous = chunks.put(chunk.chunkIndex(), chunk);
            if (previous != null) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest contains duplicate chunk index");
            }
        }

        private FileRecord build() {
            if (fileType == FileType.DIRECTORY) {
                return new FileRecord(rootAlias, relativePath, fileType, sizeBytes, null, List.of());
            }
            if (chunks.isEmpty()) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest regular file must declare at least one chunk");
            }

            List<FileChunk> orderedChunks = new ArrayList<>();
            long expectedOffset = 0;
            for (long chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                FileChunk chunk = chunks.get(chunkIndex);
                if (chunk == null) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest chunks must be contiguous");
                }
                if (chunk.offset() != expectedOffset) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest chunk offsets are not contiguous");
                }
                if (sizeBytes > 0 && chunk.payloadLength() == 0) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Non-empty file chunk payloadLength must be positive");
                }
                expectedOffset += chunk.payloadLength();
                orderedChunks.add(chunk);
            }
            if (expectedOffset != sizeBytes) {
                throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Manifest chunk payload lengths do not match file size");
            }
            if (sizeBytes == 0) {
                if (orderedChunks.size() != 1 || orderedChunks.get(0).offset() != 0 || orderedChunks.get(0).payloadLength() != 0) {
                    throw failure(ReaderRestoreStatus.INCONSISTENT_CONTENT, "Empty regular files must declare one zero-length chunk");
                }
            }
            return new FileRecord(rootAlias, relativePath, fileType, sizeBytes, sha256, List.copyOf(orderedChunks));
        }
    }
}
