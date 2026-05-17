package com.alx4j.jab4j.reader.capture.media;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.CaptureReceiverRequest;
import com.alx4j.jab4j.reader.capture.CaptureReceiverResult;
import com.alx4j.jab4j.reader.capture.decode.CaptureAssemblyResult;
import com.alx4j.jab4j.reader.capture.decode.CaptureFrameSetAssembler;
import com.alx4j.jab4j.reader.capture.decode.CaptureSessionContent;
import com.alx4j.jab4j.reader.capture.media.debug.CaptureMediaCandidateDebugExporter;
import com.alx4j.jab4j.reader.capture.media.decode.CaptureMediaFrameDecodeResult;
import com.alx4j.jab4j.reader.capture.media.decode.CaptureMediaFrameDecoder;
import com.alx4j.jab4j.reader.capture.media.input.CaptureMediaInputIntake;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.MediaIntakeResult;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler;
import com.alx4j.jab4j.reader.restore.ReaderRestoreRequest;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;
import com.alx4j.jab4j.reader.restore.ReaderRestoreService;
import com.alx4j.jab4j.reader.restore.ReaderRestoreStatus;

/**
 * Public receiver facade for evaluating or restoring MVP-3 capture media.
 *
 * <p>The receiver reads supported still-image media, normalizes accepted candidates, decodes normalized frames with the
 * media-owned tolerant sampler, assembles decoded frame identities, and invokes the source-neutral reader restore
 * service only after decoded content is complete and internally consistent.</p>
 */
