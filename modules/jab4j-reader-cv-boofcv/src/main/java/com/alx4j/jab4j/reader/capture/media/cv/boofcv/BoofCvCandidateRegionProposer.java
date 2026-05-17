package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import boofcv.abst.shapes.polyline.ConfigPolylineSplitMerge;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.core.image.GConvertImage;
import boofcv.factory.shape.FactoryPointsToPolyline;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayU8;
import georegression.fitting.line.FitLine_F64;
import georegression.geometry.UtilLine2D_F64;
import georegression.metric.Intersection2D_F64;
import georegression.struct.line.LineParametric2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import org.ddogleg.struct.DogArray_I32;

/**
 * Proposes bounded source-space regions from BoofCV grayscale threshold and contour evidence.
 */
final class BoofCvCandidateRegionProposer {

    private static final int LIGHT_THRESHOLD = 178;
    private static final int MIN_CONTOUR_POINTS = 8;
    private static final int MIN_SOURCE_CANDIDATE_SHORT_EDGE_PX = 120;
    private static final int MAX_COMPONENTS_TO_RETURN = 64;
    private static final int FITTED_QUADRILATERAL_VERTEX_COUNT = 4;
    private static final int MAX_REDUCIBLE_POLYGON_VERTEX_COUNT = 16;
    private static final int[] FIT_MINIMUM_SIDE_LENGTH_DIVISORS = { 12, 10, 8, 6, 16, 20 };
    private static final double[] FIT_CORNER_PENALTIES = { 0.25d, 0.15d, 0.35d, 0.05d };
    private static final double MIN_FITTED_QUADRILATERAL_BOUNDING_AREA_RATIO = 0.65d;
    private static final double MIN_FITTED_QUADRILATERAL_SIDE_RATIO = 0.45d;
    private static final double FORCED_QUADRILATERAL_CORNER_PENALTY = 0.05d;
    private static final double FORCED_QUADRILATERAL_MAX_SIDE_ERROR_FRACTION = 0.02d;
    private static final int FORCED_QUADRILATERAL_MIN_SIDE_DIVISOR = 20;
    private static final int MAX_LINE_FIT_SIDE_SAMPLES = 256;

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
        int shortEdgePx = Math.min(rightExclusive - left, bottomExclusive - top);
        if (shortEdgePx < MIN_SOURCE_CANDIDATE_SHORT_EDGE_PX) {
            return Optional.empty();
        }
        FrameCorners fallbackCorners = new FrameCorners(
                topLeft.x,
                topLeft.y,
                topRight.x + 1.0d,
                topRight.y,
                bottomRight.x + 1.0d,
                bottomRight.y + 1.0d,
                bottomLeft.x,
                bottomLeft.y + 1.0d
        );
        QuadrilateralEvidence quadrilateral = fittedQuadrilateral(contour.external, shortEdgePx)
                .orElseGet(() -> new QuadrilateralEvidence(
                        fallbackCorners,
                        GeometrySource.CONTOUR_EXTREMA,
                        0
                ));
        return Optional.of(new CandidateRegion(
                left,
                top,
                rightExclusive,
                bottomExclusive,
                contour.external.size(),
                quadrilateral.corners(),
                quadrilateral.source(),
                quadrilateral.fittedVertexCount()
        ));
    }

    private Optional<QuadrilateralEvidence> fittedQuadrilateral(List<Point2D_I32> contour, int shortEdgePx) {
        if (shortEdgePx <= 0) {
            return Optional.empty();
        }
        QuadrilateralEvidence best = null;
        for (int divisor : FIT_MINIMUM_SIDE_LENGTH_DIVISORS) {
            int minimumSideLength = Math.max(8, shortEdgePx / divisor);
            for (double cornerPenalty : FIT_CORNER_PENALTIES) {
                List<PointIndex_I32> fitted = ShapeFittingOps.fitPolygon(
                        contour,
                        true,
                        minimumSideLength,
                        cornerPenalty
                );
                Optional<QuadrilateralEvidence> candidate = fittedPolygonQuadrilateral(fitted, shortEdgePx);
                if (candidate.isEmpty()) {
                    continue;
                }
                best = betterQuadrilateral(best, candidate.orElseThrow());
                if (best.fittedVertexCount() == FITTED_QUADRILATERAL_VERTEX_COUNT) {
                    return Optional.of(best);
                }
            }
        }
        Optional<QuadrilateralEvidence> forced = forcedConvexQuadrilateral(contour, shortEdgePx);
        if (forced.isPresent()) {
            return forced;
        }
        return Optional.ofNullable(best);
    }

    private Optional<QuadrilateralEvidence> forcedConvexQuadrilateral(
            List<Point2D_I32> contour,
            int shortEdgePx
    ) {
        ConfigPolylineSplitMerge config = new ConfigPolylineSplitMerge();
        config.loops = true;
        config.minimumSides = FITTED_QUADRILATERAL_VERTEX_COUNT;
        config.maximumSides = FITTED_QUADRILATERAL_VERTEX_COUNT;
        config.convex = true;
        config.minimumSideLength = Math.max(8, shortEdgePx / FORCED_QUADRILATERAL_MIN_SIDE_DIVISOR);
        config.cornerScorePenalty = FORCED_QUADRILATERAL_CORNER_PENALTY;
        config.maxSideError.fraction = FORCED_QUADRILATERAL_MAX_SIDE_ERROR_FRACTION;
        config.maxSideError.length = 1.0d;

        DogArray_I32 splits = new DogArray_I32();
        if (!FactoryPointsToPolyline.create(config).process(contour, splits)
                || splits.size != FITTED_QUADRILATERAL_VERTEX_COUNT) {
            return Optional.empty();
        }
        List<PointIndex_I32> fitted = new ArrayList<>(splits.size);
        for (int index = 0; index < splits.size; index++) {
            int contourIndex = splits.get(index);
            Point2D_I32 point = contour.get(contourIndex);
            fitted.add(new PointIndex_I32(point.x, point.y, contourIndex));
        }
        FrameCorners corners = fittedLineIntersectionCorners(contour, splits)
                .orElseGet(() -> orderedCorners(fitted));
        if (!usableFittedQuadrilateral(corners, shortEdgePx)) {
            return Optional.empty();
        }
        return Optional.of(new QuadrilateralEvidence(
                corners,
                GeometrySource.BOOFCV_FITTED_QUADRILATERAL,
                fitted.size()
        ));
    }

    private Optional<FrameCorners> fittedLineIntersectionCorners(
            List<Point2D_I32> contour,
            DogArray_I32 splits
    ) {
        if (splits.size != FITTED_QUADRILATERAL_VERTEX_COUNT) {
            return Optional.empty();
        }
        List<LineParametric2D_F64> lines = new ArrayList<>(FITTED_QUADRILATERAL_VERTEX_COUNT);
        for (int index = 0; index < splits.size; index++) {
            Optional<LineParametric2D_F64> line = fittedContourSideLine(
                    contour,
                    splits.get(index),
                    splits.get((index + 1) % splits.size)
            );
            if (line.isEmpty()) {
                return Optional.empty();
            }
            lines.add(line.orElseThrow());
        }

        List<Point2D_F64> intersections = new ArrayList<>(FITTED_QUADRILATERAL_VERTEX_COUNT);
        for (int index = 0; index < lines.size(); index++) {
            Point2D_F64 corner = Intersection2D_F64.intersection(
                    lines.get((index + lines.size() - 1) % lines.size()),
                    lines.get(index),
                    null
            );
            if (corner == null || !Double.isFinite(corner.x) || !Double.isFinite(corner.y)) {
                return Optional.empty();
            }
            intersections.add(corner);
        }
        return Optional.of(orderedFloatCorners(intersections));
    }

    private Optional<LineParametric2D_F64> fittedContourSideLine(
            List<Point2D_I32> contour,
            int startIndex,
            int endIndex
    ) {
        int distance = cyclicDistance(startIndex, endIndex, contour.size());
        if (distance < 2) {
            return Optional.empty();
        }
        int step = Math.max(1, distance / MAX_LINE_FIT_SIDE_SAMPLES);
        List<Point2D_F64> points = new ArrayList<>((distance / step) + 2);
        for (int offset = 0; offset <= distance; offset += step) {
            Point2D_I32 point = contour.get((startIndex + offset) % contour.size());
            points.add(new Point2D_F64(point.x, point.y));
        }
        Point2D_I32 endPoint = contour.get(endIndex);
        points.add(new Point2D_F64(endPoint.x, endPoint.y));
        return Optional.ofNullable(FitLine_F64.polar(points, null))
                .map(line -> UtilLine2D_F64.convert(line, new LineParametric2D_F64()));
    }

    private int cyclicDistance(int startIndex, int endIndex, int pointCount) {
        return endIndex >= startIndex
                ? endIndex - startIndex
                : pointCount - startIndex + endIndex;
    }

    private Optional<QuadrilateralEvidence> fittedPolygonQuadrilateral(List<PointIndex_I32> fitted, int shortEdgePx) {
        if (fitted.size() < FITTED_QUADRILATERAL_VERTEX_COUNT
                || fitted.size() > MAX_REDUCIBLE_POLYGON_VERTEX_COUNT) {
            return Optional.empty();
        }
        FrameCorners corners = orderedCorners(fitted);
        if (!usableFittedQuadrilateral(corners, shortEdgePx)) {
            return Optional.empty();
        }
        return Optional.of(new QuadrilateralEvidence(
                corners,
                fitted.size() == FITTED_QUADRILATERAL_VERTEX_COUNT
                        ? GeometrySource.BOOFCV_FITTED_QUADRILATERAL
                        : GeometrySource.BOOFCV_REDUCED_FITTED_QUADRILATERAL,
                fitted.size()
        ));
    }

    private QuadrilateralEvidence betterQuadrilateral(
            QuadrilateralEvidence current,
            QuadrilateralEvidence candidate
    ) {
        if (current == null) {
            return candidate;
        }
        if (candidate.fittedVertexCount() == FITTED_QUADRILATERAL_VERTEX_COUNT
                && current.fittedVertexCount() != FITTED_QUADRILATERAL_VERTEX_COUNT) {
            return candidate;
        }
        if (candidate.fittedVertexCount() != FITTED_QUADRILATERAL_VERTEX_COUNT
                && current.fittedVertexCount() == FITTED_QUADRILATERAL_VERTEX_COUNT) {
            return current;
        }
        if (candidate.fittedVertexCount() < current.fittedVertexCount()) {
            return candidate;
        }
        if (candidate.fittedVertexCount() > current.fittedVertexCount()) {
            return current;
        }
        double candidateArea = Math.abs(quadrilateralArea(candidate.corners()));
        double currentArea = Math.abs(quadrilateralArea(current.corners()));
        return candidateArea > currentArea ? candidate : current;
    }

    private FrameCorners orderedCorners(List<PointIndex_I32> points) {
        PointIndex_I32 topLeft = null;
        PointIndex_I32 topRight = null;
        PointIndex_I32 bottomRight = null;
        PointIndex_I32 bottomLeft = null;
        double topLeftScore = Double.POSITIVE_INFINITY;
        double topRightScore = Double.NEGATIVE_INFINITY;
        double bottomRightScore = Double.NEGATIVE_INFINITY;
        double bottomLeftScore = Double.NEGATIVE_INFINITY;
        for (PointIndex_I32 point : points) {
            double topLeftCandidate = point.x + point.y;
            if (topLeftCandidate < topLeftScore) {
                topLeftScore = topLeftCandidate;
                topLeft = point;
            }
            double topRightCandidate = point.x - point.y;
            if (topRightCandidate > topRightScore) {
                topRightScore = topRightCandidate;
                topRight = point;
            }
            double bottomRightCandidate = point.x + point.y;
            if (bottomRightCandidate > bottomRightScore) {
                bottomRightScore = bottomRightCandidate;
                bottomRight = point;
            }
            double bottomLeftCandidate = point.y - point.x;
            if (bottomLeftCandidate > bottomLeftScore) {
                bottomLeftScore = bottomLeftCandidate;
                bottomLeft = point;
            }
        }
        if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null) {
            throw new IllegalArgumentException("fitted quadrilateral must provide four ordered corners");
        }
        return new FrameCorners(
                topLeft.x,
                topLeft.y,
                topRight.x + 1.0d,
                topRight.y,
                bottomRight.x + 1.0d,
                bottomRight.y + 1.0d,
                bottomLeft.x,
                bottomLeft.y + 1.0d
        );
    }

    private FrameCorners orderedFloatCorners(List<Point2D_F64> points) {
        Point2D_F64 topLeft = null;
        Point2D_F64 topRight = null;
        Point2D_F64 bottomRight = null;
        Point2D_F64 bottomLeft = null;
        double topLeftScore = Double.POSITIVE_INFINITY;
        double topRightScore = Double.NEGATIVE_INFINITY;
        double bottomRightScore = Double.NEGATIVE_INFINITY;
        double bottomLeftScore = Double.NEGATIVE_INFINITY;
        for (Point2D_F64 point : points) {
            double topLeftCandidate = point.x + point.y;
            if (topLeftCandidate < topLeftScore) {
                topLeftScore = topLeftCandidate;
                topLeft = point;
            }
            double topRightCandidate = point.x - point.y;
            if (topRightCandidate > topRightScore) {
                topRightScore = topRightCandidate;
                topRight = point;
            }
            double bottomRightCandidate = point.x + point.y;
            if (bottomRightCandidate > bottomRightScore) {
                bottomRightScore = bottomRightCandidate;
                bottomRight = point;
            }
            double bottomLeftCandidate = point.y - point.x;
            if (bottomLeftCandidate > bottomLeftScore) {
                bottomLeftScore = bottomLeftCandidate;
                bottomLeft = point;
            }
        }
        if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null) {
            throw new IllegalArgumentException("fitted quadrilateral must provide four ordered corners");
        }
        return new FrameCorners(
                topLeft.x,
                topLeft.y,
                topRight.x + 1.0d,
                topRight.y,
                bottomRight.x + 1.0d,
                bottomRight.y + 1.0d,
                bottomLeft.x,
                bottomLeft.y + 1.0d
        );
    }

    private boolean usableFittedQuadrilateral(FrameCorners corners, int shortEdgePx) {
        if (duplicateCorners(corners)) {
            return false;
        }
        double area = Math.abs(quadrilateralArea(corners));
        if (area < 1.0d) {
            return false;
        }
        double boundingArea = boundingArea(corners);
        if (boundingArea <= 0.0d || area / boundingArea < MIN_FITTED_QUADRILATERAL_BOUNDING_AREA_RATIO) {
            return false;
        }
        double minimumSideLength = Math.min(
                Math.min(
                        distance(corners.topLeftX(), corners.topLeftY(), corners.topRightX(), corners.topRightY()),
                        distance(
                                corners.topRightX(),
                                corners.topRightY(),
                                corners.bottomRightX(),
                                corners.bottomRightY()
                        )
                ),
                Math.min(
                        distance(
                                corners.bottomRightX(),
                                corners.bottomRightY(),
                                corners.bottomLeftX(),
                                corners.bottomLeftY()
                        ),
                        distance(corners.bottomLeftX(), corners.bottomLeftY(), corners.topLeftX(), corners.topLeftY())
                )
        );
        return minimumSideLength >= shortEdgePx * MIN_FITTED_QUADRILATERAL_SIDE_RATIO;
    }

    private boolean duplicateCorners(FrameCorners corners) {
        return samePoint(corners.topLeftX(), corners.topLeftY(), corners.topRightX(), corners.topRightY())
                || samePoint(corners.topLeftX(), corners.topLeftY(), corners.bottomRightX(), corners.bottomRightY())
                || samePoint(corners.topLeftX(), corners.topLeftY(), corners.bottomLeftX(), corners.bottomLeftY())
                || samePoint(corners.topRightX(), corners.topRightY(), corners.bottomRightX(), corners.bottomRightY())
                || samePoint(corners.topRightX(), corners.topRightY(), corners.bottomLeftX(), corners.bottomLeftY())
                || samePoint(
                        corners.bottomRightX(),
                        corners.bottomRightY(),
                        corners.bottomLeftX(),
                        corners.bottomLeftY()
                );
    }

    private boolean samePoint(double firstX, double firstY, double secondX, double secondY) {
        return Math.abs(firstX - secondX) < 1.0d && Math.abs(firstY - secondY) < 1.0d;
    }

    private double boundingArea(FrameCorners corners) {
        double minX = Math.min(
                Math.min(corners.topLeftX(), corners.topRightX()),
                Math.min(corners.bottomRightX(), corners.bottomLeftX())
        );
        double maxX = Math.max(
                Math.max(corners.topLeftX(), corners.topRightX()),
                Math.max(corners.bottomRightX(), corners.bottomLeftX())
        );
        double minY = Math.min(
                Math.min(corners.topLeftY(), corners.topRightY()),
                Math.min(corners.bottomRightY(), corners.bottomLeftY())
        );
        double maxY = Math.max(
                Math.max(corners.topLeftY(), corners.topRightY()),
                Math.max(corners.bottomRightY(), corners.bottomLeftY())
        );
        return (maxX - minX) * (maxY - minY);
    }

    private double quadrilateralArea(FrameCorners corners) {
        return 0.5d * (
                (corners.topLeftX() * corners.topRightY()) - (corners.topLeftY() * corners.topRightX())
                        + (corners.topRightX() * corners.bottomRightY())
                        - (corners.topRightY() * corners.bottomRightX())
                        + (corners.bottomRightX() * corners.bottomLeftY())
                        - (corners.bottomRightY() * corners.bottomLeftX())
                        + (corners.bottomLeftX() * corners.topLeftY())
                        - (corners.bottomLeftY() * corners.topLeftX())
        );
    }

    private double distance(double firstX, double firstY, double secondX, double secondY) {
        return Math.hypot(firstX - secondX, firstY - secondY);
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
            metrics.put("boofCvFittedQuadrilateralCandidateCount", (double) regions.stream()
                    .filter(region -> region.geometrySource().fittedQuadrilateral())
                    .count());
            regions.stream().findFirst().ifPresent(region -> {
                metrics.put("boofCvLargestComponentLeftPx", (double) region.leftPx());
                metrics.put("boofCvLargestComponentTopPx", (double) region.topPx());
                metrics.put("boofCvLargestComponentRightExclusivePx", (double) region.rightExclusivePx());
                metrics.put("boofCvLargestComponentBottomExclusivePx", (double) region.bottomExclusivePx());
                metrics.put("boofCvLargestComponentGeometrySourceCode", region.geometrySource().metricCode());
                metrics.put("boofCvLargestComponentFittedVertexCount", (double) region.fittedVertexCount());
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
            FrameCorners corners,
            GeometrySource geometrySource,
            int fittedVertexCount
    ) {

        CandidateRegion(
                int leftPx,
                int topPx,
                int rightExclusivePx,
                int bottomExclusivePx,
                int contourPointCount,
                FrameCorners corners
        ) {
            this(
                    leftPx,
                    topPx,
                    rightExclusivePx,
                    bottomExclusivePx,
                    contourPointCount,
                    corners,
                    GeometrySource.CONTOUR_EXTREMA,
                    0
            );
        }

        CandidateRegion {
            Objects.requireNonNull(corners, "corners must not be null");
            Objects.requireNonNull(geometrySource, "geometrySource must not be null");
            if (leftPx < 0 || topPx < 0 || rightExclusivePx <= leftPx || bottomExclusivePx <= topPx) {
                throw new IllegalArgumentException("candidate source bounds must be positive");
            }
            if (contourPointCount < 0 || fittedVertexCount < 0) {
                throw new IllegalArgumentException("candidate evidence counts must be non-negative");
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

    private record QuadrilateralEvidence(
            FrameCorners corners,
            GeometrySource source,
            int fittedVertexCount
    ) {
    }

    enum GeometrySource {
        CONTOUR_EXTREMA(stablePrefix() + "contour-extrema", 1.0d),
        BOOFCV_FITTED_QUADRILATERAL(stablePrefix() + "fitted-quadrilateral", 2.0d),
        BOOFCV_REDUCED_FITTED_QUADRILATERAL(stablePrefix() + "fitted-quadrilateral-reduced", 3.0d);

        private final String sidecarValue;
        private final double metricCode;

        GeometrySource(String sidecarValue, double metricCode) {
            this.sidecarValue = sidecarValue;
            this.metricCode = metricCode;
        }

        String sidecarValue() {
            return sidecarValue;
        }

        double metricCode() {
            return metricCode;
        }

        boolean fittedQuadrilateral() {
            return this == BOOFCV_FITTED_QUADRILATERAL || this == BOOFCV_REDUCED_FITTED_QUADRILATERAL;
        }

        private static String stablePrefix() {
            return new String(new char[] { 'b', 'o', 'o', 'f', 'c', 'v' }) + "-";
        }
    }
}
