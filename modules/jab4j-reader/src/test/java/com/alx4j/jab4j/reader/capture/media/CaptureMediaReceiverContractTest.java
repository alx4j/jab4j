package com.alx4j.jab4j.reader.capture.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.reader.restore.ReaderRestoreResult;
import com.alx4j.jab4j.reader.restore.ReaderRestoreStatus;

@DisplayName("Capture media receiver public contracts")
class CaptureMediaReceiverContractTest {

    private static final SessionId SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final Path OUTPUT_DIRECTORY = Path.of("restore-output").toAbsolutePath().normalize();

    @Test
    @DisplayName("Request factories normalize sources, copy inputs, and expose source kind and restore mode")
    void requestFactoriesNormalizeSourcesCopyInputsAndExposeSourceKindAndRestoreMode() {
        List<Path> sources = new ArrayList<>(List.of(Path.of("photo-001.jpg"), Path.of("photo-002.png")));

        CaptureMediaReceiverRequest evaluateOnly = CaptureMediaReceiverRequest.evaluateStillImages(sources);
        sources.clear();
        CaptureMediaReceiverRequest restore = CaptureMediaReceiverRequest.restoreExtractedFrameFolders(
                List.of(Path.of("frames")),
                Path.of("output")
        );
        CaptureMediaReceiverRequest videoEvaluateOnly = CaptureMediaReceiverRequest.evaluateVideoFiles(
                List.of(Path.of("capture.mov"))
        );

        assertAll(
                () -> assertEquals(CaptureMediaSourceKind.STILL_IMAGE_FILE, evaluateOnly.sourceKind()),
                () -> assertFalse(evaluateOnly.restoreRequested()),
                () -> assertEquals(2, evaluateOnly.inputSources().size()),
                () -> assertTrue(evaluateOnly.inputSources().get(0).isAbsolute()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> evaluateOnly.inputSources().add(Path.of("later.jpg"))
                ),
                () -> assertEquals(CaptureMediaSourceKind.EXTRACTED_FRAME_FOLDER, restore.sourceKind()),
                () -> assertTrue(restore.restoreRequested()),
                () -> assertTrue(restore.outputDirectory().orElseThrow().isAbsolute()),
                () -> assertEquals(CaptureMediaSourceKind.VIDEO_FILE, videoEvaluateOnly.sourceKind()),
                () -> assertTrue(CaptureMediaSourceKind.VIDEO_FILE.video()),
                () -> assertTrue(CaptureMediaSourceKind.EXTRACTED_FRAME_FOLDER.extractedFrameCollection()),
                () -> assertFalse(CaptureMediaSourceKind.STILL_IMAGE_FILE.video())
        );
    }

    @Test
    @DisplayName("Request rejects invalid null and empty inputs")
    void requestRejectsInvalidNullAndEmptyInputs() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> CaptureMediaReceiverRequest.evaluateOnly(null, List.of(Path.of("photo.jpg")))
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> CaptureMediaReceiverRequest.evaluateStillImages(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaReceiverRequest.evaluateStillImages(List.of())
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new CaptureMediaReceiverRequest(
                                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                                Arrays.asList(Path.of("photo.jpg"), null),
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new CaptureMediaReceiverRequest(
                                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                                List.of(Path.of("photo.jpg")),
                                null
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> CaptureMediaReceiverRequest.restoreStillImages(List.of(Path.of("photo.jpg")), null)
                )
        );
    }

