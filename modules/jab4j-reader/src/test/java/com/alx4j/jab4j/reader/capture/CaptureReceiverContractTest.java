package com.alx4j.jab4j.reader.capture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;
import com.alx4j.jab4j.reader.restore.ReaderRestoreStatus;

@DisplayName("Capture receiver public contracts")
class CaptureReceiverContractTest {

    private static final SessionId SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final Path OUTPUT_DIRECTORY = Path.of("restore-output").toAbsolutePath().normalize();

    @Test
    @DisplayName("Request factories normalize sources, copy inputs, and expose restore mode")
    void requestFactoriesNormalizeSourcesCopyInputsAndExposeRestoreMode() {
        List<Path> sources = new ArrayList<>(List.of(Path.of("frames"), Path.of("frame-001.png")));

        CaptureReceiverRequest evaluateOnly = CaptureReceiverRequest.evaluateOnly(sources);
        sources.clear();
        CaptureReceiverRequest restore = CaptureReceiverRequest.restore(List.of(Path.of("frames")), Path.of("output"));

        assertAll(
                () -> assertFalse(evaluateOnly.restoreRequested()),
                () -> assertEquals(2, evaluateOnly.inputSources().size()),
                () -> assertTrue(evaluateOnly.inputSources().get(0).isAbsolute()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> evaluateOnly.inputSources().add(Path.of("later.png"))
                ),
                () -> assertTrue(restore.restoreRequested()),
                () -> assertTrue(restore.outputDirectory().orElseThrow().isAbsolute())
        );
    }

