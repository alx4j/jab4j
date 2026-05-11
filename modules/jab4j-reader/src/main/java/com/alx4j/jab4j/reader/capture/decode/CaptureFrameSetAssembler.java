package com.alx4j.jab4j.reader.capture.decode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.content.DecodedFrameContent;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;

/**
 * Assembles decoded capture frames by authoritative envelope identity.
 */
public final class CaptureFrameSetAssembler {

    /**
     * Collapses equivalent duplicates, rejects conflicts, and recovers final session identity.
     *
     * @param decodedFrames decoded capture frames from qualification
     * @return assembly result
     */
    public CaptureAssemblyResult assemble(List<DecodedCaptureFrame> decodedFrames) {
        Objects.requireNonNull(decodedFrames, "decodedFrames must not be null");
        if (decodedFrames.isEmpty()) {
            return new CaptureAssemblyResult(Optional.empty(), List.of(), 0, 0, 0, false, false);
        }

        List<CaptureFrameDiagnostic> diagnostics = new ArrayList<>();
        Set<SessionId> sessions = new LinkedHashSet<>();
        for (DecodedCaptureFrame frame : decodedFrames) {
            Objects.requireNonNull(frame, "decodedFrames must not contain null values");
            sessions.add(frame.sessionId());
        }
        if (sessions.size() != 1) {
            diagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                    CaptureDiagnosticCode.INCONSISTENT_SESSION_CONTENT,
                    "Capture input contains more than one decoded session"
            ));
            return rejectedResult(decodedFrames, diagnostics, 0);
        }

        Map<FrameIdentity, DecodedCaptureFrame> uniqueFrames = new LinkedHashMap<>();
        int duplicateCount = 0;
        boolean conflictingDuplicate = false;
        for (DecodedCaptureFrame candidate : decodedFrames.stream()
                .sorted(Comparator.comparingInt(DecodedCaptureFrame::callerOrder))
                .toList()) {
            FrameIdentity identity = new FrameIdentity(candidate.frameIndex(), candidate.frameType());
            DecodedCaptureFrame existing = uniqueFrames.get(identity);
            if (existing == null) {
                uniqueFrames.put(identity, candidate);
            } else if (existing.sameDecodedContent(candidate)) {
                duplicateCount++;
                diagnostics.add(CaptureFrameDiagnostic.forSource(
                        CaptureDiagnosticCode.DUPLICATE_EQUIVALENT_FRAME,
                        candidate.sourceId(),
                        candidate.callerOrder(),
                        "Duplicate frame content matches an already accepted decoded identity"
                ));
            } else {
                duplicateCount++;
                conflictingDuplicate = true;
                diagnostics.add(CaptureFrameDiagnostic.forSource(
                        CaptureDiagnosticCode.DUPLICATE_CONFLICTING_FRAME,
                        candidate.sourceId(),
                        candidate.callerOrder(),
                        "Duplicate frame identity conflicts with already accepted decoded content"
                ));
            }
        }

        List<DecodedCaptureFrame> orderedFrames = uniqueFrames.values().stream()
                .sorted(Comparator.comparingLong(DecodedCaptureFrame::frameIndex))
                .toList();
        if (conflictingDuplicate || hasFrameIndexTypeConflict(orderedFrames, diagnostics)) {
            return rejectedResult(orderedFrames, diagnostics, duplicateCount);
        }

        String finalSessionDigest = extractFinalSessionDigest(orderedFrames, diagnostics);
        if (hasInconsistentSessionDiagnostic(diagnostics)) {
            return rejectedResult(orderedFrames, diagnostics, duplicateCount);
        }
        boolean missingContent = finalSessionDigest == null;
        if (hasFrameIndexGaps(orderedFrames, diagnostics)) {
            missingContent = true;
        }
        if (missingContent) {
            return incompleteResult(orderedFrames, diagnostics, duplicateCount);
        }

        SessionId sessionId = sessions.iterator().next();
        String layoutProfileId = orderedFrames.get(0).layoutProfileId();
        if (orderedFrames.stream().anyMatch(frame -> !layoutProfileId.equals(frame.layoutProfileId()))) {
            diagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                    CaptureDiagnosticCode.INCONSISTENT_SESSION_CONTENT,
                    "Decoded capture frames do not share one layout profile"
            ));
            return rejectedResult(orderedFrames, diagnostics, duplicateCount);
        }

        List<DecodedFrameContent> decodedFrameContent = orderedFrames.stream()
                .map(frame -> new DecodedFrameContent(
                        frame.frameIndex(),
                        frame.frameType(),
                        frame.layoutProfileId(),
                        frame.tilePayloads()
                ))
                .toList();
        DecodedFrameSetContent decodedContent = new DecodedFrameSetContent(sessionId, layoutProfileId, decodedFrameContent);
        return new CaptureAssemblyResult(
                Optional.of(new CaptureSessionContent(sessionId, finalSessionDigest, decodedContent)),
                diagnostics,
                orderedFrames.size(),
                duplicateCount,
                decodedContent.decodedTileCount(),
                false,
                false
        );
    }

    private boolean hasFrameIndexTypeConflict(
            List<DecodedCaptureFrame> orderedFrames,
            List<CaptureFrameDiagnostic> diagnostics
    ) {
        Map<Long, FrameType> frameTypesByIndex = new LinkedHashMap<>();
        for (DecodedCaptureFrame frame : orderedFrames) {
            FrameType existing = frameTypesByIndex.putIfAbsent(frame.frameIndex(), frame.frameType());
            if (existing != null && existing != frame.frameType()) {
                diagnostics.add(CaptureFrameDiagnostic.forSource(
                        CaptureDiagnosticCode.INCONSISTENT_SESSION_CONTENT,
                        frame.sourceId(),
                        frame.callerOrder(),
                        "Decoded frame index appears with conflicting frame types"
                ));
                return true;
            }
        }
        return false;
    }

    private boolean hasInconsistentSessionDiagnostic(List<CaptureFrameDiagnostic> diagnostics) {
        return diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.code() == CaptureDiagnosticCode.INCONSISTENT_SESSION_CONTENT);
    }

    private boolean hasFrameIndexGaps(
            List<DecodedCaptureFrame> orderedFrames,
            List<CaptureFrameDiagnostic> diagnostics
    ) {
        long expectedFrameIndex = 0L;
        for (DecodedCaptureFrame frame : orderedFrames) {
            if (frame.frameIndex() != expectedFrameIndex) {
                diagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                        CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                        "Decoded capture sequence is missing one or more frame indexes"
                ));
                return true;
            }
            expectedFrameIndex++;
        }
        return false;
    }

    private String extractFinalSessionDigest(
            List<DecodedCaptureFrame> orderedFrames,
            List<CaptureFrameDiagnostic> diagnostics
    ) {
        String finalSessionDigest = null;
        for (DecodedCaptureFrame frame : orderedFrames) {
            for (TilePayload payload : frame.tilePayloads()) {
                if (payload.payloadKind() != PayloadKind.SESSION_END) {
                    continue;
                }
                String candidate = finalSessionDigest(payload);
                if (candidate == null) {
                    continue;
                }
                if (finalSessionDigest == null) {
                    finalSessionDigest = candidate;
                } else if (!finalSessionDigest.equals(candidate)) {
                    diagnostics.add(CaptureFrameDiagnostic.forSource(
                            CaptureDiagnosticCode.INCONSISTENT_SESSION_CONTENT,
                            frame.sourceId(),
                            frame.callerOrder(),
                            "SESSION_END payloads contain conflicting final session digests"
                    ));
                    return null;
                }
            }
        }
        if (finalSessionDigest == null) {
            diagnostics.add(CaptureFrameDiagnostic.forCaptureSet(
                    CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                    "Capture input does not contain a SESSION_END finalSessionDigest"
            ));
        }
        return finalSessionDigest;
    }

    private String finalSessionDigest(TilePayload payload) {
        String text = new String(payload.body(), StandardCharsets.UTF_8);
        for (String line : text.split("\n", -1)) {
            if (!line.startsWith("finalSessionDigest=")) {
                continue;
            }
            String value = line.substring("finalSessionDigest=".length());
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private CaptureAssemblyResult rejectedResult(
            List<DecodedCaptureFrame> frames,
            List<CaptureFrameDiagnostic> diagnostics,
            int duplicateCount
    ) {
        return new CaptureAssemblyResult(
                Optional.empty(),
                diagnostics,
                frames.size(),
                duplicateCount,
                decodedTileCount(frames),
                true,
                false
        );
    }

    private CaptureAssemblyResult incompleteResult(
            List<DecodedCaptureFrame> frames,
            List<CaptureFrameDiagnostic> diagnostics,
            int duplicateCount
    ) {
        return new CaptureAssemblyResult(
                Optional.empty(),
                diagnostics,
                frames.size(),
                duplicateCount,
                decodedTileCount(frames),
                false,
                true
        );
    }

    private int decodedTileCount(List<DecodedCaptureFrame> frames) {
        return frames.stream().mapToInt(frame -> frame.tilePayloads().size()).sum();
    }

    private record FrameIdentity(long frameIndex, FrameType frameType) {
    }
}
