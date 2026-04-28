package com.alx4j.jab4j.transfer;

/**
 * Marker for deterministic logical transport records.
 */
public sealed interface TransportRecord
        permits FileChunkRecord, FileHeaderRecord, ManifestRecord, ParityRecord, SessionEndRecord, SessionHeaderRecord {

    /**
     * Returns the record category.
     *
     * @return record category
     */
    TransportRecordCategory category();

    /**
     * Returns the zero-based sequence number within the logical transport stream.
     *
     * @return logical record sequence number
     */
    long sequenceNumber();
}