    @Test
    @DisplayName("Request rejects invalid null and empty inputs")
    void requestRejectsInvalidNullAndEmptyInputs() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> CaptureReceiverRequest.evaluateOnly(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> CaptureReceiverRequest.evaluateOnly(List.of())),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new CaptureReceiverRequest(Arrays.asList(Path.of("frames"), null), Optional.empty())
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new CaptureReceiverRequest(List.of(Path.of("frames")), null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> CaptureReceiverRequest.restore(List.of(Path.of("frames")), null)
                )
        );
    }

    @Test
    @DisplayName("Diagnostic codes include required stable receiver reasons")
    void diagnosticCodesIncludeRequiredStableReceiverReasons() {
        EnumSet<CaptureDiagnosticCode> codes = EnumSet.allOf(CaptureDiagnosticCode.class);

        assertAll(
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.UNSUPPORTED_FORMAT)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.UNREADABLE_IMAGE)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.UNSUPPORTED_DIMENSIONS)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.NO_CANDIDATE_BARCODE_CONTENT)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.DUPLICATE_EQUIVALENT_FRAME)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.DUPLICATE_CONFLICTING_FRAME)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.CORRUPTED_OR_UNREADABLE_TILE_CONTENT)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.INCONSISTENT_SESSION_CONTENT)),
                () -> assertTrue(codes.contains(CaptureDiagnosticCode.RESTORE_FAILURE)),
                () -> assertTrue(CaptureDiagnosticCode.DUPLICATE_EQUIVALENT_FRAME.duplicate()),
                () -> assertTrue(CaptureDiagnosticCode.DUPLICATE_CONFLICTING_FRAME.duplicate()),
                () -> assertFalse(CaptureDiagnosticCode.RESTORE_FAILURE.duplicate())
        );
    }

    @Test
    @DisplayName("Diagnostics validate optional frame scope and messages")
    void diagnosticsValidateOptionalFrameScopeAndMessages() {
        CaptureFrameDiagnostic ordered = CaptureFrameDiagnostic.forSource(
                CaptureDiagnosticCode.UNREADABLE_IMAGE,
                "frame-001.png",
                1,
                "Frame image could not be read"
        );
        CaptureFrameDiagnostic captureSet = CaptureFrameDiagnostic.forCaptureSet(
                CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                "Capture set is missing required content"
        );

        assertAll(
                () -> assertEquals(Optional.of("frame-001.png"), ordered.sourceId()),
                () -> assertEquals(Optional.of(1), ordered.callerOrder()),
                () -> assertTrue(ordered.frameScoped()),
                () -> assertFalse(captureSet.frameScoped()),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new CaptureFrameDiagnostic(null, Optional.empty(), Optional.empty(), "message")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureFrameDiagnostic.forSource(CaptureDiagnosticCode.UNREADABLE_IMAGE, " ", "message")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureFrameDiagnostic.forSource(
                                CaptureDiagnosticCode.UNREADABLE_IMAGE,
                                "frame.png",
                                -1,
                                "message"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureFrameDiagnostic.forCaptureSet(CaptureDiagnosticCode.UNREADABLE_IMAGE, " ")
                )
        );
    }

    @Test
    @DisplayName("Summary validates count invariants and candidate helper behavior")
    void summaryValidatesCountInvariantsAndCandidateHelperBehavior() {
        CaptureReceiverSummary summary = new CaptureReceiverSummary(4, 3, 2, 1, 1, 0, 8, 0);

        assertAll(
                () -> assertTrue(summary.hasAcceptedCandidates()),
                () -> assertFalse(CaptureReceiverSummary.empty().hasAcceptedCandidates()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureReceiverSummary(-1, 0, 0, 0, 0, 0, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureReceiverSummary(1, 2, 0, 0, 0, 0, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureReceiverSummary(1, 1, 2, 0, 0, 0, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureReceiverSummary(1, 1, 0, 0, 2, 0, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("Result copies diagnostics and delegates status helper behavior")
    void resultCopiesDiagnosticsAndDelegatesStatusHelperBehavior() {
        List<CaptureFrameDiagnostic> diagnostics = new ArrayList<>(List.of(duplicateDiagnostic()));

        CaptureReceiverResult result = CaptureReceiverResult.eligible(
                summary(),
                diagnostics,
                "Capture input is eligible for restore"
        );
        diagnostics.clear();

        assertAll(
                () -> assertEquals(CaptureReceiverStatus.ELIGIBLE, result.status()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.diagnostics().add(duplicateDiagnostic())
                ),
                () -> assertTrue(result.eligibleForRestore()),
                () -> assertFalse(result.restoreAttempted()),
                () -> assertFalse(result.failed()),
                () -> assertFalse(result.restored()),
                () -> assertTrue(CaptureReceiverStatus.ELIGIBLE.eligibleForRestore()),
                () -> assertFalse(CaptureReceiverStatus.ELIGIBLE.restoreAttempted()),
                () -> assertFalse(CaptureReceiverStatus.ELIGIBLE.terminalFailure())
        );
    }

    @Test
    @DisplayName("Result rejects invalid status and restore combinations")
    void resultRejectsInvalidStatusAndRestoreCombinations() {
        ReaderRestoreResult restored = restoredResult();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureReceiverResult.incomplete(summary(), List.of(), "Missing content")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureReceiverResult(
                                CaptureReceiverStatus.ELIGIBLE,
                                summary(),
                                List.of(),
                                Optional.of(restored),
                                "Invalid restore result"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureReceiverResult(
                                CaptureReceiverStatus.RESTORED,
                                summary(),
                                List.of(),
                                Optional.empty(),
                                "Missing restore result"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureReceiverResult.restoreFailed(
                                summary(),
                                List.of(missingContentDiagnostic()),
                                Optional.empty(),
                                "Restore failed"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureReceiverResult.restoreFailed(
                                summary(),
                                List.of(restoreFailureDiagnostic()),
                                Optional.of(restored),
                                "Restore failed"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureReceiverResult(
                                CaptureReceiverStatus.ELIGIBLE,
                                summary(),
                                Arrays.asList((CaptureFrameDiagnostic) null),
                                Optional.empty(),
                                "Invalid diagnostics"
                        )
                )
        );
    }

    @Test
    @DisplayName("Restored and failed restore results expose restore outcome helpers")
    void restoredAndFailedRestoreResultsExposeRestoreOutcomeHelpers() {
        CaptureReceiverResult restored = CaptureReceiverResult.restored(
                restoredSummary(),
                List.of(),
                restoredResult()
        );
        CaptureReceiverResult restoreFailed = CaptureReceiverResult.restoreFailed(
                summary(),
                List.of(restoreFailureDiagnostic()),
                Optional.of(failedRestoreResult()),
                "Restore did not complete"
        );

        assertAll(
                () -> assertTrue(restored.restored()),
                () -> assertTrue(restored.restoreAttempted()),
                () -> assertFalse(restored.failed()),
                () -> assertTrue(restored.restoreResult().orElseThrow().restored()),
                () -> assertFalse(restoreFailed.restored()),
                () -> assertTrue(restoreFailed.restoreAttempted()),
                () -> assertTrue(restoreFailed.failed()),
                () -> assertTrue(CaptureReceiverStatus.RESTORE_FAILED.terminalFailure()),
                () -> assertTrue(CaptureReceiverStatus.RESTORED.restorationSucceeded())
        );
    }

    private CaptureReceiverSummary summary() {
        return new CaptureReceiverSummary(4, 3, 2, 1, 1, 0, 8, 0);
    }

    private CaptureReceiverSummary restoredSummary() {
        return new CaptureReceiverSummary(4, 3, 2, 1, 1, 0, 8, 2);
    }

    private CaptureFrameDiagnostic duplicateDiagnostic() {
        return CaptureFrameDiagnostic.forSource(
                CaptureDiagnosticCode.DUPLICATE_EQUIVALENT_FRAME,
                "frame-002.png",
                "Duplicate frame content"
        );
    }

    private CaptureFrameDiagnostic missingContentDiagnostic() {
        return CaptureFrameDiagnostic.forCaptureSet(
                CaptureDiagnosticCode.MISSING_REQUIRED_CONTENT,
                "Required content is missing"
        );
    }

    private CaptureFrameDiagnostic restoreFailureDiagnostic() {
        return CaptureFrameDiagnostic.forCaptureSet(
                CaptureDiagnosticCode.RESTORE_FAILURE,
                "Restore failed"
        );
    }

    private ReaderRestoreResult restoredResult() {
        return ReaderRestoreResult.restored(SESSION_ID, OUTPUT_DIRECTORY, 2, 1, 128);
    }

    private ReaderRestoreResult failedRestoreResult() {
        return ReaderRestoreResult.failed(
                ReaderRestoreStatus.IO_FAILURE,
                SESSION_ID,
                OUTPUT_DIRECTORY,
                "Restore output could not be written"
        );
    }
}
