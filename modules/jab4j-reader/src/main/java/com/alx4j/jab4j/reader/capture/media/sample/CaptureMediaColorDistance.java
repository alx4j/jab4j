package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.Objects;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteColorMethod;

/**
 * Color-distance helpers for capture-media palette classification.
 */
final class CaptureMediaColorDistance {

    private static final double XN = 0.95047d;
    private static final double YN = 1.00000d;
    private static final double ZN = 1.08883d;

    private CaptureMediaColorDistance() {
    }

    static double distance(int firstArgb, int secondArgb, ObservedPaletteColorMethod method) {
        Objects.requireNonNull(method, "method must not be null");
        return switch (method) {
            case SRGB_EUCLIDEAN_V1 -> srgbDistance(firstArgb, secondArgb);
            case LINEAR_RGB_V1 -> linearRgbDistance(firstArgb, secondArgb);
            case CIE_LAB_V1 -> labDistance(firstArgb, secondArgb);
        };
    }

    private static double srgbDistance(int firstArgb, int secondArgb) {
        int redDelta = red(firstArgb) - red(secondArgb);
        int greenDelta = green(firstArgb) - green(secondArgb);
        int blueDelta = blue(firstArgb) - blue(secondArgb);
        return Math.sqrt((redDelta * redDelta) + (greenDelta * greenDelta) + (blueDelta * blueDelta));
    }

    private static double linearRgbDistance(int firstArgb, int secondArgb) {
        double redDelta = linear(red(firstArgb)) - linear(red(secondArgb));
        double greenDelta = linear(green(firstArgb)) - linear(green(secondArgb));
        double blueDelta = linear(blue(firstArgb)) - linear(blue(secondArgb));
        return Math.sqrt((redDelta * redDelta) + (greenDelta * greenDelta) + (blueDelta * blueDelta));
    }

    private static double labDistance(int firstArgb, int secondArgb) {
        double[] firstLab = lab(firstArgb);
        double[] secondLab = lab(secondArgb);
        double lDelta = firstLab[0] - secondLab[0];
        double aDelta = firstLab[1] - secondLab[1];
        double bDelta = firstLab[2] - secondLab[2];
        return Math.sqrt((lDelta * lDelta) + (aDelta * aDelta) + (bDelta * bDelta));
    }

    private static double[] lab(int argb) {
        double r = linear(red(argb));
        double g = linear(green(argb));
        double b = linear(blue(argb));

        double x = (0.4124564d * r) + (0.3575761d * g) + (0.1804375d * b);
        double y = (0.2126729d * r) + (0.7151522d * g) + (0.0721750d * b);
        double z = (0.0193339d * r) + (0.1191920d * g) + (0.9503041d * b);

        double fx = labPivot(x / XN);
        double fy = labPivot(y / YN);
        double fz = labPivot(z / ZN);
        return new double[] {
                (116.0d * fy) - 16.0d,
                500.0d * (fx - fy),
                200.0d * (fy - fz)
        };
    }

    private static double labPivot(double value) {
        return value > 0.008856d ? Math.cbrt(value) : (7.787d * value) + (16.0d / 116.0d);
    }

    private static double linear(int value) {
        double channel = value / 255.0d;
        return channel <= 0.04045d
                ? channel / 12.92d
                : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }
}
