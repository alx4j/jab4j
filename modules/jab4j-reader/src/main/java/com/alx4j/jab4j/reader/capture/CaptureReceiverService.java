package com.alx4j.jab4j.reader.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.reader.capture.decode.CaptureAssemblyResult;
import com.alx4j.jab4j.reader.capture.decode.CaptureFrameDecodeResult;
import com.alx4j.jab4j.reader.capture.decode.CaptureFrameDecoder;
import com.alx4j.jab4j.reader.capture.decode.CaptureFrameSetAssembler;
import com.alx4j.jab4j.reader.capture.input.CaptureImageIntake;
import com.alx4j.jab4j.reader.capture.input.CaptureIntakeResult;
import com.alx4j.jab4j.reader.capture.restore.CaptureRestoreCoordinator;

/**
 * Public receiver facade for evaluating or restoring extracted PNG capture frame sets.
 */
public final class CaptureReceiverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureReceiverService.class);

    private final CaptureImageIntake imageIntake;
    private final CaptureFrameDecoder frameDecoder;
    private final CaptureFrameSetAssembler frameSetAssembler;
    private final CaptureRestoreCoordinator restoreCoordinator;

    /**
     * Creates a capture receiver using the default PNG intake, exact sampler, decoder, and restore service.
     */
    public CaptureReceiverService() {
        this(
                new CaptureImageIntake(),
                new CaptureFrameDecoder(),
                new CaptureFrameSetAssembler(),
                new CaptureRestoreCoordinator()
        );
    }

    /**
     * Creates a capture receiver with explicit collaborators.
     *
     * @param imageIntake capture image intake
     * @param frameDecoder capture frame decoder
     * @param frameSetAssembler decoded session assembler
     * @param restoreCoordinator restore coordinator
     */
    public CaptureReceiverService(
            CaptureImageIntake imageIntake,
            CaptureFrameDecoder frameDecoder,
            CaptureFrameSetAssembler frameSetAssembler,
            CaptureRestoreCoordinator restoreCoordinator
    ) {
        this.imageIntake = Objects.requireNonNull(imageIntake, "imageIntake must not be null");
        this.frameDecoder = Objects.requireNonNull(frameDecoder, "frameDecoder must not be null");
        this.frameSetAssembler = Objects.requireNonNull(frameSetAssembler, "frameSetAssembler must not be null");
        this.restoreCoordinator = Objects.requireNonNull(restoreCoordinator, "restoreCoordinator must not be null");
    }

    /**
     * Evaluates or restores capture input according to whether the request includes an output directory.
     *
     * @param request capture receiver request
     * @return receiver result
     */
    public CaptureReceiverResult receive(CaptureReceiverRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return request.restoreRequested() ? restore(request) : evaluate(request);
    }

    /**
     * Evaluates capture input without filesystem restore side effects.
     *
     * @param request capture receiver request
     * @return eligible, incomplete, or rejected receiver result
     */
    public CaptureReceiverResult evaluate(CaptureReceiverRequest request) {
        return process(request, false);
    }

    /**
     * Evaluates capture input and invokes reader restore when the capture set is complete enough.
     *
     * @param request capture receiver request with output directory
     * @return restored, incomplete, rejected, or restore-failed receiver result
     */
    public CaptureReceiverResult restore(CaptureReceiverRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.outputDirectory().isEmpty()) {
            throw new IllegalArgumentException("restore request must include outputDirectory");
        }
        return process(request, true);
    }

    private CaptureReceiverResult process(CaptureReceiverRequest request, boolean restoreRequested) {
        Objects.requireNonNull(request, "request must not be null");
        long startedAtNanos = System.nanoTime();
        LOGGER.info(
                "Capture receiver starting mode={} inputSources={}",
                restoreRequested ? "restore" : "evaluate",
                request.inputSources().size()
        );
        CaptureReceiverResult result = evaluateAndMaybeRestore(request, restoreRequested);
        logCompletion(result, startedAtNanos);
        return result;
    }

    private CaptureReceiverResult evaluateAndMaybeRestore(CaptureReceiverRequest request, boolean restoreRequested) {
        CaptureIntakeResult intakeResult = imageIntake.read(request);
        CaptureFrameDecodeResult decodeResult = frameDecoder.decode(intakeResult.readableFrames());
        CaptureAssemblyResult assemblyResult = frameSetAssembler.assemble(decodeResult.decodedFrames());

        List<CaptureFrameDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(intakeResult.diagnostics());
        diagnostics.addAll(decodeResult.diagnostics());
        diagnostics.addAll(assemblyResult.diagnostics());

        CaptureReceiverSummary summary = summary(intakeResult, diagnostics, assemblyResult);
        if (intakeResult.submittedSourceCount() == 0) {
            diagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                    CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                    "No capture frame sources were discovered"
            ));
            return CaptureReceiverResult.rejected(summary, diagnostics, "No recoverable capture frames were found");
        }
        if (decodeResult.decodedFrames().isEmpty()) {
            if (diagnostics.isEmpty()) {
                diagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                        CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                        "No capture frame content was decoded"
                ));
            }
            return CaptureReceiverResult.rejected(summary(intakeResult, diagnostics, assemblyResult), diagnostics,
                    "No recoverable capture frames were found");
        }
        if (assemblyResult.rejected()) {
            return CaptureReceiverResult.rejected(summary, diagnostics, "Capture input contains inconsistent decoded content");
        }
        if (assemblyResult.incomplete()) {
            return CaptureReceiverResult.incomplete(summary, diagnostics, "Capture input is missing required content");
        }
        if (assemblyResult.content().isEmpty()) {
            diagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                    CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                    "Decoded capture content is not complete enough for restore"
            ));
            return CaptureReceiverResult.incomplete(
                    summary(intakeResult, diagnostics, assemblyResult),
                    diagnostics,
                    "Capture input is missing required content"
            );
        }
        if (!restoreRequested) {
            return CaptureReceiverResult.eligible(summary, diagnostics, "Capture input is eligible for restore");
        }
        return restoreCoordinator.restore(
                assemblyResult.content().orElseThrow(),
                request.outputDirectory().orElseThrow(),
                summary,
                diagnostics
        );
    }

    private CaptureReceiverSummary summary(
            CaptureIntakeResult intakeResult,
            List<CaptureFrameDiagnostic> diagnostics,
            CaptureAssemblyResult assemblyResult
    ) {
        return new CaptureReceiverSummary(
                intakeResult.submittedSourceCount(),
                intakeResult.readableFrames().size(),
                assemblyResult.acceptedCandidateCount(),
                rejectedFrameCount(diagnostics),
                assemblyResult.duplicateFrameCount(),
                0,
                assemblyResult.decodedTileCount(),
                0
        );
    }

    private int rejectedFrameCount(List<CaptureFrameDiagnostic> diagnostics) {
        return (int) diagnostics.stream()
                .filter(CaptureFrameDiagnostic::frameScoped)
                .filter(diagnostic -> !diagnostic.code().duplicate())
                .count();
    }

    private void logCompletion(CaptureReceiverResult result, long startedAtNanos) {
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        if (result.failed()) {
            LOGGER.warn(
                    "Capture receiver completed status={} submittedFrames={} readableFrames={} acceptedCandidates={} duplicates={} decodedTiles={} durationMillis={}",
                    result.status(),
                    result.summary().submittedFrameCount(),
                    result.summary().readableFrameCount(),
                    result.summary().acceptedCandidateCount(),
                    result.summary().duplicateFrameCount(),
                    result.summary().decodedTileCount(),
                    durationMillis
            );
            return;
        }
        LOGGER.info(
                "Capture receiver completed status={} submittedFrames={} readableFrames={} acceptedCandidates={} duplicates={} decodedTiles={} restoredFiles={} durationMillis={}",
                result.status(),
                result.summary().submittedFrameCount(),
                result.summary().readableFrameCount(),
                result.summary().acceptedCandidateCount(),
                result.summary().duplicateFrameCount(),
                result.summary().decodedTileCount(),
                result.summary().restoredFileCount(),
                durationMillis
        );
    }
}
