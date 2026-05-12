package com.alx4j.jab4j.reader.capture.media;

/**
 * Aggregate counts reported by the media receiver.
 *
 * @param submittedMediaCount number of media sources submitted or discovered
 * @param readableMediaCount number of media sources decoded or adapted into readable images
 * @param acceptedCandidateCount number of usable media candidates accepted for decode
 * @param rejectedCandidateCount number of media candidates rejected by intake or qualification
 * @param uncertainCandidateCount number of readable candidates that could not be confidently accepted or rejected
 * @param duplicateMediaFrameCount number of duplicate media frames detected
 * @param recoveredUniqueFrameCount number of unique frame identities recovered from media candidates
 * @param decodedTileCount number of decoded tile payloads
 * @param restoredFileCount number of files restored when restore succeeds
 */
public record CaptureMediaSummary(
        int submittedMediaCount,
        int readableMediaCount,
        int acceptedCandidateCount,
        int rejectedCandidateCount,
        int uncertainCandidateCount,
        int duplicateMediaFrameCount,
        int recoveredUniqueFrameCount,
        int decodedTileCount,
        long restoredFileCount
) {

    /**
     * Creates a validated media summary.
     *
     * @param submittedMediaCount submitted or discovered media source count
     * @param readableMediaCount readable media count
     * @param acceptedCandidateCount accepted candidate count
     * @param rejectedCandidateCount rejected candidate count
     * @param uncertainCandidateCount uncertain candidate count
     * @param duplicateMediaFrameCount duplicate media frame count
     * @param recoveredUniqueFrameCount recovered unique frame count
     * @param decodedTileCount decoded tile payload count
     * @param restoredFileCount restored file count
     */
    public CaptureMediaSummary {
        if (submittedMediaCount < 0
                || readableMediaCount < 0
                || acceptedCandidateCount < 0
                || rejectedCandidateCount < 0
                || uncertainCandidateCount < 0
                || duplicateMediaFrameCount < 0
                || recoveredUniqueFrameCount < 0
                || decodedTileCount < 0
                || restoredFileCount < 0) {
            throw new IllegalArgumentException("summary counts must be non-negative");
        }
        if (readableMediaCount > submittedMediaCount) {
            throw new IllegalArgumentException("readableMediaCount must not exceed submittedMediaCount");
        }
        if (acceptedCandidateCount > readableMediaCount) {
            throw new IllegalArgumentException("acceptedCandidateCount must not exceed readableMediaCount");
        }
        if (rejectedCandidateCount > submittedMediaCount) {
            throw new IllegalArgumentException("rejectedCandidateCount must not exceed submittedMediaCount");
        }
        if (uncertainCandidateCount > readableMediaCount) {
            throw new IllegalArgumentException("uncertainCandidateCount must not exceed readableMediaCount");
        }
        if (duplicateMediaFrameCount > readableMediaCount) {
            throw new IllegalArgumentException("duplicateMediaFrameCount must not exceed readableMediaCount");
        }
        if (recoveredUniqueFrameCount > acceptedCandidateCount) {
            throw new IllegalArgumentException("recoveredUniqueFrameCount must not exceed acceptedCandidateCount");
        }
    }

    /**
     * Creates an empty summary for a result that has not discovered any media sources.
     *
     * @return summary with all counts set to zero
     */
    public static CaptureMediaSummary empty() {
        return new CaptureMediaSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Indicates whether any media candidates were accepted for decoding.
     *
     * @return true when accepted candidate count is greater than zero
     */
    public boolean hasAcceptedCandidates() {
        return acceptedCandidateCount > 0;
    }

    /**
     * Indicates whether any unique frame identities were recovered from media candidates.
     *
     * @return true when recovered unique frame count is greater than zero
     */
    public boolean hasRecoveredUniqueFrames() {
        return recoveredUniqueFrameCount > 0;
    }
}
