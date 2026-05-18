package com.alx4j.jab4j.reader.capture.media.input;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;

/**
 * Internal handle for one retained source frame and the normalized candidates derived from it.
 */
public final class RetainedMediaInputFrame {

    private final MediaInputFrame sourceFrame;
    private final List<NormalizedCaptureFrame> normalizedFrames;

    /**
     * Creates a retained source-frame handle.
     *
     * @param sourceFrame source frame with retained ARGB pixels
     * @param normalizedFrames normalized candidates derived from the source frame
     */
    public RetainedMediaInputFrame(MediaInputFrame sourceFrame, List<NormalizedCaptureFrame> normalizedFrames) {
        this.sourceFrame = Objects.requireNonNull(sourceFrame, "sourceFrame must not be null");
        this.normalizedFrames = List.copyOf(Objects.requireNonNull(
                normalizedFrames,
                "normalizedFrames must not be null"
        ));
        if (this.normalizedFrames.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("normalizedFrames must not contain null values");
        }
    }

    /**
     * Returns the source frame while its pixels remain retained.
     *
     * @return source frame
     */
    public MediaInputFrame sourceFrame() {
        return sourceFrame;
    }

    /**
     * Returns normalized candidates derived from the source frame.
     *
     * @return normalized candidates
     */
    public List<NormalizedCaptureFrame> normalizedFrames() {
        return normalizedFrames;
    }

    /**
     * Releases the retained source-frame ARGB buffer.
     */
    public void releaseSourceArgbPixels() {
        sourceFrame.releaseArgbPixels();
    }
}
