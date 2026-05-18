package com.alx4j.jab4j.reader.capture.media.sample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

@DisplayName("Capture media palette sampler")
class CaptureMediaPaletteSamplerTest {

    private final CaptureMediaPaletteSampler sampler = new CaptureMediaPaletteSampler();

    @Test
    @DisplayName("Exact rendered palette colors map deterministically by index")
    void exactRenderedPaletteColorsMapDeterministicallyByIndex() {
        List<Integer> palette = sampler.paletteArgb();

        assertAll(
                () -> assertEquals(8, palette.size()),
                () -> assertEquals(0, sampler.exactPaletteIndex(0xFF000000).orElseThrow()),
                () -> assertEquals(1, sampler.exactPaletteIndex(0xFF0000FF).orElseThrow()),
                () -> assertEquals(2, sampler.exactPaletteIndex(0xFF00FF00).orElseThrow()),
                () -> assertEquals(3, sampler.exactPaletteIndex(0xFF00FFFF).orElseThrow()),
                () -> assertEquals(4, sampler.exactPaletteIndex(0xFFFF0000).orElseThrow()),
                () -> assertEquals(5, sampler.exactPaletteIndex(0xFFFF00FF).orElseThrow()),
                () -> assertEquals(6, sampler.exactPaletteIndex(0xFFFFFF00).orElseThrow()),
                () -> assertEquals(7, sampler.exactPaletteIndex(0xFFFFFFFF).orElseThrow()),
                () -> assertTrue(sampler.exactPaletteIndex(0xFF010101).isEmpty()),
                () -> assertThrows(UnsupportedOperationException.class, () -> palette.add(0xFF010101))
        );
    }

    @Test
    @DisplayName("Exact palette model preserves current index mapping")
    void exactPaletteModelPreservesCurrentIndexMapping() {
        CaptureMediaPaletteModel model = sampler.exactPaletteModel();
        CaptureMediaPaletteSample sample = sampler.tolerantPaletteSample(0xFFFF0000, model);

        assertAll(
                () -> assertFalse(model.calibrated()),
                () -> assertTrue(model.fallbackReason().isEmpty()),
                () -> assertEquals(8, model.size()),
                () -> assertEquals(sampler.paletteArgb(), model.paletteArgb()),
                () -> assertEquals(4, sampler.paletteIndex(0xFFFF0000, model).orElseThrow()),
                () -> assertTrue(sampler.paletteIndex(0xFFFF0101, model).isEmpty()),
                () -> assertEquals(CaptureMediaPaletteSampleStatus.EXACT, sample.status()),
                () -> assertEquals(4, sample.paletteIndex()),
                () -> assertEquals(0xFFFF0000, sample.paletteArgb()),
                () -> assertEquals(1.0d, sample.confidence())
        );
    }

    @Test
    @DisplayName("Normalized frame sampling uses exact palette matching only")
    void normalizedFrameSamplingUsesExactPaletteMatchingOnly() {
        NormalizedCaptureFrame frame = frame(new int[] {
                0xFF000000, 0xFF010101,
                0xFFFF0000, 0xFFFFFFFF
        });

        assertAll(
                () -> assertEquals(0, sampler.sampleExactPaletteIndex(frame, 0, 0).orElseThrow()),
                () -> assertTrue(sampler.sampleExactPaletteIndex(frame, 0, 1).isEmpty()),
                () -> assertEquals(4, sampler.sampleExactPaletteIndex(frame, 1, 0).orElseThrow()),
                () -> assertEquals(7, sampler.sampleExactPaletteIndex(frame, 1, 1).orElseThrow())
        );
    }

    @Test
    @DisplayName("Tolerant samples preserve exact palette confidence")
    void tolerantSamplesPreserveExactPaletteConfidence() {
        CaptureMediaPaletteSample sample = sampler.tolerantPaletteSample(0xFFFF0000);

        assertAll(
                () -> assertEquals(CaptureMediaPaletteSampleStatus.EXACT, sample.status()),
                () -> assertTrue(sample.accepted()),
                () -> assertFalse(sample.diagnostic()),
                () -> assertEquals(4, sample.paletteIndex()),
                () -> assertEquals(0.0d, sample.rgbDistance()),
                () -> assertEquals(0.0d, sample.colorDistanceScore()),
                () -> assertEquals(1.0d, sample.confidence())
        );
    }

