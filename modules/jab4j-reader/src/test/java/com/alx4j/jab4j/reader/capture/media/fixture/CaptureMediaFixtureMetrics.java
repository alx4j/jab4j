package com.alx4j.jab4j.reader.capture.media.fixture;

import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverResult;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverStatus;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSummary;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;

/**
 * Snapshot of generated fixture receiver and sampling metrics used as the MVP-10 Wave 1 baseline contract.
 *
 * @param scenarioId stable corpus scenario id
 * @param receiverStatus receiver lifecycle status
 * @param submittedMediaCount submitted media count
 * @param readableMediaCount readable media count
 * @param acceptedCandidateCount accepted candidate count
 * @param rejectedCandidateCount rejected candidate count
 * @param duplicateMediaFrameCount duplicate media frame count
 * @param decodedTileCount decoded tile payload count
 * @param recoveredUniqueFrameCount recovered unique frame count
 * @param eligibleForRestore whether evaluation considered the media restore-eligible
 * @param restored whether a restore attempt produced output
 * @param restoredFileCount restored file count
 * @param primaryDiagnosticCode first blocking diagnostic code, first diagnostic code, or {@code none}
 * @param paletteFallbackStatus current palette path represented by available evidence
 * @param samplingStatus first source-space sampling evidence status, or {@code NOT_AVAILABLE}
 * @param samplingReadableModuleCount readable module count from first sampling evidence
 * @param samplingAmbiguousModuleCount ambiguous module count from first sampling evidence
 * @param samplingUnreadableModuleCount unreadable module count from first sampling evidence
 * @param samplingTileDecodeAttemptCount tile decode attempts from first sampling evidence
 * @param samplingAcceptedPayloadCount accepted tile payload count from first sampling evidence
 * @param downstreamFailureStages comma-separated downstream failure stages from first sampling evidence, or {@code none}
 * @param minimumConfidenceMargin minimum module confidence margin from first sampling evidence, or {@code -1}
 * @param averageConfidenceMargin average module confidence margin from first sampling evidence, or {@code -1}
 */
