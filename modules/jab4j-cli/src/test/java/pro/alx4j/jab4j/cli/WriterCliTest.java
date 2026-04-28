package pro.alx4j.jab4j.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.LoggerFactory;
import pro.alx4j.jab4j.api.model.SessionId;
import pro.alx4j.jab4j.writer.app.WriterApplicationService;

@DisplayName("Writer CLI execution")
class WriterCliTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-22T19:45:00Z");
    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Dry-run execution prints a completion summary")
    @ResourceLock("logback")
    void runsDryRunPipelineAndPrintsCompletionSummary() throws Exception {
        Path inputRoot = Files.createDirectory(tempDir.resolve("cli-input"));
        Files.writeString(inputRoot.resolve("hello.txt"), "hello-from-cli");

        WriterApplicationService service = new WriterApplicationService(
                () -> FIXED_NOW,
                () -> FIXED_SESSION_ID,
                tempDir.resolve("diagnostics"),
                tempDir.resolve("exports")
        );
        WriterCli cli = new WriterCli(service, new WriterCliParser());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode;
        try (LogCapture logCapture = captureLogs()) {
            exitCode = cli.run(
                    new String[] {
                            "--input", inputRoot.toString(),
                            "--chunk-bytes", "32",
                            "--fps", "4",
                            "--dry-run",
                            "--export-frames"
                    },
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8)
            );

            String stdoutText = stdout.toString(StandardCharsets.UTF_8);
            String stderrText = stderr.toString(StandardCharsets.UTF_8);

            assertAll(
                    () -> assertEquals(0, exitCode),
                    () -> assertTrue(stdoutText.contains("COMPLETED")),
                    () -> assertTrue(stdoutText.contains("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")),
                    () -> assertTrue(stdoutText.contains("finalSessionDigest=")),
                    () -> assertTrue(stdoutText.contains("exportDirectory=")),
                    () -> assertEquals("", stderrText),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Writer CLI starting dryRun=true")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "inputRoots=[" + inputRoot + "]")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "exportEnabled=true")),
                    () -> assertTrue(logCapture.contains(Level.INFO, "Writer CLI completed sessionId=" + FIXED_SESSION_ID))
            );
        }
    }

    @Test
    @DisplayName("Usage errors log concise warnings")
    @ResourceLock("logback")
    void usageErrorsLogConciseWarnings() {
        WriterCli cli = new WriterCli(
                new WriterApplicationService(() -> FIXED_NOW, () -> FIXED_SESSION_ID),
                new WriterCliParser()
        );
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (LogCapture logCapture = captureLogs()) {
            int exitCode = cli.run(
                    new String[] {"--input", "/tmp/demo", "--grid", "bad"},
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8)
            );

            assertAll(
                    () -> assertEquals(2, exitCode),
                    () -> assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Invalid value for --grid: bad")),
                    () -> assertTrue(logCapture.contains(Level.WARN, "Writer CLI usage error message=Invalid value for --grid: bad"))
            );
        }
    }

    @Test
    @DisplayName("Runtime failures keep the throwable on the error log")
    @ResourceLock("logback")
    void runtimeFailuresKeepThrowableOnErrorLog() {
        WriterCli cli = new WriterCli(
                new WriterApplicationService(() -> FIXED_NOW, () -> FIXED_SESSION_ID),
                new WriterCliParser()
        );
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String missingPath = tempDir.resolve("missing-input").toString();

        try (LogCapture logCapture = captureLogs()) {
            int exitCode = cli.run(
                    new String[] {"--input", missingPath},
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8)
            );

            assertAll(
                    () -> assertEquals(1, exitCode),
                    () -> assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("FAILED Input root is not a readable directory")),
                    () -> assertTrue(logCapture.containsThrowable(
                            Level.ERROR,
                            "Writer CLI execution failed message=Input root is not a readable directory"
                    ))
            );
        }
    }

    private LogCapture captureLogs() {
        return new LogCapture(WriterCli.class);
    }

    private static final class LogCapture implements AutoCloseable {

        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender;
        private final Level previousLevel;
        private final boolean previousAdditive;

        private LogCapture(Class<?> loggerType) {
            this.logger = (Logger) LoggerFactory.getLogger(loggerType);
            this.previousLevel = logger.getLevel();
            this.previousAdditive = logger.isAdditive();
            this.appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
        }

        private boolean contains(Level level, String messageFragment) {
            return appender.list.stream().anyMatch(event ->
                    event.getLevel() == level && event.getFormattedMessage().contains(messageFragment)
            );
        }

        private boolean containsThrowable(Level level, String messageFragment) {
            return appender.list.stream().anyMatch(event ->
                    event.getLevel() == level
                            && event.getFormattedMessage().contains(messageFragment)
                            && event.getThrowableProxy() != null
            );
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
    }
}
