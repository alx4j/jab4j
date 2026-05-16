package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend;

@DisplayName("Optional BoofCV backend shaded jar")
class BoofCvShadedJarIT {

    private static final String SERVICE_RESOURCE =
            "META-INF/services/com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend";
    private static final String BACKEND_CLASS =
            "com/alx4j/jab4j/reader/capture/media/cv/boofcv/BoofCvCaptureMediaCvBackend.class";
    private static final String OPTIONAL_BACKEND_PACKAGE =
            "com/alx4j/jab4j/reader/capture/media/cv/boofcv/";
    private static final String OPTIONAL_BACKEND_CLASS_PREFIX =
            "com.alx4j.jab4j.reader.capture.media.cv.boofcv.";
    private static final List<String> READER_PACKAGE_PREFIXES = List.of(
            "com/alx4j/jab4j/reader/app/",
            "com/alx4j/jab4j/reader/capture/media/",
            "com/alx4j/jab4j/reader/content/",
            "com/alx4j/jab4j/reader/frame/",
            "com/alx4j/jab4j/reader/restore/"
    );
    private static final String SHADED_PREFIX = "com/alx4j/jab4j/reader/cv/boofcv/shaded/";
    private static final String SHADED_CLASS_PREFIX = "com.alx4j.jab4j.reader.cv.boofcv.shaded.";
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
        Path jarPath = adapterJarPath();

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
                    () -> assertFalse(entries.stream().anyMatch(this::isPackagedReaderClass),
                            () -> "Shaded adapter must not package jab4j-reader classes"),
                    () -> assertFalse(entries.stream().anyMatch(entry -> entry.endsWith("module-info.class"))),
                    () -> assertFalse(entries.stream().anyMatch(this::isRawDependencyPackage),
                            () -> "Raw BoofCV dependency package leaked into shaded jar")
            );
        }
    }

    @Test
    @DisplayName("Packaged adapter exposes stable BoofCV backend id through ServiceLoader")
    void packagedAdapterExposesStableBoofCvBackendIdThroughServiceLoader() throws IOException {
        Path jarPath = adapterJarPath();

        assertTrue(Files.isRegularFile(jarPath), () -> "Missing adapter jar: " + jarPath);
        try (PackagedAdapterClassLoader classLoader = new PackagedAdapterClassLoader(jarPath)) {
            List<CaptureMediaCvBackend> backends = ServiceLoader
                    .load(CaptureMediaCvBackend.class, classLoader)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();

            assertAll(
                    () -> assertEquals(1, backends.size()),
                    () -> assertEquals("boofcv", backends.get(0).identity().backendId()),
                    () -> assertEquals(
                            "com.alx4j:jab4j-reader-cv-boofcv",
                            backends.get(0).identity().implementationArtifact().orElseThrow()
                    ),
                    () -> assertTrue(backends.get(0).identity().featureFlags().contains("optional-shaded-adapter"))
            );
        }
    }

    private Path adapterJarPath() {
        return Path.of(System.getProperty("adapter.jar.path"));
    }

    private boolean isRawDependencyPackage(String entryName) {
        return RAW_PACKAGE_PREFIXES.stream().anyMatch(entryName::startsWith);
    }

    private boolean isPackagedReaderClass(String entryName) {
        return entryName.endsWith(".class")
                && READER_PACKAGE_PREFIXES.stream().anyMatch(entryName::startsWith)
                && !entryName.startsWith(OPTIONAL_BACKEND_PACKAGE);
    }

    private static final class PackagedAdapterClassLoader extends URLClassLoader {

        private PackagedAdapterClassLoader(Path jarPath) throws IOException {
            super(new URL[] { jarPath.toUri().toURL() }, BoofCvShadedJarIT.class.getClassLoader());
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SERVICE_RESOURCE.equals(name)) {
                return findResources(name);
            }
            return super.getResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (isPackagedAdapterClass(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findPackagedClassOrDelegate(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> findPackagedClassOrDelegate(String name) throws ClassNotFoundException {
            try {
                return findClass(name);
            } catch (ClassNotFoundException exception) {
                return super.loadClass(name, false);
            }
        }

        private static boolean isPackagedAdapterClass(String className) {
            return className.startsWith(OPTIONAL_BACKEND_CLASS_PREFIX)
                    || className.startsWith(SHADED_CLASS_PREFIX);
        }
    }
}
