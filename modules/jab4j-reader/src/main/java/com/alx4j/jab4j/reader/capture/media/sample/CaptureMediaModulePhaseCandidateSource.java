package com.alx4j.jab4j.reader.capture.media.sample;

/**
 * Source category for a generated module phase candidate.
 */
public enum CaptureMediaModulePhaseCandidateSource {
    /**
     * The layout-derived nominal module center and module size.
     */
    NOMINAL,

    /**
     * A high-confidence external evidence offset without additional bounded shifts.
     */
    CV_EVIDENCE,

    /**
     * A bounded center-shift or module-scale variant around nominal or evidence geometry.
     */
    BOUNDED_SEARCH
}
