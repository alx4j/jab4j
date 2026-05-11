package com.alx4j.jab4j.reader.capture;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;

/**
 * Structured outcome for one capture receiver evaluation or restore attempt.
 *
 * @param status coarse receiver outcome
 * @param summary aggregate capture counts
 * @param diagnostics stable diagnostics produced while evaluating or restoring the capture set
 * @param restoreResult optional reader restore result when restore was attempted
 * @param message human-readable outcome detail
 */
public record CaptureReceiverResult(
        CaptureReceiverStatus status,
        CaptureReceiverSummary summary,
        List<CaptureFrameDiagnostic> diagnostics,
        Optional<ReaderRestoreResult> restoreResult,
        String message
) {

    /**
     * Creates a validated capture receiver result.
     *
     * @param status coarse receiver outcome
     * @param summary aggregate capture counts
     * @param diagnostics stable diagnostics
     * @param restoreResult optional reader restore result
     * @param message outcome detail
     */
    public CaptureReceiverResult {
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
        if (status == CaptureReceiverStatus.RESTORED
                && (restoreResult.isEmpty() || !restoreResult.orElseThrow().restored())) {
            throw new IllegalArgumentException("restored capture results must include a successful restoreResult");
        }
        if (status == CaptureReceiverStatus.RESTORE_FAILED
                && restoreResult.map(ReaderRestoreResult::restored).orElse(false)) {
            throw new IllegalArgumentException("failed capture restore results must not include a successful restoreResult");
        }
        if (status.terminalFailure() && diagnostics.isEmpty()) {
            throw new IllegalArgumentException("failed capture results must include diagnostics");
        }
        if (status == CaptureReceiverStatus.RESTORE_FAILED
                && diagnostics.stream().noneMatch(diagnostic -> diagnostic.code() == CaptureDiagnosticCode.RESTORE_FAILURE)) {
            throw new IllegalArgumentException("failed capture restore results must include a RESTORE_FAILURE diagnostic");
        }
    }

    /**
     * Creates a result for capture input that is complete enough to restore.
     *
     * @param summary aggregate capture counts
     * @param diagnostics stable diagnostics
     * @param message outcome detail
     * @return eligible capture receiver result
     */
    public static CaptureReceiverResult eligible(
            CaptureReceiverSummary summary,
            List<CaptureFrameDiagnostic> diagnostics,
            String message
    ) {
        return new CaptureReceiverResult(
                CaptureReceiverStatus.ELIGIBLE,
                summary,
                diagnostics,
                Optional.empty(),
                message
        );
    }

    /**
     * Creates a result for capture input that is missing required content.
     *
     * @param summary aggregate capture counts
     * @param diagnostics stable diagnostics
     * @param message outcome detail
     * @return incomplete capture receiver result
     */
    public static CaptureReceiverResult incomplete(
            CaptureReceiverSummary summary,
            List<CaptureFrameDiagnostic> diagnostics,
            String message
    ) {
        return new CaptureReceiverResult(
                CaptureReceiverStatus.INCOMPLETE,
                summary,
                diagnostics,
                Optional.empty(),
                message
        );
    }

    /**
     * Creates a result for capture input that cannot produce a recovery attempt.
     *
     * @param summary aggregate capture counts
     * @param diagnostics stable diagnostics
     * @param message outcome detail
     * @return rejected capture receiver result
     */
    public static CaptureReceiverResult rejected(
            CaptureReceiverSummary summary,
            List<CaptureFrameDiagnostic> diagnostics,
            String message
    ) {
        return new CaptureReceiverResult(
                CaptureReceiverStatus.REJECTED,
                summary,
                diagnostics,
                Optional.empty(),
                message
        );
    }

    /**
     * Creates a result for capture input restored through the reader restore service.
     *
     * @param summary aggregate capture counts
     * @param diagnostics stable diagnostics
     * @param restoreResult successful reader restore result
     * @return restored capture receiver result
     */
    public static CaptureReceiverResult restored(
            CaptureReceiverSummary summary,
            List<CaptureFrameDiagnostic> diagnostics,
            ReaderRestoreResult restoreResult
    ) {
        return new CaptureReceiverResult(
                CaptureReceiverStatus.RESTORED,
                summary,
                diagnostics,
                Optional.of(Objects.requireNonNull(restoreResult, "restoreResult must not be null")),
                "Capture input restored successfully"
        );
    }

    /**
     * Creates a result for capture input whose restore attempt failed.
     *
     * @param summary aggregate capture counts
     * @param diagnostics stable diagnostics including a restore-failure reason
     * @param restoreResult optional failed reader restore result
     * @param message outcome detail
     * @return failed restore capture receiver result
     */
    public static CaptureReceiverResult restoreFailed(
            CaptureReceiverSummary summary,
            List<CaptureFrameDiagnostic> diagnostics,
            Optional<ReaderRestoreResult> restoreResult,
            String message
    ) {
        return new CaptureReceiverResult(
                CaptureReceiverStatus.RESTORE_FAILED,
                summary,
                diagnostics,
                Objects.requireNonNull(restoreResult, "restoreResult must not be null"),
                message
        );
    }

    /**
     * Indicates whether capture input was restored successfully.
     *
     * @return true when the receiver restored output files
     */
    public boolean restored() {
        return status.restorationSucceeded();
    }

    /**
     * Indicates whether evaluation found enough content for a later restore attempt.
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
     * Indicates whether capture evaluation or restore ended in a terminal failure.
     *
     * @return true for rejected, incomplete, or restore-failed results
     */
    public boolean failed() {
        return status.terminalFailure();
    }
}
