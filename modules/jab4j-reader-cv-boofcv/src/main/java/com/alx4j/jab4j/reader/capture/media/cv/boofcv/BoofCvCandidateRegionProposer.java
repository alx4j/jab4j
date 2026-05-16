package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.core.image.GConvertImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;

/**
 * Proposes bounded source-space regions from BoofCV grayscale threshold and contour evidence.
 */
final class BoofCvCandidateRegionProposer {

    private static final int LIGHT_THRESHOLD = 178;
    private static final int MIN_CONTOUR_POINTS = 8;
    private static final int MIN_SOURCE_CANDIDATE_SHORT_EDGE_PX = 120;
    private static final int MAX_COMPONENTS_TO_RETURN = 64;

    /**
     * Uses BoofCV image primitives to propose deterministic source-space regions.
     *
     * @param frame decoded media input frame
     * @return proposed source regions and deterministic BoofCV metrics
     */
    ProposalResult propose(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        BoofCvArgbToPlanarRgbConverter.ConvertedArgbImage converted =
                BoofCvArgbToPlanarRgbConverter.convert(frame);
        BoofCvArgbToPlanarRgbConverter.ConversionMetadata conversion = converted.metadata();
        GrayU8 gray = new GrayU8(conversion.widthPixels(), conversion.heightPixels());
        GConvertImage.average(converted.image(), gray);
        GrayU8 binary = ThresholdImageOps.threshold(gray, null, LIGHT_THRESHOLD, false);
        List<Contour> contours = BinaryImageOps.contourExternal(binary, ConnectRule.EIGHT);
        List<CandidateRegion> regions = contours.stream()
                .map(contour -> toCandidateRegion(frame, contour))
                .flatMap(Optional::stream)
                .filter(region -> region.shortEdgePx() >= MIN_SOURCE_CANDIDATE_SHORT_EDGE_PX)
                .sorted(Comparator.comparingInt(CandidateRegion::areaPx).reversed())
                .limit(MAX_COMPONENTS_TO_RETURN)
                .toList();
        return new ProposalResult(conversion.widthPixels(), conversion.heightPixels(), contours.size(), regions);
    }

