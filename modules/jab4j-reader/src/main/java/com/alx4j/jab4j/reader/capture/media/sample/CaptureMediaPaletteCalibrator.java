package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic builder for conservative candidate-local palette calibration.
 */
public final class CaptureMediaPaletteCalibrator {

    private static final int MIN_OBSERVED_PALETTE_COLORS = 2;
    private static final double MAX_CLUSTER_RGB_DISTANCE = 32.0d;
    private static final double MIN_OBSERVED_CENTER_SEPARATION = 96.0d;
    private static final double MIN_MODEL_CONFIDENCE = 0.75d;
    private static final double MAX_RGB_DISTANCE = Math.sqrt(3.0d * 255.0d * 255.0d);
    private static final String INSUFFICIENT_REFERENCE_COLORS = "insufficient reference colors";
    private static final String CONTRADICTORY_REFERENCE_COLORS = "contradictory reference colors";
    private static final String CONFIDENCE_BELOW_THRESHOLD = "confidence below threshold";

    private final List<Integer> exactPaletteArgb;

    /**
     * Creates a calibrator for one exact rendered palette model.
     *
     * @param exactPaletteModel exact rendered palette model
     */
    public CaptureMediaPaletteCalibrator(CaptureMediaPaletteModel exactPaletteModel) {
        Objects.requireNonNull(exactPaletteModel, "exactPaletteModel must not be null");
        exactPaletteArgb = exactPaletteModel.paletteArgb();
    }

    /**
     * Builds a calibrated palette from known expected-index observations.
     *
     * <p>The result falls back to the exact palette when references are too sparse, contradictory within one expected
     * color, ambiguous between expected palette indexes, or below the conservative confidence threshold.</p>
     *
     * @param referenceSamples known observed ARGB samples labeled with expected palette indexes
     * @return calibrated palette result or exact-palette fallback
     */
    public CaptureMediaCalibratedPalette calibrate(List<CaptureMediaPaletteCalibrationSample> referenceSamples) {
        Objects.requireNonNull(referenceSamples, "referenceSamples must not be null");
        List<CaptureMediaPaletteCalibrationSample> samples = List.copyOf(referenceSamples);
        Map<Integer, List<Integer>> samplesByIndex = groupByExpectedIndex(samples);
        if (samplesByIndex.size() < MIN_OBSERVED_PALETTE_COLORS) {
            return fallback(INSUFFICIENT_REFERENCE_COLORS, samples);
        }

        Map<Integer, CaptureMediaPaletteModelColor> calibratedByIndex = new TreeMap<>();
        boolean contradictorySamplesDiscarded = false;
        for (Map.Entry<Integer, List<Integer>> entry : samplesByIndex.entrySet()) {
            int paletteIndex = entry.getKey();
            FilteredSamples filteredSamples = filterConsistentSamples(paletteIndex, entry.getValue());
            contradictorySamplesDiscarded = contradictorySamplesDiscarded || filteredSamples.contradictory();
            List<Integer> observedArgbSamples = filteredSamples.observedArgbSamples();
            if (observedArgbSamples.isEmpty()) {
                continue;
            }
            int modelArgb = medianArgb(observedArgbSamples);
            double clusterDistance = maximumDistanceToModel(observedArgbSamples, modelArgb);
            if (clusterDistance > MAX_CLUSTER_RGB_DISTANCE || nearestExactPaletteIndex(modelArgb) != paletteIndex) {
                contradictorySamplesDiscarded = true;
                continue;
            }

            double maximumRgbDistance = maximumDistanceToExpected(paletteIndex, observedArgbSamples);
            double confidence = confidence(maximumRgbDistance);
            calibratedByIndex.put(paletteIndex, new CaptureMediaPaletteModelColor(
                    paletteIndex,
                    exactPaletteArgb.get(paletteIndex),
                    modelArgb,
                    observedArgbSamples.size(),
                    maximumRgbDistance,
                    confidence
            ));
        }
        if (calibratedByIndex.size() < MIN_OBSERVED_PALETTE_COLORS) {
            return fallback(
                    contradictorySamplesDiscarded ? CONTRADICTORY_REFERENCE_COLORS : INSUFFICIENT_REFERENCE_COLORS,
                    samples
            );
        }
        if (!observedCentersAreSeparated(calibratedByIndex)) {
            return fallback(CONTRADICTORY_REFERENCE_COLORS, samples);
        }

        List<CaptureMediaPaletteModelColor> colors = new ArrayList<>(exactPaletteArgb.size());
        BlackWhiteModel blackWhiteModel = blackWhiteModel(calibratedByIndex);
        double confidence = 1.0d;
        double maximumRgbDistance = 0.0d;
        for (int index = 0; index < exactPaletteArgb.size(); index++) {
            CaptureMediaPaletteModelColor color = calibratedByIndex.get(index);
            if (color == null) {
                color = blackWhiteModel == null ? exactColor(index) : inferredColor(index, blackWhiteModel);
            }
            colors.add(color);
            if (color.calibrated()) {
                confidence = Math.min(confidence, color.confidence());
                maximumRgbDistance = Math.max(maximumRgbDistance, color.maximumRgbDistance());
            }
        }
        if (confidence < MIN_MODEL_CONFIDENCE) {
            return fallback(CONFIDENCE_BELOW_THRESHOLD, samples);
        }
        return new CaptureMediaCalibratedPalette(new CaptureMediaPaletteModel(
                colors,
                true,
                confidence,
                calibratedByIndex.size(),
                maximumRgbDistance,
                java.util.Optional.empty()
        ));
    }