    @Test
    @DisplayName("Tolerant samples map small camera shifts to the nearest palette color")
    void tolerantSamplesMapSmallCameraShiftsToNearestPaletteColor() {
        CaptureMediaPaletteSample sample = sampler.tolerantPaletteSample(0xFF060504);

        assertAll(
                () -> assertEquals(CaptureMediaPaletteSampleStatus.TOLERANT, sample.status()),
                () -> assertTrue(sample.accepted()),
                () -> assertFalse(sample.diagnostic()),
                () -> assertEquals(0, sample.paletteIndex()),
                () -> assertEquals(0xFF000000, sample.paletteArgb()),
                () -> assertTrue(sample.rgbDistance() > 0.0d),
                () -> assertTrue(sample.confidence() > 0.80d)
        );
    }

    @Test
    @DisplayName("Calibrated palette maps known shifted colors to expected indexes")
    void calibratedPaletteMapsKnownShiftedColorsToExpectedIndexes() {
        CaptureMediaCalibratedPalette calibratedPalette = sampler.calibratePalette(shiftedPaletteSamples());
        CaptureMediaPaletteModel model = calibratedPalette.model();
        NormalizedCaptureFrame shiftedFrame = frame(new int[] {
                0xFFF20708, 0xFFF3F4F5,
                0xFF050403, 0xFF0508F0
        });

        assertAll(
                () -> assertTrue(model.calibrated()),
                () -> assertFalse(calibratedPalette.fallbackToExact()),
                () -> assertEquals(8, calibratedPalette.observedColorCount()),
                () -> assertTrue(calibratedPalette.confidence() > 0.90d),
                () -> assertTrue(calibratedPalette.maximumRgbDistance() > 0.0d),
                () -> assertEquals(4, sampler.paletteIndex(0xFFF20708, model).orElseThrow()),
                () -> assertEquals(7, sampler.paletteIndex(0xFFF3F4F5, model).orElseThrow()),
                () -> assertEquals(4, sampler.tolerantPaletteSample(0xFFF20708, model).paletteIndex()),
                () -> assertEquals(CaptureMediaPaletteSampleStatus.EXACT,
                        sampler.tolerantPaletteSample(0xFFF20708, model).status()),
                () -> assertEquals(0xFFFF0000, sampler.tolerantPaletteSample(0xFFF20708, model).paletteArgb()),
                () -> assertEquals(4, sampler.sampleTolerantPalette(shiftedFrame, 0, 0, model).paletteIndex()),
                () -> assertEquals(0, sampler.sampleTolerantPalette(shiftedFrame, 1, 0, model).paletteIndex())
        );
    }

    @Test
    @DisplayName("Calibration exposes per-index counts confidence and maximum distance")
    void calibrationExposesPerIndexCountsConfidenceAndMaximumDistance() {
        CaptureMediaCalibratedPalette calibratedPalette = sampler.calibratePalette(List.of(
                new CaptureMediaPaletteCalibrationSample(0, 0xFF050403),
                new CaptureMediaPaletteCalibrationSample(0, 0xFF060503),
                new CaptureMediaPaletteCalibrationSample(7, 0xFFF4F5F6)
        ));
        CaptureMediaPaletteModel model = calibratedPalette.model();
        CaptureMediaPaletteModelColor black = model.color(0);
        CaptureMediaPaletteModelColor white = model.color(7);

        assertAll(
                () -> assertTrue(model.calibrated()),
                () -> assertEquals(2, model.observedColorCount()),
                () -> assertEquals(2, calibratedPalette.observedColors().size()),
                () -> assertTrue(black.calibrated()),
                () -> assertEquals(2, black.sampleCount()),
                () -> assertEquals(1, white.sampleCount()),
                () -> assertTrue(black.maximumRgbDistance() > 0.0d),
                () -> assertTrue(black.confidence() > 0.95d),
                () -> assertTrue(model.maximumRgbDistance() >= black.maximumRgbDistance()),
                () -> assertTrue(calibratedPalette.fallbackReason().isEmpty())
        );
    }

    @Test
    @DisplayName("Calibration accepts local black and white references from CV evidence")
    void calibrationAcceptsLocalBlackAndWhiteReferencesFromCvEvidence() {
        CaptureMediaCalibratedPalette calibratedPalette = sampler.calibratePalette(List.of(
                new CaptureMediaPaletteCalibrationSample(7, 0xFFE3D9D5),
                new CaptureMediaPaletteCalibrationSample(0, 0xFF1B1917)
        ));
        CaptureMediaPaletteModel model = calibratedPalette.model();

        assertAll(
                () -> assertTrue(model.calibrated()),
                () -> assertFalse(calibratedPalette.fallbackToExact()),
                () -> assertEquals(2, calibratedPalette.observedColorCount()),
                () -> assertEquals(0xFFE3D9D5, model.color(7).modelArgb()),
                () -> assertEquals(0xFF1B1917, model.color(0).modelArgb()),
                () -> assertEquals(0xFF1B19D5, model.color(1).modelArgb()),
                () -> assertEquals(0xFFE31917, model.color(4).modelArgb()),
                () -> assertEquals(7, sampler.tolerantPaletteSample(0xFFE3D9D5, model).paletteIndex()),
                () -> assertEquals(0, sampler.tolerantPaletteSample(0xFF1B1917, model).paletteIndex()),
                () -> assertEquals(1, sampler.tolerantPaletteSample(0xFF1B19D5, model).paletteIndex()),
                () -> assertEquals(4, sampler.tolerantPaletteSample(0xFFE31917, model).paletteIndex()),
                () -> assertTrue(calibratedPalette.confidence() > 0.75d)
        );
    }

