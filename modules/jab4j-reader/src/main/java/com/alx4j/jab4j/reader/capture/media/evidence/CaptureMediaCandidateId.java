package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic identity chain for one capture-media source, proposal, and downstream evidence stages.
 *
 * @param sourceImageId stable source image ID based on source kind, order, source ID, and pixel hash prefix
 * @param proposalCandidateId stable proposal candidate ID, when this identity is proposal-scoped or narrower
 * @param patternEvidenceId stable pattern evidence ID, when pattern evidence has been scoped
 * @param geometryCandidateId stable geometry candidate ID, when a geometry candidate has been scoped
 * @param samplingCandidateId stable source-space sampling candidate ID, when sampling has been scoped
 * @param refinementCandidateId stable local-refinement candidate ID, when refinement has been scoped
 * @param decodeAttemptId stable decode-attempt ID, when a sampled or refined candidate is decoded
 */
public record CaptureMediaCandidateId(
        String sourceImageId,
        Optional<String> proposalCandidateId,
        Optional<String> patternEvidenceId,
        Optional<String> geometryCandidateId,
        Optional<String> samplingCandidateId,
        Optional<String> refinementCandidateId,
        Optional<String> decodeAttemptId
) {

    public static final int PIXEL_HASH_PREFIX_LENGTH = 12;
    public static final int MAX_GEOMETRY_CANDIDATE_RANK = 3;
    public static final int MAX_SAMPLING_VARIANT_RANK = 3;

    /**
     * Creates a validated deterministic ID chain.
     *
     * @param sourceImageId stable source image ID
     * @param proposalCandidateId optional proposal candidate ID
     * @param patternEvidenceId optional pattern evidence ID
     * @param geometryCandidateId optional geometry candidate ID
     * @param samplingCandidateId optional sampling candidate ID
     * @param refinementCandidateId optional refinement candidate ID
     * @param decodeAttemptId optional decode attempt ID
     */
    public CaptureMediaCandidateId {
        sourceImageId = EvidenceValidation.requireText(sourceImageId, "sourceImageId");
        proposalCandidateId = EvidenceValidation.copyOptionalText(proposalCandidateId, "proposalCandidateId");
        patternEvidenceId = EvidenceValidation.copyOptionalText(patternEvidenceId, "patternEvidenceId");
        geometryCandidateId = EvidenceValidation.copyOptionalText(geometryCandidateId, "geometryCandidateId");
        samplingCandidateId = EvidenceValidation.copyOptionalText(samplingCandidateId, "samplingCandidateId");
        refinementCandidateId = EvidenceValidation.copyOptionalText(refinementCandidateId, "refinementCandidateId");
        decodeAttemptId = EvidenceValidation.copyOptionalText(decodeAttemptId, "decodeAttemptId");
        requireHierarchy(
                sourceImageId,
                proposalCandidateId,
                patternEvidenceId,
                geometryCandidateId,
                samplingCandidateId,
                refinementCandidateId,
                decodeAttemptId
        );
    }

    /**
     * Builds the stable source image ID string.
     *
     * @param sourceKind media source kind name
     * @param callerOrder deterministic caller-provided source order
     * @param sourceId caller-visible source identifier
     * @param pixelSha256 SHA-256 source-pixel hash, of which the first 12 hex characters are retained
     * @return deterministic source image ID
     */
    public static String sourceImageId(
            String sourceKind,
            int callerOrder,
            String sourceId,
            String pixelSha256
    ) {
        String kind = EvidenceValidation.requireText(sourceKind, "sourceKind");
        EvidenceValidation.requireNonNegative(callerOrder, "callerOrder");
        String id = EvidenceValidation.requireText(sourceId, "sourceId");
        return kind + ":" + callerOrder + ":" + id + ":" + shortPixelHashPrefix(pixelSha256);
    }

    /**
     * Builds the stable proposal candidate ID string from source metadata and proposal ranks.
     *
     * @param sourceKind media source kind name
     * @param callerOrder deterministic caller-provided source order
     * @param sourceId caller-visible source identifier
     * @param pixelSha256 SHA-256 source-pixel hash
     * @param sourceRegionRank one-based source-region rank
     * @param profileAlternativeRank one-based profile-alternative rank
     * @param profileAlternativeCount profile-alternative count for the source region
     * @return deterministic proposal candidate ID
     */
    public static String proposalCandidateId(
            String sourceKind,
            int callerOrder,
            String sourceId,
            String pixelSha256,
            int sourceRegionRank,
            int profileAlternativeRank,
            int profileAlternativeCount
    ) {
        return proposalCandidateId(
                sourceImageId(sourceKind, callerOrder, sourceId, pixelSha256),
                sourceRegionRank,
                profileAlternativeRank,
                profileAlternativeCount
        );
    }

    /**
     * Builds the stable proposal candidate ID string from a source image ID and proposal ranks.
     *
     * @param sourceImageId stable source image ID
     * @param sourceRegionRank one-based source-region rank
     * @param profileAlternativeRank one-based profile-alternative rank
     * @param profileAlternativeCount profile-alternative count for the source region
     * @return deterministic proposal candidate ID
     */
    public static String proposalCandidateId(
            String sourceImageId,
            int sourceRegionRank,
            int profileAlternativeRank,
            int profileAlternativeCount
    ) {
        String source = EvidenceValidation.requireText(sourceImageId, "sourceImageId");
        requireProposalRanks(sourceRegionRank, profileAlternativeRank, profileAlternativeCount);
        return source + "/sr" + sourceRegionRank + "/pa" + profileAlternativeRank + "-of-" + profileAlternativeCount;
    }

    /**
     * Builds the stable pattern evidence ID for a proposal candidate.
     *
     * @param proposalCandidateId stable proposal candidate ID
     * @return deterministic pattern evidence ID
     */
    public static String patternEvidenceId(String proposalCandidateId) {
        return EvidenceValidation.requireText(proposalCandidateId, "proposalCandidateId") + "/pattern-v1";
    }

    /**
     * Builds the stable geometry candidate ID for a proposal candidate and one-based rank.
     *
     * @param proposalCandidateId stable proposal candidate ID
     * @param rank one-based geometry candidate rank, capped by MVP-9 at three
     * @return deterministic geometry candidate ID
     */
    public static String geometryCandidateId(String proposalCandidateId, int rank) {
        requireBoundedRank(rank, MAX_GEOMETRY_CANDIDATE_RANK, "rank");
        return EvidenceValidation.requireText(proposalCandidateId, "proposalCandidateId") + "/geom" + rank;
    }

    /**
     * Builds the stable source-space sampling candidate ID for a geometry candidate.
     *
     * @param geometryCandidateId stable geometry candidate ID
     * @param centralScalePercent central sampling region percentage, such as 50
     * @param variantRank one-based sampling variant rank, capped by MVP-9 at three
     * @return deterministic sampling candidate ID
     */
    public static String samplingCandidateId(
            String geometryCandidateId,
            int centralScalePercent,
            int variantRank
    ) {
        EvidenceValidation.requirePositive(centralScalePercent, "centralScalePercent");
        if (centralScalePercent > 100) {
            throw new IllegalArgumentException("centralScalePercent must not exceed 100");
        }
        requireBoundedRank(variantRank, MAX_SAMPLING_VARIANT_RANK, "variantRank");
        return EvidenceValidation.requireText(geometryCandidateId, "geometryCandidateId")
                + "/sample-scale" + centralScalePercent + "-variant" + variantRank;
    }

    /**
     * Builds the stable local-refinement candidate ID for a sampling candidate.
     *
     * @param samplingCandidateId stable sampling candidate ID
     * @return deterministic refinement candidate ID
     */
    public static String refinementCandidateId(String samplingCandidateId) {
        return EvidenceValidation.requireText(samplingCandidateId, "samplingCandidateId") + "/refine-local-grid-v1";
    }

    /**
     * Builds the stable decode attempt ID for a sampled or locally refined candidate.
     *
     * @param samplingOrRefinementCandidateId stable sampling or refinement candidate ID
     * @param attemptRank one-based decode attempt rank
     * @return deterministic decode attempt ID
     */
    public static String decodeAttemptId(String samplingOrRefinementCandidateId, int attemptRank) {
        EvidenceValidation.requirePositive(attemptRank, "attemptRank");
        return EvidenceValidation.requireText(samplingOrRefinementCandidateId, "samplingOrRefinementCandidateId")
                + "/decode" + attemptRank;
    }

    /**
     * Creates a source-scoped identity from source metadata.
     *
     * @param sourceKind media source kind name
     * @param callerOrder deterministic caller-provided source order
     * @param sourceId caller-visible source identifier
     * @param pixelSha256 SHA-256 source-pixel hash
     * @return source-scoped candidate ID
     */
    public static CaptureMediaCandidateId sourceImage(
            String sourceKind,
            int callerOrder,
            String sourceId,
            String pixelSha256
    ) {
        return new CaptureMediaCandidateId(
                sourceImageId(sourceKind, callerOrder, sourceId, pixelSha256),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates a proposal-scoped identity from source metadata and proposal ranks.
     *
     * @param sourceKind media source kind name
     * @param callerOrder deterministic caller-provided source order
     * @param sourceId caller-visible source identifier
     * @param pixelSha256 SHA-256 source-pixel hash
     * @param sourceRegionRank one-based source-region rank
     * @param profileAlternativeRank one-based profile-alternative rank
     * @param profileAlternativeCount profile-alternative count for the source region
     * @return proposal-scoped candidate ID
     */
    public static CaptureMediaCandidateId proposalCandidate(
            String sourceKind,
            int callerOrder,
            String sourceId,
            String pixelSha256,
            int sourceRegionRank,
            int profileAlternativeRank,
            int profileAlternativeCount
    ) {
        CaptureMediaCandidateId source = sourceImage(sourceKind, callerOrder, sourceId, pixelSha256);
        return proposalCandidate(source, sourceRegionRank, profileAlternativeRank, profileAlternativeCount);
    }

    /**
     * Creates a proposal-scoped identity from a source-scoped identity and proposal ranks.
     *
     * @param sourceImage source-scoped candidate ID
     * @param sourceRegionRank one-based source-region rank
     * @param profileAlternativeRank one-based profile-alternative rank
     * @param profileAlternativeCount profile-alternative count for the source region
     * @return proposal-scoped candidate ID
     */
    public static CaptureMediaCandidateId proposalCandidate(
            CaptureMediaCandidateId sourceImage,
            int sourceRegionRank,
            int profileAlternativeRank,
            int profileAlternativeCount
    ) {
        Objects.requireNonNull(sourceImage, "sourceImage must not be null");
        if (sourceImage.proposalCandidateId().isPresent()) {
            throw new IllegalArgumentException("sourceImage must not already be proposal-scoped");
        }
        return new CaptureMediaCandidateId(
                sourceImage.sourceImageId(),
                Optional.of(proposalCandidateId(
                        sourceImage.sourceImageId(),
                        sourceRegionRank,
                        profileAlternativeRank,
                        profileAlternativeCount
                )),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates a pattern-scoped identity for a proposal candidate.
     *
     * @param proposalCandidate proposal-scoped candidate ID
     * @return pattern-scoped candidate ID
     */
    public static CaptureMediaCandidateId patternEvidence(CaptureMediaCandidateId proposalCandidate) {
        String proposal = requireProposal(proposalCandidate);
        return new CaptureMediaCandidateId(
                proposalCandidate.sourceImageId(),
                Optional.of(proposal),
                Optional.of(patternEvidenceId(proposal)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates a geometry-scoped identity for pattern evidence and a one-based geometry rank.
     *
     * @param patternEvidence pattern-scoped candidate ID
     * @param rank one-based geometry candidate rank
     * @return geometry-scoped candidate ID
     */
    public static CaptureMediaCandidateId geometryCandidate(CaptureMediaCandidateId patternEvidence, int rank) {
        String proposal = requireProposal(patternEvidence);
        requireStage(patternEvidence.patternEvidenceId(), "patternEvidenceId");
        return new CaptureMediaCandidateId(
                patternEvidence.sourceImageId(),
                Optional.of(proposal),
                patternEvidence.patternEvidenceId(),
                Optional.of(geometryCandidateId(proposal, rank)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates a sampling-scoped identity for a geometry candidate.
     *
     * @param geometryCandidate geometry-scoped candidate ID
     * @param centralScalePercent central sampling region percentage
     * @param variantRank one-based sampling variant rank
     * @return sampling-scoped candidate ID
     */
    public static CaptureMediaCandidateId samplingCandidate(
            CaptureMediaCandidateId geometryCandidate,
            int centralScalePercent,
            int variantRank
    ) {
        String proposal = requireProposal(geometryCandidate);
        requireStage(geometryCandidate.patternEvidenceId(), "patternEvidenceId");
        String geometry = requireStage(geometryCandidate.geometryCandidateId(), "geometryCandidateId");
        return new CaptureMediaCandidateId(
                geometryCandidate.sourceImageId(),
                Optional.of(proposal),
                geometryCandidate.patternEvidenceId(),
                Optional.of(geometry),
                Optional.of(samplingCandidateId(geometry, centralScalePercent, variantRank)),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates a refinement-scoped identity for a sampling candidate.
     *
     * @param samplingCandidate sampling-scoped candidate ID
     * @return refinement-scoped candidate ID
     */
    public static CaptureMediaCandidateId refinementCandidate(CaptureMediaCandidateId samplingCandidate) {
        String proposal = requireProposal(samplingCandidate);
        requireStage(samplingCandidate.patternEvidenceId(), "patternEvidenceId");
        String geometry = requireStage(samplingCandidate.geometryCandidateId(), "geometryCandidateId");
        String sampling = requireStage(samplingCandidate.samplingCandidateId(), "samplingCandidateId");
        return new CaptureMediaCandidateId(
                samplingCandidate.sourceImageId(),
                Optional.of(proposal),
                samplingCandidate.patternEvidenceId(),
                Optional.of(geometry),
                Optional.of(sampling),
                Optional.of(refinementCandidateId(sampling)),
                Optional.empty()
        );
    }

    /**
     * Creates a decode-attempt identity for a sampled or refined candidate.
     *
     * @param sampledOrRefinedCandidate sampling- or refinement-scoped candidate ID
     * @param attemptRank one-based decode attempt rank
     * @return decode-attempt-scoped candidate ID
     */
    public static CaptureMediaCandidateId decodeAttempt(
            CaptureMediaCandidateId sampledOrRefinedCandidate,
            int attemptRank
    ) {
        String proposal = requireProposal(sampledOrRefinedCandidate);
        requireStage(sampledOrRefinedCandidate.patternEvidenceId(), "patternEvidenceId");
        String geometry = requireStage(sampledOrRefinedCandidate.geometryCandidateId(), "geometryCandidateId");
        String sampling = requireStage(sampledOrRefinedCandidate.samplingCandidateId(), "samplingCandidateId");
        String parent = sampledOrRefinedCandidate.refinementCandidateId().orElse(sampling);
        return new CaptureMediaCandidateId(
                sampledOrRefinedCandidate.sourceImageId(),
                Optional.of(proposal),
                sampledOrRefinedCandidate.patternEvidenceId(),
                Optional.of(geometry),
                Optional.of(sampling),
                sampledOrRefinedCandidate.refinementCandidateId(),
                Optional.of(decodeAttemptId(parent, attemptRank))
        );
    }

    /**
     * Returns the most specific ID currently present in this identity chain.
     *
     * @return decode, refinement, sampling, geometry, pattern, proposal, or source ID
     */
    public String value() {
        return decodeAttemptId
                .or(() -> refinementCandidateId)
                .or(() -> samplingCandidateId)
                .or(() -> geometryCandidateId)
                .or(() -> patternEvidenceId)
                .or(() -> proposalCandidateId)
                .orElse(sourceImageId);
    }

    private static String shortPixelHashPrefix(String pixelSha256) {
        String hash = EvidenceValidation.requireText(pixelSha256, "pixelSha256").toLowerCase(Locale.ROOT);
        if (hash.length() < PIXEL_HASH_PREFIX_LENGTH) {
            throw new IllegalArgumentException("pixelSha256 must contain at least 12 hex characters");
        }
        for (int index = 0; index < PIXEL_HASH_PREFIX_LENGTH; index++) {
            char character = hash.charAt(index);
            if ((character < '0' || character > '9') && (character < 'a' || character > 'f')) {
                throw new IllegalArgumentException("pixelSha256 prefix must be lowercase or uppercase hex");
            }
        }
        return hash.substring(0, PIXEL_HASH_PREFIX_LENGTH);
    }

    private static void requireProposalRanks(
            int sourceRegionRank,
            int profileAlternativeRank,
            int profileAlternativeCount
    ) {
        EvidenceValidation.requirePositive(sourceRegionRank, "sourceRegionRank");
        EvidenceValidation.requirePositive(profileAlternativeRank, "profileAlternativeRank");
        EvidenceValidation.requirePositive(profileAlternativeCount, "profileAlternativeCount");
        if (profileAlternativeRank > profileAlternativeCount) {
            throw new IllegalArgumentException("profileAlternativeRank must not exceed profileAlternativeCount");
        }
    }

    private static void requireBoundedRank(int rank, int maximum, String fieldName) {
        EvidenceValidation.requirePositive(rank, fieldName);
        if (rank > maximum) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maximum);
        }
    }

    private static String requireProposal(CaptureMediaCandidateId candidateId) {
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        return requireStage(candidateId.proposalCandidateId(), "proposalCandidateId");
    }

    private static String requireStage(Optional<String> stageId, String fieldName) {
        Objects.requireNonNull(stageId, fieldName + " must not be null");
        return stageId.orElseThrow(() -> new IllegalArgumentException(fieldName + " must be present"));
    }

    private static void requireHierarchy(
            String sourceImageId,
            Optional<String> proposalCandidateId,
            Optional<String> patternEvidenceId,
            Optional<String> geometryCandidateId,
            Optional<String> samplingCandidateId,
            Optional<String> refinementCandidateId,
            Optional<String> decodeAttemptId
    ) {
        if (proposalCandidateId.isEmpty()
                && (patternEvidenceId.isPresent()
                || geometryCandidateId.isPresent()
                || samplingCandidateId.isPresent()
                || refinementCandidateId.isPresent()
                || decodeAttemptId.isPresent())) {
            throw new IllegalArgumentException("proposalCandidateId must be present for stage-scoped IDs");
        }
        proposalCandidateId.ifPresent(proposal -> requireStartsWith(proposal, sourceImageId + "/", "proposalCandidateId"));
        patternEvidenceId.ifPresent(pattern ->
                requireStartsWith(pattern, proposalCandidateId.orElseThrow() + "/pattern-v1", "patternEvidenceId"));
        geometryCandidateId.ifPresent(geometry -> {
            if (patternEvidenceId.isEmpty()) {
                throw new IllegalArgumentException("patternEvidenceId must be present for geometryCandidateId");
            }
            requireStartsWith(geometry, proposalCandidateId.orElseThrow() + "/geom", "geometryCandidateId");
        });
        samplingCandidateId.ifPresent(sampling -> {
            if (geometryCandidateId.isEmpty()) {
                throw new IllegalArgumentException("geometryCandidateId must be present for samplingCandidateId");
            }
            requireStartsWith(sampling, geometryCandidateId.orElseThrow() + "/sample-scale", "samplingCandidateId");
        });
        refinementCandidateId.ifPresent(refinement -> {
            if (samplingCandidateId.isEmpty()) {
                throw new IllegalArgumentException("samplingCandidateId must be present for refinementCandidateId");
            }
            requireStartsWith(refinement, samplingCandidateId.orElseThrow() + "/refine-local-grid-v1",
                    "refinementCandidateId");
        });
        decodeAttemptId.ifPresent(decode -> {
            if (samplingCandidateId.isEmpty()) {
                throw new IllegalArgumentException("samplingCandidateId must be present for decodeAttemptId");
            }
            String parent = refinementCandidateId.orElse(samplingCandidateId.orElseThrow());
            requireStartsWith(decode, parent + "/decode", "decodeAttemptId");
        });
    }

    private static void requireStartsWith(String value, String prefix, String fieldName) {
        if (!value.startsWith(prefix)) {
            throw new IllegalArgumentException(fieldName + " must extend its parent ID");
        }
    }
}

