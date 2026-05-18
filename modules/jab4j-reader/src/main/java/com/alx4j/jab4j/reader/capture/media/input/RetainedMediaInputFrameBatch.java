package com.alx4j.jab4j.reader.capture.media.input;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;

/**
 * Internal batch that owns retained source-pixel release for one synchronous media receive call.
 */
public final class RetainedMediaInputFrameBatch {

    private final List<RetainedMediaInputFrame> retainedSourceFrames;
    private final List<NormalizedCaptureFrame> normalizedFrames;

    /**
     * Returns an empty retained-source batch for pre-normalization cleanup paths.
     *
     * @return empty batch
     */
    public static RetainedMediaInputFrameBatch empty() {
        return new RetainedMediaInputFrameBatch(List.of());
    }

    /**
     * Creates a retained-source batch from readable source frames and their normalized candidates.
     *
     * @param retainedSourceFrames retained source frames
     */
    public RetainedMediaInputFrameBatch(List<RetainedMediaInputFrame> retainedSourceFrames) {
        this.retainedSourceFrames = List.copyOf(Objects.requireNonNull(
                retainedSourceFrames,
                "retainedSourceFrames must not be null"
        ));
        if (this.retainedSourceFrames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("retainedSourceFrames must not contain null values");
        }
        this.normalizedFrames = this.retainedSourceFrames.stream()
                .flatMap(retainedSourceFrame -> retainedSourceFrame.normalizedFrames().stream())
                .toList();
    }

    /**
     * Returns retained source frames in intake order.
     *
     * @return retained source frames
     */
    public List<RetainedMediaInputFrame> retainedSourceFrames() {
        return retainedSourceFrames;
    }

    /**
     * Returns normalized candidates in source and candidate order.
     *
     * @return normalized candidates
     */
    public List<NormalizedCaptureFrame> normalizedFrames() {
        return normalizedFrames;
    }

    /**
     * Releases all retained source ARGB buffers.
     */
    public void releaseSourceArgbPixels() {
        retainedSourceFrames.forEach(RetainedMediaInputFrame::releaseSourceArgbPixels);
    }
}
