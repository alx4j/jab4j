package com.alx4j.jab4j.reader.capture.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Capture media diagnostic code classification")
class CaptureMediaDiagnosticCodeTest {

    @Test
    @DisplayName("Quality issues include pre-assembly media and protocol validation failures")
    void qualityIssuesIncludePreAssemblyMediaAndProtocolValidationFailures() {
        EnumSet<CaptureMediaDiagnosticCode> qualityIssues = EnumSet.of(
                CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                CaptureMediaDiagnosticCode.MONITOR_TOO_SMALL,
                CaptureMediaDiagnosticCode.PERSPECTIVE_TOO_SEVERE,
                CaptureMediaDiagnosticCode.FRAME_PARTIALLY_OUTSIDE_IMAGE,
                CaptureMediaDiagnosticCode.BLUR,
                CaptureMediaDiagnosticCode.GLARE_OR_OVEREXPOSURE,
                CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                CaptureMediaDiagnosticCode.TILE_DECODE_OR_ENVELOPE_FAILURE
        );

        assertClassification(qualityIssues, CaptureMediaDiagnosticCode::qualityIssue);
    }

    @Test
    @DisplayName("Duplicate classification remains limited to duplicate media frames")
    void duplicateClassificationRemainsLimitedToDuplicateMediaFrames() {
        assertClassification(
                EnumSet.of(CaptureMediaDiagnosticCode.DUPLICATE_MEDIA_FRAME),
                CaptureMediaDiagnosticCode::duplicate
        );
    }

    @Test
    @DisplayName("Unsupported media classification remains limited to unsupported format boundaries")
    void unsupportedMediaClassificationRemainsLimitedToUnsupportedFormatBoundaries() {
        assertClassification(
                EnumSet.of(
                        CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT,
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                        CaptureMediaDiagnosticCode.UNSUPPORTED_CODEC
                ),
                CaptureMediaDiagnosticCode::unsupportedMedia
        );
    }

    private void assertClassification(
            EnumSet<CaptureMediaDiagnosticCode> expected,
            Predicate<CaptureMediaDiagnosticCode> classifier
    ) {
        for (CaptureMediaDiagnosticCode code : CaptureMediaDiagnosticCode.values()) {
            assertEquals(expected.contains(code), classifier.test(code), code.name());
        }
    }
}
