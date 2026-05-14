package com.alx4j.jab4j.reader.capture.media.input;

import java.io.IOException;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;

/**
 * Still-image decode failure with the media diagnostic code intake should emit.
 */
final class StillImageDecodeException extends IOException {

    private final CaptureMediaDiagnosticCode diagnosticCode;

    /**
     * Creates a still-image decode failure.
     *
     * @param diagnosticCode stable media diagnostic code
     * @param message diagnostic detail
     */
    StillImageDecodeException(CaptureMediaDiagnosticCode diagnosticCode, String message) {
        super(message);
        this.diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode must not be null");
    }

    /**
     * Creates a still-image decode failure with a root cause.
     *
     * @param diagnosticCode stable media diagnostic code
     * @param message diagnostic detail
     * @param cause root cause
     */
    StillImageDecodeException(CaptureMediaDiagnosticCode diagnosticCode, String message, Throwable cause) {
        super(message, cause);
        this.diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode must not be null");
    }

    /**
     * Returns the media diagnostic code intake should expose.
     *
     * @return stable diagnostic code
     */
    CaptureMediaDiagnosticCode diagnosticCode() {
        return diagnosticCode;
    }
}
