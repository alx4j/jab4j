package com.alx4j.jab4j.reader.capture.media.video;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;

/**
 * Result produced by an optional direct-video frame-source adapter.
 *
 * @param frames ordered decoded frames yielded to the capture-media path
 * @param diagnostics stable diagnostics for unsupported, unreadable, truncated, or limit-rejected video
 * @param durationMillis optional source-video duration
 * @param containerFrameCount optional source-video frame count before sampling
 * @param containerName optional container name
 * @param codecName optional video codec or pixel-format name
 */
public record CaptureMediaVideoFrameReadResult(
        List<CaptureMediaVideoFrame> frames,
        List<CaptureMediaDiagnostic> diagnostics,
        OptionalLong durationMillis,
        OptionalLong containerFrameCount,
        Optional<String> containerName,
        Optional<String> codecName
) {

    /**
     * Creates a validated direct-video adapter result.
     *
     * @param frames ordered decoded frames
     * @param diagnostics stable diagnostics
     * @param durationMillis optional source duration
     * @param containerFrameCount optional source frame count
     * @param containerName optional container name
     * @param codecName optional codec name
     */
    public CaptureMediaVideoFrameReadResult {
        frames = List.copyOf(Objects.requireNonNull(frames, "frames must not be null"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        durationMillis = Objects.requireNonNull(durationMillis, "durationMillis must not be null");
        containerFrameCount = Objects.requireNonNull(containerFrameCount, "containerFrameCount must not be null");
        containerName = Objects.requireNonNull(containerName, "containerName must not be null");
        codecName = Objects.requireNonNull(codecName, "codecName must not be null");
        if (frames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("frames must not contain null values");
        }
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
        requireNonNegative(durationMillis, "durationMillis");
        requireNonNegative(containerFrameCount, "containerFrameCount");
        containerName.ifPresent(CaptureMediaVideoFrameReadResult::requireText);
        codecName.ifPresent(CaptureMediaVideoFrameReadResult::requireText);
        requireOrderedFrames(frames);
    }

    /**
     * Creates a result that contains decoded frames without adapter metadata.
     *
     * @param frames ordered decoded frames
     * @param diagnostics stable diagnostics
     * @return direct-video adapter result
     */
    public static CaptureMediaVideoFrameReadResult fromFrames(
            List<CaptureMediaVideoFrame> frames,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        return new CaptureMediaVideoFrameReadResult(
                frames,
                diagnostics,
                OptionalLong.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates a diagnostics-only adapter result.
     *
     * @param diagnostics stable diagnostics
     * @return direct-video adapter result
     */
    public static CaptureMediaVideoFrameReadResult diagnosticsOnly(List<CaptureMediaDiagnostic> diagnostics) {
        return fromFrames(List.of(), diagnostics);
    }

    private static void requireNonNegative(OptionalLong value, String fieldName) {
        if (value.isPresent() && value.getAsLong() < 0L) {
            throw new IllegalArgumentException(fieldName + " must be non-negative when present");
        }
    }

    private static void requireText(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("optional text fields must not be blank when present");
        }
    }

    private static void requireOrderedFrames(List<CaptureMediaVideoFrame> frames) {
        long previousFrameNumber = -1L;
        long previousTimestampMillis = -1L;
        for (int index = 0; index < frames.size(); index++) {
            CaptureMediaVideoFrame frame = frames.get(index);
            if (frame.callerOrder() != index) {
                throw new IllegalArgumentException("frames must use contiguous zero-based callerOrder values");
            }
            if (frame.frameNumber() < previousFrameNumber) {
                throw new IllegalArgumentException("frames must be ordered by non-decreasing frameNumber");
            }
            if (frame.timestampMillis() < previousTimestampMillis) {
                throw new IllegalArgumentException("frames must be ordered by non-decreasing timestampMillis");
            }
            previousFrameNumber = frame.frameNumber();
            previousTimestampMillis = frame.timestampMillis();
        }
    }
}
