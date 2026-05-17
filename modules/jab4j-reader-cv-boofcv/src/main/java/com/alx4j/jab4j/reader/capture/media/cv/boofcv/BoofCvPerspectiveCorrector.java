package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import boofcv.abst.distort.FDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.geo.h.HomographyDirectLinearTransform;
import boofcv.alg.interpolate.InterpolationType;
import boofcv.core.image.GConvertImage;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.homography.UtilHomography_F64;
import org.ejml.data.DMatrixRMaj;

/**
 * Applies BoofCV homography correction from source-space frame corners into a rendered layout profile.
 */
final class BoofCvPerspectiveCorrector {

    private static final String EVIDENCE_BACKEND_ID = new String(new char[] { 'b', 'o', 'o', 'f', 'c', 'v' });
    private static final int MAX_PHASE_OFFSET_PX = 8;
    private static final int MAX_FEASIBLE_PHASE_OFFSET_PX = 4;
    private static final int MAX_PHASE_SAMPLES = 96;

    /**
     * Corrects a source-space quadrilateral into the selected layout profile.
     *
     * @param frame decoded source frame
     * @param layoutProfile selected rendered layout profile
     * @param frameCorners detected source-space frame corners
     * @param qualityMetrics deterministic quality metrics to preserve with the corrected frame
     * @return BoofCV-corrected normalized frame candidate
     */
    CvNormalizedFrame correct(
            MediaInputFrame frame,
            LayoutProfile layoutProfile,
            FrameCorners frameCorners,
            CaptureMediaQualityMetrics qualityMetrics
    ) {
        CorrectedImage correctedImage = correctedImage(frame, layoutProfile, frameCorners);
        return new CvNormalizedFrame(
                layoutProfile,
                frameCorners,
                qualityMetrics,
                correctedImage.argbPixels()
        );
    }

