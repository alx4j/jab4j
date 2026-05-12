package com.alx4j.jab4j.reader.capture.media;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.CaptureReceiverRequest;
import com.alx4j.jab4j.reader.capture.CaptureReceiverResult;
import com.alx4j.jab4j.reader.capture.CaptureReceiverService;
import com.alx4j.jab4j.reader.capture.CaptureReceiverStatus;
import com.alx4j.jab4j.reader.capture.CaptureReceiverSummary;
import com.alx4j.jab4j.reader.capture.media.input.CaptureMediaInputIntake;
import com.alx4j.jab4j.reader.capture.media.input.MediaIntakeResult;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

/**
 * Public receiver facade for evaluating or restoring MVP-3 capture media.
 *
 * <p>This slice is intentionally conservative. It accepts PNG sources that already match supported rendered frame
 * dimensions or contain a clean generated axis-aligned rendered-frame inset, then delegates normalized candidates to
 * the existing clean capture receiver. It reports stable diagnostics for HEIC/HEIF, direct video containers,
 * unreadable media, and arbitrary photos that cannot yet be normalized.</p>
 */
public final class CaptureMediaReceiverService {

    private final CaptureMediaInputIntake mediaInputIntake;
    private final CaptureMediaFrameNormalizer frameNormalizer;
    private final Function<CaptureReceiverRequest, CaptureReceiverResult> cleanCaptureReceiver;

    /**
     * Creates a media receiver using default PNG intake, conservative normalization, and clean capture restore.
     */
    public CaptureMediaReceiverService() {
        this(
                new CaptureMediaInputIntake(),
                new CaptureMediaFrameNormalizer(),
                new CaptureReceiverService()::receive
        );
    }

    /**
     * Creates a media receiver with explicit collaborators for focused tests.
     *
     * @param mediaInputIntake media source intake
     * @param frameNormalizer conservative frame normalizer
     * @param cleanCaptureReceiver existing clean PNG capture receiver
     */
    public CaptureMediaReceiverService(
            CaptureMediaInputIntake mediaInputIntake,
            CaptureMediaFrameNormalizer frameNormalizer,
            Function<CaptureReceiverRequest, CaptureReceiverResult> cleanCaptureReceiver
    ) {
        this.mediaInputIntake = Objects.requireNonNull(mediaInputIntake, "mediaInputIntake must not be null");
        this.frameNormalizer = Objects.requireNonNull(frameNormalizer, "frameNormalizer must not be null");
        this.cleanCaptureReceiver = Objects.requireNonNull(cleanCaptureReceiver, "cleanCaptureReceiver must not be null");
    }

    /**
     * Evaluates or restores media input according to whether the request includes an output directory.
     *
     * @param request media receiver request
     * @return media receiver result
     */
    public CaptureMediaReceiverResult receive(CaptureMediaReceiverRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return request.restoreRequested() ? restore(request) : evaluate(request);
    }

    /**
     * Evaluates media input without filesystem restore side effects.
     *
     * @param request media receiver request
     * @return eligible, incomplete, or rejected media receiver result
     */
    public CaptureMediaReceiverResult evaluate(CaptureMediaReceiverRequest request) {
        return process(request, false);
    }

