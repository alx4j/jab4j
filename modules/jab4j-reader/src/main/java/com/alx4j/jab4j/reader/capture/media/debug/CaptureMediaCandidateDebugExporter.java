package com.alx4j.jab4j.reader.capture.media.debug;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.cv.CvTileSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.BorderInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.DecodeInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.PaletteConfidenceSummary;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.SlotInspection;

/**
 * Writes normalized capture candidates, module-grid overlays, and sidecar metadata for visual diagnostics.
 */
public final class CaptureMediaCandidateDebugExporter {

    private final CaptureMediaTilePayloadSampler tilePayloadSampler;
    private final CaptureMediaModuleGridOverlayRenderer overlayRenderer;
    private final String cvBackendId;
    private final String cvBackendVersion;

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
        this(tilePayloadSampler, "legacy", "");
    }

    /**
     * Creates a debug exporter with explicit sampler and CV backend metadata.
     *
     * @param tilePayloadSampler sampler used to inspect normalized candidates
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     */
    public CaptureMediaCandidateDebugExporter(
            CaptureMediaTilePayloadSampler tilePayloadSampler,
            String cvBackendId,
            String cvBackendVersion
    ) {
        this(
                tilePayloadSampler,
                cvBackendId,
                cvBackendVersion,
                new CaptureMediaModuleGridOverlayRenderer()
        );
    }

    CaptureMediaCandidateDebugExporter(
            CaptureMediaTilePayloadSampler tilePayloadSampler,
            String cvBackendId,
            String cvBackendVersion,
            CaptureMediaModuleGridOverlayRenderer overlayRenderer
    ) {
        this.tilePayloadSampler = Objects.requireNonNull(tilePayloadSampler, "tilePayloadSampler");
        this.overlayRenderer = Objects.requireNonNull(overlayRenderer, "overlayRenderer must not be null");
        requireBackendId(cvBackendId);
        this.cvBackendId = cvBackendId;
        this.cvBackendVersion = Objects.requireNonNull(cvBackendVersion, "cvBackendVersion must not be null");
    }

    /**
     * Exports one normalized candidate as a PNG, module-grid overlay PNG, and metadata text file.
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
     * Exports one normalized candidate with explicit CV backend metadata.
     *
     * @param frame the normalized candidate to export
     * @param outputDirectory destination directory, created when missing
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     * @return paths written for the candidate
     * @throws IOException when the destination cannot be written
     */
    public CandidateDebugExport export(
            NormalizedCaptureFrame frame,
            Path outputDirectory,
            String cvBackendId,
            String cvBackendVersion
    ) throws IOException {
        Objects.requireNonNull(frame, "frame");
        return export(List.of(frame), outputDirectory, cvBackendId, cvBackendVersion).get(0);
    }

    /**
     * Exports normalized candidates as PNG images, module-grid overlay PNGs, and metadata text files.
     *
     * @param frames normalized candidates to export in input order
     * @param outputDirectory destination directory, created when missing
     * @return paths written for each candidate, in input order
     * @throws IOException when the destination cannot be written
     */
    public List<CandidateDebugExport> export(List<NormalizedCaptureFrame> frames, Path outputDirectory)
            throws IOException {
        return export(frames, outputDirectory, cvBackendId, cvBackendVersion);
    }

    /**
     * Exports normalized candidates as PNG images, module-grid overlay PNGs, and metadata text files with explicit CV
     * backend metadata.
     *
     * @param frames normalized candidates to export in input order
     * @param outputDirectory destination directory, created when missing
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     * @return paths written for each candidate, in input order
     * @throws IOException when the destination cannot be written
     */
    public List<CandidateDebugExport> export(
            List<NormalizedCaptureFrame> frames,
            Path outputDirectory,
            String cvBackendId,
            String cvBackendVersion
    ) throws IOException {
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        requireBackendId(cvBackendId);
        Objects.requireNonNull(cvBackendVersion, "cvBackendVersion must not be null");
        Files.createDirectories(outputDirectory);

        List<CandidateDebugExport> exports = new ArrayList<>(frames.size());
        Map<CandidateSourceKey, Integer> candidateRanks = new LinkedHashMap<>();
        for (int index = 0; index < frames.size(); index++) {
            NormalizedCaptureFrame frame = Objects.requireNonNull(frames.get(index), "frames[" + index + "]");
            CandidateSourceKey sourceKey = new CandidateSourceKey(frame.sourceId(), frame.callerOrder());
            int rank = candidateRanks.merge(sourceKey, 1, Integer::sum);
            CandidateDebugContext debugContext = new CandidateDebugContext(cvBackendId, cvBackendVersion, rank);
            exports.add(exportCandidate(frame, outputDirectory, index, debugContext));
        }
        return List.copyOf(exports);
    }

    private CandidateDebugExport exportCandidate(
            NormalizedCaptureFrame frame,
            Path outputDirectory,
            int index,
            CandidateDebugContext debugContext
    )
            throws IOException {
        String baseName = "candidate-%04d".formatted(index);
        Path imagePath = outputDirectory.resolve(baseName + ".png");
        Path metadataPath = outputDirectory.resolve(baseName + ".txt");
        Path overlayPath = outputDirectory.resolve(baseName + "-grid-overlay.png");

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
        writePng(image, imagePath);

        FrameInspection inspection = tilePayloadSampler.inspect(frame);
        writePng(overlayRenderer.render(frame, inspection), overlayPath);

        Files.writeString(
                metadataPath,
                metadata(frame, inspection, debugContext, overlayPath),
                StandardCharsets.UTF_8
        );
        return new CandidateDebugExport(imagePath, metadataPath, Optional.of(overlayPath));
    }

    private void writePng(BufferedImage image, Path outputPath) throws IOException {
        if (!ImageIO.write(image, "PNG", outputPath.toFile())) {
            throw new IOException("No PNG writer is available for " + outputPath);
        }
    }

    private String metadata(
            NormalizedCaptureFrame frame,
            FrameInspection inspection,
            CandidateDebugContext debugContext,
            Path overlayPath
    ) {
        FrameCorners corners = frame.frameCorners();
        List<String> lines = new ArrayList<>();
        lines.add("sourceId=" + frame.sourceId());
        lines.add("sourceKind=" + frame.sourceKind());
        lines.add("callerOrder=" + frame.callerOrder());
        lines.add("cv.backendId=" + debugContext.cvBackendId());
        lines.add("cv.backendVersion=" + debugContext.cvBackendVersion());
        lines.add("candidate.rank=" + debugContext.rank());
        lines.add("debug.gridOverlayPath=" + overlayPath.getFileName());
        addOverlayCounts(lines, inspection);
        addCandidateSourceBounds(lines, corners);
        addCandidateCorners(lines, corners);
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
        lines.add("perspective.frameCoverageRatio=" + frame.qualityMetrics().frameCoverageRatio());
        lines.add("perspective.skewScore=" + frame.qualityMetrics().skewScore());
        lines.add("quality.frameCoverageRatio=" + frame.qualityMetrics().frameCoverageRatio());
        lines.add("quality.skewScore=" + frame.qualityMetrics().skewScore());
        lines.add("quality.blurScore=" + frame.qualityMetrics().blurScore());
        lines.add("quality.glareScore=" + frame.qualityMetrics().glareScore());
        lines.add("quality.exposureScore=" + frame.qualityMetrics().exposureScore());
        lines.add("quality.colorDistanceScore=" + frame.qualityMetrics().colorDistanceScore());
        lines.add("sampler.candidateAttemptCount=" + inspection.candidateAttemptCount());
        lines.add("sampler.layoutProfileId=" + inspection.layoutProfileId());
        lines.add("sampler.noFinderAttemptCount=" + inspection.noFinderAttemptCount());
        lines.add("sampler.paletteRejectedAttemptCount=" + inspection.paletteRejectedAttemptCount());
        lines.add("sampler.decodedPayloadCount=" + inspection.decodedPayloadCount());
        lines.add("sampler.slotCount=" + inspection.slots().size());
        lines.add("sampler.tileDecode.attemptCount=" + tileDecodeAttemptCount(inspection));
        lines.add("sampler.envelope.acceptedPayloadCount=" + inspection.decodedPayloadCount());
        lines.add("sampler.envelope.rejectedAttemptCount=" + envelopeRejectedAttemptCount(inspection));
        lines.add("sampler.reason.borderSignatureSlotCount=" + borderStatusCount(inspection, BorderInspectionStatus.SIGNATURE));
        lines.add("sampler.reason.borderNoSignatureSlotCount=" + borderStatusCount(inspection, BorderInspectionStatus.NO_SIGNATURE));
        lines.add("sampler.reason.borderPaletteRejectedSlotCount="
                + borderStatusCount(inspection, BorderInspectionStatus.PALETTE_REJECTED));
        lines.add("sampler.reason.noFinderAttemptCount=" + inspection.noFinderAttemptCount());
        lines.add("sampler.reason.paletteRejectedAttemptCount=" + inspection.paletteRejectedAttemptCount());
        lines.add("sampler.reason.finderCandidateAttemptCount="
                + candidateStatusCount(inspection, CandidateInspectionStatus.FINDER_CANDIDATE));
        lines.add("sampler.reason.tileOrEnvelopeRejectedAttemptCount=" + envelopeRejectedAttemptCount(inspection));
        lines.add("sampler.reason.acceptedPayloadCount=" + inspection.decodedPayloadCount());
        addSamplingEvidence(lines, inspection);
        addSlotInspection(lines, inspection);
        lines.add("diagnostic.selectedPublicCode=" + selectedPublicDiagnosticCode(inspection)
                .map(CaptureMediaDiagnosticCode::name)
                .orElse(""));
        lines.add("");
        return String.join(System.lineSeparator(), lines);
    }

    private void addOverlayCounts(List<String> lines, FrameInspection inspection) {
        lines.add("overlay.tileSlotCount=" + inspection.slots().size());
        lines.add("overlay.sideVersionAttemptCount=" + inspection.candidateAttemptCount());
        lines.add("overlay.candidateAttemptCount=" + inspection.candidateAttemptCount());
        lines.add("overlay.noFinderAttemptCount=" + inspection.noFinderAttemptCount());
        lines.add("overlay.paletteRejectedAttemptCount=" + inspection.paletteRejectedAttemptCount());
        lines.add("overlay.finderCandidateAttemptCount="
                + candidateStatusCount(inspection, CandidateInspectionStatus.FINDER_CANDIDATE));
        lines.add("overlay.tileDecodeAttemptCount=" + tileDecodeAttemptCount(inspection));
        lines.add("overlay.envelope.acceptedPayloadCount=" + inspection.decodedPayloadCount());
        lines.add("overlay.envelope.rejectedAttemptCount=" + envelopeRejectedAttemptCount(inspection));
    }

    private void addCandidateSourceBounds(List<String> lines, FrameCorners corners) {
        lines.add("candidate.sourceBounds.leftPx=" + min(
                corners.topLeftX(),
                corners.topRightX(),
                corners.bottomRightX(),
                corners.bottomLeftX()
        ));
        lines.add("candidate.sourceBounds.topPx=" + min(
                corners.topLeftY(),
                corners.topRightY(),
                corners.bottomRightY(),
                corners.bottomLeftY()
        ));
        lines.add("candidate.sourceBounds.rightExclusivePx=" + max(
                corners.topLeftX(),
                corners.topRightX(),
                corners.bottomRightX(),
                corners.bottomLeftX()
        ));
        lines.add("candidate.sourceBounds.bottomExclusivePx=" + max(
                corners.topLeftY(),
                corners.topRightY(),
                corners.bottomRightY(),
                corners.bottomLeftY()
        ));
    }

    private void addCandidateCorners(List<String> lines, FrameCorners corners) {
        lines.add("candidate.corners.topLeftX=" + corners.topLeftX());
        lines.add("candidate.corners.topLeftY=" + corners.topLeftY());
        lines.add("candidate.corners.topRightX=" + corners.topRightX());
        lines.add("candidate.corners.topRightY=" + corners.topRightY());
        lines.add("candidate.corners.bottomRightX=" + corners.bottomRightX());
        lines.add("candidate.corners.bottomRightY=" + corners.bottomRightY());
        lines.add("candidate.corners.bottomLeftX=" + corners.bottomLeftX());
        lines.add("candidate.corners.bottomLeftY=" + corners.bottomLeftY());
    }

    private double min(double first, double second, double third, double fourth) {
        return Math.min(Math.min(first, second), Math.min(third, fourth));
    }

    private double max(double first, double second, double third, double fourth) {
        return Math.max(Math.max(first, second), Math.max(third, fourth));
    }

    private void addSlotInspection(List<String> lines, FrameInspection inspection) {
        for (SlotInspection slot : inspection.slots()) {
            String slotPrefix = "sampler.slot." + slot.tileIndex();
            lines.add(slotPrefix + ".borderStatus=" + slot.borderStatus());
            lines.add(slotPrefix + ".interiorContent=" + slot.interiorContent());
            lines.add(slotPrefix + ".effectiveTileShiftXPx=" + slot.effectiveTileShiftXPx());
            lines.add(slotPrefix + ".effectiveTileShiftYPx=" + slot.effectiveTileShiftYPx());
            lines.add(slotPrefix + ".effectiveTilePlacementSource=" + slot.effectiveTilePlacementSource());
            lines.add(slotPrefix + ".candidateCount=" + slot.candidates().size());
            lines.add(slotPrefix + ".reason.noFinderAttemptCount="
                    + candidateStatusCount(slot, CandidateInspectionStatus.NO_FINDER));
            lines.add(slotPrefix + ".reason.paletteRejectedAttemptCount="
                    + candidateStatusCount(slot, CandidateInspectionStatus.PALETTE_REJECTED));
            lines.add(slotPrefix + ".reason.finderCandidateAttemptCount="
                    + candidateStatusCount(slot, CandidateInspectionStatus.FINDER_CANDIDATE));
            lines.add(slotPrefix + ".reason.tileOrEnvelopeRejectedAttemptCount="
                    + decodeStatusCount(slot, DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE));
            lines.add(slotPrefix + ".reason.acceptedPayloadCount="
                    + decodeStatusCount(slot, DecodeInspectionStatus.ACCEPTED_PAYLOAD));
            for (CandidateInspection candidate : slot.candidates()) {
                String candidatePrefix = slotPrefix + ".sideVersion." + candidate.sideVersion();
                lines.add(candidatePrefix + ".dimension=" + candidate.dimension());
                lines.add(candidatePrefix + ".moduleSizePx=" + candidate.moduleSizePx());
                lines.add(candidatePrefix + ".moduleSampling.offsetXPx=" + candidate.moduleCenterOffsetXPx());
                lines.add(candidatePrefix + ".moduleSampling.offsetYPx=" + candidate.moduleCenterOffsetYPx());
                lines.add(candidatePrefix + ".moduleSampling.offsetSource="
                        + candidate.moduleSamplingOffsetSource());
                lines.add(candidatePrefix + ".moduleSampling.areaSampleRadiusPx=" + candidate.areaSampleRadiusPx());
                lines.add(candidatePrefix + ".status=" + candidate.status());
                lines.add(candidatePrefix + ".decodeStatus=" + candidate.decodeStatus());
                lines.add(candidatePrefix + ".tileDecode.status=" + candidate.decodeStatus());
                lines.add(candidatePrefix + ".envelopeValidation.status=" + candidate.decodeStatus());
                candidate.decodeFailureReason()
                        .ifPresent(reason -> lines.add(candidatePrefix + ".decodeFailureReason=" + reason));
                candidate.samplingDiagnostics().forEach((name, value) ->
                        lines.add(candidatePrefix + ".diagnostic." + name + "=" + value));
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

    private void addSamplingEvidence(List<String> lines, FrameInspection inspection) {
        Optional<CvSamplingEvidence> samplingEvidence = inspection.samplingEvidence();
        if (samplingEvidence.isEmpty()) {
            lines.add("sampler.evidence.available=false");
            lines.add("sampler.gridPhase.available=false");
            lines.add("sampler.evidence.tileCount=0");
            return;
        }
        CvSamplingEvidence evidence = samplingEvidence.orElseThrow();
        lines.add("sampler.evidence.available=true");
        lines.add("sampler.evidence.backendId=" + evidence.backendId());
        lines.add("sampler.evidence.confidence=" + evidence.confidence());
        lines.add("sampler.evidence.moduleCenterOffsetXPx=" + evidence.moduleCenterOffsetXPx());
        lines.add("sampler.evidence.moduleCenterOffsetYPx=" + evidence.moduleCenterOffsetYPx());
        evidence.metrics().forEach((name, value) -> lines.add("sampler.evidence.metric." + name + "=" + value));
        if (evidence.gridPhase().isEmpty()) {
            lines.add("sampler.gridPhase.available=false");
        } else {
            addGridPhase(lines, evidence.gridPhase().orElseThrow());
        }
        lines.add("sampler.evidence.tileCount=" + evidence.tileEvidence().size());
        for (CvTileSamplingEvidence tileEvidence : evidence.tileEvidence()) {
            String prefix = "sampler.evidence.tile." + tileEvidence.tileIndex();
            lines.add(prefix + ".moduleCenterOffsetXPx=" + tileEvidence.moduleCenterOffsetXPx());
            lines.add(prefix + ".moduleCenterOffsetYPx=" + tileEvidence.moduleCenterOffsetYPx());
            lines.add(prefix + ".confidence=" + tileEvidence.confidence());
            lines.add(prefix + ".localContrast=" + tileEvidence.localContrast());
            tileEvidence.localWhiteReferenceArgb()
                    .ifPresent(value -> lines.add(prefix + ".localWhiteReferenceArgb=" + argb(value)));
            tileEvidence.localBlackReferenceArgb()
                    .ifPresent(value -> lines.add(prefix + ".localBlackReferenceArgb=" + argb(value)));
            tileEvidence.metrics().forEach((name, value) -> lines.add(prefix + ".metric." + name + "=" + value));
        }
    }

    private void addGridPhase(List<String> lines, CvGridPhase phase) {
        lines.add("sampler.gridPhase.available=true");
        lines.add("sampler.gridPhase.offsetXPx=" + phase.offsetXPx());
        lines.add("sampler.gridPhase.offsetYPx=" + phase.offsetYPx());
        lines.add("sampler.gridPhase.confidence=" + phase.confidence());
        lines.add("sampler.gridPhase.localContrast=" + phase.localContrast());
        phase.localWhiteReferenceArgb()
                .ifPresent(value -> lines.add("sampler.gridPhase.localWhiteReferenceArgb=" + argb(value)));
        phase.localBlackReferenceArgb()
                .ifPresent(value -> lines.add("sampler.gridPhase.localBlackReferenceArgb=" + argb(value)));
    }

    private String argb(int value) {
        return "0x%08X".formatted(value);
    }

    private int borderStatusCount(FrameInspection inspection, BorderInspectionStatus status) {
        int count = 0;
        for (SlotInspection slot : inspection.slots()) {
            if (slot.borderStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private int candidateStatusCount(FrameInspection inspection, CandidateInspectionStatus status) {
        int count = 0;
        for (SlotInspection slot : inspection.slots()) {
            count += candidateStatusCount(slot, status);
        }
        return count;
    }

    private int candidateStatusCount(SlotInspection slot, CandidateInspectionStatus status) {
        int count = 0;
        for (CandidateInspection candidate : slot.candidates()) {
            if (candidate.status() == status) {
                count++;
            }
        }
        return count;
    }

    private int decodeStatusCount(SlotInspection slot, DecodeInspectionStatus status) {
        int count = 0;
        for (CandidateInspection candidate : slot.candidates()) {
            if (candidate.decodeStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private int tileDecodeAttemptCount(FrameInspection inspection) {
        int attemptCount = 0;
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.decodeStatus() != DecodeInspectionStatus.NOT_ATTEMPTED) {
                    attemptCount++;
                }
            }
        }
        return attemptCount;
    }

    private int envelopeRejectedAttemptCount(FrameInspection inspection) {
        int rejectedCount = 0;
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE) {
                    rejectedCount++;
                }
            }
        }
        return rejectedCount;
    }

    private Optional<CaptureMediaDiagnosticCode> selectedPublicDiagnosticCode(FrameInspection inspection) {
        if (inspection.decodedPayloadCount() > 0) {
            return Optional.empty();
        }
        if (inspection.candidateAttemptCount() > 0
                || inspection.paletteRejectedAttemptCount() > 0
                || inspection.noFinderAttemptCount() > 0
                || inspection.slots().stream()
                .anyMatch(slot -> slot.borderStatus() == BorderInspectionStatus.PALETTE_REJECTED)) {
            return Optional.of(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT);
        }
        return Optional.of(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND);
    }

    private void requireBackendId(String cvBackendId) {
        if (cvBackendId == null || cvBackendId.isBlank()) {
            throw new IllegalArgumentException("cvBackendId must not be blank");
        }
    }

    /**
     * Paths produced for one exported normalized candidate.
     *
     * @param imagePath normalized candidate PNG path
     * @param metadataPath sidecar metadata path
     * @param overlayPath optional module-grid overlay PNG path
     */
    public record CandidateDebugExport(Path imagePath, Path metadataPath, Optional<Path> overlayPath) {
        public CandidateDebugExport(Path imagePath, Path metadataPath) {
            this(imagePath, metadataPath, Optional.empty());
        }

        public CandidateDebugExport {
            Objects.requireNonNull(imagePath, "imagePath");
            Objects.requireNonNull(metadataPath, "metadataPath");
            Objects.requireNonNull(overlayPath, "overlayPath");
        }
    }

    private record CandidateDebugContext(String cvBackendId, String cvBackendVersion, int rank) {
        CandidateDebugContext {
            if (cvBackendId == null || cvBackendId.isBlank()) {
                throw new IllegalArgumentException("cvBackendId must not be blank");
            }
            Objects.requireNonNull(cvBackendVersion, "cvBackendVersion must not be null");
            if (rank <= 0) {
                throw new IllegalArgumentException("rank must be positive");
            }
        }
    }

    private record CandidateSourceKey(String sourceId, int callerOrder) {
        CandidateSourceKey {
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("sourceId must not be blank");
            }
            if (callerOrder < 0) {
                throw new IllegalArgumentException("callerOrder must be non-negative");
            }
        }
    }
}
