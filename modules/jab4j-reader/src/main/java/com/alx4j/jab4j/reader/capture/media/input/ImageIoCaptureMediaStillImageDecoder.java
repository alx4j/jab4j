package com.alx4j.jab4j.reader.capture.media.input;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Decodes Java SE ImageIO still-image formats used by the baseline media receiver.
 */
public final class ImageIoCaptureMediaStillImageDecoder implements CaptureMediaStillImageDecoder {

    @Override
    public boolean supportsExtension(String extension) {
        return switch (CaptureMediaStillImageDecoder.normalizedExtension(extension)) {
            case "png", "jpg", "jpeg" -> true;
            default -> false;
        };
    }

    @Override
    public DecodedStillImage decode(Path sourceFile, String extension) throws IOException {
        BufferedImage image = ImageIO.read(sourceFile.toFile());
        if (image == null) {
            throw new IOException("Still-image media source could not be decoded");
        }
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            return new DecodedStillImage(
                    width,
                    height,
                    imageFormatName(sourceFile).orElse(formatName(extension)),
                    pixels
            );
        } finally {
            image.flush();
        }
    }

    private Optional<String> imageFormatName(Path sourceFile) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(sourceFile.toFile())) {
            if (stream == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                return Optional.of(reader.getFormatName().toLowerCase(Locale.ROOT));
            } finally {
                reader.dispose();
            }
        }
    }

    private String formatName(String extension) {
        return "jpg".equals(extension) ? "jpeg" : extension;
    }
}
