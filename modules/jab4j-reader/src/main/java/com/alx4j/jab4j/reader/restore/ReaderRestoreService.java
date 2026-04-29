package com.alx4j.jab4j.reader.restore;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores a validated decoded reader frame set into a caller-selected output directory.
 */
public final class ReaderRestoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderRestoreService.class);

    private final RestoreReassembler restoreReassembler;
    private final StagedRestoreWriter stagedRestoreWriter;

    /**
     * Creates a restore service with the default payload reassembler and staged filesystem writer.
     */
    public ReaderRestoreService() {
        this(new RestoreReassembler(), new StagedRestoreWriter());
    }

    private ReaderRestoreService(RestoreReassembler restoreReassembler, StagedRestoreWriter stagedRestoreWriter) {
        this.restoreReassembler = Objects.requireNonNull(restoreReassembler, "restoreReassembler must not be null");
        this.stagedRestoreWriter = Objects.requireNonNull(stagedRestoreWriter, "stagedRestoreWriter must not be null");
    }

    /**
     * Reassembles decoded transfer payloads and publishes restored files only after validation succeeds.
     *
     * @param request decoded restore request
     * @return structured restore result
     */
    public ReaderRestoreResult restore(ReaderRestoreRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long startedAtNanos = System.nanoTime();
        LOGGER.info(
                "Reader restore starting sessionId={} outputDirectory={} decodedFrames={} decodedTiles={}",
                request.sessionId(),
                request.outputDirectory(),
                request.decodedContent().frames().size(),
                request.decodedContent().decodedTileCount()
        );
        try {
            RestorePlan plan = restoreReassembler.reassemble(request);
            ReaderRestoreResult result = stagedRestoreWriter.restore(plan);
            LOGGER.info(
                    "Reader restore completed sessionId={} outputDirectory={} restoredFiles={} restoredDirectories={} totalRestoredBytes={} durationMillis={}",
                    result.sessionId(),
                    result.outputDirectory(),
                    result.restoredFileCount(),
                    result.restoredDirectoryCount(),
                    result.totalRestoredBytes(),
                    elapsedMillis(startedAtNanos)
            );
            return result;
        } catch (ReaderRestoreException exception) {
            LOGGER.warn(
                    "Reader restore failed status={} sessionId={} outputDirectory={} durationMillis={} message={}",
                    exception.status(),
                    request.sessionId(),
                    request.outputDirectory(),
                    elapsedMillis(startedAtNanos),
                    exception.getMessage()
            );
            return ReaderRestoreResult.failed(
                    exception.status(),
                    request.sessionId(),
                    request.outputDirectory(),
                    exception.getMessage()
            );
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
