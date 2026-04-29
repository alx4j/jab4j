package com.alx4j.jab4j.reader.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reader CLI parsing")
class ReaderCliParserTest {

    private final ReaderCliParser parser = new ReaderCliParser();

    @Test
    @DisplayName("The input flag parses into a reader input path")
    void parsesInputPath() {
        Path input = parser.parse(new String[] {"--input", "/tmp/export/imageSequence"});

        assertEquals(Path.of("/tmp/export/imageSequence"), input);
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
                () -> parser.parse(new String[] {"--input", "/tmp/a", "--input", "/tmp/b"})
        );

        assertEquals("One --input path is required", missing.getMessage());
        assertEquals("Only one --input path is supported", duplicate.getMessage());
    }

    @Test
    @DisplayName("Unknown arguments are rejected")
    void rejectsUnknownArguments() {
        ReaderCliException exception = assertThrows(
                ReaderCliException.class,
                () -> parser.parse(new String[] {"--input", "/tmp/export", "--output", "/tmp/out"})
        );

        assertEquals("Unknown argument: --output", exception.getMessage());
    }
}
