package com.alx4j.jab4j.reader.capture;

import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostic emitted while evaluating a capture input source or capture set.
 *
 * @param code stable diagnostic reason code
 * @param sourceId optional caller-visible source identifier, such as a file name or path
 * @param callerOrder optional zero-based caller order for frame collections that preserve ordering
 * @param message human-readable diagnostic detail
 */
public record CaptureFrameDiagnostic(
        CaptureDiagnosticCode code,
        Optional<String> sourceId,
        Optional<Integer> callerOrder,
        String message
) {

    /**
     * Creates a validated capture diagnostic.
     *
     * @param code stable diagnostic reason code
     * @param sourceId optional caller-visible source identifier
     * @param callerOrder optional zero-based caller order
     * @param message diagnostic detail
     */
    public CaptureFrameDiagnostic {
        Objects.requireNonNull(code, "code must not be null");
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
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Creates a diagnostic for a specific capture source.
     *
     * @param code stable diagnostic reason code
     * @param sourceId caller-visible source identifier
     * @param message diagnostic detail
     * @return source-scoped capture diagnostic
     */
    public static CaptureFrameDiagnostic forSource(CaptureDiagnosticCode code, String sourceId, String message) {
        return new CaptureFrameDiagnostic(
                code,
                Optional.of(Objects.requireNonNull(sourceId, "sourceId must not be null")),
                Optional.empty(),
                message
        );
    }

    /**
     * Creates a diagnostic for a specific ordered capture source.
     *
     * @param code stable diagnostic reason code
     * @param sourceId caller-visible source identifier
     * @param callerOrder zero-based caller order
     * @param message diagnostic detail
     * @return ordered source-scoped capture diagnostic
     */
    public static CaptureFrameDiagnostic forSource(
            CaptureDiagnosticCode code,
            String sourceId,
            int callerOrder,
            String message
    ) {
        return new CaptureFrameDiagnostic(
                code,
                Optional.of(Objects.requireNonNull(sourceId, "sourceId must not be null")),
                Optional.of(callerOrder),
                message
        );
    }

    /**
     * Creates a diagnostic that applies to the capture set rather than one frame source.
     *
     * @param code stable diagnostic reason code
     * @param message diagnostic detail
     * @return capture-set diagnostic
     */
    public static CaptureFrameDiagnostic forCaptureSet(CaptureDiagnosticCode code, String message) {
        return new CaptureFrameDiagnostic(code, Optional.empty(), Optional.empty(), message);
    }

    /**
     * Indicates whether this diagnostic points at a specific frame source or caller order.
     *
     * @return true when source or order context is available
     */
    public boolean frameScoped() {
        return sourceId.isPresent() || callerOrder.isPresent();
    }
}
