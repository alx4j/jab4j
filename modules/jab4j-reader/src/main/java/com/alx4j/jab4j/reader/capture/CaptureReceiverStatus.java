package com.alx4j.jab4j.reader.capture;

/**
 * Coarse lifecycle status for one capture receiver evaluation or restore attempt.
 */
public enum CaptureReceiverStatus {
    /**
     * Capture input was decoded and restored successfully.
     */
    RESTORED,

    /**
     * Capture input appears complete enough for a restore attempt.
     */
    ELIGIBLE,

    /**
     * Capture input is missing required frame, tile, session, or manifest content.
     */
    INCOMPLETE,

    /**
     * Capture input cannot be used by the receiver.
     */
    REJECTED,

    /**
     * Capture input qualified for restore, but restore did not complete successfully.
     */
    RESTORE_FAILED;

    /**
     * Indicates whether this status represents a completed restore attempt.
     *
     * @return true for restored or restore-failed outcomes
     */
    public boolean restoreAttempted() {
        return this == RESTORED || this == RESTORE_FAILED;
    }

    /**
     * Indicates whether this status represents a successful restore.
     *
     * @return true when restore completed successfully
     */
    public boolean restorationSucceeded() {
        return this == RESTORED;
    }

    /**
     * Indicates whether this status can be used as an evaluate-only success before restore.
     *
     * @return true when capture content is eligible for restore
     */
    public boolean eligibleForRestore() {
        return this == ELIGIBLE;
    }

    /**
     * Indicates whether this status represents a terminal receiver failure.
     *
     * @return true for rejected, incomplete, or restore-failed outcomes
     */
    public boolean terminalFailure() {
        return this == INCOMPLETE || this == REJECTED || this == RESTORE_FAILED;
    }
}
