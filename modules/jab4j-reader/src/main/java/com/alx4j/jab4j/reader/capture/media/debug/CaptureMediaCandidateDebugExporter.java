package com.alx4j.jab4j.reader.capture.media.debug;

import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes normalized capture candidates and sidecar metadata for visual diagnostics.
 */
public final class CaptureMediaCandidateDebugExporter {

    /**
     * Exports one normalized candidate as a PNG plus a metadata text file.
     *
     * @param frame the normalized candidate to export
     * @param outputDirectory destination directory, created when missing
     * @return paths written for the candidate
     * @throws IOException when the destination cannot be written
     */
    public CandidateDebugExport export(NormalizedCaptureFrame frame, Path outputDirectory) throws IOException {
        Objects.requireNonNull(frame, "frame");
        return export(List.of(frame), outputDirectory).get(0);
    }

    /**
     * Exports normalized candidates as PNG images plus metadata text files.
     *
     * @param frames normalized candidates to export in input order
     * @param outputDirectory destination directory, created when missing
     * @return paths written for each candidate, in input order
     * @throws IOException when the destination cannot be written
     */
    public List<CandidateDebugExport> export(List<NormalizedCaptureFrame> frames, Path outputDirectory)
            throws IOException {
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Files.createDirectories(outputDirectory);

        List<CandidateDebugExport> exports = new ArrayList<>(frames.size());
        for (int index = 0; index < frames.size(); index++) {
            NormalizedCaptureFrame frame = Objects.requireNonNull(frames.get(index), "frames[" + index + "]");
            exports.add(exportCandidate(frame, outputDirectory, index));
        }
        return List.copyOf(exports);
    }

    private CandidateDebugExport exportCandidate(NormalizedCaptureFrame frame, Path outputDirectory, int index)
            throws IOException {
        String baseName = "candidate-%04d".formatted(index);
        Path imagePath = outputDirectory.resolve(baseName + ".png");
        Path metadataPath = outputDirectory.resolve(baseName + ".txt");

        BufferedImage image = new BufferedImage(
                frame.normalizedWidthPixels(),
                frame.normalizedHeightPixels(),
                BufferedImage.TYPE_INT_ARGB
        );
        image.setRGB(
                0,
                0,
                frame.normalizedWidthPixels(),
                frame.normalizedHeightPixels(),
                frame.copyArgbPixels(),
                0,
                frame.normalizedWidthPixels()
        );
        ImageIO.write(image, "PNG", imagePath.toFile());

        Files.writeString(metadataPath, metadata(frame), StandardCharsets.UTF_8);
        return new CandidateDebugExport(imagePath, metadataPath);
    }

    private String metadata(NormalizedCaptureFrame frame) {
        FrameCorners corners = frame.frameCorners();
        return String.join(System.lineSeparator(),
                "sourceId=" + frame.sourceId(),
                "sourceKind=" + frame.sourceKind(),
                "callerOrder=" + frame.callerOrder(),
                "formatName=" + frame.formatName(),
                "originalWidthPixels=" + frame.originalWidthPixels(),
                "originalHeightPixels=" + frame.originalHeightPixels(),
                "normalizedWidthPixels=" + frame.normalizedWidthPixels(),
                "normalizedHeightPixels=" + frame.normalizedHeightPixels(),
                "pixelSha256=" + frame.pixelSha256(),
                "layoutProfileId=" + frame.layoutProfileId(),
                "frameCorners.topLeftX=" + corners.topLeftX(),
                "frameCorners.topLeftY=" + corners.topLeftY(),
                "frameCorners.topRightX=" + corners.topRightX(),
                "frameCorners.topRightY=" + corners.topRightY(),
                "frameCorners.bottomRightX=" + corners.bottomRightX(),
                "frameCorners.bottomRightY=" + corners.bottomRightY(),
                "frameCorners.bottomLeftX=" + corners.bottomLeftX(),
                "frameCorners.bottomLeftY=" + corners.bottomLeftY(),
                "quality.frameCoverageRatio=" + frame.qualityMetrics().frameCoverageRatio(),
                "quality.skewScore=" + frame.qualityMetrics().skewScore(),
                "");
    }

    /**
     * Paths produced for one exported normalized candidate.
     */
    public record CandidateDebugExport(Path imagePath, Path metadataPath) {
        public CandidateDebugExport {
            Objects.requireNonNull(imagePath, "imagePath");
            Objects.requireNonNull(metadataPath, "metadataPath");
        }
    }
}
