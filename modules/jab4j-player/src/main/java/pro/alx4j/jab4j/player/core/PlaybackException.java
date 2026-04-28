package pro.alx4j.jab4j.player.core;

/**
 * Signals invalid playback input or runtime failure in the deterministic player.
 */
public final class PlaybackException extends RuntimeException {

    /**
     * Creates an exception with one message.
     *
     * @param message failure description
     */
    public PlaybackException(String message) {
        super(message);
    }

    /**
     * Creates an exception with message and cause.
     *
     * @param message failure description
     * @param cause root cause
     */
    public PlaybackException(String message, Throwable cause) {
        super(message, cause);
    }
}
