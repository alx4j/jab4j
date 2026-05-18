package com.alx4j.jab4j.reader.capture.media.sample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Capture media module phase search")
class CaptureMediaModulePhaseSearchTest {

    private static final double EPSILON = 0.000_001d;

    private final CaptureMediaModulePhaseSearch search = new CaptureMediaModulePhaseSearch();

    @Test
    @DisplayName("Nominal geometry wins for exact frames")
    void nominalGeometryWinsForExactFrames() {
        CaptureMediaModulePhaseSearchRequest request = request(
                Optional.empty(),
                bounds(1.0d, 1.0d, 1.0d, 0.05d, 0.05d, 32, 0.75d)
        );

        CaptureMediaModulePhaseSearchResult result = search.search(
                request,
                candidate -> CaptureMediaModulePhaseAttempt.accepted(candidate, 4, true, 1.0d, 1.0d, 0)
        );

        CaptureMediaModulePhaseCandidate selected = result.selectedCandidate();
        CaptureMediaModulePhaseGeometry geometry = selected.geometry();
        assertAll(
                () -> assertEquals(CaptureMediaModulePhaseCandidateSource.NOMINAL, selected.source()),
                () -> assertFalse(selected.evidenceDerived()),
                () -> assertFalse(selected.shifted()),
                () -> assertEquals(10.0d, geometry.centerXPx(), EPSILON),
                () -> assertEquals(20.0d, geometry.centerYPx(), EPSILON),
                () -> assertEquals(4.0d, geometry.moduleSizePx(), EPSILON),
                () -> assertEquals(1.0d, geometry.moduleSizeScale(), EPSILON)
        );
    }

    @Test
    @DisplayName("High-confidence CV evidence is included but not blindly trusted")
    void highConfidenceCvEvidenceIsIncludedButNotBlindlyTrusted() {
        CaptureMediaModulePhaseEvidence evidence =
                CaptureMediaModulePhaseEvidence.cv("boofcv", 0.75d, -0.5d, 0.95d);
        CaptureMediaModulePhaseSearchRequest request = request(
                Optional.of(evidence),
                bounds(0.0d, 0.0d, 1.0d, 0.0d, 0.05d, 8, 0.75d)
        );

        CaptureMediaModulePhaseSearchResult result = search.search(request, candidate -> {
            if (candidate.source() == CaptureMediaModulePhaseCandidateSource.NOMINAL) {
                return CaptureMediaModulePhaseAttempt.accepted(candidate, 1, false, 0.2d, 0.2d, 4);
            }
            return CaptureMediaModulePhaseAttempt.rejected(
                    candidate,
                    CaptureMediaModulePhaseOutcome.SLOT_VALIDATION_FAILURE,
                    Optional.of("slot mismatch"),
                    4,
                    true,
                    1.0d,
                    1.0d,
                    0
            );
        });

        assertAll(
                () -> assertTrue(result.rankedCandidates().stream()
                        .anyMatch(candidate -> candidate.source() == CaptureMediaModulePhaseCandidateSource.CV_EVIDENCE
                                && candidate.evidenceDerived())),
                () -> assertEquals(CaptureMediaModulePhaseCandidateSource.NOMINAL,
                        result.selectedCandidate().source()),
                () -> assertTrue(result.selectedAttempt().acceptedPayload())
        );
    }

    @Test
    @DisplayName("Bounded center shifts can recover a shifted module center")
    void boundedCenterShiftsCanRecoverShiftedModuleCenter() {
        CaptureMediaModulePhaseSearchRequest request = request(
                Optional.empty(),
                bounds(1.0d, 1.0d, 1.0d, 0.0d, 0.05d, 16, 0.75d)
        );

        CaptureMediaModulePhaseSearchResult result = search.search(request, candidate -> {
            CaptureMediaModulePhaseGeometry geometry = candidate.geometry();
            if (close(geometry.centerOffsetXPx(), 1.0d) && close(geometry.centerOffsetYPx(), -1.0d)) {
                return CaptureMediaModulePhaseAttempt.accepted(candidate, 4, true, 0.9d, 0.8d, 1);
            }
            return CaptureMediaModulePhaseAttempt.rejected(
                    candidate,
                    CaptureMediaModulePhaseOutcome.FINDER_OR_PALETTE_FAILURE,
                    Optional.empty(),
                    0,
                    false,
                    0.0d,
                    0.0d,
                    8
            );
        });

        CaptureMediaModulePhaseCandidate selected = result.selectedCandidate();
        assertAll(
                () -> assertEquals(CaptureMediaModulePhaseCandidateSource.BOUNDED_SEARCH, selected.source()),
                () -> assertTrue(selected.shifted()),
                () -> assertEquals(1.0d, selected.geometry().centerOffsetXPx(), EPSILON),
                () -> assertEquals(-1.0d, selected.geometry().centerOffsetYPx(), EPSILON),
                () -> assertEquals(11.0d, selected.geometry().centerXPx(), EPSILON),
                () -> assertEquals(19.0d, selected.geometry().centerYPx(), EPSILON)
        );
    }

