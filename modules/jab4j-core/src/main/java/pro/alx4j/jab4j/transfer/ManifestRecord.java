package pro.alx4j.jab4j.transfer;

import java.util.Objects;
import pro.alx4j.jab4j.api.model.Manifest;

/**
 * Logical manifest record for one deterministic manifest fragment.
 *
 * @param sequenceNumber zero-based logical record sequence
 * @param fragmentIndex zero-based manifest fragment index
 * @param totalFragments total manifest fragments
 * @param manifest manifest payload for the fragment
 */
public record ManifestRecord(long sequenceNumber, int fragmentIndex, int totalFragments, Manifest manifest)
        implements TransportRecord {

    /**
     * Creates a validated manifest record.
     *
     * @param sequenceNumber zero-based logical record sequence
     * @param fragmentIndex zero-based fragment index
     * @param totalFragments total manifest fragments
     * @param manifest manifest payload
     */
    public ManifestRecord {
        if (sequenceNumber < 0 || fragmentIndex < 0) {
            throw new IllegalArgumentException("sequenceNumber and fragmentIndex must be non-negative");
        }
        if (totalFragments <= 0 || fragmentIndex >= totalFragments) {
            throw new IllegalArgumentException("manifest fragment indexes must stay within totalFragments");
        }
        Objects.requireNonNull(manifest, "manifest must not be null");
    }

    @Override
    public TransportRecordCategory category() {
        return TransportRecordCategory.MANIFEST_RECORD;
    }
}
