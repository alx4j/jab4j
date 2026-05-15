package com.alx4j.jab4j.reader.capture.media.cv.legacy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.cv.CvBackendIdentity;
import com.alx4j.jab4j.reader.capture.media.cv.CvCandidateScore;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.cv.PerspectiveTransform;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;

/**
 * Legacy capture-media CV backend that preserves the custom MVP-3 detection and normalization behavior.
 */
public final class LegacyCaptureMediaCvBackend implements CaptureMediaCvBackend {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int DARK_GRAY = 0xFF202020;
    private static final int[] RENDERED_COLORS = {
            0xFF000000,
            0xFF0000FF,
            0xFF00FF00,
            0xFF00FFFF,
            0xFFFF0000,
            0xFFFF00FF,
            0xFFFFFF00,
            0xFFFFFFFF,
            DARK_GRAY
    };
    private static final double MIN_GENERATED_FRAME_COVERAGE_RATIO = 0.20d;
    private static final double MAX_GENERATED_PERSPECTIVE_SKEW_SCORE = 0.35d;
    private static final CvBackendIdentity IDENTITY = new CvBackendIdentity(
            "legacy",
            Optional.of("com.alx4j:jab4j-reader"),
            Optional.ofNullable(LegacyCaptureMediaCvBackend.class.getPackage().getImplementationVersion()),
            List.of(
                    "generated-perspective-detection",
                    "camera-like-candidate-ranking",
                    "legacy-perspective-resampling"
            )
    );

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final JabFrameRegionDetector jabFrameRegionDetector;

    /**
     * Creates a legacy backend backed by the supported rendered layout catalog.
     */
    public LegacyCaptureMediaCvBackend() {
        this(new CaptureRenderedLayoutCatalog(), new FixedLayoutPlanner());
    }

