package com.alx4j.jab4j.reader.capture.media.normalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;

/**
 * Performs conservative media normalization for exact rendered frames and generated axis-aligned insets.
 */
public final class CaptureMediaFrameNormalizer {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final double MIN_GENERATED_FRAME_COVERAGE_RATIO = 0.20d;
    private static final int MIN_PARTIAL_SYNC_SAMPLES = 4;

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;

    /**
     * Creates a normalizer backed by the existing supported rendered layout catalog.
     */
    public CaptureMediaFrameNormalizer() {
        this(new CaptureRenderedLayoutCatalog(), new FixedLayoutPlanner());
    }

    /**
     * Creates a normalizer with an explicit rendered layout catalog.
     *
     * @param layoutCatalog supported rendered layout catalog
     */
    public CaptureMediaFrameNormalizer(CaptureRenderedLayoutCatalog layoutCatalog) {
        this(layoutCatalog, new FixedLayoutPlanner());
    }

    /**
     * Creates a normalizer with explicit rendered-layout collaborators.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for rendered-frame signatures
     */
    public CaptureMediaFrameNormalizer(CaptureRenderedLayoutCatalog layoutCatalog, FixedLayoutPlanner layoutPlanner) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
    }

    /**
     * Normalizes one frame when it is an exact supported render or a clean generated axis-aligned inset.
     *
     * @param frame decoded media input frame
     * @return accepted normalized frame or a blocking normalization diagnostic
     */
    public MediaNormalizationResult normalize(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        return layoutCatalog.resolve(frame.widthPixels(), frame.heightPixels())
                .map(profile -> accepted(frame, profile))
                .orElseGet(() -> normalizeAxisAlignedInset(frame));
    }

    private MediaNormalizationResult accepted(MediaInputFrame frame, LayoutProfile profile) {
        return MediaNormalizationResult.accepted(NormalizedCaptureFrame.fromExactRenderedFrame(frame, profile));
    }

    private MediaNormalizationResult rejectedUnsupportedDimensions(MediaInputFrame frame) {
        return rejected(
                frame,
                CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                "Media normalization did not find a clean supported rendered frame region"
        );
    }

    private MediaNormalizationResult rejected(
            MediaInputFrame frame,
            CaptureMediaDiagnosticCode code,
            String message
    ) {
        return MediaNormalizationResult.rejected(CaptureMediaDiagnostic.forSource(
                code,
                CaptureMediaDiagnosticSeverity.ERROR,
                frame.sourceKind(),
                frame.sourceId(),
                frame.callerOrder(),
                message
        ));
    }

    private MediaNormalizationResult normalizeAxisAlignedInset(MediaInputFrame frame) {
        RegionDetection detection = detectAxisAlignedInset(frame);
        return switch (detection.status()) {
            case ACCEPTED -> {
                DetectedInset detectedInset = detection.inset().orElseThrow();
                yield MediaNormalizationResult.accepted(NormalizedCaptureFrame.fromAxisAlignedInset(
                        frame,
                        detectedInset.profile(),
                        detectedInset.leftPx(),
                        detectedInset.topPx()
                ));
            }
            case AMBIGUOUS -> rejected(
                    frame,
                    CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS,
                    "Media normalization found multiple complete supported rendered frame regions"
            );
            case TOO_SMALL -> rejected(
                    frame,
                    CaptureMediaDiagnosticCode.MONITOR_TOO_SMALL,
                    "Detected supported rendered frame region is below the minimum generated coverage threshold"
            );
            case PARTIAL -> rejected(
                    frame,
                    CaptureMediaDiagnosticCode.FRAME_PARTIALLY_OUTSIDE_IMAGE,
                    "Media normalization found partial generated frame evidence at the image boundary"
            );
            case NOT_FOUND -> rejectedUnsupportedDimensions(frame);
        };
    }

    private RegionDetection detectAxisAlignedInset(MediaInputFrame frame) {
        List<DetectedInset> detectedInsets = new ArrayList<>();
        for (LayoutProfile profile : layoutCatalog.profiles()) {
            if (frame.widthPixels() < profile.frameWidthPx()
                    || frame.heightPixels() < profile.frameHeightPx()
                    || (frame.widthPixels() == profile.frameWidthPx()
                    && frame.heightPixels() == profile.frameHeightPx())) {
                continue;
            }
            findAxisAlignedInsets(frame, profile, detectedInsets);
            if (detectedInsets.size() > 1) {
                return RegionDetection.ambiguous();
            }
        }
        if (detectedInsets.size() == 1) {
            DetectedInset detectedInset = detectedInsets.get(0);
            double coverageRatio = frameCoverageRatio(frame, detectedInset.profile());
            if (coverageRatio < MIN_GENERATED_FRAME_COVERAGE_RATIO) {
                return RegionDetection.tooSmall();
            }
            return RegionDetection.accepted(detectedInset);
        }
        if (hasPartialAxisAlignedFrameEvidence(frame)) {
            return RegionDetection.partial();
        }
        return RegionDetection.notFound();
    }

    private void findAxisAlignedInsets(
            MediaInputFrame frame,
            LayoutProfile profile,
            List<DetectedInset> detectedInsets
    ) {
        int maxLeft = frame.widthPixels() - profile.frameWidthPx();
        int maxTop = frame.heightPixels() - profile.frameHeightPx();
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
        for (int top = 0; top <= maxTop; top++) {
            for (int left = 0; left <= maxLeft; left++) {
                if (hasExactRenderedFrameSignature(frame, profile, layoutPlan, left, top)) {
                    detectedInsets.add(new DetectedInset(profile, left, top));
                    if (detectedInsets.size() > 1) {
                        return;
                    }
                }
            }
        }
    }

    private boolean hasExactRenderedFrameSignature(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        int right = left + profile.frameWidthPx() - 1;
        int bottom = top + profile.frameHeightPx() - 1;
        int border = layoutPlan.separatorThicknessPx();
        if (frame.argbPixelAt(top, left) != WHITE
                || frame.argbPixelAt(top, right) != WHITE
                || frame.argbPixelAt(bottom, left) != WHITE
                || frame.argbPixelAt(bottom, right) != WHITE
                || frame.argbPixelAt(top + border, left + border) != BLACK
                || !hasExactSyncBandSample(frame, profile, layoutPlan, left, top)
                || !hasExactTileSlotGridSample(frame, profile, layoutPlan, left, top)) {
            return false;
        }
        return hasExactWhiteOuterBorder(frame, profile, left, top, border)
                && hasExactSyncBand(frame, profile, layoutPlan, left, top)
                && hasExactTileSlotGridGeometry(frame, profile, layoutPlan, left, top);
    }

    private boolean hasExactWhiteOuterBorder(
            MediaInputFrame frame,
            LayoutProfile profile,
            int left,
            int top,
            int border
    ) {
        int width = profile.frameWidthPx();
        int height = profile.frameHeightPx();
        for (int row = 0; row < border; row++) {
            if (!rowIsWhite(frame, top + row, left, width)
                    || !rowIsWhite(frame, top + height - border + row, left, width)) {
                return false;
            }
        }
        for (int row = border; row < height - border; row++) {
            if (!columnsAreWhite(frame, top + row, left, width, border)) {
                return false;
            }
        }
        return true;
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

    private boolean hasExactSyncBand(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        int bandLeft = profile.outerMarginPx();
        int bandTop = profile.outerMarginPx();
        int bandRightExclusive = profile.frameWidthPx() - profile.outerMarginPx();
        int bandBottomExclusive = bandTop + profile.topSyncBandPx();
        int cellWidth = syncCellWidth(layoutPlan);
        for (int row = bandTop; row < bandBottomExclusive; row++) {
            for (int col = bandLeft; col < bandRightExclusive; col++) {
                if (frame.argbPixelAt(top + row, left + col) != syncBandColor(profile, cellWidth, col)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int syncCellWidth(FixedLayoutPlan layoutPlan) {
        return Math.max(8, layoutPlan.separatorThicknessPx() * 2);
    }

    private int syncBandColor(LayoutProfile profile, int cellWidth, int relativeX) {
        int segmentIndex = (relativeX - profile.outerMarginPx()) / cellWidth;
        return segmentIndex % 2 == 0 ? WHITE : BLACK;
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

    private boolean hasExactTileSlotGridGeometry(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        boolean checkedGridEvidence = false;
        int gridBottomExclusive = gridBottomExclusive(profile, layoutPlan);
        for (int col = 0; col < profile.cols() - 1; col++) {
            TilePlacement leftPlacement = layoutPlan.tilePlacements().get(col);
            TilePlacement rightPlacement = layoutPlan.tilePlacements().get(col + 1);
            if (!rectangleIsWhite(
                    frame,
                    left + leftPlacement.xPx() + leftPlacement.widthPx(),
                    top + layoutPlan.gridOriginYPx(),
                    left + rightPlacement.xPx(),
                    top + gridBottomExclusive
            )) {
                return false;
            }
            checkedGridEvidence = true;
        }
        int gridRightExclusive = gridRightExclusive(profile, layoutPlan);
        for (int row = 0; row < profile.rows() - 1; row++) {
            TilePlacement upperPlacement = layoutPlan.tilePlacements().get(row * profile.cols());
            TilePlacement lowerPlacement = layoutPlan.tilePlacements().get((row + 1) * profile.cols());
            if (!rectangleIsWhite(
                    frame,
                    left + layoutPlan.gridOriginXPx(),
                    top + upperPlacement.yPx() + upperPlacement.heightPx(),
                    left + gridRightExclusive,
                    top + lowerPlacement.yPx()
            )) {
                return false;
            }
            checkedGridEvidence = true;
        }
        return checkedGridEvidence;
    }

    private int gridRightExclusive(LayoutProfile profile, FixedLayoutPlan layoutPlan) {
        return layoutPlan.gridOriginXPx()
                + (profile.cols() * layoutPlan.tileSlotWidthPx())
                + ((profile.cols() - 1) * profile.tileGapPx());
    }

    private int gridBottomExclusive(LayoutProfile profile, FixedLayoutPlan layoutPlan) {
        return layoutPlan.gridOriginYPx()
                + (profile.rows() * layoutPlan.tileSlotHeightPx())
                + ((profile.rows() - 1) * profile.tileGapPx());
    }

    private boolean rectangleIsWhite(
            MediaInputFrame frame,
            int left,
            int top,
            int rightExclusive,
            int bottomExclusive
    ) {
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = left; col < rightExclusive; col++) {
                if (frame.argbPixelAt(row, col) != WHITE) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean rowIsWhite(MediaInputFrame frame, int row, int left, int width) {
        for (int col = 0; col < width; col++) {
            if (frame.argbPixelAt(row, left + col) != WHITE) {
                return false;
            }
        }
        return true;
    }

    private boolean columnsAreWhite(MediaInputFrame frame, int row, int left, int width, int border) {
        for (int col = 0; col < border; col++) {
            if (frame.argbPixelAt(row, left + col) != WHITE
                    || frame.argbPixelAt(row, left + width - border + col) != WHITE) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPartialAxisAlignedFrameEvidence(MediaInputFrame frame) {
        for (LayoutProfile profile : layoutCatalog.profiles()) {
            FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
            if (hasPartialAxisAlignedFrameEvidence(frame, profile, layoutPlan)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPartialAxisAlignedFrameEvidence(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        int minVisibleWidth = Math.max(1, profile.frameWidthPx() / 3);
        int minVisibleHeight = Math.max(1, profile.frameHeightPx() / 3);
        int minLeft = -profile.frameWidthPx() + minVisibleWidth;
        int maxLeft = frame.widthPixels() - minVisibleWidth;
        int minTop = -profile.frameHeightPx() + minVisibleHeight;
        int maxTop = frame.heightPixels() - minVisibleHeight;
        if (minLeft > maxLeft || minTop > maxTop) {
            return false;
        }
        for (int top = minTop; top <= maxTop; top++) {
            for (int left = minLeft; left <= maxLeft; left++) {
                if (candidateFullyInside(frame, profile, left, top)) {
                    continue;
                }
                if (hasPartialRenderedFrameEvidence(frame, profile, layoutPlan, left, top)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean candidateFullyInside(MediaInputFrame frame, LayoutProfile profile, int left, int top) {
        return left >= 0
                && top >= 0
                && left + profile.frameWidthPx() <= frame.widthPixels()
                && top + profile.frameHeightPx() <= frame.heightPixels();
    }

    private boolean hasPartialRenderedFrameEvidence(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        return hasPartialSyncBandEvidence(frame, profile, layoutPlan, left, top)
                && hasPartialTileSlotGridEvidence(frame, profile, layoutPlan, left, top)
                && hasPartialOuterBorderEvidence(frame, profile, layoutPlan, left, top);
    }

    private boolean hasPartialSyncBandEvidence(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        int relativeStartX = Math.max(profile.outerMarginPx(), -left);
        int relativeEndX = Math.min(profile.frameWidthPx() - profile.outerMarginPx(), frame.widthPixels() - left);
        int relativeStartY = Math.max(profile.outerMarginPx(), -top);
        int relativeEndY = Math.min(profile.outerMarginPx() + profile.topSyncBandPx(), frame.heightPixels() - top);
        if (relativeStartX >= relativeEndX || relativeStartY >= relativeEndY) {
            return false;
        }
        int cellWidth = syncCellWidth(layoutPlan);
        int samples = 0;
        int relativeY = relativeStartY + ((relativeEndY - relativeStartY) / 2);
        for (int relativeX = relativeStartX; relativeX < relativeEndX; relativeX += cellWidth) {
            if (frame.argbPixelAt(top + relativeY, left + relativeX)
                    != syncBandColor(profile, cellWidth, relativeX)) {
                return false;
            }
            samples++;
        }
        return samples >= MIN_PARTIAL_SYNC_SAMPLES;
    }

    private boolean hasPartialTileSlotGridEvidence(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        Optional<GridSample> sample = firstGridSample(profile, layoutPlan);
        if (sample.isEmpty()) {
            return false;
        }
        int absoluteX = left + sample.get().relativeX();
        int absoluteY = top + sample.get().relativeY();
        return absoluteX >= 0
                && absoluteX < frame.widthPixels()
                && absoluteY >= 0
                && absoluteY < frame.heightPixels()
                && frame.argbPixelAt(absoluteY, absoluteX) == WHITE;
    }

    private boolean hasPartialOuterBorderEvidence(
            MediaInputFrame frame,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int left,
            int top
    ) {
        int border = layoutPlan.separatorThicknessPx();
        return visibleRectangleHasColorSamples(frame, left, top, 0, 0, profile.frameWidthPx(), border, WHITE)
                || visibleRectangleHasColorSamples(
                        frame,
                        left,
                        top,
                        0,
                        profile.frameHeightPx() - border,
                        profile.frameWidthPx(),
                        profile.frameHeightPx(),
                        WHITE
                )
                || visibleRectangleHasColorSamples(frame, left, top, 0, 0, border, profile.frameHeightPx(), WHITE)
                || visibleRectangleHasColorSamples(
                        frame,
                        left,
                        top,
                        profile.frameWidthPx() - border,
                        0,
                        profile.frameWidthPx(),
                        profile.frameHeightPx(),
                        WHITE
                );
    }

    private boolean visibleRectangleHasColorSamples(
            MediaInputFrame frame,
            int candidateLeft,
            int candidateTop,
            int relativeLeft,
            int relativeTop,
            int relativeRightExclusive,
            int relativeBottomExclusive,
            int expectedColor
    ) {
        int startX = Math.max(relativeLeft, -candidateLeft);
        int endX = Math.min(relativeRightExclusive, frame.widthPixels() - candidateLeft);
        int startY = Math.max(relativeTop, -candidateTop);
        int endY = Math.min(relativeBottomExclusive, frame.heightPixels() - candidateTop);
        if (startX >= endX || startY >= endY) {
            return false;
        }
        int middleX = startX + ((endX - startX) / 2);
        int middleY = startY + ((endY - startY) / 2);
        return frame.argbPixelAt(candidateTop + startY, candidateLeft + startX) == expectedColor
                && frame.argbPixelAt(candidateTop + middleY, candidateLeft + middleX) == expectedColor
                && frame.argbPixelAt(candidateTop + endY - 1, candidateLeft + endX - 1) == expectedColor;
    }

    private double frameCoverageRatio(MediaInputFrame frame, LayoutProfile profile) {
        return ((double) profile.frameWidthPx() * profile.frameHeightPx())
                / ((double) frame.widthPixels() * frame.heightPixels());
    }

    private enum RegionDetectionStatus {
        ACCEPTED,
        TOO_SMALL,
        PARTIAL,
        AMBIGUOUS,
        NOT_FOUND
    }

    private record RegionDetection(RegionDetectionStatus status, Optional<DetectedInset> inset) {

        private static RegionDetection accepted(DetectedInset inset) {
            return new RegionDetection(RegionDetectionStatus.ACCEPTED, Optional.of(inset));
        }

        private static RegionDetection tooSmall() {
            return new RegionDetection(RegionDetectionStatus.TOO_SMALL, Optional.empty());
        }

        private static RegionDetection partial() {
            return new RegionDetection(RegionDetectionStatus.PARTIAL, Optional.empty());
        }

        private static RegionDetection ambiguous() {
            return new RegionDetection(RegionDetectionStatus.AMBIGUOUS, Optional.empty());
        }

        private static RegionDetection notFound() {
            return new RegionDetection(RegionDetectionStatus.NOT_FOUND, Optional.empty());
        }
    }

    private record DetectedInset(LayoutProfile profile, int leftPx, int topPx) {
    }

    private record GridSample(int relativeX, int relativeY) {
    }
}