    @Test
    @DisplayName("Bounded module-size scale variants are considered")
    void boundedModuleSizeScaleVariantsAreConsidered() {
        CaptureMediaModulePhaseSearchRequest request = request(
                Optional.empty(),
                bounds(0.0d, 0.0d, 1.0d, 0.10d, 0.05d, 8, 0.75d)
        );

        CaptureMediaModulePhaseSearchResult result = search.search(request, candidate -> {
            if (close(candidate.geometry().moduleSizeScale(), 1.05d)) {
                return CaptureMediaModulePhaseAttempt.accepted(candidate, 4, true, 0.8d, 0.7d, 0);
            }
            return CaptureMediaModulePhaseAttempt.rejected(
                    candidate,
                    CaptureMediaModulePhaseOutcome.TILE_DECODE_FAILURE,
                    Optional.of("payload crc"),
                    3,
                    false,
                    0.7d,
                    0.7d,
                    2
            );
        });

        assertAll(
                () -> assertTrue(result.rankedCandidates().stream()
                        .anyMatch(candidate -> close(candidate.geometry().moduleSizeScale(), 1.05d))),
                () -> assertEquals(1.05d, result.selectedCandidate().geometry().moduleSizeScale(), EPSILON),
                () -> assertEquals(4.2d, result.selectedCandidate().geometry().moduleSizePx(), EPSILON)
        );
    }

    @Test
    @DisplayName("Corrupted high-score attempts rank below accepted attempts")
    void corruptedHighScoreAttemptsRankBelowAcceptedAttempts() {
        CaptureMediaModulePhaseSearchRequest request = request(
                Optional.empty(),
                bounds(1.0d, 0.0d, 1.0d, 0.0d, 0.05d, 8, 0.75d)
        );

        CaptureMediaModulePhaseSearchResult result = search.search(request, candidate -> {
            if (candidate.source() == CaptureMediaModulePhaseCandidateSource.NOMINAL) {
                return CaptureMediaModulePhaseAttempt.accepted(candidate, 0, false, 0.0d, 0.0d, 12);
            }
            if (close(candidate.geometry().centerOffsetXPx(), 1.0d)) {
                return CaptureMediaModulePhaseAttempt.rejected(
                        candidate,
                        CaptureMediaModulePhaseOutcome.SLOT_VALIDATION_FAILURE,
                        Optional.of("wrong tile index"),
                        6,
                        true,
                        1.0d,
                        1.0d,
                        0
                );
            }
            return CaptureMediaModulePhaseAttempt.rejected(
                    candidate,
                    CaptureMediaModulePhaseOutcome.FINDER_OR_PALETTE_FAILURE,
                    Optional.empty(),
                    1,
                    false,
                    0.2d,
                    0.2d,
                    4
            );
        });

        assertAll(
                () -> assertTrue(result.selectedAttempt().acceptedPayload()),
                () -> assertEquals(CaptureMediaModulePhaseCandidateSource.NOMINAL,
                        result.selectedCandidate().source()),
                () -> assertTrue(result.rankedAttempts().stream()
                        .filter(attempt -> !attempt.acceptedPayload())
                        .allMatch(attempt -> attempt.candidate().ordinal()
                                > result.selectedCandidate().ordinal()))
        );
    }

    @Test
    @DisplayName("Variant count cap is enforced")
    void variantCountCapIsEnforced() {
        CaptureMediaModulePhaseSearchRequest request = request(
                Optional.empty(),
                bounds(2.0d, 2.0d, 1.0d, 0.10d, 0.05d, 3, 0.75d)
        );

        CaptureMediaModulePhaseSearchResult result = search.search(
                request,
                candidate -> CaptureMediaModulePhaseAttempt.rejected(
                        candidate,
                        CaptureMediaModulePhaseOutcome.FINDER_OR_PALETTE_FAILURE,
                        Optional.empty(),
                        0,
                        false,
                        0.0d,
                        0.0d,
                        1
                )
        );

        assertAll(
                () -> assertEquals(3, result.variantCap()),
                () -> assertEquals(3, result.attemptedVariantCount()),
                () -> assertEquals(3, search.candidates(request).size()),
                () -> assertTrue(result.capReached())
        );
    }

    private CaptureMediaModulePhaseSearchRequest request(
            Optional<CaptureMediaModulePhaseEvidence> evidence,
            CaptureMediaModulePhaseBounds bounds
    ) {
        return new CaptureMediaModulePhaseSearchRequest(
                0,
                21,
                10.0d,
                20.0d,
                4.0d,
                evidence,
                bounds
        );
    }

    private CaptureMediaModulePhaseBounds bounds(
            double maxCenterShiftXPx,
            double maxCenterShiftYPx,
            double centerShiftStepPx,
            double maxModuleScaleDelta,
            double moduleScaleStep,
            int maxVariantCount,
            double minimumEvidenceConfidence
    ) {
        return new CaptureMediaModulePhaseBounds(
                maxCenterShiftXPx,
                maxCenterShiftYPx,
                centerShiftStepPx,
                maxModuleScaleDelta,
                moduleScaleStep,
                maxVariantCount,
                minimumEvidenceConfidence
        );
    }

    private boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }
}