    @Test
    @DisplayName("Weak calibration falls back to exact palette")
    void weakCalibrationFallsBackToExactPalette() {
        CaptureMediaCalibratedPalette calibratedPalette = sampler.calibratePalette(List.of(
                new CaptureMediaPaletteCalibrationSample(0, 0xFF050403)
        ));
        CaptureMediaPaletteModel model = calibratedPalette.model();

        assertAll(
                () -> assertTrue(calibratedPalette.fallbackToExact()),
                () -> assertFalse(model.calibrated()),
                () -> assertEquals("insufficient reference colors", calibratedPalette.fallbackReason().orElseThrow()),
                () -> assertEquals(1, calibratedPalette.observedColorCount()),
                () -> assertEquals(0xFF000000, model.color(0).modelArgb()),
                () -> assertTrue(sampler.paletteIndex(0xFF050403, model).isEmpty()),
                () -> assertEquals(0, sampler.tolerantPaletteSample(0xFF050403, model).paletteIndex())
        );
    }

    @Test
    @DisplayName("Contradictory calibration falls back to exact palette")
    void contradictoryCalibrationFallsBackToExactPalette() {
        CaptureMediaCalibratedPalette calibratedPalette = sampler.calibratePalette(List.of(
                new CaptureMediaPaletteCalibrationSample(0, 0xFF000000),
                new CaptureMediaPaletteCalibrationSample(4, 0xFF0000FF)
        ));

        assertAll(
                () -> assertTrue(calibratedPalette.fallbackToExact()),
                () -> assertFalse(calibratedPalette.model().calibrated()),
                () -> assertTrue(calibratedPalette.fallbackReason().orElseThrow().contains("contradictory")),
                () -> assertEquals(2, calibratedPalette.observedColorCount()),
                () -> assertEquals(0xFFFF0000, calibratedPalette.model().color(4).modelArgb())
        );
    }

    @Test
    @DisplayName("Calibration keeps dominant color clusters and ignores outliers")
    void calibrationKeepsDominantColorClustersAndIgnoresOutliers() {
        CaptureMediaCalibratedPalette calibratedPalette = sampler.calibratePalette(List.of(
                new CaptureMediaPaletteCalibrationSample(0, 0xFF1B1917),
                new CaptureMediaPaletteCalibrationSample(7, 0xFFE3D9D5),
                new CaptureMediaPaletteCalibrationSample(4, 0xFFE31917),
                new CaptureMediaPaletteCalibrationSample(4, 0xFFE21A18),
                new CaptureMediaPaletteCalibrationSample(4, 0xFFFF6060)
        ));
        CaptureMediaPaletteModel model = calibratedPalette.model();

        assertAll(
                () -> assertTrue(model.calibrated()),
                () -> assertEquals(3, calibratedPalette.observedColorCount()),
                () -> assertEquals(2, model.color(4).sampleCount()),
                () -> assertEquals(4, sampler.tolerantPaletteSample(0xFFE31917, model).paletteIndex()),
                () -> assertTrue(calibratedPalette.fallbackReason().isEmpty())
        );
    }

    @Test
    @DisplayName("Low-confidence calibration falls back to exact palette")
    void lowConfidenceCalibrationFallsBackToExactPalette() {
        CaptureMediaCalibratedPalette calibratedPalette = sampler.calibratePalette(List.of(
                new CaptureMediaPaletteCalibrationSample(0, 0xFF606060),
                new CaptureMediaPaletteCalibrationSample(7, 0xFF9F9F9F)
        ));

        assertAll(
                () -> assertTrue(calibratedPalette.fallbackToExact()),
                () -> assertFalse(calibratedPalette.model().calibrated()),
                () -> assertEquals("confidence below threshold",
                        calibratedPalette.fallbackReason().orElseThrow()),
                () -> assertEquals(2, calibratedPalette.observedColorCount()),
                () -> assertEquals(0xFF000000, calibratedPalette.model().color(0).modelArgb()),
                () -> assertEquals(0xFFFFFFFF, calibratedPalette.model().color(7).modelArgb())
        );
    }

