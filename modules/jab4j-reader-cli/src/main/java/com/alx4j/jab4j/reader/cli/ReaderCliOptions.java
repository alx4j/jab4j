package com.alx4j.jab4j.reader.cli;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable reader CLI arguments for a decode-and-restore run.
 *
 * @param inputPath frame export or session directory to decode
 * @param outputPath restore target directory
 */
record ReaderCliOptions(Path inputPath, Path outputPath) {

    ReaderCliOptions {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
    }
}