    private Map<Integer, List<Integer>> groupByExpectedIndex(List<CaptureMediaPaletteCalibrationSample> samples) {
        Map<Integer, List<Integer>> samplesByIndex = new TreeMap<>();
        for (CaptureMediaPaletteCalibrationSample sample : samples) {
            Objects.requireNonNull(sample, "referenceSamples must not contain null entries");
            int paletteIndex = sample.expectedPaletteIndex();
            if (paletteIndex >= exactPaletteArgb.size()) {
                throw new IllegalArgumentException("expectedPaletteIndex is outside the palette model");
            }
            samplesByIndex.computeIfAbsent(paletteIndex, ignored -> new ArrayList<>())
                    .add(sample.observedArgb());
        }
        return samplesByIndex;
    }

    private FilteredSamples filterConsistentSamples(int paletteIndex, List<Integer> observedArgbSamples) {
        List<Integer> nearestMatchingSamples = observedArgbSamples.stream()
                .filter(argb -> nearestExactPaletteIndex(argb) == paletteIndex)
                .toList();
        boolean contradictory = nearestMatchingSamples.size() != observedArgbSamples.size();
        if (nearestMatchingSamples.size() <= 1) {
            return new FilteredSamples(nearestMatchingSamples, contradictory);
        }

        int medianArgb = medianArgb(nearestMatchingSamples);
        if (nearestExactPaletteIndex(medianArgb) == paletteIndex
                && maximumDistanceToModel(nearestMatchingSamples, medianArgb) <= MAX_CLUSTER_RGB_DISTANCE) {
            return new FilteredSamples(nearestMatchingSamples, contradictory);
        }

        List<Integer> dominantCluster = List.of();
        for (int centerArgb : nearestMatchingSamples) {
            List<Integer> cluster = nearestMatchingSamples.stream()
                    .filter(argb -> rgbDistance(argb, centerArgb) <= MAX_CLUSTER_RGB_DISTANCE)
                    .toList();
            if (betterCluster(paletteIndex, cluster, dominantCluster)) {
                dominantCluster = cluster;
            }
        }
        if (dominantCluster.size() <= 1) {
            return new FilteredSamples(List.of(), true);
        }
        return new FilteredSamples(dominantCluster, true);
    }

    private boolean betterCluster(int paletteIndex, List<Integer> candidate, List<Integer> current) {
        if (candidate.size() != current.size()) {
            return candidate.size() > current.size();
        }
        return maximumDistanceToExpected(paletteIndex, candidate) < maximumDistanceToExpected(paletteIndex, current);
    }

    private CaptureMediaCalibratedPalette fallback(
            String fallbackReason,
            List<CaptureMediaPaletteCalibrationSample> samples
    ) {
        return new CaptureMediaCalibratedPalette(CaptureMediaPaletteModel.fallbackToExact(
                exactPaletteArgb,
                fallbackReason,
                distinctObservedColorCount(samples),
                maximumDistanceToExpected(samples)
        ));
    }

    private int distinctObservedColorCount(List<CaptureMediaPaletteCalibrationSample> samples) {
        return (int) samples.stream()
                .map(CaptureMediaPaletteCalibrationSample::expectedPaletteIndex)
                .distinct()
                .count();
    }

    private double maximumDistanceToExpected(List<CaptureMediaPaletteCalibrationSample> samples) {
        double maximumDistance = 0.0d;
        for (CaptureMediaPaletteCalibrationSample sample : samples) {
            maximumDistance = Math.max(maximumDistance, rgbDistance(
                    sample.observedArgb(),
                    exactPaletteArgb.get(sample.expectedPaletteIndex())
            ));
        }
        return maximumDistance;
    }