    /**
     * Evaluates media input and invokes clean capture restore when the normalized set is complete enough.
     *
     * @param request media receiver request with output directory
     * @return restored, incomplete, rejected, or restore-failed media receiver result
     */
    public CaptureMediaReceiverResult restore(CaptureMediaReceiverRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.outputDirectory().isEmpty()) {
            throw new IllegalArgumentException("restore request must include outputDirectory");
        }
        return process(request, true);
    }

    private CaptureMediaReceiverResult process(CaptureMediaReceiverRequest request, boolean restoreRequested) {
        Objects.requireNonNull(request, "request must not be null");
        MediaIntakeResult intakeResult = mediaInputIntake.read(request);
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>(intakeResult.diagnostics());
        List<NormalizedCaptureFrame> normalizedFrames = normalizeReadableFrames(intakeResult, diagnostics);
        CaptureMediaSummary mediaSummary = mediaSummary(intakeResult, normalizedFrames, 0, 0);

        if (diagnostics.stream().anyMatch(CaptureMediaDiagnostic::blocking)) {
            return failedFromMediaDiagnostics(mediaSummary, diagnostics);
        }
        if (normalizedFrames.isEmpty()) {
            diagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                    CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                    CaptureMediaDiagnosticSeverity.ERROR,
                    "Capture media input does not contain any normalized frame candidates"
            ));
            return CaptureMediaReceiverResult.incomplete(
                    mediaSummary,
                    diagnostics,
                    "Capture media input is missing required unique frame content"
            );
        }

        CaptureReceiverRequest cleanCaptureRequest;
        NormalizedCaptureSources normalizedSources;
        try {
            normalizedSources = writeNormalizedSources(normalizedFrames);
        } catch (IOException exception) {
            diagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                    CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                    CaptureMediaDiagnosticSeverity.ERROR,
                    "Normalized capture media frames could not be staged for decode"
            ));
            return CaptureMediaReceiverResult.rejected(
                    mediaSummary,
                    diagnostics,
                    "Capture media input could not be normalized for decode"
            );
        }
        try {
            cleanCaptureRequest = restoreRequested
                    ? CaptureReceiverRequest.restore(normalizedSources.files(), request.outputDirectory().orElseThrow())
                    : CaptureReceiverRequest.evaluateOnly(normalizedSources.files());
            CaptureReceiverResult captureResult = cleanCaptureReceiver.apply(cleanCaptureRequest);
            return fromCleanCaptureResult(request.sourceKind(), Objects.requireNonNull(
                    captureResult,
                    "clean capture receiver result must not be null"
            ), normalizedSources.framesByCleanSourceId());
        } finally {
            deleteNormalizedSources(normalizedSources.directory());
        }
    }

    private List<NormalizedCaptureFrame> normalizeReadableFrames(
            MediaIntakeResult intakeResult,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        List<NormalizedCaptureFrame> normalizedFrames = new ArrayList<>();
        intakeResult.readableFrames().forEach(frame -> {
            MediaNormalizationResult normalizationResult = frameNormalizer.normalize(frame);
            normalizationResult.frame().ifPresent(normalizedFrames::add);
            diagnostics.addAll(normalizationResult.diagnostics());
        });
        return List.copyOf(normalizedFrames);
    }

    private CaptureMediaReceiverResult failedFromMediaDiagnostics(
            CaptureMediaSummary summary,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        if (diagnostics.stream()
                .filter(CaptureMediaDiagnostic::blocking)
                .allMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME)) {
            return CaptureMediaReceiverResult.incomplete(
                    summary,
                    diagnostics,
                    "Capture media input is missing required unique frame content"
            );
        }
        return CaptureMediaReceiverResult.rejected(summary, diagnostics, failureMessage(diagnostics));
    }

    private String failureMessage(List<CaptureMediaDiagnostic> diagnostics) {
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER)) {
            return "Direct .mov/.mp4 capture media input is unsupported";
        }
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT)) {
            return "HEIC/HEIF capture media input is unsupported";
        }
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND)) {
            return "No recoverable capture media frames were found";
        }
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.UNREADABLE_MEDIA)) {
            return "Capture media input could not be read";
        }
        return "Capture media input cannot be used by the receiver";
    }

    private CaptureMediaReceiverResult fromCleanCaptureResult(
            CaptureMediaSourceKind sourceKind,
            CaptureReceiverResult captureResult,
            Map<String, NormalizedCaptureFrame> framesByCleanSourceId
    ) {
        CaptureMediaSummary summary = fromCaptureSummary(captureResult.summary());
        List<CaptureMediaDiagnostic> diagnostics = captureResult.diagnostics().stream()
                .map(diagnostic -> mapCaptureDiagnostic(sourceKind, diagnostic, captureResult.status(), framesByCleanSourceId))
                .toList();

        return switch (captureResult.status()) {
            case RESTORED -> CaptureMediaReceiverResult.restored(
                    summary,
                    diagnostics,
                    captureResult.restoreResult().orElseThrow()
            );
            case ELIGIBLE -> CaptureMediaReceiverResult.eligible(summary, diagnostics, captureResult.message());
            case INCOMPLETE -> CaptureMediaReceiverResult.incomplete(summary, diagnostics, captureResult.message());
            case REJECTED -> CaptureMediaReceiverResult.rejected(summary, diagnostics, captureResult.message());
            case RESTORE_FAILED -> CaptureMediaReceiverResult.restoreFailed(
                    summary,
                    diagnostics,
                    captureResult.restoreResult(),
                    captureResult.message()
            );
        };
    }

    private CaptureMediaDiagnostic mapCaptureDiagnostic(
            CaptureMediaSourceKind sourceKind,
            CaptureFrameDiagnostic diagnostic,
            CaptureReceiverStatus captureStatus,
            Map<String, NormalizedCaptureFrame> framesByCleanSourceId
    ) {
        CaptureMediaDiagnosticSeverity severity = severityFor(diagnostic.code(), captureStatus);
        Optional<NormalizedCaptureFrame> normalizedFrame = diagnostic.sourceId()
                .map(framesByCleanSourceId::get);
        Optional<String> sourceId = normalizedFrame
                .map(NormalizedCaptureFrame::sourceId)
                .or(() -> diagnostic.sourceId());
        Optional<Integer> callerOrder = normalizedFrame
                .map(NormalizedCaptureFrame::callerOrder)
                .or(() -> diagnostic.callerOrder());
        return new CaptureMediaDiagnostic(
                mapDiagnosticCode(diagnostic.code()),
                severity,
                severity.blocksRestore(),
                diagnostic.frameScoped()
                        ? Optional.of(normalizedFrame.map(NormalizedCaptureFrame::sourceKind).orElse(sourceKind))
                        : Optional.empty(),
                diagnostic.frameScoped() ? sourceId : Optional.empty(),
                diagnostic.frameScoped() ? callerOrder : Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                normalizedFrame.map(this::qualityMetricMap).orElse(Map.of()),
                diagnostic.message()
        );
    }

    private CaptureMediaDiagnosticSeverity severityFor(
            CaptureDiagnosticCode code,
            CaptureReceiverStatus captureStatus
    ) {
        if (code == CaptureDiagnosticCode.DUPLICATE_EQUIVALENT_FRAME) {
            return CaptureMediaDiagnosticSeverity.WARNING;
        }
        return captureStatus.terminalFailure()
                ? CaptureMediaDiagnosticSeverity.ERROR
                : CaptureMediaDiagnosticSeverity.WARNING;
    }

    private CaptureMediaDiagnosticCode mapDiagnosticCode(CaptureDiagnosticCode code) {
        return switch (code) {
            case UNSUPPORTED_FORMAT -> CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT;
            case UNREADABLE_IMAGE -> CaptureMediaDiagnosticCode.UNREADABLE_MEDIA;
            case UNSUPPORTED_DIMENSIONS, NO_CANDIDATE_BARCODE_CONTENT ->
                    CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND;
            case DUPLICATE_EQUIVALENT_FRAME, DUPLICATE_CONFLICTING_FRAME ->
                    CaptureMediaDiagnosticCode.DUPLICATE_MEDIA_FRAME;
            case CORRUPTED_OR_UNREADABLE_TILE_CONTENT -> CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT;
            case MISSING_REQUIRED_CONTENT -> CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME;
            case INCONSISTENT_SESSION_CONTENT -> CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS;
            case RESTORE_FAILURE -> CaptureMediaDiagnosticCode.RESTORE_FAILURE;
        };
    }

    private CaptureMediaSummary mediaSummary(
            MediaIntakeResult intakeResult,
            List<NormalizedCaptureFrame> normalizedFrames,
            int decodedTileCount,
            long restoredFileCount
    ) {
        int acceptedCandidateCount = normalizedFrames.size();
        int rejectedCandidateCount = Math.min(
                intakeResult.submittedSourceCount(),
                Math.max(0, intakeResult.submittedSourceCount() - acceptedCandidateCount)
        );
        return new CaptureMediaSummary(
                intakeResult.submittedSourceCount(),
                intakeResult.readableFrames().size(),
                acceptedCandidateCount,
                rejectedCandidateCount,
                0,
                0,
                acceptedCandidateCount,
                decodedTileCount,
                restoredFileCount
        );
    }

    private CaptureMediaSummary fromCaptureSummary(CaptureReceiverSummary summary) {
        return new CaptureMediaSummary(
                summary.submittedFrameCount(),
                summary.readableFrameCount(),
                summary.acceptedCandidateCount(),
                summary.rejectedFrameCount(),
                summary.uncertainFrameCount(),
                summary.duplicateFrameCount(),
                summary.acceptedCandidateCount(),
                summary.decodedTileCount(),
                summary.restoredFileCount()
        );
    }

    private NormalizedCaptureSources writeNormalizedSources(List<NormalizedCaptureFrame> normalizedFrames) throws IOException {
        Path directory = Files.createTempDirectory("jab4j-capture-media-normalized-");
        try {
            List<Path> files = new ArrayList<>(normalizedFrames.size());
            Map<String, NormalizedCaptureFrame> framesByCleanSourceId = new LinkedHashMap<>();
            for (int index = 0; index < normalizedFrames.size(); index++) {
                NormalizedCaptureFrame frame = normalizedFrames.get(index);
                Path path = directory.resolve("normalized-frame-%04d.png".formatted(index))
                        .toAbsolutePath()
                        .normalize();
                writeNormalizedPng(frame, path);
                files.add(path);
                framesByCleanSourceId.put(path.toString(), frame);
            }
            return new NormalizedCaptureSources(directory, List.copyOf(files), Map.copyOf(framesByCleanSourceId));
        } catch (IOException | RuntimeException exception) {
            deleteNormalizedSources(directory);
            throw exception;
        }
    }

    private void writeNormalizedPng(NormalizedCaptureFrame frame, Path path) throws IOException {
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
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG ImageIO writer is available");
        }
    }

    private void deleteNormalizedSources(Path directory) {
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    // Temporary normalized sources are best-effort cleanup after synchronous decode.
                }
            });
        } catch (IOException exception) {
            // Temporary normalized sources are best-effort cleanup after synchronous decode.
        }
    }

    private Map<String, Double> qualityMetricMap(NormalizedCaptureFrame frame) {
        CaptureMediaQualityMetrics metrics = frame.qualityMetrics();
        Map<String, Double> values = new LinkedHashMap<>();
        putMeasured(values, "frameCoverageRatio", metrics.frameCoverageRatio(), metrics);
        putMeasured(values, "skewScore", metrics.skewScore(), metrics);
        putMeasured(values, "blurScore", metrics.blurScore(), metrics);
        putMeasured(values, "glareScore", metrics.glareScore(), metrics);
        putMeasured(values, "exposureScore", metrics.exposureScore(), metrics);
        putMeasured(values, "colorDistanceScore", metrics.colorDistanceScore(), metrics);
        return Map.copyOf(values);
    }

    private void putMeasured(
            Map<String, Double> values,
            String metricName,
            double metricValue,
            CaptureMediaQualityMetrics metrics
    ) {
        if (metrics.measured(metricValue)) {
            values.put(metricName, metricValue);
        }
    }

    private record NormalizedCaptureSources(
            Path directory,
            List<Path> files,
            Map<String, NormalizedCaptureFrame> framesByCleanSourceId
    ) {
    }
}
