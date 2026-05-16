package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Optional BoofCV backend shaded jar")
class BoofCvShadedJarIT {

    private static final String SERVICE_RESOURCE =
            "META-INF/services/com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend";
    private static final String BACKEND_CLASS =
            "com/alx4j/jab4j/reader/capture/media/cv/boofcv/BoofCvCaptureMediaCvBackend.class";
    private static final String SHADED_PREFIX = "com/alx4j/jab4j/reader/cv/boofcv/shaded/";
    private static final List<String> RAW_PACKAGE_PREFIXES = List.of(
            "boofcv/",
            "georegression/",
            "org/ejml/",
            "org/ddogleg/",
            "pabeles/concurrency/"
    );

    @Test
    @DisplayName("Packaged adapter keeps BoofCV dependencies relocated behind the optional module")
    void packagedAdapterKeepsBoofCvDependenciesRelocatedBehindOptionalModule() throws IOException {
        Path jarPath = Path.of(System.getProperty("adapter.jar.path"));

        assertTrue(Files.isRegularFile(jarPath), () -> "Missing adapter jar: " + jarPath);
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<String> entries = jar.stream()
                    .map(entry -> entry.getName())
                    .toList();
            Manifest manifest = jar.getManifest();

            assertAll(
                    () -> assertEquals(
                            "com.alx4j.jab4j.reader.cv.boofcv",
                            manifest.getMainAttributes().getValue("Automatic-Module-Name")
                    ),
                    () -> assertTrue(entries.contains(SERVICE_RESOURCE)),
                    () -> assertTrue(entries.contains(BACKEND_CLASS)),
                    () -> assertTrue(entries.stream().anyMatch(entry -> entry.startsWith(SHADED_PREFIX + "boofcv/"))),
                    () -> assertFalse(entries.contains(
                            "com/alx4j/jab4j/reader/capture/media/cv/CaptureMediaCvBackend.class"
                    )),
                    () -> assertFalse(entries.stream().anyMatch(entry -> entry.endsWith("module-info.class"))),
                    () -> assertFalse(entries.stream().anyMatch(this::isRawDependencyPackage),
                            () -> "Raw BoofCV dependency package leaked into shaded jar")
            );
        }
    }

    private boolean isRawDependencyPackage(String entryName) {
        return RAW_PACKAGE_PREFIXES.stream().anyMatch(entryName::startsWith);
    }
}