public final class CaptureMediaReceiverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureMediaReceiverService.class);

    private final CaptureMediaInputIntake mediaInputIntake;
    private final CaptureMediaFrameNormalizer frameNormalizer;
    private final CaptureMediaFrameDecoder mediaFrameDecoder;
    private final CaptureFrameSetAssembler frameSetAssembler;
    private final ReaderRestoreService readerRestoreService;
    private final CaptureMediaCandidateDebugExporter debugExporter;

    /**
     * Creates a media receiver using default still-image intake, normalization, media decode, assembly, and restore
     * services.
     */
    public CaptureMediaReceiverService() {
        this(new CaptureMediaTilePayloadSampler());
    }

    private CaptureMediaReceiverService(CaptureMediaTilePayloadSampler tilePayloadSampler) {
        this(
                new CaptureMediaInputIntake(),
                new CaptureMediaFrameNormalizer(),
                new CaptureMediaFrameDecoder(tilePayloadSampler),
                new CaptureFrameSetAssembler(),
                new ReaderRestoreService(),
                new CaptureMediaCandidateDebugExporter(tilePayloadSampler)
        );
    }

    /**
     * Creates a media receiver with legacy clean-capture receiver compatibility.
     *
     * <p>The media receiver now owns normalized-frame decode and restore orchestration directly. The clean receiver
     * argument is still validated so existing focused tests or callers using this constructor keep their contract.</p>
     *
     * @param mediaInputIntake media source intake
     * @param frameNormalizer conservative frame normalizer
     * @param cleanCaptureReceiver legacy clean PNG capture receiver
     */
    public CaptureMediaReceiverService(
            CaptureMediaInputIntake mediaInputIntake,
            CaptureMediaFrameNormalizer frameNormalizer,
            Function<CaptureReceiverRequest, CaptureReceiverResult> cleanCaptureReceiver
    ) {
        this(
                mediaInputIntake,
                frameNormalizer,
                requireLegacyReceiver(cleanCaptureReceiver),
                new CaptureFrameSetAssembler(),
                new ReaderRestoreService(),
                new CaptureMediaCandidateDebugExporter()
        );
    }

    private static CaptureMediaFrameDecoder requireLegacyReceiver(
            Function<CaptureReceiverRequest, CaptureReceiverResult> cleanCaptureReceiver
    ) {
        Objects.requireNonNull(cleanCaptureReceiver, "cleanCaptureReceiver must not be null");
        return new CaptureMediaFrameDecoder();
    }

    /**
     * Creates a media receiver with explicit collaborators for focused tests.
     *
     * @param mediaInputIntake media source intake
     * @param frameNormalizer conservative frame normalizer
     * @param mediaFrameDecoder normalized media frame decoder
     * @param frameSetAssembler decoded frame-set assembler
     * @param readerRestoreService source-neutral restore service
     */
    public CaptureMediaReceiverService(
            CaptureMediaInputIntake mediaInputIntake,
            CaptureMediaFrameNormalizer frameNormalizer,
            CaptureMediaFrameDecoder mediaFrameDecoder,
            CaptureFrameSetAssembler frameSetAssembler,
            ReaderRestoreService readerRestoreService
    ) {
        this(
                mediaInputIntake,
                frameNormalizer,
                mediaFrameDecoder,
                frameSetAssembler,
                readerRestoreService,
                new CaptureMediaCandidateDebugExporter()
        );
    }

    /**
     * Creates a media receiver with explicit collaborators, including candidate debug export.
     *
     * @param mediaInputIntake media source intake
     * @param frameNormalizer conservative frame normalizer
     * @param mediaFrameDecoder normalized media frame decoder
     * @param frameSetAssembler decoded frame-set assembler
     * @param readerRestoreService source-neutral restore service
     * @param debugExporter normalized candidate debug exporter
     */
    public CaptureMediaReceiverService(
            CaptureMediaInputIntake mediaInputIntake,
            CaptureMediaFrameNormalizer frameNormalizer,
            CaptureMediaFrameDecoder mediaFrameDecoder,
            CaptureFrameSetAssembler frameSetAssembler,
            ReaderRestoreService readerRestoreService,
            CaptureMediaCandidateDebugExporter debugExporter
    ) {
        this.mediaInputIntake = Objects.requireNonNull(mediaInputIntake, "mediaInputIntake must not be null");
        this.frameNormalizer = Objects.requireNonNull(frameNormalizer, "frameNormalizer must not be null");
        this.mediaFrameDecoder = Objects.requireNonNull(mediaFrameDecoder, "mediaFrameDecoder must not be null");
        this.frameSetAssembler = Objects.requireNonNull(frameSetAssembler, "frameSetAssembler must not be null");
        this.readerRestoreService = Objects.requireNonNull(readerRestoreService, "readerRestoreService must not be null");
        this.debugExporter = Objects.requireNonNull(debugExporter, "debugExporter must not be null");
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
     * Evaluates media input and invokes reader restore when decoded content is complete enough.
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
        long startedAtNanos = System.nanoTime();
        String backendId = frameNormalizer.normalizationBackendId();
        String backendVersion = frameNormalizer.normalizationBackendVersion().orElse("");
        LOGGER.info(
                "Capture media receiver starting mode={} sourceKind={} inputSources={} backendId={} debugOutputRequested={}",
                restoreRequested ? "restore" : "evaluate",
                request.sourceKind(),
                request.inputSources().size(),
                backendId,
                request.debugOutputDirectory().isPresent()
        );
        CaptureMediaReceiverResult result = processInternal(request, restoreRequested, backendId, backendVersion);
        logCompletion(request, result, backendId, startedAtNanos);
        return result;
    }

    private CaptureMediaReceiverResult processInternal(
            CaptureMediaReceiverRequest request,
            boolean restoreRequested,
            String backendId,
            String backendVersion
    ) {
        Objects.requireNonNull(request, "request must not be null");
        MediaIntakeResult intakeResult = mediaInputIntake.read(request);
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>(intakeResult.diagnostics());
        List<NormalizedCaptureFrame> normalizedFrames = normalizeReadableFrames(intakeResult, diagnostics);
        try {
            CaptureMediaSummary intakeSummary = mediaSummary(
                    intakeResult,
                    normalizedFrames,
                    diagnostics,
                    0,
                    0,
                    0,
                    0,
                    0
            );

            if (normalizedFrames.isEmpty()) {
                if (diagnostics.stream().anyMatch(CaptureMediaDiagnostic::blocking)) {
                    return failedFromMediaDiagnostics(intakeSummary, diagnostics);
                }
                diagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                        CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        "Capture media input does not contain any normalized frame candidates"
                ));
                return CaptureMediaReceiverResult.incomplete(
                        mediaSummary(intakeResult, normalizedFrames, diagnostics, 0, 0, 0, 0, 0),
                        diagnostics,
                        "Capture media input is missing required unique frame content"
                );
            }
            Optional<CaptureMediaReceiverResult> debugFailure = exportDebugCandidates(
                    request,
                    intakeResult,
                    normalizedFrames,
                    diagnostics,
                    backendId,
                    backendVersion
            );
            if (debugFailure.isPresent()) {
                return debugFailure.orElseThrow();
            }

            CaptureMediaFrameDecodeResult decodeResult = mediaFrameDecoder.decode(normalizedFrames);
            diagnostics.addAll(decodeResult.diagnostics());
            if (decodeResult.decodedFrames().isEmpty()
                    && diagnostics.stream().noneMatch(CaptureMediaDiagnostic::blocking)) {
                diagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                        CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        "No capture media frame content was decoded"
                ));
            }

            CaptureAssemblyResult assemblyResult = frameSetAssembler.assemble(decodeResult.decodedFrames());
            diagnostics.addAll(mapAssemblyDiagnostics(request.sourceKind(), assemblyResult.diagnostics(), normalizedFrames));
            CaptureMediaSummary summary = mediaSummary(
                    intakeResult,
                    normalizedFrames,
                    diagnostics,
                    decodeResult.decodedCandidateCount(),
                    assemblyResult.acceptedCandidateCount(),
                    assemblyResult.duplicateFrameCount(),
                    decodeResult.rejectedCandidateCount(),
                    assemblyResult.decodedTileCount()
            );

            if (assemblyResult.rejected()) {
                return CaptureMediaReceiverResult.rejected(
                        summary,
                        diagnostics,
                        "Capture media input contains inconsistent decoded content"
                );
            }
            if (assemblyResult.incomplete()) {
                return CaptureMediaReceiverResult.incomplete(
                        summary,
                        diagnostics,
                        "Capture media input is missing required unique frame content"
                );
            }
            if (assemblyResult.content().isEmpty()) {
                diagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                        CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        "Decoded capture media content is not complete enough for restore"
                ));
                return CaptureMediaReceiverResult.incomplete(
                        mediaSummary(
                                intakeResult,
                                normalizedFrames,
                                diagnostics,
                                decodeResult.decodedCandidateCount(),
                                assemblyResult.acceptedCandidateCount(),
                                assemblyResult.duplicateFrameCount(),
                                decodeResult.rejectedCandidateCount(),
                                assemblyResult.decodedTileCount()
                        ),
                        diagnostics,
                        "Capture media input is missing required unique frame content"
                );
            }
            List<CaptureMediaDiagnostic> successfulDiagnostics =
                    diagnosticsForCompleteContent(request.sourceKind(), diagnostics);
            CaptureMediaSummary successfulSummary = mediaSummary(
                    intakeResult,
                    normalizedFrames,
                    successfulDiagnostics,
                    decodeResult.decodedCandidateCount(),
                    assemblyResult.acceptedCandidateCount(),
                    assemblyResult.duplicateFrameCount(),
                    decodeResult.rejectedCandidateCount(),
                    assemblyResult.decodedTileCount()
            );
            if (successfulDiagnostics.stream().anyMatch(CaptureMediaDiagnostic::blocking)) {
                return failedFromMediaDiagnostics(successfulSummary, successfulDiagnostics);
            }
            if (!restoreRequested) {
                return CaptureMediaReceiverResult.eligible(
                        successfulSummary,
                        successfulDiagnostics,
                        "Capture media input is eligible for restore"
                );
            }
            return restoreDecodedContent(
                    assemblyResult.content().orElseThrow(),
                    request.outputDirectory().orElseThrow(),
                    successfulSummary,
                    successfulDiagnostics
            );
        } finally {
            normalizedFrames.forEach(NormalizedCaptureFrame::releaseArgbPixels);
        }
    }

    private void logCompletion(
            CaptureMediaReceiverRequest request,
            CaptureMediaReceiverResult result,
            String backendId,
            long startedAtNanos
    ) {
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        CaptureMediaSummary summary = result.summary();
        String primaryDiagnosticCode = primaryDiagnosticCode(result.diagnostics())
                .map(CaptureMediaDiagnosticCode::name)
                .orElse("none");
        if (result.failed()) {
            LOGGER.warn(
                    "Capture media receiver completed status={} sourceKind={} backendId={} primaryDiagnosticCode={} submittedMedia={} readableMedia={} acceptedCandidates={} rejectedCandidates={} duplicateFrames={} decodedTiles={} restoredFiles={} durationMillis={}",
                    result.status(),
                    request.sourceKind(),
                    backendId,
                    primaryDiagnosticCode,
                    summary.submittedMediaCount(),
                    summary.readableMediaCount(),
                    summary.acceptedCandidateCount(),
                    summary.rejectedCandidateCount(),
                    summary.duplicateMediaFrameCount(),
                    summary.decodedTileCount(),
                    summary.restoredFileCount(),
                    durationMillis
            );
            return;
        }
        LOGGER.info(
                "Capture media receiver completed status={} sourceKind={} backendId={} submittedMedia={} readableMedia={} acceptedCandidates={} rejectedCandidates={} duplicateFrames={} decodedTiles={} restoredFiles={} durationMillis={}",
                result.status(),
                request.sourceKind(),
                backendId,
                summary.submittedMediaCount(),
                summary.readableMediaCount(),
                summary.acceptedCandidateCount(),
                summary.rejectedCandidateCount(),
                summary.duplicateMediaFrameCount(),
                summary.decodedTileCount(),
                summary.restoredFileCount(),
                durationMillis
        );
    }

    private Optional<CaptureMediaDiagnosticCode> primaryDiagnosticCode(List<CaptureMediaDiagnostic> diagnostics) {
        Optional<CaptureMediaDiagnosticCode> blockingCode = diagnostics.stream()
                .filter(CaptureMediaDiagnostic::blocking)
                .map(CaptureMediaDiagnostic::code)
                .findFirst();
        return blockingCode.or(() -> diagnostics.stream()
                .map(CaptureMediaDiagnostic::code)
                .findFirst());
    }

    private Optional<CaptureMediaReceiverResult> exportDebugCandidates(
            CaptureMediaReceiverRequest request,
            MediaIntakeResult intakeResult,
            List<NormalizedCaptureFrame> normalizedFrames,
            List<CaptureMediaDiagnostic> diagnostics,
            String backendId,
            String backendVersion
    ) {
        Optional<Path> debugOutputDirectory = request.debugOutputDirectory();
        if (debugOutputDirectory.isEmpty()) {
            return Optional.empty();
        }
        try {
            debugExporter.export(
                    normalizedFrames,
                    debugOutputDirectory.orElseThrow(),
                    backendId,
                    backendVersion
            );
            return Optional.empty();
        } catch (IOException exception) {
            diagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                    CaptureMediaDiagnosticCode.DEBUG_EXPORT_FAILURE,
                    CaptureMediaDiagnosticSeverity.ERROR,
                    "Capture media debug output could not be written to " + debugOutputDirectory.orElseThrow()
            ));
            return Optional.of(CaptureMediaReceiverResult.rejected(
                    mediaSummary(
                            intakeResult,
                            normalizedFrames,
                            diagnostics,
                            0,
                            0,
                            0,
                            normalizedFrames.size(),
                            0
                    ),
                    diagnostics,
                    "Capture media debug output could not be written"
            ));
        }
    }

    private List<NormalizedCaptureFrame> normalizeReadableFrames(
            MediaIntakeResult intakeResult,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        List<NormalizedCaptureFrame> normalizedFrames = new ArrayList<>();
        for (MediaInputFrame frame : intakeResult.readableFrames()) {
            try {
                MediaNormalizationResult normalizationResult = frameNormalizer.normalize(frame);
                normalizedFrames.addAll(normalizationResult.frames());
                diagnostics.addAll(normalizationResult.diagnostics());
            } finally {
                frame.releaseArgbPixels();
            }
        }
        return List.copyOf(normalizedFrames);
    }

    private CaptureMediaReceiverResult restoreDecodedContent(
            CaptureSessionContent content,
            Path outputDirectory,
            CaptureMediaSummary summary,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        List<CaptureMediaDiagnostic> combinedDiagnostics = new ArrayList<>(diagnostics);
        try {
            ReaderRestoreResult restoreResult = readerRestoreService.restore(new ReaderRestoreRequest(
                    content.sessionId(),
                    content.finalSessionDigest(),
                    content.decodedContent(),
                    outputDirectory
            ));
            if (restoreResult.restored()) {
                return CaptureMediaReceiverResult.restored(
                        withRestoredFileCount(summary, restoreResult.restoredFileCount()),
                        combinedDiagnostics,
                        restoreResult
                );
            }
            if (restoreResult.status() == ReaderRestoreStatus.INCOMPLETE_CONTENT) {
                combinedDiagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                        CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                        CaptureMediaDiagnosticSeverity.ERROR,
                        restoreResult.message()
                ));
                return CaptureMediaReceiverResult.incomplete(
                        summary,
                        combinedDiagnostics,
                        "Capture media input is missing required restore content"
                );
            }
            combinedDiagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                    CaptureMediaDiagnosticCode.RESTORE_FAILURE,
                    CaptureMediaDiagnosticSeverity.ERROR,
                    restoreResult.message()
            ));
            return CaptureMediaReceiverResult.restoreFailed(
                    summary,
                    combinedDiagnostics,
                    Optional.of(restoreResult),
                    "Capture media input qualified, but restore did not complete"
            );
        } catch (RuntimeException exception) {
            combinedDiagnostics.add(CaptureMediaDiagnostic.forMediaSet(
                    CaptureMediaDiagnosticCode.RESTORE_FAILURE,
                    CaptureMediaDiagnosticSeverity.ERROR,
                    "Capture media restore failed before reader restore completed"
            ));
            return CaptureMediaReceiverResult.restoreFailed(
                    summary,
                    combinedDiagnostics,
                    Optional.empty(),
                    "Capture media input qualified, but restore did not complete"
            );
        }
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
            if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("HEIC/HEIF"))) {
                return "HEIC/HEIF capture media input requires optional libheif support";
            }
            return "Capture media image format is unsupported";
        }
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND)) {
            return "No recoverable capture media frames were found";
        }
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.UNREADABLE_MEDIA)) {
            return "Capture media input could not be read";
        }
        return "Capture media input cannot be used by the receiver";
    }

    private List<CaptureMediaDiagnostic> mapAssemblyDiagnostics(
            CaptureMediaSourceKind sourceKind,
            List<CaptureFrameDiagnostic> diagnostics,
            List<NormalizedCaptureFrame> normalizedFrames
    ) {
        Map<MediaSourceContext, NormalizedCaptureFrame> framesBySourceContext = framesBySourceContext(normalizedFrames);
        return diagnostics.stream()
                .map(diagnostic -> mapAssemblyDiagnostic(sourceKind, diagnostic, framesBySourceContext))
                .toList();
    }

    private CaptureMediaDiagnostic mapAssemblyDiagnostic(
            CaptureMediaSourceKind sourceKind,
            CaptureFrameDiagnostic diagnostic,
            Map<MediaSourceContext, NormalizedCaptureFrame> framesBySourceContext
    ) {
        CaptureMediaDiagnosticSeverity severity = severityFor(diagnostic.code());
        Optional<NormalizedCaptureFrame> normalizedFrame = diagnostic.sourceId()
                .flatMap(sourceId -> diagnostic.callerOrder()
                        .map(callerOrder -> new MediaSourceContext(sourceId, callerOrder))
                        .map(framesBySourceContext::get));
        Optional<String> sourceId = normalizedFrame
                .map(NormalizedCaptureFrame::sourceId)
                .or(diagnostic::sourceId);
        Optional<Integer> callerOrder = normalizedFrame
                .map(NormalizedCaptureFrame::callerOrder)
                .or(diagnostic::callerOrder);
        Optional<Long> timestampMillis = normalizedFrame
                .flatMap(NormalizedCaptureFrame::timestampMillis);
        Optional<Long> frameNumber = normalizedFrame
                .flatMap(NormalizedCaptureFrame::frameNumber);
        boolean sourceScoped = diagnostic.frameScoped();
        return new CaptureMediaDiagnostic(
                mapDiagnosticCode(diagnostic.code()),
                severity,
                severity.blocksRestore(),
                sourceScoped
                        ? Optional.of(normalizedFrame.map(NormalizedCaptureFrame::sourceKind).orElse(sourceKind))
                        : Optional.empty(),
                sourceScoped ? sourceId : Optional.empty(),
                sourceScoped ? callerOrder : Optional.empty(),
                sourceScoped ? timestampMillis : Optional.empty(),
                sourceScoped ? frameNumber : Optional.empty(),
                normalizedFrame.map(this::qualityMetricMap).orElse(Map.of()),
                diagnostic.message()
        );
    }

    private CaptureMediaDiagnosticSeverity severityFor(CaptureDiagnosticCode code) {
        return code == CaptureDiagnosticCode.DUPLICATE_EQUIVALENT_FRAME
                ? CaptureMediaDiagnosticSeverity.WARNING
                : CaptureMediaDiagnosticSeverity.ERROR;
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
            List<CaptureMediaDiagnostic> diagnostics,
            int acceptedCandidateCount,
            int recoveredUniqueFrameCount,
            int duplicateFrameCount,
            int decodeRejectedCandidateCount,
            int decodedTileCount
    ) {
        int rejectedCandidateCount = rejectedCandidateCount(
                intakeResult,
                normalizedFrames,
                diagnostics,
                decodeRejectedCandidateCount
        );
        return new CaptureMediaSummary(
                intakeResult.submittedSourceCount(),
                intakeResult.readableFrames().size(),
                acceptedCandidateCount,
                rejectedCandidateCount,
                0,
                duplicateFrameCount,
                recoveredUniqueFrameCount,
                decodedTileCount,
                0
        );
    }

    private int rejectedCandidateCount(
            MediaIntakeResult intakeResult,
            List<NormalizedCaptureFrame> normalizedFrames,
            List<CaptureMediaDiagnostic> diagnostics,
            int decodeRejectedCandidateCount
    ) {
        int rejectedByNormalization = Math.max(0, intakeResult.readableFrames().size() - normalizedFrames.size());
        int rejectedByDiagnostics = (int) diagnostics.stream()
                .filter(CaptureMediaDiagnostic::sourceScoped)
                .filter(CaptureMediaDiagnostic::blocking)
                .filter(diagnostic -> !diagnostic.code().duplicate())
                .count();
        int unsupportedUnreadableSources = Math.max(0, intakeResult.submittedSourceCount() - intakeResult.readableFrames().size());
        return Math.min(
                intakeResult.submittedSourceCount(),
                Math.max(
                        Math.max(Math.max(rejectedByNormalization, rejectedByDiagnostics), unsupportedUnreadableSources),
                        decodeRejectedCandidateCount
                )
        );
    }

    private List<CaptureMediaDiagnostic> diagnosticsForCompleteContent(
            CaptureMediaSourceKind sourceKind,
            List<CaptureMediaDiagnostic> diagnostics
    ) {
        if (sourceKind != CaptureMediaSourceKind.EXTRACTED_FRAME_FOLDER) {
            return List.copyOf(diagnostics);
        }
        return diagnostics.stream()
                .map(this::asRecoverableExtractedFrameDiagnostic)
                .toList();
    }

    private CaptureMediaDiagnostic asRecoverableExtractedFrameDiagnostic(CaptureMediaDiagnostic diagnostic) {
        if (!diagnostic.blocking()
                || !diagnostic.sourceScoped()
                || !recoverableExtractedFrameRejection(diagnostic.code())) {
            return diagnostic;
        }
        return new CaptureMediaDiagnostic(
                diagnostic.code(),
                CaptureMediaDiagnosticSeverity.WARNING,
                false,
                diagnostic.sourceKind(),
                diagnostic.sourceId(),
                diagnostic.callerOrder(),
                diagnostic.timestampMillis(),
                diagnostic.frameNumber(),
                diagnostic.metrics(),
                diagnostic.message()
        );
    }

    private boolean recoverableExtractedFrameRejection(CaptureMediaDiagnosticCode code) {
        return code.qualityIssue()
                || code.unsupportedMedia()
                || code == CaptureMediaDiagnosticCode.UNREADABLE_MEDIA
                || code == CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS;
    }

    private CaptureMediaSummary withRestoredFileCount(CaptureMediaSummary summary, long restoredFileCount) {
        return new CaptureMediaSummary(
                summary.submittedMediaCount(),
                summary.readableMediaCount(),
                summary.acceptedCandidateCount(),
                summary.rejectedCandidateCount(),
                summary.uncertainCandidateCount(),
                summary.duplicateMediaFrameCount(),
                summary.recoveredUniqueFrameCount(),
                summary.decodedTileCount(),
                restoredFileCount
        );
    }

    private Map<MediaSourceContext, NormalizedCaptureFrame> framesBySourceContext(
            List<NormalizedCaptureFrame> normalizedFrames
    ) {
        Map<MediaSourceContext, NormalizedCaptureFrame> frames = new LinkedHashMap<>();
        for (NormalizedCaptureFrame frame : normalizedFrames) {
            frames.put(new MediaSourceContext(frame.sourceId(), frame.callerOrder()), frame);
        }
        return Map.copyOf(frames);
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

    private record MediaSourceContext(String sourceId, int callerOrder) {
    }
}
