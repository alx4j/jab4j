package com.alx4j.jab4j.reader.capture.media.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPolygon;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePolygon;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.TileCodecProfile;

/**
 * Projects fixed-layout module cells from canonical normalized-frame coordinates into source-image coordinates.
 */
public final class ModuleLatticeProjector {

    private static final int HOMOGRAPHY_PARAMETER_COUNT = 9;
    private static final double DENOMINATOR_EPSILON = 1.0e-10d;

    private final FixedLayoutPlanner layoutPlanner;

    /**
     * Creates a projector using the default fixed-layout planner.
     */
    public ModuleLatticeProjector() {
        this(new FixedLayoutPlanner());
    }

    /**
     * Creates a projector with an explicit fixed-layout planner for focused tests.
     *
     * @param layoutPlanner planner used to resolve tile slots
     */
    public ModuleLatticeProjector(FixedLayoutPlanner layoutPlanner) {
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
    }

    /**
     * Builds and projects every logical module cell for the selected layout and tile side version.
     *
     * @param layoutProfile selected rendered-frame layout profile
     * @param tileCodecProfile tile codec profile that supplies quiet-zone and dimension rules
     * @param sideVersion deterministic tile side version used for this diagnostic lattice
     * @param geometryCandidate accepted geometry candidate with row-major homography parameters
     * @param centralScale central sampling polygon scale, between zero and one
     * @return projected module cells in deterministic tile-row/module-row order
     */
    public List<ProjectedModuleCell> project(
            LayoutProfile layoutProfile,
            TileCodecProfile tileCodecProfile,
            int sideVersion,
            GeometryCandidateEvidence geometryCandidate,
            double centralScale
    ) {
        return project(layoutProfile, tileCodecProfile, sideVersion, geometryCandidate, centralScale, null);
    }

    /**
     * Builds and projects every logical module cell, optionally applying bounded local-grid correction.
     *
     * @param layoutProfile selected rendered-frame layout profile
     * @param tileCodecProfile tile codec profile that supplies quiet-zone and dimension rules
     * @param sideVersion deterministic tile side version used for this diagnostic lattice
     * @param geometryCandidate accepted geometry candidate with row-major homography parameters
     * @param centralScale central sampling polygon scale, between zero and one
     * @param localRefinement optional local-grid correction to apply after global projection
     * @return projected module cells in deterministic tile-row/module-row order
     */
    public List<ProjectedModuleCell> project(
            LayoutProfile layoutProfile,
            TileCodecProfile tileCodecProfile,
            int sideVersion,
            GeometryCandidateEvidence geometryCandidate,
            double centralScale,
            LocalGridRefinement localRefinement
    ) {
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
        Objects.requireNonNull(geometryCandidate, "geometryCandidate must not be null");
        if (!Double.isFinite(centralScale) || centralScale <= 0.0d || centralScale > 1.0d) {
            throw new IllegalArgumentException("centralScale must be greater than zero and at most one");
        }
        int dimension = tileCodecProfile.dimensionForSideVersion(sideVersion);
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(layoutProfile);
        ModuleGeometry moduleGeometry = moduleGeometry(layoutPlan, tileCodecProfile, dimension);

        List<ProjectedModuleCell> cells = new ArrayList<>(
                layoutPlan.tilePlacements().size() * dimension * dimension
        );
        List<Double> transformParameters = requireTransformParameters(geometryCandidate);
        for (int tileIndex = 0; tileIndex < layoutPlan.tilePlacements().size(); tileIndex++) {
            TilePlacement placement = layoutPlan.tilePlacements().get(tileIndex);
            for (int moduleRow = 0; moduleRow < dimension; moduleRow++) {
                for (int moduleCol = 0; moduleCol < dimension; moduleCol++) {
                    CanonicalPolygon canonicalPolygon = canonicalCell(placement, moduleGeometry, moduleRow, moduleCol);
                    CanonicalPolygon innerCanonicalPolygon = shrink(canonicalPolygon, centralScale);
                    int globalModuleX = (placement.col() * dimension) + moduleCol;
                    int globalModuleY = (placement.row() * dimension) + moduleRow;
                    cells.add(new ProjectedModuleCell(
                            tileIndex,
                            globalModuleX,
                            globalModuleY,
                            moduleCol,
                            moduleRow,
                            canonicalPolygon,
                            innerCanonicalPolygon,
                            sourcePolygon(transformParameters, canonicalPolygon, localRefinement),
                            sourcePolygon(transformParameters, innerCanonicalPolygon, localRefinement)
                    ));
                }
            }
        }
        return List.copyOf(cells);
    }

