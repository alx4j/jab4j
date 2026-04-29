package com.alx4j.jab4j.reader.cli;

import java.nio.file.Path;
import java.util.Objects;

final class ReaderCliParser {

    Path parse(String[] args) {
        Objects.requireNonNull(args, "args must not be null");

        Path input = null;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--input" -> {
                    if (input != null) {
                        throw new ReaderCliException("Only one --input path is supported");
                    }
                    input = Path.of(requireValue(args, ++index, argument));
                }
                default -> throw new ReaderCliException("Unknown argument: " + argument);
            }
        }

        if (input == null) {
            throw new ReaderCliException("One --input path is required");
        }
        return input;
    }

    private String requireValue(String[] args, int index, String argumentName) {
        if (index >= args.length) {
            throw new ReaderCliException("Missing value for " + argumentName);
        }
        return args[index];
    }
}