    /**
     * Creates a legacy backend with explicit rendered-layout collaborators.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for JAB geometry
     */
    public LegacyCaptureMediaCvBackend(CaptureRenderedLayoutCatalog layoutCatalog, FixedLayoutPlanner layoutPlanner) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.jabFrameRegionDetector = new JabFrameRegionDetector(this.layoutCatalog, this.layoutPlanner);
    }

    /**
     * Returns metadata for the legacy custom capture-media CV implementation.
     *
     * @return legacy backend identity metadata
     */
    @Override
    public CvBackendIdentity identity() {
        return IDENTITY;
    }

    /**
     * Detects generated perspective-corrected frames and camera-like JAB frame candidates.
     *
     * @param frame decoded media input frame
     * @return backend-neutral detection result
     */
    @Override
    public CvDetectionResult detect(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        try {
            return detectInternal(frame);
        } catch (RuntimeException exception) {
            return CvDetectionResult.backendFailure(
                    Map.of(),
                    "Legacy capture-media CV backend failed while evaluating the frame"
            );
        }
    }

    private CvDetectionResult detectInternal(MediaInputFrame frame) {
        Optional<CvDetectionResult> generatedPerspectiveResult = detectGeneratedPerspectiveCorrectedFrame(frame);
        if (generatedPerspectiveResult.isPresent()) {
            return generatedPerspectiveResult.orElseThrow();
        }

        JabFrameDetectionResult detectionResult = jabFrameRegionDetector.detect(frame);
        return switch (detectionResult.status()) {
            case ACCEPTED -> CvDetectionResult.acceptedCandidates(toCvFrameCandidates(
                    detectionResult.rankedCandidates()
            ));
            case AMBIGUOUS -> CvDetectionResult.ambiguous(
                    toCvFrameCandidates(detectionResult.rankedCandidates()),
                    detectionResult.metrics(),
                    "Media normalization found multiple plausible JAB frame regions"
            );
            case TOO_SMALL -> CvDetectionResult.tooSmall(
                    toCvFrameCandidates(detectionResult.rankedCandidates()),
                    detectionResult.metrics(),
                    "Detected JAB frame region is below the minimum generated coverage threshold"
            );
            case NOT_FOUND -> CvDetectionResult.rejected(
                    CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                    detectionResult.metrics(),
                    "Media normalization did not find a clean supported rendered frame region"
            );
        };
    }

    private Optional<CvDetectionResult> detectGeneratedPerspectiveCorrectedFrame(MediaInputFrame frame) {
        Optional<FrameCorners> detectedCorners = detectRenderedColorQuadrilateral(frame);
        if (detectedCorners.isEmpty()) {
            return Optional.empty();
        }
        FrameCorners corners = detectedCorners.get();
        double coverageRatio = quadrilateralArea(corners)
                / ((double) frame.widthPixels() * frame.heightPixels());
        if (coverageRatio < MIN_GENERATED_FRAME_COVERAGE_RATIO) {
            return Optional.empty();
        }

        PerspectiveTransform transform;
        try {
            transform = PerspectiveTransform.fromUnitSquareTo(corners);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        double skewScore = perspectiveSkewScore(corners);
        for (LayoutProfile profile : layoutCatalog.profiles()) {
            int[] correctedPixels = resamplePerspective(frame, profile, transform);
            if (!hasPerspectiveCorrectedFrameEvidence(frame, profile, correctedPixels)) {
                continue;
            }
            if (skewScore > MAX_GENERATED_PERSPECTIVE_SKEW_SCORE) {
                return Optional.of(CvDetectionResult.rejected(
                        CaptureMediaDiagnosticCode.PERSPECTIVE_TOO_SEVERE,
                        Map.of(),
                        "Detected generated frame perspective exceeds the supported correction threshold"
                ));
            }
            return Optional.of(CvDetectionResult.acceptedNormalizedFrames(List.of(new CvNormalizedFrame(
                    profile,
                    corners,
                    CaptureMediaQualityMetrics.perspectiveCorrected(coverageRatio, skewScore),
                    correctedPixels
            ))));
        }
        return Optional.empty();
    }

    private List<CvFrameCandidate> toCvFrameCandidates(List<JabFrameCandidate> candidates) {
        return candidates.stream()
                .map(this::toCvFrameCandidate)
                .toList();
    }

    private CvFrameCandidate toCvFrameCandidate(JabFrameCandidate candidate) {
        return new CvFrameCandidate(
                candidate.profile(),
                candidate.corners(),
                candidate.sourceLeftPx(),
                candidate.sourceTopPx(),
                candidate.sourceRightExclusivePx(),
                candidate.sourceBottomExclusivePx(),
                toCvCandidateScore(candidate.score())
        );
    }

    private CvCandidateScore toCvCandidateScore(JabFrameCandidateScore score) {
        return new CvCandidateScore(
                score.totalScore(),
                score.frameCoverageRatio(),
                score.skewScore(),
                score.borderContrastScore(),
                score.syncBandScore(),
                score.gridScore(),
                score.layoutAspectScore(),
                score.blurScore(),
                score.glareScore(),
                score.paletteDistanceConfidence()
        );
    }

    private Optional<FrameCorners> detectRenderedColorQuadrilateral(MediaInputFrame frame) {
        ExtremePoint topLeft = null;
        ExtremePoint topRight = null;
        ExtremePoint bottomRight = null;
        ExtremePoint bottomLeft = null;
        int renderedColorPixels = 0;

        for (int row = 0; row < frame.heightPixels(); row++) {
            for (int col = 0; col < frame.widthPixels(); col++) {
                if (!isRenderedColor(frame.argbPixelAt(row, col))) {
                    continue;
                }
                renderedColorPixels++;
                topLeft = minExtreme(topLeft, col + row, col, row);
                topRight = maxExtreme(topRight, col - row, col, row);
                bottomRight = maxExtreme(bottomRight, col + row, col, row);
                bottomLeft = maxExtreme(bottomLeft, row - col, col, row);
            }
        }

        if (renderedColorPixels < 1000
                || topLeft == null
                || topRight == null
                || bottomRight == null
                || bottomLeft == null) {
            return Optional.empty();
        }

        FrameCorners corners = new FrameCorners(
                topLeft.x(),
                topLeft.y(),
                topRight.x(),
                topRight.y(),
                bottomRight.x(),
                bottomRight.y(),
                bottomLeft.x(),
                bottomLeft.y()
        );
        if (!cornersAreDistinct(corners)
                || quadrilateralArea(corners) <= 0.0d
                || !cornersInsideFrame(frame, corners)) {
            return Optional.empty();
        }
        return Optional.of(corners);
    }

    private ExtremePoint minExtreme(ExtremePoint current, int score, int x, int y) {
        if (current == null || score < current.score()) {
            return new ExtremePoint(score, x, y);
        }
        return current;
    }

    private ExtremePoint maxExtreme(ExtremePoint current, int score, int x, int y) {
        if (current == null || score > current.score()) {
            return new ExtremePoint(score, x, y);
        }
        return current;
    }

    private boolean isRenderedColor(int argb) {
        for (int renderedColor : RENDERED_COLORS) {
            if (argb == renderedColor) {
                return true;
            }
        }
        return false;
    }

    private boolean cornersAreDistinct(FrameCorners corners) {
        return distance(corners.topLeftX(), corners.topLeftY(), corners.topRightX(), corners.topRightY()) > 1.0d
                && distance(corners.topRightX(), corners.topRightY(), corners.bottomRightX(), corners.bottomRightY()) > 1.0d
                && distance(corners.bottomRightX(), corners.bottomRightY(), corners.bottomLeftX(), corners.bottomLeftY()) > 1.0d
                && distance(corners.bottomLeftX(), corners.bottomLeftY(), corners.topLeftX(), corners.topLeftY()) > 1.0d;
    }

    private boolean cornersInsideFrame(MediaInputFrame frame, FrameCorners corners) {
        return insideFrame(frame, corners.topLeftX(), corners.topLeftY())
                && insideFrame(frame, corners.topRightX(), corners.topRightY())
                && insideFrame(frame, corners.bottomRightX(), corners.bottomRightY())
                && insideFrame(frame, corners.bottomLeftX(), corners.bottomLeftY());
    }

    private boolean insideFrame(MediaInputFrame frame, double x, double y) {
        return x >= 0.0d
                && x < frame.widthPixels()
                && y >= 0.0d
                && y < frame.heightPixels();
    }

    private int[] resamplePerspective(
            MediaInputFrame frame,
            LayoutProfile profile,
            PerspectiveTransform transform
    ) {
        int width = profile.frameWidthPx();
        int height = profile.frameHeightPx();
        int[] correctedPixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            double normalizedY = normalizedCoordinate(row, height);
            for (int col = 0; col < width; col++) {
                double normalizedX = normalizedCoordinate(col, width);
                PerspectiveTransform.PerspectivePoint source = transform.map(normalizedX, normalizedY);
                correctedPixels[(row * width) + col] = nearestPixel(frame, source.x(), source.y());
            }
        }
        return correctedPixels;
    }

    private double normalizedCoordinate(int coordinate, int size) {
        if (size <= 1) {
            return 0.0d;
        }
        return (double) coordinate / (double) (size - 1);
    }

    private int nearestPixel(MediaInputFrame frame, double x, double y) {
        int sourceX = clamp((int) Math.round(x), 0, frame.widthPixels() - 1);
        int sourceY = clamp((int) Math.round(y), 0, frame.heightPixels() - 1);
        return frame.argbPixelAt(sourceY, sourceX);
    }

    private boolean hasPerspectiveCorrectedFrameEvidence(
            MediaInputFrame sourceFrame,
            LayoutProfile profile,
            int[] correctedPixels
    ) {
        MediaInputFrame correctedFrame = new MediaInputFrame(
                sourceFrame.sourceId(),
                sourceFrame.sourceKind(),
                sourceFrame.callerOrder(),
                profile.frameWidthPx(),
                profile.frameHeightPx(),
                sourceFrame.formatName(),
                sourceFrame.pixelSha256(),
                correctedPixels
        );
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
        int right = profile.frameWidthPx() - 1;
        int bottom = profile.frameHeightPx() - 1;
        int border = layoutPlan.separatorThicknessPx();
        try {
            return correctedFrame.argbPixelAt(0, 0) == WHITE
                    && correctedFrame.argbPixelAt(0, right) == WHITE
                    && correctedFrame.argbPixelAt(bottom, 0) == WHITE
                    && correctedFrame.argbPixelAt(bottom, right) == WHITE
                    && correctedFrame.argbPixelAt(border, border) == BLACK
                    && hasExactSyncBandSample(correctedFrame, profile, layoutPlan, 0, 0)
                    && hasExactTileSlotGridSample(correctedFrame, profile, layoutPlan, 0, 0);
        } finally {
            correctedFrame.releaseArgbPixels();
        }
    }

    private boolean hasExactSyncBandSample(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        int syncLeft = left + profile.outerMarginPx();
        int syncTop = top + profile.outerMarginPx();
        int cellWidth = syncCellWidth(layoutPlan);
        int firstBlackCellX = syncLeft + cellWidth;
        int syncRightExclusive = left + profile.frameWidthPx() - profile.outerMarginPx();
        return frame.argbPixelAt(syncTop, syncLeft) == WHITE
                && firstBlackCellX < syncRightExclusive
                && frame.argbPixelAt(syncTop, firstBlackCellX) == BLACK;
    }

    private boolean hasExactTileSlotGridSample(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        Optional<GridSample> sample = firstGridSample(profile, layoutPlan);
        return sample.isPresent()
                && frame.argbPixelAt(top + sample.get().relativeY(), left + sample.get().relativeX()) == WHITE;
    }

    private Optional<GridSample> firstGridSample(LayoutProfile profile, FixedLayoutPlan layoutPlan) {
        if (profile.cols() > 1) {
            TilePlacement leftPlacement = layoutPlan.tilePlacements().get(0);
            TilePlacement rightPlacement = layoutPlan.tilePlacements().get(1);
            int relativeX = leftPlacement.xPx() + leftPlacement.widthPx()
                    + ((rightPlacement.xPx() - leftPlacement.xPx() - leftPlacement.widthPx()) / 2);
            int relativeY = layoutPlan.gridOriginYPx() + (layoutPlan.tileSlotHeightPx() / 2);
            return Optional.of(new GridSample(relativeX, relativeY));
        }
        if (profile.rows() > 1) {
            TilePlacement upperPlacement = layoutPlan.tilePlacements().get(0);
            TilePlacement lowerPlacement = layoutPlan.tilePlacements().get(profile.cols());
            int relativeX = layoutPlan.gridOriginXPx() + (layoutPlan.tileSlotWidthPx() / 2);
            int relativeY = upperPlacement.yPx() + upperPlacement.heightPx()
                    + ((lowerPlacement.yPx() - upperPlacement.yPx() - upperPlacement.heightPx()) / 2);
            return Optional.of(new GridSample(relativeX, relativeY));
        }
        return Optional.empty();
    }

    private int syncCellWidth(FixedLayoutPlan layoutPlan) {
        return Math.max(8, layoutPlan.separatorThicknessPx() * 2);
    }

    private double quadrilateralArea(FrameCorners corners) {
        double doubledArea = (corners.topLeftX() * corners.topRightY())
                - (corners.topLeftY() * corners.topRightX())
                + (corners.topRightX() * corners.bottomRightY())
                - (corners.topRightY() * corners.bottomRightX())
                + (corners.bottomRightX() * corners.bottomLeftY())
                - (corners.bottomRightY() * corners.bottomLeftX())
                + (corners.bottomLeftX() * corners.topLeftY())
                - (corners.bottomLeftY() * corners.topLeftX());
        return Math.abs(doubledArea) / 2.0d;
    }

    private double perspectiveSkewScore(FrameCorners corners) {
        double top = distance(corners.topLeftX(), corners.topLeftY(), corners.topRightX(), corners.topRightY());
        double bottom = distance(corners.bottomLeftX(), corners.bottomLeftY(), corners.bottomRightX(), corners.bottomRightY());
        double left = distance(corners.topLeftX(), corners.topLeftY(), corners.bottomLeftX(), corners.bottomLeftY());
        double right = distance(corners.topRightX(), corners.topRightY(), corners.bottomRightX(), corners.bottomRightY());
        double horizontalSkew = normalizedDifference(top, bottom);
        double verticalSkew = normalizedDifference(left, right);
        return Math.min(1.0d, Math.max(horizontalSkew, verticalSkew));
    }

    private double normalizedDifference(double first, double second) {
        double denominator = Math.max(first, second);
        if (denominator <= 0.0d) {
            return 1.0d;
        }
        return Math.abs(first - second) / denominator;
    }

    private double distance(double firstX, double firstY, double secondX, double secondY) {
        double deltaX = firstX - secondX;
        double deltaY = firstY - secondY;
        return Math.hypot(deltaX, deltaY);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ExtremePoint(int score, int x, int y) {
    }

    private record GridSample(int relativeX, int relativeY) {
    }
}
