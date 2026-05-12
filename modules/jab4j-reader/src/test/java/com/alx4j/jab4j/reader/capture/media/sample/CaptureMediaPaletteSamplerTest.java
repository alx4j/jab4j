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

    private NormalizedCaptureFrame frame(int[] pixels) {
        return new NormalizedCaptureFrame(
                "source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                2,
                2,
                2,
                2,
                "png",
                "abc123",
                "test-layout",
                FrameCorners.exactFrame(2, 2),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }
}
