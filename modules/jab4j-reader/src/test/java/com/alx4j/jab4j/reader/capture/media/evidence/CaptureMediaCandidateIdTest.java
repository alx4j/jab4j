package com.alx4j.jab4j.reader.capture.media.evidence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Capture media candidate IDs")
class CaptureMediaCandidateIdTest {

    private static final String PIXEL_SHA256 =
            "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789";

    @Test
    @DisplayName("Factories produce deterministic source, proposal, and stage IDs")
    void factoriesProduceDeterministicSourceProposalAndStageIds() {
        String sourceId = CaptureMediaCandidateId.sourceImageId(
                "STILL_IMAGE_FILE",
                0,
                "photo-001.jpg",
                PIXEL_SHA256
        );
        String proposalId = CaptureMediaCandidateId.proposalCandidateId(sourceId, 2, 1, 3);
        CaptureMediaCandidateId proposal = CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                "photo-001.jpg",
                PIXEL_SHA256,
                2,
                1,
                3
        );
        CaptureMediaCandidateId pattern = CaptureMediaCandidateId.patternEvidence(proposal);
        CaptureMediaCandidateId geometry = CaptureMediaCandidateId.geometryCandidate(pattern, 1);
        CaptureMediaCandidateId sampling = CaptureMediaCandidateId.samplingCandidate(geometry, 50, 2);
        CaptureMediaCandidateId refinement = CaptureMediaCandidateId.refinementCandidate(sampling);
        CaptureMediaCandidateId decode = CaptureMediaCandidateId.decodeAttempt(refinement, 1);

        assertAll(
                () -> assertEquals("STILL_IMAGE_FILE:0:photo-001.jpg:abcdef012345", sourceId),
                () -> assertEquals(sourceId + "/sr2/pa1-of-3", proposalId),
                () -> assertEquals(proposalId, proposal.proposalCandidateId().orElseThrow()),
                () -> assertEquals(proposalId + "/pattern-v1", pattern.patternEvidenceId().orElseThrow()),
                () -> assertEquals(proposalId + "/geom1", geometry.geometryCandidateId().orElseThrow()),
                () -> assertEquals(
                        proposalId + "/geom1/sample-scale50-variant2",
                        sampling.samplingCandidateId().orElseThrow()
                ),
                () -> assertEquals(
                        proposalId + "/geom1/sample-scale50-variant2/refine-local-grid-v1",
                        refinement.refinementCandidateId().orElseThrow()
                ),
                () -> assertEquals(
                        proposalId + "/geom1/sample-scale50-variant2/refine-local-grid-v1/decode1",
                        decode.decodeAttemptId().orElseThrow()
                ),
                () -> assertEquals(decode.decodeAttemptId().orElseThrow(), decode.value()),
                () -> assertEquals(
                        CaptureMediaCandidateId.proposalCandidate(
                                "STILL_IMAGE_FILE",
                                0,
                                "photo-001.jpg",
                                PIXEL_SHA256,
                                2,
                                1,
                                3
                        ),
                        proposal
                )
        );
    }

    @Test
    @DisplayName("Factories reject invalid source, proposal, and stage inputs")
    void factoriesRejectInvalidSourceProposalAndStageInputs() {
        CaptureMediaCandidateId pattern = patternCandidateId();
        CaptureMediaCandidateId geometry = CaptureMediaCandidateId.geometryCandidate(pattern, 1);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.sourceImageId(" ", 0, "photo.jpg", PIXEL_SHA256)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.sourceImageId("STILL_IMAGE_FILE", -1, "photo.jpg", PIXEL_SHA256)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.sourceImageId("STILL_IMAGE_FILE", 0, "photo.jpg", "abcd")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.sourceImageId("STILL_IMAGE_FILE", 0, "photo.jpg", "xyzdef012345")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.proposalCandidateId("source", 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.proposalCandidateId("source", 1, 2, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.geometryCandidate(pattern, 4)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.samplingCandidate(geometry, 101, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.samplingCandidate(geometry, 50, 4)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.decodeAttempt(geometry, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.decodeAttempt(
                                CaptureMediaCandidateId.samplingCandidate(geometry, 50, 1),
                                0
                        ))
        );
    }

    @Test
    @DisplayName("Record constructor rejects invalid hierarchy combinations")
    void recordConstructorRejectsInvalidHierarchyCombinations() {
        String source = CaptureMediaCandidateId.sourceImageId("STILL_IMAGE_FILE", 0, "photo.jpg", PIXEL_SHA256);
        String proposal = CaptureMediaCandidateId.proposalCandidateId(source, 1, 1, 1);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaCandidateId(
                                source,
                                Optional.empty(),
                                Optional.of(proposal + "/pattern-v1"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaCandidateId(
                                source,
                                Optional.of(proposal),
                                Optional.empty(),
                                Optional.of(proposal + "/geom1"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaCandidateId(
                                source,
                                Optional.of(proposal),
                                Optional.of(proposal + "/pattern-v1"),
                                Optional.of(proposal + "/geom1"),
                                Optional.of(proposal + "/geom2/sample-scale50-variant1"),
                                Optional.empty(),
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaCandidateId.proposalCandidate(patternCandidateId(), 1, 1, 1)
                ),
                () -> assertTrue(CaptureMediaCandidateId.sourceImage(
                        "STILL_IMAGE_FILE",
                        0,
                        "photo.jpg",
                        PIXEL_SHA256
                ).proposalCandidateId().isEmpty())
        );
    }

    private CaptureMediaCandidateId patternCandidateId() {
        CaptureMediaCandidateId proposal = CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                "photo-001.jpg",
                PIXEL_SHA256,
                1,
                1,
                1
        );
        return CaptureMediaCandidateId.patternEvidence(proposal);
    }
}

