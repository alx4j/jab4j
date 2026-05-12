package com.alx4j.jab4j.reader.capture.media.video;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;

/**
 * Default direct-video adapter that preserves stable unsupported diagnostics without decoding video.
 */
public final class UnsupportedCaptureMediaVideoFrameSourceAdapter implements CaptureMediaVideoFrameSourceAdapter {

    static final UnsupportedCaptureMediaVideoFrameSourceAdapter INSTANCE =
            new UnsupportedCaptureMediaVideoFrameSourceAdapter();

    private UnsupportedCaptureMediaVideoFrameSourceAdapter() {
    }

    /**
     * Returns an unsupported-container diagnostic for the submitted video source.
     *
     * @param request direct-video source and extraction limits
     * @return diagnostics-only adapter result
     */
    @Override
    public CaptureMediaVideoFrameReadResult read(CaptureMediaVideoFrameReadRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return CaptureMediaVideoFrameReadResult.diagnosticsOnly(List.of(CaptureMediaDiagnostic.forSource(
                CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                CaptureMediaDiagnosticSeverity.ERROR,
                CaptureMediaSourceKind.VIDEO_FILE,
                request.sourceFile().toString(),
                request.callerOrder(),
                messageFor(request)
        )));
    }

    private String messageFor(CaptureMediaVideoFrameReadRequest request) {
        String fileName = request.sourceFile().getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".mov") || fileName.endsWith(".mp4")) {
            return "Direct .mov/.mp4 capture media input is unsupported; no adapter is configured";
        }
        return "Direct video capture media input is unsupported; no adapter is configured";
    }
}
