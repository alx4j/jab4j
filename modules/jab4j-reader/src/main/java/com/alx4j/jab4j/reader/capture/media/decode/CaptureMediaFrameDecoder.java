package com.alx4j.jab4j.reader.capture.media.decode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.decode.DecodedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaGeometryFitter;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaPatternEvidenceDetector;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.RetainedMediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.RetainedMediaInputFrameBatch;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameSample;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameSampleStatus;

/**
 * Decodes accepted normalized media candidates into tile-payload envelope frames.
 *
 * <p>The decoder is media-owned and uses tolerant media sampling, but the authoritative frame identity still comes
 * from validated {@link TilePayload} envelopes. Candidates with no decodable envelopes are rejected before frame-set
 * assembly.</p>
 */
public final class CaptureMediaFrameDecoder {

    private final CaptureMediaTilePayloadSampler tilePayloadSampler;
    private final CaptureMediaPatternEvidenceDetector patternEvidenceDetector;
    private final BiFunction<NormalizedCaptureFrame, PatternEvidence, GeometryFitEvidence> geometryFitProvider;
    private final CaptureMediaSourceSpaceModuleSampler sourceSpaceModuleSampler;

    /**
     * Creates a decoder with the default media tile payload sampler.
     */
    public CaptureMediaFrameDecoder() {
        this(new CaptureMediaTilePayloadSampler());
    }

    /**
     * Creates a decoder with an explicit sampler for focused tests.
     *
     * @param tilePayloadSampler normalized media tile payload sampler
     */
    public CaptureMediaFrameDecoder(CaptureMediaTilePayloadSampler tilePayloadSampler) {
        this(
                tilePayloadSampler,
                new CaptureMediaPatternEvidenceDetector(),
                new CaptureMediaGeometryFitter()::fit,
                new CaptureMediaSourceSpaceModuleSampler()
        );
    }

    /**
     * Creates a decoder with explicit internal MVP-9 rollback switches.
     *
     * @param sourceSpaceSamplingEnabled true when retained source-space sampling should run before normalized fallback
     * @param localRefinementEnabled true when bounded local refinement may add one refined source-space sampling pass
     */
    public CaptureMediaFrameDecoder(
            boolean sourceSpaceSamplingEnabled,
            boolean localRefinementEnabled
    ) {
        this(
                new CaptureMediaTilePayloadSampler(),
                new CaptureMediaPatternEvidenceDetector(),
                new CaptureMediaGeometryFitter()::fit,
                new CaptureMediaSourceSpaceModuleSampler(sourceSpaceSamplingEnabled, localRefinementEnabled)
        );
    }

    /**
     * Creates a decoder with explicit source-space collaborators for focused tests.
     *
     * @param tilePayloadSampler normalized media tile payload sampler
     * @param patternEvidenceDetector detector used before source-space geometry fitting
     * @param geometryFitProvider geometry fit provider
     * @param sourceSpaceModuleSampler source-space module sampler and validator
     */
    CaptureMediaFrameDecoder(
            CaptureMediaTilePayloadSampler tilePayloadSampler,
            CaptureMediaPatternEvidenceDetector patternEvidenceDetector,
            BiFunction<NormalizedCaptureFrame, PatternEvidence, GeometryFitEvidence> geometryFitProvider,
            CaptureMediaSourceSpaceModuleSampler sourceSpaceModuleSampler
    ) {
        this.tilePayloadSampler = Objects.requireNonNull(
                tilePayloadSampler,
                "tilePayloadSampler must not be null"
        );
        this.patternEvidenceDetector = Objects.requireNonNull(
                patternEvidenceDetector,
                "patternEvidenceDetector must not be null"
        );
        this.geometryFitProvider = Objects.requireNonNull(geometryFitProvider, "geometryFitProvider must not be null");
        this.sourceSpaceModuleSampler = Objects.requireNonNull(
                sourceSpaceModuleSampler,
                "sourceSpaceModuleSampler must not be null"
        );
    }

    /**
     * Samples and decodes normalized media frames into decoded capture frames.
     *
     * @param frames normalized media frames
     * @return decoded frame result with media diagnostics
     */
    public CaptureMediaFrameDecodeResult decode(List<NormalizedCaptureFrame> frames) {
        Objects.requireNonNull(frames, "frames must not be null");
        List<DecodedCaptureFrame> decodedFrames = new ArrayList<>();
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>();
        int rejectedCandidateCount = 0;
        for (NormalizedCaptureFrame frame : frames) {
            Objects.requireNonNull(frame, "frames must not contain null values");
            CandidateDecode candidateDecode = decodeNormalizedCandidate(frame);
            diagnostics.addAll(candidateDecode.diagnostics());
            if (candidateDecode.decodedFrame().isPresent()) {
                decodedFrames.add(candidateDecode.decodedFrame().orElseThrow());
            } else {
                rejectedCandidateCount++;
            }
        }
        return new CaptureMediaFrameDecodeResult(decodedFrames, diagnostics, rejectedCandidateCount);
    }

