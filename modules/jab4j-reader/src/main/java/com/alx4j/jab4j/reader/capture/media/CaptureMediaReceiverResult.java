package com.alx4j.jab4j.reader.capture.media;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;

/**
 * Structured outcome for one real-world media evaluation or restore attempt.
 *
 * @param status coarse media receiver outcome
 * @param summary aggregate media counts
 * @param diagnostics stable diagnostics produced while evaluating or restoring the media set
 * @param restoreResult optional reader restore result when restore was attempted
 * @param message human-readable outcome detail
 */
public record CaptureMediaReceiverResult(
        CaptureMediaReceiverStatus status,
        CaptureMediaSummary summary,
        List<CaptureMediaDiagnostic> diagnostics,
        Optional<ReaderRestoreResult> restoreResult,
        String message
) {

    /**
     * Creates a validated media receiver result.
     *
     * @param status coarse media receiver outcome
     * @param summary aggregate media counts
     * @param diagnostics stable diagnostics
     * @param restoreResult optional reader restore result
     * @param message outcome detail
     */
    public CaptureMediaReceiverResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
        diagnostics = List.copyOf(diagnostics);
        restoreResult = Objects.requireNonNull(restoreResult, "restoreResult must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        if (!status.restoreAttempted() && restoreResult.isPresent()) {
            throw new IllegalArgumentException("only restore-attempt results may include restoreResult");
        }
        if (status == CaptureMediaReceiverStatus.RESTORED
                && (restoreResult.isEmpty() || !restoreResult.orElseThrow().restored())) {
            throw new IllegalArgumentException("restored media results must include a successful restoreResult");
        }
        if (status == CaptureMediaReceiverStatus.RESTORE_FAILED
                && restoreResult.map(ReaderRestoreResult::restored).orElse(false)) {
            throw new IllegalArgumentException("failed media restore results must not include a successful restoreResult");
        }
        if (!status.terminalFailure() && diagnostics.stream().anyMatch(CaptureMediaDiagnostic::blocking)) {
            throw new IllegalArgumentException("successful media results must not include blocking diagnostics");
        }
        if (status.terminalFailure() && diagnostics.stream().noneMatch(CaptureMediaDiagnostic::blocking)) {
            throw new IllegalArgumentException("failed media results must include a blocking diagnostic");
        }
        if (status == CaptureMediaReceiverStatus.RESTORE_FAILED
                && diagnostics.stream().noneMatch(CaptureMediaReceiverResult::blockingRestoreFailure)) {
            throw new IllegalArgumentException("failed media restore results must include a blocking RESTORE_FAILURE diagnostic");
        }
    }

    /**
     * Creates a result for media input that is complete enough to restore.
     *
     * @param summary aggregate media counts
     * @param diagnostics stable non-blocking diagnostics
     * @param message outcome detail
     * @return eligible media receiver result
     */
    public static CaptureMediaReceiverResult eligible(
            CaptureMediaSummary summary,
            List<CaptureMediaDiagnostic> diagnostics,
            String message
    ) {
        return new CaptureMediaReceiverResult(
                CaptureMediaReceiverStatus.ELIGIBLE,
                summary,
                diagnostics,
                Optional.empty(),
                message
        );
    }

    /**
     * Creates a result for media input that is missing required unique content.
     *
     * @param summary aggregate media counts
     * @param diagnostics stable diagnostics including a blocking reason
     * @param message outcome detail
     * @return incomplete media receiver result
     */
    public static CaptureMediaReceiverResult incomplete(
            CaptureMediaSummary summary,
            List<CaptureMediaDiagnostic> diagnostics,
            String message
    ) {
        return new CaptureMediaReceiverResult(
                CaptureMediaReceiverStatus.INCOMPLETE,
                summary,
                diagnostics,
                Optional.empty(),
                message
        );
    }

    /**
     * Creates a result for media input that cannot produce a recovery attempt.
     *
     * @param summary aggregate media counts
     * @param diagnostics stable diagnostics including a blocking reason
     * @param message outcome detail
     * @return rejected media receiver result
     */
    public static CaptureMediaReceiverResult rejected(
            CaptureMediaSummary summary,
            List<CaptureMediaDiagnostic> diagnostics,
            String message
    ) {
        return new CaptureMediaReceiverResult(
                CaptureMediaReceiverStatus.REJECTED,
                summary,
                diagnostics,
                Optional.empty(),
                message
        );
    }

    /**
     * Creates a result for media input restored through the reader restore service.
     *
     * @param summary aggregate media counts
     * @param diagnostics stable non-blocking diagnostics
     * @param restoreResult successful reader restore result
     * @return restored media receiver result
     */
    public static CaptureMediaReceiverResult restored(
            CaptureMediaSummary summary,
            List<CaptureMediaDiagnostic> diagnostics,
            ReaderRestoreResult restoreResult
    ) {
        return new CaptureMediaReceiverResult(
                CaptureMediaReceiverStatus.RESTORED,
                summary,
                diagnostics,
                Optional.of(Objects.requireNonNull(restoreResult, "restoreResult must not be null")),
                "Capture media restored successfully"
        );
    }

    /**
     * Creates a result for media input whose restore attempt failed.
     *
     * @param summary aggregate media counts
     * @param diagnostics stable diagnostics including a blocking restore-failure reason
     * @param restoreResult optional failed reader restore result
     * @param message outcome detail
     * @return failed restore media receiver result
     */
    public static CaptureMediaReceiverResult restoreFailed(
            CaptureMediaSummary summary,
            List<CaptureMediaDiagnostic> diagnostics,
            Optional<ReaderRestoreResult> restoreResult,
            String message
    ) {
        return new CaptureMediaReceiverResult(
                CaptureMediaReceiverStatus.RESTORE_FAILED,
                summary,
                diagnostics,
                Objects.requireNonNull(restoreResult, "restoreResult must not be null"),
                message
        );
    }

    /**
     * Indicates whether media input was restored successfully.
     *
     * @return true when the receiver restored output files
     */
    public boolean restored() {
        return status.restorationSucceeded();
    }

    /**
     * Indicates whether evaluation found enough media content for a later restore attempt.
     *
     * @return true when the result is eligible but restore has not been attempted
     */
    public boolean eligibleForRestore() {
        return status.eligibleForRestore();
    }

    /**
     * Indicates whether this result includes a restore attempt outcome.
     *
     * @return true for restored or restore-failed results
     */
    public boolean restoreAttempted() {
        return status.restoreAttempted();
    }

    /**
     * Indicates whether media evaluation or restore ended in a terminal failure.
     *
     * @return true for rejected, incomplete, or restore-failed results
     */
    public boolean failed() {
        return status.terminalFailure();
    }

    private static boolean blockingRestoreFailure(CaptureMediaDiagnostic diagnostic) {
        return diagnostic.blocking() && diagnostic.code() == CaptureMediaDiagnosticCode.RESTORE_FAILURE;
    }
}
