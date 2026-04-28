package com.alx4j.jab4j.player.core;

import com.alx4j.jab4j.render.frame.RenderedFrame;

enum DryRunDisplaySurface implements DisplaySurface {
    INSTANCE;

    @Override
    public void open(int widthPixels, int heightPixels, boolean fullscreen) {
    }

    @Override
    public void present(RenderedFrame frame) {
    }

    @Override
    public void close() {
    }
}
