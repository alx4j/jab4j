package com.alx4j.jab4j.reader.capture.media.evidence;

/**
 * Candidate-scoped observed palette extraction and safety state.
 */
public enum ObservedPaletteStatus {
    NOT_AVAILABLE,
    EXTRACTED,
    SAFE_FOR_DIAGNOSTICS,
    SAFE_FOR_CLASSIFICATION,
    UNSAFE,
    WITHHELD,
    FALLBACK_EXACT
}
