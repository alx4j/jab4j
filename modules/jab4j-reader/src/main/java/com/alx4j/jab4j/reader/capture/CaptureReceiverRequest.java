package com.alx4j.jab4j.reader.capture;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Caller request for evaluating or restoring capture-derived reader input.
 *
 * @param inputSources capture folders or image frame paths supplied by the caller
 * @param outputDirectory optional restore output directory; absent for evaluate-only requests
 */
public record CaptureReceiverRequest(List<Path> inputSources, Optional<Path> outputDirectory) {

    /**
     * Creates a validated capture receiver request.
     *
     * @param inputSources capture folders or image frame paths
     * @param outputDirectory optional restore output directory
     */
    public CaptureReceiverRequest {
        Objects.requireNonNull(inputSources, "inputSources must not be null");
        inputSources = inputSources.stream()
                .map(CaptureReceiverRequest::normalizedPath)
                .toList();
        if (inputSources.isEmpty()) {
            throw new IllegalArgumentException("inputSources must not be empty");
        }
        inputSources = List.copyOf(inputSources);

        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null")
                .map(CaptureReceiverRequest::normalizedPath);
    }

    /**
     * Creates an evaluate-only request with no filesystem restore side effects.
     *
     * @param inputSources capture folders or image frame paths
     * @return evaluate-only capture receiver request
     */
    public static CaptureReceiverRequest evaluateOnly(Collection<Path> inputSources) {
        Objects.requireNonNull(inputSources, "inputSources must not be null");
        return new CaptureReceiverRequest(inputSources.stream().toList(), Optional.empty());
    }

    /**
     * Creates a restore request for capture input and a caller-selected output directory.
     *
     * @param inputSources capture folders or image frame paths
     * @param outputDirectory restore output directory
     * @return capture receiver restore request
     */
    public static CaptureReceiverRequest restore(Collection<Path> inputSources, Path outputDirectory) {
        Objects.requireNonNull(inputSources, "inputSources must not be null");
        return new CaptureReceiverRequest(
                inputSources.stream().toList(),
                Optional.of(Objects.requireNonNull(outputDirectory, "outputDirectory must not be null"))
        );
    }

    /**
     * Indicates whether this request allows a restore attempt after successful qualification.
     *
     * @return true when an output directory was supplied
     */
    public boolean restoreRequested() {
        return outputDirectory.isPresent();
    }

    private static Path normalizedPath(Path path) {
        return Objects.requireNonNull(path, "inputSources must not contain null values")
                .toAbsolutePath()
                .normalize();
    }
}
