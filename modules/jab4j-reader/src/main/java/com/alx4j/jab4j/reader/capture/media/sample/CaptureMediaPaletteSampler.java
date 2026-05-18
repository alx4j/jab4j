package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;

/**
 * Conservative media sampler that supports exact and bounded nearest-palette media samples.
 */
public final class CaptureMediaPaletteSampler {

    private static final double MAX_RGB_DISTANCE = Math.sqrt(3.0d * 255.0d * 255.0d);
    private static final double LOW_CONFIDENCE_RGB_DISTANCE = 24.0d;
    private static final double MAX_ACCEPTED_RGB_DISTANCE = 64.0d;
    private static final List<Integer> PALETTE = List.of(
            0xFF000000,
            0xFF0000FF,
            0xFF00FF00,
            0xFF00FFFF,
            0xFFFF0000,
            0xFFFF00FF,
            0xFFFFFF00,
            0xFFFFFFFF
    );
    private static final CaptureMediaPaletteModel EXACT_PALETTE_MODEL = CaptureMediaPaletteModel.exact(PALETTE);

    /**
     * Returns a copy of the exact eight-color rendered palette.
     *
     * @return ARGB palette values
     */
    public List<Integer> paletteArgb() {
        return List.copyOf(PALETTE);
    }

    /**
     * Returns the immutable exact rendered palette model.
     *
     * @return exact palette model
     */
    public CaptureMediaPaletteModel exactPaletteModel() {
        return EXACT_PALETTE_MODEL;
    }

    /**
     * Builds a conservative calibrated palette from known expected-index observations.
     *
     * @param referenceSamples known observed ARGB samples labeled with expected palette indexes
     * @return calibrated palette result or exact-palette fallback
     */
    public CaptureMediaCalibratedPalette calibratePalette(
            List<CaptureMediaPaletteCalibrationSample> referenceSamples
    ) {
        return new CaptureMediaPaletteCalibrator(EXACT_PALETTE_MODEL).calibrate(referenceSamples);
    }

