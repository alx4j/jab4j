package com.alx4j.jab4j.reader.capture.media.geometry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.evidence.CanonicalPoint;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitModelType;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;
import com.alx4j.jab4j.reader.capture.media.evidence.SourcePoint;
import com.alx4j.jab4j.reader.capture.media.geometry.ModuleLatticeProjector.ProjectedModuleCell;
import com.alx4j.jab4j.tile.TileCodecProfiles;

@DisplayName("Module lattice projector")
class ModuleLatticeProjectorTest {

    private static final String PIXEL_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private final ModuleLatticeProjector projector = new ModuleLatticeProjector();

    @Test
    @DisplayName("Shrinks central module polygons in canonical space before source projection")
    void shrinksCentralModulePolygonsInCanonicalSpace() {
        List<ProjectedModuleCell> cells = projector.project(
                layoutProfile(),
                TileCodecProfiles.balancedV1(),
                1,
                geometryCandidate(1, List.of(
                        1.0d, 0.0d, 0.0d,
                        0.0d, 1.0d, 0.0d,
                        0.0d, 0.0d, 1.0d
                )),
                0.50d
        );

        ProjectedModuleCell first = cells.get(0);
        assertAll(
                () -> assertEquals(441, cells.size()),
                () -> assertPoint(8.0d, 8.0d, first.canonicalPolygon().vertices().get(0)),
                () -> assertPoint(12.0d, 12.0d, first.canonicalPolygon().vertices().get(2)),
                () -> assertPoint(9.0d, 9.0d, first.innerSourcePolygon().vertices().get(0)),
                () -> assertPoint(11.0d, 11.0d, first.innerSourcePolygon().vertices().get(2))
        );
    }

    @Test
    @DisplayName("Maps projected module polygons through geometry evidence transform parameters")
    void mapsProjectedModulePolygonsThroughGeometryEvidenceTransformParameters() {
        List<ProjectedModuleCell> cells = projector.project(
                layoutProfile(),
                TileCodecProfiles.balancedV1(),
                1,
                geometryCandidate(1, List.of(
                        2.0d, 0.0d, 5.0d,
                        0.0d, 3.0d, 7.0d,
                        0.0d, 0.0d, 1.0d
                )),
                0.50d
        );

        ProjectedModuleCell first = cells.get(0);
        assertAll(
                () -> assertPoint(21.0d, 31.0d, first.sourcePolygon().vertices().get(0)),
                () -> assertPoint(29.0d, 43.0d, first.sourcePolygon().vertices().get(2)),
                () -> assertPoint(23.0d, 34.0d, first.innerSourcePolygon().vertices().get(0)),
                () -> assertPoint(27.0d, 40.0d, first.innerSourcePolygon().vertices().get(2))
        );
    }

    private void assertPoint(double expectedX, double expectedY, SourcePoint point) {
        assertAll(
                () -> assertEquals(expectedX, point.x(), 0.000001d),
                () -> assertEquals(expectedY, point.y(), 0.000001d)
        );
    }

    private void assertPoint(double expectedX, double expectedY, CanonicalPoint point) {
        assertAll(
                () -> assertEquals(expectedX, point.x(), 0.000001d),
                () -> assertEquals(expectedY, point.y(), 0.000001d)
        );
    }

    private GeometryCandidateEvidence geometryCandidate(int rank, List<Double> transformParameters) {
        return new GeometryCandidateEvidence(
                CaptureMediaCandidateId.geometryCandidate(patternCandidateId(), rank),
                rank,
                GeometryFitStatus.ACCEPTED,
                GeometryFitModelType.HOMOGRAPHY,
                "pattern-feature-canonical-pixels:" + layoutProfile().profileId(),
                "source-image-pixels",
                transformParameters,
                1.0d,
                List.of(),
                true,
                4,
                4,
                4,
                4,
                0,
                0,
                reprojectionMetrics(),
                1.0d,
                1.0d,
                true,
                false,
                List.of(CaptureMediaEvidenceReasonCode.NORMALIZED_CANDIDATE_ONLY)
        );
    }

    private CaptureMediaCandidateId patternCandidateId() {
        return CaptureMediaCandidateId.patternEvidence(CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                "projector-test.png",
                PIXEL_SHA256,
                1,
                1,
                1
        ));
    }

    private ReprojectionMetrics reprojectionMetrics() {
        return new ReprojectionMetrics(
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                List.of(0.0d),
                List.of(0.0d)
        );
    }

    private LayoutProfile layoutProfile() {
        return new LayoutProfile(
                "source-space-test-layout",
                1,
                1,
                100,
                100,
                0,
                0,
                "solidWhite",
                0,
                0,
                "black",
                "preserveAspect"
        );
    }
}