    private Optional<CandidateRegion> toCandidateRegion(MediaInputFrame frame, Contour contour) {
        if (contour.external == null || contour.external.size() < MIN_CONTOUR_POINTS) {
            return Optional.empty();
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        Point2D_I32 topLeft = null;
        Point2D_I32 topRight = null;
        Point2D_I32 bottomRight = null;
        Point2D_I32 bottomLeft = null;
        int topLeftScore = Integer.MAX_VALUE;
        int topRightScore = Integer.MIN_VALUE;
        int bottomRightScore = Integer.MIN_VALUE;
        int bottomLeftScore = Integer.MIN_VALUE;

        for (Point2D_I32 point : contour.external) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);

            int topLeftCandidate = point.x + point.y;
            if (topLeftCandidate < topLeftScore) {
                topLeftScore = topLeftCandidate;
                topLeft = point;
            }
            int topRightCandidate = point.x - point.y;
            if (topRightCandidate > topRightScore) {
                topRightScore = topRightCandidate;
                topRight = point;
            }
            int bottomRightCandidate = point.x + point.y;
            if (bottomRightCandidate > bottomRightScore) {
                bottomRightScore = bottomRightCandidate;
                bottomRight = point;
            }
            int bottomLeftCandidate = point.y - point.x;
            if (bottomLeftCandidate > bottomLeftScore) {
                bottomLeftScore = bottomLeftCandidate;
                bottomLeft = point;
            }
        }
        if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null) {
            return Optional.empty();
        }
        int left = clamp(minX, 0, frame.widthPixels() - 1);
        int top = clamp(minY, 0, frame.heightPixels() - 1);
        int rightExclusive = clamp(maxX + 1, left + 1, frame.widthPixels());
        int bottomExclusive = clamp(maxY + 1, top + 1, frame.heightPixels());
        return Optional.of(new CandidateRegion(
                left,
                top,
                rightExclusive,
                bottomExclusive,
                contour.external.size(),
                new FrameCorners(
                        topLeft.x,
                        topLeft.y,
                        topRight.x + 1.0d,
                        topRight.y,
                        bottomRight.x + 1.0d,
                        bottomRight.y + 1.0d,
                        bottomLeft.x,
                        bottomLeft.y + 1.0d
                )
        ));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * BoofCV proposal outcome for one decoded source frame.
     *
     * @param widthPixels source width
     * @param heightPixels source height
     * @param externalContourCount count of BoofCV external contours
     * @param regions bounded source-space candidate regions
     */
    record ProposalResult(
            int widthPixels,
            int heightPixels,
            int externalContourCount,
            List<CandidateRegion> regions
    ) {

        ProposalResult {
            if (widthPixels <= 0 || heightPixels <= 0) {
                throw new IllegalArgumentException("proposal dimensions must be positive");
            }
            regions = List.copyOf(Objects.requireNonNull(regions, "regions must not be null"));
        }

        /**
         * Returns deterministic BoofCV proposal metrics for diagnostics.
         *
         * @return proposal metrics
         */
        Map<String, Double> metrics() {
            Map<String, Double> metrics = new LinkedHashMap<>();
            metrics.put("boofCvInputWidthPixels", (double) widthPixels);
            metrics.put("boofCvInputHeightPixels", (double) heightPixels);
            metrics.put("boofCvConversionCopyCount", 1.0d);
            metrics.put("boofCvGrayscaleConversionCount", 1.0d);
            metrics.put("boofCvThreshold", (double) LIGHT_THRESHOLD);
            metrics.put("boofCvExternalContourCount", (double) externalContourCount);
            metrics.put("boofCvComponentCandidateCount", (double) regions.size());
            regions.stream().findFirst().ifPresent(region -> {
                metrics.put("boofCvLargestComponentLeftPx", (double) region.leftPx());
                metrics.put("boofCvLargestComponentTopPx", (double) region.topPx());
                metrics.put("boofCvLargestComponentRightExclusivePx", (double) region.rightExclusivePx());
                metrics.put("boofCvLargestComponentBottomExclusivePx", (double) region.bottomExclusivePx());
            });
            return Map.copyOf(metrics);
        }
    }

    /**
     * Bounded source-space component evidence proposed by BoofCV contours.
     *
     * @param leftPx source left coordinate
     * @param topPx source top coordinate
     * @param rightExclusivePx exclusive source right coordinate
     * @param bottomExclusivePx exclusive source bottom coordinate
     * @param contourPointCount number of contour points supporting this region
     * @param corners deterministic source-space corner proposal
     */
    record CandidateRegion(
            int leftPx,
            int topPx,
            int rightExclusivePx,
            int bottomExclusivePx,
            int contourPointCount,
            FrameCorners corners
    ) {

        CandidateRegion {
            Objects.requireNonNull(corners, "corners must not be null");
            if (leftPx < 0 || topPx < 0 || rightExclusivePx <= leftPx || bottomExclusivePx <= topPx) {
                throw new IllegalArgumentException("candidate source bounds must be positive");
            }
        }

        /**
         * Returns the candidate width in source pixels.
         *
         * @return source width
         */
        int widthPx() {
            return rightExclusivePx - leftPx;
        }

        /**
         * Returns the candidate height in source pixels.
         *
         * @return source height
         */
        int heightPx() {
            return bottomExclusivePx - topPx;
        }

        /**
         * Returns the shorter source edge in pixels.
         *
         * @return shorter edge length
         */
        int shortEdgePx() {
            return Math.min(widthPx(), heightPx());
        }

        /**
         * Returns bounded source area in pixels.
         *
         * @return source area
         */
        int areaPx() {
            return widthPx() * heightPx();
        }

        /**
         * Returns a normalized skew estimate from the proposed corners.
         *
         * @return skew score between 0 and 1
         */
        double skewScore() {
            double top = distance(corners.topLeftX(), corners.topLeftY(), corners.topRightX(), corners.topRightY());
            double bottom = distance(corners.bottomLeftX(), corners.bottomLeftY(),
                    corners.bottomRightX(), corners.bottomRightY());
            double left = distance(corners.topLeftX(), corners.topLeftY(), corners.bottomLeftX(),
                    corners.bottomLeftY());
            double right = distance(corners.topRightX(), corners.topRightY(), corners.bottomRightX(),
                    corners.bottomRightY());
            return clampScore(Math.max(normalizedDifference(top, bottom), normalizedDifference(left, right)));
        }

        private double normalizedDifference(double first, double second) {
            double denominator = Math.max(first, second);
            return denominator <= 0.0d ? 1.0d : Math.abs(first - second) / denominator;
        }

        private double distance(double firstX, double firstY, double secondX, double secondY) {
            return Math.hypot(firstX - secondX, firstY - secondY);
        }

        private double clampScore(double value) {
            if (!Double.isFinite(value)) {
                return 0.0d;
            }
            return Math.max(0.0d, Math.min(1.0d, value));
        }
    }
}
