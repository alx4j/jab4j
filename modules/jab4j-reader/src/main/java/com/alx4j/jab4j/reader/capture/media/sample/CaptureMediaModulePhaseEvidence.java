package com.alx4j.jab4j.reader.capture.media.sample;

/**
 * Optional backend-neutral evidence for an observed module-center offset.
 *
 * @param source stable source identifier for the evidence
 * @param offsetXPx estimated horizontal module-center offset in normalized pixels
 * @param offsetYPx estimated vertical module-center offset in normalized pixels
 * @param confidence confidence that the offset improves module-center sampling
 */
public record CaptureMediaModulePhaseEvidence(
        String source,
        double offsetXPx,
        double offsetYPx,
        double confidence
) {

    /**
     * Creates validated module phase evidence.
     *
     * @param source stable evidence source identifier
     * @param offsetXPx horizontal module-center offset
     * @param offsetYPx vertical module-center offset
     * @param confidence normalized evidence confidence
     */
    public CaptureMediaModulePhaseEvidence {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        source = source.trim();
        requireFinite(offsetXPx, "offsetXPx");
        requireFinite(offsetYPx, "offsetYPx");
        requireUnitScore(confidence, "confidence");
    }

    /**
     * Creates a CV-derived evidence offset without exposing any CV backend type.
     *
     * @param backendId stable CV backend identifier
     * @param offsetXPx horizontal module-center offset
     * @param offsetYPx vertical module-center offset
     * @param confidence normalized evidence confidence
     * @return CV-derived module phase evidence
     */
    public static CaptureMediaModulePhaseEvidence cv(
            String backendId,
            double offsetXPx,
            double offsetYPx,
            double confidence
    ) {
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId must not be blank");
        }
        return new CaptureMediaModulePhaseEvidence(
                "cv:" + backendId.trim(),
                offsetXPx,
                offsetYPx,
                confidence
        );
    }

    /**
     * Returns whether this evidence is strong enough for candidate generation.
     *
     * @param minimumConfidence inclusive confidence threshold
     * @return true when confidence meets the threshold
     */
    public boolean highConfidence(double minimumConfidence) {
        requireUnitScore(minimumConfidence, "minimumConfidence");
        return confidence >= minimumConfidence;
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }

    private static void requireUnitScore(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