    /**
     * Samples retained source pixels first, then falls back to normalized sampling when source-space validation fails.
     *
     * @param retainedSources retained source frames and normalized candidates
     * @return decoded frame result with media diagnostics
     */
    public CaptureMediaFrameDecodeResult decode(RetainedMediaInputFrameBatch retainedSources) {
        Objects.requireNonNull(retainedSources, "retainedSources must not be null");
        List<DecodedCaptureFrame> decodedFrames = new ArrayList<>();
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>();
        int rejectedCandidateCount = 0;
        for (RetainedMediaInputFrame retainedSourceFrame : retainedSources.retainedSourceFrames()) {
            MediaInputFrame sourceFrame = retainedSourceFrame.sourceFrame();
            for (NormalizedCaptureFrame frame : retainedSourceFrame.normalizedFrames()) {
                Optional<DecodedCaptureFrame> sourceSpaceDecoded = decodeSourceSpaceCandidate(sourceFrame, frame);
                if (sourceSpaceDecoded.isPresent()) {
                    decodedFrames.add(sourceSpaceDecoded.orElseThrow());
                    continue;
                }
                CandidateDecode normalizedDecode = decodeNormalizedCandidate(frame);
                diagnostics.addAll(normalizedDecode.diagnostics());
                if (normalizedDecode.decodedFrame().isPresent()) {
                    decodedFrames.add(normalizedDecode.decodedFrame().orElseThrow());
                } else {
                    rejectedCandidateCount++;
                }
            }
        }
        return new CaptureMediaFrameDecodeResult(decodedFrames, diagnostics, rejectedCandidateCount);
    }

    private Optional<DecodedCaptureFrame> decodeSourceSpaceCandidate(
            MediaInputFrame sourceFrame,
            NormalizedCaptureFrame frame
    ) {
        PatternEvidence patternEvidence = patternEvidenceDetector.detect(sourceFrame, frame);
        GeometryFitEvidence geometryEvidence = Objects.requireNonNull(
                geometryFitProvider.apply(frame, patternEvidence),
                "geometryEvidence must not be null"
        );
        SourceSpaceValidationSample sample = sourceSpaceModuleSampler.sampleAndValidate(
                sourceFrame,
                frame,
                geometryEvidence
        );
        if (sample.acceptedPayloads().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(createDecodedFrame(frame, sample.acceptedPayloads()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private CandidateDecode decodeNormalizedCandidate(NormalizedCaptureFrame frame) {
        FrameSample frameSample = tilePayloadSampler.sample(frame);
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>(frameSample.diagnostics());
        if (frameSample.status() == FrameSampleStatus.ACCEPTED) {
            try {
                return CandidateDecode.accepted(createDecodedFrame(frame, frameSample.payloads()), diagnostics);
            } catch (IllegalArgumentException exception) {
                diagnostics.add(sourceDiagnostic(
                        frame,
                        CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS,
                        "Decoded media tile payload identity is inconsistent within one normalized frame"
                ));
                return CandidateDecode.rejected(diagnostics);
            }
        }

        if (frameSample.status() == FrameSampleStatus.EMPTY) {
            diagnostics.add(sourceDiagnostic(
                    frame,
                    CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                    "Normalized media frame does not contain supported JAB tile payload content"
            ));
        } else if (frameSample.diagnostics().isEmpty()) {
            diagnostics.add(sourceDiagnostic(
                    frame,
                    CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                    "Normalized media tile content could not be decoded"
            ));
        }
        return CandidateDecode.rejected(diagnostics);
    }

    private DecodedCaptureFrame createDecodedFrame(NormalizedCaptureFrame frame, List<TilePayload> payloads) {
        if (payloads.isEmpty()) {
            throw new IllegalArgumentException("decoded payloads must not be empty");
        }
        TilePayload firstPayload = payloads.get(0);
        SessionId sessionId = firstPayload.sessionId();
        long frameIndex = firstPayload.frameIndex();
        String layoutProfileId = firstPayload.layoutProfileId();
        for (TilePayload payload : payloads) {
            if (!sessionId.equals(payload.sessionId())
                    || payload.frameIndex() != frameIndex
                    || payload.frameType() != firstPayload.frameType()
                    || !layoutProfileId.equals(payload.layoutProfileId())) {
                throw new IllegalArgumentException("decoded payloads disagree on frame identity");
            }
            if (payload.payloadKind() == PayloadKind.SESSION_END && payload.body().length == 0) {
                throw new IllegalArgumentException("SESSION_END payload body must not be empty");
            }
        }
        return new DecodedCaptureFrame(
                frame.sourceId(),
                frame.callerOrder(),
                frame.pixelSha256(),
                sessionId,
                frameIndex,
                firstPayload.frameType(),
                layoutProfileId,
                payloads
        );
    }

    private CaptureMediaDiagnostic sourceDiagnostic(
            NormalizedCaptureFrame frame,
            CaptureMediaDiagnosticCode code,
            String message
    ) {
        return new CaptureMediaDiagnostic(
                code,
                CaptureMediaDiagnosticSeverity.ERROR,
                true,
                Optional.of(frame.sourceKind()),
                Optional.of(frame.sourceId()),
                Optional.of(frame.callerOrder()),
                Optional.empty(),
                Optional.empty(),
                frame.qualityMetrics().measured(frame.qualityMetrics().frameCoverageRatio())
                        ? java.util.Map.of("frameCoverageRatio", frame.qualityMetrics().frameCoverageRatio())
                        : java.util.Map.of(),
                message
        );
    }

    private record CandidateDecode(
            Optional<DecodedCaptureFrame> decodedFrame,
            List<CaptureMediaDiagnostic> diagnostics
    ) {

        private CandidateDecode {
            Objects.requireNonNull(decodedFrame, "decodedFrame must not be null");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
            if (diagnostics.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("diagnostics must not contain null values");
            }
        }

        private static CandidateDecode accepted(
                DecodedCaptureFrame decodedFrame,
                List<CaptureMediaDiagnostic> diagnostics
        ) {
            return new CandidateDecode(Optional.of(decodedFrame), diagnostics);
        }

        private static CandidateDecode rejected(List<CaptureMediaDiagnostic> diagnostics) {
            return new CandidateDecode(Optional.empty(), diagnostics);
        }
    }
}
