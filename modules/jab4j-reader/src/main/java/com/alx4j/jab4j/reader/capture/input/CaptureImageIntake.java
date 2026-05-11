package com.alx4j.jab4j.reader.capture.input;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.CaptureReceiverRequest;

/**
 * Discovers capture frame files and decodes PNG images without requiring writer sequence metadata.
 */
public final class CaptureImageIntake {

    /**
     * Discovers source files in request order and decodes PNG images into ARGB frames.
     *
     * @param request capture receiver request
     * @return intake result with readable frames and diagnostics
     */
    public CaptureIntakeResult read(CaptureReceiverRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<Path> sourceFiles = discoverSourceFiles(request.inputSources());
        List<CaptureInputFrame> frames = new ArrayList<>();
        List<CaptureFrameDiagnostic> diagnostics = new ArrayList<>();

        for (int order = 0; order < sourceFiles.size(); order++) {
            Path sourceFile = sourceFiles.get(order);
            String sourceId = sourceFile.toString();
            if (!Files.isRegularFile(sourceFile) || !Files.isReadable(sourceFile)) {
                diagnostics.add(CaptureFrameDiagnostic.forSource(
                        CaptureDiagnosticCode.UNREADABLE_IMAGE,
                        sourceId,
                        order,
                        "Capture source is not a readable regular file"
                ));
                continue;
            }
            if (!isPng(sourceFile)) {
                diagnostics.add(CaptureFrameDiagnostic.forSource(
                        CaptureDiagnosticCode.UNSUPPORTED_FORMAT,
                        sourceId,
                        order,
                        "Capture receiver supports PNG frame images only"
                ));
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(sourceFile.toFile());
                if (image == null) {
                    diagnostics.add(CaptureFrameDiagnostic.forSource(
                            CaptureDiagnosticCode.UNREADABLE_IMAGE,
                            sourceId,
                            order,
                            "PNG frame image could not be decoded"
                    ));
                    continue;
                }
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
                frames.add(new CaptureInputFrame(
                        sourceId,
                        order,
                        width,
                        height,
                        pixels,
                        sha256Hex(toArgbBytes(pixels))
                ));
            } catch (IOException exception) {
                diagnostics.add(CaptureFrameDiagnostic.forSource(
                        CaptureDiagnosticCode.UNREADABLE_IMAGE,
                        sourceId,
                        order,
                        "PNG frame image could not be read"
                ));
            }
        }

        return new CaptureIntakeResult(sourceFiles.size(), frames, diagnostics);
    }

    private List<Path> discoverSourceFiles(List<Path> inputSources) {
        List<Path> sourceFiles = new ArrayList<>();
        for (Path inputSource : inputSources) {
            if (Files.isDirectory(inputSource)) {
                sourceFiles.addAll(filesInDirectory(inputSource));
            } else {
                sourceFiles.add(inputSource);
            }
        }
        return List.copyOf(sourceFiles);
    }

    private List<Path> filesInDirectory(Path directory) {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
        } catch (IOException exception) {
            return List.of(directory.toAbsolutePath().normalize());
        }
    }

    private boolean isPng(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".png");
    }

    private byte[] toArgbBytes(int[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        return buffer.array();
    }

    private String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