    /**
     * Maps one ARGB color to the exact rendered palette.
     *
     * @param argb ARGB color
     * @return palette index, or empty when the color is not an exact palette member
     */
    public OptionalInt exactPaletteIndex(int argb) {
        int index = PALETTE.indexOf(argb);
        return index < 0 ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /**
     * Maps one ARGB color to an exact color center in the supplied palette model.
     *
     * @param argb ARGB color
     * @param paletteModel exact or calibrated palette model
     * @return palette index, or empty when the color is not an exact model color center
     */
    public OptionalInt paletteIndex(int argb, CaptureMediaPaletteModel paletteModel) {
        Objects.requireNonNull(paletteModel, "paletteModel must not be null");
        for (CaptureMediaPaletteModelColor color : paletteModel.colors()) {
            if (color.modelArgb() == argb) {
                return OptionalInt.of(color.paletteIndex());
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Samples one normalized frame pixel using exact palette matching only.
     *
     * @param frame normalized frame
     * @param row zero-based row
     * @param col zero-based column
     * @return exact palette index, or empty when the pixel is off-palette
     */
    public OptionalInt sampleExactPaletteIndex(NormalizedCaptureFrame frame, int row, int col) {
        Objects.requireNonNull(frame, "frame must not be null");
        return exactPaletteIndex(frame.argbPixelAt(row, col));
    }

    /**
     * Maps one ARGB color to the nearest rendered palette color when it is inside configured tolerance.
     *
     * @param argb ARGB color
     * @return structured palette sample with confidence and diagnostic status
     */
    public CaptureMediaPaletteSample tolerantPaletteSample(int argb) {
        return tolerantPaletteSample(argb, EXACT_PALETTE_MODEL);
    }

    /**
     * Maps one ARGB color to the nearest color in an explicit palette model when it is inside configured tolerance.
     *
     * @param argb ARGB color
     * @param paletteModel exact or calibrated palette model
     * @return structured palette sample with confidence and diagnostic status
     */
    public CaptureMediaPaletteSample tolerantPaletteSample(int argb, CaptureMediaPaletteModel paletteModel) {
        Objects.requireNonNull(paletteModel, "paletteModel must not be null");
        OptionalInt exactIndex = paletteIndex(argb, paletteModel);
        if (exactIndex.isPresent()) {
            int paletteIndex = exactIndex.getAsInt();
            CaptureMediaPaletteModelColor color = paletteModel.color(paletteIndex);
            return new CaptureMediaPaletteSample(
                    argb,
                    paletteIndex,
                    color.expectedArgb(),
                    0.0d,
                    0.0d,
                    1.0d,
                    CaptureMediaPaletteSampleStatus.EXACT
            );
        }

        NearestPaletteColor nearest = nearestPaletteColor(argb, paletteModel);
        CaptureMediaPaletteSampleStatus status = statusForDistance(nearest.rgbDistance());
        return new CaptureMediaPaletteSample(
                argb,
                nearest.paletteIndex(),
                nearest.paletteArgb(),
                nearest.rgbDistance(),
                nearest.rgbDistance() / MAX_RGB_DISTANCE,
                confidence(nearest.rgbDistance()),
                status
        );
    }

    /**
     * Samples one normalized frame pixel using bounded nearest-palette matching.
     *
     * @param frame normalized frame
     * @param row zero-based row
     * @param col zero-based column
     * @return structured palette sample with confidence and diagnostic status
     */
    public CaptureMediaPaletteSample sampleTolerantPalette(NormalizedCaptureFrame frame, int row, int col) {
        Objects.requireNonNull(frame, "frame must not be null");
        return tolerantPaletteSample(frame.argbPixelAt(row, col));
    }

    /**
     * Samples one normalized frame pixel using bounded nearest-palette matching against an explicit model.
     *
     * @param frame normalized frame
     * @param row zero-based row
     * @param col zero-based column
     * @param paletteModel exact or calibrated palette model
     * @return structured palette sample with confidence and diagnostic status
     */
    public CaptureMediaPaletteSample sampleTolerantPalette(
            NormalizedCaptureFrame frame,
            int row,
            int col,
            CaptureMediaPaletteModel paletteModel
    ) {
        Objects.requireNonNull(frame, "frame must not be null");
        return tolerantPaletteSample(frame.argbPixelAt(row, col), paletteModel);
    }

    /**
     * Samples a bounded square area, reduces it to a median RGB color, then maps that color to the rendered palette.
     *
     * <p>This keeps palette ownership in the palette sampler while allowing capture-media callers to use local
     * multi-point sampling when CV evidence indicates a noisy module center.</p>
     *
     * @param frame normalized frame
     * @param centerRow center row in normalized-frame coordinates
     * @param centerCol center column in normalized-frame coordinates
     * @param radiusPx non-negative sampling radius in pixels
     * @return structured palette sample for the median area color
     */
    public CaptureMediaPaletteSample sampleTolerantPaletteArea(
            NormalizedCaptureFrame frame,
            int centerRow,
            int centerCol,
            int radiusPx
    ) {
        return sampleTolerantPaletteArea(frame, centerRow, centerCol, radiusPx, EXACT_PALETTE_MODEL);
    }

    /**
     * Samples a bounded square area, reduces it to a median RGB color, then maps that color with an explicit model.
     *
     * @param frame normalized frame
     * @param centerRow center row in normalized-frame coordinates
     * @param centerCol center column in normalized-frame coordinates
     * @param radiusPx non-negative sampling radius in pixels
     * @param paletteModel exact or calibrated palette model
     * @return structured palette sample for the median area color
     */
    public CaptureMediaPaletteSample sampleTolerantPaletteArea(
            NormalizedCaptureFrame frame,
            int centerRow,
            int centerCol,
            int radiusPx,
            CaptureMediaPaletteModel paletteModel
    ) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(paletteModel, "paletteModel must not be null");
        if (radiusPx < 0) {
            throw new IllegalArgumentException("radiusPx must be non-negative");
        }
        if (radiusPx == 0) {
            return sampleTolerantPalette(frame, centerRow, centerCol, paletteModel);
        }

        int top = Math.max(0, centerRow - radiusPx);
        int bottom = Math.min(frame.normalizedHeightPixels() - 1, centerRow + radiusPx);
        int left = Math.max(0, centerCol - radiusPx);
        int right = Math.min(frame.normalizedWidthPixels() - 1, centerCol + radiusPx);
        int sampleCount = (bottom - top + 1) * (right - left + 1);
        int[] redValues = new int[sampleCount];
        int[] greenValues = new int[sampleCount];
        int[] blueValues = new int[sampleCount];
        int index = 0;
        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) {
                int argb = frame.argbPixelAt(row, col);
                redValues[index] = red(argb);
                greenValues[index] = green(argb);
                blueValues[index] = blue(argb);
                index++;
            }
        }

        int medianArgb = 0xFF000000
                | (median(redValues) << 16)
                | (median(greenValues) << 8)
                | median(blueValues);
        return tolerantPaletteSample(medianArgb, paletteModel);
    }

    /**
     * Emits a source-scoped color/compression diagnostic for low-confidence or rejected samples.
     *
     * @param frame normalized source frame
     * @param sample palette sample
     * @return diagnostic when the sample is low-confidence or rejected
     */
    public Optional<CaptureMediaDiagnostic> diagnosticFor(
            NormalizedCaptureFrame frame,
            CaptureMediaPaletteSample sample
    ) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(sample, "sample must not be null");
        if (!sample.diagnostic()) {
            return Optional.empty();
        }
        CaptureMediaDiagnosticSeverity severity = sample.accepted()
                ? CaptureMediaDiagnosticSeverity.WARNING
                : CaptureMediaDiagnosticSeverity.ERROR;
        return Optional.of(new CaptureMediaDiagnostic(
                CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                severity,
                severity.blocksRestore(),
                Optional.of(frame.sourceKind()),
                Optional.of(frame.sourceId()),
                Optional.of(frame.callerOrder()),
                Optional.empty(),
                Optional.empty(),
                Map.of(
                        "colorDistanceScore", sample.colorDistanceScore(),
                        "paletteConfidence", sample.confidence(),
                        "nearestPaletteIndex", (double) sample.paletteIndex()
                ),
                sample.accepted()
                        ? "Media palette sample was accepted with low color confidence"
                        : "Media palette sample is outside the supported color/compression threshold"
        ));
    }

