package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import boofcv.abst.distort.FDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.geo.h.HomographyDirectLinearTransform;
import boofcv.alg.interpolate.InterpolationType;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.homography.UtilHomography_F64;
import org.ejml.data.DMatrixRMaj;

/**
 * Test-scoped BoofCV perspective corrector from source-space frame corners to a rendered layout profile.
 */
final class BoofCvPerspectiveCorrector {

    /**
     * Corrects a source-space quadrilateral into the selected layout profile and packages it as a CV-normalized frame.
     *
     * @param frame decoded source frame
     * @param layoutProfile selected rendered layout profile
     * @param frameCorners detected source-space frame corners
     * @param qualityMetrics deterministic quality metrics to preserve with the corrected frame
     * @return BoofCV-corrected normalized frame candidate
     */
    CvNormalizedFrame correct(
            MediaInputFrame frame,
            LayoutProfile layoutProfile,
            FrameCorners frameCorners,
            CaptureMediaQualityMetrics qualityMetrics
    ) {
        return new CvNormalizedFrame(
                layoutProfile,
                frameCorners,
                qualityMetrics,
                correctArgb(frame, layoutProfile, frameCorners)
        );
    }

    /**
     * Corrects a source-space quadrilateral into selected layout dimensions using BoofCV bilinear interpolation.
     *
     * @param frame decoded source frame
     * @param layoutProfile selected rendered layout profile
     * @param frameCorners detected source-space frame corners
     * @return row-major corrected ARGB pixels
     */
    int[] correctArgb(MediaInputFrame frame, LayoutProfile layoutProfile, FrameCorners frameCorners) {
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(layoutProfile, "layoutProfile must not be null");
        Objects.requireNonNull(frameCorners, "frameCorners must not be null");
        if (layoutProfile.frameWidthPx() < 2 || layoutProfile.frameHeightPx() < 2) {
            throw new IllegalArgumentException("layout profile dimensions must be at least 2x2");
        }

        BoofCvArgbToPlanarRgbConverter.ConvertedArgbImage source =
                BoofCvArgbToPlanarRgbConverter.convert(frame);
        Planar<GrayU8> corrected = new Planar<>(
                GrayU8.class,
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                3
        );
        new FDistort(source.image(), corrected)
                .interp(InterpolationType.BILINEAR)
                .borderExt()
                .transform(new PixelTransformHomography_F32(outputToSourceHomography(layoutProfile, frameCorners)))
                .apply();
        return toArgbPixels(corrected);
    }

    private Homography2D_F64 outputToSourceHomography(LayoutProfile layoutProfile, FrameCorners frameCorners) {
        double right = layoutProfile.frameWidthPx() - 1.0d;
        double bottom = layoutProfile.frameHeightPx() - 1.0d;
        List<AssociatedPair> pairs = List.of(
                new AssociatedPair(0.0d, 0.0d, frameCorners.topLeftX(), frameCorners.topLeftY()),
                new AssociatedPair(right, 0.0d, frameCorners.topRightX(), frameCorners.topRightY()),
                new AssociatedPair(right, bottom, frameCorners.bottomRightX(), frameCorners.bottomRightY()),
                new AssociatedPair(0.0d, bottom, frameCorners.bottomLeftX(), frameCorners.bottomLeftY())
        );
        DMatrixRMaj matrix = new DMatrixRMaj(3, 3);
        if (!new HomographyDirectLinearTransform(true).process(pairs, matrix)) {
            throw new IllegalArgumentException("perspective corners must produce a valid BoofCV homography");
        }
        return UtilHomography_F64.convert(matrix, null);
    }

    private int[] toArgbPixels(Planar<GrayU8> image) {
        int[] pixels = new int[image.width * image.height];
        GrayU8 red = image.getBand(0);
        GrayU8 green = image.getBand(1);
        GrayU8 blue = image.getBand(2);
        for (int row = 0; row < image.height; row++) {
            for (int col = 0; col < image.width; col++) {
                pixels[(row * image.width) + col] = 0xFF000000
                        | (red.get(col, row) << 16)
                        | (green.get(col, row) << 8)
                        | blue.get(col, row);
            }
        }
        return pixels;
    }
}
