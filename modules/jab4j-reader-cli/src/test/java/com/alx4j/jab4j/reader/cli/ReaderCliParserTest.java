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
                () -> assertEquals(Path.of("/tmp/export/imageSequence"), options.inputPath().orElseThrow()),
                () -> assertEquals(java.util.Optional.empty(), options.captureInputPath()),
                () -> assertEquals(Path.of("/tmp/restore"), options.outputPath())
        );
    }

    @Test
    @DisplayName("The capture input flag parses into capture mode")
    void parsesCaptureInputPath() {
        ReaderCliOptions options = parser.parse(new String[] {
                "--capture-input", "/tmp/capture-frames",
                "--output", "/tmp/restore"
        });

        assertAll(
                () -> assertEquals(java.util.Optional.empty(), options.inputPath()),
                () -> assertEquals(Path.of("/tmp/capture-frames"), options.captureInputPath().orElseThrow()),
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
                () -> assertEquals(Path.of("/tmp/export/imageSequence"), options.inputPath().orElseThrow()),
                () -> assertEquals(java.util.Optional.empty(), options.captureInputPath()),
                () -> assertEquals(Path.of("/tmp/restore"), options.outputPath())
        );
    }

    @Test
    @DisplayName("Exactly one reader input path is required")
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
        ReaderCliException duplicateCapture = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {
                        "--capture-input", "/tmp/a",
                        "--capture-input", "/tmp/b",
                        "--output", "/tmp/out"
                })
        );
        ReaderCliException mutuallyExclusive = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {
                        "--input", "/tmp/exact",
                        "--capture-input", "/tmp/capture",
                        "--output", "/tmp/out"
                })
        );

        assertAll(
                () -> assertEquals("One input path is required: use --input or --capture-input", missing.getMessage()),
                () -> assertEquals("Only one --input path is supported", duplicate.getMessage()),
                () -> assertEquals("Only one --capture-input path is supported", duplicateCapture.getMessage()),
                () -> assertEquals("--input and --capture-input are mutually exclusive", mutuallyExclusive.getMessage())
        );
    }

    @Test
    @DisplayName("Exactly one output path is required")
    void requiresExactlyOneOutputPath() {
        ReaderCliException missing = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--input", "/tmp/export"})
        );
        ReaderCliException missingCaptureOutput = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--capture-input", "/tmp/capture"})
        );
        ReaderCliException duplicate = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {
                        "--input", "/tmp/export",
                        "--output", "/tmp/a",
                        "--output", "/tmp/b"
                })
        );

        assertAll(
                () -> assertEquals("One --output path is required", missing.getMessage()),
                () -> assertEquals("One --output path is required", missingCaptureOutput.getMessage()),
                () -> assertEquals("Only one --output path is supported", duplicate.getMessage())
        );
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
