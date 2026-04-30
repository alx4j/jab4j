package com.alx4j.jab4j.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.writer.app.WriterRunRequest;

@DisplayName("Writer CLI parsing")
class WriterCliParserTest {

    private static final String INPUT_ROOT_A = "/data/root-a";
    private static final String INPUT_ROOT_B = "/data/root-b";

    private final WriterCliParser parser = new WriterCliParser();

    @Test
    @DisplayName("Baseline flags parse into a writer run request")
    void parsesBaselineFlagsIntoWriterRunRequest() {
        WriterRunRequest request = parser.parse(new String[] {
                "--input", INPUT_ROOT_A,
                "--input", INPUT_ROOT_B,
                "--profile", "debug-low-density",
                "--grid", "2x2",
                "--fps", "5",
                "--chunk-bytes", "64",
                "--fullscreen",
                "--dry-run"
        });

        assertAll(
                () -> assertTrue(request.dryRun()),
                () -> assertEquals("debug-low-density", request.cliOverrides().app().profile()),
                () -> assertEquals(2, request.cliOverrides().input().roots().size()),
                () -> assertEquals(2, request.cliOverrides().layout().rows()),
                () -> assertEquals(2, request.cliOverrides().layout().cols()),
                () -> assertEquals(5, request.cliOverrides().playback().fps()),
                () -> assertTrue(request.cliOverrides().playback().fullscreen()),
                () -> assertEquals(64, request.cliOverrides().transport().chunkBytes())
        );
    }

    @Test
    @DisplayName("Capture-ready sender command uses display playback with exact frame export")
    void parsesCaptureReadySenderCommand() {
        WriterRunRequest request = parser.parse(new String[] {
                "--input", INPUT_ROOT_A,
                "--profile", "debug-low-density",
                "--export-frames"
        });

        assertAll(
                () -> assertFalse(request.dryRun()),
                () -> assertEquals("debug-low-density", request.cliOverrides().app().profile()),
                () -> assertTrue(request.cliOverrides().export().enabled()),
                () -> assertEquals("imageSequence", request.cliOverrides().export().mode())
        );
    }

    @Test
    @DisplayName("The export-frames flag enables image-sequence export mode")
    void parsesOptionalExportFramesFlagIntoImageSequenceExportMode() {
        WriterRunRequest request = parser.parse(new String[] {
                "--input", INPUT_ROOT_A,
                "--export-frames"
        });

        assertAll(
                () -> assertTrue(request.cliOverrides().export().enabled()),
                () -> assertEquals("imageSequence", request.cliOverrides().export().mode())
        );
    }

    @Test
    @DisplayName("Invalid grid syntax is rejected")
    void rejectsInvalidGridSyntax() {
        WriterCliException exception = assertThrows(
                WriterCliException.class,
                () -> parser.parse(new String[] {"--input", INPUT_ROOT_A, "--grid", "bad"})
        );

        assertEquals("Invalid value for --grid: bad", exception.getMessage());
    }

    @Test
    @DisplayName("At least one input path is required")
    void requiresAtLeastOneInput() {
        WriterCliException exception = assertThrows(
                WriterCliException.class,
                () -> parser.parse(new String[] {"--profile", "desktop-1080p-safe"})
        );

        assertEquals("At least one --input path is required", exception.getMessage());
    }
}
