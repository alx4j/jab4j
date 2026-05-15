package com.alx4j.jab4j.reader.capture.media.cv;

/**
 * Coarse backend-neutral status for capture-media CV analysis.
 */
public enum CvDetectionStatus {
    /**
     * The backend found candidates or normalized frames that can proceed to JAB-specific decoding.
     */
    ACCEPTED,

    /**
     * The backend rejected the frame with a stable media diagnostic code.
     */
    REJECTED,

    /**
     * The backend found evidence, but the detected frame is below the supported coverage threshold.
     */
    TOO_SMALL,

    /**
     * The backend found multiple plausible candidates and no safe dominant candidate.
     */
    AMBIGUOUS,

    /**
     * The backend failed internally and mapped that failure to a stable media diagnostic.
     */
    BACKEND_FAILURE
}