    /**
     * Projects deterministic sample points inside the cell's central canonical polygon.
     *
     * @param cell projected module cell
     * @param geometryCandidate geometry candidate with the same transform used to project the cell
     * @param gridSize positive sample grid side length
     * @return source-space sample points in row-major grid order
     */
    public List<SourcePoint> sourceSamplePoints(
            ProjectedModuleCell cell,
            GeometryCandidateEvidence geometryCandidate,
            int gridSize
    ) {
        Objects.requireNonNull(cell, "cell must not be null");
        Objects.requireNonNull(geometryCandidate, "geometryCandidate must not be null");
        if (gridSize <= 0) {
            throw new IllegalArgumentException("gridSize must be positive");
        }
        List<SourcePoint> vertices = cell.innerSourcePolygon().vertices();
        if (vertices.size() != 4) {
            throw new IllegalArgumentException("projected module cells must have four inner vertices");
        }

        List<SourcePoint> samples = new ArrayList<>(gridSize * gridSize);
        for (int row = 0; row < gridSize; row++) {
            double vertical = (row + 0.5d) / gridSize;
            for (int col = 0; col < gridSize; col++) {
                double horizontal = (col + 0.5d) / gridSize;
                SourcePoint top = interpolate(vertices.get(0), vertices.get(1), horizontal);
                SourcePoint bottom = interpolate(vertices.get(3), vertices.get(2), horizontal);
                samples.add(interpolate(top, bottom, vertical));
            }
        }
        return List.copyOf(samples);
    }

    /**
     * Projects one canonical point through the geometry candidate transform.
     *
     * @param geometryCandidate geometry candidate with row-major homography parameters
     * @param point canonical point
     * @return source-image point
     */
    public SourcePoint project(GeometryCandidateEvidence geometryCandidate, CanonicalPoint point) {
        return project(requireTransformParameters(geometryCandidate), point);
    }

