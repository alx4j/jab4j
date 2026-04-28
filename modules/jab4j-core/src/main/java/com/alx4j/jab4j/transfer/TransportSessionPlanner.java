package com.alx4j.jab4j.transfer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.api.model.FileChunk;
import com.alx4j.jab4j.api.model.FileRecord;
import com.alx4j.jab4j.api.model.FileType;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.Manifest;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.api.model.TransferSession;
import com.alx4j.jab4j.api.model.TransportProfile;
import com.alx4j.jab4j.support.ChecksumUtils;
import com.alx4j.jab4j.support.HashingUtils;
import com.alx4j.jab4j.catalog.PackagedEntry;
import com.alx4j.jab4j.catalog.PackagingResult;

/**
 * Builds deterministic transport records, parity planning, and frame descriptors from packaged input.
 */
public final class TransportSessionPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportSessionPlanner.class);
    private static final int INITIAL_SESSION_HEADER_FRAMES = 2;
    private static final int MAX_MANIFEST_FRAGMENT_BODY_BYTES = 768;

    /**
     * Plans transport records, parity groups, and frame descriptors from one packaged manifest and transfer session.
     *
     * @param draftSession transfer session with manifest-level file records and profile metadata
     * @param packagingResult packaging result aligned with the manifest
     * @return deterministic transport session plan
     */
    public TransportSessionPlan plan(TransferSession draftSession, PackagingResult packagingResult) {
        Objects.requireNonNull(draftSession, "draftSession must not be null");
        Objects.requireNonNull(packagingResult, "packagingResult must not be null");
        String sessionId = draftSession.sessionId().toString();
        String transportProfileId = draftSession.profile().transport().profileId();
        String manifestFingerprint = draftSession.manifest().manifestFingerprint();
        long startedAtNanos = System.nanoTime();
        LOGGER.debug(
                "Planning transport session sessionId={} transportProfileId={} manifestFingerprint={} manifestFiles={} packagingEntries={}",
                sessionId,
                transportProfileId,
                manifestFingerprint,
                draftSession.manifest().files().size(),
                packagingResult.entries().size()
        );
        try {
            if (!draftSession.protocolVersion().equals(draftSession.profile().transport().protocolVersion())) {
                throw new TransportException("Session protocolVersion must match the transport profile protocolVersion");
            }

            Manifest manifest = draftSession.manifest();
            if (!manifest.equals(packagingResult.manifest())) {
                throw new TransportException("Packaging result manifest must match the transfer session manifest");
            }

            TransportProfile transportProfile = draftSession.profile().transport();
            List<PackagedEntry> entries = packagingResult.entries();
            List<PlannedFileTransport> plannedFiles = new ArrayList<>(entries.size());
            List<ChunkPayload> chunkPayloads = new ArrayList<>();
            long totalLogicalChunkCount = 0;

            for (int fileIndex = 0; fileIndex < entries.size(); fileIndex++) {
                PackagedEntry entry = entries.get(fileIndex);
                FileRecord packagedRecord = entry.fileRecord();
                if (packagedRecord.fileType() == FileType.DIRECTORY) {
                    plannedFiles.add(new PlannedFileTransport(packagedRecord));
                    continue;
                }

                byte[] fileBytes = readBytes(entry);
                String actualSha256 = HashingUtils.sha256Hex(fileBytes);
                if (!actualSha256.equals(packagedRecord.sha256())) {
                    throw new TransportException("Packaged file digest does not match the source bytes for " + packagedRecord.relativePath());
                }

                List<FileChunk> chunks = chunkFile(fileIndex, fileBytes, transportProfile.chunkBytes());
                FileRecord updatedRecord = new FileRecord(
                        packagedRecord.rootAlias(),
                        packagedRecord.relativePath(),
                        packagedRecord.fileType(),
                        packagedRecord.sizeBytes(),
                        packagedRecord.sha256(),
                        chunks
                );
                plannedFiles.add(new PlannedFileTransport(updatedRecord));
                totalLogicalChunkCount += chunks.size();

                for (FileChunk chunk : chunks) {
                    int start = Math.toIntExact(chunk.offset());
                    byte[] payload = new byte[chunk.payloadLength()];
                    System.arraycopy(fileBytes, start, payload, 0, payload.length);
                    chunkPayloads.add(new ChunkPayload(chunk, payload));
                }
            }

            List<FileRecord> updatedFiles = plannedFiles.stream()
                    .map(PlannedFileTransport::updatedFileRecord)
                    .toList();

            Manifest updatedManifest = new Manifest(
                    updatedFiles,
                    manifest.rootAliases(),
                    manifest.totalSizeBytes(),
                    manifest.manifestFingerprint()
            );
            TransferSession updatedSession = new TransferSession(
                    draftSession.sessionId(),
                    draftSession.createdAt(),
                    draftSession.protocolVersion(),
                    draftSession.writerBuildId(),
                    draftSession.profile(),
                    updatedManifest,
                    updatedFiles
            );
            ManifestSummary manifestSummary = new ManifestSummary(
                    updatedFiles.size(),
                    totalLogicalChunkCount,
                    updatedManifest.totalSizeBytes(),
                    updatedManifest.manifestFingerprint()
            );

            SessionHeaderRecord sessionHeaderRecord = new SessionHeaderRecord(
                    0,
                    draftSession.sessionId(),
                    draftSession.createdAt(),
                    draftSession.protocolVersion(),
                    draftSession.writerBuildId(),
                    draftSession.profile().layout().profileId(),
                    draftSession.profile().codec().profileId(),
                    transportProfile.profileId(),
                    manifestSummary,
                    transportProfile.dataShardsPerGroup(),
                    transportProfile.parityShardsPerGroup(),
                    transportProfile.parityGroupSizingStrategy()
            );
            List<TransportRecord> records = new ArrayList<>();
            records.add(sessionHeaderRecord);
            List<ManifestRecord> manifestRecords = createManifestRecords(records.size(), updatedManifest);
            records.addAll(manifestRecords);

            long sequenceNumber = records.size();
            for (int fileIndex = 0; fileIndex < plannedFiles.size(); fileIndex++) {
                FileRecord updatedFile = plannedFiles.get(fileIndex).updatedFileRecord();
                records.add(new FileHeaderRecord(sequenceNumber++, fileIndex, updatedFile));
                for (FileChunk chunk : updatedFile.chunks()) {
                    records.add(new FileChunkRecord(sequenceNumber++, fileIndex, chunk));
                }
            }

            List<ParityGroupPlan> parityPlan = createParityPlan(chunkPayloads, transportProfile);
            List<ParityRecord> parityRecords = createParityRecords(sequenceNumber, parityPlan, chunkPayloads);
            records.addAll(parityRecords);
            sequenceNumber += parityRecords.size();

            ScheduledFrames scheduledFrames = buildNonTerminalFrames(
                    updatedSession,
                    manifestSummary,
                    sessionHeaderRecord,
                    manifestRecords,
                    chunkPayloads,
                    parityPlan,
                    parityRecords
            );
            int endFrameCount = Math.max(1, updatedSession.profile().playback().endFrames());
            String finalSessionDigest = computeFinalSessionDigest(
                    updatedSession,
                    records,
                    chunkPayloads,
                    parityPlan,
                    scheduledFrames.frameDescriptors(),
                    endFrameCount
            );
            SessionEndRecord sessionEndRecord = new SessionEndRecord(
                    sequenceNumber,
                    chunkPayloads.size(),
                    parityRecords.size(),
                    finalSessionDigest
            );
            records.add(sessionEndRecord);

            List<FrameDescriptor> frameDescriptors = appendEndFrames(
                    updatedSession,
                    scheduledFrames.frameDescriptors(),
                    sessionEndRecord,
                    scheduledFrames.nextPayloadSequenceNumber(),
                    endFrameCount
            );

            TransportSessionPlan plan = new TransportSessionPlan(
                    updatedSession,
                    manifestSummary,
                    records,
                    chunkPayloads,
                    parityPlan,
                    frameDescriptors,
                    finalSessionDigest
            );
            LOGGER.info(
                    "Planned transport session sessionId={} transportProfileId={} manifestFingerprint={} files={} chunks={} parityGroups={} records={} frames={} durationMillis={} finalSessionDigest={}",
                    sessionId,
                    transportProfileId,
                    manifestFingerprint,
                    manifestSummary.totalFileCount(),
                    manifestSummary.totalLogicalChunkCount(),
                    parityPlan.size(),
                    records.size(),
                    frameDescriptors.size(),
                    elapsedMillis(startedAtNanos),
                    finalSessionDigest
            );
            return plan;
        } catch (RuntimeException exception) {
            if (exception instanceof TransportException) {
                LOGGER.warn(
                        "Transport session planning failed sessionId={} transportProfileId={} manifestFingerprint={} durationMillis={} message={}",
                        sessionId,
                        transportProfileId,
                        manifestFingerprint,
                        elapsedMillis(startedAtNanos),
                        exception.getMessage()
                );
            } else {
                LOGGER.error(
                        "Transport session planning failed sessionId={} transportProfileId={} manifestFingerprint={} durationMillis={}",
                        sessionId,
                        transportProfileId,
                        manifestFingerprint,
                        elapsedMillis(startedAtNanos),
                        exception
                );
            }
            throw exception;
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private byte[] readBytes(PackagedEntry entry) {
        try {
            return Files.readAllBytes(entry.sourcePath());
        } catch (IOException exception) {
            throw new TransportException("Unable to read packaged file bytes from " + entry.sourcePath(), exception);
        }
    }

    private List<FileChunk> chunkFile(long fileIndex, byte[] fileBytes, int chunkBytes) {
        List<FileChunk> chunks = new ArrayList<>();
        if (fileBytes.length == 0) {
            chunks.add(new FileChunk(fileIndex, 0, 0, 0, ChecksumUtils.crc32c(new byte[0])));
            return chunks;
        }

        int chunkIndex = 0;
        for (int offset = 0; offset < fileBytes.length; offset += chunkBytes) {
            int payloadLength = Math.min(chunkBytes, fileBytes.length - offset);
            int crc32c = ChecksumUtils.crc32c(fileBytes, offset, payloadLength);
            chunks.add(new FileChunk(fileIndex, chunkIndex++, offset, payloadLength, crc32c));
        }
        return List.copyOf(chunks);
    }

    private List<ParityGroupPlan> createParityPlan(List<ChunkPayload> chunkPayloads, TransportProfile transportProfile) {
        List<ParityGroupPlan> parityPlan = new ArrayList<>();
        if (chunkPayloads.isEmpty()) {
            return List.of();
        }

        int dataShardsPerGroup = transportProfile.dataShardsPerGroup();
        for (int start = 0, groupIndex = 0; start < chunkPayloads.size(); start += dataShardsPerGroup, groupIndex++) {
            int end = Math.min(start + dataShardsPerGroup, chunkPayloads.size());
            parityPlan.add(new ParityGroupPlan(
                    groupIndex,
                    dataShardsPerGroup,
                    transportProfile.parityShardsPerGroup(),
                    transportProfile.parityGroupSizingStrategy(),
                    start,
                    end,
                    end - start
            ));
        }
        return List.copyOf(parityPlan);
    }

    private List<ParityRecord> createParityRecords(
            long firstSequenceNumber,
            List<ParityGroupPlan> parityPlan,
            List<ChunkPayload> chunkPayloads
    ) {
        List<ParityRecord> parityRecords = new ArrayList<>();
        long sequenceNumber = firstSequenceNumber;
        for (ParityGroupPlan groupPlan : parityPlan) {
            List<ChunkPayload> sourceChunks = chunkPayloads.subList(
                    Math.toIntExact(groupPlan.sourceChunkStartInclusive()),
                    Math.toIntExact(groupPlan.sourceChunkEndExclusive())
            );
            for (int parityShardIndex = 0; parityShardIndex < groupPlan.parityShardCount(); parityShardIndex++) {
                byte[] parityPayload = serializeParityPayload(groupPlan, parityShardIndex, sourceChunks);
                parityRecords.add(new ParityRecord(
                        sequenceNumber++,
                        groupPlan.groupIndex(),
                        groupPlan.sourceChunkStartInclusive(),
                        groupPlan.sourceChunkEndExclusive(),
                        parityShardIndex,
                        parityPayload.length,
                        ChecksumUtils.crc32c(parityPayload)
                ));
            }
        }
        return List.copyOf(parityRecords);
    }

    private ScheduledFrames buildNonTerminalFrames(
            TransferSession session,
            ManifestSummary manifestSummary,
            SessionHeaderRecord sessionHeaderRecord,
            List<ManifestRecord> manifestRecords,
            List<ChunkPayload> chunkPayloads,
            List<ParityGroupPlan> parityPlan,
            List<ParityRecord> parityRecords
    ) {
        List<FrameDescriptor> frames = new ArrayList<>();
        long payloadSequenceNumber = 0;

        int warmupSyncFrames = Math.max(1, session.profile().playback().warmupSyncFrames());
        byte[] syncPayload = serializeSyncPayload(session, manifestSummary);
        for (int index = 0; index < warmupSyncFrames; index++) {
            payloadSequenceNumber = appendBroadcastFrame(
                    session,
                    frames,
                    FrameType.SYNC,
                    PayloadKind.SYNC_METADATA,
                    syncPayload,
                    payloadSequenceNumber
            );
        }
        long lastSyncFrameIndex = frames.get(frames.size() - 1).frameIndex();

        byte[] sessionHeaderPayload = serializeSessionHeaderPayload(sessionHeaderRecord);
        long lastSessionHeaderFrameIndex = -1;
        for (int index = 0; index < INITIAL_SESSION_HEADER_FRAMES; index++) {
            if (frames.size() - lastSyncFrameIndex >= session.profile().transport().syncEveryFrames()) {
                payloadSequenceNumber = appendBroadcastFrame(
                        session,
                        frames,
                        FrameType.SYNC,
                        PayloadKind.SYNC_METADATA,
                        syncPayload,
                        payloadSequenceNumber
                );
                lastSyncFrameIndex = frames.get(frames.size() - 1).frameIndex();
            }
            payloadSequenceNumber = appendBroadcastFrame(
                    session,
                    frames,
                    FrameType.SESSION_HEADER,
                    PayloadKind.SESSION_HEADER,
                    sessionHeaderPayload,
                    payloadSequenceNumber
            );
            lastSessionHeaderFrameIndex = frames.get(frames.size() - 1).frameIndex();
        }

        List<byte[]> manifestPayloads = manifestRecords.stream()
                .map(this::serializeManifestPayload)
                .toList();
        long lastManifestFrameIndex = -1;
        for (byte[] manifestPayload : manifestPayloads) {
            if (frames.size() - lastSyncFrameIndex >= session.profile().transport().syncEveryFrames()) {
                payloadSequenceNumber = appendBroadcastFrame(
                        session,
                        frames,
                        FrameType.SYNC,
                        PayloadKind.SYNC_METADATA,
                        syncPayload,
                        payloadSequenceNumber
                );
                lastSyncFrameIndex = frames.get(frames.size() - 1).frameIndex();
            }
            payloadSequenceNumber = appendBroadcastFrame(
                    session,
                    frames,
                    FrameType.MANIFEST,
                    PayloadKind.MANIFEST_FRAGMENT,
                    manifestPayload,
                    payloadSequenceNumber
            );
            lastManifestFrameIndex = frames.get(frames.size() - 1).frameIndex();
        }

        List<ContentFrame> contentFrames = buildContentFrames(session, chunkPayloads, parityPlan, parityRecords);
        for (ContentFrame contentFrame : contentFrames) {
            if (frames.size() - lastSyncFrameIndex >= session.profile().transport().syncEveryFrames()) {
                payloadSequenceNumber = appendBroadcastFrame(
                        session,
                        frames,
                        FrameType.SYNC,
                        PayloadKind.SYNC_METADATA,
                        syncPayload,
                        payloadSequenceNumber
                );
                lastSyncFrameIndex = frames.get(frames.size() - 1).frameIndex();
            }
            long projectedContentFrameIndex = frames.size() + 1L;
            if (projectedContentFrameIndex - lastManifestFrameIndex >= session.profile().transport().manifestRepeatEveryFrames()) {
                payloadSequenceNumber = appendManifestSequence(session, frames, manifestPayloads, payloadSequenceNumber);
                lastManifestFrameIndex = frames.get(frames.size() - 1).frameIndex();
            }
            if (frames.size() - lastSyncFrameIndex >= session.profile().transport().syncEveryFrames()) {
                payloadSequenceNumber = appendBroadcastFrame(
                        session,
                        frames,
                        FrameType.SYNC,
                        PayloadKind.SYNC_METADATA,
                        syncPayload,
                        payloadSequenceNumber
                );
                lastSyncFrameIndex = frames.get(frames.size() - 1).frameIndex();
            }

            payloadSequenceNumber = appendContentFrame(session, frames, contentFrame, payloadSequenceNumber);
        }

        return new ScheduledFrames(List.copyOf(frames), payloadSequenceNumber);
    }

    private List<ContentFrame> buildContentFrames(
            TransferSession session,
            List<ChunkPayload> chunkPayloads,
            List<ParityGroupPlan> parityPlan,
            List<ParityRecord> parityRecords
    ) {
        List<ContentFrame> contentFrames = new ArrayList<>();
        int tileCapacity = totalTiles(session.profile().layout());
        for (ParityGroupPlan groupPlan : parityPlan) {
            List<PayloadSpec> dataPayloads = new ArrayList<>();
            for (int sourceChunkIndex = Math.toIntExact(groupPlan.sourceChunkStartInclusive());
                 sourceChunkIndex < groupPlan.sourceChunkEndExclusive();
                 sourceChunkIndex++) {
                dataPayloads.add(new PayloadSpec(
                        PayloadKind.FILE_CHUNK,
                        serializeFileChunkPayload(chunkPayloads.get(sourceChunkIndex))
                ));
            }

            List<PayloadSpec> parityPayloads = new ArrayList<>();
            for (ParityRecord parityRecord : parityRecords) {
                if (parityRecord.parityGroupIndex() != groupPlan.groupIndex()) {
                    continue;
                }
                List<ChunkPayload> sourceChunks = chunkPayloads.subList(
                        Math.toIntExact(groupPlan.sourceChunkStartInclusive()),
                        Math.toIntExact(groupPlan.sourceChunkEndExclusive())
                );
                parityPayloads.add(new PayloadSpec(
                        PayloadKind.PARITY_SHARD,
                        serializeParityPayload(groupPlan, parityRecord.parityShardIndex(), sourceChunks)
                ));
            }

            List<ContentFrame> dataFrames = partitionContentFrames(FrameType.DATA, dataPayloads, tileCapacity);
            List<ContentFrame> parityFrames = partitionContentFrames(FrameType.PARITY, parityPayloads, tileCapacity);
            contentFrames.addAll(interleaveContentFrames(dataFrames, parityFrames));
        }
        return List.copyOf(contentFrames);
    }

    private List<ContentFrame> partitionContentFrames(FrameType frameType, List<PayloadSpec> payloads, int tileCapacity) {
        if (payloads.isEmpty()) {
            return List.of();
        }

        List<ContentFrame> frames = new ArrayList<>();
        for (int start = 0; start < payloads.size(); start += tileCapacity) {
            int end = Math.min(start + tileCapacity, payloads.size());
            frames.add(new ContentFrame(frameType, List.copyOf(payloads.subList(start, end))));
        }
        return List.copyOf(frames);
    }

    private List<ContentFrame> interleaveContentFrames(List<ContentFrame> dataFrames, List<ContentFrame> parityFrames) {
        if (parityFrames.isEmpty()) {
            return dataFrames;
        }
        if (dataFrames.isEmpty()) {
            return parityFrames;
        }

        List<ContentFrame> interleaved = new ArrayList<>();
        int parityIndex = 0;
        List<Integer> parityInsertPositions = new ArrayList<>(parityFrames.size());
        for (int index = 0; index < parityFrames.size(); index++) {
            parityInsertPositions.add((int) Math.ceil((double) (index + 1) * dataFrames.size() / (parityFrames.size() + 1)));
        }

        for (int dataIndex = 0; dataIndex < dataFrames.size(); dataIndex++) {
            interleaved.add(dataFrames.get(dataIndex));
            while (parityIndex < parityFrames.size() && parityInsertPositions.get(parityIndex) == dataIndex + 1) {
                interleaved.add(parityFrames.get(parityIndex++));
            }
        }
        while (parityIndex < parityFrames.size()) {
            interleaved.add(parityFrames.get(parityIndex++));
        }
        return List.copyOf(interleaved);
    }

    private List<ManifestRecord> createManifestRecords(long firstSequenceNumber, Manifest manifest) {
        List<List<String>> fragments = partitionManifestLines(manifest);
        List<ManifestRecord> manifestRecords = new ArrayList<>(fragments.size());
        long sequenceNumber = firstSequenceNumber;
        for (int fragmentIndex = 0; fragmentIndex < fragments.size(); fragmentIndex++) {
            manifestRecords.add(new ManifestRecord(sequenceNumber++, fragmentIndex, fragments.size(), manifest));
        }
        return List.copyOf(manifestRecords);
    }

    private long appendManifestSequence(
            TransferSession session,
            List<FrameDescriptor> frames,
            List<byte[]> manifestPayloads,
            long payloadSequenceNumber
    ) {
        long nextPayloadSequenceNumber = payloadSequenceNumber;
        for (byte[] manifestPayload : manifestPayloads) {
            nextPayloadSequenceNumber = appendBroadcastFrame(
                    session,
                    frames,
                    FrameType.MANIFEST,
                    PayloadKind.MANIFEST_FRAGMENT,
                    manifestPayload,
                    nextPayloadSequenceNumber
            );
        }
        return nextPayloadSequenceNumber;
    }

    private long appendBroadcastFrame(
            TransferSession session,
            List<FrameDescriptor> frames,
            FrameType frameType,
            PayloadKind payloadKind,
            byte[] body,
            long payloadSequenceNumber
    ) {
        LayoutProfile layout = session.profile().layout();
        int totalTiles = totalTiles(layout);
        long frameIndex = frames.size();
        List<TilePayload> tiles = new ArrayList<>(totalTiles);
        for (int tileIndex = 0; tileIndex < totalTiles; tileIndex++) {
            tiles.add(createTilePayload(
                    session,
                    frameType,
                    frameIndex,
                    tileIndex,
                    totalTiles,
                    payloadKind,
                    payloadSequenceNumber++,
                    body
            ));
        }
        frames.add(new FrameDescriptor(frameIndex, frameType, layout, tiles));
        return payloadSequenceNumber;
    }

    private long appendContentFrame(
            TransferSession session,
            List<FrameDescriptor> frames,
            ContentFrame contentFrame,
            long payloadSequenceNumber
    ) {
        LayoutProfile layout = session.profile().layout();
        int totalTiles = totalTiles(layout);
        long frameIndex = frames.size();
        List<TilePayload> tiles = new ArrayList<>(contentFrame.payloads().size());
        int tileIndex = 0;
        for (PayloadSpec payload : contentFrame.payloads()) {
            tiles.add(createTilePayload(
                    session,
                    contentFrame.frameType(),
                    frameIndex,
                    tileIndex++,
                    totalTiles,
                    payload.payloadKind(),
                    payloadSequenceNumber++,
                    payload.body()
            ));
        }
        frames.add(new FrameDescriptor(frameIndex, contentFrame.frameType(), layout, tiles));
        return payloadSequenceNumber;
    }

    private List<FrameDescriptor> appendEndFrames(
            TransferSession session,
            List<FrameDescriptor> existingFrames,
            SessionEndRecord sessionEndRecord,
            long nextPayloadSequenceNumber,
            int endFrameCount
    ) {
        List<FrameDescriptor> frames = new ArrayList<>(existingFrames);
        byte[] endPayload = serializeSessionEndPayload(sessionEndRecord);
        long payloadSequenceNumber = nextPayloadSequenceNumber;
        for (int index = 0; index < endFrameCount; index++) {
            payloadSequenceNumber = appendBroadcastFrame(
                    session,
                    frames,
                    FrameType.END,
                    PayloadKind.SESSION_END,
                    endPayload,
                    payloadSequenceNumber
            );
        }
        return List.copyOf(frames);
    }

    private TilePayload createTilePayload(
            TransferSession session,
            FrameType frameType,
            long frameIndex,
            int tileIndex,
            int totalTiles,
            PayloadKind payloadKind,
            long payloadSequenceNumber,
            byte[] body
    ) {
        byte[] payloadBody = body.clone();
        return new TilePayload(
                session.protocolVersion().compatibilityVersion(),
                session.sessionId(),
                frameType,
                frameIndex,
                new TileIndex(tileIndex),
                totalTiles,
                session.profile().layout().profileId(),
                payloadKind,
                payloadSequenceNumber,
                payloadBody.length,
                ChecksumUtils.crc32c(payloadBody),
                0,
                payloadBody
        );
    }

    private int totalTiles(LayoutProfile layout) {
        return Math.multiplyExact(layout.rows(), layout.cols());
    }

    private byte[] serializeSyncPayload(TransferSession session, ManifestSummary manifestSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=SYNC\n");
        builder.append("sessionId=").append(session.sessionId()).append('\n');
        builder.append("protocol=").append(session.protocolVersion().displayValue()).append('\n');
        builder.append("compatibilityVersion=").append(session.protocolVersion().compatibilityVersion()).append('\n');
        builder.append("layoutProfileId=").append(session.profile().layout().profileId()).append('\n');
        builder.append("transportProfileId=").append(session.profile().transport().profileId()).append('\n');
        builder.append("manifestFingerprint=").append(manifestSummary.manifestFingerprint()).append('\n');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] serializeSessionHeaderPayload(SessionHeaderRecord sessionHeaderRecord) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=SESSION_HEADER\n");
        builder.append("sessionId=").append(sessionHeaderRecord.sessionId()).append('\n');
        builder.append("createdAt=").append(sessionHeaderRecord.createdAt()).append('\n');
        builder.append("protocol=").append(sessionHeaderRecord.protocolVersion().displayValue()).append('\n');
        builder.append("compatibilityVersion=").append(sessionHeaderRecord.protocolVersion().compatibilityVersion()).append('\n');
        builder.append("writerBuildId=").append(sessionHeaderRecord.writerBuildId()).append('\n');
        builder.append("layoutProfileId=").append(sessionHeaderRecord.layoutProfileId()).append('\n');
        builder.append("codecProfileId=").append(sessionHeaderRecord.codecProfileId()).append('\n');
        builder.append("transportProfileId=").append(sessionHeaderRecord.transportProfileId()).append('\n');
        builder.append("totalFileCount=").append(sessionHeaderRecord.manifestSummary().totalFileCount()).append('\n');
        builder.append("totalLogicalChunkCount=").append(sessionHeaderRecord.manifestSummary().totalLogicalChunkCount()).append('\n');
        builder.append("totalSizeBytes=").append(sessionHeaderRecord.manifestSummary().totalSizeBytes()).append('\n');
        builder.append("manifestFingerprint=").append(sessionHeaderRecord.manifestSummary().manifestFingerprint()).append('\n');
        builder.append("dataShardsPerGroup=").append(sessionHeaderRecord.dataShardsPerGroup()).append('\n');
        builder.append("parityShardsPerGroup=").append(sessionHeaderRecord.parityShardsPerGroup()).append('\n');
        builder.append("parityGroupSizingStrategy=").append(sessionHeaderRecord.parityGroupSizingStrategy()).append('\n');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] serializeManifestPayload(ManifestRecord manifestRecord) {
        List<List<String>> fragments = partitionManifestLines(manifestRecord.manifest());
        if (manifestRecord.totalFragments() != fragments.size()) {
            throw new TransportException("Manifest fragment metadata no longer matches the deterministic partition plan");
        }
        StringBuilder builder = manifestHeader(
                manifestRecord.fragmentIndex(),
                fragments.size(),
                manifestRecord.manifest()
        );
        fragments.get(manifestRecord.fragmentIndex()).forEach(builder::append);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<List<String>> partitionManifestLines(Manifest manifest) {
        List<String> contentLines = manifestContentLines(manifest);
        int estimatedTotalFragments = 1;
        while (true) {
            List<List<String>> fragments = partitionManifestLines(manifest, contentLines, estimatedTotalFragments);
            if (fragments.size() == estimatedTotalFragments) {
                return fragments;
            }
            estimatedTotalFragments = fragments.size();
        }
    }

    private List<List<String>> partitionManifestLines(
            Manifest manifest,
            List<String> contentLines,
            int estimatedTotalFragments
    ) {
        List<List<String>> fragments = new ArrayList<>();
        List<String> currentFragment = new ArrayList<>();
        int currentBytes = manifestHeader(0, estimatedTotalFragments, manifest).toString().getBytes(StandardCharsets.UTF_8).length;

        for (String contentLine : contentLines) {
            int lineBytes = contentLine.getBytes(StandardCharsets.UTF_8).length;
            int currentHeaderBytes = manifestHeader(fragments.size(), estimatedTotalFragments, manifest)
                    .toString()
                    .getBytes(StandardCharsets.UTF_8).length;
            if (lineBytes + currentHeaderBytes > MAX_MANIFEST_FRAGMENT_BODY_BYTES) {
                throw new TransportException("One manifest entry exceeds the supported fragment payload budget");
            }
            if (!currentFragment.isEmpty() && currentBytes + lineBytes > MAX_MANIFEST_FRAGMENT_BODY_BYTES) {
                fragments.add(List.copyOf(currentFragment));
                currentFragment = new ArrayList<>();
                currentBytes = manifestHeader(fragments.size(), estimatedTotalFragments, manifest)
                        .toString()
                        .getBytes(StandardCharsets.UTF_8).length;
            }
            currentFragment.add(contentLine);
            currentBytes += lineBytes;
        }

        if (currentFragment.isEmpty() && fragments.isEmpty()) {
            fragments.add(List.of());
        } else if (!currentFragment.isEmpty()) {
            fragments.add(List.copyOf(currentFragment));
        }
        return List.copyOf(fragments);
    }

    private List<String> manifestContentLines(Manifest manifest) {
        List<String> contentLines = new ArrayList<>();
        for (String rootAlias : manifest.rootAliases()) {
            contentLines.add("rootAlias=" + rootAlias + '\n');
        }
        for (FileRecord fileRecord : manifest.files()) {
            contentLines.add("file=" + fileRecord.rootAlias() + '\t'
                    + fileRecord.relativePath() + '\t'
                    + fileRecord.fileType() + '\t'
                    + fileRecord.sizeBytes() + '\t'
                    + (fileRecord.sha256() == null ? "-" : fileRecord.sha256())
                    + '\n');
            for (FileChunk chunk : fileRecord.chunks()) {
                contentLines.add("chunk=" + chunk.fileIndex() + '\t'
                        + chunk.chunkIndex() + '\t'
                        + chunk.offset() + '\t'
                        + chunk.payloadLength() + '\t'
                        + chunk.crc32c() + '\n');
            }
        }
        return List.copyOf(contentLines);
    }

    private StringBuilder manifestHeader(int fragmentIndex, int totalFragments, Manifest manifest) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=MANIFEST\n");
        builder.append("fragmentIndex=").append(fragmentIndex).append('\n');
        builder.append("totalFragments=").append(totalFragments).append('\n');
        builder.append("manifestFingerprint=").append(manifest.manifestFingerprint()).append('\n');
        builder.append("totalSizeBytes=").append(manifest.totalSizeBytes()).append('\n');
        return builder;
    }

    private byte[] serializeFileChunkPayload(ChunkPayload chunkPayload) {
        FileChunk chunk = chunkPayload.chunk();
        StringBuilder builder = new StringBuilder();
        builder.append("type=DATA\n");
        builder.append("fileIndex=").append(chunk.fileIndex()).append('\n');
        builder.append("chunkIndex=").append(chunk.chunkIndex()).append('\n');
        builder.append("offset=").append(chunk.offset()).append('\n');
        builder.append("payloadLength=").append(chunk.payloadLength()).append('\n');
        builder.append("crc32c=").append(chunk.crc32c()).append("\n\n");
        byte[] headerBytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] payload = chunkPayload.payload();
        byte[] body = new byte[headerBytes.length + payload.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(payload, 0, body, headerBytes.length, payload.length);
        return body;
    }

    private byte[] serializeParityPayload(
            ParityGroupPlan groupPlan,
            int parityShardIndex,
            List<ChunkPayload> sourceChunks
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=PARITY\n");
        builder.append("groupIndex=").append(groupPlan.groupIndex()).append('\n');
        builder.append("parityShardIndex=").append(parityShardIndex).append('\n');
        builder.append("dataShardCount=").append(groupPlan.dataShardCount()).append('\n');
        builder.append("parityShardCount=").append(groupPlan.parityShardCount()).append('\n');
        builder.append("groupSizingStrategy=").append(groupPlan.groupSizingStrategy()).append('\n');
        builder.append("sourceChunkStartInclusive=").append(groupPlan.sourceChunkStartInclusive()).append('\n');
        builder.append("sourceChunkEndExclusive=").append(groupPlan.sourceChunkEndExclusive()).append('\n');
        for (ChunkPayload sourceChunk : sourceChunks) {
            builder.append("sourceChunk=").append(sourceChunk.chunk().fileIndex()).append(':')
                    .append(sourceChunk.chunk().chunkIndex()).append(':')
                    .append(HashingUtils.sha256Hex(sourceChunk.payload()))
                    .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] serializeSessionEndPayload(SessionEndRecord sessionEndRecord) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=END\n");
        builder.append("totalDataRecords=").append(sessionEndRecord.totalDataRecords()).append('\n');
        builder.append("totalParityRecords=").append(sessionEndRecord.totalParityRecords()).append('\n');
        builder.append("finalSessionDigest=").append(sessionEndRecord.finalSessionDigest()).append('\n');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String computeFinalSessionDigest(
            TransferSession session,
            List<TransportRecord> records,
            List<ChunkPayload> chunkPayloads,
            List<ParityGroupPlan> parityPlan,
            List<FrameDescriptor> frameDescriptors,
            int plannedEndFrameCount
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("session\t").append(session.sessionId()).append('\n');
        builder.append("createdAt\t").append(session.createdAt()).append('\n');
        builder.append("protocol\t").append(session.protocolVersion().displayValue()).append('\t')
                .append(session.protocolVersion().compatibilityVersion()).append('\n');
        builder.append("profiles\t")
                .append(session.profile().layout().profileId()).append('\t')
                .append(session.profile().codec().profileId()).append('\t')
                .append(session.profile().transport().profileId()).append('\n');
        builder.append("manifest\t").append(session.manifest().manifestFingerprint()).append('\t')
                .append(session.manifest().totalSizeBytes()).append('\n');
        builder.append("planned-end-frames\t").append(plannedEndFrameCount).append('\n');
        for (TransportRecord record : records) {
            builder.append(record.category()).append('\t').append(record.sequenceNumber()).append('\n');
            if (record instanceof FileHeaderRecord fileHeaderRecord) {
                FileRecord fileRecord = fileHeaderRecord.fileRecord();
                builder.append("file\t").append(fileHeaderRecord.fileIndex()).append('\t')
                        .append(fileRecord.rootAlias()).append('\t')
                        .append(fileRecord.relativePath()).append('\t')
                        .append(fileRecord.fileType()).append('\t')
                        .append(fileRecord.sizeBytes()).append('\t')
                        .append(fileRecord.sha256() == null ? "-" : fileRecord.sha256())
                        .append('\n');
            } else if (record instanceof FileChunkRecord fileChunkRecord) {
                FileChunk chunk = fileChunkRecord.chunk();
                builder.append("chunk\t").append(chunk.fileIndex()).append('\t')
                        .append(chunk.chunkIndex()).append('\t')
                        .append(chunk.offset()).append('\t')
                        .append(chunk.payloadLength()).append('\t')
                        .append(chunk.crc32c()).append('\n');
            } else if (record instanceof ManifestRecord manifestRecord) {
                builder.append("manifest-fragment\t")
                        .append(manifestRecord.fragmentIndex()).append('\t')
                        .append(manifestRecord.totalFragments()).append('\t')
                        .append(manifestRecord.manifest().manifestFingerprint()).append('\n');
            } else if (record instanceof SessionHeaderRecord headerRecord) {
                builder.append("header\t").append(headerRecord.writerBuildId()).append('\t')
                        .append(headerRecord.layoutProfileId()).append('\t')
                        .append(headerRecord.codecProfileId()).append('\t')
                        .append(headerRecord.transportProfileId()).append('\t')
                        .append(headerRecord.parityGroupSizingStrategy()).append('\n');
            } else if (record instanceof ParityRecord parityRecord) {
                builder.append("parity-record\t")
                        .append(parityRecord.parityGroupIndex()).append('\t')
                        .append(parityRecord.parityShardIndex()).append('\t')
                        .append(parityRecord.sourceChunkStartInclusive()).append('\t')
                        .append(parityRecord.sourceChunkEndExclusive()).append('\t')
                        .append(parityRecord.payloadLength()).append('\t')
                        .append(parityRecord.payloadCrc32c()).append('\n');
            }
        }
        for (ChunkPayload chunkPayload : chunkPayloads) {
            builder.append("chunk-payload\t")
                    .append(chunkPayload.chunk().fileIndex()).append('\t')
                    .append(chunkPayload.chunk().chunkIndex()).append('\t')
                    .append(HashingUtils.sha256Hex(chunkPayload.payload())).append('\n');
        }
        for (ParityGroupPlan parityGroup : parityPlan) {
            builder.append("parity-plan\t")
                    .append(parityGroup.groupIndex()).append('\t')
                    .append(parityGroup.dataShardCount()).append('\t')
                    .append(parityGroup.parityShardCount()).append('\t')
                    .append(parityGroup.groupSizingStrategy()).append('\t')
                    .append(parityGroup.sourceChunkStartInclusive()).append('\t')
                    .append(parityGroup.sourceChunkEndExclusive()).append('\t')
                    .append(parityGroup.sourceChunkCount()).append('\n');
        }
        for (FrameDescriptor frameDescriptor : frameDescriptors) {
            builder.append("frame\t")
                    .append(frameDescriptor.frameIndex()).append('\t')
                    .append(frameDescriptor.frameType()).append('\t')
                    .append(frameDescriptor.tiles().size()).append('\n');
            for (TilePayload tilePayload : frameDescriptor.tiles()) {
                builder.append("tile\t")
                        .append(tilePayload.frameType()).append('\t')
                        .append(tilePayload.frameIndex()).append('\t')
                        .append(tilePayload.tileIndex().value()).append('\t')
                        .append(tilePayload.payloadKind()).append('\t')
                        .append(tilePayload.payloadSequenceNumber()).append('\t')
                        .append(tilePayload.payloadByteLength()).append('\t')
                        .append(tilePayload.payloadCrc32c()).append('\t')
                        .append(HashingUtils.sha256Hex(tilePayload.body())).append('\n');
            }
        }
        return HashingUtils.sha256Hex(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record PayloadSpec(PayloadKind payloadKind, byte[] body) {
    }

    private record ContentFrame(FrameType frameType, List<PayloadSpec> payloads) {
    }

    private record PlannedFileTransport(FileRecord updatedFileRecord) {
    }

    private record ScheduledFrames(List<FrameDescriptor> frameDescriptors, long nextPayloadSequenceNumber) {
    }
}
