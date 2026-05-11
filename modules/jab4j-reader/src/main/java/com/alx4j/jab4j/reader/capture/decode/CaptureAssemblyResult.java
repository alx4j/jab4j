package com.alx4j.jab4j.reader.capture.decode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;

/**
 * Result of assembling decoded capture frames into one authoritative decoded session.
 *
 * @param content complete session content when assembly is eligible for restore
 * @param diagnostics assembly diagnostics, including duplicates and missing content
 * @param acceptedCandidateCount unique decoded frames accepted into the session
 * @param duplicateFrameCount equivalent duplicate frame count
 * @param decodedTileCount decoded tile payload count for unique accepted frames
 * @param rejected true when decoded content is inconsistent and cannot be trusted
 * @param incomplete true when content is internally consistent but missing required restore input
 */
public record CaptureAssemblyResult(
        Optional<CaptureSessionContent> content,
        List<CaptureFrameDiagnostic> diagnostics,
        int acceptedCandidateCount,
        int duplicateFrameCount,
        int decodedTileCount,
        boolean rejected,
        boolean incomplete
) {

    /**
     * Creates a validated assembly result.
     *
     * @param content complete session content
     * @param diagnostics assembly diagnostics
     * @param acceptedCandidateCount unique accepted frame count
     * @param duplicateFrameCount duplicate frame count
     * @param decodedTileCount decoded tile count
     * @param rejected true when content is inconsistent
     * @param incomplete true when required content is missing
     */
    public CaptureAssemblyResult {
        content = Objects.requireNonNull(content, "content must not be null");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null values");
        }
        if (acceptedCandidateCount < 0 || duplicateFrameCount < 0 || decodedTileCount < 0) {
            throw new IllegalArgumentException("assembly counts must be non-negative");
        }
        if ((rejected || incomplete) && content.isPresent()) {
            throw new IllegalArgumentException("failed assembly results must not include complete content");
        }
        if (rejected && incomplete) {
            throw new IllegalArgumentException("assembly cannot be both rejected and incomplete");
        }
    }
}
