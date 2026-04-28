package pro.alx4j.jab4j.player.core;

final class SystemPlaybackClock implements PlaybackClock {

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public void sleepNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            long millis = nanos / 1_000_000L;
            int remainingNanos = (int) (nanos % 1_000_000L);
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlaybackException("Playback interrupted while pacing frames", exception);
        }
    }
}
