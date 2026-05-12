package com.alx4j.jab4j.reader.capture.media;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostic emitted while evaluating one media source or a submitted media set.
 *
 * @param code stable diagnostic reason code
 * @param severity diagnostic severity
 * @param blocking whether this diagnostic blocks restore; must match severity
 * @param sourceKind optional media source kind when the diagnostic points at a source
 * @param sourceId optional caller-visible source identifier, such as a file name or path
 * @param callerOrder optional zero-based caller order for source collections that preserve ordering
 * @param timestampMillis optional media timestamp in milliseconds
 * @param frameNumber optional media frame number
 * @param metrics optional metric name and value pairs for support and tests
 * @param message human-readable diagnostic detail
 */
public record CaptureMediaDiagnostic(
        CaptureMediaDiagnosticCode code,
        CaptureMediaDiagnosticSeverity severity,
        boolean blocking,
        Optional<CaptureMediaSourceKind> sourceKind,
        Optional<String> sourceId,
        Optional<Integer> callerOrder,
        Optional<Long> timestampMillis,
        Optional<Long> frameNumber,
        Map<String, Double> metrics,
        String message
) {

    /**
     * Creates a validated media diagnostic.
     *
     * @param code stable diagnostic reason code
     * @param severity diagnostic severity
     * @param blocking whether this diagnostic blocks restore
     * @param sourceKind optional media source kind
     * @param sourceId optional caller-visible source identifier
     * @param callerOrder optional zero-based caller order
     * @param timestampMillis optional media timestamp in milliseconds
     * @param frameNumber optional media frame number
     * @param metrics optional metric name and value pairs
     * @param message diagnostic detail
     */
    public CaptureMediaDiagnostic {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (blocking != severity.blocksRestore()) {
            throw new IllegalArgumentException("blocking must match severity");
        }
        sourceKind = Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null");
        sourceId.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("sourceId must not be blank when present");
            }
        });
        callerOrder = Objects.requireNonNull(callerOrder, "callerOrder must not be null");
        callerOrder.ifPresent(value -> {
            if (value < 0) {
                throw new IllegalArgumentException("callerOrder must be non-negative when present");
            }
        });
        timestampMillis = Objects.requireNonNull(timestampMillis, "timestampMillis must not be null");
        timestampMillis.ifPresent(value -> {
            if (value < 0) {
                throw new IllegalArgumentException("timestampMillis must be non-negative when present");
            }
        });
        frameNumber = Objects.requireNonNull(frameNumber, "frameNumber must not be null");
        frameNumber.ifPresent(value -> {
            if (value < 0) {
                throw new IllegalArgumentException("frameNumber must be non-negative when present");
            }
        });
        if (sourceKind.isEmpty()
                && (sourceId.isPresent()
                || callerOrder.isPresent()
                || timestampMillis.isPresent()
                || frameNumber.isPresent())) {
            throw new IllegalArgumentException("sourceKind must be present when source context is present");
        }
        metrics = copyMetrics(metrics);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Creates a diagnostic that applies to the media set rather than one source.
     *
     * @param code stable diagnostic reason code
     * @param severity diagnostic severity
     * @param message diagnostic detail
     * @return media-set diagnostic
     */
    public static CaptureMediaDiagnostic forMediaSet(
            CaptureMediaDiagnosticCode code,
            CaptureMediaDiagnosticSeverity severity,
            String message
    ) {
        return new CaptureMediaDiagnostic(
                code,
                severity,
                severity.blocksRestore(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                message
        );
    }

    /**
     * Creates a diagnostic for a specific media source.
     *
     * @param code stable diagnostic reason code
     * @param severity diagnostic severity
     * @param sourceKind media source kind
     * @param sourceId caller-visible source identifier
     * @param message diagnostic detail
     * @return source-scoped media diagnostic
     */
    public static CaptureMediaDiagnostic forSource(
            CaptureMediaDiagnosticCode code,
            CaptureMediaDiagnosticSeverity severity,
            CaptureMediaSourceKind sourceKind,
            String sourceId,
            String message
    ) {
        return new CaptureMediaDiagnostic(
                code,
                severity,
                severity.blocksRestore(),
                Optional.of(Objects.requireNonNull(sourceKind, "sourceKind must not be null")),
                Optional.of(Objects.requireNonNull(sourceId, "sourceId must not be null")),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                message
        );
    }

    /**
     * Creates a diagnostic for a specific ordered media source.
     *
     * @param code stable diagnostic reason code
     * @param severity diagnostic severity
     * @param sourceKind media source kind
     * @param sourceId caller-visible source identifier
     * @param callerOrder zero-based caller order
     * @param message diagnostic detail
     * @return ordered source-scoped media diagnostic
     */
    public static CaptureMediaDiagnostic forSource(
            CaptureMediaDiagnosticCode code,
            CaptureMediaDiagnosticSeverity severity,
            CaptureMediaSourceKind sourceKind,
            String sourceId,
            int callerOrder,
            String message
    ) {
        return new CaptureMediaDiagnostic(
                code,
                severity,
                severity.blocksRestore(),
                Optional.of(Objects.requireNonNull(sourceKind, "sourceKind must not be null")),
                Optional.of(Objects.requireNonNull(sourceId, "sourceId must not be null")),
                Optional.of(callerOrder),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                message
        );
    }

    /**
     * Indicates whether this diagnostic points at a specific media source or media position.
     *
     * @return true when source kind, source id, order, timestamp, or frame number context is available
     */
    public boolean sourceScoped() {
        return sourceKind.isPresent()
                || sourceId.isPresent()
                || callerOrder.isPresent()
                || timestampMillis.isPresent()
                || frameNumber.isPresent();
    }

    private static Map<String, Double> copyMetrics(Map<String, Double> metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        metrics.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("metric names must not be blank");
            }
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("metric values must be finite");
            }
        });
        return Map.copyOf(metrics);
    }
}
