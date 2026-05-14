package com.alx4j.jab4j.reader.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable reader CLI arguments for one exact, capture, or capture-media run.
 *
 * @param inputPath optional current writer {@code imageSequence} PNG export directory or parent session directory with
 *         exactly one {@code imageSequence} child
 * @param captureInputPath optional extracted capture frame directory
 * @param captureMediaInputPath optional MVP-3 capture media source
 * @param outputPath optional restore target directory
 * @param captureMediaDebugOutputPath optional capture-media debug output directory
 */
record ReaderCliOptions(
        Optional<Path> inputPath,
        Optional<Path> captureInputPath,
        Optional<Path> captureMediaInputPath,
        Optional<Path> outputPath,
        Optional<Path> captureMediaDebugOutputPath
) {

    ReaderCliOptions {
        inputPath = Objects.requireNonNull(inputPath, "inputPath must not be null");
        captureInputPath = Objects.requireNonNull(captureInputPath, "captureInputPath must not be null");
        captureMediaInputPath = Objects.requireNonNull(captureMediaInputPath, "captureMediaInputPath must not be null");
        outputPath = Objects.requireNonNull(outputPath, "outputPath must not be null");
        captureMediaDebugOutputPath = Objects.requireNonNull(
                captureMediaDebugOutputPath,
                "captureMediaDebugOutputPath must not be null"
        );
        int inputModes = (inputPath.isPresent() ? 1 : 0)
                + (captureInputPath.isPresent() ? 1 : 0)
                + (captureMediaInputPath.isPresent() ? 1 : 0);
        if (inputModes != 1) {
            throw new IllegalArgumentException("exactly one reader input mode must be supplied");
        }
        if ((inputPath.isPresent() || captureInputPath.isPresent()) && outputPath.isEmpty()) {
            throw new IllegalArgumentException("baseline and capture modes require an output path");
        }
        if (captureMediaDebugOutputPath.isPresent() && captureMediaInputPath.isEmpty()) {
            throw new IllegalArgumentException("capture media debug output requires capture-media input");
        }
    }

    /**
     * Creates options for the exact writer PNG baseline path.
     *
     * @param inputPath writer {@code imageSequence} or parent session directory
     * @param outputPath restore target directory
     * @return baseline reader CLI options
     */
    static ReaderCliOptions baseline(Path inputPath, Path outputPath) {
        return new ReaderCliOptions(
                Optional.of(Objects.requireNonNull(inputPath, "inputPath must not be null")),
                Optional.empty(),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(outputPath, "outputPath must not be null")),
                Optional.empty()
        );
    }

    /**
     * Creates options for the extracted capture frame receiver path.
     *
     * @param captureInputPath extracted capture frame directory
     * @param outputPath restore target directory
     * @return capture reader CLI options
     */
    static ReaderCliOptions capture(Path captureInputPath, Path outputPath) {
        return new ReaderCliOptions(
                Optional.empty(),
                Optional.of(Objects.requireNonNull(captureInputPath, "captureInputPath must not be null")),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(outputPath, "outputPath must not be null")),
                Optional.empty()
        );
    }

    /**
     * Creates options for the MVP-3 capture media receiver path.
     *
     * @param captureMediaInputPath capture media source path
     * @param outputPath optional restore target directory
     * @return capture media reader CLI options
     */
    static ReaderCliOptions captureMedia(Path captureMediaInputPath, Optional<Path> outputPath) {
        return captureMedia(captureMediaInputPath, outputPath, Optional.empty());
    }

    /**
     * Creates options for the MVP-3 capture media receiver path with optional debug output.
     *
     * @param captureMediaInputPath capture media source path
     * @param outputPath optional restore target directory
     * @param captureMediaDebugOutputPath optional debug output directory
     * @return capture media reader CLI options
     */
    static ReaderCliOptions captureMedia(
            Path captureMediaInputPath,
            Optional<Path> outputPath,
            Optional<Path> captureMediaDebugOutputPath
    ) {
        return new ReaderCliOptions(
                Optional.empty(),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(captureMediaInputPath, "captureMediaInputPath must not be null")),
                Objects.requireNonNull(outputPath, "outputPath must not be null"),
                Objects.requireNonNull(captureMediaDebugOutputPath, "captureMediaDebugOutputPath must not be null")
        );
    }
}
