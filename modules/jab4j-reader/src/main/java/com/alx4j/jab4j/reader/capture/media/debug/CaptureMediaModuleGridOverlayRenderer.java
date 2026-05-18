package com.alx4j.jab4j.reader.capture.media.debug;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalControlPointEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidenceStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternFeatureType;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.BorderInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.CandidateInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.DecodeInspectionStatus;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.FrameInspection;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler.SlotInspection;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;

/**
 * Renders deterministic debug overlays for normalized capture-media candidates using sampler inspection evidence.
 */
public final class CaptureMediaModuleGridOverlayRenderer {

    private static final Color NOMINAL_SLOT_COLOR = new Color(255, 255, 255, 96);
    private static final Color SHIFT_VECTOR_COLOR = new Color(255, 255, 0, 192);
    private static final Color SIGNATURE_SLOT_COLOR = new Color(0, 220, 120, 176);
    private static final Color NO_SIGNATURE_SLOT_COLOR = new Color(255, 210, 0, 176);
    private static final Color PALETTE_REJECTED_SLOT_COLOR = new Color(255, 64, 64, 192);
    private static final Color NO_FINDER_ATTEMPT_COLOR = new Color(255, 160, 0, 144);
    private static final Color PALETTE_REJECTED_ATTEMPT_COLOR = new Color(255, 48, 48, 160);
    private static final Color FINDER_CANDIDATE_COLOR = new Color(64, 192, 255, 160);
    private static final Color TILE_OR_ENVELOPE_REJECTED_COLOR = new Color(220, 64, 255, 176);
    private static final Color ACCEPTED_PAYLOAD_COLOR = new Color(0, 255, 128, 192);
    private static final Color PATTERN_DETECTED_COLOR = new Color(0, 255, 128, 224);
    private static final Color PATTERN_AMBIGUOUS_COLOR = new Color(255, 220, 0, 224);
    private static final Color PATTERN_REJECTED_COLOR = new Color(255, 64, 64, 224);
    private static final Color GEOMETRY_ACCEPTED_COLOR = new Color(64, 220, 255, 224);
    private static final Color GEOMETRY_WITHHELD_COLOR = new Color(255, 160, 0, 208);
    private static final Color GEOMETRY_REJECTED_COLOR = new Color(255, 64, 128, 208);
    private static final Color SOURCE_SAMPLING_READABLE_COLOR = new Color(0, 220, 120, 192);
    private static final Color SOURCE_SAMPLING_AMBIGUOUS_COLOR = new Color(255, 220, 0, 192);
    private static final Color SOURCE_SAMPLING_UNREADABLE_COLOR = new Color(255, 80, 80, 192);
    private static final Color SOURCE_SAMPLING_CLIPPED_COLOR = new Color(180, 120, 255, 192);
    private static final Color LOCAL_RESIDUAL_VECTOR_COLOR = new Color(255, 255, 255, 224);
    private static final Color LOCAL_RESIDUAL_POINT_COLOR = new Color(255, 0, 255, 224);
    private static final int MAX_MODULE_CENTER_POINTS_PER_ATTEMPT = 2_500;
    private static final int MAX_LOCAL_RESIDUAL_VECTORS = 64;
    private static final double MAX_PROPORTIONAL_LAYOUT_SCALE_ERROR = 0.01d;
    private static final double MIN_HOMOGRAPHY_DENOMINATOR = 1.0e-9d;

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final TileCodecProfile tileCodecProfile;

    /**
     * Creates an overlay renderer for the current capture layout catalog and balanced tile codec profile.
     */
    public CaptureMediaModuleGridOverlayRenderer() {
        this(new CaptureRenderedLayoutCatalog(), new FixedLayoutPlanner(), TileCodecProfiles.balancedV1());
    }