record CaptureMediaFixtureMetrics(
        String scenarioId,
        CaptureMediaReceiverStatus receiverStatus,
        int submittedMediaCount,
        int readableMediaCount,
        int acceptedCandidateCount,
        int rejectedCandidateCount,
        int duplicateMediaFrameCount,
        int decodedTileCount,
        int recoveredUniqueFrameCount,
        boolean eligibleForRestore,
        boolean restored,
        long restoredFileCount,
        String primaryDiagnosticCode,
        String paletteFallbackStatus,
        String samplingStatus,
        int samplingReadableModuleCount,
        int samplingAmbiguousModuleCount,
        int samplingUnreadableModuleCount,
        int samplingTileDecodeAttemptCount,
        int samplingAcceptedPayloadCount,
        String downstreamFailureStages,
        double minimumConfidenceMargin,
        double averageConfidenceMargin
) {

    /**
     * Creates validated fixture metrics.
     *
     * @param scenarioId stable corpus scenario id
     * @param receiverStatus receiver status
     * @param submittedMediaCount submitted media count
     * @param readableMediaCount readable media count
     * @param acceptedCandidateCount accepted candidate count
     * @param rejectedCandidateCount rejected candidate count
     * @param duplicateMediaFrameCount duplicate media frame count
     * @param decodedTileCount decoded tile payload count
     * @param recoveredUniqueFrameCount recovered unique frame count
     * @param eligibleForRestore restore eligibility flag
     * @param restored restore success flag
     * @param restoredFileCount restored file count
     * @param primaryDiagnosticCode primary diagnostic code label
     * @param paletteFallbackStatus current palette path label
     * @param samplingStatus source-space sampling status label
     * @param samplingReadableModuleCount readable module count
     * @param samplingAmbiguousModuleCount ambiguous module count
     * @param samplingUnreadableModuleCount unreadable module count
     * @param samplingTileDecodeAttemptCount tile decode attempt count
     * @param samplingAcceptedPayloadCount accepted payload count
     * @param downstreamFailureStages downstream failure-stage label
     * @param minimumConfidenceMargin minimum confidence margin
     * @param averageConfidenceMargin average confidence margin
     */
    CaptureMediaFixtureMetrics {
        requireText(scenarioId, "scenarioId");
        Objects.requireNonNull(receiverStatus, "receiverStatus must not be null");
        requireNonNegative(submittedMediaCount, "submittedMediaCount");
        requireNonNegative(readableMediaCount, "readableMediaCount");
        requireNonNegative(acceptedCandidateCount, "acceptedCandidateCount");
        requireNonNegative(rejectedCandidateCount, "rejectedCandidateCount");
        requireNonNegative(duplicateMediaFrameCount, "duplicateMediaFrameCount");
        requireNonNegative(decodedTileCount, "decodedTileCount");
        requireNonNegative(recoveredUniqueFrameCount, "recoveredUniqueFrameCount");
        if (restoredFileCount < 0) {
            throw new IllegalArgumentException("restoredFileCount must be non-negative");
        }
        requireText(primaryDiagnosticCode, "primaryDiagnosticCode");
        requireText(paletteFallbackStatus, "paletteFallbackStatus");
        requireText(samplingStatus, "samplingStatus");
        requireNonNegative(samplingReadableModuleCount, "samplingReadableModuleCount");
        requireNonNegative(samplingAmbiguousModuleCount, "samplingAmbiguousModuleCount");
        requireNonNegative(samplingUnreadableModuleCount, "samplingUnreadableModuleCount");
        requireNonNegative(samplingTileDecodeAttemptCount, "samplingTileDecodeAttemptCount");
        requireNonNegative(samplingAcceptedPayloadCount, "samplingAcceptedPayloadCount");
        requireText(downstreamFailureStages, "downstreamFailureStages");
        requireFinite(minimumConfidenceMargin, "minimumConfidenceMargin");
        requireFinite(averageConfidenceMargin, "averageConfidenceMargin");
    }

    /**
     * Creates a baseline metric snapshot from receiver output and optional source-space sampling evidence.
     *
     * @param scenarioId stable corpus scenario id
     * @param result receiver result to snapshot
     * @param samplingEvidence first available source-space sampling evidence for the fixture
     * @return fixture metrics snapshot
     */
    static CaptureMediaFixtureMetrics from(
            String scenarioId,
            CaptureMediaReceiverResult result,
            Optional<ModuleSamplingEvidence> samplingEvidence
    ) {
        Objects.requireNonNull(result, "result must not be null");
        samplingEvidence = Objects.requireNonNull(samplingEvidence, "samplingEvidence must not be null");
        CaptureMediaSummary summary = result.summary();
        Optional<ModuleSamplingEvidence> evidence = samplingEvidence;
        return new CaptureMediaFixtureMetrics(
                scenarioId,
                result.status(),
                summary.submittedMediaCount(),
                summary.readableMediaCount(),
                summary.acceptedCandidateCount(),
                summary.rejectedCandidateCount(),
                summary.duplicateMediaFrameCount(),
                summary.decodedTileCount(),
                summary.recoveredUniqueFrameCount(),
                result.eligibleForRestore(),
                result.restored(),
                summary.restoredFileCount(),
                primaryDiagnosticCode(result),
                evidence.map(ModuleSamplingEvidence::observedPaletteEvidence)
                        .map(palette -> palette.status().name())
                        .orElse("NOT_AVAILABLE"),
                evidence.map(ModuleSamplingEvidence::status)
                        .map(ModuleSamplingStatus::name)
                        .orElse("NOT_AVAILABLE"),
                evidence.map(ModuleSamplingEvidence::readableModuleCount).orElse(0),
                evidence.map(ModuleSamplingEvidence::ambiguousModuleCount).orElse(0),
                evidence.map(ModuleSamplingEvidence::unreadableModuleCount).orElse(0),
                evidence.map(ModuleSamplingEvidence::tileDecodeAttemptCount).orElse(0),
                evidence.map(ModuleSamplingEvidence::acceptedPayloadCount).orElse(0),
                evidence.map(CaptureMediaFixtureMetrics::downstreamFailureStages).orElse("not_available"),
                evidence.map(value -> metric(value, "min")).orElse(-1.0d),
                evidence.map(value -> metric(value, "mean")).orElse(-1.0d)
        );
    }

    private static String primaryDiagnosticCode(CaptureMediaReceiverResult result) {
        return result.diagnostics().stream()
                .filter(CaptureMediaDiagnostic::blocking)
                .findFirst()
                .or(() -> result.diagnostics().stream().findFirst())
                .map(diagnostic -> diagnostic.code().name())
                .orElse("none");
    }

    private static String downstreamFailureStages(ModuleSamplingEvidence evidence) {
        if (evidence.tileDecodeFailureStages().isEmpty()) {
            return "none";
        }
        return String.join(",", evidence.tileDecodeFailureStages());
    }

    private static double metric(ModuleSamplingEvidence evidence, String metricName) {
        return evidence.confidenceMarginSummary().getOrDefault(metricName, -1.0d);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}
