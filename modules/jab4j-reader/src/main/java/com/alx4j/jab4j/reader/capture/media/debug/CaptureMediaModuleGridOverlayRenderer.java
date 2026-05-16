package com.alx4j.jab4j.reader.capture.media.debug;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.LayoutProfile;
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
    private static final int MAX_MODULE_CENTER_POINTS_PER_ATTEMPT = 2_500;
    private static final double MAX_PROPORTIONAL_LAYOUT_SCALE_ERROR = 0.01d;

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
