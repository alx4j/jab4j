package com.alx4j.jab4j.reader.capture.media.debug;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.PaletteConfidenceSummary;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.SlotInspection;

/**
 * Writes normalized capture candidates and sidecar metadata for visual diagnostics.
 */
public final class CaptureMediaCandidateDebugExporter {

    private final CaptureMediaTilePayloadSampler tilePayloadSampler;

    /**
     * Creates a debug exporter with the default media sampler inspection logic.
     */
    public CaptureMediaCandidateDebugExporter() {
        this(new CaptureMediaTilePayloadSampler());
    }

    /**
     * Creates a debug exporter with an explicit sampler for focused tests.
     *
     * @param tilePayloadSampler sampler used to inspect normalized candidates
     */
    public CaptureMediaCandidateDebugExporter(CaptureMediaTilePayloadSampler tilePayloadSampler) {
        this.tilePayloadSampler = Objects.requireNonNull(tilePayloadSampler, "tilePayloadSampler");
    }

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

        Files.writeString(metadataPath, metadata(frame, tilePayloadSampler.inspect(frame)), StandardCharsets.UTF_8);
        return new CandidateDebugExport(imagePath, metadataPath);
    }

    private String metadata(NormalizedCaptureFrame frame, FrameInspection inspection) {
        FrameCorners corners = frame.frameCorners();
        List<String> lines = new ArrayList<>();
        lines.add("sourceId=" + frame.sourceId());
        lines.add("sourceKind=" + frame.sourceKind());
        lines.add("callerOrder=" + frame.callerOrder());
        lines.add("formatName=" + frame.formatName());
        lines.add("originalWidthPixels=" + frame.originalWidthPixels());
        lines.add("originalHeightPixels=" + frame.originalHeightPixels());
        lines.add("normalizedWidthPixels=" + frame.normalizedWidthPixels());
        lines.add("normalizedHeightPixels=" + frame.normalizedHeightPixels());
        lines.add("pixelSha256=" + frame.pixelSha256());
        lines.add("layoutProfileId=" + frame.layoutProfileId());
        lines.add("frameCorners.topLeftX=" + corners.topLeftX());
        lines.add("frameCorners.topLeftY=" + corners.topLeftY());
        lines.add("frameCorners.topRightX=" + corners.topRightX());
        lines.add("frameCorners.topRightY=" + corners.topRightY());
        lines.add("frameCorners.bottomRightX=" + corners.bottomRightX());
        lines.add("frameCorners.bottomRightY=" + corners.bottomRightY());
        lines.add("frameCorners.bottomLeftX=" + corners.bottomLeftX());
        lines.add("frameCorners.bottomLeftY=" + corners.bottomLeftY());
        lines.add("quality.frameCoverageRatio=" + frame.qualityMetrics().frameCoverageRatio());
        lines.add("quality.skewScore=" + frame.qualityMetrics().skewScore());
        lines.add("quality.blurScore=" + frame.qualityMetrics().blurScore());
        lines.add("quality.glareScore=" + frame.qualityMetrics().glareScore());
        lines.add("quality.exposureScore=" + frame.qualityMetrics().exposureScore());
        lines.add("quality.colorDistanceScore=" + frame.qualityMetrics().colorDistanceScore());
        lines.add("sampler.candidateAttemptCount=" + inspection.candidateAttemptCount());
        lines.add("sampler.noFinderAttemptCount=" + inspection.noFinderAttemptCount());
        lines.add("sampler.paletteRejectedAttemptCount=" + inspection.paletteRejectedAttemptCount());
        lines.add("sampler.decodedPayloadCount=" + inspection.decodedPayloadCount());
        lines.add("sampler.slotCount=" + inspection.slots().size());
        addSlotInspection(lines, inspection);
        lines.add("");
        return String.join(System.lineSeparator(), lines);
    }

    private void addSlotInspection(List<String> lines, FrameInspection inspection) {
        for (SlotInspection slot : inspection.slots()) {
            String slotPrefix = "sampler.slot." + slot.tileIndex();
            lines.add(slotPrefix + ".borderStatus=" + slot.borderStatus());
            lines.add(slotPrefix + ".interiorContent=" + slot.interiorContent());
            lines.add(slotPrefix + ".candidateCount=" + slot.candidates().size());
            for (CandidateInspection candidate : slot.candidates()) {
                String candidatePrefix = slotPrefix + ".sideVersion." + candidate.sideVersion();
                lines.add(candidatePrefix + ".dimension=" + candidate.dimension());
                lines.add(candidatePrefix + ".status=" + candidate.status());
                lines.add(candidatePrefix + ".decodeStatus=" + candidate.decodeStatus());
                candidate.paletteConfidence()
                        .ifPresent(confidence -> addPaletteConfidence(lines, candidatePrefix, confidence));
            }
        }
    }

    private void addPaletteConfidence(
            List<String> lines,
            String candidatePrefix,
            PaletteConfidenceSummary confidence
    ) {
        lines.add(candidatePrefix + ".palette.sampledModuleCount=" + confidence.sampledModuleCount());
        lines.add(candidatePrefix + ".palette.shiftedSampleCount=" + confidence.shiftedSampleCount());
        lines.add(candidatePrefix + ".palette.lowConfidenceSampleCount=" + confidence.lowConfidenceSampleCount());
        lines.add(candidatePrefix + ".palette.rejectedSampleCount=" + confidence.rejectedSampleCount());
        lines.add(candidatePrefix + ".palette.minimumConfidence=" + confidence.minimumConfidence());
        lines.add(candidatePrefix + ".palette.averageConfidence=" + confidence.averageConfidence());
        lines.add(candidatePrefix + ".palette.maximumRgbDistance=" + confidence.maximumRgbDistance());
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
