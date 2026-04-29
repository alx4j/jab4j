package com.alx4j.jab4j.reader.restore;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.api.model.FileChunk;
import com.alx4j.jab4j.api.model.FileRecord;
import com.alx4j.jab4j.api.model.FileType;

/**
 * Writes restore output through a staging directory and publishes only validated roots.
 */
final class StagedRestoreWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StagedRestoreWriter.class);

    /**
     * Writes, validates, and publishes one restore plan.
     *
     * @param plan validated restore plan
     * @return successful restore result
     */
    ReaderRestoreResult restore(RestorePlan plan) {
        preflightFinalOutput(plan);

        Path stagingDirectory = null;
        List<Path> publishedRoots = new ArrayList<>();
        boolean outputDirectoryCreated = false;
        try {
            stagingDirectory = createStagingDirectory(plan.request().outputDirectory());
            writeStagedOutput(plan, stagingDirectory);
            validateStagedOutput(plan, stagingDirectory);
            outputDirectoryCreated = ensureOutputDirectory(plan.request().outputDirectory());
            publish(plan, stagingDirectory, publishedRoots);
            cleanupPath(stagingDirectory);
            return ReaderRestoreResult.restored(
                    plan.request().sessionId(),
                    plan.request().outputDirectory(),
                    plan.restoredFileCount(),
                    plan.restoredDirectoryCount(),
                    plan.totalRestoredBytes()
            );
        } catch (ReaderRestoreException exception) {
            cleanupAfterFailure(stagingDirectory, publishedRoots, plan.request().outputDirectory(), outputDirectoryCreated);
            throw exception;
        } catch (IOException | UncheckedIOException exception) {
            cleanupAfterFailure(stagingDirectory, publishedRoots, plan.request().outputDirectory(), outputDirectoryCreated);
            throw new ReaderRestoreException(
                    ReaderRestoreStatus.IO_FAILURE,
                    "Failed to restore transfer package: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void preflightFinalOutput(RestorePlan plan) {
        Path outputDirectory = plan.request().outputDirectory();
        if (Files.exists(outputDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(outputDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReaderRestoreException(
                    ReaderRestoreStatus.OUTPUT_CONFLICT,
                    "Restore output path already exists and is not a directory: " + outputDirectory
            );
        }

        Path parent = outputDirectory.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReaderRestoreException(
                    ReaderRestoreStatus.IO_FAILURE,
                    "Restore output parent directory does not exist: " + outputDirectory
            );
        }

        for (String rootAlias : plan.rootAliases()) {
            Path finalRoot = outputDirectory.resolve(rootAlias).normalize();
            if (Files.exists(finalRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new ReaderRestoreException(
                        ReaderRestoreStatus.OUTPUT_CONFLICT,
                        "Restore output path already exists: " + finalRoot
                );
            }
        }
    }

    private Path createStagingDirectory(Path outputDirectory) throws IOException {
        Path parent = outputDirectory.getParent();
        String fileName = outputDirectory.getFileName() == null ? "output" : outputDirectory.getFileName().toString();
        String prefix = "." + fileName.replaceAll("[^A-Za-z0-9._-]", "_") + "-jab4j-restore-";
        if (prefix.length() < 3) {
            prefix = ".jab4j-restore-";
        }
        return Files.createTempDirectory(parent, prefix);
    }

    private void writeStagedOutput(RestorePlan plan, Path stagingDirectory) throws IOException {
        for (String rootAlias : plan.rootAliases()) {
            Files.createDirectory(stagingDirectory.resolve(rootAlias));
        }
        for (RestoreEntry entry : plan.entries()) {
            Path stagedPath = stagedPath(plan, stagingDirectory, entry.finalPath());
            FileRecord fileRecord = entry.fileRecord();
            if (fileRecord.fileType() == FileType.DIRECTORY) {
                Files.createDirectories(stagedPath);
            } else {
                Path parent = stagedPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (OutputStream outputStream = Files.newOutputStream(
                        stagedPath,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                )) {
                    for (FileChunk chunk : fileRecord.chunks()) {
                        outputStream.write(plan.chunks().get(ChunkKey.from(chunk)).payload());
                    }
                }
            }
        }
    }

    private void validateStagedOutput(RestorePlan plan, Path stagingDirectory) throws IOException {
        for (RestoreEntry entry : plan.entries()) {
            Path stagedPath = stagedPath(plan, stagingDirectory, entry.finalPath());
            FileRecord fileRecord = entry.fileRecord();
            if (fileRecord.fileType() == FileType.DIRECTORY) {
                if (!Files.isDirectory(stagedPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw new ReaderRestoreException(
                            ReaderRestoreStatus.INCONSISTENT_CONTENT,
                            "Staged manifest directory was not created: " + fileRecord.relativePath()
                    );
                }
                continue;
            }

            long sizeBytes = Files.size(stagedPath);
            if (sizeBytes != fileRecord.sizeBytes()) {
                throw new ReaderRestoreException(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        "Staged file size does not match manifest for " + fileRecord.relativePath()
                );
            }
            String sha256 = RestoreChecksums.sha256Hex(stagedPath);
            if (!sha256.equals(fileRecord.sha256())) {
                throw new ReaderRestoreException(
                        ReaderRestoreStatus.INCONSISTENT_CONTENT,
                        "Staged file SHA-256 does not match manifest for " + fileRecord.relativePath()
                );
            }
        }
    }

    private boolean ensureOutputDirectory(Path outputDirectory) throws IOException {
        if (Files.exists(outputDirectory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(outputDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new ReaderRestoreException(
                        ReaderRestoreStatus.OUTPUT_CONFLICT,
                        "Restore output path already exists and is not a directory: " + outputDirectory
                );
            }
            return false;
        }
        Files.createDirectory(outputDirectory);
        return true;
    }

    private void publish(RestorePlan plan, Path stagingDirectory, List<Path> publishedRoots) throws IOException {
        for (String rootAlias : plan.rootAliases()) {
            Path stagedRoot = stagingDirectory.resolve(rootAlias);
            Path finalRoot = plan.request().outputDirectory().resolve(rootAlias).normalize();
            try {
                Files.move(stagedRoot, finalRoot);
                publishedRoots.add(finalRoot);
            } catch (FileAlreadyExistsException exception) {
                throw new ReaderRestoreException(
                        ReaderRestoreStatus.OUTPUT_CONFLICT,
                        "Restore output path already exists: " + finalRoot,
                        exception
                );
            }
        }
    }

    private Path stagedPath(RestorePlan plan, Path stagingDirectory, Path finalPath) {
        Path relativeToOutput = plan.request().outputDirectory().relativize(finalPath);
        return stagingDirectory.resolve(relativeToOutput).normalize();
    }

    private void cleanupAfterFailure(
            Path stagingDirectory,
            List<Path> publishedRoots,
            Path outputDirectory,
            boolean outputDirectoryCreated
    ) {
        for (int index = publishedRoots.size() - 1; index >= 0; index--) {
            cleanupPath(publishedRoots.get(index));
        }
        cleanupPath(stagingDirectory);
        if (outputDirectoryCreated) {
            try {
                Files.deleteIfExists(outputDirectory);
            } catch (IOException exception) {
                LOGGER.warn("Failed to clean up restore output directory after failure outputDirectory={}", outputDirectory, exception);
            }
        }
    }

    private void cleanupPath(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path current : paths) {
                Files.deleteIfExists(current);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to clean up restore path path={}", path, exception);
        }
    }
}