    private NearestPaletteColor nearestPaletteColor(int argb, CaptureMediaPaletteModel paletteModel) {
        int nearestIndex = 0;
        int nearestArgb = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (CaptureMediaPaletteModelColor color : paletteModel.colors()) {
            double distance = rgbDistance(argb, color.modelArgb());
            if (distance < nearestDistance) {
                nearestIndex = color.paletteIndex();
                nearestArgb = color.expectedArgb();
                nearestDistance = distance;
            }
        }
        return new NearestPaletteColor(nearestIndex, nearestArgb, nearestDistance);
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

    private CaptureMediaPaletteSampleStatus statusForDistance(double rgbDistance) {
        if (rgbDistance > MAX_ACCEPTED_RGB_DISTANCE) {
            return CaptureMediaPaletteSampleStatus.REJECTED;
        }
        if (rgbDistance > LOW_CONFIDENCE_RGB_DISTANCE) {
            return CaptureMediaPaletteSampleStatus.LOW_CONFIDENCE;
        }
        return CaptureMediaPaletteSampleStatus.TOLERANT;
    }

    private double confidence(double rgbDistance) {
        if (rgbDistance > MAX_ACCEPTED_RGB_DISTANCE) {
            return 0.0d;
        }
        return 1.0d - (rgbDistance / MAX_ACCEPTED_RGB_DISTANCE);
    }

    private int median(int[] values) {
        int[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private record NearestPaletteColor(int paletteIndex, int paletteArgb, double rgbDistance) {
    }
}
