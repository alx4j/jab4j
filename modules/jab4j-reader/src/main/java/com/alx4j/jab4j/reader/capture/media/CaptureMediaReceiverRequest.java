package com.alx4j.jab4j.reader.capture.media;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Caller request for evaluating or restoring real-world capture media.
 *
 * @param sourceKind kind of media supplied by the caller
 * @param inputSources media files or folders supplied by the caller
 * @param outputDirectory optional restore output directory; absent for evaluate-only requests
 */
public record CaptureMediaReceiverRequest(
        CaptureMediaSourceKind sourceKind,
        List<Path> inputSources,
        Optional<Path> outputDirectory
) {

    /**
     * Creates a validated media receiver request.
     *
     * @param sourceKind caller-declared media source kind
     * @param inputSources media files or folders supplied by the caller
     * @param outputDirectory optional restore output directory
     */
    public CaptureMediaReceiverRequest {
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        Objects.requireNonNull(inputSources, "inputSources must not be null");
        inputSources = inputSources.stream()
                .map(CaptureMediaReceiverRequest::normalizedInputPath)
                .toList();
        if (inputSources.isEmpty()) {
            throw new IllegalArgumentException("inputSources must not be empty");
        }
        inputSources = List.copyOf(inputSources);

        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null")
                .map(CaptureMediaReceiverRequest::normalizedOutputPath);
    }

    /**
     * Creates an evaluate-only request with no filesystem restore side effects.
     *
     * @param sourceKind caller-declared media source kind
     * @param inputSources media files or folders supplied by the caller
     * @return evaluate-only media receiver request
     */
    public static CaptureMediaReceiverRequest evaluateOnly(
            CaptureMediaSourceKind sourceKind,
            Collection<Path> inputSources
    ) {
        Objects.requireNonNull(inputSources, "inputSources must not be null");
        return new CaptureMediaReceiverRequest(sourceKind, inputSources.stream().toList(), Optional.empty());
    }

    /**
     * Creates a restore request for media input and a caller-selected output directory.
     *
     * @param sourceKind caller-declared media source kind
     * @param inputSources media files or folders supplied by the caller
     * @param outputDirectory restore output directory
     * @return media receiver restore request
     */
    public static CaptureMediaReceiverRequest restore(
            CaptureMediaSourceKind sourceKind,
            Collection<Path> inputSources,
            Path outputDirectory
    ) {
        Objects.requireNonNull(inputSources, "inputSources must not be null");
        return new CaptureMediaReceiverRequest(
                sourceKind,
                inputSources.stream().toList(),
                Optional.of(Objects.requireNonNull(outputDirectory, "outputDirectory must not be null"))
        );
    }

    /**
     * Creates an evaluate-only request for still image files.
     *
     * @param inputSources still image files supplied by the caller
     * @return evaluate-only still-image media request
     */
    public static CaptureMediaReceiverRequest evaluateStillImages(Collection<Path> inputSources) {
        return evaluateOnly(CaptureMediaSourceKind.STILL_IMAGE_FILE, inputSources);
    }

    /**
     * Creates a restore request for still image files.
     *
     * @param inputSources still image files supplied by the caller
     * @param outputDirectory restore output directory
     * @return still-image media restore request
     */
    public static CaptureMediaReceiverRequest restoreStillImages(Collection<Path> inputSources, Path outputDirectory) {
        return restore(CaptureMediaSourceKind.STILL_IMAGE_FILE, inputSources, outputDirectory);
    }

    /**
     * Creates an evaluate-only request for folders of extracted video frames.
     *
     * @param inputSources extracted-frame folders supplied by the caller
     * @return evaluate-only extracted-frame media request
     */
    public static CaptureMediaReceiverRequest evaluateExtractedFrameFolders(Collection<Path> inputSources) {
        return evaluateOnly(CaptureMediaSourceKind.EXTRACTED_FRAME_FOLDER, inputSources);
    }

    /**
     * Creates a restore request for folders of extracted video frames.
     *
     * @param inputSources extracted-frame folders supplied by the caller
     * @param outputDirectory restore output directory
     * @return extracted-frame media restore request
     */
    public static CaptureMediaReceiverRequest restoreExtractedFrameFolders(
            Collection<Path> inputSources,
            Path outputDirectory
    ) {
        return restore(CaptureMediaSourceKind.EXTRACTED_FRAME_FOLDER, inputSources, outputDirectory);
    }

    /**
     * Creates an evaluate-only request for direct video files.
     *
     * @param inputSources direct video files supplied by the caller
     * @return evaluate-only direct-video media request
     */
    public static CaptureMediaReceiverRequest evaluateVideoFiles(Collection<Path> inputSources) {
        return evaluateOnly(CaptureMediaSourceKind.VIDEO_FILE, inputSources);
    }

    /**
     * Creates a restore request for direct video files.
     *
     * @param inputSources direct video files supplied by the caller
     * @param outputDirectory restore output directory
     * @return direct-video media restore request
     */
    public static CaptureMediaReceiverRequest restoreVideoFiles(Collection<Path> inputSources, Path outputDirectory) {
        return restore(CaptureMediaSourceKind.VIDEO_FILE, inputSources, outputDirectory);
    }

    /**
     * Indicates whether this request allows a restore attempt after successful media evaluation.
     *
     * @return true when an output directory was supplied
     */
    public boolean restoreRequested() {
        return outputDirectory.isPresent();
    }

    private static Path normalizedInputPath(Path path) {
        return Objects.requireNonNull(path, "inputSources must not contain null values")
                .toAbsolutePath()
                .normalize();
    }

    private static Path normalizedOutputPath(Path path) {
        return Objects.requireNonNull(path, "outputDirectory must not be null")
                .toAbsolutePath()
                .normalize();
    }
}
