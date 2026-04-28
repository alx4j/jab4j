package pro.alx4j.jab4j.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.alx4j.jab4j.api.model.FileRecord;
import pro.alx4j.jab4j.api.model.FileType;
import pro.alx4j.jab4j.api.model.Manifest;
import pro.alx4j.jab4j.support.HashingUtils;
import pro.alx4j.jab4j.support.ImmutableCollections;

/**
 * Recursively scans declared roots and builds a deterministic manifest.
 */
public final class DeterministicPackager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeterministicPackager.class);

    private static final Comparator<PackagedEntry> ENTRY_ORDER = Comparator
            .comparing((PackagedEntry entry) -> entry.fileRecord().rootAlias())
            .thenComparing(entry -> entry.fileRecord().relativePath())
            .thenComparingInt(entry -> entry.fileRecord().fileType() == FileType.DIRECTORY ? 0 : 1)
            .thenComparingLong(entry -> entry.fileRecord().sizeBytes())
            .thenComparing(entry -> entry.fileRecord().sha256() == null ? "" : entry.fileRecord().sha256());

    /**
     * Builds a deterministic manifest from declared input roots.
     *
     * @param declaredRoots ordered declared roots
     * @return deterministic manifest snapshot
     */
    public Manifest buildManifest(List<DeclaredInputRoot> declaredRoots) {
        return buildPackagingResult(declaredRoots).manifest();
    }

    /**
     * Builds a deterministic packaging result that includes both manifest metadata and the ordered source paths
     * needed by later chunking work.
     *
     * @param declaredRoots ordered declared roots
     * @return deterministic packaging result
     */
    public PackagingResult buildPackagingResult(List<DeclaredInputRoot> declaredRoots) {
        Objects.requireNonNull(declaredRoots, "declaredRoots must not be null");
        if (declaredRoots.isEmpty()) {
            throw new PackagingException("At least one input root must be declared");
        }

        LOGGER.debug(
                "Building deterministic packaging result declaredRootCount={} declaredRoots={}",
                declaredRoots.size(),
                describeDeclaredRoots(declaredRoots)
        );
        try {
            List<ResolvedRoot> resolvedRoots = resolveRoots(declaredRoots);
            List<PackagedEntry> entries = new ArrayList<>();
            for (ResolvedRoot resolvedRoot : resolvedRoots) {
                entries.addAll(scanRoot(resolvedRoot));
            }
            entries.sort(ENTRY_ORDER);

            List<String> rootAliases = resolvedRoots.stream()
                    .map(ResolvedRoot::alias)
                    .toList();
            List<FileRecord> records = entries.stream()
                    .map(PackagedEntry::fileRecord)
                    .toList();
            long totalSizeBytes = records.stream()
                    .filter(record -> record.fileType() == FileType.REGULAR_FILE)
                    .mapToLong(FileRecord::sizeBytes)
                    .sum();

            Manifest manifest = new Manifest(
                    ImmutableCollections.listCopyOf(records),
                    ImmutableCollections.listCopyOf(rootAliases),
                    totalSizeBytes,
                    fingerprint(rootAliases, records)
            );
            LOGGER.info(
                    "Built deterministic packaging result: roots={}, entries={}, files={}, totalBytes={}, manifestFingerprint={}",
                    resolvedRoots.size(),
                    entries.size(),
                    manifest.files().size(),
                    manifest.totalSizeBytes(),
                    manifest.manifestFingerprint()
            );
            return new PackagingResult(manifest, entries);
        } catch (PackagingException exception) {
            LOGGER.warn(
                    "Deterministic packaging failed declaredRootCount={} declaredRoots={} message={}",
                    declaredRoots.size(),
                    describeDeclaredRoots(declaredRoots),
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Deterministic packaging failed unexpectedly declaredRootCount={} declaredRoots={}",
                    declaredRoots.size(),
                    describeDeclaredRoots(declaredRoots),
                    exception
            );
            throw exception;
        }
    }

    private List<ResolvedRoot> resolveRoots(List<DeclaredInputRoot> declaredRoots) {
        List<ResolvedRoot> resolved = new ArrayList<>(declaredRoots.size());
        LinkedHashSet<String> aliases = new LinkedHashSet<>();

        for (int index = 0; index < declaredRoots.size(); index++) {
            DeclaredInputRoot declaredRoot = declaredRoots.get(index);
            Path rootPath = declaredRoot.path().toAbsolutePath().normalize();
            if (!Files.isDirectory(rootPath)) {
                throw new PackagingException("Declared input root is not a readable directory: " + rootPath);
            }

            String alias = declaredRoot.alias() != null ? validateAlias(declaredRoot.alias()) : fallbackAlias(index + 1);
            if (!aliases.add(alias)) {
                throw new PackagingException("Ambiguous root alias collision: " + alias);
            }
            resolved.add(new ResolvedRoot(rootPath, alias));
        }

        return resolved;
    }

    private List<PackagedEntry> scanRoot(ResolvedRoot root) {
        List<PackagedEntry> records = new ArrayList<>();
        LOGGER.debug("Scanning packaged input root alias={} path={}", root.alias(), root.path());
        try {
            Files.walkFileTree(root.path(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root.path())) {
                        records.add(new PackagedEntry(directoryRecord(root.alias(), dir, root.path()), dir));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    records.add(fileRecord(root.alias(), file, root.path(), attrs));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    throw new PackagingException("Failed to read filesystem entry: " + file, exception);
                }
            });
        } catch (IOException exception) {
            throw new PackagingException("Failed to scan input root: " + root.path(), exception);
        }
        LOGGER.debug("Scanned packaged input root alias={} path={} entries={}", root.alias(), root.path(), records.size());
        return records;
    }

    private String describeDeclaredRoots(List<DeclaredInputRoot> declaredRoots) {
        return declaredRoots.stream()
                .map(root -> (root.alias() == null || root.alias().isBlank() ? "<auto>" : root.alias())
                        + "->"
                        + root.path().toAbsolutePath().normalize())
                .toList()
                .toString();
    }

    private FileRecord directoryRecord(String alias, Path dir, Path rootPath) {
        String relativePath = LogicalPathNormalizer.normalize(rootPath.relativize(dir));
        return new FileRecord(alias, relativePath, FileType.DIRECTORY, 0L, null, List.of());
    }

    private PackagedEntry fileRecord(String alias, Path file, Path rootPath, BasicFileAttributes attrs) {
        if (Files.isSymbolicLink(file) || attrs.isSymbolicLink()) {
            throw new PackagingException("Unsupported filesystem entry type (symbolic link): " + file);
        }
        if (attrs.isDirectory()) {
            return new PackagedEntry(directoryRecord(alias, file, rootPath), file);
        }
        if (!attrs.isRegularFile()) {
            throw new PackagingException("Unsupported filesystem entry type: " + file);
        }

        String relativePath = LogicalPathNormalizer.normalize(rootPath.relativize(file));
        return new PackagedEntry(
                new FileRecord(
                        alias,
                        relativePath,
                        FileType.REGULAR_FILE,
                        attrs.size(),
                        HashingUtils.sha256Hex(file),
                        List.of()
                ),
                file
        );
    }

    private String validateAlias(String alias) {
        String trimmed = alias.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new PackagingException("Illegal root alias: " + alias);
        }
        return trimmed;
    }

    private String fallbackAlias(int position) {
        return String.format(Locale.ROOT, "root-%03d", position);
    }

    private String fingerprint(List<String> rootAliases, List<FileRecord> records) {
        StringBuilder builder = new StringBuilder();
        for (String rootAlias : rootAliases) {
            builder.append("root\t").append(rootAlias).append('\n');
        }
        for (FileRecord record : records) {
            builder.append(record.rootAlias()).append('\t')
                    .append(record.relativePath()).append('\t')
                    .append(record.fileType().name()).append('\t')
                    .append(record.sizeBytes()).append('\t')
                    .append(record.sha256() == null ? "-" : record.sha256())
                    .append('\n');
        }
        return HashingUtils.sha256Hex(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record ResolvedRoot(Path path, String alias) {
    }
}
