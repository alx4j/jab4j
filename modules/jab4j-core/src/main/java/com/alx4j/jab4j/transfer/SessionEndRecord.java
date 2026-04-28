package com.alx4j.jab4j.transfer;

/**
 * Logical end-of-session record that closes one transport stream.
 *
 * @param sequenceNumber zero-based logical record sequence
 * @param totalDataRecords total logical data records emitted
 * @param totalParityRecords total logical parity records emitted
 * @param finalSessionDigest deterministic final session digest
 */
public record SessionEndRecord(
        long sequenceNumber,
        long totalDataRecords,
        long totalParityRecords,
        String finalSessionDigest
) implements TransportRecord {

    /**
     * Creates a validated session-end record.
     *
     * @param sequenceNumber zero-based logical record sequence
     * @param totalDataRecords total logical data records
     * @param totalParityRecords total logical parity records
     * @param finalSessionDigest deterministic final session digest
     */
    public SessionEndRecord {
        if (sequenceNumber < 0 || totalDataRecords < 0 || totalParityRecords < 0) {
            throw new IllegalArgumentException("session-end counts must be non-negative");
        }
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new IllegalArgumentException("finalSessionDigest must not be blank");
        }
    }

    @Override
    public TransportRecordCategory category() {
        return TransportRecordCategory.SESSION_END_RECORD;
    }
}
