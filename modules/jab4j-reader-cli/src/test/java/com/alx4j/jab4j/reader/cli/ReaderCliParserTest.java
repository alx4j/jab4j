package com.alx4j.jab4j.reader.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reader CLI parsing")
class ReaderCliParserTest {

    private final ReaderCliParser parser = new ReaderCliParser();

    @Test
    @DisplayName("The required flags parse into reader input and output paths")
    void parsesInputAndOutputPaths() {
        ReaderCliOptions options = parser.parse(new String[] {
                "--input", "/tmp/export/imageSequence",
                "--output", "/tmp/restore"
        });

        assertAll(
                () -> assertEquals(Path.of("/tmp/export/imageSequence"), options.inputPath()),
                () -> assertEquals(Path.of("/tmp/restore"), options.outputPath())
        );
    }

    @Test
    @DisplayName("The required flags may appear in either order")
    void parsesFlagsInEitherOrder() {
        ReaderCliOptions options = parser.parse(new String[] {
                "--output", "/tmp/restore",
                "--input", "/tmp/export/imageSequence"
        });

        assertAll(
                () -> assertEquals(Path.of("/tmp/export/imageSequence"), options.inputPath()),
                () -> assertEquals(Path.of("/tmp/restore"), options.outputPath())
        );
    }

    @Test
    @DisplayName("Exactly one input path is required")
    void requiresExactlyOneInputPath() {
        ReaderCliException missing = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[0])
        );
        ReaderCliException duplicate = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {
                        "--input", "/tmp/a",
                        "--input", "/tmp/b",
                        "--output", "/tmp/out"
                })
        );

        assertEquals("One --input path is required", missing.getMessage());
        assertEquals("Only one --input path is supported", duplicate.getMessage());
    }

    @Test
    @DisplayName("Exactly one output path is required")
    void requiresExactlyOneOutputPath() {
        ReaderCliException missing = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--input", "/tmp/export"})
        );
        ReaderCliException duplicate = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {
                        "--input", "/tmp/export",
                        "--output", "/tmp/a",
                        "--output", "/tmp/b"
                })
        );

        assertEquals("One --output path is required", missing.getMessage());
        assertEquals("Only one --output path is supported", duplicate.getMessage());
    }

    @Test
    @DisplayName("Unknown arguments are rejected")
    void rejectsUnknownArguments() {
        ReaderCliException exception = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--input", "/tmp/export", "--output", "/tmp/out", "--verbose"})
        );

        assertEquals("Unknown argument: --verbose", exception.getMessage());
    }

    @Test
    @DisplayName("Positional arguments are rejected")
    void rejectsPositionalArguments() {
        ReaderCliException exception = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--input", "/tmp/export", "positional", "--output", "/tmp/out"})
        );

        assertEquals("Unknown argument: positional", exception.getMessage());
    }

    @Test
    @DisplayName("Missing flag values are rejected")
    void rejectsMissingFlagValues() {
        ReaderCliException trailing = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--input", "/tmp/export", "--output"})
        );
        ReaderCliException nextFlag = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--input", "--output", "/tmp/out"})
        );

        assertEquals("Missing value for --output", trailing.getMessage());
        assertEquals("Missing value for --input", nextFlag.getMessage());
    }
}
