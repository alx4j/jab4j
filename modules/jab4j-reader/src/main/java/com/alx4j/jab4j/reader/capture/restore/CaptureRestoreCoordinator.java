package com.alx4j.jab4j.reader.capture.restore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.CaptureReceiverResult;
import com.alx4j.jab4j.reader.capture.CaptureReceiverSummary;
import com.alx4j.jab4j.reader.capture.decode.CaptureSessionContent;
import com.alx4j.jab4j.reader.restore.ReaderRestoreRequest;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;
import com.alx4j.jab4j.reader.restore.ReaderRestoreService;
import com.alx4j.jab4j.reader.restore.ReaderRestoreStatus;

/**
 * Invokes the source-neutral reader restore service for assembled capture session content.
 */
public final class CaptureRestoreCoordinator {

    private final ReaderRestoreService restoreService;

    /**
     * Creates a coordinator with the default reader restore service.
     */
    public CaptureRestoreCoordinator() {
        this(new ReaderRestoreService());
    }

    /**
     * Creates a coordinator with an explicit reader restore service.
     *
     * @param restoreService source-neutral restore service
     */
    public CaptureRestoreCoordinator(ReaderRestoreService restoreService) {
        this.restoreService = Objects.requireNonNull(restoreService, "restoreService must not be null");
    }

    /**
     * Restores assembled capture content and maps reader restore outcomes to capture receiver statuses.
     *
     * @param content assembled capture content
     * @param outputDirectory restore output directory
     * @param summary summary before restored-file count is known
     * @param diagnostics diagnostics already emitted by evaluation
     * @return capture receiver restore result
     */
    public CaptureReceiverResult restore(
            CaptureSessionContent content,
            Path outputDirectory,
            CaptureReceiverSummary summary,
            List<CaptureFrameDiagnostic> diagnostics
    ) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        List<CaptureFrameDiagnostic> combinedDiagnostics = new ArrayList<>(
                Objects.requireNonNull(diagnostics, "diagnostics must not be null")
        );
        try {
            ReaderRestoreResult restoreResult = restoreService.restore(new ReaderRestoreRequest(
                    content.sessionId(),
                    content.finalSessionDigest(),
                    content.decodedContent(),
                    outputDirectory
            ));
            if (restoreResult.restored()) {
                return CaptureReceiverResult.restored(
                        withRestoredFileCount(summary, restoreResult.restoredFileCount()),
                        combinedDiagnostics,
                        restoreResult
                );
            }
            if (restoreResult.status() == ReaderRestoreStatus.INCOMPLETE_CONTENT) {
                combinedDiagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                        CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                        restoreResult.message()
                ));
                return CaptureReceiverResult.incomplete(
                        summary,
                        combinedDiagnostics,
                        "Capture input is missing required restore content"
                );
            }
            combinedDiagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                    CaptureDiagnosticCode.RESTORE_FAILURE,
                    restoreResult.message()
            ));
            return CaptureReceiverResult.restoreFailed(
                    summary,
                    combinedDiagnostics,
                    Optional.of(restoreResult),
                    "Capture input qualified, but restore did not complete"
            );
        } catch (RuntimeException exception) {
            combinedDiagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                    CaptureDiagnosticCode.RESTORE_FAILURE,
                    "Capture restore failed before reader restore completed"
            ));
            return CaptureReceiverResult.restoreFailed(
                    summary,
                    combinedDiagnostics,
                    Optional.empty(),
                    "Capture input qualified, but restore did not complete"
            );
        }
    }

    private CaptureReceiverSummary withRestoredFileCount(CaptureReceiverSummary summary, long restoredFileCount) {
        return new CaptureReceiverSummary(
                summary.submittedFrameCount(),
                summary.readableFrameCount(),
                summary.acceptedCandidateCount(),
                summary.rejectedFrameCount(),
                summary.duplicateFrameCount(),
                summary.uncertainFrameCount(),
                summary.decodedTileCount(),
                restoredFileCount
        );
    }
}
