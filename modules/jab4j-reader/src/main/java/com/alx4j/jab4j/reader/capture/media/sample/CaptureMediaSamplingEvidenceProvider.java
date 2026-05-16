package com.alx4j.jab4j.reader.capture.media.sample;

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
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;

/**
 * Estimates backend-neutral sampler evidence from reader-owned normalized ARGB pixels and fixed layout geometry.
 *
 * <p>The provider intentionally ignores exact rendered frames and clean axis-aligned crops. It returns evidence only for
 * camera-derived normalized candidates with enough sync-band contrast to support bounded sampler phase evidence.</p>
 */
public final class CaptureMediaSamplingEvidenceProvider implements CvSamplingEvidenceProvider {

    private static final String BACKEND_ID = "reader-normalized-argb";
    private static final double CAMERA_FRAME_COVERAGE_LIMIT = 0.99d;
    private static final double MIN_EVIDENCE_CONFIDENCE = 0.55d;
    private static final int MAX_PHASE_OFFSET_PX = 8;

    /**
     * Estimates sync-band grid phase and local black/white references for one camera-derived normalized frame.
     *
     * @param frame normalized capture frame
     * @param layoutPlan fixed layout plan matched to the frame
     * @return sampling evidence, or empty when the frame is clean, unsupported, or low confidence
     */
    @Override
    public Optional<CvSamplingEvidence> evidenceFor(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(layoutPlan, "layoutPlan must not be null");
        if (!supportedLayout(frame, layoutPlan) || !cameraDerived(frame)) {
            return Optional.empty();
        }

        LayoutProfile profile = layoutPlan.profile();
        int cellWidth = Math.max(8, layoutPlan.separatorThicknessPx() * 2);
        int left = profile.outerMarginPx();
        int rightExclusive = profile.frameWidthPx() - profile.outerMarginPx();
        if (left >= rightExclusive) {
            return Optional.empty();
        }

        int centerY = clamp(
                profile.outerMarginPx() + (profile.topSyncBandPx() / 2),
                0,
                frame.normalizedHeightPixels() - 1
        );
        int cellCount = Math.max(1, (rightExclusive - left) / cellWidth);
        PhaseEstimate phase = estimateSyncPhase(frame, left, centerY, cellWidth, cellCount);
        ReferenceEstimate references = estimateReferences(frame, left, centerY, cellWidth, cellCount, phase);
        double confidence = clampScore((0.70d * phase.score()) + (0.30d * references.localContrastScore()));
        if (confidence < MIN_EVIDENCE_CONFIDENCE) {
            return Optional.empty();
        }

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("readerGridPhaseOffsetXPx", phase.offsetPx());
        metrics.put("readerGridPhaseOffsetYPx", 0.0d);
        metrics.put("readerModuleCenterOffsetXPx", phase.offsetPx());
        metrics.put("readerModuleCenterOffsetYPx", 0.0d);
        metrics.put("readerLocalContrastScore", references.localContrastScore());
        metrics.put("readerSamplingEvidenceConfidence", confidence);
        metrics.put("readerSyncSampleCount", (double) references.sampleCount());
        CvGridPhase gridPhase = new CvGridPhase(
                phase.offsetPx(),
                0.0d,
                confidence,
                references.localContrastScore(),
                OptionalInt.of(references.whiteArgb()),
                OptionalInt.of(references.blackArgb())
        );
        return Optional.of(new CvSamplingEvidence(BACKEND_ID, Optional.of(gridPhase), List.of(), metrics));
    }

    private boolean supportedLayout(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan) {
        LayoutProfile profile = layoutPlan.profile();
        return frame.normalizedWidthPixels() == profile.frameWidthPx()
                && frame.normalizedHeightPixels() == profile.frameHeightPx()
                && frame.layoutProfileId().equals(profile.profileId());
    }

    private boolean cameraDerived(NormalizedCaptureFrame frame) {
        CaptureMediaQualityMetrics metrics = frame.qualityMetrics();
        return metrics.measured(metrics.frameCoverageRatio())
                && metrics.frameCoverageRatio() < CAMERA_FRAME_COVERAGE_LIMIT
                && !cleanAxisAlignedFrame(frame);
    }

    private boolean cleanAxisAlignedFrame(NormalizedCaptureFrame frame) {
        FrameCorners corners = frame.frameCorners();
        return same(corners.topLeftY(), corners.topRightY())
                && same(corners.bottomLeftY(), corners.bottomRightY())
                && same(corners.topLeftX(), corners.bottomLeftX())
                && same(corners.topRightX(), corners.bottomRightX())
                && same(corners.topRightX() - corners.topLeftX(), frame.normalizedWidthPixels())
                && same(corners.bottomRightY() - corners.topRightY(), frame.normalizedHeightPixels());
    }

    private PhaseEstimate estimateSyncPhase(
            NormalizedCaptureFrame frame,
            int left,
            int centerY,
            int cellWidth,
            int cellCount
    ) {
        int searchRadius = Math.min(MAX_PHASE_OFFSET_PX, Math.max(1, cellWidth / 2));
        int bestOffset = 0;
        double bestScore = -1.0d;
        for (int offset = -searchRadius; offset <= searchRadius; offset++) {
            double score = syncScore(frame, left, centerY, cellWidth, cellCount, offset);
            if (score > bestScore || (same(score, bestScore) && Math.abs(offset) < Math.abs(bestOffset))) {
                bestOffset = offset;
                bestScore = score;
            }
        }
        return new PhaseEstimate(bestOffset, clampScore(bestScore));
    }

    private double syncScore(
            NormalizedCaptureFrame frame,
            int left,
            int centerY,
            int cellWidth,
            int cellCount,
            int offset
    ) {
        double totalScore = 0.0d;
        int samples = 0;
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
            int x = clamp(
                    left + (cellIndex * cellWidth) + (cellWidth / 2) + offset,
                    0,
                    frame.normalizedWidthPixels() - 1
            );
            int luminance = luminance(frame.argbPixelAt(centerY, x));
            totalScore += cellIndex % 2 == 0
                    ? luminance / 255.0d
                    : (255 - luminance) / 255.0d;
            samples++;
        }
        return samples == 0 ? 0.0d : totalScore / samples;
    }

    private ReferenceEstimate estimateReferences(
            NormalizedCaptureFrame frame,
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
        int roundedOffset = (int) Math.round(phase.offsetPx());
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
            int x = clamp(
                    left + (cellIndex * cellWidth) + (cellWidth / 2) + roundedOffset,
                    0,
                    frame.normalizedWidthPixels() - 1
            );
            int argb = frame.argbPixelAt(centerY, x);
            int luminance = luminance(argb);
            if (cellIndex % 2 == 0) {
                white.add(argb);
            } else {
                black.add(argb);
            }
            minimumLuminance = Math.min(minimumLuminance, luminance);
            maximumLuminance = Math.max(maximumLuminance, luminance);
        }
        return new ReferenceEstimate(
                black.averageArgb(0xFF000000),
                white.averageArgb(0xFFFFFFFF),
                clampScore((maximumLuminance - minimumLuminance) / 255.0d),
                black.sampleCount() + white.sampleCount()
        );
    }

    private boolean same(double first, double second) {
        return Math.abs(first - second) < 0.000001d;
    }

    private int luminance(int argb) {
        return (int) Math.round((0.2126d * red(argb)) + (0.7152d * green(argb)) + (0.0722d * blue(argb)));
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
