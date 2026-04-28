package pro.alx4j.jab4j.render.frame;

/**
 * Rendering options for frame-level composition.
 *
 * @param outerBorderEnabled whether to paint the conservative outer border
 * @param debugOverlayEnabled whether to paint optional non-transport debug overlay markers
 */
public record FrameRenderOptions(boolean outerBorderEnabled, boolean debugOverlayEnabled) {

    /**
     * Returns the conservative transport-safe rendering defaults.
     *
     * @return default options with outer border enabled and debug overlay disabled
     */
    public static FrameRenderOptions transportSafeDefaults() {
        return new FrameRenderOptions(true, false);
    }

    /**
     * Returns transport-safe rendering with the optional debug overlay enabled.
     *
     * @return options with debug overlay enabled
     */
    public static FrameRenderOptions debugOverlay() {
        return new FrameRenderOptions(true, true);
    }
}
