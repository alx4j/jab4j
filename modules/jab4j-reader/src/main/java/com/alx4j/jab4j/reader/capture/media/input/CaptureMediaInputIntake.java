package com.alx4j.jab4j.reader.capture.media.input;

import java.io.IOException;
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
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrame;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameReadRequest;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameReadResult;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoFrameSourceAdapter;
import com.alx4j.jab4j.reader.capture.media.video.CaptureMediaVideoLimits;

/**
 * Discovers still-image media sources and adapts optional direct-video sources into deterministic frame inputs.
 */
public final class CaptureMediaInputIntake {

    private final CaptureMediaStillImageDecoder stillImageDecoder;
    private final CaptureMediaVideoFrameSourceAdapter videoFrameSourceAdapter;
    private final CaptureMediaVideoLimits videoLimits;

    /**
     * Creates media intake with ImageIO still-image support, optional libheif HEIC/HEIF support, and the first
     * configured direct-video adapter, if any.
     */
    public CaptureMediaInputIntake() {
        this(
                CaptureMediaStillImageDecoder.defaultDecoder(),
                CaptureMediaVideoFrameSourceAdapter.loadFirstAvailable()
                        .orElseGet(CaptureMediaVideoFrameSourceAdapter::unsupported),
                CaptureMediaVideoLimits.conservativeDefaults()
        );
    }

    /**
     * Creates media intake with an explicit direct-video adapter boundary.
     *
     * @param videoFrameSourceAdapter optional direct-video frame-source adapter
     * @param videoLimits direct-video extraction limits supplied to the adapter
     */
    public CaptureMediaInputIntake(
            CaptureMediaVideoFrameSourceAdapter videoFrameSourceAdapter,
            CaptureMediaVideoLimits videoLimits
    ) {
        this(CaptureMediaStillImageDecoder.defaultDecoder(), videoFrameSourceAdapter, videoLimits);
    }

    /**
     * Creates media intake with explicit still-image and direct-video adapter boundaries.
     *
     * @param stillImageDecoder still-image decoder chain
     * @param videoFrameSourceAdapter optional direct-video frame-source adapter
     * @param videoLimits direct-video extraction limits supplied to the adapter
     */
    public CaptureMediaInputIntake(
            CaptureMediaStillImageDecoder stillImageDecoder,
            CaptureMediaVideoFrameSourceAdapter videoFrameSourceAdapter,
            CaptureMediaVideoLimits videoLimits
    ) {
        this.stillImageDecoder = Objects.requireNonNull(stillImageDecoder, "stillImageDecoder must not be null");
        this.videoFrameSourceAdapter = Objects.requireNonNull(
                videoFrameSourceAdapter,
                "videoFrameSourceAdapter must not be null"
        );
        this.videoLimits = Objects.requireNonNull(videoLimits, "videoLimits must not be null");
    }

    /**
     * Discovers and decodes one still-image file or one folder of still-image files.
     *
     * @param inputSource input file or directory
     * @return intake result with decoded still-image frames and stable diagnostics
     */
    public MediaIntakeResult read(Path inputSource) {
        return read(List.of(Objects.requireNonNull(inputSource, "inputSource must not be null")));
    }

    /**
     * Discovers and decodes sources declared by a media receiver request.
     *
     * @param request media receiver request
     * @return intake result with decoded still-image frames and stable diagnostics
     */
    public MediaIntakeResult read(CaptureMediaReceiverRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return read(request.sourceKind(), request.inputSources());
    }

    /**
     * Discovers and decodes source files in deterministic input order.
     *
     * @param inputSources input files or directories
     * @return intake result with decoded still-image frames and stable diagnostics
     */
    public MediaIntakeResult read(List<Path> inputSources) {
        return read(CaptureMediaSourceKind.STILL_IMAGE_FILE, inputSources);
    }

