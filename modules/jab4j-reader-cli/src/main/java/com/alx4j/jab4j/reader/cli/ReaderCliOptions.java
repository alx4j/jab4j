package com.alx4j.jab4j.reader.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable reader CLI arguments for a baseline or capture decode-and-restore run.
 *
 * @param inputPath optional current writer {@code imageSequence} PNG export directory or parent session directory with
 *         exactly one {@code imageSequence} child
 * @param captureInputPath optional extracted capture frame directory
 * @param outputPath restore target directory
 */
record ReaderCliOptions(Optional<Path> inputPath, Optional<Path> captureInputPath, Path outputPath) {

    ReaderCliOptions {
        inputPath = Objects.requireNonNull(inputPath, "inputPath must not be null");
        captureInputPath = Objects.requireNonNull(captureInputPath, "captureInputPath must not be null");
        outputPath = Objects.requireNonNull(outputPath, "outputPath must not be null");
        if (inputPath.isPresent() == captureInputPath.isPresent()) {
            throw new IllegalArgumentException("exactly one reader input mode must be supplied");
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
                outputPath
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
                outputPath
        );
    }
}
