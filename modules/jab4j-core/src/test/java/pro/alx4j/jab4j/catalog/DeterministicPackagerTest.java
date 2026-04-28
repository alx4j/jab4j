package pro.alx4j.jab4j.catalog;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.alx4j.jab4j.api.model.FileRecord;
import pro.alx4j.jab4j.api.model.FileType;
import pro.alx4j.jab4j.api.model.Manifest;
import pro.alx4j.jab4j.support.HashingUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Deterministic packaging")
class DeterministicPackagerTest {

    private final DeterministicPackager packager = new DeterministicPackager();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Packaging builds deterministic manifests and preserves mandatory metadata")
    void buildsDeterministicManifestAndPreservesMandatoryMetadata() throws IOException {
        Path mediaRoot = Files.createDirectories(tempDir.resolve("media-root"));
        Files.createDirectories(mediaRoot.resolve("nested/empty-dir"));
        Files.writeString(mediaRoot.resolve("nested/one-byte.bin"), "X");
        Files.writeString(mediaRoot.resolve("z-empty.txt"), "");

        Path fallbackRoot = Files.createDirectories(tempDir.resolve("fallback-root"));
        Files.writeString(fallbackRoot.resolve("alpha.txt"), "alpha");

        List<DeclaredInputRoot> roots = List.of(
                new DeclaredInputRoot(mediaRoot, "media"),
                new DeclaredInputRoot(fallbackRoot, null)
        );

        PackagingResult firstResult = packager.buildPackagingResult(roots);
        PackagingResult secondResult = packager.buildPackagingResult(roots);
        Manifest first = firstResult.manifest();
        Manifest second = secondResult.manifest();

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(firstResult.entries(), secondResult.entries()),
                () -> assertEquals(List.of("media", "root-002"), first.rootAliases()),
                () -> assertEquals(6L, first.totalSizeBytes()),
                () -> assertNotEquals("", first.manifestFingerprint()),
                () -> assertEquals(
                        List.of(
                                "media|nested|DIRECTORY",
                                "media|nested/empty-dir|DIRECTORY",
                                "media|nested/one-byte.bin|REGULAR_FILE",
                                "media|z-empty.txt|REGULAR_FILE",
                                "root-002|alpha.txt|REGULAR_FILE"
                        ),
                        describeManifestEntries(first)
                )
        );

        FileRecord emptyDirectory = first.files().get(1);
        assertAll(
                () -> assertEquals(FileType.DIRECTORY, emptyDirectory.fileType()),
                () -> assertEquals(0L, emptyDirectory.sizeBytes()),
                () -> assertNull(emptyDirectory.sha256())
        );

        FileRecord oneByteFile = first.files().get(2);
        assertAll(
                () -> assertEquals(1L, oneByteFile.sizeBytes()),
                () -> assertEquals(HashingUtils.sha256Hex("X".getBytes()), oneByteFile.sha256())
        );

        FileRecord emptyFile = first.files().get(3);
        assertAll(
                () -> assertEquals(0L, emptyFile.sizeBytes()),
                () -> assertEquals(HashingUtils.sha256Hex(new byte[0]), emptyFile.sha256())
        );

        PackagedEntry packagedAlpha = firstResult.entries().stream()
                .filter(entry -> entry.fileRecord().relativePath().equals("nested/one-byte.bin"))
                .findFirst()
                .orElseThrow();
        assertEquals(mediaRoot.resolve("nested/one-byte.bin").toAbsolutePath().normalize(), packagedAlpha.sourcePath());
    }

    @Test
    @DisplayName("Resolved root aliases must remain unique")
    void rejectsDuplicateResolvedRootAliases() throws IOException {
        Path firstRoot = Files.createDirectories(tempDir.resolve("root-a"));
        Path secondRoot = Files.createDirectories(tempDir.resolve("root-b"));

        PackagingException exception = assertThrows(
                PackagingException.class,
                () -> packager.buildManifest(List.of(
                        new DeclaredInputRoot(firstRoot, "dup"),
                        new DeclaredInputRoot(secondRoot, "dup")
                ))
        );

        assertEquals("Ambiguous root alias collision: dup", exception.getMessage());
    }

    @Test
    @DisplayName("Symbolic links are rejected explicitly when supported")
    void rejectsSymbolicLinksExplicitlyWhenSupported() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path target = Files.writeString(root.resolve("target.txt"), "payload");
        Path symlink = root.resolve("link.txt");

        try {
            Files.createSymbolicLink(symlink, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("Symbolic links are not available in this test environment");
            return;
        }

        PackagingException packagingException = assertThrows(
                PackagingException.class,
                () -> packager.buildManifest(List.of(new DeclaredInputRoot(root, null)))
        );

        assertEquals("Unsupported filesystem entry type (symbolic link): " + symlink, packagingException.getMessage());
    }

    @Test
    @DisplayName("Non-directory roots are rejected")
    void rejectsNonDirectoryRoots() throws IOException {
        Path file = Files.writeString(tempDir.resolve("not-a-dir.txt"), "payload");

        PackagingException exception = assertThrows(
                PackagingException.class,
                () -> packager.buildManifest(List.of(new DeclaredInputRoot(file, null)))
        );

        assertEquals("Declared input root is not a readable directory: " + file.toAbsolutePath().normalize(), exception.getMessage());
    }

    @Test
    @DisplayName("Packaging results keep the manifest ready for later chunking without a rescan")
    void packagingResultKeepsManifestReadyForLaterChunkingWithoutRescan() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("transport-ready"));
        Path payload = Files.writeString(root.resolve("payload.bin"), "transport");

        PackagingResult result = packager.buildPackagingResult(List.of(new DeclaredInputRoot(root, null)));

        assertEquals(1, result.entries().size());
        assertEquals(result.entries().get(0).fileRecord(), result.manifest().files().get(0));
        assertEquals(payload.toAbsolutePath().normalize(), result.entries().get(0).sourcePath());
    }

    private static List<String> describeManifestEntries(Manifest manifest) {
        return manifest.files().stream()
                .map(record -> record.rootAlias() + "|" + record.relativePath() + "|" + record.fileType().name())
                .toList();
    }
}
