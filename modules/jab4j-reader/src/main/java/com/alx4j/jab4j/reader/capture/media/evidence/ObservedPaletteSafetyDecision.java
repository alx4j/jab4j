package com.alx4j.jab4j.reader.capture.media.evidence;

/**
 * Explicit decision for whether observed palette evidence may influence classification.
 */
public enum ObservedPaletteSafetyDecision {
    NOT_EVALUATED,
    DIAGNOSTIC_ONLY,
    SAFE_FOR_CLASSIFICATION,
    UNSAFE,
    FALLBACK_EXACT,
    WITHHELD
}
