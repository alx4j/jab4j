package com.alx4j.jab4j.reader.capture.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.decode.DecodedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.decode.CaptureMediaFrameDecodeResult;
import com.alx4j.jab4j.reader.capture.media.decode.CaptureMediaFrameDecoder;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

@DisplayName("Capture media frame decoder")
class CaptureMediaFrameDecoderTest {

    private final CaptureMediaFrameDecoder decoder = new CaptureMediaFrameDecoder();

    @Test
    @DisplayName("Sampler-accepted media payloads decode when frame identities agree")
    void samplerAcceptedMediaPayloadsDecodeWhenFrameIdentitiesAgree() {
        TilePayload first = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                3L,
                0,
                PayloadKind.FILE_CHUNK,
                "left-tile"
        );
        TilePayload second = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                3L,
                1,
                PayloadKind.FILE_CHUNK,
                "right-tile"
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.normalizedFrame(
                "consistent-frame.png",
                7,
                first,
                second
        );

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));
        DecodedCaptureFrame decodedFrame = result.decodedFrames().get(0);

        assertAll(
                () -> assertEquals(1, result.decodedCandidateCount()),
                () -> assertEquals(0, result.rejectedCandidateCount()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals("consistent-frame.png", decodedFrame.sourceId()),
                () -> assertEquals(7, decodedFrame.callerOrder()),
                () -> assertEquals(CaptureMediaTestFrames.defaultSessionId(), decodedFrame.sessionId()),
                () -> assertEquals(3L, decodedFrame.frameIndex()),
                () -> assertEquals(FrameType.DATA, decodedFrame.frameType()),
                () -> assertEquals(CaptureMediaTestFrames.layoutProfileId(), decodedFrame.layoutProfileId()),
                () -> assertEquals(List.of(first, second), decodedFrame.tilePayloads())
        );
    }

    @Test
    @DisplayName("Sampler-accepted partial camera-derived payloads become decoded candidates")
    void samplerAcceptedPartialCameraDerivedPayloadsBecomeDecodedCandidates() {
        TilePayload accepted = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                3L,
                0,
                PayloadKind.FILE_CHUNK,
                "left-tile"
        );
        TilePayload corruptedSibling = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                3L,
                1,
                PayloadKind.FILE_CHUNK,
                "right-tile"
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.cameraDerivedNormalizedFrameWithCorruptedSibling(
                "partial-camera-frame.jpeg",
                8,
                accepted,
                corruptedSibling
        );

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));
        DecodedCaptureFrame decodedFrame = result.decodedFrames().get(0);

        assertAll(
                () -> assertEquals(1, result.decodedCandidateCount()),
                () -> assertEquals(0, result.rejectedCandidateCount()),
                () -> assertTrue(result.diagnostics().stream().noneMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertTrue(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.metrics().containsKey("partialAccepted"))),
                () -> assertEquals("partial-camera-frame.jpeg", decodedFrame.sourceId()),
                () -> assertEquals(8, decodedFrame.callerOrder()),
                () -> assertEquals(List.of(accepted), decodedFrame.tilePayloads())
        );
    }

    @Test
    @DisplayName("Sampler-accepted media payloads remain rejected diagnostics when frame identities disagree")
    void samplerAcceptedMediaPayloadsRemainRejectedDiagnosticsWhenFrameIdentitiesDisagree() {
        TilePayload first = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                3L,
                0,
                PayloadKind.FILE_CHUNK,
                "left-tile"
        );
        TilePayload second = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                4L,
                1,
                PayloadKind.FILE_CHUNK,
                "right-tile"
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.normalizedFrame(
                "inconsistent-frame.png",
                2,
                first,
                second
        );

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));
        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);

        assertAll(
                () -> assertTrue(result.decodedFrames().isEmpty()),
                () -> assertEquals(1, result.rejectedCandidateCount()),
                () -> assertEquals(CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertEquals("inconsistent-frame.png", diagnostic.sourceId().orElseThrow()),
                () -> assertEquals(2, diagnostic.callerOrder().orElseThrow())
        );
    }

    @Test
    @DisplayName("Envelope CRC failures remain diagnostics instead of decoded frames")
    void envelopeCrcFailuresRemainDiagnosticsInsteadOfDecodedFrames() {
        TilePayload payload = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                0L,
                0,
                PayloadKind.FILE_CHUNK,
                "crc-protected-content"
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.normalizedFrameWithCorruptedEnvelope(
                "bad-envelope.png",
                0,
                0,
                payload
        );

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));

        assertRejectedAsDiagnostic(
                result,
                "bad-envelope.png",
                CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE
        );
    }

    @Test
    @DisplayName("Slot identity mismatches remain diagnostics instead of decoded frames")
    void slotIdentityMismatchesRemainDiagnosticsInsteadOfDecodedFrames() {
        TilePayload payloadForSlotOne = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                0L,
                1,
                PayloadKind.FILE_CHUNK,
                "slot-one-content"
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.normalizedFrameWithPayloadInSlot(
                "wrong-slot.png",
                0,
                0,
                payloadForSlotOne
        );

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));

        assertRejectedAsDiagnostic(
                result,
                "wrong-slot.png",
                CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE
        );
    }

    @Test
    @DisplayName("Empty SESSION_END payloads remain rejected")
    void emptySessionEndPayloadsRemainRejected() {
        TilePayload emptySessionEnd = CaptureMediaTestFrames.payload(
                CaptureMediaTestFrames.defaultSessionId(),
                FrameType.END,
                0L,
                0,
                PayloadKind.SESSION_END,
                new byte[0]
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.normalizedFrame(
                "empty-session-end.png",
                0,
                emptySessionEnd
        );

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));

        assertRejectedAsDiagnostic(
                result,
                "empty-session-end.png",
                CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE
        );
    }

    @Test
    @DisplayName("No-content normalized frames remain screen-or-frame-not-found diagnostics")
    void noContentNormalizedFramesRemainScreenOrFrameNotFoundDiagnostics() {
        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(noContentNormalizedFrame()));

        assertRejectedAsDiagnostic(
                result,
                "blank-frame.png",
                CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND
        );
    }

    @Test
    @DisplayName("Non-empty SESSION_END payloads are eligible for decode before assembly completeness checks")
    void nonEmptySessionEndPayloadsAreEligibleForDecodeBeforeAssemblyCompletenessChecks() {
        TilePayload sessionEnd = CaptureMediaTestFrames.payload(
                CaptureMediaTestFrames.defaultSessionId(),
                FrameType.END,
                0L,
                0,
                PayloadKind.SESSION_END,
                "finalSessionDigest=digest-123\n".getBytes(StandardCharsets.UTF_8)
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.normalizedFrame(
                "non-empty-session-end.png",
                0,
                sessionEnd
        );

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));

        assertAll(
                () -> assertEquals(1, result.decodedCandidateCount()),
                () -> assertEquals(0, result.rejectedCandidateCount()),
                () -> assertEquals(FrameType.END, result.decodedFrames().get(0).frameType()),
                () -> assertEquals(PayloadKind.SESSION_END,
                        result.decodedFrames().get(0).tilePayloads().get(0).payloadKind())
        );
    }

    @Test
    @DisplayName("Mixed-session payloads in one sampled frame are rejected before assembly")
    void mixedSessionPayloadsInOneSampledFrameAreRejectedBeforeAssembly() {
        TilePayload first = CaptureMediaTestFrames.payload(
                FrameType.DATA,
                0L,
                0,
                PayloadKind.FILE_CHUNK,
                "first-session"
        );
        TilePayload second = CaptureMediaTestFrames.payload(
                new SessionId(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")),
                FrameType.DATA,
                0L,
                1,
                PayloadKind.FILE_CHUNK,
                "second-session".getBytes(StandardCharsets.UTF_8)
        );
        NormalizedCaptureFrame frame = CaptureMediaTestFrames.normalizedFrame("mixed-session.png", 0, first, second);

        CaptureMediaFrameDecodeResult result = decoder.decode(List.of(frame));

        assertAll(
                () -> assertTrue(result.decodedFrames().isEmpty()),
                () -> assertEquals(1, result.rejectedCandidateCount()),
                () -> assertEquals(CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS,
                        result.diagnostics().get(0).code())
        );
    }

    private NormalizedCaptureFrame noContentNormalizedFrame() {
        int width = 1280;
        int height = 720;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xFF000000);
        return new NormalizedCaptureFrame(
                "blank-frame.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                width,
                height,
                width,
                height,
                "png",
                "abc123",
                CaptureMediaTestFrames.layoutProfileId(),
                FrameCorners.exactFrame(width, height),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private void assertRejectedAsDiagnostic(
            CaptureMediaFrameDecodeResult result,
            String sourceId,
            CaptureMediaDiagnosticCode expectedCode
    ) {
        assertAll(
                () -> assertTrue(result.decodedFrames().isEmpty()),
                () -> assertEquals(1, result.rejectedCandidateCount()),
                () -> assertFalse(result.diagnostics().isEmpty()),
                () -> assertTrue(result.diagnostics().stream().allMatch(CaptureMediaDiagnostic::blocking)),
                () -> assertTrue(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.code() == expectedCode
                                && sourceId.equals(diagnostic.sourceId().orElseThrow())))
        );
    }
}
