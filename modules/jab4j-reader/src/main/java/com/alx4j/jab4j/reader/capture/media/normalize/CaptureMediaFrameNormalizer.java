package com.alx4j.jab4j.reader.capture.media.normalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticSeverity;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackends;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvFrameCandidate;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.cv.PerspectiveTransform;
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
    private static final double MIN_SYNC_BAND_CONTRAST_SCORE = 0.30d;
    private static final double MAX_GLARE_NEAR_WHITE_RATIO = 0.96d;
    private static final int GLARE_SAMPLE_STRIDE_PX = 4;
    private static final int MIN_PARTIAL_SYNC_SAMPLES = 4;
    private static final int MAX_JAB_CANDIDATES_TO_NORMALIZE = 3;
    private static final double MAX_RGB_DISTANCE = Math.sqrt(3.0d * 255.0d * 255.0d);

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final CaptureMediaCvBackend cvBackend;

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
     * Creates a normalizer with an explicit CV backend for non-exact media normalization.
     *
     * @param cvBackend backend-neutral CV backend
     */
    public CaptureMediaFrameNormalizer(CaptureMediaCvBackend cvBackend) {
        this(new CaptureRenderedLayoutCatalog(), new FixedLayoutPlanner(), cvBackend);
    }

    /**
     * Creates a normalizer with explicit rendered layouts and a CV backend.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param cvBackend backend-neutral CV backend
     */
    public CaptureMediaFrameNormalizer(
            CaptureRenderedLayoutCatalog layoutCatalog,
            CaptureMediaCvBackend cvBackend
    ) {
        this(layoutCatalog, new FixedLayoutPlanner(), cvBackend);
    }

    /**
     * Creates a normalizer with explicit rendered-layout collaborators.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for rendered-frame signatures
     */
    public CaptureMediaFrameNormalizer(CaptureRenderedLayoutCatalog layoutCatalog, FixedLayoutPlanner layoutPlanner) {
        this(layoutCatalog, layoutPlanner, CaptureMediaCvBackends.configuredOrLegacy(layoutCatalog, layoutPlanner));
    }

    /**
     * Creates a normalizer with explicit rendered-layout collaborators and a CV backend.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for rendered-frame signatures
     * @param cvBackend backend-neutral CV backend
     */
    public CaptureMediaFrameNormalizer(
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            CaptureMediaCvBackend cvBackend
    ) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.cvBackend = Objects.requireNonNull(cvBackend, "cvBackend must not be null");
    }

    /**
     * Returns the selected backend identifier without exposing internal CV DTOs to caller packages.
     *
     * @return backend identifier
     */
    public String normalizationBackendId() {
        return cvBackend.identity().backendId();
    }

    /**
     * Returns the selected backend implementation version when available.
     *
     * @return optional backend implementation version
     */
    public Optional<String> normalizationBackendVersion() {
        return cvBackend.identity().implementationVersion();
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
                .map(profile -> normalizeExactRenderedDimensions(frame, profile))
                .orElseGet(() -> normalizeAxisAlignedInset(frame));
    }

    private MediaNormalizationResult normalizeExactRenderedDimensions(MediaInputFrame frame, LayoutProfile profile) {
        Optional<CaptureMediaDiagnostic> qualityDiagnostic = severeQualityDiagnostic(frame, profile);
        return qualityDiagnostic
                .map(MediaNormalizationResult::rejected)
                .orElseGet(() -> accepted(frame, profile));
    }

    private MediaNormalizationResult accepted(MediaInputFrame frame, LayoutProfile profile) {
        return MediaNormalizationResult.accepted(NormalizedCaptureFrame.fromExactRenderedFrame(frame, profile));
    }

    private MediaNormalizationResult rejected(
            MediaInputFrame frame,
            CaptureMediaDiagnosticCode code,
            String message
    ) {
        return rejected(frame, code, Map.of(), message);
    }

    private MediaNormalizationResult rejected(
            MediaInputFrame frame,
            CaptureMediaDiagnosticCode code,
            Map<String, Double> metrics,
            String message
    ) {
        return MediaNormalizationResult.rejected(new CaptureMediaDiagnostic(
                code,
                CaptureMediaDiagnosticSeverity.ERROR,
                true,
                Optional.of(frame.sourceKind()),
                Optional.of(frame.sourceId()),
                Optional.of(frame.callerOrder()),
                Optional.empty(),
                Optional.empty(),
                metrics,
                message
        ));
    }

    private Optional<CaptureMediaDiagnostic> severeQualityDiagnostic(MediaInputFrame frame, LayoutProfile profile) {
        double glareScore = glareScore(frame);
        if (glareScore > MAX_GLARE_NEAR_WHITE_RATIO) {
            return Optional.of(qualityDiagnostic(
                    frame,
                    CaptureMediaDiagnosticCode.GLARE_OR_OVEREXPOSURE,
                    Map.of("glareScore", glareScore),
                    "Capture media frame is overexposed enough that tile contrast is unreliable"
            ));
        }

        double blurScore = blurScore(frame, profile);
        if (blurScore > 1.0d - MIN_SYNC_BAND_CONTRAST_SCORE) {
            return Optional.of(qualityDiagnostic(
                    frame,
                    CaptureMediaDiagnosticCode.BLUR,
                    Map.of("blurScore", blurScore),
                    "Capture media frame sync-band contrast is below the supported blur threshold"
            ));
        }
        return Optional.empty();
    }

    private CaptureMediaDiagnostic qualityDiagnostic(
            MediaInputFrame frame,
            CaptureMediaDiagnosticCode code,
            Map<String, Double> metrics,
            String message
    ) {
        return new CaptureMediaDiagnostic(
                code,
                CaptureMediaDiagnosticSeverity.ERROR,
                true,
                Optional.of(frame.sourceKind()),
                Optional.of(frame.sourceId()),
                Optional.of(frame.callerOrder()),
                Optional.empty(),
                Optional.empty(),
                metrics,
                message
        );
    }

    private double glareScore(MediaInputFrame frame) {
        int sampledPixels = 0;
        int nearWhitePixels = 0;
        for (int row = 0; row < frame.heightPixels(); row += GLARE_SAMPLE_STRIDE_PX) {
            for (int col = 0; col < frame.widthPixels(); col += GLARE_SAMPLE_STRIDE_PX) {
                sampledPixels++;
                if (nearWhite(frame.argbPixelAt(row, col))) {
                    nearWhitePixels++;
                }
            }
        }
        return sampledPixels == 0 ? 0.0d : (double) nearWhitePixels / sampledPixels;
    }

    private boolean nearWhite(int argb) {
        return red(argb) >= 245 && green(argb) >= 245 && blue(argb) >= 245;
    }

    private double blurScore(MediaInputFrame frame, LayoutProfile profile) {
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
        int cellWidth = syncCellWidth(layoutPlan);
        int row = profile.outerMarginPx() + (profile.topSyncBandPx() / 2);
        int startX = profile.outerMarginPx() + (cellWidth / 2);
        int endX = profile.frameWidthPx() - profile.outerMarginPx();
        if (row < 0 || row >= frame.heightPixels() || startX >= endX) {
            return 1.0d;
        }

        int previous = frame.argbPixelAt(row, startX);
        int minimumLuminance = luminance(previous);
        int maximumLuminance = minimumLuminance;
        double totalContrast = 0.0d;
        int transitionCount = 0;
        for (int x = startX + cellWidth; x < endX; x += cellWidth) {
            int current = frame.argbPixelAt(row, x);
            int luminance = luminance(current);
            minimumLuminance = Math.min(minimumLuminance, luminance);
            maximumLuminance = Math.max(maximumLuminance, luminance);
            totalContrast += rgbDistance(previous, current) / MAX_RGB_DISTANCE;
            previous = current;
            transitionCount++;
        }
        if (transitionCount == 0) {
            return 1.0d;
        }
        if (maximumLuminance - minimumLuminance < 40) {
            return 0.0d;
        }
        double averageContrast = totalContrast / transitionCount;
        return 1.0d - Math.min(1.0d, averageContrast);
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

    private int luminance(int argb) {
        return (int) Math.round((0.2126d * red(argb)) + (0.7152d * green(argb)) + (0.0722d * blue(argb)));
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
            case NOT_FOUND -> normalizeNonExactRegion(frame);
        };
    }

    private MediaNormalizationResult normalizeNonExactRegion(MediaInputFrame frame) {
        return normalizeWithCvBackend(frame);
    }

    private MediaNormalizationResult normalizeWithCvBackend(MediaInputFrame frame) {
        CvDetectionResult result;
        try {
            result = Objects.requireNonNull(
                    cvBackend.detect(frame),
                    "CV backend result must not be null"
            );
        } catch (RuntimeException exception) {
            return rejected(
                    frame,
                    CaptureMediaDiagnosticCode.UNREADABLE_MEDIA,
                    "Capture-media CV backend failed while evaluating the frame"
            );
        }
        if (isScreenOrFrameNotFound(result) && hasPartialAxisAlignedFrameEvidence(frame)) {
            return rejected(
                    frame,
                    CaptureMediaDiagnosticCode.FRAME_PARTIALLY_OUTSIDE_IMAGE,
                    "Media normalization found partial generated frame evidence at the image boundary"
            );
        }
        return normalizeCvDetectionResult(frame, result);
    }

    private boolean isScreenOrFrameNotFound(CvDetectionResult result) {
        return result.diagnosticCode()
                .filter(code -> code == CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND)
                .isPresent();
    }

    private MediaNormalizationResult normalizeCvDetectionResult(MediaInputFrame frame, CvDetectionResult result) {
        return switch (result.status()) {
            case ACCEPTED -> normalizeAcceptedCvDetectionResult(frame, result);
            case REJECTED, TOO_SMALL, AMBIGUOUS, BACKEND_FAILURE -> rejected(
                    frame,
                    result.diagnosticCode().orElseThrow(),
                    result.diagnosticMetrics(),
                    result.message()
            );
        };
    }

    private MediaNormalizationResult normalizeAcceptedCvDetectionResult(
            MediaInputFrame frame,
            CvDetectionResult result
    ) {
        if (!result.normalizedFrames().isEmpty()) {
            return MediaNormalizationResult.accepted(result.normalizedFrames().stream()
                    .map(normalizedFrame -> normalizeCvNormalizedFrame(frame, normalizedFrame))
                    .toList());
        }
        return normalizeCvFrameCandidates(frame, result.candidates());
    }

    private NormalizedCaptureFrame normalizeCvNormalizedFrame(MediaInputFrame frame, CvNormalizedFrame normalizedFrame) {
        LayoutProfile profile = normalizedFrame.layoutProfile();
        return new NormalizedCaptureFrame(
                frame.sourceId(),
                frame.sourceKind(),
                frame.callerOrder(),
                frame.widthPixels(),
                frame.heightPixels(),
                profile.frameWidthPx(),
                profile.frameHeightPx(),
                frame.formatName(),
                frame.pixelSha256(),
                profile.profileId(),
                frame.timestampMillis(),
                frame.frameNumber(),
                normalizedFrame.frameCorners(),
                normalizedFrame.qualityMetrics(),
                normalizedFrame.argbPixels()
        );
    }

    private MediaNormalizationResult normalizeCvFrameCandidates(MediaInputFrame frame, List<CvFrameCandidate> candidates) {
        List<NormalizedCaptureFrame> normalizedCandidates = new ArrayList<>();
        for (CvFrameCandidate candidate : candidates.stream()
                .limit(MAX_JAB_CANDIDATES_TO_NORMALIZE)
                .toList()) {
            normalizeCvFrameCandidate(frame, candidate)
                    .frame()
                    .ifPresent(normalizedCandidates::add);
        }
        if (normalizedCandidates.isEmpty()) {
            return rejected(
                    frame,
                    CaptureMediaDiagnosticCode.PERSPECTIVE_TOO_SEVERE,
                    "Detected JAB frame region perspective is not invertible"
            );
        }
        return MediaNormalizationResult.accepted(normalizedCandidates);
    }

    private MediaNormalizationResult normalizeCvFrameCandidate(MediaInputFrame frame, CvFrameCandidate candidate) {
        PerspectiveTransform transform;
        try {
            transform = PerspectiveTransform.fromUnitSquareTo(candidate.frameCorners());
        } catch (IllegalArgumentException exception) {
            return rejected(
                    frame,
                    CaptureMediaDiagnosticCode.PERSPECTIVE_TOO_SEVERE,
                    candidate.score().metrics(),
                    "Detected JAB frame region perspective is not invertible"
            );
        }

        int[] correctedPixels = resamplePerspective(frame, candidate.layoutProfile(), transform);
        return MediaNormalizationResult.accepted(NormalizedCaptureFrame.fromPerspectiveCorrectedFrame(
                frame,
                candidate.layoutProfile(),
                candidate.frameCorners(),
                candidate.score().frameCoverageRatio(),
                candidate.score().skewScore(),
                correctedPixels
        ));
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
            if (rowHasNonWhite(frame, top + row, left, width)
                    || rowHasNonWhite(frame, top + height - border + row, left, width)) {
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
            if (rectangleHasNonWhite(
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
            if (rectangleHasNonWhite(
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

    private boolean rectangleHasNonWhite(
            MediaInputFrame frame,
            int left,
            int top,
            int rightExclusive,
            int bottomExclusive
    ) {
        for (int row = top; row < bottomExclusive; row++) {
            for (int col = left; col < rightExclusive; col++) {
                if (frame.argbPixelAt(row, col) != WHITE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean rowHasNonWhite(MediaInputFrame frame, int row, int left, int width) {
        for (int col = 0; col < width; col++) {
            if (frame.argbPixelAt(row, left + col) != WHITE) {
                return true;
            }
        }
        return false;
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
        int sourceX = clampToUpperBound((int) Math.round(x), frame.widthPixels() - 1);
        int sourceY = clampToUpperBound((int) Math.round(y), frame.heightPixels() - 1);
        return frame.argbPixelAt(sourceY, sourceX);
    }

    private int clampToUpperBound(int value, int max) {
        return Math.max(0, Math.min(max, value));
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
        return visibleRectangleHasWhiteSamples(frame, left, top, 0, 0, profile.frameWidthPx(), border)
                || visibleRectangleHasWhiteSamples(
                        frame,
                        left,
                        top,
                        0,
                        profile.frameHeightPx() - border,
                        profile.frameWidthPx(),
                        profile.frameHeightPx()
                )
                || visibleRectangleHasWhiteSamples(frame, left, top, 0, 0, border, profile.frameHeightPx())
                || visibleRectangleHasWhiteSamples(
                        frame,
                        left,
                        top,
                        profile.frameWidthPx() - border,
                        0,
                        profile.frameWidthPx(),
                        profile.frameHeightPx()
                );
    }

    private boolean visibleRectangleHasWhiteSamples(
            MediaInputFrame frame,
            int candidateLeft,
            int candidateTop,
            int relativeLeft,
            int relativeTop,
            int relativeRightExclusive,
            int relativeBottomExclusive
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
        return frame.argbPixelAt(candidateTop + startY, candidateLeft + startX) == WHITE
                && frame.argbPixelAt(candidateTop + middleY, candidateLeft + middleX) == WHITE
                && frame.argbPixelAt(candidateTop + endY - 1, candidateLeft + endX - 1) == WHITE;
    }

    private double frameCoverageRatio(MediaInputFrame frame, LayoutProfile profile) {
        return ((double) profile.frameWidthPx() * profile.frameHeightPx())
                / ((double) frame.widthPixels() * frame.heightPixels());
    }

    private enum RegionDetectionStatus {
        ACCEPTED,
        TOO_SMALL,
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
