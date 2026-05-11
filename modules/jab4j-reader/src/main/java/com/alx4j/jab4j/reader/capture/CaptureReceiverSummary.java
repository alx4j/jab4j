package com.alx4j.jab4j.reader.capture;

/**
 * Aggregate counts reported by the capture receiver.
 *
 * @param submittedFrameCount number of input frame sources submitted or discovered
 * @param readableFrameCount number of frame sources decoded as images
 * @param acceptedCandidateCount number of usable candidate frames accepted for decode
 * @param rejectedFrameCount number of frame sources rejected by intake or qualification
 * @param duplicateFrameCount number of duplicate frames or payloads detected
 * @param uncertainFrameCount number of readable frames that could not be confidently accepted or rejected
 * @param decodedTileCount number of decoded tile payloads
 * @param restoredFileCount number of files restored when restore succeeds
 */
public record CaptureReceiverSummary(
        int submittedFrameCount,
        int readableFrameCount,
        int acceptedCandidateCount,
        int rejectedFrameCount,
        int duplicateFrameCount,
        int uncertainFrameCount,
        int decodedTileCount,
        long restoredFileCount
) {

    /**
     * Creates a validated capture receiver summary.
     *
     * @param submittedFrameCount submitted or discovered frame count
     * @param readableFrameCount readable image count
     * @param acceptedCandidateCount accepted candidate count
     * @param rejectedFrameCount rejected frame count
     * @param duplicateFrameCount duplicate frame count
     * @param uncertainFrameCount uncertain frame count
     * @param decodedTileCount decoded tile payload count
     * @param restoredFileCount restored file count
     */
    public CaptureReceiverSummary {
        if (submittedFrameCount < 0
                || readableFrameCount < 0
                || acceptedCandidateCount < 0
                || rejectedFrameCount < 0
                || duplicateFrameCount < 0
                || uncertainFrameCount < 0
                || decodedTileCount < 0
                || restoredFileCount < 0) {
            throw new IllegalArgumentException("summary counts must be non-negative");
        }
        if (readableFrameCount > submittedFrameCount) {
            throw new IllegalArgumentException("readableFrameCount must not exceed submittedFrameCount");
        }
        if (acceptedCandidateCount > readableFrameCount) {
            throw new IllegalArgumentException("acceptedCandidateCount must not exceed readableFrameCount");
        }
        if (rejectedFrameCount > submittedFrameCount) {
            throw new IllegalArgumentException("rejectedFrameCount must not exceed submittedFrameCount");
        }
        if (duplicateFrameCount > readableFrameCount) {
            throw new IllegalArgumentException("duplicateFrameCount must not exceed readableFrameCount");
        }
        if (uncertainFrameCount > readableFrameCount) {
            throw new IllegalArgumentException("uncertainFrameCount must not exceed readableFrameCount");
        }
    }

    /**
     * Creates an empty summary for a result that has not discovered any frame sources.
     *
     * @return summary with all counts set to zero
     */
    public static CaptureReceiverSummary empty() {
        return new CaptureReceiverSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Indicates whether any capture candidates were accepted for decoding.
     *
     * @return true when accepted candidate count is greater than zero
     */
    public boolean hasAcceptedCandidates() {
        return acceptedCandidateCount > 0;
    }
}
