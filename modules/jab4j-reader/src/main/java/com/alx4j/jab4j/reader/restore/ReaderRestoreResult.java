package com.alx4j.jab4j.reader.restore;

import java.nio.file.Path;
import java.util.Objects;
import com.alx4j.jab4j.api.model.SessionId;

/**
 * Structured result for one reader restore request.
 *
 * @param status restore status
 * @param sessionId transfer session id
 * @param outputDirectory normalized output directory
 * @param restoredFileCount number of manifest-declared regular files restored
 * @param restoredDirectoryCount number of manifest-declared directories restored
 * @param totalRestoredBytes total bytes written for regular files
 * @param message human-readable result detail
 */
public record ReaderRestoreResult(
        ReaderRestoreStatus status,
        SessionId sessionId,
        Path outputDirectory,
        long restoredFileCount,
        long restoredDirectoryCount,
        long totalRestoredBytes,
        String message
) {

    /**
     * Creates a validated restore result.
     *
     * @param status restore status
     * @param sessionId transfer session id
     * @param outputDirectory normalized output directory
     * @param restoredFileCount number of regular files restored
     * @param restoredDirectoryCount number of directories restored
     * @param totalRestoredBytes total restored bytes
     * @param message result detail
     */
    public ReaderRestoreResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        if (restoredFileCount < 0 || restoredDirectoryCount < 0 || totalRestoredBytes < 0) {
            throw new IllegalArgumentException("restore counts must be non-negative");
        }
        if (status != ReaderRestoreStatus.RESTORED
                && (restoredFileCount != 0 || restoredDirectoryCount != 0 || totalRestoredBytes != 0)) {
            throw new IllegalArgumentException("failed restore results must not include restored counts");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Creates a successful restore result.
     *
     * @param sessionId transfer session id
     * @param outputDirectory normalized output directory
     * @param restoredFileCount number of regular files restored
     * @param restoredDirectoryCount number of directories restored
     * @param totalRestoredBytes total restored bytes
     * @return successful restore result
     */
    public static ReaderRestoreResult restored(
            SessionId sessionId,
            Path outputDirectory,
            long restoredFileCount,
            long restoredDirectoryCount,
            long totalRestoredBytes
    ) {
        return new ReaderRestoreResult(
                ReaderRestoreStatus.RESTORED,
                sessionId,
                outputDirectory,
                restoredFileCount,
                restoredDirectoryCount,
                totalRestoredBytes,
                "Transfer package restored successfully"
        );
    }

    /**
     * Creates a failed restore result.
     *
     * @param status failed restore status
     * @param sessionId transfer session id
     * @param outputDirectory normalized output directory
     * @param message failure detail
     * @return failed restore result
     */
    public static ReaderRestoreResult failed(
            ReaderRestoreStatus status,
            SessionId sessionId,
            Path outputDirectory,
            String message
    ) {
        if (status == ReaderRestoreStatus.RESTORED) {
            throw new IllegalArgumentException("failed restore status must not be RESTORED");
        }
        return new ReaderRestoreResult(status, sessionId, outputDirectory, 0, 0, 0, message);
    }

    /**
     * Indicates whether restore completed and published output.
     *
     * @return true when restore succeeded
     */
    public boolean restored() {
        return status == ReaderRestoreStatus.RESTORED;
    }
}
