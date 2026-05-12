package com.alx4j.jab4j.reader.cli;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Parses reader CLI arguments into either the current writer-export path or the capture receiver path.
 */
final class ReaderCliParser {

    /**
     * Parses exactly one reader input mode and one output path from CLI arguments.
     *
     * @param args raw CLI arguments
     * @return parsed reader CLI options
     */
    ReaderCliOptions parse(String[] args) {
        Objects.requireNonNull(args, "args must not be null");

        Path input = null;
        Path captureInput = null;
        Path captureMediaInput = null;
        Path output = null;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--input" -> {
                    if (input != null) {
                        throw new ReaderCliException("Only one --input path is supported");
                    }
                    input = Path.of(requireValue(args, ++index, argument));
                }
                case "--capture-input" -> {
                    if (captureInput != null) {
                        throw new ReaderCliException("Only one --capture-input path is supported");
                    }
                    captureInput = Path.of(requireValue(args, ++index, argument));
                }
                case "--capture-media-input" -> {
                    if (captureMediaInput != null) {
                        throw new ReaderCliException("Only one --capture-media-input path is supported");
                    }
                    captureMediaInput = Path.of(requireValue(args, ++index, argument));
                }
                case "--output" -> {
                    if (output != null) {
                        throw new ReaderCliException("Only one --output path is supported");
                    }
                    output = Path.of(requireValue(args, ++index, argument));
                }
                default -> throw new ReaderCliException("Unknown argument: " + argument);
            }
        }

        int inputModeCount = (input != null ? 1 : 0)
                + (captureInput != null ? 1 : 0)
                + (captureMediaInput != null ? 1 : 0);
        if (inputModeCount > 1 && input != null && captureInput != null && captureMediaInput == null) {
            throw new ReaderCliException("--input and --capture-input are mutually exclusive");
        }
        if (inputModeCount > 1 && input != null && captureMediaInput != null && captureInput == null) {
            throw new ReaderCliException("--input and --capture-media-input are mutually exclusive");
        }
        if (inputModeCount > 1 && captureInput != null && captureMediaInput != null && input == null) {
            throw new ReaderCliException("--capture-input and --capture-media-input are mutually exclusive");
        }
        if (inputModeCount > 1) {
            throw new ReaderCliException(
                    "Only one input mode is supported: use --input, --capture-input, or --capture-media-input"
            );
        }
        if (inputModeCount == 0) {
            throw new ReaderCliException(
                    "One input path is required: use --input, --capture-input, or --capture-media-input"
            );
        }
        if (captureMediaInput == null && output == null) {
            throw new ReaderCliException("One --output path is required");
        }
        if (input != null) {
            return ReaderCliOptions.baseline(input, output);
        }
        if (captureInput != null) {
            return ReaderCliOptions.capture(captureInput, output);
        }
        return ReaderCliOptions.captureMedia(captureMediaInput, java.util.Optional.ofNullable(output));
    }

    private String requireValue(String[] args, int index, String argumentName) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new ReaderCliException("Missing value for " + argumentName);
        }
        return args[index];
    }
}
