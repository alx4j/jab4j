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
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.cv.CvTileSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.CoordinateObservationSource;
import com.alx4j.jab4j.reader.capture.media.evidence.FinderRole;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureType;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;
import com.alx4j.jab4j.reader.capture.media.evidence.WeakTileEvidence;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaGeometryFitter;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaLocalLatticeRefiner;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaPatternEvidenceDetector;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.RetainedMediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.RetainedMediaInputFrameBatch;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.BorderInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.DecodeInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.PaletteCalibrationInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.PaletteColorCalibrationInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.PaletteConfidenceSummary;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.PhaseInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.ProfileAttemptInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.SlotInspection;

/**
 * Writes normalized capture candidates, module-grid overlays, and sidecar metadata for visual diagnostics.
 */
public final class CaptureMediaCandidateDebugExporter {

    private final CaptureMediaTilePayloadSampler tilePayloadSampler;
    private final CaptureMediaPatternEvidenceDetector patternEvidenceDetector;
    private final BiFunction<NormalizedCaptureFrame, PatternEvidence, GeometryFitEvidence> geometryFitProvider;
    private final CaptureMediaSourceSpaceModuleSampler sourceSpaceModuleSampler;
    private final CaptureMediaLocalLatticeRefiner localLatticeRefiner;
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
                new CaptureMediaPatternEvidenceDetector(),
                new CaptureMediaGeometryFitter()::fit,
                new CaptureMediaSourceSpaceModuleSampler(),
                new CaptureMediaModuleGridOverlayRenderer()
        );
    }

    /**
     * Creates a debug exporter with explicit sampler, pattern detector, backend metadata, and overlay renderer.
     *
     * @param tilePayloadSampler sampler used to inspect normalized candidates
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     * @param patternEvidenceDetector detector used for direct pattern evidence sidecar summaries
     * @param overlayRenderer renderer used for bounded grid overlays
     */
    CaptureMediaCandidateDebugExporter(
            CaptureMediaTilePayloadSampler tilePayloadSampler,
            String cvBackendId,
            String cvBackendVersion,
            CaptureMediaPatternEvidenceDetector patternEvidenceDetector,
            CaptureMediaModuleGridOverlayRenderer overlayRenderer
    ) {
        this(
                tilePayloadSampler,
                cvBackendId,
                cvBackendVersion,
                patternEvidenceDetector,
                new CaptureMediaGeometryFitter()::fit,
                new CaptureMediaSourceSpaceModuleSampler(),
                overlayRenderer
        );
    }

    /**
     * Creates a debug exporter with explicit evidence providers for focused tests and debug-only wiring.
     *
     * @param tilePayloadSampler sampler used to inspect normalized candidates
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     * @param patternEvidenceDetector detector used for direct pattern evidence sidecar summaries
     * @param geometryFitProvider fitter used after pattern evidence exists
     * @param overlayRenderer renderer used for bounded grid overlays
     */
    CaptureMediaCandidateDebugExporter(
            CaptureMediaTilePayloadSampler tilePayloadSampler,
            String cvBackendId,
            String cvBackendVersion,
            CaptureMediaPatternEvidenceDetector patternEvidenceDetector,
            BiFunction<NormalizedCaptureFrame, PatternEvidence, GeometryFitEvidence> geometryFitProvider,
            CaptureMediaModuleGridOverlayRenderer overlayRenderer
    ) {
        this(
                tilePayloadSampler,
                cvBackendId,
                cvBackendVersion,
                patternEvidenceDetector,
                geometryFitProvider,
                new CaptureMediaSourceSpaceModuleSampler(),
                overlayRenderer
        );
    }

    /**
     * Creates a debug exporter with explicit evidence providers for focused tests and debug-only wiring.
     *
     * @param tilePayloadSampler sampler used to inspect normalized candidates
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     * @param patternEvidenceDetector detector used for direct pattern evidence sidecar summaries
     * @param geometryFitProvider fitter used after pattern evidence exists
     * @param sourceSpaceModuleSampler sampler used for retained source-space evidence summaries
     * @param overlayRenderer renderer used for bounded grid overlays
     */
    CaptureMediaCandidateDebugExporter(
            CaptureMediaTilePayloadSampler tilePayloadSampler,
            String cvBackendId,
            String cvBackendVersion,
            CaptureMediaPatternEvidenceDetector patternEvidenceDetector,
            BiFunction<NormalizedCaptureFrame, PatternEvidence, GeometryFitEvidence> geometryFitProvider,
            CaptureMediaSourceSpaceModuleSampler sourceSpaceModuleSampler,
            CaptureMediaModuleGridOverlayRenderer overlayRenderer
    ) {
        this.tilePayloadSampler = Objects.requireNonNull(tilePayloadSampler, "tilePayloadSampler");
        this.patternEvidenceDetector = Objects.requireNonNull(
                patternEvidenceDetector,
                "patternEvidenceDetector must not be null"
        );
        this.geometryFitProvider = Objects.requireNonNull(
                geometryFitProvider,
                "geometryFitProvider must not be null"
        );
        this.sourceSpaceModuleSampler = Objects.requireNonNull(
                sourceSpaceModuleSampler,
                "sourceSpaceModuleSampler must not be null"
        );
        this.localLatticeRefiner = new CaptureMediaLocalLatticeRefiner();
        this.overlayRenderer = Objects.requireNonNull(overlayRenderer, "overlayRenderer must not be null");
        requireBackendId(cvBackendId);
        this.cvBackendId = cvBackendId;
        this.cvBackendVersion = Objects.requireNonNull(cvBackendVersion, "cvBackendVersion must not be null");
    }

    /**
     * Creates a debug exporter with explicit sampler, backend metadata, and overlay renderer.
     *
     * @param tilePayloadSampler sampler used to inspect normalized candidates
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     * @param overlayRenderer renderer used for bounded grid overlays
     */
    CaptureMediaCandidateDebugExporter(
            CaptureMediaTilePayloadSampler tilePayloadSampler,
            String cvBackendId,
            String cvBackendVersion,
            CaptureMediaModuleGridOverlayRenderer overlayRenderer
    ) {
        this(
                tilePayloadSampler,
                cvBackendId,
                cvBackendVersion,
                new CaptureMediaPatternEvidenceDetector(),
                new CaptureMediaGeometryFitter()::fit,
                new CaptureMediaSourceSpaceModuleSampler(),
                overlayRenderer
        );
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
            exports.add(exportCandidate(frame, Optional.empty(), outputDirectory, index, debugContext));
        }
        return List.copyOf(exports);
    }

    /**
     * Exports retained normalized candidates with source-space debug summaries while source pixels are still available.
     *
     * @param retainedSources retained source frames and derived normalized candidates
     * @param outputDirectory destination directory, created when missing
     * @param cvBackendId selected backend identifier
     * @param cvBackendVersion selected backend implementation version, or blank when unavailable
     * @return paths written for each candidate, in source and candidate order
     * @throws IOException when the destination cannot be written
     */
    public List<CandidateDebugExport> export(
            RetainedMediaInputFrameBatch retainedSources,
            Path outputDirectory,
            String cvBackendId,
            String cvBackendVersion
    ) throws IOException {
        Objects.requireNonNull(retainedSources, "retainedSources must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        requireBackendId(cvBackendId);
        Objects.requireNonNull(cvBackendVersion, "cvBackendVersion must not be null");
        Files.createDirectories(outputDirectory);

        List<CandidateDebugExport> exports = new ArrayList<>(retainedSources.normalizedFrames().size());
        Map<CandidateSourceKey, Integer> candidateRanks = new LinkedHashMap<>();
        int index = 0;
        for (RetainedMediaInputFrame retainedSource : retainedSources.retainedSourceFrames()) {
            MediaInputFrame sourceFrame = retainedSource.sourceFrame();
            for (NormalizedCaptureFrame frame : retainedSource.normalizedFrames()) {
                CandidateSourceKey sourceKey = new CandidateSourceKey(frame.sourceId(), frame.callerOrder());
                int rank = candidateRanks.merge(sourceKey, 1, Integer::sum);
                CandidateDebugContext debugContext = new CandidateDebugContext(cvBackendId, cvBackendVersion, rank);
                exports.add(exportCandidate(
                        frame,
                        Optional.of(sourceFrame),
                        outputDirectory,
                        index,
                        debugContext
                ));
                index++;
            }
        }
        return List.copyOf(exports);
    }

    private CandidateDebugExport exportCandidate(
            NormalizedCaptureFrame frame,
            Optional<MediaInputFrame> sourceFrame,
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
        PatternEvidence patternEvidence = sourceFrame
                .map(source -> patternEvidenceDetector.detect(source, frame))
                .orElseGet(() -> patternEvidenceDetector.detect(frame));
        GeometryFitEvidence geometryEvidence = Objects.requireNonNull(
                geometryFitProvider.apply(frame, patternEvidence),
                "geometryEvidence must not be null"
        );
        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sourceSamplingSample =
                sourceSamplingEvidence(sourceFrame, frame, geometryEvidence);
        List<ModuleSamplingEvidence> sourceSamplingEvidence = sourceSamplingSample.evidence();
        Optional<ModuleSamplingEvidence> selectedSourceSamplingEvidence =
                selectedSourceSamplingEvidence(sourceSamplingEvidence);
        LocalRefinementEvidence localRefinementEvidence = selectedLocalRefinementEvidence(
                sourceSamplingSample.localRefinementEvidence(),
                selectedSourceSamplingEvidence
        ).orElseGet(() -> localLatticeRefiner.diagnose(geometryEvidence, selectedSourceSamplingEvidence.orElse(null)));
        List<ModuleSamplingEvidence> selectedSamplingVariants = sourceSamplingEvidence;
        Optional<ModuleSamplingEvidence> selectedRefinedOrBaselineSamplingEvidence =
                selectedAppliedSamplingEvidence(selectedSamplingVariants, localRefinementEvidence)
                        .or(() -> selectedSourceSamplingEvidence(selectedSamplingVariants));
        writePng(overlayRenderer.render(
                frame,
                inspection,
                patternEvidence,
                geometryEvidence,
                selectedRefinedOrBaselineSamplingEvidence,
                localRefinementEvidence
        ), overlayPath);

        Files.writeString(
                metadataPath,
                metadata(
                        frame,
                        inspection,
                        patternEvidence,
                        geometryEvidence,
                        selectedSamplingVariants,
                        selectedRefinedOrBaselineSamplingEvidence,
                        localRefinementEvidence,
                        debugContext,
                        overlayPath
                ),
                StandardCharsets.UTF_8
        );
        return new CandidateDebugExport(imagePath, metadataPath, Optional.of(overlayPath));
    }

    private CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sourceSamplingEvidence(
            Optional<MediaInputFrame> sourceFrame,
            NormalizedCaptureFrame frame,
            GeometryFitEvidence geometryEvidence
    ) {
        if (sourceFrame.isEmpty()) {
            return new CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample(List.of(), List.of());
        }
        return sourceSpaceModuleSampler.sampleAndValidate(
                sourceFrame.orElseThrow(),
                frame,
                geometryEvidence
        );
    }

    private Optional<ModuleSamplingEvidence> selectedSourceSamplingEvidence(List<ModuleSamplingEvidence> evidence) {
        return evidence.stream()
                .filter(candidate -> candidate.acceptedPayloadCount() > 0)
                .findFirst()
                .or(() -> evidence.stream()
                        .filter(candidate -> candidate.status() == ModuleSamplingStatus.SAMPLED)
                        .findFirst())
                .or(() -> evidence.stream()
                .filter(candidate -> candidate.status() == ModuleSamplingStatus.PARTIAL)
                .findFirst())
                .or(() -> evidence.stream().findFirst());
    }

    private Optional<LocalRefinementEvidence> selectedLocalRefinementEvidence(
            List<LocalRefinementEvidence> evidence,
            Optional<ModuleSamplingEvidence> selectedSamplingEvidence
    ) {
        Optional<LocalRefinementEvidence> applied = evidence.stream()
                .filter(LocalRefinementEvidence::appliedToSampling)
                .findFirst();
        if (applied.isPresent() || selectedSamplingEvidence.isEmpty()) {
            return applied.or(() -> evidence.stream().findFirst());
        }
        String samplingCandidateId = selectedSamplingEvidence.orElseThrow()
                .candidateId()
                .samplingCandidateId()
                .orElse("");
        return evidence.stream()
                .filter(candidate -> candidate.baseSamplingCandidateId().equals(samplingCandidateId))
                .findFirst()
                .or(() -> evidence.stream().findFirst());
    }

    private Optional<ModuleSamplingEvidence> selectedAppliedSamplingEvidence(
            List<ModuleSamplingEvidence> samplingEvidence,
            LocalRefinementEvidence localRefinementEvidence
    ) {
        if (!localRefinementEvidence.appliedToSampling()) {
            return Optional.empty();
        }
        String refinementCandidateId = localRefinementEvidence.candidateId().refinementCandidateId().orElse("");
        return samplingEvidence.stream()
                .filter(candidate -> candidate.candidateId()
                        .refinementCandidateId()
                        .filter(refinementCandidateId::equals)
                        .isPresent())
                .findFirst();
    }

    private void writePng(BufferedImage image, Path outputPath) throws IOException {
        if (!ImageIO.write(image, "PNG", outputPath.toFile())) {
            throw new IOException("No PNG writer is available for " + outputPath);
        }
    }

    private String metadata(
            NormalizedCaptureFrame frame,
            FrameInspection inspection,
            PatternEvidence patternEvidence,
            GeometryFitEvidence geometryEvidence,
            List<ModuleSamplingEvidence> sourceSamplingEvidence,
            Optional<ModuleSamplingEvidence> selectedSourceSamplingEvidence,
            LocalRefinementEvidence localRefinementEvidence,
            CandidateDebugContext debugContext,
            Path overlayPath
    ) {
        FrameCorners corners = frame.frameCorners();
        DownstreamSummary downstreamSummary = downstreamSummary(inspection, selectedSourceSamplingEvidence);
        List<String> lines = new ArrayList<>();
        lines.add("sourceId=" + frame.sourceId());
        lines.add("sourceKind=" + frame.sourceKind());
        lines.add("callerOrder=" + frame.callerOrder());
        lines.add("cv.backendId=" + debugContext.cvBackendId());
        lines.add("cv.backendVersion=" + debugContext.cvBackendVersion());
        lines.add("candidate.rank=" + debugContext.rank());
        lines.add("candidate.normalizationLayoutProfileId=" + frame.layoutProfileId());
        lines.add("candidate.sourceRegionRank=" + frame.sourceRegionRank());
        lines.add("candidate.profileAlternativeRank=" + frame.profileAlternativeRank());
        lines.add("candidate.profileAlternativeCount=" + frame.profileAlternativeCount());
        frame.geometrySource().ifPresent(source -> lines.add("candidate.geometrySource=" + source));
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
        lines.add("sampler.profileAttemptCount=" + inspection.profileAttempts().size());
        lines.add("sampler.selectedLayoutProfileId=" + inspection.selectedLayoutProfileId().orElse(""));
        lines.add("sampler.profileSelectionSource=" + inspection.profileSelectionSource().sidecarValue());
        lines.add("sampler.noFinderAttemptCount=" + inspection.noFinderAttemptCount());
        lines.add("sampler.paletteRejectedAttemptCount=" + inspection.paletteRejectedAttemptCount());
        lines.add("sampler.decodedPayloadCount=" + inspection.decodedPayloadCount());
        lines.add("sampler.slotCount=" + inspection.slots().size());
        addPaletteCalibration(lines, inspection.paletteCalibration());
        int partialRejectedSlotCount = partialRejectedSlotCount(inspection);
        int partialUndecodableSlotCount = partialUndecodableSlotCount(inspection);
        boolean partialAccepted = cameraDerived(frame)
                && inspection.decodedPayloadCount() > 0
                && (partialRejectedSlotCount > 0 || partialUndecodableSlotCount > 0);
        lines.add("sampler.partialAccepted=" + partialAccepted);
        lines.add("sampler.partialRejectedSlotCount=" + partialRejectedSlotCount);
        lines.add("sampler.partialUndecodableSlotCount=" + partialUndecodableSlotCount);
        lines.add("sampler.partialAcceptedPayloadCount="
                + (partialAccepted ? inspection.decodedPayloadCount() : 0));
        lines.add("sampler.partialWarningCount=" + (partialAccepted ? 1 : 0));
        lines.add("sampler.tileDecode.attemptCount=" + tileDecodeAttemptCount(inspection));
        lines.add("sampler.phase.attemptedVariantCount=" + phaseAttemptedVariantCount(inspection));
        lines.add("sampler.phase.variantCap=" + phaseVariantCap(inspection));
        lines.add("sampler.phase.capReached=" + phaseCapReached(inspection));
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
        lines.add("sampler.reason.postPaletteRejectedAttemptCount=" + postPaletteRejectedAttemptCount(inspection));
        lines.add("sampler.reason.acceptedPayloadCount=" + inspection.decodedPayloadCount());
        addEvidenceSummary(
                lines,
                patternEvidence,
                geometryEvidence,
                selectedSourceSamplingEvidence,
                localRefinementEvidence,
                downstreamSummary
        );
        addPatternEvidence(lines, patternEvidence);
        addGeometryEvidence(lines, geometryEvidence, inspection);
        addSourceSamplingEvidence(lines, sourceSamplingEvidence, selectedSourceSamplingEvidence);
        addLocalRefinementEvidence(lines, localRefinementEvidence);
        addDownstreamSummary(lines, downstreamSummary);
        addDuplicateCandidateComparison(lines, inspection, selectedSourceSamplingEvidence);
        addOverlayEvidenceCounts(
                lines,
                patternEvidence,
                geometryEvidence,
                selectedSourceSamplingEvidence,
                localRefinementEvidence
        );
        addProfileAttempts(lines, inspection);
        addSamplingEvidence(lines, inspection);
        addSlotInspection(lines, inspection);
        lines.add("diagnostic.selectedPublicCode=" + selectedPublicDiagnosticCode(inspection)
                .map(CaptureMediaDiagnosticCode::name)
                .orElse(""));
        lines.add("diagnostic.selectedFailureStage=" + selectedFailureStage(inspection).orElse(""));
        lines.add("");
        return String.join(System.lineSeparator(), lines);
    }

    private void addEvidenceSummary(
            List<String> lines,
            PatternEvidence patternEvidence,
            GeometryFitEvidence geometryEvidence,
            Optional<ModuleSamplingEvidence> sourceSamplingEvidence,
            LocalRefinementEvidence localRefinementEvidence,
            DownstreamSummary downstreamSummary
    ) {
        ReprojectionMetrics summaryMetrics = selectedOrFirstGeometryCandidate(geometryEvidence)
                .map(GeometryCandidateEvidence::reprojectionMetrics)
                .orElseGet(ReprojectionMetrics::zero);
        lines.add("evidence.schemaVersion=1");
        lines.add("evidence.sourceImageId=" + patternEvidence.candidateId().sourceImageId());
        lines.add("evidence.proposalCandidateId="
                + patternEvidence.candidateId().proposalCandidateId().orElse(""));
        lines.add("evidence.patternEvidenceId="
                + patternEvidence.candidateId().patternEvidenceId().orElse(""));
        lines.add("evidence.geometryCandidateId="
                + geometryEvidence.selectedGeometryCandidateId().orElse(""));
        lines.add("evidence.samplingCandidateId=" + sourceSamplingEvidence
                .flatMap(evidence -> evidence.candidateId().samplingCandidateId())
                .orElse(""));
        lines.add("evidence.refinementCandidateId="
                + localRefinementEvidence.candidateId().refinementCandidateId().orElse(""));
        lines.add("evidence.patternStatus=" + patternEvidence.status());
        lines.add("evidence.patternReasonCodes=" + reasonCodes(patternEvidence.reasonCodes()));
        lines.add("evidence.geometryStatus=" + geometryEvidence.status());
        lines.add("evidence.geometryRetainedCandidateCount=" + geometryEvidence.retainedCandidates().size());
        lines.add("evidence.geometrySelectedCandidateId="
                + geometryEvidence.selectedGeometryCandidateId().orElse(""));
        lines.add("evidence.geometryReprojectionSummary=" + reprojectionSummary(summaryMetrics));
        lines.add("evidence.sourceSamplingStatus=" + sourceSamplingEvidence
                .map(evidence -> evidence.status().name())
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("evidence.localRefinementStatus=" + localRefinementEvidence.status());
        lines.add("evidence.downstreamStatus=" + downstreamSummary.summary());
        lines.add("evidence.restoreEligibility=" + downstreamSummary.restoreEligibility());
    }

    private void addSourceSamplingEvidence(
            List<String> lines,
            List<ModuleSamplingEvidence> sourceSamplingEvidence,
            Optional<ModuleSamplingEvidence> selectedEvidence
    ) {
        lines.add("sourceSampling.evidenceAvailable=" + selectedEvidence.isPresent());
        lines.add("sourceSampling.variantCount=" + sourceSamplingEvidence.size());
        lines.add("sourceSampling.status=" + selectedEvidence
                .map(evidence -> evidence.status().name())
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.schemaVersion=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.schemaVersion()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.sourceImageId=" + selectedEvidence
                .map(evidence -> evidence.candidateId().sourceImageId())
                .orElse(""));
        lines.add("sourceSampling.proposalCandidateId=" + selectedEvidence
                .flatMap(evidence -> evidence.candidateId().proposalCandidateId())
                .orElse(""));
        lines.add("sourceSampling.patternEvidenceId=" + selectedEvidence
                .flatMap(evidence -> evidence.candidateId().patternEvidenceId())
                .orElse(""));
        lines.add("sourceSampling.geometryCandidateId=" + selectedEvidence
                .map(ModuleSamplingEvidence::geometryCandidateId)
                .orElse(""));
        lines.add("sourceSampling.samplingCandidateId=" + selectedEvidence
                .flatMap(evidence -> evidence.candidateId().samplingCandidateId())
                .orElse(""));
        lines.add("sourceSampling.candidateId=" + selectedEvidence
                .map(evidence -> evidence.candidateId().value())
                .orElse(""));
        lines.add("sourceSampling.layoutProfileId=" + selectedEvidence
                .map(ModuleSamplingEvidence::layoutProfileId)
                .orElse(""));
        lines.add("sourceSampling.coordinateSystem=" + selectedEvidence
                .map(ModuleSamplingEvidence::coordinateSystem)
                .orElse(""));
        lines.add("sourceSampling.geometrySource=" + selectedEvidence
                .map(ModuleSamplingEvidence::geometrySource)
                .orElse(""));
        lines.add("sourceSampling.centralScale=" + selectedEvidence
                .map(evidence -> Double.toString(evidence.centralScale()))
                .orElse(""));
        lines.add("sourceSampling.aggregationMethod=" + selectedEvidence
                .map(evidence -> evidence.aggregationMethod().name())
                .orElse(""));
        lines.add("sourceSampling.centralRegionPolicy=" + selectedEvidence
                .map(ModuleSamplingEvidence::centralRegionPolicy)
                .orElse(""));
        lines.add("sourceSampling.totalModuleCount=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.totalModuleCount()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.sampledModuleCount=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.sampledModuleCount()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.readableModuleCount=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.readableModuleCount()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.ambiguousModuleCount=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.ambiguousModuleCount()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.unreadableModuleCount=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.unreadableModuleCount()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.clippedModuleCount=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.clippedModuleCount()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.outOfBoundsModuleCount=" + selectedEvidence
                .map(evidence -> Integer.toString(evidence.outOfBoundsModuleCount()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        addMetricSummary(lines, "sourceSampling.confidenceMargin", selectedEvidence.isPresent(), selectedEvidence
                .map(ModuleSamplingEvidence::confidenceMarginSummary)
                .orElse(Map.of()));
        addMetricSummary(lines, "sourceSampling.colorVariance", selectedEvidence.isPresent(), selectedEvidence
                .map(ModuleSamplingEvidence::colorVarianceSummary)
                .orElse(Map.of()));
        addMetricSummary(lines, "moduleConfidence", selectedEvidence.isPresent(), selectedEvidence
                .map(ModuleSamplingEvidence::moduleConfidenceSummary)
                .orElse(Map.of()));
        addMetricSummary(lines, "moduleFootprint", selectedEvidence.isPresent(), selectedEvidence
                .map(ModuleSamplingEvidence::geometryFootprintQualitySummary)
                .orElse(Map.of()));
        lines.add("moduleConfidence.weakModuleCount="
                + sourceSamplingInt(selectedEvidence, ModuleSamplingEvidence::weakModuleCount));
        lines.add("moduleFootprint.poorFootprintModuleCount="
                + sourceSamplingInt(selectedEvidence, ModuleSamplingEvidence::poorFootprintModuleCount));
        addObservedPaletteEvidence(lines, selectedEvidence);
        addWeakModuleEvidence(lines, selectedEvidence);
        lines.add("sourceSampling.tileDecodeAttempted=" + selectedEvidence
                .map(evidence -> Boolean.toString(evidence.tileDecodeAttempted()))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name()));
        lines.add("sourceSampling.tileDecodeAttemptCount="
                + sourceSamplingInt(selectedEvidence, ModuleSamplingEvidence::tileDecodeAttemptCount));
        lines.add("sourceSampling.validatedTileCount="
                + sourceSamplingInt(selectedEvidence, ModuleSamplingEvidence::acceptedPayloadCount));
        lines.add("sourceSampling.tileDecodeFailureStages=" + selectedEvidence
                .map(evidence -> textList(evidence.tileDecodeFailureStages()))
                .orElse(""));
        lines.add("sourceSampling.reasonCodes=" + selectedEvidence
                .map(evidence -> reasonCodes(evidence.reasonCodes()))
                .orElse(CaptureMediaEvidenceReasonCode.SOURCE_PIXELS_UNAVAILABLE.name()));
        lines.add("sourceSampling.moduleDetailIncluded=false");
        lines.add("sourceSampling.moduleDetailCount=0");
    }

    private void addObservedPaletteEvidence(
            List<String> lines,
            Optional<ModuleSamplingEvidence> selectedEvidence
    ) {
        if (selectedEvidence.isEmpty()) {
            lines.add("palette.observed.status=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("palette.observed.colorMethod=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("palette.observed.classificationMode=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("palette.safety.decision=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("classification.colorMethod=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("classification.mode=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("classification.paletteModelSource=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("palette.observed.colorCount=0");
            return;
        }
        ModuleSamplingEvidence evidence = selectedEvidence.orElseThrow();
        var palette = evidence.observedPaletteEvidence();
        lines.add("palette.observed.status=" + palette.status());
        lines.add("palette.observed.source=" + palette.paletteSource());
        lines.add("palette.observed.colorMethod=" + palette.colorMethod());
        lines.add("palette.observed.classificationMode=" + palette.classificationMode());
        lines.add("classification.colorMethod=" + palette.colorMethod());
        lines.add("classification.mode=" + palette.classificationMode());
        lines.add("classification.paletteModelSource=" + selectedPaletteModelSource(evidence));
        lines.add("palette.observed.coverageRatio=" + palette.observedCoverageRatio());
        lines.add("palette.observed.separationScore=" + palette.separationScore());
        lines.add("palette.observed.weakestPalettePair=" + palette.weakestPalettePair().orElse(""));
        lines.add("palette.observed.minimumConfidence=" + palette.minimumConfidence());
        lines.add("palette.observed.thresholdVersion=" + palette.thresholdVersion());
        lines.add("palette.observed.fallbackReason=" + palette.fallbackReason().orElse(""));
        lines.add("palette.safety.decision=" + palette.safetyDecision());
        lines.add("palette.safety.reasonCodes=" + reasonCodes(palette.reasonCodes()));
        lines.add("palette.observed.colorCount=" + palette.colors().size());
        int colorLimit = Math.min(8, palette.colors().size());
        for (int index = 0; index < colorLimit; index++) {
            var color = palette.colors().get(index);
            String prefix = "palette.observed.color." + index;
            lines.add(prefix + ".paletteIndex=" + color.paletteIndex());
            lines.add(prefix + ".sampleCount=" + color.sampleCount());
            lines.add(prefix + ".centerSource=" + color.centerSource());
            lines.add(prefix + ".confidence=" + color.confidence());
            lines.add(prefix + ".maximumDistanceToCentroid=" + color.maximumDistanceToCentroid());
            lines.add(prefix + ".maximumDistanceToExpected=" + color.maximumDistanceToExpected());
        }
    }

    private String selectedPaletteModelSource(ModuleSamplingEvidence evidence) {
        return evidence.modules()
                .stream()
                .map(ModuleEvidence::paletteModelSource)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(evidence.observedPaletteEvidence().paletteSource());
    }

    private void addWeakModuleEvidence(
            List<String> lines,
            Optional<ModuleSamplingEvidence> selectedEvidence
    ) {
        if (selectedEvidence.isEmpty()) {
            lines.add("weakModule.included=false");
            lines.add("weakModule.count=0");
            addWeakTileEvidence(lines, selectedEvidence);
            addFrameBlockerEvidence(lines, selectedEvidence);
            return;
        }
        List<ModuleEvidence> weakModules = selectedEvidence.orElseThrow().modules().stream()
                .filter(this::weakModule)
                .sorted(this::compareWeakModules)
                .limit(20)
                .toList();
        lines.add("weakModule.included=true");
        lines.add("weakModule.count=" + weakModules.size());
        for (int index = 0; index < weakModules.size(); index++) {
            ModuleEvidence module = weakModules.get(index);
            String prefix = "weakModule." + index;
            lines.add(prefix + ".moduleX=" + module.moduleX());
            lines.add(prefix + ".moduleY=" + module.moduleY());
            lines.add(prefix + ".status=" + module.status());
            lines.add(prefix + ".moduleConfidence=" + module.moduleConfidence());
            lines.add(prefix + ".geometryFootprintQuality=" + module.geometryFootprintQuality());
            lines.add(prefix + ".sourceFootprintAreaPx=" + module.sourceFootprintAreaPx());
            lines.add(prefix + ".innerFootprintAreaPx=" + module.innerFootprintAreaPx());
            lines.add(prefix + ".minimumFootprintEdgePx=" + module.minimumFootprintEdgePx());
            lines.add(prefix + ".clippedFraction=" + module.clippedFraction());
            lines.add(prefix + ".confidenceMargin=" + module.confidenceMargin());
            lines.add(prefix + ".colorVariance=" + module.colorVariance());
            lines.add(prefix + ".reasonCodes=" + reasonCodes(module.reasonCodes()));
        }
        addWeakTileEvidence(lines, selectedEvidence);
        addFrameBlockerEvidence(lines, selectedEvidence);
    }

    private void addWeakTileEvidence(
            List<String> lines,
            Optional<ModuleSamplingEvidence> selectedEvidence
    ) {
        if (selectedEvidence.isEmpty()) {
            lines.add("weakTile.included=false");
            lines.add("weakTile.totalTileRegionCount=0");
            lines.add("weakTile.count=0");
            return;
        }
        List<WeakTileEvidence> weakTiles = selectedEvidence.orElseThrow().weakTileEvidence()
                .stream()
                .sorted(this::compareWeakTiles)
                .limit(5)
                .toList();
        lines.add("weakTile.included=true");
        lines.add("weakTile.totalTileRegionCount=" + selectedEvidence.orElseThrow().weakTileEvidence().size());
        lines.add("weakTile.count=" + weakTiles.size());
        for (int index = 0; index < weakTiles.size(); index++) {
            WeakTileEvidence tile = weakTiles.get(index);
            String prefix = "weakTile." + index;
            lines.add(prefix + ".tileIndex=" + tile.tileIndex());
            lines.add(prefix + ".moduleStartX=" + tile.moduleStartX());
            lines.add(prefix + ".moduleStartY=" + tile.moduleStartY());
            lines.add(prefix + ".moduleWidth=" + tile.moduleWidth());
            lines.add(prefix + ".moduleHeight=" + tile.moduleHeight());
            lines.add(prefix + ".weakModuleCount=" + tile.weakModuleCount());
            lines.add(prefix + ".weakModuleDensity=" + tile.weakModuleDensity());
            lines.add(prefix + ".poorFootprintModuleCount=" + tile.poorFootprintModuleCount());
            lines.add(prefix + ".minimumModuleConfidence=" + tile.minimumModuleConfidence());
            lines.add(prefix + ".minimumGeometryFootprintQuality=" + tile.minimumGeometryFootprintQuality());
            lines.add(prefix + ".tileDecodeAttempted=" + tile.tileDecodeAttempted());
            lines.add(prefix + ".acceptedPayload=" + tile.acceptedPayload());
            lines.add(prefix + ".failureStages=" + textList(tile.failureStages()));
            lines.add(prefix + ".reasonCodes=" + reasonCodes(tile.reasonCodes()));
        }
    }

    private int compareWeakTiles(WeakTileEvidence first, WeakTileEvidence second) {
        int accepted = Boolean.compare(first.acceptedPayload(), second.acceptedPayload());
        if (accepted != 0) {
            return accepted;
        }
        int density = Double.compare(second.weakModuleDensity(), first.weakModuleDensity());
        if (density != 0) {
            return density;
        }
        int weakCount = Integer.compare(second.weakModuleCount(), first.weakModuleCount());
        return weakCount != 0 ? weakCount : Integer.compare(first.tileIndex(), second.tileIndex());
    }

    private void addFrameBlockerEvidence(
            List<String> lines,
            Optional<ModuleSamplingEvidence> selectedEvidence
    ) {
        if (selectedEvidence.isEmpty()) {
            lines.add("frameBlocker.stage=NO_SOURCE_SPACE_SAMPLING");
            lines.add("frameBlocker.paletteSafetyStatus=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add("frameBlocker.weakModuleCount=0");
            lines.add("frameBlocker.poorFootprintModuleCount=0");
            lines.add("frameBlocker.weakTileCount=0");
            return;
        }
        ModuleSamplingEvidence evidence = selectedEvidence.orElseThrow();
        String stage;
        if (evidence.acceptedPayloadCount() > 0) {
            stage = "VALIDATED_TILE_AVAILABLE";
        } else if (evidence.tileDecodeAttempted()) {
            stage = evidence.tileDecodeFailureStages().isEmpty()
                    ? "TILE_DECODE"
                    : String.join(",", evidence.tileDecodeFailureStages());
        } else if (evidence.observedPaletteEvidence().safetyDecision().name().contains("UNSAFE")
                || evidence.observedPaletteEvidence().safetyDecision().name().contains("WITHHELD")) {
            stage = "PALETTE_SAFETY";
        } else if (evidence.weakModuleCount() > 0) {
            stage = "MODULE_CONFIDENCE_OR_GEOMETRY";
        } else {
            stage = "NO_PLAUSIBLE_TILE";
        }
        long weakTileCount = evidence.weakTileEvidence()
                .stream()
                .filter(tile -> tile.weakModuleCount() > 0 || !tile.acceptedPayload())
                .count();
        lines.add("frameBlocker.stage=" + stage);
        lines.add("frameBlocker.paletteSafetyStatus=" + evidence.observedPaletteEvidence().status());
        lines.add("frameBlocker.weakModuleCount=" + evidence.weakModuleCount());
        lines.add("frameBlocker.poorFootprintModuleCount=" + evidence.poorFootprintModuleCount());
        lines.add("frameBlocker.weakTileCount=" + weakTileCount);
    }

    private void addDownstreamSummary(List<String> lines, DownstreamSummary summary) {
        lines.add("downstream.summary=" + summary.summary());
        lines.add("downstream.restoreEligible=" + summary.restoreEligible());
        lines.add("downstream.restoreEligibility=" + summary.restoreEligibility());
        lines.add("downstream.tileDecodeAttempted=" + summary.tileDecodeAttempted());
        lines.add("downstream.tileDecodeAttemptCount=" + summary.tileDecodeAttemptCount());
        lines.add("downstream.normalizedTileDecodeAttemptCount=" + summary.normalizedTileDecodeAttemptCount());
        lines.add("downstream.sourceSamplingTileDecodeAttemptCount="
                + summary.sourceSamplingTileDecodeAttemptCount());
        lines.add("downstream.validatedTileCount=" + summary.validatedTileCount());
        lines.add("downstream.normalizedValidatedTileCount=" + summary.normalizedValidatedTileCount());
        lines.add("downstream.sourceSamplingValidatedTileCount="
                + summary.sourceSamplingValidatedTileCount());
        lines.add("downstream.failureStage=" + summary.failureStage());
        lines.add("downstream.sourceSamplingFailureStages=" + summary.sourceSamplingFailureStages());
    }

    private void addDuplicateCandidateComparison(
            List<String> lines,
            FrameInspection inspection,
            Optional<ModuleSamplingEvidence> selectedSourceSamplingEvidence
    ) {
        Map<String, List<DecodedCandidateSummary>> acceptedByIdentity = new LinkedHashMap<>();
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.decodeStatus() != DecodeInspectionStatus.ACCEPTED_PAYLOAD) {
                    continue;
                }
                DecodedCandidateSummary summary = decodedCandidateSummary(slot, candidate);
                acceptedByIdentity.computeIfAbsent(summary.identity(), ignored -> new ArrayList<>()).add(summary);
            }
        }
        List<Map.Entry<String, List<DecodedCandidateSummary>>> duplicateGroups = acceptedByIdentity.entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .limit(5)
                .toList();
        lines.add("duplicateCandidate.included=true");
        lines.add("duplicateCandidate.groupCount=" + duplicateGroups.size());
        lines.add("duplicateCandidate.acceptedCandidateCount=" + acceptedByIdentity.values()
                .stream()
                .mapToInt(List::size)
                .sum());
        lines.add("duplicateCandidate.sourceSamplingAcceptedPayloadCount=" + selectedSourceSamplingEvidence
                .map(ModuleSamplingEvidence::acceptedPayloadCount)
                .orElse(0));
        for (int groupIndex = 0; groupIndex < duplicateGroups.size(); groupIndex++) {
            Map.Entry<String, List<DecodedCandidateSummary>> group = duplicateGroups.get(groupIndex);
            String groupPrefix = "duplicateCandidate.group." + groupIndex;
            lines.add(groupPrefix + ".identity=" + group.getKey());
            lines.add(groupPrefix + ".candidateCount=" + group.getValue().size());
            lines.add(groupPrefix + ".equivalentContent=true");
            int candidateLimit = Math.min(4, group.getValue().size());
            for (int candidateIndex = 0; candidateIndex < candidateLimit; candidateIndex++) {
                DecodedCandidateSummary candidate = group.getValue().get(candidateIndex);
                String prefix = groupPrefix + ".candidate." + candidateIndex;
                lines.add(prefix + ".slotIndex=" + candidate.slotIndex());
                lines.add(prefix + ".sideVersion=" + candidate.sideVersion());
                lines.add(prefix + ".moduleConfidence=" + candidate.moduleConfidence());
                lines.add(prefix + ".paletteMinimumConfidence=" + candidate.paletteMinimumConfidence());
                lines.add(prefix + ".moduleOffsetXPx=" + candidate.moduleOffsetXPx());
                lines.add(prefix + ".moduleOffsetYPx=" + candidate.moduleOffsetYPx());
                lines.add(prefix + ".classificationSource=normalized-sampler");
            }
        }
    }

    private DecodedCandidateSummary decodedCandidateSummary(SlotInspection slot, CandidateInspection candidate) {
        String layoutProfileId = candidate.decodedPayloadLayoutProfileId()
                .or(() -> optionalDiagnostic(candidate.decodeDiagnostics(), "decodedPayload.layoutProfileId"))
                .orElse("");
        String tileIndex = optionalDiagnostic(candidate.decodeDiagnostics(), "decodedPayload.tileIndex")
                .orElse(Integer.toString(slot.tileIndex()));
        String totalTiles = optionalDiagnostic(candidate.decodeDiagnostics(), "decodedPayload.totalTiles").orElse("");
        String identity = "layoutProfileId:" + layoutProfileId
                + ",tileIndex:" + tileIndex
                + ",totalTiles:" + totalTiles;
        double paletteMinimumConfidence = candidate.paletteConfidence()
                .map(PaletteConfidenceSummary::minimumConfidence)
                .orElse(0.0d);
        double moduleConfidence = Math.min(
                paletteMinimumConfidence,
                candidate.selectedPhase().paletteCalibrationConfidence()
        );
        return new DecodedCandidateSummary(
                identity,
                slot.tileIndex(),
                candidate.sideVersion(),
                moduleConfidence,
                paletteMinimumConfidence,
                candidate.moduleCenterOffsetXPx(),
                candidate.moduleCenterOffsetYPx()
        );
    }

    private Optional<String> optionalDiagnostic(Map<String, String> diagnostics, String key) {
        String value = diagnostics.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private void addOverlayEvidenceCounts(
            List<String> lines,
            PatternEvidence patternEvidence,
            GeometryFitEvidence geometryEvidence,
            Optional<ModuleSamplingEvidence> sourceSamplingEvidence,
            LocalRefinementEvidence localRefinementEvidence
    ) {
        lines.add("overlay.patternFeatureCount=" + patternEvidence.features().size());
        lines.add("overlay.geometryRetainedCandidateCount=" + geometryEvidence.retainedCandidates().size());
        lines.add("overlay.sourceSamplingSummaryDrawn=" + sourceSamplingEvidence.isPresent());
        lines.add("overlay.localResidualVectorCount=" + localRefinementEvidence.controlPoints().size());
    }

    private void addPatternEvidence(List<String> lines, PatternEvidence evidence) {
        lines.add("pattern.schemaVersion=" + evidence.schemaVersion());
        lines.add("pattern.sourceImageId=" + evidence.candidateId().sourceImageId());
        lines.add("pattern.proposalCandidateId=" + evidence.candidateId().proposalCandidateId().orElse(""));
        lines.add("pattern.patternEvidenceId=" + evidence.candidateId().patternEvidenceId().orElse(""));
        lines.add("pattern.candidateId=" + evidence.candidateId().value());
        lines.add("pattern.layoutProfileId=" + evidence.layoutProfileId());
        lines.add("pattern.status=" + evidence.status());
        lines.add("pattern.confidence=" + evidence.confidence());
        lines.add("pattern.dominanceMargin=" + evidence.dominanceMargin());
        lines.add("pattern.featureCount=" + evidence.features().size());
        lines.add("pattern.finderFeatureCount=" + finderFeatureCount(evidence));
        lines.add("pattern.alignmentExpected=" + evidence.alignmentExpected());
        lines.add("pattern.alignmentStatus=NOT_SUPPORTED_FOR_PROFILE");
        lines.add("pattern.observationSource=" + observationSource(evidence).map(Enum::name).orElse(""));
        lines.add("pattern.reasonCodes=" + reasonCodes(evidence.reasonCodes()));
        lines.add("pattern.downstreamConflictReasons=" + reasonCodes(evidence.downstreamConflictReasons()));
        lines.add("pattern.orientationCandidates=" + scoreMap(evidence.orientationCandidates()));
        lines.add("pattern.layoutProfileCandidates=" + scoreMap(evidence.layoutProfileCandidates()));
        for (FinderRole role : FinderRole.values()) {
            FinderRoleSummary summary = finderRoleSummary(evidence, role);
            String rolePrefix = "pattern.finder." + role;
            lines.add(rolePrefix + ".featureCount=" + summary.featureCount());
            lines.add(rolePrefix + ".matchedModuleCount=" + summary.matchedModuleCount());
            lines.add(rolePrefix + ".expectedModuleCount=" + summary.expectedModuleCount());
            lines.add(rolePrefix + ".confidence=" + summary.confidence());
        }
    }

    private void addGeometryEvidence(
            List<String> lines,
            GeometryFitEvidence evidence,
            FrameInspection inspection
    ) {
        List<CaptureMediaEvidenceReasonCode> downstreamConflictReasons =
                geometryDownstreamConflictReasons(evidence, inspection);
        ReprojectionMetrics summaryMetrics = selectedOrFirstGeometryCandidate(evidence)
                .map(GeometryCandidateEvidence::reprojectionMetrics)
                .orElseGet(ReprojectionMetrics::zero);

        lines.add("geometry.schemaVersion=" + evidence.schemaVersion());
        lines.add("geometry.sourceImageId=" + evidence.candidateId().sourceImageId());
        lines.add("geometry.proposalCandidateId=" + evidence.candidateId().proposalCandidateId().orElse(""));
        lines.add("geometry.patternEvidenceId=" + evidence.candidateId().patternEvidenceId().orElse(""));
        lines.add("geometry.candidateId=" + evidence.candidateId().value());
        lines.add("geometry.status=" + evidence.status());
        lines.add("geometry.selectedGeometryCandidateId=" + evidence.selectedGeometryCandidateId().orElse(""));
        lines.add("geometry.downstreamSelectedGeometryCandidateId="
                + evidence.downstreamSelectedGeometryCandidateId().orElse(""));
        lines.add("geometry.retainedCandidateCount=" + evidence.retainedCandidates().size());
        lines.add("geometry.reasonCodes=" + reasonCodes(evidence.reasonCodes()));
        lines.add("geometry.meanErrorModules=" + summaryMetrics.meanErrorModules());
        lines.add("geometry.p95ErrorModules=" + summaryMetrics.p95ErrorModules());
        lines.add("geometry.maxErrorModules=" + summaryMetrics.maxErrorModules());
        lines.add("geometry.downstreamConflict=" + !downstreamConflictReasons.isEmpty());
        lines.add("geometry.downstreamConflictSummary="
                + (downstreamConflictReasons.isEmpty() ? "" : "ACCEPTED_GEOMETRY_NO_ACCEPTED_PAYLOAD"));
        lines.add("geometry.downstreamConflictReasonCodes=" + reasonCodes(downstreamConflictReasons));

        for (int index = 0; index < evidence.retainedCandidates().size(); index++) {
            GeometryCandidateEvidence candidate = evidence.retainedCandidates().get(index);
            String prefix = "geometry.retainedCandidate." + index;
            lines.add(prefix + ".rank=" + candidate.rank());
            lines.add(prefix + ".sourceImageId=" + candidate.candidateId().sourceImageId());
            lines.add(prefix + ".proposalCandidateId=" + candidate.candidateId().proposalCandidateId().orElse(""));
            lines.add(prefix + ".patternEvidenceId=" + candidate.candidateId().patternEvidenceId().orElse(""));
            lines.add(prefix + ".geometryCandidateId=" + candidate.candidateId().geometryCandidateId().orElse(""));
            lines.add(prefix + ".candidateId=" + candidate.candidateId().value());
            lines.add(prefix + ".status=" + candidate.status());
            lines.add(prefix + ".model=" + candidate.fitModelType());
            lines.add(prefix + ".score=" + candidate.score());
            lines.add(prefix + ".dominanceMargin=" + candidate.dominanceMargin());
            lines.add(prefix + ".retainedForSampling=" + candidate.retainedForSampling());
            lines.add(prefix + ".downstreamSelected=" + candidate.downstreamSelected());
            lines.add(prefix + ".observedPointCount=" + candidate.observedPointCount());
            lines.add(prefix + ".expectedPointCount=" + candidate.expectedPointCount());
            lines.add(prefix + ".matchedPointCount=" + candidate.matchedPointCount());
            lines.add(prefix + ".inlierCount=" + candidate.inlierCount());
            lines.add(prefix + ".outlierCount=" + candidate.outlierCount());
            lines.add(prefix + ".missingExpectedPointCount=" + candidate.missingExpectedPointCount());
            addReprojectionMetrics(lines, prefix + ".reprojection", candidate.reprojectionMetrics());
            lines.add(prefix + ".degeneracyFlags=" + textList(candidate.degeneracyFlags()));
            lines.add(prefix + ".reasonCodes=" + reasonCodes(candidate.reasonCodes()));
        }
    }

    private Optional<GeometryCandidateEvidence> selectedOrFirstGeometryCandidate(GeometryFitEvidence evidence) {
        Optional<String> selectedId = evidence.selectedGeometryCandidateId();
        if (selectedId.isPresent()) {
            String selected = selectedId.orElseThrow();
            for (GeometryCandidateEvidence candidate : evidence.retainedCandidates()) {
                if (candidate.candidateId().geometryCandidateId().filter(selected::equals).isPresent()) {
                    return Optional.of(candidate);
                }
            }
        }
        return evidence.retainedCandidates().stream().findFirst();
    }

    private List<CaptureMediaEvidenceReasonCode> geometryDownstreamConflictReasons(
            GeometryFitEvidence evidence,
            FrameInspection inspection
    ) {
        if (evidence.status() != GeometryFitStatus.ACCEPTED || inspection.decodedPayloadCount() > 0) {
            return List.of();
        }
        List<CaptureMediaEvidenceReasonCode> reasonCodes = new ArrayList<>();
        reasonCodes.add(CaptureMediaEvidenceReasonCode.DOWNSTREAM_CONFLICT);
        if (tileDecodeAttemptCount(inspection) == 0) {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.DOWNSTREAM_SAMPLING_FAILED);
            reasonCodes.add(CaptureMediaEvidenceReasonCode.TILE_DECODE_NOT_ATTEMPTED);
        } else {
            reasonCodes.add(CaptureMediaEvidenceReasonCode.DOWNSTREAM_TILE_VALIDATION_FAILED);
        }
        return reasonCodes;
    }

    private DownstreamSummary downstreamSummary(
            FrameInspection inspection,
            Optional<ModuleSamplingEvidence> sourceSamplingEvidence
    ) {
        int normalizedTileDecodeAttemptCount = tileDecodeAttemptCount(inspection);
        int sourceSamplingTileDecodeAttemptCount = sourceSamplingEvidence
                .map(ModuleSamplingEvidence::tileDecodeAttemptCount)
                .orElse(0);
        int normalizedValidatedTileCount = inspection.decodedPayloadCount();
        int sourceSamplingValidatedTileCount = sourceSamplingEvidence
                .map(ModuleSamplingEvidence::acceptedPayloadCount)
                .orElse(0);
        int validatedTileCount = Math.max(normalizedValidatedTileCount, sourceSamplingValidatedTileCount);
        boolean tileDecodeAttempted = normalizedTileDecodeAttemptCount > 0
                || sourceSamplingEvidence.map(ModuleSamplingEvidence::tileDecodeAttempted).orElse(false);
        String sourceSamplingFailureStages = sourceSamplingEvidence
                .map(evidence -> textList(evidence.tileDecodeFailureStages()))
                .orElse("");
        String failureStage = selectedFailureStage(inspection)
                .or(() -> firstText(sourceSamplingEvidence
                        .map(ModuleSamplingEvidence::tileDecodeFailureStages)
                        .orElse(List.of())))
                .orElse("");
        String summary;
        String restoreEligibility;
        boolean restoreEligible = validatedTileCount > 0;
        if (restoreEligible) {
            summary = "VALIDATED_TILE_AVAILABLE";
            restoreEligibility = "CANDIDATE_PAYLOAD_AVAILABLE";
        } else if (tileDecodeAttempted) {
            summary = "TILE_DECODE_ATTEMPTED_NO_VALIDATED_TILE";
            restoreEligibility = "RESTORE_GATES_NOT_MET";
        } else {
            summary = "TILE_DECODE_NOT_ATTEMPTED";
            restoreEligibility = "RESTORE_GATES_NOT_MET";
        }
        return new DownstreamSummary(
                summary,
                restoreEligible,
                restoreEligibility,
                tileDecodeAttempted,
                normalizedTileDecodeAttemptCount + sourceSamplingTileDecodeAttemptCount,
                normalizedTileDecodeAttemptCount,
                sourceSamplingTileDecodeAttemptCount,
                validatedTileCount,
                normalizedValidatedTileCount,
                sourceSamplingValidatedTileCount,
                failureStage,
                sourceSamplingFailureStages
        );
    }

    private Optional<String> firstText(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private void addReprojectionMetrics(List<String> lines, String prefix, ReprojectionMetrics metrics) {
        lines.add(prefix + ".meanErrorPixels=" + metrics.meanErrorPixels());
        lines.add(prefix + ".medianErrorPixels=" + metrics.medianErrorPixels());
        lines.add(prefix + ".p95ErrorPixels=" + metrics.p95ErrorPixels());
        lines.add(prefix + ".maxErrorPixels=" + metrics.maxErrorPixels());
        lines.add(prefix + ".meanErrorModules=" + metrics.meanErrorModules());
        lines.add(prefix + ".medianErrorModules=" + metrics.medianErrorModules());
        lines.add(prefix + ".p95ErrorModules=" + metrics.p95ErrorModules());
        lines.add(prefix + ".maxErrorModules=" + metrics.maxErrorModules());
    }

    private String reprojectionSummary(ReprojectionMetrics metrics) {
        return "meanModules:" + metrics.meanErrorModules()
                + ",p95Modules:" + metrics.p95ErrorModules()
                + ",maxModules:" + metrics.maxErrorModules();
    }

    private String sourceSamplingInt(
            Optional<ModuleSamplingEvidence> evidence,
            ToIntFunction<ModuleSamplingEvidence> extractor
    ) {
        return evidence
                .map(value -> Integer.toString(extractor.applyAsInt(value)))
                .orElse(ModuleSamplingStatus.NOT_AVAILABLE.name());
    }

    private boolean weakModule(ModuleEvidence module) {
        return module.status().name().equals("OUT_OF_BOUNDS")
                || module.status().name().equals("CLIPPED")
                || module.status().name().equals("UNREADABLE")
                || module.status().name().equals("AMBIGUOUS")
                || module.moduleConfidence() < 0.20d
                || module.geometryFootprintQuality() < 0.25d;
    }

    private int compareWeakModules(ModuleEvidence first, ModuleEvidence second) {
        int status = Integer.compare(weakStatusRank(first), weakStatusRank(second));
        if (status != 0) {
            return status;
        }
        int confidence = Double.compare(first.moduleConfidence(), second.moduleConfidence());
        if (confidence != 0) {
            return confidence;
        }
        int footprint = Double.compare(first.geometryFootprintQuality(), second.geometryFootprintQuality());
        if (footprint != 0) {
            return footprint;
        }
        int variance = Double.compare(second.colorVariance(), first.colorVariance());
        if (variance != 0) {
            return variance;
        }
        int y = Integer.compare(first.moduleY(), second.moduleY());
        return y != 0 ? y : Integer.compare(first.moduleX(), second.moduleX());
    }

    private int weakStatusRank(ModuleEvidence module) {
        return switch (module.status()) {
            case OUT_OF_BOUNDS -> 0;
            case CLIPPED -> 1;
            case UNREADABLE -> 2;
            case AMBIGUOUS -> 3;
            case READABLE -> 4;
        };
    }

    private void addMetricSummary(
            List<String> lines,
            String prefix,
            boolean available,
            Map<String, Double> metrics
    ) {
        if (!available) {
            lines.add(prefix + ".count=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add(prefix + ".min=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add(prefix + ".median=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add(prefix + ".mean=" + ModuleSamplingStatus.NOT_AVAILABLE);
            lines.add(prefix + ".max=" + ModuleSamplingStatus.NOT_AVAILABLE);
            return;
        }
        lines.add(prefix + ".count=" + metric(metrics, "count"));
        lines.add(prefix + ".min=" + metric(metrics, "min"));
        lines.add(prefix + ".median=" + metric(metrics, "median"));
        lines.add(prefix + ".mean=" + metric(metrics, "mean"));
        lines.add(prefix + ".max=" + metric(metrics, "max"));
    }

    private void addLocalRefinementEvidence(List<String> lines, LocalRefinementEvidence evidence) {
        lines.add("localRefinement.schemaVersion=" + evidence.schemaVersion());
        lines.add("localRefinement.sourceImageId=" + evidence.candidateId().sourceImageId());
        lines.add("localRefinement.proposalCandidateId="
                + evidence.candidateId().proposalCandidateId().orElse(""));
        lines.add("localRefinement.patternEvidenceId="
                + evidence.candidateId().patternEvidenceId().orElse(""));
        lines.add("localRefinement.geometryCandidateId="
                + evidence.candidateId().geometryCandidateId().orElse(""));
        lines.add("localRefinement.samplingCandidateId="
                + evidence.candidateId().samplingCandidateId().orElse(""));
        lines.add("localRefinement.refinementCandidateId="
                + evidence.candidateId().refinementCandidateId().orElse(""));
        lines.add("localRefinement.candidateId=" + evidence.candidateId().value());
        lines.add("localRefinement.status=" + evidence.status());
        lines.add("localRefinement.baseGeometryCandidateId=" + evidence.baseGeometryCandidateId());
        lines.add("localRefinement.baseSamplingCandidateId=" + evidence.baseSamplingCandidateId());
        lines.add("localRefinement.modelType=" + evidence.modelType());
        lines.add("localRefinement.gridColumns=" + evidence.gridColumns());
        lines.add("localRefinement.gridRows=" + evidence.gridRows());
        lines.add("localRefinement.maxLocalDisplacementModules=" + evidence.maxLocalDisplacementModules());
        lines.add("localRefinement.maxLocalDisplacementPixels=" + evidence.maxLocalDisplacementPixels());
        lines.add("localRefinement.smoothnessConstraint=" + evidence.smoothnessConstraint());
        lines.add("localRefinement.smoothnessScore=" + evidence.smoothnessScore());
        lines.add("localRefinement.regularizationScore=" + evidence.regularizationScore());
        lines.add("localRefinement.appliedToSampling=" + evidence.appliedToSampling());
        lines.add("localRefinement.reasonCodes=" + reasonCodes(evidence.reasonCodes()));
        addReprojectionMetrics(lines, "localRefinement.baseReprojection", evidence.baseReprojectionMetrics());
        lines.add("localRefinement.baseSampling.totalModuleCount="
                + metric(evidence.baseSamplingMetrics(), "totalModuleCount"));
        lines.add("localRefinement.baseSampling.sampledModuleCount="
                + metric(evidence.baseSamplingMetrics(), "sampledModuleCount"));
        lines.add("localRefinement.baseSampling.readableModuleCount="
                + metric(evidence.baseSamplingMetrics(), "readableModuleCount"));
        lines.add("localRefinement.baseSampling.ambiguousUnreadableRatio="
                + metric(evidence.baseSamplingMetrics(), "ambiguousUnreadableRatio"));
        lines.add("localRefinement.control.expectedCount="
                + metric(evidence.baseSamplingMetrics(), "control.expectedCount"));
        lines.add("localRefinement.control.observedCount="
                + metric(evidence.baseSamplingMetrics(), "control.observedCount"));
        lines.add("localRefinement.control.matchedCount="
                + metric(evidence.baseSamplingMetrics(), "control.matchedCount"));
        lines.add("localRefinement.control.inlierCount="
                + metric(evidence.baseSamplingMetrics(), "control.inlierCount"));
        lines.add("localRefinement.control.distributedQuadrantCount="
                + metric(evidence.baseSamplingMetrics(), "control.distributedQuadrantCount"));
        lines.add("localRefinement.residual.meanModules="
                + metric(evidence.baseSamplingMetrics(), "residual.meanModules"));
        lines.add("localRefinement.residual.p95Modules="
                + metric(evidence.baseSamplingMetrics(), "residual.p95Modules"));
        lines.add("localRefinement.residual.maxModules="
                + metric(evidence.baseSamplingMetrics(), "residual.maxModules"));
    }

    private double metric(Map<String, Double> metrics, String name) {
        return metrics.getOrDefault(name, 0.0d);
    }

    private int finderFeatureCount(PatternEvidence evidence) {
        int count = 0;
        for (PatternFeatureEvidence feature : evidence.features()) {
            if (feature.featureType() == PatternFeatureType.FINDER) {
                count++;
            }
        }
        return count;
    }

    private Optional<CoordinateObservationSource> observationSource(PatternEvidence evidence) {
        return evidence.features().stream()
                .map(PatternFeatureEvidence::observationSource)
                .findFirst();
    }

    private FinderRoleSummary finderRoleSummary(PatternEvidence evidence, FinderRole role) {
        int featureCount = 0;
        int matchedModuleCount = 0;
        int expectedModuleCount = 0;
        double confidenceSum = 0.0d;
        for (PatternFeatureEvidence feature : evidence.features()) {
            if (feature.featureType() == PatternFeatureType.FINDER
                    && feature.finderRole().filter(candidate -> candidate == role).isPresent()) {
                featureCount++;
                matchedModuleCount += feature.matchedModuleCount();
                expectedModuleCount += feature.expectedModuleCount();
                confidenceSum += feature.confidence();
            }
        }
        double confidence = featureCount == 0 ? 0.0d : confidenceSum / featureCount;
        return new FinderRoleSummary(featureCount, matchedModuleCount, expectedModuleCount, confidence);
    }

    private String reasonCodes(List<CaptureMediaEvidenceReasonCode> reasonCodes) {
        return reasonCodes.stream()
                .map(Enum::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String textList(List<String> values) {
        return values.stream()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String scoreMap(Map<String, Double> scores) {
        return scores.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
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

    private void addProfileAttempts(List<String> lines, FrameInspection inspection) {
        for (int index = 0; index < inspection.profileAttempts().size(); index++) {
            ProfileAttemptInspection attempt = inspection.profileAttempts().get(index);
            String prefix = "sampler.profileAttempt." + index;
            lines.add(prefix + ".layoutProfileId=" + attempt.layoutProfileId());
            lines.add(prefix + ".rows=" + attempt.rows());
            lines.add(prefix + ".cols=" + attempt.cols());
            lines.add(prefix + ".status=" + attempt.status());
            lines.add(prefix + ".decodedPayloadCount=" + attempt.decodedPayloadCount());
            lines.add(prefix + ".tileDecodeAttemptCount=" + attempt.tileDecodeAttemptCount());
            lines.add(prefix + ".slotValidation.layoutProfileMismatchCount="
                    + attempt.slotValidationLayoutProfileMismatchCount());
            lines.add(prefix + ".slotValidation.tileIndexMismatchCount="
                    + attempt.slotValidationTileIndexMismatchCount());
            lines.add(prefix + ".slotValidation.totalTilesMismatchCount="
                    + attempt.slotValidationTotalTilesMismatchCount());
            lines.add(prefix + ".decodedPayloadLayoutProfileId="
                    + attempt.decodedPayloadLayoutProfileId().orElse(""));
        }
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
            lines.add(slotPrefix + ".samplingEvidenceAlignmentStatus="
                    + slot.samplingEvidenceAlignmentStatus());
            lines.add(slotPrefix + ".candidateCount=" + slot.candidates().size());
            lines.add(slotPrefix + ".reason.noFinderAttemptCount="
                    + candidateStatusCount(slot, CandidateInspectionStatus.NO_FINDER));
            lines.add(slotPrefix + ".reason.paletteRejectedAttemptCount="
                    + candidateStatusCount(slot, CandidateInspectionStatus.PALETTE_REJECTED));
            lines.add(slotPrefix + ".reason.finderCandidateAttemptCount="
                    + candidateStatusCount(slot, CandidateInspectionStatus.FINDER_CANDIDATE));
            lines.add(slotPrefix + ".reason.tileOrEnvelopeRejectedAttemptCount="
                    + decodeStatusCount(slot, DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE));
            lines.add(slotPrefix + ".reason.postPaletteRejectedAttemptCount="
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
                addPhaseInspection(lines, candidatePrefix, candidate);
                lines.add(candidatePrefix + ".status=" + candidate.status());
                lines.add(candidatePrefix + ".decodeStatus=" + candidate.decodeStatus());
                lines.add(candidatePrefix + ".tileDecode.status=" + candidate.decodeStatus());
                lines.add(candidatePrefix + ".tileDecode.attempted="
                        + (candidate.decodeStatus() != DecodeInspectionStatus.NOT_ATTEMPTED));
                lines.add(candidatePrefix + ".envelopeValidation.status=" + candidate.decodeStatus());
                candidate.decodeFailureReason()
                        .ifPresent(reason -> lines.add(candidatePrefix + ".decodeFailureReason=" + reason));
                candidate.decodeFailureReason()
                        .ifPresent(reason -> lines.add(candidatePrefix + ".tileDecode.failureReason=" + reason));
                addTileDecodeFields(lines, candidatePrefix, candidate);
                candidate.samplingDiagnostics().forEach((name, value) ->
                        lines.add(candidatePrefix + ".diagnostic." + name + "=" + value));
                candidate.paletteConfidence()
                        .ifPresent(confidence -> addPaletteConfidence(lines, candidatePrefix, confidence));
            }
        }
    }

    private void addPhaseInspection(
            List<String> lines,
            String candidatePrefix,
            CandidateInspection candidate
    ) {
        PhaseInspection selected = candidate.selectedPhase();
        lines.add(candidatePrefix + ".phase.attemptedVariantCount=" + selected.attemptedVariantCount());
        lines.add(candidatePrefix + ".phase.variantCap=" + selected.variantCap());
        lines.add(candidatePrefix + ".phase.capReached=" + selected.capReached());
        addPhaseInspection(lines, candidatePrefix + ".phase.selected", selected);
        if (candidate.runnerUpPhase().isPresent()) {
            lines.add(candidatePrefix + ".phase.runnerUp.available=true");
            addPhaseInspection(lines, candidatePrefix + ".phase.runnerUp", candidate.runnerUpPhase().orElseThrow());
        } else {
            lines.add(candidatePrefix + ".phase.runnerUp.available=false");
        }
    }

    private void addPhaseInspection(List<String> lines, String prefix, PhaseInspection phase) {
        lines.add(prefix + ".rank=" + phase.rank());
        lines.add(prefix + ".generationOrdinal=" + phase.generationOrdinal());
        lines.add(prefix + ".source=" + phase.source());
        lines.add(prefix + ".evidenceSource=" + phase.evidenceSource().orElse(""));
        lines.add(prefix + ".outcome=" + phase.outcome());
        lines.add(prefix + ".failureStage=" + phase.failureStage().orElse(""));
        lines.add(prefix + ".failureDetail=" + phase.failureDetail().orElse(""));
        lines.add(prefix + ".moduleCenterOffsetXPx=" + phase.moduleCenterOffsetXPx());
        lines.add(prefix + ".moduleCenterOffsetYPx=" + phase.moduleCenterOffsetYPx());
        lines.add(prefix + ".moduleSizePx=" + phase.moduleSizePx());
        lines.add(prefix + ".moduleSizeScale=" + phase.moduleSizeScale());
        lines.add(prefix + ".finderCandidateCount=" + phase.finderCandidateCount());
        lines.add(prefix + ".finderExact=" + phase.finderExact());
        lines.add(prefix + ".borderStrength=" + phase.borderStrength());
        lines.add(prefix + ".paletteCalibrationConfidence=" + phase.paletteCalibrationConfidence());
        lines.add(prefix + ".rejectedSampleCount=" + phase.rejectedSampleCount());
    }

    private void addPaletteCalibration(List<String> lines, PaletteCalibrationInspection calibration) {
        lines.add("palette.calibration.enabled=" + calibration.enabled());
        lines.add("palette.calibration.confidence=" + calibration.confidence());
        lines.add("palette.calibration.observedColorCount=" + calibration.observedColorCount());
        lines.add("palette.calibration.fallbackReason=" + calibration.fallbackReason().orElse(""));
        lines.add("palette.calibration.maximumObservedRgbDistance="
                + calibration.maximumObservedRgbDistance());
        lines.add("palette.calibration.observedColor.entryCount=" + calibration.observedColors().size());
        for (PaletteColorCalibrationInspection color : calibration.observedColors()) {
            String prefix = "palette.calibration.observedColor." + color.paletteIndex();
            lines.add(prefix + ".expectedArgb=" + argb(color.expectedArgb()));
            lines.add(prefix + ".modelArgb=" + argb(color.modelArgb()));
            lines.add(prefix + ".sampleCount=" + color.sampleCount());
            lines.add(prefix + ".maximumObservedRgbDistance=" + color.maximumObservedRgbDistance());
            lines.add(prefix + ".confidence=" + color.confidence());
        }
    }

    private void addTileDecodeFields(
            List<String> lines,
            String candidatePrefix,
            CandidateInspection candidate
    ) {
        addSamplingAlias(lines, candidatePrefix, candidate, "layoutProfileId", "tileDecode.layoutProfileId");
        addSamplingAlias(lines, candidatePrefix, candidate, "tileIndex", "tileDecode.tileIndex");
        addSamplingAlias(lines, candidatePrefix, candidate, "sampledSideVersion", "tileDecode.sampledSideVersion");
        addSamplingAlias(lines, candidatePrefix, candidate, "sampledDimension", "tileDecode.sampledDimension");
        addSamplingAlias(lines, candidatePrefix, candidate, "finderExact", "finder.exact");
        addSamplingAlias(lines, candidatePrefix, candidate, "finderRecoverableCount", "finder.recoverableCount");
        addSamplingAlias(lines, candidatePrefix, candidate, "finderCanonicalized", "finder.canonicalized");
        addSamplingAlias(lines, candidatePrefix, candidate, "sampledMatrixSha256", "tileDecode.logicalTile.sha256");
        addSamplingAlias(lines, candidatePrefix, candidate, "sampledMatrixPrefix", "tileDecode.logicalTile.prefix");
        addSamplingAlias(lines, candidatePrefix, candidate, "sampledMatrixHistogram", "tileDecode.logicalTile.histogram");

        addDecodeAlias(lines, candidatePrefix, candidate, "failureStage", "tileDecode.failureStage");
        addDecodeAlias(lines, candidatePrefix, candidate, "postPalette.failureStage", "postPalette.failureStage");
        addDecodeAlias(lines, candidatePrefix, candidate, "maskPattern", "tileDecode.maskPattern");
        addDecodeAlias(lines, candidatePrefix, candidate, "encodedBytes", "tileDecode.encodedBytes");
        addDecodeAlias(lines, candidatePrefix, candidate, "logicalCapacityBytes", "tileDecode.logicalCapacityBytes");
        addDecodeAlias(lines, candidatePrefix, candidate, "parityBytes", "tileDecode.parityBytes");
        addDecodeAlias(lines, candidatePrefix, candidate, "headerBytes", "tileDecode.headerBytes");
        addDecodeAlias(lines, candidatePrefix, candidate, "deinterleavedBytes", "tileDecode.deinterleavedBytes");
        addDecodeAlias(lines, candidatePrefix, candidate, "parsedHeaderHex", "tileDecode.parsedHeaderHex");
        addDecodeAlias(lines, candidatePrefix, candidate, "formatVersion", "tileDecode.formatVersion");
        addDecodeAlias(lines, candidatePrefix, candidate, "payloadLength", "tileDecode.headerPayloadLength");
        addDecodeAlias(lines, candidatePrefix, candidate, "framedLength", "tileDecode.framedLength");
        addDecodeAlias(lines, candidatePrefix, candidate, "expectedEncodedBytes", "tileDecode.expectedEncodedBytes");
        addDecodeAlias(lines, candidatePrefix, candidate, "storedPayloadCrc32c", "tileDecode.storedPayloadCrc32c");
        addDecodeAlias(lines, candidatePrefix, candidate, "calculatedPayloadCrc32c", "tileDecode.calculatedPayloadCrc32c");
        addDecodeAlias(lines, candidatePrefix, candidate, "decodedPayload.layoutProfileId",
                "decodedPayload.layoutProfileId");
        addDecodeAlias(lines, candidatePrefix, candidate, "decodedPayload.tileIndex", "decodedPayload.tileIndex");
        addDecodeAlias(lines, candidatePrefix, candidate, "decodedPayload.totalTiles", "decodedPayload.totalTiles");
        addDecodeAlias(lines, candidatePrefix, candidate, "slotValidation.stage", "slotValidation.stage");
        addDecodeAlias(lines, candidatePrefix, candidate, "slotValidation.expectedLayoutProfileId",
                "slotValidation.expectedLayoutProfileId");
        addDecodeAlias(lines, candidatePrefix, candidate, "slotValidation.actualLayoutProfileId",
                "slotValidation.actualLayoutProfileId");
        addDecodeAlias(lines, candidatePrefix, candidate, "slotValidation.expectedTileIndex",
                "slotValidation.expectedTileIndex");
        addDecodeAlias(lines, candidatePrefix, candidate, "slotValidation.actualTileIndex",
                "slotValidation.actualTileIndex");
        addDecodeAlias(lines, candidatePrefix, candidate, "slotValidation.expectedTotalTiles",
                "slotValidation.expectedTotalTiles");
        addDecodeAlias(lines, candidatePrefix, candidate, "slotValidation.actualTotalTiles",
                "slotValidation.actualTotalTiles");
        encodedLengthDelta(candidate)
                .ifPresent(value -> lines.add(candidatePrefix + ".tileDecode.encodedLengthDelta=" + value));
    }

    private void addSamplingAlias(
            List<String> lines,
            String candidatePrefix,
            CandidateInspection candidate,
            String diagnosticName,
            String sidecarName
    ) {
        String value = candidate.samplingDiagnostics().get(diagnosticName);
        if (value != null && !value.isBlank()) {
            lines.add(candidatePrefix + "." + sidecarName + "=" + value);
        }
    }

    private void addDecodeAlias(
            List<String> lines,
            String candidatePrefix,
            CandidateInspection candidate,
            String diagnosticName,
            String sidecarName
    ) {
        String value = candidate.decodeDiagnostics().get(diagnosticName);
        if (value != null && !value.isBlank()) {
            lines.add(candidatePrefix + "." + sidecarName + "=" + value);
        }
    }

    private Optional<Integer> encodedLengthDelta(CandidateInspection candidate) {
        Optional<Integer> expectedEncodedBytes = intDiagnostic(candidate.decodeDiagnostics(), "expectedEncodedBytes");
        Optional<Integer> encodedBytes = intDiagnostic(candidate.decodeDiagnostics(), "encodedBytes");
        if (expectedEncodedBytes.isEmpty() || encodedBytes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expectedEncodedBytes.orElseThrow() - encodedBytes.orElseThrow());
    }

    private Optional<Integer> intDiagnostic(Map<String, String> diagnostics, String name) {
        try {
            String value = diagnostics.get(name);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
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

    private int phaseAttemptedVariantCount(FrameInspection inspection) {
        int attemptCount = 0;
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                attemptCount += candidate.selectedPhase().attemptedVariantCount();
            }
        }
        return attemptCount;
    }

    private int phaseVariantCap(FrameInspection inspection) {
        int variantCap = 0;
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                variantCap = Math.max(variantCap, candidate.selectedPhase().variantCap());
            }
        }
        return variantCap;
    }

    private boolean phaseCapReached(FrameInspection inspection) {
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.selectedPhase().capReached()) {
                    return true;
                }
            }
        }
        return false;
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

    private int postPaletteRejectedAttemptCount(FrameInspection inspection) {
        return envelopeRejectedAttemptCount(inspection);
    }

    private int partialRejectedSlotCount(FrameInspection inspection) {
        int count = 0;
        for (SlotInspection slot : inspection.slots()) {
            if (partialRejectedSlot(slot)) {
                count++;
            }
        }
        return count;
    }

    private int partialUndecodableSlotCount(FrameInspection inspection) {
        int count = 0;
        for (SlotInspection slot : inspection.slots()) {
            if (partialUndecodableSlot(slot)) {
                count++;
            }
        }
        return count;
    }

    private boolean partialRejectedSlot(SlotInspection slot) {
        if (slotHasAcceptedPayload(slot)) {
            return false;
        }
        if (slot.borderStatus() == BorderInspectionStatus.PALETTE_REJECTED && slot.interiorContent()) {
            return true;
        }
        return slot.candidates().stream()
                .anyMatch(candidate -> candidate.status() == CandidateInspectionStatus.PALETTE_REJECTED);
    }

    private boolean partialUndecodableSlot(SlotInspection slot) {
        return !slotHasAcceptedPayload(slot)
                && !partialRejectedSlot(slot)
                && slot.borderStatus() == BorderInspectionStatus.SIGNATURE;
    }

    private boolean slotHasAcceptedPayload(SlotInspection slot) {
        return slot.candidates().stream()
                .anyMatch(candidate -> candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD);
    }

    private boolean cameraDerived(NormalizedCaptureFrame frame) {
        return frame.qualityMetrics().measured(frame.qualityMetrics().frameCoverageRatio())
                && frame.qualityMetrics().frameCoverageRatio() < 0.99d;
    }

    private Optional<CaptureMediaDiagnosticCode> selectedPublicDiagnosticCode(FrameInspection inspection) {
        if (inspection.decodedPayloadCount() > 0) {
            return Optional.empty();
        }
        if (postPaletteRejectedAttemptCount(inspection) > 0) {
            return Optional.of(CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE);
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

    private Optional<String> selectedFailureStage(FrameInspection inspection) {
        if (inspection.decodedPayloadCount() > 0) {
            return Optional.empty();
        }
        Optional<String> postPaletteStage = selectedPostPaletteFailureStage(inspection);
        if (postPaletteStage.isPresent()) {
            return postPaletteStage;
        }
        if (inspection.paletteRejectedAttemptCount() > 0
                || inspection.slots().stream()
                .anyMatch(slot -> slot.borderStatus() == BorderInspectionStatus.PALETTE_REJECTED)) {
            return Optional.of("PALETTE");
        }
        if (inspection.candidateAttemptCount() > 0 || inspection.noFinderAttemptCount() > 0) {
            return Optional.of("FINDER");
        }
        return Optional.of("SCREEN_OR_FRAME_NOT_FOUND");
    }

    private Optional<String> selectedPostPaletteFailureStage(FrameInspection inspection) {
        for (SlotInspection slot : inspection.slots()) {
            for (CandidateInspection candidate : slot.candidates()) {
                if (candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE) {
                    Optional<String> stage = postPaletteFailureStage(candidate);
                    if (stage.isPresent()) {
                        return stage;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> postPaletteFailureStage(CandidateInspection candidate) {
        String explicitStage = candidate.decodeDiagnostics().get("postPalette.failureStage");
        if (explicitStage != null && !explicitStage.isBlank()) {
            return Optional.of(explicitStage);
        }
        String slotStage = candidate.decodeDiagnostics().get("slotValidation.stage");
        if (slotStage != null && !slotStage.isBlank()) {
            return Optional.of(slotStage);
        }
        String envelopeStage = candidate.decodeDiagnostics().get("envelopeValidation.stage");
        if (envelopeStage != null && !envelopeStage.isBlank()) {
            return Optional.of(envelopeStage);
        }
        if (candidate.decodeDiagnostics().containsKey("failureStage")) {
            return Optional.of("TILE_DECODE");
        }
        return Optional.empty();
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

    private record FinderRoleSummary(
            int featureCount,
            int matchedModuleCount,
            int expectedModuleCount,
            double confidence
    ) {
    }

    private record DownstreamSummary(
            String summary,
            boolean restoreEligible,
            String restoreEligibility,
            boolean tileDecodeAttempted,
            int tileDecodeAttemptCount,
            int normalizedTileDecodeAttemptCount,
            int sourceSamplingTileDecodeAttemptCount,
            int validatedTileCount,
            int normalizedValidatedTileCount,
            int sourceSamplingValidatedTileCount,
            String failureStage,
            String sourceSamplingFailureStages
    ) {
        private DownstreamSummary {
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("summary must not be blank");
            }
            if (restoreEligibility == null || restoreEligibility.isBlank()) {
                throw new IllegalArgumentException("restoreEligibility must not be blank");
            }
            if (tileDecodeAttemptCount < 0
                    || normalizedTileDecodeAttemptCount < 0
                    || sourceSamplingTileDecodeAttemptCount < 0
                    || validatedTileCount < 0
                    || normalizedValidatedTileCount < 0
                    || sourceSamplingValidatedTileCount < 0) {
                throw new IllegalArgumentException("downstream counts must be non-negative");
            }
            Objects.requireNonNull(failureStage, "failureStage must not be null");
            Objects.requireNonNull(sourceSamplingFailureStages, "sourceSamplingFailureStages must not be null");
        }
    }

    private record DecodedCandidateSummary(
            String identity,
            int slotIndex,
            int sideVersion,
            double moduleConfidence,
            double paletteMinimumConfidence,
            int moduleOffsetXPx,
            int moduleOffsetYPx
    ) {
        private DecodedCandidateSummary {
            if (identity == null || identity.isBlank()) {
                throw new IllegalArgumentException("identity must not be blank");
            }
            if (slotIndex < 0 || sideVersion < 0) {
                throw new IllegalArgumentException("decoded candidate indexes must be non-negative");
            }
            if (!Double.isFinite(moduleConfidence)
                    || moduleConfidence < 0.0d
                    || moduleConfidence > 1.0d
                    || !Double.isFinite(paletteMinimumConfidence)
                    || paletteMinimumConfidence < 0.0d
                    || paletteMinimumConfidence > 1.0d) {
                throw new IllegalArgumentException("decoded candidate confidence values must be unit scores");
            }
        }
    }
}
