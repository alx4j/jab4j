package pro.alx4j.jab4j.player.core;

/**
 * Thread-safe pause, resume, and stop controls for one playback run.
 */
public final class PlaybackControl {

    private boolean paused;
    private boolean stopRequested;

    /**
     * Requests that playback pause before the next presentation step.
     */
    public synchronized void pause() {
        paused = true;
    }

    /**
     * Resumes playback if it is paused.
     */
    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    /**
     * Requests an orderly stop and releases any paused waiter.
     */
    public synchronized void stop() {
        stopRequested = true;
        paused = false;
        notifyAll();
    }

    synchronized boolean isStopRequested() {
        return stopRequested;
    }

    synchronized void awaitIfPaused() {
        while (paused && !stopRequested) {
            try {
                wait();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PlaybackException("Playback interrupted while paused", exception);
            }
        }
    }
}
