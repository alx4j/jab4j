package com.alx4j.jab4j.reader.capture.media.sample;

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

    /**
     * Returns a copy of the exact eight-color rendered palette.
     *
     * @return ARGB palette values
     */
    public List<Integer> paletteArgb() {
        return List.copyOf(PALETTE);
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
        OptionalInt exactIndex = exactPaletteIndex(argb);
        if (exactIndex.isPresent()) {
            int paletteIndex = exactIndex.getAsInt();
            return new CaptureMediaPaletteSample(
                    argb,
                    paletteIndex,
                    PALETTE.get(paletteIndex),
                    0.0d,
                    0.0d,
                    1.0d,
                    CaptureMediaPaletteSampleStatus.EXACT
            );
        }

        NearestPaletteColor nearest = nearestPaletteColor(argb);
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

    private NearestPaletteColor nearestPaletteColor(int argb) {
        int nearestIndex = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < PALETTE.size(); index++) {
            double distance = rgbDistance(argb, PALETTE.get(index));
            if (distance < nearestDistance) {
                nearestIndex = index;
                nearestDistance = distance;
            }
        }
        return new NearestPaletteColor(nearestIndex, PALETTE.get(nearestIndex), nearestDistance);
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

    private record NearestPaletteColor(int paletteIndex, int paletteArgb, double rgbDistance) {
    }
}
