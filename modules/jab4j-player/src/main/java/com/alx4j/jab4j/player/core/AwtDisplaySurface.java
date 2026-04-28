package com.alx4j.jab4j.player.core;

import java.awt.BorderLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import com.alx4j.jab4j.render.frame.RenderedFrame;

final class AwtDisplaySurface implements DisplaySurface {

    private final boolean fullscreen;

    private JFrame frame;
    private JLabel imageLabel;
    private GraphicsDevice graphicsDevice;

    AwtDisplaySurface(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    @Override
    public void open(int widthPixels, int heightPixels, boolean fullscreenRequested) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new PlaybackException("Display playback is unavailable in a headless environment");
        }
        invokeOnEdt(() -> {
            frame = new JFrame("jab4j player");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.setResizable(false);
            imageLabel = new JLabel();
            frame.add(imageLabel, BorderLayout.CENTER);
            frame.setSize(widthPixels, heightPixels);
            if (fullscreen && fullscreenRequested) {
                frame.setUndecorated(true);
                graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                graphicsDevice.setFullScreenWindow(frame);
            } else {
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
            frame.setVisible(true);
        });
    }

    @Override
    public void present(RenderedFrame frameRaster) {
        BufferedImage image = new BufferedImage(frameRaster.widthPixels(), frameRaster.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < frameRaster.heightPixels(); row++) {
            for (int col = 0; col < frameRaster.widthPixels(); col++) {
                image.setRGB(col, row, frameRaster.argbPixels().get((row * frameRaster.widthPixels()) + col));
            }
        }
        invokeOnEdt(() -> imageLabel.setIcon(new ImageIcon(image)));
    }

    @Override
    public void close() {
        invokeOnEdt(() -> {
            if (graphicsDevice != null && graphicsDevice.getFullScreenWindow() == frame) {
                graphicsDevice.setFullScreenWindow(null);
            }
            if (frame != null) {
                frame.dispose();
            }
            graphicsDevice = null;
            frame = null;
            imageLabel = null;
        });
    }

    private void invokeOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlaybackException("Playback interrupted while updating the display surface", exception);
        } catch (InvocationTargetException exception) {
            throw new PlaybackException("Display surface update failed", exception.getCause());
        }
    }
}
