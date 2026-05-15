package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.core.image.GConvertImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F32;
import georegression.struct.point.Point2D_F32;

@DisplayName("BoofCV dependency smoke")
class BoofCvDependencySmokeTest {

    @Test
    @DisplayName("Selected BoofCV artifacts provide planned CV primitives on the test classpath")
    void selectedBoofCvArtifactsProvidePlannedCvPrimitivesOnTestClasspath() {
        Planar<GrayU8> rgb = new Planar<>(GrayU8.class, 4, 4, 3);
        paintWhiteBlock(rgb);
        GrayU8 gray = new GrayU8(4, 4);

        GConvertImage.average(rgb, gray);
        GrayU8 binary = ThresholdImageOps.threshold(gray, null, 128, false);
        List<Contour> contours = BinaryImageOps.contourExternal(binary, ConnectRule.EIGHT);

        Point2D_F32 mapped = new Point2D_F32();
        PixelTransformHomography_F32 transform = new PixelTransformHomography_F32(
                new Homography2D_F32(1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 2.0f, 0.0f, 0.0f, 1.0f)
        );
        transform.compute(3, 4, mapped);

        assertAll(
                () -> assertEquals(255, gray.get(1, 1)),
                () -> assertEquals(1, binary.get(1, 1)),
                () -> assertEquals(0, binary.get(0, 0)),
                () -> assertFalse(contours.isEmpty()),
                () -> assertEquals(4.0f, mapped.x, 0.0001f),
                () -> assertEquals(6.0f, mapped.y, 0.0001f),
                () -> assertDoesNotThrow(() -> Class.forName("boofcv.factory.feature.detect.line.FactoryDetectLine")),
                () -> assertDoesNotThrow(() -> Class.forName("boofcv.factory.feature.detect.interest.FactoryDetectPoint")),
                () -> assertTrue(Planar.class.getName().startsWith("boofcv."))
        );
    }

    private void paintWhiteBlock(Planar<GrayU8> rgb) {
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 2; x++) {
                rgb.getBand(0).set(x, y, 255);
                rgb.getBand(1).set(x, y, 255);
                rgb.getBand(2).set(x, y, 255);
            }
        }
    }
}
