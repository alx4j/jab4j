package com.alx4j.jab4j.player.core;

import com.alx4j.jab4j.render.frame.RenderedFrame;

interface DisplaySurface {

    void open(int widthPixels, int heightPixels, boolean fullscreen);

    void present(RenderedFrame frame);

    void close();
}
