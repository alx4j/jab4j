package com.alx4j.jab4j.reader.capture.media.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;

@DisplayName("Reprojection metric calculator")
class ReprojectionMetricCalculatorTest {

    private final ReprojectionMetricCalculator calculator = new ReprojectionMetricCalculator();

    @Test
    @DisplayName("Calculates pixel and module summaries deterministically")
    void calculatesPixelAndModuleSummariesDeterministically() {
        ReprojectionMetrics metrics = calculator.calculate(List.of(0.0d, 2.0d, 4.0d, 6.0d, 10.0d), 2.0d);

        assertAll(
                () -> assertEquals(4.4d, metrics.meanErrorPixels(), 0.000001d),
                () -> assertEquals(4.0d, metrics.medianErrorPixels(), 0.000001d),
                () -> assertEquals(10.0d, metrics.p95ErrorPixels(), 0.000001d),
                () -> assertEquals(10.0d, metrics.maxErrorPixels(), 0.000001d),
                () -> assertEquals(2.2d, metrics.meanErrorModules(), 0.000001d),
                () -> assertEquals(2.0d, metrics.medianErrorModules(), 0.000001d),
                () -> assertEquals(5.0d, metrics.p95ErrorModules(), 0.000001d),
                () -> assertEquals(5.0d, metrics.maxErrorModules(), 0.000001d),
                () -> assertEquals(List.of(0.0d, 2.0d, 4.0d, 6.0d, 10.0d), metrics.pointErrorsPixels()),
                () -> assertEquals(List.of(0.0d, 1.0d, 2.0d, 3.0d, 5.0d), metrics.pointErrorsModules())
        );
    }

    @Test
    @DisplayName("Rejects invalid error inputs")
    void rejectsInvalidErrorInputs() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> calculator.calculate(List.of(-0.1d), 2.0d)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> calculator.calculate(List.of(Double.NaN), 2.0d)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> calculator.calculate(List.of(1.0d), 0.0d))
        );
    }
}