    /**
     * Corrects an accepted source-space candidate and attaches BoofCV-measured grid-phase evidence when available.
     *
     * @param frame decoded source frame
     * @param candidate accepted source-space candidate
     * @param layoutPlan fixed layout plan for the candidate profile
     * @return BoofCV-corrected normalized frame candidate with optional sampling evidence
     */
    CvNormalizedFrame correct(MediaInputFrame frame, CvFrameCandidate candidate, FixedLayoutPlan layoutPlan) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(layoutPlan, "layoutPlan must not be null");
        CaptureMediaQualityMetrics qualityMetrics = CaptureMediaQualityMetrics.perspectiveCorrected(
                candidate.score().frameCoverageRatio(),
                candidate.score().skewScore()
        );
        CorrectedImage correctedImage = correctedImage(frame, candidate.layoutProfile(), candidate.frameCorners());
        Optional<CvSamplingEvidence> samplingEvidence = samplingEvidence(
                correctedImage,
                layoutPlan,
                candidate
        );
        return new CvNormalizedFrame(
                candidate.layoutProfile(),
                candidate.frameCorners(),
                qualityMetrics,
                correctedImage.argbPixels(),
                samplingEvidence,
                candidate.geometrySource(),
                candidate.sourceRegionRank(),
                candidate.profileAlternativeRank(),
                candidate.profileAlternativeCount()
        );
    }

    /**
     * Corrects a source-space quadrilateral into selected layout dimensions using BoofCV nearest-neighbor sampling.
     *
     * @param frame decoded source frame
     * @param layoutProfile selected rendered layout profile
     * @param frameCorners detected source-space frame corners
     * @return row-major corrected ARGB pixels
     */
    int[] correctArgb(MediaInputFrame frame, LayoutProfile layoutProfile, FrameCorners frameCorners) {
        return correctedImage(frame, layoutProfile, frameCorners).argbPixels();
    }

    private CorrectedImage correctedImage(MediaInputFrame frame, LayoutProfile layoutProfile, FrameCorners frameCorners) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        Objects.requireNonNull(frameCorners, "frameCorners must not be null");
        if (layoutProfile.frameWidthPx() < 2 || layoutProfile.frameHeightPx() < 2) {
            throw new IllegalArgumentException("layout profile dimensions must be at least 2x2");
        }

        BoofCvArgbToPlanarRgbConverter.ConvertedArgbImage source =
                BoofCvArgbToPlanarRgbConverter.convert(frame);
        Planar<GrayU8> corrected = new Planar<>(
                GrayU8.class,
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                3
        );
        new FDistort(source.image(), corrected)
                .interp(InterpolationType.NEAREST_NEIGHBOR)
                .borderExt()
                .transform(new PixelTransformHomography_F32(outputToSourceHomography(layoutProfile, frameCorners)))
                .apply();
        return new CorrectedImage(corrected, toArgbPixels(corrected));
    }

    private Optional<CvSamplingEvidence> samplingEvidence(
            CorrectedImage correctedImage,
            FixedLayoutPlan layoutPlan,
            CvFrameCandidate candidate
    ) {
        LayoutProfile profile = layoutPlan.profile();
        if (correctedImage.rgb().width != profile.frameWidthPx()
                || correctedImage.rgb().height != profile.frameHeightPx()) {
            return Optional.empty();
        }
        GrayU8 gray = new GrayU8(correctedImage.rgb().width, correctedImage.rgb().height);
        GConvertImage.average(correctedImage.rgb(), gray);

        int cellWidth = Math.max(8, layoutPlan.separatorThicknessPx() * 2);
        int left = profile.outerMarginPx();
        int rightExclusive = profile.frameWidthPx() - profile.outerMarginPx();
        if (left >= rightExclusive) {
            return Optional.empty();
        }
        int centerY = clamp(
                profile.outerMarginPx() + (profile.topSyncBandPx() / 2),
                0,
                gray.height - 1
        );
        int cellCount = Math.max(1, (rightExclusive - left) / cellWidth);
        PhaseEstimate phase = estimateSyncPhase(gray, left, centerY, cellWidth, cellCount);
        ReferenceEstimate references = estimateReferences(
                correctedImage,
                gray,
                left,
                centerY,
                cellWidth,
                cellCount,
                phase
        );
        if (references.sampleCount() == 0) {
            return Optional.empty();
        }
        if (Math.abs(phase.offsetPx()) > MAX_FEASIBLE_PHASE_OFFSET_PX) {
            return Optional.empty();
        }

        double confidence = clampScore((0.70d * phase.score()) + (0.30d * references.localContrastScore()));
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("boofCvGridPhaseOffsetXPx", phase.offsetPx());
        metrics.put("boofCvGridPhaseOffsetYPx", 0.0d);
        metrics.put("boofCvModuleCenterOffsetXPx", phase.offsetPx());
        metrics.put("boofCvModuleCenterOffsetYPx", 0.0d);
        metrics.put("boofCvLocalContrastScore", references.localContrastScore());
        metrics.put("boofCvSamplingEvidenceConfidence", confidence);
        metrics.put("boofCvSyncSampleCount", (double) references.sampleCount());
        candidate.geometrySource()
                .flatMap(BoofCvPerspectiveCorrector::geometrySourceCode)
                .ifPresent(code -> metrics.put("boofCvGeometrySourceCode", code));
        metrics.put("boofCvSourceRegionRank", (double) candidate.sourceRegionRank());
        metrics.put("boofCvProfileAlternativeRank", (double) candidate.profileAlternativeRank());
        CvGridPhase gridPhase = new CvGridPhase(
                phase.offsetPx(),
                0.0d,
                confidence,
                references.localContrastScore(),
                OptionalInt.of(references.whiteArgb()),
                OptionalInt.of(references.blackArgb())
        );
        return Optional.of(new CvSamplingEvidence(
                EVIDENCE_BACKEND_ID,
                Optional.of(gridPhase),
                List.of(),
                metrics
        ));
    }

    private PhaseEstimate estimateSyncPhase(
            GrayU8 gray,
            int left,
            int centerY,
            int cellWidth,
            int cellCount
    ) {
        int searchRadius = Math.min(MAX_PHASE_OFFSET_PX, Math.max(1, cellWidth / 2));
        int bestOffset = 0;
        double bestScore = -1.0d;
        for (int offset = -searchRadius; offset <= searchRadius; offset++) {
            double score = syncScore(gray, left, centerY, cellWidth, cellCount, offset);
            if (score > bestScore || (same(score, bestScore) && Math.abs(offset) < Math.abs(bestOffset))) {
                bestOffset = offset;
                bestScore = score;
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
        int step = Math.max(1, cellCount / MAX_PHASE_SAMPLES);
        double totalScore = 0.0d;
        int samples = 0;
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex += step) {
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
            CorrectedImage correctedImage,
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
        int step = Math.max(1, cellCount / MAX_PHASE_SAMPLES);
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex += step) {
            int x = clamp(
                    left + (cellIndex * cellWidth) + (cellWidth / 2) + (int) Math.round(phase.offsetPx()),
                    0,
                    gray.width - 1
            );
            int luminance = gray.get(x, centerY);
            int argb = correctedImage.argbPixelAt(centerY, x);
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

    private Homography2D_F64 outputToSourceHomography(LayoutProfile layoutProfile, FrameCorners frameCorners) {
        double right = layoutProfile.frameWidthPx() - 1.0d;
        double bottom = layoutProfile.frameHeightPx() - 1.0d;
        List<AssociatedPair> pairs = List.of(
                new AssociatedPair(0.0d, 0.0d, frameCorners.topLeftX(), frameCorners.topLeftY()),
                new AssociatedPair(right, 0.0d, frameCorners.topRightX(), frameCorners.topRightY()),
                new AssociatedPair(right, bottom, frameCorners.bottomRightX(), frameCorners.bottomRightY()),
                new AssociatedPair(0.0d, bottom, frameCorners.bottomLeftX(), frameCorners.bottomLeftY())
        );
        DMatrixRMaj matrix = new DMatrixRMaj(3, 3);
        if (!new HomographyDirectLinearTransform(true).process(pairs, matrix)) {
            throw new IllegalArgumentException("perspective corners must produce a valid BoofCV homography");
        }
        return UtilHomography_F64.convert(matrix, null);
    }

    private int[] toArgbPixels(Planar<GrayU8> image) {
        int[] pixels = new int[image.width * image.height];
        GrayU8 red = image.getBand(0);
        GrayU8 green = image.getBand(1);
        GrayU8 blue = image.getBand(2);
        for (int row = 0; row < image.height; row++) {
            for (int col = 0; col < image.width; col++) {
                pixels[(row * image.width) + col] = 0xFF000000
                        | (red.get(col, row) << 16)
                        | (green.get(col, row) << 8)
                        | blue.get(col, row);
            }
        }
        return pixels;
    }

    private static Optional<Double> geometrySourceCode(String geometrySource) {
        if (BoofCvCandidateRegionProposer.GeometrySource.BOOFCV_FITTED_QUADRILATERAL.sidecarValue()
                .equals(geometrySource)) {
            return Optional.of(BoofCvCandidateRegionProposer.GeometrySource.BOOFCV_FITTED_QUADRILATERAL.metricCode());
        }
        if (BoofCvCandidateRegionProposer.GeometrySource.BOOFCV_REDUCED_FITTED_QUADRILATERAL.sidecarValue()
                .equals(geometrySource)) {
            return Optional.of(
                    BoofCvCandidateRegionProposer.GeometrySource.BOOFCV_REDUCED_FITTED_QUADRILATERAL.metricCode()
            );
        }
        if (BoofCvCandidateRegionProposer.GeometrySource.CONTOUR_EXTREMA.sidecarValue().equals(geometrySource)) {
            return Optional.of(BoofCvCandidateRegionProposer.GeometrySource.CONTOUR_EXTREMA.metricCode());
        }
        return Optional.empty();
    }

    private boolean same(double first, double second) {
        return Math.abs(first - second) < 0.000001d;
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

    private int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private int blue(int argb) {
        return argb & 0xFF;
    }

    private record CorrectedImage(Planar<GrayU8> rgb, int[] argbPixels) {

        private int argbPixelAt(int row, int col) {
            return argbPixels[(row * rgb.width) + col];
        }
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
