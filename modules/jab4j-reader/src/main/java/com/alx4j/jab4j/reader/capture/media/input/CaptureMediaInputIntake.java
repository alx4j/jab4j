package com.alx4j.jab4j.reader.capture.media.input;

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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;

/**
 * Discovers still-image media sources and decodes deterministic PNG frames through ImageIO.
 */
public final class CaptureMediaInputIntake {

    /**
     * Discovers and decodes one still-image file or one folder of still-image files.
     *
     * @param inputSource input file or directory
     * @return intake result with decoded PNG frames and stable diagnostics
     */
    public MediaIntakeResult read(Path inputSource) {
        return read(List.of(Objects.requireNonNull(inputSource, "inputSource must not be null")));
    }

    /**
     * Discovers and decodes sources declared by a media receiver request.
     *
     * @param request media receiver request
     * @return intake result with decoded PNG frames and stable diagnostics
     */
    public MediaIntakeResult read(CaptureMediaReceiverRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return read(request.sourceKind(), request.inputSources());
    }

    /**
     * Discovers and decodes source files in deterministic input order.
     *
     * @param inputSources input files or directories
     * @return intake result with decoded PNG frames and stable diagnostics
     */
    public MediaIntakeResult read(List<Path> inputSources) {
        return read(CaptureMediaSourceKind.STILL_IMAGE_FILE, inputSources);
    }

    /**
     * Discovers and decodes source files with an explicit caller-declared source kind.
     *
     * @param sourceKind caller-declared media source kind
     * @param inputSources input files or directories
     * @return intake result with decoded PNG frames and stable diagnostics
     */
    public MediaIntakeResult read(CaptureMediaSourceKind sourceKind, List<Path> inputSources) {
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        Objects.requireNonNull(inputSources, "inputSources must not be null");
        if (inputSources.isEmpty()) {
            throw new IllegalArgumentException("inputSources must not be empty");
        }
        if (inputSources.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("inputSources must not contain null values");
        }

        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>();
        List<Path> sourceFiles = discoverSourceFiles(sourceKind, inputSources, diagnostics);
        List<MediaInputFrame> frames = new ArrayList<>();
        for (int order = 0; order < sourceFiles.size(); order++) {
            Path sourceFile = sourceFiles.get(order);
            String sourceId = sourceFile.toString();
            SourceClassification classification = classify(sourceKind, sourceFile);
            if (classification.unsupported()) {
                diagnostics.add(errorForSource(
                        classification.diagnosticCode().orElseThrow(),
                        sourceKind,
                        sourceId,
                        order,
                        classification.message()
                ));
                continue;
            }

            if (!Files.isRegularFile(sourceFile) || !Files.isReadable(sourceFile)) {
                diagnostics.add(errorForSource(
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        sourceKind,
                        sourceId,
                        order,
                        "Media source is not a readable regular file"
                ));
                continue;
            }

            try {
                BufferedImage image = ImageIO.read(sourceFile.toFile());
                if (image == null) {
                    diagnostics.add(errorForSource(
                            CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                            sourceKind,
                            sourceId,
                            order,
                            "PNG media source could not be decoded"
                    ));
                    continue;
                }
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
                frames.add(new MediaInputFrame(
                        sourceId,
                        sourceKind,
                        order,
                        width,
                        height,
                        imageFormatName(sourceFile).orElse(classification.formatName()),
                        sha256Hex(toArgbBytes(pixels)),
                        pixels
                ));
            } catch (IOException exception) {
                diagnostics.add(errorForSource(
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        sourceKind,
                        sourceId,
                        order,
                        "PNG media source could not be read"
                ));
            }
        }
        return new MediaIntakeResult(sourceFiles.size(), frames, diagnostics);
    }

    private List<Path> discoverSourceFiles(
            CaptureMediaSourceKind sourceKind,
            List<Path> inputSources,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        List<Path> sourceFiles = new ArrayList<>();
        for (Path inputSource : inputSources) {
            Path normalized = inputSource.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                sourceFiles.addAll(filesInDirectory(sourceKind, normalized, diagnostics));
            } else {
                sourceFiles.add(normalized);
            }
        }
        return List.copyOf(sourceFiles);
    }

    private List<Path> filesInDirectory(
            CaptureMediaSourceKind sourceKind,
            Path directory,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        try (var stream = Files.list(directory)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
            if (files.isEmpty()) {
                diagnostics.add(errorForSource(
                        CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                        sourceKind,
                        directory.toString(),
                        "Media source directory does not contain regular files"
                ));
            }
            return files;
        } catch (IOException exception) {
            diagnostics.add(errorForSource(
                    CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                    sourceKind,
                    directory.toString(),
                    "Media source directory could not be listed"
            ));
            return List.of();
        }
    }

    private SourceClassification classify(CaptureMediaSourceKind sourceKind, Path sourceFile) {
        String extension = extension(sourceFile).toLowerCase(Locale.ROOT);
        if (sourceKind.video()) {
            return SourceClassification.unsupported(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                    "Direct .mov/.mp4 capture media input is unsupported; no adapter is configured"
            );
        }
        return switch (extension) {
            case "png" -> SourceClassification.supported("png");
            case "heic", "heif" -> SourceClassification.unsupported(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                    "HEIC/HEIF capture media input is unsupported by this ImageIO-only media intake slice"
            );
            case "mov", "mp4" -> SourceClassification.unsupported(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                    "Direct .mov/.mp4 capture media input is unsupported; no adapter is configured"
            );
            default -> SourceClassification.unsupported(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                    "Media intake supports PNG still-image files only"
            );
        };
    }

    private String extension(Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
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

    private CaptureMediaDiagnostic errorForSource(
            CaptureMediaDiagnosticCode code,
            CaptureMediaSourceKind sourceKind,
            String sourceId,
            int callerOrder,
            String message
    ) {
        return CaptureMediaDiagnostic.forSource(
                code,
                CaptureMediaDiagnosticSeverity.ERROR,
                sourceKind,
                sourceId,
                callerOrder,
                message
        );
    }

    private CaptureMediaDiagnostic errorForSource(
            CaptureMediaDiagnosticCode code,
            CaptureMediaSourceKind sourceKind,
            String sourceId,
            String message
    ) {
        return CaptureMediaDiagnostic.forSource(
                code,
                CaptureMediaDiagnosticSeverity.ERROR,
                sourceKind,
                sourceId,
                message
        );
    }

    private record SourceClassification(
            boolean unsupported,
            Optional<CaptureMediaDiagnosticCode> diagnosticCode,
            String formatName,
            String message
    ) {

        static SourceClassification supported(String formatName) {
            return new SourceClassification(false, Optional.empty(), formatName, "");
        }

        static SourceClassification unsupported(CaptureMediaDiagnosticCode code, String message) {
            return new SourceClassification(true, Optional.of(code), "", message);
        }
    }
}