    /**
     * Creates an overlay renderer with explicit collaborators for focused tests.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner
     * @param tileCodecProfile tile codec profile used by the sampler
     */
    public CaptureMediaModuleGridOverlayRenderer(
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            TileCodecProfile tileCodecProfile
    ) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.tileCodecProfile = Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
    }

    /**
     * Renders one overlay image from normalized pixels and the sampler inspection selected for those pixels.
     *
     * @param frame normalized candidate frame
     * @param inspection sampler inspection evidence for the frame
     * @return ARGB overlay PNG content
     */
    public BufferedImage render(NormalizedCaptureFrame frame, FrameInspection inspection) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(inspection, "inspection must not be null");

        BufferedImage image = new BufferedImage(
                frame.normalizedWidthPixels(),
                frame.normalizedHeightPixels(),
                BufferedImage.TYPE_INT_ARGB
        );
        image.setRGB(
                0,
                0,
                frame.normalizedWidthPixels(),
                frame.normalizedHeightPixels(),
                frame.copyArgbPixels(),
                0,
                frame.normalizedWidthPixels()
        );

        Optional<FixedLayoutPlan> layoutPlan = resolveLayoutPlan(frame, inspection.layoutProfileId());
        if (layoutPlan.isEmpty()) {
            return image;
        }
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            drawSlots(graphics, frame, layoutPlan.orElseThrow(), inspection);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Renders one overlay image with bounded MVP-9 evidence marks added to the existing grid overlay.
     *
     * @param frame normalized candidate frame
     * @param inspection sampler inspection evidence for the frame
     * @param patternEvidence direct pattern evidence for finder footprints
     * @param geometryEvidence geometry fit evidence for retained geometry summary
     * @param sourceSamplingEvidence selected source-space sampling evidence, when available
     * @param localRefinementEvidence local residual diagnostics for bounded residual vectors
     * @return ARGB overlay PNG content
     */
    public BufferedImage render(
            NormalizedCaptureFrame frame,
            FrameInspection inspection,
            PatternEvidence patternEvidence,
            GeometryFitEvidence geometryEvidence,
            Optional<ModuleSamplingEvidence> sourceSamplingEvidence,
            LocalRefinementEvidence localRefinementEvidence
    ) {
        Objects.requireNonNull(patternEvidence, "patternEvidence must not be null");
        Objects.requireNonNull(geometryEvidence, "geometryEvidence must not be null");
        Objects.requireNonNull(sourceSamplingEvidence, "sourceSamplingEvidence must not be null");
        Objects.requireNonNull(localRefinementEvidence, "localRefinementEvidence must not be null");

        BufferedImage image = render(frame, inspection);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            drawPatternFootprints(graphics, patternEvidence);
            drawGeometryOutline(graphics, frame, geometryEvidence);
            sourceSamplingEvidence.ifPresent(evidence -> drawSourceSamplingSummary(graphics, frame, evidence));
            drawLocalResidualVectors(graphics, localRefinementEvidence);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void drawPatternFootprints(Graphics2D graphics, PatternEvidence evidence) {
        graphics.setStroke(new BasicStroke(3.0f));
        graphics.setColor(patternColor(evidence.status()));
        for (PatternFeatureEvidence feature : evidence.features()) {
            if (feature.featureType() == PatternFeatureType.FINDER) {
                drawPolygon(graphics, feature.sourceFootprint());
            }
        }
    }

    private Color patternColor(PatternEvidenceStatus status) {
        return switch (status) {
            case DETECTED -> PATTERN_DETECTED_COLOR;
            case AMBIGUOUS -> PATTERN_AMBIGUOUS_COLOR;
            case REJECTED, NOT_FOUND -> PATTERN_REJECTED_COLOR;
        };
    }

    private void drawGeometryOutline(
            Graphics2D graphics,
            NormalizedCaptureFrame frame,
            GeometryFitEvidence evidence
    ) {
        Optional<GeometryCandidateEvidence> candidate = selectedOrFirstGeometryCandidate(evidence);
        if (candidate.isEmpty()) {
            return;
        }
        List<Double> transform = candidate.orElseThrow().transformParameters();
        if (transform.size() != 9) {
            return;
        }
        Optional<SourcePolygon> outline = projectedFrameOutline(frame, transform);
        if (outline.isEmpty()) {
            return;
        }
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(geometryColor(evidence.status()));
        drawPolygon(graphics, outline.orElseThrow());
    }

    private Optional<GeometryCandidateEvidence> selectedOrFirstGeometryCandidate(GeometryFitEvidence evidence) {
        Optional<String> selectedId = evidence.selectedGeometryCandidateId();
        if (selectedId.isPresent()) {
            String selected = selectedId.orElseThrow();
            for (GeometryCandidateEvidence candidate : evidence.retainedCandidates()) {
                if (candidate.candidateId().geometryCandidateId().filter(selected::equals).isPresent()) {
                    return Optional.of(candidate);
                }
            }
        }
        return evidence.retainedCandidates().stream().findFirst();
    }

    private Color geometryColor(GeometryFitStatus status) {
        return switch (status) {
            case ACCEPTED -> GEOMETRY_ACCEPTED_COLOR;
            case AMBIGUOUS -> PATTERN_AMBIGUOUS_COLOR;
            case WITHHELD, NOT_AVAILABLE -> GEOMETRY_WITHHELD_COLOR;
            case REJECTED -> GEOMETRY_REJECTED_COLOR;
        };
    }

    private Optional<SourcePolygon> projectedFrameOutline(NormalizedCaptureFrame frame, List<Double> transform) {
        Optional<SourcePoint> topLeft = project(transform, 0.0d, 0.0d);
        Optional<SourcePoint> topRight = project(transform, frame.normalizedWidthPixels(), 0.0d);
        Optional<SourcePoint> bottomRight = project(
                transform,
                frame.normalizedWidthPixels(),
                frame.normalizedHeightPixels()
        );
        Optional<SourcePoint> bottomLeft = project(transform, 0.0d, frame.normalizedHeightPixels());
        if (topLeft.isEmpty() || topRight.isEmpty() || bottomRight.isEmpty() || bottomLeft.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SourcePolygon(List.of(
                topLeft.orElseThrow(),
                topRight.orElseThrow(),
                bottomRight.orElseThrow(),
                bottomLeft.orElseThrow()
        )));
    }

    private Optional<SourcePoint> project(List<Double> transform, double x, double y) {
        double denominator = (transform.get(6) * x) + (transform.get(7) * y) + transform.get(8);
        if (!Double.isFinite(denominator) || Math.abs(denominator) < MIN_HOMOGRAPHY_DENOMINATOR) {
            return Optional.empty();
        }
        double projectedX = ((transform.get(0) * x) + (transform.get(1) * y) + transform.get(2)) / denominator;
        double projectedY = ((transform.get(3) * x) + (transform.get(4) * y) + transform.get(5)) / denominator;
        if (!Double.isFinite(projectedX) || !Double.isFinite(projectedY)) {
            return Optional.empty();
        }
        return Optional.of(new SourcePoint(projectedX, projectedY));
    }

    private void drawSourceSamplingSummary(
            Graphics2D graphics,
            NormalizedCaptureFrame frame,
            ModuleSamplingEvidence evidence
    ) {
        int total = Math.max(1, evidence.totalModuleCount());
        int width = Math.max(80, Math.min(220, frame.normalizedWidthPixels() / 4));
        int height = 8;
        int x = 12;
        int y = Math.max(12, frame.normalizedHeightPixels() - 20);
        int drawn = 0;
        drawn += drawSummarySegment(
                graphics,
                SOURCE_SAMPLING_READABLE_COLOR,
                x + drawn,
                y,
                width,
                height,
                evidence.readableModuleCount(),
                total
        );
        drawn += drawSummarySegment(
                graphics,
                SOURCE_SAMPLING_AMBIGUOUS_COLOR,
                x + drawn,
                y,
                width,
                height,
                evidence.ambiguousModuleCount(),
                total
        );
        drawn += drawSummarySegment(
                graphics,
                SOURCE_SAMPLING_UNREADABLE_COLOR,
                x + drawn,
                y,
                width,
                height,
                evidence.unreadableModuleCount(),
                total
        );
        drawSummarySegment(
                graphics,
                SOURCE_SAMPLING_CLIPPED_COLOR,
                x + drawn,
                y,
                width,
                height,
                evidence.clippedModuleCount() + evidence.outOfBoundsModuleCount(),
                total
        );
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(GEOMETRY_ACCEPTED_COLOR);
        graphics.drawRect(x, y, width, height);
    }

    private int drawSummarySegment(
            Graphics2D graphics,
            Color color,
            int x,
            int y,
            int totalWidth,
            int height,
            int count,
            int total
    ) {
        if (count <= 0) {
            return 0;
        }
        int width = Math.max(1, (int) Math.round((double) totalWidth * (double) count / (double) total));
        graphics.setColor(color);
        graphics.fillRect(x, y, width, height);
        return width;
    }

    private void drawLocalResidualVectors(Graphics2D graphics, LocalRefinementEvidence evidence) {
        graphics.setStroke(new BasicStroke(1.0f));
        int count = 0;
        for (LocalControlPointEvidence controlPoint : evidence.controlPoints()) {
            if (count >= MAX_LOCAL_RESIDUAL_VECTORS) {
                return;
            }
            SourcePoint expected = controlPoint.expectedSourcePoint();
            graphics.setColor(LOCAL_RESIDUAL_POINT_COLOR);
            graphics.fillRect(round(expected.x()) - 1, round(expected.y()) - 1, 3, 3);
            if (controlPoint.observedSourcePoint().isPresent()) {
                SourcePoint observed = controlPoint.observedSourcePoint().orElseThrow();
                graphics.setColor(LOCAL_RESIDUAL_VECTOR_COLOR);
                graphics.drawLine(round(expected.x()), round(expected.y()), round(observed.x()), round(observed.y()));
            }
            count++;
        }
    }

    private void drawPolygon(Graphics2D graphics, SourcePolygon polygon) {
        List<SourcePoint> vertices = polygon.vertices();
        if (vertices.size() < 2) {
            return;
        }
        int[] xPoints = new int[vertices.size()];
        int[] yPoints = new int[vertices.size()];
        for (int index = 0; index < vertices.size(); index++) {
            SourcePoint point = vertices.get(index);
            xPoints[index] = round(point.x());
            yPoints[index] = round(point.y());
        }
        graphics.drawPolygon(xPoints, yPoints, vertices.size());
    }

    private int round(double value) {
        return (int) Math.round(value);
    }

    private Optional<FixedLayoutPlan> resolveLayoutPlan(NormalizedCaptureFrame frame, String layoutProfileId) {
        Optional<LayoutProfile> exactProfile = layoutCatalog
                .resolve(frame.normalizedWidthPixels(), frame.normalizedHeightPixels())
                .filter(profile -> profile.profileId().equals(layoutProfileId));
        if (exactProfile.isPresent()) {
            return exactProfile.map(layoutPlanner::plan);
        }
        return layoutCatalog.profiles().stream()
                .filter(profile -> profile.profileId().equals(layoutProfileId))
                .map(profile -> scaledProfile(profile, frame.normalizedWidthPixels(), frame.normalizedHeightPixels()))
                .flatMap(Optional::stream)
                .findFirst()
                .map(layoutPlanner::plan);
    }

    private Optional<LayoutProfile> scaledProfile(LayoutProfile profile, int frameWidthPx, int frameHeightPx) {
        if (profile.frameWidthPx() == frameWidthPx && profile.frameHeightPx() == frameHeightPx) {
            return Optional.of(profile);
        }
        double scaleX = (double) frameWidthPx / profile.frameWidthPx();
        double scaleY = (double) frameHeightPx / profile.frameHeightPx();
        if (!proportionalScale(scaleX, scaleY)) {
            return Optional.empty();
        }
        return Optional.of(new LayoutProfile(
                profile.profileId(),
                profile.rows(),
                profile.cols(),
                frameWidthPx,
                frameHeightPx,
                scaledPixels(profile.tileGapPx(), scaleX),
                scaledPixels(profile.outerMarginPx(), scaleX),
                profile.separatorStyle(),
                scaledPixels(profile.topSyncBandPx(), scaleX),
                scaledPixels(profile.metadataBandPx(), scaleX),
                profile.backgroundStyle(),
                profile.fitPolicy()
        ));
    }

    private boolean proportionalScale(double scaleX, double scaleY) {
        if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY) || scaleX <= 0.0d || scaleY <= 0.0d) {
            return false;
        }
        double error = Math.abs(scaleX - scaleY) / Math.max(scaleX, scaleY);
        return error <= MAX_PROPORTIONAL_LAYOUT_SCALE_ERROR;
    }

    private int scaledPixels(int pixels, double scale) {
        return Math.max(0, (int) Math.round(pixels * scale));
    }

    private void drawSlots(
            Graphics2D graphics,
            NormalizedCaptureFrame frame,
            FixedLayoutPlan layoutPlan,
            FrameInspection inspection
    ) {
        for (SlotInspection slot : inspection.slots()) {
            if (slot.tileIndex() >= layoutPlan.tilePlacements().size()) {
                continue;
            }
            TilePlacement nominalPlacement = layoutPlan.tilePlacements().get(slot.tileIndex());
            TilePlacement effectivePlacement = shiftedPlacement(frame, nominalPlacement, slot);
            drawSlotBounds(graphics, nominalPlacement, effectivePlacement, slot);
            for (CandidateInspection candidate : slot.candidates()) {
                drawCandidateAttempt(graphics, layoutPlan, effectivePlacement, candidate);
            }
        }
    }

    private TilePlacement shiftedPlacement(
            NormalizedCaptureFrame frame,
            TilePlacement placement,
            SlotInspection slot
    ) {
        int x = clamp(
                placement.xPx() + slot.effectiveTileShiftXPx(),
                0,
                frame.normalizedWidthPixels() - placement.widthPx()
        );
        int y = clamp(
                placement.yPx() + slot.effectiveTileShiftYPx(),
                0,
                frame.normalizedHeightPixels() - placement.heightPx()
        );
        return new TilePlacement(
                placement.row(),
                placement.col(),
                x,
                y,
                placement.widthPx(),
                placement.heightPx()
        );
    }

    private void drawSlotBounds(
            Graphics2D graphics,
            TilePlacement nominalPlacement,
            TilePlacement effectivePlacement,
            SlotInspection slot
    ) {
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(NOMINAL_SLOT_COLOR);
        drawRectangle(graphics, nominalPlacement);

        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(slotColor(slot.borderStatus()));
        drawRectangle(graphics, effectivePlacement);

        if (slot.effectiveTileShiftXPx() != 0 || slot.effectiveTileShiftYPx() != 0) {
            int nominalCenterX = nominalPlacement.xPx() + (nominalPlacement.widthPx() / 2);
            int nominalCenterY = nominalPlacement.yPx() + (nominalPlacement.heightPx() / 2);
            int effectiveCenterX = effectivePlacement.xPx() + (effectivePlacement.widthPx() / 2);
            int effectiveCenterY = effectivePlacement.yPx() + (effectivePlacement.heightPx() / 2);
            graphics.setStroke(new BasicStroke(1.0f));
            graphics.setColor(SHIFT_VECTOR_COLOR);
            graphics.drawLine(nominalCenterX, nominalCenterY, effectiveCenterX, effectiveCenterY);
            graphics.fillRect(effectiveCenterX - 1, effectiveCenterY - 1, 3, 3);
        }
    }

    private Color slotColor(BorderInspectionStatus status) {
        return switch (status) {
            case SIGNATURE -> SIGNATURE_SLOT_COLOR;
            case NO_SIGNATURE -> NO_SIGNATURE_SLOT_COLOR;
            case PALETTE_REJECTED -> PALETTE_REJECTED_SLOT_COLOR;
        };
    }

    private void drawCandidateAttempt(
            Graphics2D graphics,
            FixedLayoutPlan layoutPlan,
            TilePlacement placement,
            CandidateInspection candidate
    ) {
        if (candidate.moduleSizePx() <= 0) {
            return;
        }
        CandidateGeometry geometry = candidateGeometry(layoutPlan, candidate);
        Color color = candidateColor(candidate);
        graphics.setColor(color);
        drawModuleCenters(graphics, placement, candidate, geometry);
        graphics.setStroke(new BasicStroke(2.0f));
        drawFinderWindows(graphics, placement, candidate, geometry);
    }

    private CandidateGeometry candidateGeometry(FixedLayoutPlan layoutPlan, CandidateInspection candidate) {
        int border = layoutPlan.separatorThicknessPx();
        int logicalSide = candidate.dimension() + (2 * tileCodecProfile.quietZoneModules());
        int contentWidth = logicalSide * candidate.moduleSizePx();
        int contentHeight = logicalSide * candidate.moduleSizePx();
        int innerWidth = layoutPlan.tileSlotWidthPx() - (2 * border);
        int innerHeight = layoutPlan.tileSlotHeightPx() - (2 * border);
        return new CandidateGeometry(
                border + ((innerWidth - contentWidth) / 2),
                border + ((innerHeight - contentHeight) / 2)
        );
    }

    private Color candidateColor(CandidateInspection candidate) {
        if (candidate.decodeStatus() == DecodeInspectionStatus.ACCEPTED_PAYLOAD) {
            return ACCEPTED_PAYLOAD_COLOR;
        }
        if (candidate.decodeStatus() == DecodeInspectionStatus.REJECTED_BY_TILE_OR_ENVELOPE) {
            return TILE_OR_ENVELOPE_REJECTED_COLOR;
        }
        if (candidate.status() == CandidateInspectionStatus.PALETTE_REJECTED) {
            return PALETTE_REJECTED_ATTEMPT_COLOR;
        }
        if (candidate.status() == CandidateInspectionStatus.FINDER_CANDIDATE) {
            return FINDER_CANDIDATE_COLOR;
        }
        return NO_FINDER_ATTEMPT_COLOR;
    }

    private void drawModuleCenters(
            Graphics2D graphics,
            TilePlacement placement,
            CandidateInspection candidate,
            CandidateGeometry geometry
    ) {
        int dimension = candidate.dimension();
        int stride = moduleCenterStride(dimension);
        for (int row = 0; row < dimension; row += stride) {
            for (int col = 0; col < dimension; col += stride) {
                int x = moduleCenterX(placement, candidate, geometry, col);
                int y = moduleCenterY(placement, candidate, geometry, row);
                graphics.fillRect(x, y, 1, 1);
            }
        }
    }

    private int moduleCenterStride(int dimension) {
        long moduleCenters = (long) dimension * (long) dimension;
        if (moduleCenters <= MAX_MODULE_CENTER_POINTS_PER_ATTEMPT) {
            return 1;
        }
        return (int) Math.ceil(Math.sqrt((double) moduleCenters / (double) MAX_MODULE_CENTER_POINTS_PER_ATTEMPT));
    }

    private void drawFinderWindows(
            Graphics2D graphics,
            TilePlacement placement,
            CandidateInspection candidate,
            CandidateGeometry geometry
    ) {
        int dimension = candidate.dimension();
        drawFinderWindow(graphics, placement, candidate, geometry, 0, 0);
        drawFinderWindow(graphics, placement, candidate, geometry, 0, dimension - 3);
        drawFinderWindow(graphics, placement, candidate, geometry, dimension - 3, 0);
        drawFinderWindow(graphics, placement, candidate, geometry, dimension - 3, dimension - 3);
    }

    private void drawFinderWindow(
            Graphics2D graphics,
            TilePlacement placement,
            CandidateInspection candidate,
            CandidateGeometry geometry,
            int startRow,
            int startCol
    ) {
        int quietZone = tileCodecProfile.quietZoneModules();
        int x = placement.xPx()
                + geometry.contentOffsetXPx()
                + ((startCol + quietZone) * candidate.moduleSizePx())
                + candidate.moduleCenterOffsetXPx();
        int y = placement.yPx()
                + geometry.contentOffsetYPx()
                + ((startRow + quietZone) * candidate.moduleSizePx())
                + candidate.moduleCenterOffsetYPx();
        int size = 3 * candidate.moduleSizePx();
        graphics.drawRect(x, y, Math.max(0, size - 1), Math.max(0, size - 1));
    }

    private int moduleCenterX(
            TilePlacement placement,
            CandidateInspection candidate,
            CandidateGeometry geometry,
            int moduleCol
    ) {
        return placement.xPx()
                + geometry.contentOffsetXPx()
                + ((moduleCol + tileCodecProfile.quietZoneModules()) * candidate.moduleSizePx())
                + (candidate.moduleSizePx() / 2)
                + candidate.moduleCenterOffsetXPx();
    }

    private int moduleCenterY(
            TilePlacement placement,
            CandidateInspection candidate,
            CandidateGeometry geometry,
            int moduleRow
    ) {
        return placement.yPx()
                + geometry.contentOffsetYPx()
                + ((moduleRow + tileCodecProfile.quietZoneModules()) * candidate.moduleSizePx())
                + (candidate.moduleSizePx() / 2)
                + candidate.moduleCenterOffsetYPx();
    }

    private void drawRectangle(Graphics2D graphics, TilePlacement placement) {
        graphics.drawRect(
                placement.xPx(),
                placement.yPx(),
                Math.max(0, placement.widthPx() - 1),
                Math.max(0, placement.heightPx() - 1)
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CandidateGeometry(int contentOffsetXPx, int contentOffsetYPx) {
    }
}
