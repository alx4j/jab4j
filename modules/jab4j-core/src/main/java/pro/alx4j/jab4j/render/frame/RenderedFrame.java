package pro.alx4j.jab4j.render.frame;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pro.alx4j.jab4j.api.model.FrameType;

/**
 * Immutable full-frame raster ready for playback.
 *
 * @param frameIndex zero-based frame index
 * @param frameType rendered frame type
 * @param widthPixels frame width in pixels
 * @param heightPixels frame height in pixels
 * @param argbPixels row-major ARGB pixel buffer
 * @param diagnostics deterministic diagnostics for tests and downstream validation
 */
public record RenderedFrame(
        long frameIndex,
        FrameType frameType,
        int widthPixels,
        int heightPixels,
        List<Integer> argbPixels,
        Map<String, String> diagnostics
) {

    /**
     * Creates a validated rendered frame raster.
     *
     * @param frameIndex zero-based frame index
     * @param frameType rendered frame type
     * @param widthPixels frame width in pixels
     * @param heightPixels frame height in pixels
     * @param argbPixels row-major ARGB pixels
     * @param diagnostics deterministic diagnostics for tests and downstream validation
     */
    public RenderedFrame {
        if (frameIndex < 0) {
            throw new FrameRenderException("frameIndex must be non-negative");
        }
        Objects.requireNonNull(frameType, "frameType must not be null");
        if (widthPixels <= 0 || heightPixels <= 0) {
            throw new FrameRenderException("frame dimensions must be positive");
        }
        argbPixels = List.copyOf(Objects.requireNonNull(argbPixels, "argbPixels must not be null"));
        if (argbPixels.size() != widthPixels * heightPixels) {
            throw new FrameRenderException("argbPixels size must equal widthPixels * heightPixels");
        }
        diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(diagnostics, "diagnostics must not be null")
        ));
    }
}