    /**
     * Discovers and decodes source files with an explicit caller-declared source kind.
     *
     * @param sourceKind caller-declared media source kind
     * @param inputSources input files or directories
     * @return intake result with decoded still-image frames and stable diagnostics
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
        if (sourceKind.video()) {
            return readVideoSources(sourceFiles, diagnostics);
        }

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
                CaptureMediaStillImageDecoder.DecodedStillImage image =
                        stillImageDecoder.decode(sourceFile, classification.extension());
                int[] pixels = image.copyArgbPixels();
                frames.add(new MediaInputFrame(
                        sourceId,
                        sourceKind,
                        order,
                        image.widthPixels(),
                        image.heightPixels(),
                        image.formatName(),
                        sha256Hex(pixels),
                        pixels
                ));
            } catch (StillImageDecodeException exception) {
                diagnostics.add(errorForSource(
                        exception.diagnosticCode(),
                        sourceKind,
                        sourceId,
                        order,
                        exception.getMessage()
                ));
            } catch (IOException exception) {
                diagnostics.add(errorForSource(
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        sourceKind,
                        sourceId,
                        order,
                        "Still-image media source could not be read"
                ));
            }
        }
        return new MediaIntakeResult(sourceFiles.size(), frames, diagnostics);
    }

    private MediaIntakeResult readVideoSources(
            List<Path> sourceFiles,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        List<MediaInputFrame> frames = new ArrayList<>();
        int frameOrder = 0;
        for (int order = 0; order < sourceFiles.size(); order++) {
            Path sourceFile = sourceFiles.get(order);
            CaptureMediaVideoFrameReadResult videoResult = videoFrameSourceAdapter.read(
                    new CaptureMediaVideoFrameReadRequest(sourceFile, order, videoLimits)
            );
            diagnostics.addAll(videoResult.diagnostics());
            for (CaptureMediaVideoFrame videoFrame : videoResult.frames()) {
                int[] pixels = videoFrame.copyArgbPixels();
                frames.add(new MediaInputFrame(
                        videoFrame.sourceId(),
                        CaptureMediaSourceKind.VIDEO_FILE,
                        frameOrder++,
                        videoFrame.widthPixels(),
                        videoFrame.heightPixels(),
                        videoFrame.pixelFormat(),
                        sha256Hex(pixels),
                        Optional.of(videoFrame.timestampMillis()),
                        Optional.of(videoFrame.frameNumber()),
                        pixels
                ));
            }
        }
        return new MediaIntakeResult(Math.max(sourceFiles.size(), frames.size()), frames, diagnostics);
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
        return switch (extension) {
            case "png", "jpg", "jpeg" -> stillImageDecoder.supportsExtension(extension)
                    ? SourceClassification.supported(extension)
                    : SourceClassification.unsupported(
                            CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                            "Media intake still-image decoder is unavailable for ." + extension
                    );
            case "heic", "heif" -> stillImageDecoder.supportsExtension(extension)
                    ? SourceClassification.supported(extension)
                    : SourceClassification.unsupported(
                            CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                            "HEIC/HEIF capture media input requires optional libheif heif-convert/heif-dec support; install libheif tools or configure jab4j.heif.convert.path / JAB4J_HEIF_CONVERT"
                    );
            case "mov", "mp4" -> SourceClassification.unsupported(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                    "Direct .mov/.mp4 capture media input is unsupported; no adapter is configured"
            );
            default -> SourceClassification.unsupported(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                    "Media intake supports PNG, JPEG, and optional HEIC/HEIF still-image files only"
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

    private String sha256Hex(int[] pixels) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int pixel : pixels) {
                digest.update((byte) (pixel >>> 24));
                digest.update((byte) (pixel >>> 16));
                digest.update((byte) (pixel >>> 8));
                digest.update((byte) pixel);
            }
            return HexFormat.of().formatHex(digest.digest());
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
            String extension,
            String message
    ) {

        static SourceClassification supported(String extension) {
            return new SourceClassification(false, Optional.empty(), extension, "");
        }

        static SourceClassification unsupported(CaptureMediaDiagnosticCode code, String message) {
            return new SourceClassification(true, Optional.of(code), "", message);
        }
    }
}