    @Test
    @DisplayName("Low-confidence shifted samples emit nonblocking color diagnostics")
    void lowConfidenceShiftedSamplesEmitNonblockingColorDiagnostics() {
        NormalizedCaptureFrame frame = frame(new int[] {
                0xFF1E1E1E, 0xFF000000,
                0xFFFF0000, 0xFFFFFFFF
        });
        CaptureMediaPaletteSample sample = sampler.sampleTolerantPalette(frame, 0, 0);

        Optional<CaptureMediaDiagnostic> diagnostic = sampler.diagnosticFor(frame, sample);

        assertAll(
                () -> assertEquals(CaptureMediaPaletteSampleStatus.LOW_CONFIDENCE, sample.status()),
                () -> assertTrue(sample.accepted()),
                () -> assertEquals(0, sample.paletteIndex()),
                () -> assertTrue(diagnostic.isPresent()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                        diagnostic.orElseThrow().code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.WARNING, diagnostic.orElseThrow().severity()),
                () -> assertFalse(diagnostic.orElseThrow().blocking()),
                () -> assertEquals("source.png", diagnostic.orElseThrow().sourceId().orElseThrow()),
                () -> assertEquals(0, diagnostic.orElseThrow().callerOrder().orElseThrow()),
                () -> assertTrue(diagnostic.orElseThrow().metrics().containsKey("colorDistanceScore")),
                () -> assertTrue(diagnostic.orElseThrow().metrics().containsKey("paletteConfidence"))
        );
    }

    @Test
    @DisplayName("Samples outside threshold emit blocking color diagnostics")
    void samplesOutsideThresholdEmitBlockingColorDiagnostics() {
        NormalizedCaptureFrame frame = frame(new int[] {
                0xFF555555, 0xFF000000,
                0xFFFF0000, 0xFFFFFFFF
        });
        CaptureMediaPaletteSample sample = sampler.sampleTolerantPalette(frame, 0, 0);

        CaptureMediaDiagnostic diagnostic = sampler.diagnosticFor(frame, sample).orElseThrow();

        assertAll(
                () -> assertEquals(CaptureMediaPaletteSampleStatus.REJECTED, sample.status()),
                () -> assertFalse(sample.accepted()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, diagnostic.code()),
                () -> assertEquals(CaptureMediaDiagnosticSeverity.ERROR, diagnostic.severity()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertEquals(0.0d, sample.confidence()),
                () -> assertTrue(sample.colorDistanceScore() > 0.0d)
        );
    }

    @Test
    @DisplayName("Area samples use median RGB before palette mapping")
    void areaSamplesUseMedianRgbBeforePaletteMapping() {
        int[] pixels = new int[25];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = 0xFF000000;
        }
        pixels[(2 * 5) + 2] = 0xFF808080;
        NormalizedCaptureFrame frame = frame(5, 5, pixels);

        CaptureMediaPaletteSample centerSample = sampler.sampleTolerantPalette(frame, 2, 2);
        CaptureMediaPaletteSample areaSample = sampler.sampleTolerantPaletteArea(frame, 2, 2, 1);

        assertAll(
                () -> assertEquals(CaptureMediaPaletteSampleStatus.REJECTED, centerSample.status()),
                () -> assertEquals(CaptureMediaPaletteSampleStatus.EXACT, areaSample.status()),
                () -> assertEquals(0, areaSample.paletteIndex()),
                () -> assertEquals(0xFF000000, areaSample.sourceArgb())
        );
    }

    private List<CaptureMediaPaletteCalibrationSample> shiftedPaletteSamples() {
        return List.of(
                new CaptureMediaPaletteCalibrationSample(0, 0xFF050403),
                new CaptureMediaPaletteCalibrationSample(1, 0xFF0508F0),
                new CaptureMediaPaletteCalibrationSample(2, 0xFF08F104),
                new CaptureMediaPaletteCalibrationSample(3, 0xFF0CF0F4),
                new CaptureMediaPaletteCalibrationSample(4, 0xFFF20708),
                new CaptureMediaPaletteCalibrationSample(5, 0xFFF20AF0),
                new CaptureMediaPaletteCalibrationSample(6, 0xFFF2F108),
                new CaptureMediaPaletteCalibrationSample(7, 0xFFF3F4F5)
        );
    }

    private NormalizedCaptureFrame frame(int[] pixels) {
        return frame(2, 2, pixels);
    }

    private NormalizedCaptureFrame frame(int width, int height, int[] pixels) {
        return new NormalizedCaptureFrame(
                "source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                width,
                height,
                width,
                height,
                "png",
                "abc123",
                "test-layout",
                FrameCorners.exactFrame(width, height),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }
}
