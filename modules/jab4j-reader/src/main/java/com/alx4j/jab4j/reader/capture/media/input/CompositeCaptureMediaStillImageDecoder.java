package com.alx4j.jab4j.reader.capture.media.input;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Routes still-image decode requests to the first configured decoder that supports the file extension.
 */
final class CompositeCaptureMediaStillImageDecoder implements CaptureMediaStillImageDecoder {

    private final List<CaptureMediaStillImageDecoder> decoders;

    /**
     * Creates a composite decoder that tries decoders in declaration order.
     *
     * @param decoders non-empty decoder chain
     */
    CompositeCaptureMediaStillImageDecoder(List<CaptureMediaStillImageDecoder> decoders) {
        this.decoders = List.copyOf(Objects.requireNonNull(decoders, "decoders must not be null"));
        if (this.decoders.isEmpty()) {
            throw new IllegalArgumentException("decoders must not be empty");
        }
        if (this.decoders.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("decoders must not contain null values");
        }
    }

    @Override
    public boolean supportsExtension(String extension) {
        String normalizedExtension = CaptureMediaStillImageDecoder.normalizedExtension(extension);
        return decoders.stream().anyMatch(decoder -> decoder.supportsExtension(normalizedExtension));
    }

    @Override
    public DecodedStillImage decode(Path sourceFile, String extension) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        String normalizedExtension = CaptureMediaStillImageDecoder.normalizedExtension(extension);
        for (CaptureMediaStillImageDecoder decoder : decoders) {
            if (decoder.supportsExtension(normalizedExtension)) {
                return decoder.decode(sourceFile, normalizedExtension);
            }
        }
        throw new IOException("No still-image decoder is available for ." + normalizedExtension);
    }
}
