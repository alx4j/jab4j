package com.alx4j.jab4j.reader.capture.media.quality;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Capture media quality metrics")
class CaptureMediaQualityMetricsTest {

    @Test
    @DisplayName("Exact rendered-frame metrics use measured coverage and placeholders where not measurable")
    void exactRenderedFrameMetricsUseMeasuredCoverageAndPlaceholdersWhereNotMeasurable() {
        CaptureMediaQualityMetrics metrics = CaptureMediaQualityMetrics.exactRenderedFrame();

        assertAll(
                () -> assertEquals(1.0d, metrics.frameCoverageRatio()),
                () -> assertEquals(0.0d, metrics.skewScore()),
                () -> assertEquals(CaptureMediaQualityMetrics.NOT_MEASURED, metrics.blurScore()),
                () -> assertTrue(metrics.measured(metrics.frameCoverageRatio())),
                () -> assertTrue(metrics.measured(metrics.skewScore())),
                () -> assertFalse(metrics.measured(metrics.blurScore())),
                () -> assertFalse(metrics.measured(metrics.glareScore())),
                () -> assertFalse(metrics.measured(metrics.exposureScore())),
                () -> assertFalse(metrics.measured(metrics.colorDistanceScore()))
        );
    }

    @Test
    @DisplayName("Metric values reject non-finite and out-of-range scores")
    void metricValuesRejectNonFiniteAndOutOfRangeScores() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaptureMediaQualityMetrics(Double.NaN, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaptureMediaQualityMetrics(-0.01d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaptureMediaQualityMetrics(0.0d, 1.01d, 0.0d, 0.0d, 0.0d, 0.0d))
        );
    }
}
