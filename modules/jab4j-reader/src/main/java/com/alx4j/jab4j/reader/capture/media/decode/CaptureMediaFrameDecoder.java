package com.alx4j.jab4j.reader.capture.media.decode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.decode.DecodedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
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
        this.tilePayloadSampler = Objects.requireNonNull(
                tilePayloadSampler,
                "tilePayloadSampler must not be null"
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
            FrameSample frameSample = tilePayloadSampler.sample(frame);
            diagnostics.addAll(frameSample.diagnostics());
            if (frameSample.status() == FrameSampleStatus.ACCEPTED) {
                try {
                    decodedFrames.add(createDecodedFrame(frame, frameSample.payloads()));
                } catch (IllegalArgumentException exception) {
                    rejectedCandidateCount++;
                    diagnostics.add(sourceDiagnostic(
                            frame,
                            CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS,
                            "Decoded media tile payload identity is inconsistent within one normalized frame"
                    ));
                }
                continue;
            }

            rejectedCandidateCount++;
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
        }
        return new CaptureMediaFrameDecodeResult(decodedFrames, diagnostics, rejectedCandidateCount);
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
}
