package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidenceProvider;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import boofcv.core.image.GConvertImage;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;

/**
 * Test-scoped BoofCV estimator for normalized-frame grid phase and local black/white sampling evidence.
 */
final class BoofCvSamplingEvidenceEstimator implements CvSamplingEvidenceProvider {

    /**
     * Estimates grid-phase and local sampling evidence from the normalized sync band.
     *
     * @param frame normalized capture frame
     * @param layoutPlan fixed layout plan matched to the frame
     * @return sampling evidence when the sync band has measurable local contrast
     */
    @Override
    public Optional<CvSamplingEvidence> evidenceFor(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(layoutPlan, "layoutPlan must not be null");
        Planar<GrayU8> rgb = toBoofRgb(frame);
        GrayU8 gray = new GrayU8(frame.normalizedWidthPixels(), frame.normalizedHeightPixels());
        GConvertImage.average(rgb, gray);

        LayoutProfile profile = layoutPlan.profile();
        int cellWidth = Math.max(8, layoutPlan.separatorThicknessPx() * 2);
        int left = profile.outerMarginPx();
        int rightExclusive = profile.frameWidthPx() - profile.outerMarginPx();
        int centerY = clamp(
                profile.outerMarginPx() + (profile.topSyncBandPx() / 2),
                0,
                frame.normalizedHeightPixels() - 1
        );
        int cellCount = Math.max(1, (rightExclusive - left) / cellWidth);
        PhaseEstimate phase = estimateSyncPhase(gray, left, centerY, cellWidth, cellCount);
        ReferenceEstimate references = estimateReferences(frame, gray, left, centerY, cellWidth, cellCount, phase);
        if (references.localContrastScore() <= 0.0d) {
            return Optional.empty();
        }

        double confidence = clampScore((0.70d * phase.score()) + (0.30d * references.localContrastScore()));
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("boofCvGridPhaseOffsetXPx", phase.offsetPx());
        metrics.put("boofCvGridPhaseOffsetYPx", 0.0d);
        metrics.put("boofCvModuleCenterOffsetXPx", 0.0d);
        metrics.put("boofCvModuleCenterOffsetYPx", 0.0d);
        metrics.put("boofCvLocalContrastScore", references.localContrastScore());
        metrics.put("boofCvSamplingEvidenceConfidence", confidence);
        metrics.put("boofCvSyncSampleCount", (double) references.sampleCount());
        CvGridPhase gridPhase = new CvGridPhase(
                phase.offsetPx(),
                0.0d,
                confidence,
                references.localContrastScore(),
                OptionalInt.of(references.whiteArgb()),
                OptionalInt.of(references.blackArgb())
        );
        return Optional.of(new CvSamplingEvidence("boofcv", Optional.of(gridPhase), List.of(), metrics));
    }

    private Planar<GrayU8> toBoofRgb(NormalizedCaptureFrame frame) {
        Planar<GrayU8> rgb = new Planar<>(
                GrayU8.class,
                frame.normalizedWidthPixels(),
                frame.normalizedHeightPixels(),
                3
        );
        for (int row = 0; row < frame.normalizedHeightPixels(); row++) {
            for (int col = 0; col < frame.normalizedWidthPixels(); col++) {
                int argb = frame.argbPixelAt(row, col);
                rgb.getBand(0).set(col, row, red(argb));
                rgb.getBand(1).set(col, row, green(argb));
                rgb.getBand(2).set(col, row, blue(argb));
            }
        }
        return rgb;
    }

    private PhaseEstimate estimateSyncPhase(
            GrayU8 gray,
            int left,
            int centerY,
            int cellWidth,
            int cellCount
    ) {
        int searchRadius = Math.max(1, cellWidth / 2);
        int bestOffset = 0;
        double bestScore = -1.0d;
        for (int offset = -searchRadius; offset <= searchRadius; offset++) {
            double score = syncScore(gray, left, centerY, cellWidth, cellCount, offset);
            if (score > bestScore) {
                bestScore = score;
                bestOffset = offset;
            }
        }
        return new PhaseEstimate(bestOffset, clampScore(bestScore));
    }

    private double syncScore(
            GrayU8 gray,
            int left,
            int centerY,
            int cellWidth,
            int cellCount,
            int offset
    ) {
        double totalScore = 0.0d;
        int samples = 0;
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
            int x = clamp(left + (cellIndex * cellWidth) + (cellWidth / 2) + offset, 0, gray.width - 1);
            int luminance = gray.get(x, centerY);
            totalScore += cellIndex % 2 == 0
                    ? luminance / 255.0d
                    : (255 - luminance) / 255.0d;
            samples++;
        }
        return samples == 0 ? 0.0d : totalScore / samples;
    }

    private ReferenceEstimate estimateReferences(
            NormalizedCaptureFrame frame,
            GrayU8 gray,
            int left,
            int centerY,
            int cellWidth,
            int cellCount,
            PhaseEstimate phase
    ) {
        ChannelAccumulator black = new ChannelAccumulator();
        ChannelAccumulator white = new ChannelAccumulator();
        int minimumLuminance = 255;
        int maximumLuminance = 0;
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
            int x = clamp(
                    left + (cellIndex * cellWidth) + (cellWidth / 2) + (int) Math.round(phase.offsetPx()),
                    0,
                    frame.normalizedWidthPixels() - 1
            );
            int luminance = gray.get(x, centerY);
            int argb = frame.argbPixelAt(centerY, x);
            if (cellIndex % 2 == 0) {
                white.add(argb);
            } else {
                black.add(argb);
            }
            minimumLuminance = Math.min(minimumLuminance, luminance);
            maximumLuminance = Math.max(maximumLuminance, luminance);
        }
        double contrast = clampScore((maximumLuminance - minimumLuminance) / 255.0d);
        return new ReferenceEstimate(
                black.averageArgb(0xFF000000),
                white.averageArgb(0xFFFFFFFF),
                contrast,
                black.sampleCount() + white.sampleCount()
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

    private double clampScore(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PhaseEstimate(double offsetPx, double score) {
    }

    private record ReferenceEstimate(
            int blackArgb,
            int whiteArgb,
            double localContrastScore,
            int sampleCount
    ) {
    }

    private final class ChannelAccumulator {

        private int sampleCount;
        private int redTotal;
        private int greenTotal;
        private int blueTotal;

        private void add(int argb) {
            redTotal += red(argb);
            greenTotal += green(argb);
            blueTotal += blue(argb);
            sampleCount++;
        }

        private int sampleCount() {
            return sampleCount;
        }

        private int averageArgb(int defaultArgb) {
            if (sampleCount == 0) {
                return defaultArgb;
            }
            int red = redTotal / sampleCount;
            int green = greenTotal / sampleCount;
            int blue = blueTotal / sampleCount;
            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
    }
}
