package com.alx4j.jab4j.reader.capture.media.normalize;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;

/**
 * Bounded source-space detector for JAB frame regions inside camera-like still images.
 *
 * <p>The detector intentionally scores JAB-rendered evidence rather than generic monitor geometry. Candidate generation
 * starts from downsampled bright components, then scoring checks the rendered border, quiet zone, top sync band, and
 * tile-slot grid before a candidate can be selected.</p>
 */
final class JabFrameRegionDetector {

    private static final int MAX_SCAN_DIMENSION_PX = 960;
    private static final int MAX_COMPONENTS_TO_SCORE = 64;
    private static final int MAX_SYNC_SAMPLES = 48;
    private static final int MIN_LIGHT_COMPONENT_PIXELS = 24;
    private static final int MIN_SOURCE_CANDIDATE_SHORT_EDGE_PX = 120;
    private static final int MIN_SYNC_RUN_WIDTH_SOURCE_PX = 4;
    private static final int MAX_SYNC_RUN_WIDTH_SOURCE_PX = 48;
    private static final int MAX_SYNC_RUN_GAP_SOURCE_PX = 48;
    private static final int MIN_SYNC_ROW_RUN_COUNT = 16;
    private static final int MIN_SYNC_BAND_SPAN_SOURCE_PX = 280;
    private static final int MIN_SYNC_BAND_HEIGHT_SOURCE_PX = 10;
    private static final int SYNC_ROW_ALIGNMENT_TOLERANCE_SOURCE_PX = 48;
    private static final int LIGHT_LUMINANCE_THRESHOLD = 178;
    private static final int LIGHT_CONFIDENCE_FLOOR = 160;
    private static final int DARK_CONFIDENCE_CEILING = 125;
    private static final int REQUIRED_CONTRAST_DELTA = 44;
    private static final double MIN_SYNC_ROW_FILL_RATIO = 0.25d;
    private static final double MAX_SYNC_ROW_FILL_RATIO = 0.72d;
    private static final double MAX_ASPECT_RATIO_ERROR = 0.12d;
    private static final double MIN_FRAME_COVERAGE_RATIO = 0.20d;
    private static final double MIN_TOTAL_SCORE = 0.76d;
    private static final double MIN_BORDER_SCORE = 0.58d;
    private static final double MIN_SYNC_SCORE = 0.72d;
    private static final double MIN_GRID_SCORE = 0.58d;
    private static final double MIN_ASPECT_SCORE = 0.80d;
    private static final double MIN_CAMERA_TOTAL_SCORE = 0.45d;
    private static final double MIN_CAMERA_BORDER_SCORE = 0.08d;
    private static final double MIN_CAMERA_SYNC_SCORE = 0.48d;
    private static final double MIN_CAMERA_GRID_SCORE = 0.36d;
    private static final double MIN_CAMERA_PALETTE_CONFIDENCE = 0.49d;
    private static final double SAME_REGION_IOU = 0.80d;
    private static final int EDGE_SAMPLE_COUNT = 19;
    private static final int MIN_EDGE_POINTS = 5;
    private static final double EDGE_SCAN_WINDOW_RATIO = 0.24d;
    private static final double MIN_EDGE_POINT_SCORE = 0.52d;
    private static final double MAX_REFINED_ASPECT_ERROR = 0.24d;

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;

