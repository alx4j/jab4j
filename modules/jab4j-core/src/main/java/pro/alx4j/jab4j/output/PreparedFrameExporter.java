package pro.alx4j.jab4j.output;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.alx4j.jab4j.api.model.SessionId;
import pro.alx4j.jab4j.render.frame.RenderedFrame;

/**
 * Exports prepared full-frame rasters without altering their playback order or content.
 */
public final class PreparedFrameExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreparedFrameExporter.class);

    /**
     * Writes deterministic export artifacts for one prepared frame list.
     *
     * @param mode export mode
     * @param sessionId stable session id
     * @param finalSessionDigest deterministic final session digest
     * @param preparedFrames ordered prepared frames
     * @param exportRootDirectory export root directory
     * @return deterministic export artifact metadata
     */
    public ExportArtifacts export(
            ExportMode mode,
            SessionId sessionId,
            String finalSessionDigest,
            List<RenderedFrame> preparedFrames,
            Path exportRootDirectory
    ) {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new ExportException("finalSessionDigest must not be blank");
        }
        List<RenderedFrame> frames = List.copyOf(Objects.requireNonNull(preparedFrames, "preparedFrames must not be null"));
        if (frames.isEmpty()) {
            throw new ExportException("preparedFrames must not be empty");
        }
        Path exportDirectory = Objects.requireNonNull(exportRootDirectory, "exportRootDirectory must not be null")
                .toAbsolutePath()
                .normalize()
                .resolve(sessionId.toString())
                .resolve(mode.wireValue());

        Map<String, Path> exportedFiles = new LinkedHashMap<>();
        LOGGER.info(
                "Exporting prepared frames sessionId={} mode={} frameCount={} exportDirectory={}",
                sessionId,
                mode.wireValue(),
                frames.size(),
                exportDirectory
        );
        try {
            Files.createDirectories(exportDirectory);
            exportedFiles.put(
                    "frameSequence",
                    writeStringArtifact(exportDirectory.resolve("frame-sequence.txt"), serializeSequence(sessionId, finalSessionDigest, frames))
            );
            if (mode == ExportMode.IMAGE_SEQUENCE) {
                for (int index = 0; index < frames.size(); index++) {
                    RenderedFrame frame = frames.get(index);
                    exportedFiles.put(
                            String.format(Locale.ROOT, "image-%04d", index),
                            writeImage(exportDirectory.resolve(fileName(frame)), frame)
                    );
                }
            }
            LOGGER.info(
                    "Prepared frame export completed sessionId={} mode={} frameCount={} exportedArtifacts={} exportDirectory={}",
                    sessionId,
                    mode.wireValue(),
                    frames.size(),
                    exportedFiles.size(),
                    exportDirectory
            );
            return new ExportArtifacts(exportDirectory, exportedFiles);
        } catch (IOException exception) {
            LOGGER.error(
                    "Prepared frame export failed while creating directories sessionId={} mode={} exportDirectory={}",
                    sessionId,
                    mode.wireValue(),
                    exportDirectory,
                    exception
            );
            throw new ExportException("Failed to create export directory " + exportDirectory, exception);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Prepared frame export failed sessionId={} mode={} exportDirectory={}",
                    sessionId,
                    mode.wireValue(),
                    exportDirectory,
                    exception
            );
            throw exception;
        }
    }

    private Path writeStringArtifact(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path;
        } catch (IOException exception) {
            throw new ExportException("Failed to write export artifact " + path, exception);
        }
    }

    private Path writeImage(Path path, RenderedFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < frame.heightPixels(); row++) {
            for (int col = 0; col < frame.widthPixels(); col++) {
                image.setRGB(col, row, frame.argbPixels().get((row * frame.widthPixels()) + col));
            }
        }
        try {
            ImageIO.write(image, "png", path.toFile());
            return path;
        } catch (IOException exception) {
            throw new ExportException("Failed to write export frame " + path, exception);
        }
    }

    private String serializeSequence(SessionId sessionId, String finalSessionDigest, List<RenderedFrame> frames) {
        StringBuilder builder = new StringBuilder();
        builder.append("sessionId=").append(sessionId).append('\n');
        builder.append("finalSessionDigest=").append(finalSessionDigest).append('\n');
        builder.append("frameCount=").append(frames.size()).append('\n');
        for (RenderedFrame frame : frames) {
            builder.append("frame=").append(frame.frameIndex())
                    .append('\t').append(frame.frameType())
                    .append('\t').append(frame.diagnostics().getOrDefault("pixelSha256", "-"))
                    .append('\n');
        }
        return builder.toString();
    }

    private String fileName(RenderedFrame frame) {
        return String.format(
                Locale.ROOT,
                "frame-%04d-%s.png",
                frame.frameIndex(),
                frame.frameType().name().toLowerCase(Locale.ROOT)
        );
    }
}
