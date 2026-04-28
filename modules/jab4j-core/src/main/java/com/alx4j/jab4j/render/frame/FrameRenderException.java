package com.alx4j.jab4j.render.frame;

/**
 * Signals invalid input or unsupported conditions during deterministic full-frame composition.
 */
public final class FrameRenderException extends RuntimeException {

    /**
     * Creates an exception with one message.
     *
     * @param message failure description
     */
    public FrameRenderException(String message) {
        super(message);
    }
}