    @Test
    @DisplayName("Diagnostic codes include required stable media reasons")
    void diagnosticCodesIncludeRequiredStableMediaReasons() {
        EnumSet<CaptureMediaDiagnosticCode> codes = EnumSet.allOf(CaptureMediaDiagnosticCode.class);

        assertAll(
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.MONITOR_TOO_SMALL)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.PERSPECTIVE_TOO_SEVERE)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.FRAME_PARTIALLY_OUTSIDE_IMAGE)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.BLUR)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.GLARE_OR_OVEREXPOSURE)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.DUPLICATE_MEDIA_FRAME)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.UNSUPPORTED_CODEC)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS)),
                () -> assertTrue(codes.contains(CaptureMediaDiagnosticCode.RESTORE_FAILURE)),
                () -> assertTrue(CaptureMediaDiagnosticCode.DUPLICATE_MEDIA_FRAME.duplicate()),
                () -> assertFalse(CaptureMediaDiagnosticCode.RESTORE_FAILURE.duplicate()),
                () -> assertTrue(CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER.unsupportedMedia()),
                () -> assertTrue(CaptureMediaDiagnosticCode.GLARE_OR_OVEREXPOSURE.qualityIssue())
        );
    }

    @Test
    @DisplayName("Diagnostics validate severity, blocking, source context, metrics, and messages")
    void diagnosticsValidateSeverityBlockingSourceContextMetricsAndMessages() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("blurScore", 0.42d);
        CaptureMediaDiagnostic measured = new CaptureMediaDiagnostic(
                CaptureMediaDiagnosticCode.BLUR,
                CaptureMediaDiagnosticSeverity.WARNING,
                false,
                Optional.of(CaptureMediaSourceKind.STILL_IMAGE_FILE),
                Optional.of("photo-001.jpg"),
                Optional.of(1),
                Optional.of(250L),
                Optional.of(7L),
                metrics,
                "Motion blur is near the supported threshold"
        );
        metrics.clear();
        CaptureMediaDiagnostic blocking = CaptureMediaDiagnostic.forMediaSet(
                CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                CaptureMediaDiagnosticSeverity.ERROR,
                "Required unique frames are missing"
        );

        assertAll(
                () -> assertTrue(measured.sourceScoped()),
                () -> assertFalse(measured.blocking()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.WARNING, measured.severity()),
                () -> assertEquals(Optional.of(1), measured.callerOrder()),
                () -> assertEquals(1, measured.metrics().size()),
                () -> assertThrows(UnsupportedOperationException.class, () -> measured.metrics().put("later", 1.0d)),
                () -> assertTrue(blocking.blocking()),
                () -> assertTrue(CaptureMediaDiagnosticSeverity.ERROR.blocksRestore()),
                () -> assertFalse(CaptureMediaDiagnosticSeverity.WARNING.blocksRestore()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaDiagnostic(
                                CaptureMediaDiagnosticCode.BLUR,
                                CaptureMediaDiagnosticSeverity.ERROR,
                                false,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of(),
                                "Invalid blocking flag"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaDiagnostic(
                                CaptureMediaDiagnosticCode.BLUR,
                                CaptureMediaDiagnosticSeverity.WARNING,
                                false,
                                Optional.empty(),
                                Optional.of("photo-001.jpg"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of(),
                                "Missing source kind"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaDiagnostic.forSource(
                                CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                                CaptureMediaDiagnosticSeverity.ERROR,
                                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                                " ",
                                "Unreadable media"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaDiagnostic.forSource(
                                CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                                CaptureMediaDiagnosticSeverity.ERROR,
                                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                                "photo.jpg",
                                -1,
                                "Unreadable media"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaDiagnostic(
                                CaptureMediaDiagnosticCode.BLUR,
                                CaptureMediaDiagnosticSeverity.WARNING,
                                false,
                                Optional.of(CaptureMediaSourceKind.STILL_IMAGE_FILE),
                                Optional.of("photo.jpg"),
                                Optional.empty(),
                                Optional.of(-1L),
                                Optional.empty(),
                                Map.of(),
                                "Invalid timestamp"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaDiagnostic(
                                CaptureMediaDiagnosticCode.BLUR,
                                CaptureMediaDiagnosticSeverity.WARNING,
                                false,
                                Optional.of(CaptureMediaSourceKind.STILL_IMAGE_FILE),
                                Optional.of("photo.jpg"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(-1L),
                                Map.of(),
                                "Invalid frame"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaDiagnostic(
                                CaptureMediaDiagnosticCode.BLUR,
                                CaptureMediaDiagnosticSeverity.WARNING,
                                false,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of(" ", 1.0d),
                                "Invalid metric"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaDiagnostic(
                                CaptureMediaDiagnosticCode.BLUR,
                                CaptureMediaDiagnosticSeverity.WARNING,
                                false,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of("blurScore", Double.NaN),
                                "Invalid metric"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaDiagnostic.forMediaSet(
                                CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                                CaptureMediaDiagnosticSeverity.ERROR,
                                " "
                        )
                )
        );
    }

    @Test
    @DisplayName("Summary validates count invariants and candidate helper behavior")
    void summaryValidatesCountInvariantsAndCandidateHelperBehavior() {
        CaptureMediaSummary summary = new CaptureMediaSummary(5, 4, 3, 1, 0, 1, 2, 12, 0);

        assertAll(
                () -> assertTrue(summary.hasAcceptedCandidates()),
                () -> assertTrue(summary.hasRecoveredUniqueFrames()),
                () -> assertFalse(CaptureMediaSummary.empty().hasAcceptedCandidates()),
                () -> assertFalse(CaptureMediaSummary.empty().hasRecoveredUniqueFrames()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaSummary(-1, 0, 0, 0, 0, 0, 0, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaSummary(1, 2, 0, 0, 0, 0, 0, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaSummary(1, 1, 2, 0, 0, 0, 0, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaSummary(1, 1, 1, 0, 0, 2, 0, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaSummary(1, 1, 1, 0, 0, 0, 2, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("Result copies diagnostics and delegates status helper behavior")
    void resultCopiesDiagnosticsAndDelegatesStatusHelperBehavior() {
        List<CaptureMediaDiagnostic> diagnostics = new ArrayList<>(List.of(duplicateWarningDiagnostic()));

        CaptureMediaReceiverResult result = CaptureMediaReceiverResult.eligible(
                summary(),
                diagnostics,
                "Media input is eligible for restore"
        );
        diagnostics.clear();

        assertAll(
                () -> assertEquals(CaptureMediaReceiverStatus.ELIGIBLE, result.status()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.diagnostics().add(duplicateWarningDiagnostic())
                ),
                () -> assertTrue(result.eligibleForRestore()),
                () -> assertFalse(result.restoreAttempted()),
                () -> assertFalse(result.failed()),
                () -> assertFalse(result.restored()),
                () -> assertTrue(CaptureMediaReceiverStatus.ELIGIBLE.eligibleForRestore()),
                () -> assertFalse(CaptureMediaReceiverStatus.ELIGIBLE.restoreAttempted()),
                () -> assertFalse(CaptureMediaReceiverStatus.ELIGIBLE.terminalFailure())
        );
    }

    @Test
    @DisplayName("Result rejects invalid status, restore, and blocking diagnostic combinations")
    void resultRejectsInvalidStatusRestoreAndBlockingDiagnosticCombinations() {
        ReaderRestoreResult restored = restoredResult();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaReceiverResult.incomplete(summary(), List.of(), "Missing content")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaReceiverResult.eligible(
                                summary(),
                                List.of(missingUniqueFrameDiagnostic()),
                                "Invalid eligible result"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaReceiverResult(
                                CaptureMediaReceiverStatus.ELIGIBLE,
                                summary(),
                                List.of(),
                                Optional.of(restored),
                                "Invalid restore result"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaReceiverResult(
                                CaptureMediaReceiverStatus.RESTORED,
                                summary(),
                                List.of(),
                                Optional.empty(),
                                "Missing restore result"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaReceiverResult.restored(
                                restoredSummary(),
                                List.of(),
                                failedRestoreResult()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaReceiverResult.restoreFailed(
                                summary(),
                                List.of(missingUniqueFrameDiagnostic()),
                                Optional.empty(),
                                "Restore failed"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> CaptureMediaReceiverResult.restoreFailed(
                                summary(),
                                List.of(restoreFailureDiagnostic()),
                                Optional.of(restored),
                                "Restore failed"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CaptureMediaReceiverResult(
                                CaptureMediaReceiverStatus.ELIGIBLE,
                                summary(),
                                Arrays.asList((CaptureMediaDiagnostic) null),
                                Optional.empty(),
                                "Invalid diagnostics"
                        )
                )
        );
    }

    @Test
    @DisplayName("Restored and failed restore results expose restore outcome helpers")
    void restoredAndFailedRestoreResultsExposeRestoreOutcomeHelpers() {
        CaptureMediaReceiverResult restored = CaptureMediaReceiverResult.restored(
                restoredSummary(),
                List.of(duplicateWarningDiagnostic()),
                restoredResult()
        );
        CaptureMediaReceiverResult restoreFailed = CaptureMediaReceiverResult.restoreFailed(
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
                () -> assertTrue(CaptureMediaReceiverStatus.RESTORE_FAILED.terminalFailure()),
                () -> assertTrue(CaptureMediaReceiverStatus.RESTORED.restorationSucceeded())
        );
    }

    private CaptureMediaSummary summary() {
        return new CaptureMediaSummary(5, 4, 3, 1, 0, 1, 2, 12, 0);
    }

    private CaptureMediaSummary restoredSummary() {
        return new CaptureMediaSummary(5, 4, 3, 1, 0, 1, 2, 12, 2);
    }

    private CaptureMediaDiagnostic duplicateWarningDiagnostic() {
        return CaptureMediaDiagnostic.forSource(
                CaptureMediaDiagnosticCode.DUPLICATE_MEDIA_FRAME,
                CaptureMediaDiagnosticSeverity.WARNING,
                CaptureMediaSourceKind.EXTRACTED_FRAME_FOLDER,
                "frame-002.png",
                "Duplicate media frame content"
        );
    }

    private CaptureMediaDiagnostic missingUniqueFrameDiagnostic() {
        return CaptureMediaDiagnostic.forMediaSet(
                CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME,
                CaptureMediaDiagnosticSeverity.ERROR,
                "Required unique frame content is missing"
        );
    }

    private CaptureMediaDiagnostic restoreFailureDiagnostic() {
        return CaptureMediaDiagnostic.forMediaSet(
                CaptureMediaDiagnosticCode.RESTORE_FAILURE,
                CaptureMediaDiagnosticSeverity.ERROR,
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
