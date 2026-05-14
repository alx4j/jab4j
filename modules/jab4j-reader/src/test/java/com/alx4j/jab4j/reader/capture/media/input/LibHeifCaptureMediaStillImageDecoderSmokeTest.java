package com.alx4j.jab4j.reader.capture.media.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;

@DisplayName("libheif still-image decoder native smoke")
class LibHeifCaptureMediaStillImageDecoderSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("PATH discovery includes MSYS2 libheif command names")
    void pathDiscoveryIncludesMsys2LibheifCommandNames() {
        assertIterableEquals(
                List.of("heif-convert", "heif-dec", "heif-convert.exe", "heif-dec.exe"),
                LibHeifCaptureMediaStillImageDecoder.executableNames("Windows 11")
        );
    }

    @Test
    @DisplayName("Native libheif command starts when HEIC smoke validation is enabled")
    void nativeLibheifCommandStartsWhenHeicSmokeValidationIsEnabled() throws Exception {
        assumeTrue(
                nativeSmokeEnabled(),
                "Set -Djab4j.heif.nativeSmoke=true or JAB4J_HEIF_NATIVE_SMOKE=true to run native HEIC smoke validation."
        );
        LibHeifCaptureMediaStillImageDecoder decoder = LibHeifCaptureMediaStillImageDecoder.fromEnvironment();
        assertTrue(decoder.supportsExtension("HEIC"),
                "heif-convert or heif-dec must be on PATH or configured with jab4j.heif.convert.path / JAB4J_HEIF_CONVERT");
        Path invalidHeic = tempDir.resolve("invalid.heic");
        Files.writeString(invalidHeic, "not a real HEIC image", StandardCharsets.UTF_8);

        StillImageDecodeException exception =
                assertThrows(StillImageDecodeException.class, () -> decoder.decode(invalidHeic, "heic"));

        assertEquals(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA, exception.diagnosticCode());
    }

    private boolean nativeSmokeEnabled() {
        return Boolean.getBoolean("jab4j.heif.nativeSmoke")
                || Boolean.parseBoolean(System.getenv("JAB4J_HEIF_NATIVE_SMOKE"));
    }
}
