package com.alx4j.jab4j.writer.app;

import java.util.Objects;

/**
 * Reproducibility metadata that ties one run to its config, protocol, build, and codec lineage.
 *
 * @param selectedProfile selected built-in profile id
 * @param writerBuildId writer build identifier
 * @param protocolVersionDisplay human-readable protocol version
 * @param protocolCompatibilityVersion machine-readable compatibility version
 * @param effectiveConfigSha256 digest of the effective config JSON
 * @param manifestFingerprint deterministic manifest fingerprint
 * @param finalSessionDigest deterministic final session digest
 * @param codecProfileHash deterministic codec snapshot hash
 */
public record WriterReproducibilityMetadata(
        String selectedProfile,
        String writerBuildId,
        String protocolVersionDisplay,
        int protocolCompatibilityVersion,
        String effectiveConfigSha256,
        String manifestFingerprint,
        String finalSessionDigest,
        String codecProfileHash
) {

    /**
     * Creates validated reproducibility metadata.
     *
     * @param selectedProfile selected built-in profile id
     * @param writerBuildId writer build identifier
     * @param protocolVersionDisplay human-readable protocol version
     * @param protocolCompatibilityVersion machine-readable compatibility version
     * @param effectiveConfigSha256 effective-config digest
     * @param manifestFingerprint manifest fingerprint
     * @param finalSessionDigest final session digest
     * @param codecProfileHash deterministic snapshot hash
     */
    public WriterReproducibilityMetadata {
        requireText(selectedProfile, "selectedProfile");
        requireText(writerBuildId, "writerBuildId");
        requireText(protocolVersionDisplay, "protocolVersionDisplay");
        if (protocolCompatibilityVersion <= 0) {
            throw new WriterJobException(
                    WriterJobStatus.FAILED,
                    "protocolCompatibilityVersion must be positive"
            );
        }
        requireText(effectiveConfigSha256, "effectiveConfigSha256");
        requireText(manifestFingerprint, "manifestFingerprint");
        requireText(finalSessionDigest, "finalSessionDigest");
        requireText(codecProfileHash, "codecProfileHash");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new WriterJobException(WriterJobStatus.FAILED, field + " must not be blank");
        }
    }
}