    private double maximumDistanceToExpected(int paletteIndex, List<Integer> observedArgbSamples) {
        double maximumDistance = 0.0d;
        int expectedArgb = exactPaletteArgb.get(paletteIndex);
        for (int observedArgb : observedArgbSamples) {
            maximumDistance = Math.max(maximumDistance, rgbDistance(observedArgb, expectedArgb));
        }
        return maximumDistance;
    }

    private boolean observedCentersAreSeparated(Map<Integer, CaptureMediaPaletteModelColor> calibratedByIndex) {
        List<CaptureMediaPaletteModelColor> colors = new ArrayList<>(calibratedByIndex.values());
        for (int first = 0; first < colors.size(); first++) {
            for (int second = first + 1; second < colors.size(); second++) {
                if (rgbDistance(colors.get(first).modelArgb(), colors.get(second).modelArgb())
                        < MIN_OBSERVED_CENTER_SEPARATION) {
                    return false;
                }
            }
        }
        return true;
    }

    private BlackWhiteModel blackWhiteModel(Map<Integer, CaptureMediaPaletteModelColor> calibratedByIndex) {
        CaptureMediaPaletteModelColor black = calibratedByIndex.get(0);
        CaptureMediaPaletteModelColor white = calibratedByIndex.get(exactPaletteArgb.size() - 1);
        if (black == null || white == null) {
            return null;
        }
        return new BlackWhiteModel(black.modelArgb(), white.modelArgb());
    }

    private CaptureMediaPaletteModelColor inferredColor(int paletteIndex, BlackWhiteModel model) {
        int expectedArgb = exactPaletteArgb.get(paletteIndex);
        int modelArgb = 0xFF000000
                | (modelChannel(red(expectedArgb), red(model.blackArgb()), red(model.whiteArgb())) << 16)
                | (modelChannel(green(expectedArgb), green(model.blackArgb()), green(model.whiteArgb())) << 8)
                | modelChannel(blue(expectedArgb), blue(model.blackArgb()), blue(model.whiteArgb()));
        return new CaptureMediaPaletteModelColor(
                paletteIndex,
                expectedArgb,
                modelArgb,
                0,
                0.0d,
                1.0d
        );
    }

    private int modelChannel(int expectedValue, int blackValue, int whiteValue) {
        return expectedValue < 128 ? blackValue : whiteValue;
    }

    private CaptureMediaPaletteModelColor exactColor(int paletteIndex) {
        int expectedArgb = exactPaletteArgb.get(paletteIndex);
        return new CaptureMediaPaletteModelColor(
                paletteIndex,
                expectedArgb,
                expectedArgb,
                0,
                0.0d,
                1.0d
        );
    }

    private int nearestExactPaletteIndex(int argb) {
        int nearestIndex = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < exactPaletteArgb.size(); index++) {
            double distance = rgbDistance(argb, exactPaletteArgb.get(index));
            if (distance < nearestDistance) {
                nearestIndex = index;
                nearestDistance = distance;
            }
        }
        return nearestIndex;
    }

    private double maximumDistanceToModel(List<Integer> observedArgbSamples, int modelArgb) {
        double maximumDistance = 0.0d;
        for (int observedArgb : observedArgbSamples) {
            maximumDistance = Math.max(maximumDistance, rgbDistance(observedArgb, modelArgb));
        }
        return maximumDistance;
    }

    private int medianArgb(List<Integer> argbSamples) {
        int[] redValues = new int[argbSamples.size()];
        int[] greenValues = new int[argbSamples.size()];
        int[] blueValues = new int[argbSamples.size()];
        for (int index = 0; index < argbSamples.size(); index++) {
            int argb = argbSamples.get(index);
            redValues[index] = red(argb);
            greenValues[index] = green(argb);
            blueValues[index] = blue(argb);
        }
        return 0xFF000000
                | (median(redValues) << 16)
                | (median(greenValues) << 8)
                | median(blueValues);
    }

    private int median(int[] values) {
        int[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private double confidence(double rgbDistance) {
        return 1.0d - (Math.min(rgbDistance, MAX_RGB_DISTANCE) / MAX_RGB_DISTANCE);
    }

    private double rgbDistance(int firstArgb, int secondArgb) {
        int redDelta = red(firstArgb) - red(secondArgb);
        int greenDelta = green(firstArgb) - green(secondArgb);
        int blueDelta = blue(firstArgb) - blue(secondArgb);
        return Math.sqrt(
                (redDelta * redDelta)
                        + (greenDelta * greenDelta)
                        + (blueDelta * blueDelta)
        );
    }

    private int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private int blue(int argb) {
        return argb & 0xFF;
    }

    private record FilteredSamples(List<Integer> observedArgbSamples, boolean contradictory) {
    }

    private record BlackWhiteModel(int blackArgb, int whiteArgb) {
    }
}
