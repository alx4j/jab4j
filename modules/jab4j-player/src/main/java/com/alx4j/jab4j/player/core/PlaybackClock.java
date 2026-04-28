package com.alx4j.jab4j.player.core;

interface PlaybackClock {

    long nanoTime();

    void sleepNanos(long nanos);
}
