package pro.alx4j.jab4j.player.core;

final class AwtDisplaySurfaceFactory implements DisplaySurfaceFactory {

    @Override
    public DisplaySurface create(boolean fullscreen) {
        return new AwtDisplaySurface(fullscreen);
    }
}