    /**
     * Creates a detector for the supplied supported layout catalog.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for JAB geometry
     */
    JabFrameRegionDetector(CaptureRenderedLayoutCatalog layoutCatalog, FixedLayoutPlanner layoutPlanner) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
    }

    /**
     * Detects ranked source-space JAB frame candidates.
     *
     * @param frame decoded media input frame
     * @return detection result with ranked candidates and conservative status
     */
    JabFrameDetectionResult detect(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        DownsampledLuminance luminance = downsample(frame);
        List<SourceRegion> regions = candidateRegions(frame, luminance).stream()
                .sorted(Comparator.comparingInt(SourceRegion::areaPx).reversed())
                .limit(MAX_COMPONENTS_TO_SCORE)
                .toList();

        List<JabFrameCandidate> scoredCandidates = scoreRegions(frame, regions);
        if (scoredCandidates.isEmpty()) {
            return JabFrameDetectionResult.notFound(List.of());
        }

        List<JabFrameCandidate> evidenceCandidates = distinctRegions(scoredCandidates.stream()
                .filter(this::hasMinimumJabEvidence)
                .toList());
        if (evidenceCandidates.isEmpty()) {
            return JabFrameDetectionResult.notFound(scoredCandidates);
        }

        List<JabFrameCandidate> acceptedCandidates = evidenceCandidates.stream()
                .filter(candidate -> candidate.score().frameCoverageRatio() >= MIN_FRAME_COVERAGE_RATIO)
                .toList();
        if (acceptedCandidates.isEmpty()) {
            return JabFrameDetectionResult.tooSmall(evidenceCandidates);
        }
        return JabFrameDetectionResult.accepted(acceptedCandidates);
    }

    private List<SourceRegion> candidateRegions(MediaInputFrame frame, DownsampledLuminance luminance) {
        List<SourceRegion> regions = new ArrayList<>();
        regions.addAll(findLightComponentRegions(frame, luminance));
        regions.addAll(findSyncBandCandidateRegions(frame, luminance));
        return regions.stream().distinct().toList();
    }

    private DownsampledLuminance downsample(MediaInputFrame frame) {
        int maxDimension = Math.max(frame.widthPixels(), frame.heightPixels());
        int stride = Math.max(1, (maxDimension + MAX_SCAN_DIMENSION_PX - 1) / MAX_SCAN_DIMENSION_PX);
        int width = (frame.widthPixels() + stride - 1) / stride;
        int height = (frame.heightPixels() + stride - 1) / stride;
        int[] luminance = new int[width * height];
        boolean[] light = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(frame.heightPixels() - 1, (y * stride) + (stride / 2));
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(frame.widthPixels() - 1, (x * stride) + (stride / 2));
                int value = luminance(frame.argbPixelAt(sourceY, sourceX));
                luminance[(y * width) + x] = value;
                light[(y * width) + x] = value >= LIGHT_LUMINANCE_THRESHOLD;
            }
        }
        return new DownsampledLuminance(width, height, stride, luminance, light);
    }

    private List<SourceRegion> findLightComponentRegions(MediaInputFrame frame, DownsampledLuminance image) {
        boolean[] visited = new boolean[image.width() * image.height()];
        List<SourceRegion> regions = new ArrayList<>();
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int index = (y * image.width()) + x;
                if (visited[index] || !image.light()[index]) {
                    continue;
                }
                Component component = collectComponent(image, visited, x, y);
                if (component.pixelCount() < MIN_LIGHT_COMPONENT_PIXELS) {
                    continue;
                }
                SourceRegion region = toSourceRegion(frame, image.stride(), component);
                if (region.shortEdgePx() < MIN_SOURCE_CANDIDATE_SHORT_EDGE_PX
                        || !matchesSupportedAspect(region)) {
                    continue;
                }
                regions.add(region);
            }
        }
        return regions;
    }

    private List<SourceRegion> findSyncBandCandidateRegions(MediaInputFrame frame, DownsampledLuminance image) {
        List<SyncBandEvidence> syncBands = syncBands(image);
        List<SourceRegion> regions = new ArrayList<>();
        for (SyncBandEvidence syncBand : syncBands) {
            if (syncBand.heightPx() < MIN_SYNC_BAND_HEIGHT_SOURCE_PX
                    || syncBand.spanPx() < MIN_SYNC_BAND_SPAN_SOURCE_PX) {
                continue;
            }
            for (LayoutProfile profile : layoutCatalog.profiles()) {
                regions.addAll(toSourceRegions(frame, profile, syncBand));
            }
        }
        return regions;
    }

    private List<SyncBandEvidence> syncBands(DownsampledLuminance image) {
        List<SyncBandEvidence> bands = new ArrayList<>();
        for (int y = 0; y < image.height(); y++) {
            bestSyncRowEvidence(image, y).ifPresent(row -> mergeSyncRow(bands, row));
        }
        return List.copyOf(bands);
    }

    private Optional<SyncRowEvidence> bestSyncRowEvidence(DownsampledLuminance image, int y) {
        List<LightRun> runs = lightRuns(image, y).stream()
                .filter(run -> run.widthPx(image.stride()) >= MIN_SYNC_RUN_WIDTH_SOURCE_PX)
                .filter(run -> run.widthPx(image.stride()) <= MAX_SYNC_RUN_WIDTH_SOURCE_PX)
                .toList();
        SyncRowEvidence best = null;
        int groupStartX = -1;
        int groupEndExclusiveX = -1;
        int groupRunCount = 0;
        int groupLightWidth = 0;
        for (LightRun run : runs) {
            if (groupRunCount == 0) {
                groupStartX = run.startX();
                groupEndExclusiveX = run.endExclusiveX();
                groupRunCount = 1;
                groupLightWidth = run.widthPx(image.stride());
                continue;
            }
            int gapPx = (run.startX() - groupEndExclusiveX) * image.stride();
            if (gapPx <= MAX_SYNC_RUN_GAP_SOURCE_PX) {
                groupEndExclusiveX = run.endExclusiveX();
                groupRunCount++;
                groupLightWidth += run.widthPx(image.stride());
                continue;
            }
            best = betterSyncRow(best, syncRowEvidence(
                    image,
                    y,
                    groupStartX,
                    groupEndExclusiveX,
                    groupRunCount,
                    groupLightWidth
            ).orElse(null));
            groupStartX = run.startX();
            groupEndExclusiveX = run.endExclusiveX();
            groupRunCount = 1;
            groupLightWidth = run.widthPx(image.stride());
        }
        best = betterSyncRow(best, syncRowEvidence(
                image,
                y,
                groupStartX,
                groupEndExclusiveX,
                groupRunCount,
                groupLightWidth
        ).orElse(null));
        return Optional.ofNullable(best);
    }

    private List<LightRun> lightRuns(DownsampledLuminance image, int y) {
        List<LightRun> runs = new ArrayList<>();
        int startX = -1;
        for (int x = 0; x < image.width(); x++) {
            boolean light = image.light()[(y * image.width()) + x];
            if (light && startX < 0) {
                startX = x;
            } else if (!light && startX >= 0) {
                runs.add(new LightRun(startX, x));
                startX = -1;
            }
        }
        if (startX >= 0) {
            runs.add(new LightRun(startX, image.width()));
        }
        return List.copyOf(runs);
    }

    private Optional<SyncRowEvidence> syncRowEvidence(
            DownsampledLuminance image,
            int y,
            int startX,
            int endExclusiveX,
            int runCount,
            int lightWidthPx
    ) {
        if (runCount < MIN_SYNC_ROW_RUN_COUNT || startX < 0 || endExclusiveX <= startX) {
            return Optional.empty();
        }
        int leftPx = startX * image.stride();
        int rightExclusivePx = endExclusiveX * image.stride();
        int spanPx = rightExclusivePx - leftPx;
        if (spanPx < MIN_SYNC_BAND_SPAN_SOURCE_PX) {
            return Optional.empty();
        }
        double fillRatio = (double) lightWidthPx / spanPx;
        if (fillRatio < MIN_SYNC_ROW_FILL_RATIO || fillRatio > MAX_SYNC_ROW_FILL_RATIO) {
            return Optional.empty();
        }
        return Optional.of(new SyncRowEvidence(
                leftPx,
                rightExclusivePx,
                y * image.stride(),
                (y + 1) * image.stride(),
                runCount,
                fillRatio
        ));
    }

    private SyncRowEvidence betterSyncRow(SyncRowEvidence first, SyncRowEvidence second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        int firstStrength = first.runCount() * first.spanPx();
        int secondStrength = second.runCount() * second.spanPx();
        return secondStrength > firstStrength ? second : first;
    }

    private void mergeSyncRow(List<SyncBandEvidence> bands, SyncRowEvidence row) {
        if (!bands.isEmpty()) {
            int lastIndex = bands.size() - 1;
            SyncBandEvidence last = bands.get(lastIndex);
            if (last.alignedWith(row)) {
                bands.set(lastIndex, last.merge(row));
                return;
            }
        }
        bands.add(SyncBandEvidence.from(row));
    }

    private List<SourceRegion> toSourceRegions(
            MediaInputFrame frame,
            LayoutProfile profile,
            SyncBandEvidence syncBand
    ) {
        double syncSpan = profile.frameWidthPx() - (2.0d * profile.outerMarginPx());
        if (syncSpan <= 0.0d) {
            return List.of();
        }
        double scale = syncBand.spanPx() / syncSpan;
        List<SourceRegion> regions = new ArrayList<>();
        double[] syncYOffsets = {
                profile.outerMarginPx(),
                profile.outerMarginPx() + (profile.topSyncBandPx() / 2.0d),
                profile.outerMarginPx() + profile.topSyncBandPx()
        };
        for (double syncYOffset : syncYOffsets) {
            int left = (int) Math.round(syncBand.leftPx() - (profile.outerMarginPx() * scale));
            int top = (int) Math.round(syncBand.topPx() - (syncYOffset * scale));
            int right = (int) Math.round(syncBand.rightExclusivePx() + (profile.outerMarginPx() * scale));
            int bottom = (int) Math.round(top + (profile.frameHeightPx() * scale));
            int clampedLeft = clamp(left, 0, frame.widthPixels() - 1);
            int clampedTop = clamp(top, 0, frame.heightPixels() - 1);
            SourceRegion region = new SourceRegion(
                    clampedLeft,
                    clampedTop,
                    clamp(right, clampedLeft + 1, frame.widthPixels()),
                    clamp(bottom, clampedTop + 1, frame.heightPixels())
            );
            if (region.shortEdgePx() >= MIN_SOURCE_CANDIDATE_SHORT_EDGE_PX
                    && aspectScore(region, profile) >= MIN_ASPECT_SCORE) {
                regions.add(region);
            }
        }
        return List.copyOf(regions);
    }

    private Component collectComponent(DownsampledLuminance image, boolean[] visited, int startX, int startY) {
        ArrayDeque<Point> queue = new ArrayDeque<>();
        queue.add(new Point(startX, startY));
        visited[(startY * image.width()) + startX] = true;
        int minX = startX;
        int maxX = startX;
        int minY = startY;
        int maxY = startY;
        int pixelCount = 0;
        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            pixelCount++;
            minX = Math.min(minX, current.x());
            maxX = Math.max(maxX, current.x());
            minY = Math.min(minY, current.y());
            maxY = Math.max(maxY, current.y());
            addNeighbor(image, visited, queue, current.x() - 1, current.y());
            addNeighbor(image, visited, queue, current.x() + 1, current.y());
            addNeighbor(image, visited, queue, current.x(), current.y() - 1);
            addNeighbor(image, visited, queue, current.x(), current.y() + 1);
        }
        return new Component(minX, minY, maxX, maxY, pixelCount);
    }

    private void addNeighbor(
            DownsampledLuminance image,
            boolean[] visited,
            ArrayDeque<Point> queue,
            int x,
            int y
    ) {
        if (x < 0 || x >= image.width() || y < 0 || y >= image.height()) {
            return;
        }
        int index = (y * image.width()) + x;
        if (visited[index] || !image.light()[index]) {
            return;
        }
        visited[index] = true;
        queue.addLast(new Point(x, y));
    }

    private SourceRegion toSourceRegion(MediaInputFrame frame, int stride, Component component) {
        int left = clamp((component.minX() * stride) - stride, 0, frame.widthPixels() - 1);
        int top = clamp((component.minY() * stride) - stride, 0, frame.heightPixels() - 1);
        int right = clamp(((component.maxX() + 1) * stride) + stride, left + 1, frame.widthPixels());
        int bottom = clamp(((component.maxY() + 1) * stride) + stride, top + 1, frame.heightPixels());
        return new SourceRegion(left, top, right, bottom);
    }

    private boolean matchesSupportedAspect(SourceRegion region) {
        return layoutCatalog.profiles().stream()
                .anyMatch(profile -> aspectScore(region, profile) >= MIN_ASPECT_SCORE);
    }

    private List<JabFrameCandidate> scoreRegions(MediaInputFrame frame, List<SourceRegion> regions) {
        List<JabFrameCandidate> scoredCandidates = new ArrayList<>();
        for (SourceRegion region : regions) {
            for (LayoutProfile profile : layoutCatalog.profiles()) {
                double aspectScore = aspectScore(region, profile);
                if (aspectScore < MIN_ASPECT_SCORE) {
                    continue;
                }
                scoredCandidates.add(scoreRegion(frame, region, profile, aspectScore));
            }
        }
        scoredCandidates.sort(Comparator.comparingDouble((JabFrameCandidate candidate) ->
                candidate.score().totalScore()).reversed());
        return List.copyOf(scoredCandidates);
    }

    private JabFrameCandidate scoreRegion(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            double aspectScore
    ) {
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(profile);
        BorderScore borderScore = scoreBorderAndQuietZone(frame, region, profile, layoutPlan);
        SyncScore syncScore = scoreSyncBand(frame, region, profile, layoutPlan);
        EvidenceScore gridScore = scoreTileGrid(frame, region, profile, layoutPlan);
        FrameCorners corners = refineFrameCorners(frame, region, profile, layoutPlan)
                .orElseGet(() -> axisAlignedCorners(region));
        double coverageRatio = quadrilateralArea(corners)
                / ((double) frame.widthPixels() * frame.heightPixels());
        double skewScore = perspectiveSkewScore(corners);
        double totalScore = clampScore(
                (0.30d * borderScore.score())
                        + (0.30d * syncScore.score())
                        + (0.25d * gridScore.score())
                        + (0.15d * aspectScore)
        );
        double glareScore = glareScore(frame, region);
        double paletteDistanceConfidence = averageMeasured(
                borderScore.paletteDistanceConfidence(),
                syncScore.paletteDistanceConfidence(),
                gridScore.paletteDistanceConfidence()
        );
        JabFrameCandidateScore score = new JabFrameCandidateScore(
                totalScore,
                clampScore(coverageRatio),
                skewScore,
                borderScore.score(),
                syncScore.score(),
                gridScore.score(),
                aspectScore,
                1.0d - syncScore.contrastScore(),
                glareScore,
                paletteDistanceConfidence
        );
        return new JabFrameCandidate(
                profile,
                corners,
                region.leftPx(),
                region.topPx(),
                region.rightExclusivePx(),
                region.bottomExclusivePx(),
                score
        );
    }

    private BorderScore scoreBorderAndQuietZone(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        int border = layoutPlan.separatorThicknessPx();
        double borderCenter = Math.max(0.5d, border / 2.0d);
        double innerOffset = Math.min(profile.outerMarginPx() - 1.0d, border + Math.max(4.0d, border / 2.0d));
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        int samplesPerSide = 14;
        for (int index = 0; index < samplesPerSide; index++) {
            double ratio = (index + 0.5d) / samplesPerSide;
            double horizontal = profile.outerMarginPx()
                    + (ratio * (profile.frameWidthPx() - (2.0d * profile.outerMarginPx())));
            double vertical = profile.outerMarginPx()
                    + (ratio * (profile.frameHeightPx() - (2.0d * profile.outerMarginPx())));
            evidence.addContrastingPair(
                    sample(frame, region, profile, horizontal, borderCenter),
                    sample(frame, region, profile, horizontal, innerOffset)
            );
            evidence.addContrastingPair(
                    sample(frame, region, profile, horizontal, profile.frameHeightPx() - 1.0d - borderCenter),
                    sample(frame, region, profile, horizontal, profile.frameHeightPx() - 1.0d - innerOffset)
            );
            evidence.addContrastingPair(
                    sample(frame, region, profile, borderCenter, vertical),
                    sample(frame, region, profile, innerOffset, vertical)
            );
            evidence.addContrastingPair(
                    sample(frame, region, profile, profile.frameWidthPx() - 1.0d - borderCenter, vertical),
                    sample(frame, region, profile, profile.frameWidthPx() - 1.0d - innerOffset, vertical)
            );
        }
        return new BorderScore(evidence.score(), evidence.paletteDistanceConfidence());
    }

    private SyncScore scoreSyncBand(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        int cellWidth = syncCellWidth(layoutPlan);
        SyncScore bestScore = new SyncScore(0.0d, 0.0d, JabFrameCandidateScore.NOT_MEASURED);
        int phaseStep = Math.max(1, cellWidth / 4);
        for (int phaseOffset = -cellWidth / 2; phaseOffset <= cellWidth / 2; phaseOffset += phaseStep) {
            SyncScore score = scoreSyncBand(frame, region, profile, layoutPlan, phaseOffset);
            if (score.score() > bestScore.score()) {
                bestScore = score;
            }
        }
        return bestScore;
    }

    private SyncScore scoreSyncBand(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            int phaseOffset
    ) {
        int cellWidth = syncCellWidth(layoutPlan);
        int cellCount = Math.max(1, (profile.frameWidthPx() - (2 * profile.outerMarginPx())) / cellWidth);
        int step = Math.max(1, cellCount / MAX_SYNC_SAMPLES);
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        int minimumLuminance = 255;
        int maximumLuminance = 0;
        int samples = 0;
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex += step) {
            double x = clampDouble(
                    profile.outerMarginPx() + (cellIndex * cellWidth) + (cellWidth / 2.0d) + phaseOffset,
                    profile.outerMarginPx(),
                    profile.frameWidthPx() - profile.outerMarginPx() - 1.0d
            );
            double y = profile.outerMarginPx() + (profile.topSyncBandPx() / 2.0d);
            int luminance = sample(frame, region, profile, x, y);
            if (cellIndex % 2 == 0) {
                evidence.addLight(luminance);
            } else {
                evidence.addDark(luminance);
            }
            minimumLuminance = Math.min(minimumLuminance, luminance);
            maximumLuminance = Math.max(maximumLuminance, luminance);
            samples++;
        }
        double contrastScore = samples == 0 ? 0.0d : contrastConfidence(maximumLuminance, minimumLuminance);
        return new SyncScore(
                (0.82d * evidence.score()) + (0.18d * contrastScore),
                contrastScore,
                evidence.paletteDistanceConfidence()
        );
    }

    private EvidenceScore scoreTileGrid(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        for (int col = 0; col < profile.cols() - 1; col++) {
            TilePlacement left = layoutPlan.tilePlacements().get(col);
            TilePlacement right = layoutPlan.tilePlacements().get(col + 1);
            double x = left.xPx() + left.widthPx() + ((right.xPx() - left.xPx() - left.widthPx()) / 2.0d);
            addVerticalGridSamples(frame, region, profile, layoutPlan, evidence, x);
        }
        for (int row = 0; row < profile.rows() - 1; row++) {
            TilePlacement upper = layoutPlan.tilePlacements().get(row * profile.cols());
            TilePlacement lower = layoutPlan.tilePlacements().get((row + 1) * profile.cols());
            double y = upper.yPx() + upper.heightPx() + ((lower.yPx() - upper.yPx() - upper.heightPx()) / 2.0d);
            addHorizontalGridSamples(frame, region, profile, layoutPlan, evidence, y);
        }
        for (TilePlacement placement : layoutPlan.tilePlacements()) {
            addTileBorderSamples(frame, region, profile, layoutPlan, evidence, placement);
        }
        return new EvidenceScore(evidence.score(), evidence.paletteDistanceConfidence());
    }

    private void addVerticalGridSamples(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            EvidenceAccumulator evidence,
            double x
    ) {
        int gridBottomExclusive = layoutPlan.gridOriginYPx()
                + (profile.rows() * layoutPlan.tileSlotHeightPx())
                + ((profile.rows() - 1) * profile.tileGapPx());
        int sampleCount = Math.max(4, profile.rows() * 4);
        for (int index = 0; index < sampleCount; index++) {
            double ratio = (index + 0.5d) / sampleCount;
            double y = layoutPlan.gridOriginYPx() + (ratio * (gridBottomExclusive - layoutPlan.gridOriginYPx()));
            evidence.addLight(sample(frame, region, profile, x, y));
        }
    }

    private void addHorizontalGridSamples(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            EvidenceAccumulator evidence,
            double y
    ) {
        int gridRightExclusive = layoutPlan.gridOriginXPx()
                + (profile.cols() * layoutPlan.tileSlotWidthPx())
                + ((profile.cols() - 1) * profile.tileGapPx());
        int sampleCount = Math.max(4, profile.cols() * 4);
        for (int index = 0; index < sampleCount; index++) {
            double ratio = (index + 0.5d) / sampleCount;
            double x = layoutPlan.gridOriginXPx() + (ratio * (gridRightExclusive - layoutPlan.gridOriginXPx()));
            evidence.addLight(sample(frame, region, profile, x, y));
        }
    }

    private void addTileBorderSamples(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            EvidenceAccumulator evidence,
            TilePlacement placement
    ) {
        int border = layoutPlan.separatorThicknessPx();
        double centerY = placement.yPx() + (placement.heightPx() / 2.0d);
        double centerX = placement.xPx() + (placement.widthPx() / 2.0d);
        evidence.addLight(sample(frame, region, profile, centerX, placement.yPx() + (border / 2.0d)));
        evidence.addLight(sample(frame, region, profile, centerX, placement.yPx() + placement.heightPx() - 1.0d - (border / 2.0d)));
        evidence.addLight(sample(frame, region, profile, placement.xPx() + (border / 2.0d), centerY));
        evidence.addLight(sample(frame, region, profile, placement.xPx() + placement.widthPx() - 1.0d - (border / 2.0d), centerY));
    }

    private boolean hasMinimumJabEvidence(JabFrameCandidate candidate) {
        JabFrameCandidateScore score = candidate.score();
        return hasStrictRenderedEvidence(score) || hasCameraDerivedEvidence(score);
    }

    private boolean hasStrictRenderedEvidence(JabFrameCandidateScore score) {
        return score.totalScore() >= MIN_TOTAL_SCORE
                && score.borderContrastScore() >= MIN_BORDER_SCORE
                && score.syncBandScore() >= MIN_SYNC_SCORE
                && score.gridScore() >= MIN_GRID_SCORE
                && score.layoutAspectScore() >= MIN_ASPECT_SCORE;
    }

    private boolean hasCameraDerivedEvidence(JabFrameCandidateScore score) {
        return score.totalScore() >= MIN_CAMERA_TOTAL_SCORE
                && score.borderContrastScore() >= MIN_CAMERA_BORDER_SCORE
                && score.syncBandScore() >= MIN_CAMERA_SYNC_SCORE
                && score.gridScore() >= MIN_CAMERA_GRID_SCORE
                && score.layoutAspectScore() >= MIN_ASPECT_SCORE
                && score.paletteDistanceConfidence() >= MIN_CAMERA_PALETTE_CONFIDENCE;
    }

    private List<JabFrameCandidate> distinctRegions(List<JabFrameCandidate> candidates) {
        List<JabFrameCandidate> distinct = new ArrayList<>();
        for (JabFrameCandidate candidate : candidates) {
            if (distinct.stream().noneMatch(existing -> intersectionOverUnion(existing, candidate) >= SAME_REGION_IOU)) {
                distinct.add(candidate);
            }
        }
        return List.copyOf(distinct);
    }

    private double intersectionOverUnion(JabFrameCandidate first, JabFrameCandidate second) {
        int left = Math.max(first.sourceLeftPx(), second.sourceLeftPx());
        int top = Math.max(first.sourceTopPx(), second.sourceTopPx());
        int right = Math.min(first.sourceRightExclusivePx(), second.sourceRightExclusivePx());
        int bottom = Math.min(first.sourceBottomExclusivePx(), second.sourceBottomExclusivePx());
        int intersectionWidth = Math.max(0, right - left);
        int intersectionHeight = Math.max(0, bottom - top);
        double intersection = (double) intersectionWidth * intersectionHeight;
        double firstArea = (double) first.widthPx() * first.heightPx();
        double secondArea = (double) second.widthPx() * second.heightPx();
        double union = firstArea + secondArea - intersection;
        return union <= 0.0d ? 0.0d : intersection / union;
    }

    private double aspectScore(SourceRegion region, LayoutProfile profile) {
        double candidateAspect = (double) region.widthPx() / region.heightPx();
        double profileAspect = (double) profile.frameWidthPx() / profile.frameHeightPx();
        double normalizedError = Math.abs(candidateAspect - profileAspect) / profileAspect;
        return clampScore(1.0d - (normalizedError / MAX_ASPECT_RATIO_ERROR));
    }

    private int sample(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            double profileX,
            double profileY
    ) {
        double xRatio = profile.frameWidthPx() <= 1 ? 0.0d : profileX / (profile.frameWidthPx() - 1.0d);
        double yRatio = profile.frameHeightPx() <= 1 ? 0.0d : profileY / (profile.frameHeightPx() - 1.0d);
        int sourceX = clamp(
                (int) Math.round(region.leftPx() + (xRatio * (region.widthPx() - 1.0d))),
                0,
                frame.widthPixels() - 1
        );
        int sourceY = clamp(
                (int) Math.round(region.topPx() + (yRatio * (region.heightPx() - 1.0d))),
                0,
                frame.heightPixels() - 1
        );
        return luminance(frame.argbPixelAt(sourceY, sourceX));
    }

    private Optional<FrameCorners> refineFrameCorners(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan
    ) {
        List<PointD> topPoints = horizontalEdgePoints(frame, region, profile, layoutPlan, true);
        List<PointD> bottomPoints = horizontalEdgePoints(frame, region, profile, layoutPlan, false);
        List<PointD> leftPoints = verticalEdgePoints(frame, region, profile, layoutPlan, true);
        List<PointD> rightPoints = verticalEdgePoints(frame, region, profile, layoutPlan, false);
        if (topPoints.size() < MIN_EDGE_POINTS
                || bottomPoints.size() < MIN_EDGE_POINTS
                || leftPoints.size() < MIN_EDGE_POINTS
                || rightPoints.size() < MIN_EDGE_POINTS) {
            return Optional.empty();
        }

        Optional<HorizontalLine> topLine = fitHorizontalLine(topPoints);
        Optional<HorizontalLine> bottomLine = fitHorizontalLine(bottomPoints);
        Optional<VerticalLine> leftLine = fitVerticalLine(leftPoints);
        Optional<VerticalLine> rightLine = fitVerticalLine(rightPoints);
        if (topLine.isEmpty() || bottomLine.isEmpty() || leftLine.isEmpty() || rightLine.isEmpty()) {
            return Optional.empty();
        }

        Optional<PointD> topLeft = intersection(topLine.get(), leftLine.get());
        Optional<PointD> topRight = intersection(topLine.get(), rightLine.get());
        Optional<PointD> bottomRight = intersection(bottomLine.get(), rightLine.get());
        Optional<PointD> bottomLeft = intersection(bottomLine.get(), leftLine.get());
        if (topLeft.isEmpty() || topRight.isEmpty() || bottomRight.isEmpty() || bottomLeft.isEmpty()) {
            return Optional.empty();
        }

        FrameCorners corners = new FrameCorners(
                clampDouble(topLeft.get().x(), 0.0d, frame.widthPixels()),
                clampDouble(topLeft.get().y(), 0.0d, frame.heightPixels()),
                clampDouble(topRight.get().x(), 0.0d, frame.widthPixels()),
                clampDouble(topRight.get().y(), 0.0d, frame.heightPixels()),
                clampDouble(bottomRight.get().x(), 0.0d, frame.widthPixels()),
                clampDouble(bottomRight.get().y(), 0.0d, frame.heightPixels()),
                clampDouble(bottomLeft.get().x(), 0.0d, frame.widthPixels()),
                clampDouble(bottomLeft.get().y(), 0.0d, frame.heightPixels())
        );
        return plausibleRefinedCorners(corners, region, profile) ? Optional.of(corners) : Optional.empty();
    }

    private List<PointD> horizontalEdgePoints(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            boolean topEdge
    ) {
        int scanWindow = Math.max(8, (int) Math.round(region.heightPx() * EDGE_SCAN_WINDOW_RATIO));
        int insideOffset = sourceOffsetPx(region.heightPx(), profile.frameHeightPx(), layoutPlan.separatorThicknessPx());
        int minY = topEdge ? region.topPx() : Math.max(region.topPx(), region.bottomExclusivePx() - scanWindow);
        int maxY = topEdge ? Math.min(region.bottomExclusivePx() - 1, region.topPx() + scanWindow)
                : region.bottomExclusivePx() - 1;
        List<PointD> points = new ArrayList<>();
        for (int index = 0; index < EDGE_SAMPLE_COUNT; index++) {
            double ratio = 0.08d + (((double) index / (EDGE_SAMPLE_COUNT - 1)) * 0.84d);
            int sourceX = clamp(
                    (int) Math.round(region.leftPx() + (ratio * (region.widthPx() - 1.0d))),
                    0,
                    frame.widthPixels() - 1
            );
            bestHorizontalEdgeY(frame, sourceX, minY, maxY, insideOffset, topEdge)
                    .filter(point -> point.score() >= MIN_EDGE_POINT_SCORE)
                    .ifPresent(point -> points.add(new PointD(sourceX, point.coordinate())));
        }
        return List.copyOf(points);
    }

    private List<PointD> verticalEdgePoints(
            MediaInputFrame frame,
            SourceRegion region,
            LayoutProfile profile,
            FixedLayoutPlan layoutPlan,
            boolean leftEdge
    ) {
        int scanWindow = Math.max(8, (int) Math.round(region.widthPx() * EDGE_SCAN_WINDOW_RATIO));
        int insideOffset = sourceOffsetPx(region.widthPx(), profile.frameWidthPx(), layoutPlan.separatorThicknessPx());
        int minX = leftEdge ? region.leftPx() : Math.max(region.leftPx(), region.rightExclusivePx() - scanWindow);
        int maxX = leftEdge ? Math.min(region.rightExclusivePx() - 1, region.leftPx() + scanWindow)
                : region.rightExclusivePx() - 1;
        List<PointD> points = new ArrayList<>();
        for (int index = 0; index < EDGE_SAMPLE_COUNT; index++) {
            double ratio = 0.10d + (((double) index / (EDGE_SAMPLE_COUNT - 1)) * 0.80d);
            int sourceY = clamp(
                    (int) Math.round(region.topPx() + (ratio * (region.heightPx() - 1.0d))),
                    0,
                    frame.heightPixels() - 1
            );
            bestVerticalEdgeX(frame, sourceY, minX, maxX, insideOffset, leftEdge)
                    .filter(point -> point.score() >= MIN_EDGE_POINT_SCORE)
                    .ifPresent(point -> points.add(new PointD(point.coordinate(), sourceY)));
        }
        return List.copyOf(points);
    }

    private Optional<EdgePoint> bestHorizontalEdgeY(
            MediaInputFrame frame,
            int sourceX,
            int minY,
            int maxY,
            int insideOffset,
            boolean topEdge
    ) {
        EdgePoint best = null;
        int sourceY = topEdge ? minY : maxY;
        while (topEdge ? sourceY <= maxY : sourceY >= minY) {
            int insideY = topEdge
                    ? clamp(sourceY + insideOffset, 0, frame.heightPixels() - 1)
                    : clamp(sourceY - insideOffset, 0, frame.heightPixels() - 1);
            int edgeLuminance = luminance(frame.argbPixelAt(sourceY, sourceX));
            int insideLuminance = luminance(frame.argbPixelAt(insideY, sourceX));
            double score = edgePointScore(edgeLuminance, insideLuminance);
            if (score >= MIN_EDGE_POINT_SCORE) {
                return Optional.of(new EdgePoint(sourceY, score));
            }
            if (best == null || score > best.score()) {
                best = new EdgePoint(sourceY, score);
            }
            sourceY += topEdge ? 1 : -1;
        }
        return Optional.ofNullable(best);
    }

    private Optional<EdgePoint> bestVerticalEdgeX(
            MediaInputFrame frame,
            int sourceY,
            int minX,
            int maxX,
            int insideOffset,
            boolean leftEdge
    ) {
        EdgePoint best = null;
        int sourceX = leftEdge ? minX : maxX;
        while (leftEdge ? sourceX <= maxX : sourceX >= minX) {
            int insideX = leftEdge
                    ? clamp(sourceX + insideOffset, 0, frame.widthPixels() - 1)
                    : clamp(sourceX - insideOffset, 0, frame.widthPixels() - 1);
            int edgeLuminance = luminance(frame.argbPixelAt(sourceY, sourceX));
            int insideLuminance = luminance(frame.argbPixelAt(sourceY, insideX));
            double score = edgePointScore(edgeLuminance, insideLuminance);
            if (score >= MIN_EDGE_POINT_SCORE) {
                return Optional.of(new EdgePoint(sourceX, score));
            }
            if (best == null || score > best.score()) {
                best = new EdgePoint(sourceX, score);
            }
            sourceX += leftEdge ? 1 : -1;
        }
        return Optional.ofNullable(best);
    }

    private double edgePointScore(int edgeLuminance, int insideLuminance) {
        return (0.55d * lightConfidence(edgeLuminance))
                + (0.30d * darkConfidence(insideLuminance))
                + (0.15d * contrastConfidence(edgeLuminance, insideLuminance));
    }

    private int sourceOffsetPx(int sourceDimension, int profileDimension, int separatorThicknessPx) {
        double scale = sourceDimension / (double) profileDimension;
        return Math.max(2, (int) Math.round(scale * Math.max(4, separatorThicknessPx * 2)));
    }

    private Optional<HorizontalLine> fitHorizontalLine(List<PointD> points) {
        if (points.isEmpty()) {
            return Optional.empty();
        }
        double sumX = 0.0d;
        double sumY = 0.0d;
        double sumXX = 0.0d;
        double sumXY = 0.0d;
        for (PointD point : points) {
            sumX += point.x();
            sumY += point.y();
            sumXX += point.x() * point.x();
            sumXY += point.x() * point.y();
        }
        double count = points.size();
        double denominator = (count * sumXX) - (sumX * sumX);
        if (Math.abs(denominator) < 0.000001d) {
            return Optional.of(new HorizontalLine(0.0d, sumY / count));
        }
        double slope = ((count * sumXY) - (sumX * sumY)) / denominator;
        double intercept = (sumY - (slope * sumX)) / count;
        return Optional.of(new HorizontalLine(slope, intercept));
    }

    private Optional<VerticalLine> fitVerticalLine(List<PointD> points) {
        if (points.isEmpty()) {
            return Optional.empty();
        }
        double sumY = 0.0d;
        double sumX = 0.0d;
        double sumYY = 0.0d;
        double sumYX = 0.0d;
        for (PointD point : points) {
            sumY += point.y();
            sumX += point.x();
            sumYY += point.y() * point.y();
            sumYX += point.y() * point.x();
        }
        double count = points.size();
        double denominator = (count * sumYY) - (sumY * sumY);
        if (Math.abs(denominator) < 0.000001d) {
            return Optional.of(new VerticalLine(0.0d, sumX / count));
        }
        double slope = ((count * sumYX) - (sumY * sumX)) / denominator;
        double intercept = (sumX - (slope * sumY)) / count;
        return Optional.of(new VerticalLine(slope, intercept));
    }

    private Optional<PointD> intersection(HorizontalLine horizontalLine, VerticalLine verticalLine) {
        double denominator = 1.0d - (verticalLine.slope() * horizontalLine.slope());
        if (Math.abs(denominator) < 0.000001d) {
            return Optional.empty();
        }
        double x = ((verticalLine.slope() * horizontalLine.intercept()) + verticalLine.intercept()) / denominator;
        double y = (horizontalLine.slope() * x) + horizontalLine.intercept();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return Optional.empty();
        }
        return Optional.of(new PointD(x, y));
    }

    private boolean plausibleRefinedCorners(FrameCorners corners, SourceRegion region, LayoutProfile profile) {
        double area = quadrilateralArea(corners);
        if (area < region.areaPx() * 0.45d || area > region.areaPx() * 1.20d) {
            return false;
        }
        double top = distance(corners.topLeftX(), corners.topLeftY(), corners.topRightX(), corners.topRightY());
        double bottom = distance(corners.bottomLeftX(), corners.bottomLeftY(), corners.bottomRightX(), corners.bottomRightY());
        double left = distance(corners.topLeftX(), corners.topLeftY(), corners.bottomLeftX(), corners.bottomLeftY());
        double right = distance(corners.topRightX(), corners.topRightY(), corners.bottomRightX(), corners.bottomRightY());
        double averageWidth = (top + bottom) / 2.0d;
        double averageHeight = (left + right) / 2.0d;
        if (averageWidth <= 0.0d || averageHeight <= 0.0d) {
            return false;
        }
        double profileAspect = (double) profile.frameWidthPx() / profile.frameHeightPx();
        double refinedAspect = averageWidth / averageHeight;
        double normalizedError = Math.abs(refinedAspect - profileAspect) / profileAspect;
        return normalizedError <= MAX_REFINED_ASPECT_ERROR;
    }

    private FrameCorners axisAlignedCorners(SourceRegion region) {
        return new FrameCorners(
                region.leftPx(),
                region.topPx(),
                region.rightExclusivePx(),
                region.topPx(),
                region.rightExclusivePx(),
                region.bottomExclusivePx(),
                region.leftPx(),
                region.bottomExclusivePx()
        );
    }

    private double quadrilateralArea(FrameCorners corners) {
        double twiceArea = (corners.topLeftX() * corners.topRightY())
                - (corners.topLeftY() * corners.topRightX())
                + (corners.topRightX() * corners.bottomRightY())
                - (corners.topRightY() * corners.bottomRightX())
                + (corners.bottomRightX() * corners.bottomLeftY())
                - (corners.bottomRightY() * corners.bottomLeftX())
                + (corners.bottomLeftX() * corners.topLeftY())
                - (corners.bottomLeftY() * corners.topLeftX());
        return Math.abs(twiceArea) / 2.0d;
    }

    private double perspectiveSkewScore(FrameCorners corners) {
        double top = distance(corners.topLeftX(), corners.topLeftY(), corners.topRightX(), corners.topRightY());
        double bottom = distance(corners.bottomLeftX(), corners.bottomLeftY(), corners.bottomRightX(), corners.bottomRightY());
        double left = distance(corners.topLeftX(), corners.topLeftY(), corners.bottomLeftX(), corners.bottomLeftY());
        double right = distance(corners.topRightX(), corners.topRightY(), corners.bottomRightX(), corners.bottomRightY());
        double horizontalSkew = normalizedDifference(top, bottom);
        double verticalSkew = normalizedDifference(left, right);
        return clampScore(Math.max(horizontalSkew, verticalSkew));
    }

    private double normalizedDifference(double first, double second) {
        double denominator = Math.max(first, second);
        return denominator <= 0.0d ? 1.0d : Math.abs(first - second) / denominator;
    }

    private double distance(double firstX, double firstY, double secondX, double secondY) {
        double deltaX = firstX - secondX;
        double deltaY = firstY - secondY;
        return Math.hypot(deltaX, deltaY);
    }

    private double glareScore(MediaInputFrame frame, SourceRegion region) {
        int samples = 0;
        int nearWhite = 0;
        for (int yIndex = 0; yIndex < 9; yIndex++) {
            int sourceY = region.topPx() + (int) Math.round(((yIndex + 0.5d) / 9.0d) * region.heightPx());
            for (int xIndex = 0; xIndex < 16; xIndex++) {
                int sourceX = region.leftPx() + (int) Math.round(((xIndex + 0.5d) / 16.0d) * region.widthPx());
                int luminance = luminance(frame.argbPixelAt(
                        clamp(sourceY, 0, frame.heightPixels() - 1),
                        clamp(sourceX, 0, frame.widthPixels() - 1)
                ));
                if (luminance >= 245) {
                    nearWhite++;
                }
                samples++;
            }
        }
        return samples == 0 ? JabFrameCandidateScore.NOT_MEASURED : (double) nearWhite / samples;
    }

    private int syncCellWidth(FixedLayoutPlan layoutPlan) {
        return Math.max(8, layoutPlan.separatorThicknessPx() * 2);
    }

    private int luminance(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (int) Math.round((0.2126d * red) + (0.7152d * green) + (0.0722d * blue));
    }

    private double lightConfidence(int luminance) {
        return clampScore((luminance - LIGHT_CONFIDENCE_FLOOR) / (255.0d - LIGHT_CONFIDENCE_FLOOR));
    }

    private double darkConfidence(int luminance) {
        return clampScore((DARK_CONFIDENCE_CEILING - luminance) / (double) DARK_CONFIDENCE_CEILING);
    }

    private double contrastConfidence(int lightLuminance, int darkLuminance) {
        return clampScore((lightLuminance - darkLuminance - REQUIRED_CONTRAST_DELTA) / 128.0d);
    }

    private double paletteDistanceConfidence(int luminance) {
        return clampScore(1.0d - (Math.min(luminance, 255 - luminance) / 96.0d));
    }

    private double averageMeasured(double first, double second, double third) {
        double total = 0.0d;
        int count = 0;
        if (first != JabFrameCandidateScore.NOT_MEASURED) {
            total += first;
            count++;
        }
        if (second != JabFrameCandidateScore.NOT_MEASURED) {
            total += second;
            count++;
        }
        if (third != JabFrameCandidateScore.NOT_MEASURED) {
            total += third;
            count++;
        }
        return count == 0 ? JabFrameCandidateScore.NOT_MEASURED : total / count;
    }

    private double clampScore(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record DownsampledLuminance(int width, int height, int stride, int[] luminance, boolean[] light) {
    }

    private record Component(int minX, int minY, int maxX, int maxY, int pixelCount) {
    }

    private record Point(int x, int y) {
    }

    private record PointD(double x, double y) {
    }

    private record EdgePoint(double coordinate, double score) {
    }

    private record HorizontalLine(double slope, double intercept) {
    }

    private record VerticalLine(double slope, double intercept) {
    }

    private record LightRun(int startX, int endExclusiveX) {

        private int widthPx(int stride) {
            return (endExclusiveX - startX) * stride;
        }
    }

    private record SyncRowEvidence(
            int leftPx,
            int rightExclusivePx,
            int topPx,
            int bottomExclusivePx,
            int runCount,
            double fillRatio
    ) {

        private int spanPx() {
            return rightExclusivePx - leftPx;
        }
    }

    private record SyncBandEvidence(
            int leftPx,
            int rightExclusivePx,
            int topPx,
            int bottomExclusivePx,
            int maxRunCount,
            double totalFillRatio,
            int rowCount
    ) {

        private static SyncBandEvidence from(SyncRowEvidence row) {
            return new SyncBandEvidence(
                    row.leftPx(),
                    row.rightExclusivePx(),
                    row.topPx(),
                    row.bottomExclusivePx(),
                    row.runCount(),
                    row.fillRatio(),
                    1
            );
        }

        private boolean alignedWith(SyncRowEvidence row) {
            return row.topPx() <= bottomExclusivePx + MAX_SYNC_RUN_GAP_SOURCE_PX
                    && Math.abs(row.leftPx() - leftPx) <= SYNC_ROW_ALIGNMENT_TOLERANCE_SOURCE_PX
                    && Math.abs(row.rightExclusivePx() - rightExclusivePx) <= SYNC_ROW_ALIGNMENT_TOLERANCE_SOURCE_PX;
        }

        private SyncBandEvidence merge(SyncRowEvidence row) {
            return new SyncBandEvidence(
                    Math.min(leftPx, row.leftPx()),
                    Math.max(rightExclusivePx, row.rightExclusivePx()),
                    Math.min(topPx, row.topPx()),
                    Math.max(bottomExclusivePx, row.bottomExclusivePx()),
                    Math.max(maxRunCount, row.runCount()),
                    totalFillRatio + row.fillRatio(),
                    rowCount + 1
            );
        }

        private int spanPx() {
            return rightExclusivePx - leftPx;
        }

        private int heightPx() {
            return bottomExclusivePx - topPx;
        }
    }

    private record SourceRegion(int leftPx, int topPx, int rightExclusivePx, int bottomExclusivePx) {

        private int widthPx() {
            return rightExclusivePx - leftPx;
        }

        private int heightPx() {
            return bottomExclusivePx - topPx;
        }

        private int shortEdgePx() {
            return Math.min(widthPx(), heightPx());
        }

        private int areaPx() {
            return widthPx() * heightPx();
        }
    }

    private final class EvidenceAccumulator {

        private double totalScore;
        private double totalPaletteDistanceConfidence;
        private int sampleCount;

        private void addLight(int luminance) {
            add(lightConfidence(luminance), JabFrameRegionDetector.this.paletteDistanceConfidence(luminance));
        }

        private void addDark(int luminance) {
            add(darkConfidence(luminance), JabFrameRegionDetector.this.paletteDistanceConfidence(luminance));
        }

        private void addContrastingPair(int lightLuminance, int darkLuminance) {
            add(
                    (lightConfidence(lightLuminance)
                            + darkConfidence(darkLuminance)
                            + contrastConfidence(lightLuminance, darkLuminance)) / 3.0d,
                    (JabFrameRegionDetector.this.paletteDistanceConfidence(lightLuminance)
                            + JabFrameRegionDetector.this.paletteDistanceConfidence(darkLuminance)) / 2.0d
            );
        }

        private void add(double score, double paletteDistanceConfidence) {
            totalScore += clampScore(score);
            totalPaletteDistanceConfidence += clampScore(paletteDistanceConfidence);
            sampleCount++;
        }

        private double score() {
            return sampleCount == 0 ? 0.0d : totalScore / sampleCount;
        }

        private double paletteDistanceConfidence() {
            return sampleCount == 0
                    ? JabFrameCandidateScore.NOT_MEASURED
                    : totalPaletteDistanceConfidence / sampleCount;
        }
    }

    private record EvidenceScore(double score, double paletteDistanceConfidence) {
    }

    private record BorderScore(double score, double paletteDistanceConfidence) {
    }

    private record SyncScore(double score, double contrastScore, double paletteDistanceConfidence) {
    }
}
