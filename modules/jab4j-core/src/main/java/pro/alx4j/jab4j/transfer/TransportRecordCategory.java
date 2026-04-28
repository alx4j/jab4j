package pro.alx4j.jab4j.transfer;

/**
 * Logical transport record categories mandated by the architecture.
 */
public enum TransportRecordCategory {
    SESSION_HEADER_RECORD,
    MANIFEST_RECORD,
    FILE_HEADER_RECORD,
    FILE_CHUNK_RECORD,
    PARITY_RECORD,
    SESSION_END_RECORD
}
