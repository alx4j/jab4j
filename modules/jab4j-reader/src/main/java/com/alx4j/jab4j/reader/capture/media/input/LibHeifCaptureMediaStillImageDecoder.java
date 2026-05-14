package com.alx4j.jab4j.reader.capture.media.input;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;

/**
 * Optional libheif command-line adapter that converts HEIC/HEIF still images through libheif tools.
 */
final class LibHeifCaptureMediaStillImageDecoder implements CaptureMediaStillImageDecoder {

    static final String CONVERTER_PROPERTY = "jab4j.heif.convert.path";
    static final String CONVERTER_ENVIRONMENT_VARIABLE = "JAB4J_HEIF_CONVERT";
    private static final Duration CONVERSION_TIMEOUT = Duration.ofSeconds(30);

    private final Optional<Path> converterExecutable;
    private final ImageIoCaptureMediaStillImageDecoder imageIoDecoder;

    /**
     * Creates a libheif decoder for an explicitly discovered converter executable.
     *
     * @param converterExecutable optional absolute or relative path to {@code heif-convert} or {@code heif-dec}
     */
    LibHeifCaptureMediaStillImageDecoder(Optional<Path> converterExecutable) {
        this.converterExecutable = Objects.requireNonNull(
                        converterExecutable,
                        "converterExecutable must not be null"
                )
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> Files.isRegularFile(path) && Files.isReadable(path));
        this.imageIoDecoder = new ImageIoCaptureMediaStillImageDecoder();
    }

    /**
     * Creates a libheif decoder using system property, environment variable, or PATH discovery.
     *
     * @return environment-discovered libheif decoder
     */
    static LibHeifCaptureMediaStillImageDecoder fromEnvironment() {
        return new LibHeifCaptureMediaStillImageDecoder(discoverConverterExecutable());
    }

    @Override
    public boolean supportsExtension(String extension) {
        return converterExecutable.isPresent() && heifExtension(extension);
    }

    @Override
    public DecodedStillImage decode(Path sourceFile, String extension) throws IOException {
        if (!supportsExtension(extension)) {
            throw new StillImageDecodeException(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                    unavailableMessage()
            );
        }

        Path tempDirectory = Files.createTempDirectory("jab4j-heif-");
        Path convertedPng = tempDirectory.resolve(sourceFile.getFileName() + ".png");
        Path processLog = tempDirectory.resolve("libheif-decode.log");
        try {
            runConverter(sourceFile, convertedPng, processLog);
            DecodedStillImage decodedPng;
            try {
                decodedPng = imageIoDecoder.decode(convertedPng, "png");
            } catch (IOException exception) {
                throw new StillImageDecodeException(
                        CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                        "libheif HEIC tool did not produce a readable PNG image",
                        exception
                );
            }
            return new DecodedStillImage(
                    decodedPng.widthPixels(),
                    decodedPng.heightPixels(),
                    heifFormatName(extension),
                    decodedPng.copyArgbPixels()
            );
        } finally {
            deleteQuietly(convertedPng);
            deleteQuietly(processLog);
            deleteQuietly(tempDirectory);
        }
    }

    private void runConverter(Path sourceFile, Path convertedPng, Path processLog) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                converterExecutable.orElseThrow().toString(),
                sourceFile.toString(),
                convertedPng.toString()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(processLog.toFile());
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            throw new StillImageDecodeException(
                    CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                    unavailableMessage(),
                    exception
            );
        }
        boolean finished;
        try {
            finished = process.waitFor(CONVERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new StillImageDecodeException(
                    CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                    "Interrupted while converting HEIC/HEIF media with libheif",
                    exception
            );
        }
        if (!finished) {
            process.destroyForcibly();
            throw new StillImageDecodeException(
                    CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                    "Timed out while converting HEIC/HEIF media with libheif"
            );
        }
        if (process.exitValue() != 0) {
            String processOutput = truncatedProcessLog(processLog);
            if (looksLikeNativeLoadFailure(process.exitValue(), processOutput)) {
                throw new StillImageDecodeException(
                        CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                        withProcessOutput(
                                "HEIC/HEIF capture media input requires optional libheif HEIC tool support; the configured tool could not load its native libraries",
                                processOutput
                        )
                );
            }
            throw new StillImageDecodeException(
                    CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                    withProcessOutput(
                            "HEIC/HEIF media source could not be decoded by the configured libheif HEIC tool",
                            processOutput
                    )
            );
        }
        if (!Files.isRegularFile(convertedPng)) {
            throw new StillImageDecodeException(
                    CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                    "libheif HEIC tool did not produce a PNG image"
            );
        }
    }

    private String truncatedProcessLog(Path processLog) throws IOException {
        if (!Files.isRegularFile(processLog)) {
            return "";
        }
        String log = new String(Files.readAllBytes(processLog), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
        if (log.length() <= 240) {
            return log;
        }
        return log.substring(0, 240) + "...";
    }

    private boolean looksLikeNativeLoadFailure(int exitCode, String processOutput) {
        String normalizedOutput = processOutput.toLowerCase(Locale.ROOT);
        return normalizedOutput.contains("error while loading shared libraries")
                || normalizedOutput.contains("cannot open shared object file")
                || (normalizedOutput.contains("libheif")
                && (normalizedOutput.contains("not found")
                || normalizedOutput.contains("cannot open")
                || normalizedOutput.contains("missing")
                || normalizedOutput.contains("load")))
                || (normalizedOutput.contains("dll") && normalizedOutput.contains("not found"))
                || (exitCode == 127 && normalizedOutput.contains("library"));
    }

    private String unavailableMessage() {
        return "HEIC/HEIF capture media input requires optional libheif heif-convert/heif-dec support; install libheif tools or configure "
                + CONVERTER_PROPERTY + " / " + CONVERTER_ENVIRONMENT_VARIABLE;
    }

    private String withProcessOutput(String message, String processOutput) {
        if (processOutput == null || processOutput.isBlank()) {
            return message;
        }
        return message + ": " + processOutput;
    }

    private static Optional<Path> discoverConverterExecutable() {
        Optional<Path> configured = configuredExecutable();
        if (configured.isPresent()) {
            return configured;
        }
        return searchPathExecutable();
    }

    private static Optional<Path> configuredExecutable() {
        String configured = firstNonBlank(
                System.getProperty(CONVERTER_PROPERTY),
                System.getenv(CONVERTER_ENVIRONMENT_VARIABLE)
        );
        if (configured == null) {
            return Optional.empty();
        }
        return Optional.of(Path.of(configured));
    }

    private static Optional<Path> searchPathExecutable() {
        String pathVariable = System.getenv("PATH");
        if (pathVariable == null || pathVariable.isBlank()) {
            return Optional.empty();
        }
        for (String directory : pathVariable.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            for (String executableName : executableNames()) {
                Path candidate = Path.of(directory).resolve(executableName);
                if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> executableNames() {
        return executableNames(System.getProperty("os.name", ""));
    }

    /**
     * Returns libheif executable names to try for the supplied operating system name.
     *
     * @param operatingSystemName JVM operating system name
     * @return ordered executable names for PATH discovery
     */
    static List<String> executableNames(String operatingSystemName) {
        List<String> names = new ArrayList<>();
        names.add("heif-convert");
        names.add("heif-dec");
        if (operatingSystemName.toLowerCase(Locale.ROOT).contains("win")) {
            names.add("heif-convert.exe");
            names.add("heif-dec.exe");
        }
        return names;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private boolean heifExtension(String extension) {
        return switch (CaptureMediaStillImageDecoder.normalizedExtension(extension)) {
            case "heic", "heif" -> true;
            default -> false;
        };
    }

    private String heifFormatName(String extension) {
        return "heif".equals(extension) ? "heif" : "heic";
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary conversion artifacts are best-effort cleanup.
        }
    }
}
