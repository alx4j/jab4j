package com.alx4j.jab4j.reader.capture.media;

/**
 * Coarse lifecycle status for one real-world media evaluation or restore attempt.
 */
public enum CaptureMediaReceiverStatus {
    /**
     * Media input was decoded and restored successfully.
     */
    RESTORED,

    /**
     * Media input appears complete enough for a restore attempt.
     */
    ELIGIBLE,

    /**
     * Media input is missing required unique frame, tile, session, or manifest content.
     */
    INCOMPLETE,

    /**
     * Media input cannot be used by the receiver.
     */
    REJECTED,

    /**
     * Media input qualified for restore, but restore did not complete successfully.
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
     * @return true when media content is eligible for restore
     */
    public boolean eligibleForRestore() {
        return this == ELIGIBLE;
    }

    /**
     * Indicates whether this status represents a terminal media receiver failure.
     *
     * @return true for rejected, incomplete, or restore-failed outcomes
     */
    public boolean terminalFailure() {
        return this == INCOMPLETE || this == REJECTED || this == RESTORE_FAILED;
    }
}