    private ModuleGeometry moduleGeometry(
            FixedLayoutPlan layoutPlan,
            TileCodecProfile tileCodecProfile,
            int dimension
    ) {
        int border = layoutPlan.separatorThicknessPx();
        int innerWidth = layoutPlan.tileSlotWidthPx() - (2 * border);
        int innerHeight = layoutPlan.tileSlotHeightPx() - (2 * border);
        int logicalSide = dimension + (2 * tileCodecProfile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        if (moduleSize <= 0) {
            throw new IllegalArgumentException("layout cannot fit the selected tile side version");
        }
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        return new ModuleGeometry(moduleSize, offsetX, offsetY, tileCodecProfile.quietZoneModules());
    }

    private CanonicalPolygon canonicalCell(
            TilePlacement placement,
            ModuleGeometry geometry,
            int moduleRow,
            int moduleCol
    ) {
        double left = placement.xPx()
                + geometry.offsetXPx()
                + ((moduleCol + geometry.quietZoneModules()) * geometry.moduleSizePx());
        double top = placement.yPx()
                + geometry.offsetYPx()
                + ((moduleRow + geometry.quietZoneModules()) * geometry.moduleSizePx());
        double right = left + geometry.moduleSizePx();
        double bottom = top + geometry.moduleSizePx();
        return new CanonicalPolygon(List.of(
                new CanonicalPoint(left, top),
                new CanonicalPoint(right, top),
                new CanonicalPoint(right, bottom),
                new CanonicalPoint(left, bottom)
        ));
    }

    private CanonicalPolygon shrink(CanonicalPolygon polygon, double scale) {
        List<CanonicalPoint> vertices = polygon.vertices();
        double centerX = 0.0d;
        double centerY = 0.0d;
        for (CanonicalPoint vertex : vertices) {
            centerX += vertex.x();
            centerY += vertex.y();
        }
        centerX /= vertices.size();
        centerY /= vertices.size();

        List<CanonicalPoint> shrunken = new ArrayList<>(vertices.size());
        for (CanonicalPoint vertex : vertices) {
            shrunken.add(new CanonicalPoint(
                    centerX + ((vertex.x() - centerX) * scale),
                    centerY + ((vertex.y() - centerY) * scale)
            ));
        }
        return new CanonicalPolygon(shrunken);
    }

    private SourcePolygon sourcePolygon(
            List<Double> transformParameters,
            CanonicalPolygon polygon,
            LocalGridRefinement localRefinement
    ) {
        List<SourcePoint> vertices = new ArrayList<>(polygon.vertices().size());
        for (CanonicalPoint vertex : polygon.vertices()) {
            SourcePoint projected = project(transformParameters, vertex);
            vertices.add(localRefinement == null ? projected : localRefinement.apply(vertex, projected));
        }
        return new SourcePolygon(vertices);
    }

    private SourcePoint project(List<Double> transformParameters, CanonicalPoint point) {
        Objects.requireNonNull(point, "point must not be null");
        double denominator = (transformParameters.get(6) * point.x())
                + (transformParameters.get(7) * point.y())
                + transformParameters.get(8);
        if (Math.abs(denominator) < DENOMINATOR_EPSILON) {
            throw new IllegalArgumentException("homography mapping reached a degenerate point");
        }
        return new SourcePoint(
                ((transformParameters.get(0) * point.x())
                        + (transformParameters.get(1) * point.y())
                        + transformParameters.get(2)) / denominator,
                ((transformParameters.get(3) * point.x())
                        + (transformParameters.get(4) * point.y())
                        + transformParameters.get(5)) / denominator
        );
    }

    private List<Double> requireTransformParameters(GeometryCandidateEvidence geometryCandidate) {
        Objects.requireNonNull(geometryCandidate, "geometryCandidate must not be null");
        List<Double> transformParameters = geometryCandidate.transformParameters();
        if (transformParameters.size() != HOMOGRAPHY_PARAMETER_COUNT) {
            throw new IllegalArgumentException("geometryCandidate must provide 9 homography parameters");
        }
        return transformParameters;
    }

    private CanonicalPoint interpolate(CanonicalPoint first, CanonicalPoint second, double fraction) {
        return new CanonicalPoint(
                first.x() + ((second.x() - first.x()) * fraction),
                first.y() + ((second.y() - first.y()) * fraction)
        );
    }

    private SourcePoint interpolate(SourcePoint first, SourcePoint second, double fraction) {
        return new SourcePoint(
                first.x() + ((second.x() - first.x()) * fraction),
                first.y() + ((second.y() - first.y()) * fraction)
        );
    }

    /**
     * One projected logical module cell with full and central source-space footprints.
     *
     * @param tileIndex zero-based tile slot index
     * @param moduleX zero-based global module x coordinate across tile slots
     * @param moduleY zero-based global module y coordinate across tile slots
     * @param tileModuleX zero-based module x coordinate inside the tile
     * @param tileModuleY zero-based module y coordinate inside the tile
     * @param canonicalPolygon full canonical module polygon
     * @param innerCanonicalPolygon shrunken canonical sampling polygon
     * @param sourcePolygon full source-space module polygon
     * @param innerSourcePolygon shrunken source-space sampling polygon
     */
    public record ProjectedModuleCell(
            int tileIndex,
            int moduleX,
            int moduleY,
            int tileModuleX,
            int tileModuleY,
            CanonicalPolygon canonicalPolygon,
            CanonicalPolygon innerCanonicalPolygon,
            SourcePolygon sourcePolygon,
            SourcePolygon innerSourcePolygon
    ) {

        /**
         * Creates a validated projected module cell.
         *
         * @param tileIndex zero-based tile index
         * @param moduleX global module x coordinate
         * @param moduleY global module y coordinate
         * @param tileModuleX tile-local module x coordinate
         * @param tileModuleY tile-local module y coordinate
         * @param canonicalPolygon full canonical polygon
         * @param innerCanonicalPolygon inner canonical polygon
         * @param sourcePolygon full source-space polygon
         * @param innerSourcePolygon inner source-space polygon
         */
        public ProjectedModuleCell {
            if (tileIndex < 0 || moduleX < 0 || moduleY < 0 || tileModuleX < 0 || tileModuleY < 0) {
                throw new IllegalArgumentException("module cell coordinates must be non-negative");
            }
            Objects.requireNonNull(canonicalPolygon, "canonicalPolygon must not be null");
            Objects.requireNonNull(innerCanonicalPolygon, "innerCanonicalPolygon must not be null");
            Objects.requireNonNull(sourcePolygon, "sourcePolygon must not be null");
            Objects.requireNonNull(innerSourcePolygon, "innerSourcePolygon must not be null");
        }
    }

    private record ModuleGeometry(int moduleSizePx, int offsetXPx, int offsetYPx, int quietZoneModules) {
    }
}
