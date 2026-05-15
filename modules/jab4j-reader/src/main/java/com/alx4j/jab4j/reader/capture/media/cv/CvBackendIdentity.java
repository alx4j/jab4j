package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral metadata describing the selected capture-media CV backend.
 *
 * @param backendId stable backend identifier for diagnostics and tests
 * @param implementationArtifact implementation artifact coordinate, when cheaply available
 * @param implementationVersion implementation artifact version, when cheaply available
 * @param featureFlags stable feature flags selected by the backend
 */
public record CvBackendIdentity(
        String backendId,
        Optional<String> implementationArtifact,
        Optional<String> implementationVersion,
        List<String> featureFlags
) {

    /**
     * Creates validated backend identity metadata.
     *
     * @param backendId stable backend identifier
     * @param implementationArtifact implementation artifact coordinate, when known
     * @param implementationVersion implementation artifact version, when known
     * @param featureFlags selected backend feature flags
     */
    public CvBackendIdentity {
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId must not be blank");
        }
        Objects.requireNonNull(implementationArtifact, "implementationArtifact must not be null");
        implementationArtifact.ifPresent(artifact -> requireText(artifact, "implementationArtifact"));
        Objects.requireNonNull(implementationVersion, "implementationVersion must not be null");
        implementationVersion.ifPresent(version -> requireText(version, "implementationVersion"));
        featureFlags = List.copyOf(Objects.requireNonNull(featureFlags, "featureFlags must not be null"));
        if (featureFlags.stream().anyMatch(flag -> flag == null || flag.isBlank())) {
            throw new IllegalArgumentException("featureFlags must not contain blank values");
        }
    }

    /**
     * Creates an identity for a backend that does not expose implementation metadata.
     *
     * @param backendId stable backend identifier
     * @return backend identity without artifact or version details
     */
    public static CvBackendIdentity unspecified(String backendId) {
        return new CvBackendIdentity(backendId, Optional.empty(), Optional.empty(), List.of());
    }

    private static void requireText(String value, String fieldName) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not contain blank values");
        }
    }
}
