package pro.alx4j.jab4j.writer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.alx4j.jab4j.api.model.SessionId;
import pro.alx4j.jab4j.writer.app.WriterApplicationService;
import pro.alx4j.jab4j.writer.app.WriterRunResult;

@DisplayName("JAB writer facade")
class JabWriterTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    private static final int UNPACED_TEST_FPS = 10_000;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Dry writer jobs are available through the client facade")
    void runsDryWriterJobThroughFacade() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-facade");
        WriterApplicationService service = new WriterApplicationService(
                () -> Instant.parse("2026-03-22T19:40:00Z"),
                () -> FIXED_SESSION_ID,
                tempDir.resolve("diagnostics"),
                tempDir.resolve("exports")
        );

        WriterRunResult result = JabWriter.builder(service)
                .input(inputRoot)
                .profile("debug-low-density")
                .chunkBytes(32)
                .fps(UNPACED_TEST_FPS)
                .dryRun()
                .run();

        assertAll(
                () -> assertEquals(FIXED_SESSION_ID, result.sessionId()),
                () -> assertEquals("debug-low-density", result.effectiveConfig().app().profile()),
                () -> assertEquals(UNPACED_TEST_FPS, result.effectiveConfig().playback().fps()),
                () -> assertEquals(32, result.effectiveConfig().transport().chunkBytes()),
                () -> assertFalse(result.renderedFrameHashes().isEmpty())
        );
    }

    @Test
    @DisplayName("Writer facade requires at least one input root")
    void requiresInputRootBeforeRunning() {
        WriterApplicationService service = new WriterApplicationService(
                () -> Instant.parse("2026-03-22T19:40:00Z"),
                () -> FIXED_SESSION_ID,
                tempDir.resolve("diagnostics"),
                tempDir.resolve("exports")
        );

        assertThrows(IllegalStateException.class, () -> JabWriter.builder(service).dryRun().run());
    }
}
