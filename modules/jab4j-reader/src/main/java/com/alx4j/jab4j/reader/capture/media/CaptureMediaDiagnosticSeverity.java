package com.alx4j.jab4j.reader.capture.media;

/**
 * Severity for a media diagnostic and whether that severity blocks restore.
 */
public enum CaptureMediaDiagnosticSeverity {
    /**
     * Informational diagnostic that does not block restore.
     */
    INFO(false),

    /**
     * Non-blocking diagnostic that should be shown to callers before or after restore.
     */
    WARNING(false),

    /**
     * Blocking diagnostic that prevents a restore attempt or marks restore failure.
     */
    ERROR(true);

    private final boolean blocksRestore;

    CaptureMediaDiagnosticSeverity(boolean blocksRestore) {
        this.blocksRestore = blocksRestore;
    }

    /**
     * Indicates whether diagnostics at this severity block restore.
     *
     * @return true for blocking error diagnostics
     */
    public boolean blocksRestore() {
        return blocksRestore;
    }
}
