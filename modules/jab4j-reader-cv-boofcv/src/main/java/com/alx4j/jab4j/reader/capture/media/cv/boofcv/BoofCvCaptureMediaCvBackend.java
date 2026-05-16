package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.cv.CvBackendIdentity;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.core.image.GConvertImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;

/**
 * Optional shaded BoofCV backend registered through the reader CV service boundary.
 */
public final class BoofCvCaptureMediaCvBackend implements CaptureMediaCvBackend {

    private static final int LIGHT_THRESHOLD = 178;
    private static final String NOT_FOUND_MESSAGE =
            "BoofCV capture-media backend is installed, but full candidate recovery is not enabled in this slice";
    private static final String FAILURE_MESSAGE = "Capture-media CV backend failed while evaluating the frame";
    private static final CvBackendIdentity IDENTITY = new CvBackendIdentity(
            "boofcv",
            Optional.of("com.alx4j:jab4j-reader-cv-boofcv"),
            Optional.ofNullable(BoofCvCaptureMediaCvBackend.class.getPackage().getImplementationVersion()),
            List.of(
                    "optional-shaded-adapter",
                    "argb-to-planar-rgb-copy",
                    "boofcv-grayscale-threshold-probe",
                    "stable-backend-failure-mapping",
                    "no-default-selection"
            )
    );

    /**
     * Creates a BoofCV backend instance for explicit developer selection.
     */
    public BoofCvCaptureMediaCvBackend() {
    }

    /**
     * Returns stable metadata for the optional shaded BoofCV adapter.
     *
     * @return backend identity metadata
     */
    @Override
    public CvBackendIdentity identity() {
        return IDENTITY;
    }

    /**
     * Runs a minimal BoofCV image probe and maps the result to a backend-neutral diagnostic.
     *
     * @param frame decoded media input frame
     * @return backend-neutral rejection or backend-failure result
     */
    @Override
    public CvDetectionResult detect(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        try {
            return CvDetectionResult.rejected(
                    CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                    probe(frame).metrics(),
                    NOT_FOUND_MESSAGE
            );
        } catch (RuntimeException exception) {
            return CvDetectionResult.backendFailure(Map.of("backendFailureCount", 1.0d), FAILURE_MESSAGE);
        }
    }

    private ProbeResult probe(MediaInputFrame frame) {
        Planar<GrayU8> rgb = toBoofRgb(frame);
        GrayU8 gray = new GrayU8(frame.widthPixels(), frame.heightPixels());
        GConvertImage.average(rgb, gray);
        GrayU8 binary = ThresholdImageOps.threshold(gray, null, LIGHT_THRESHOLD, false);
        List<Contour> contours = BinaryImageOps.contourExternal(binary, ConnectRule.EIGHT);

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("boofCvInputWidthPixels", (double) frame.widthPixels());
        metrics.put("boofCvInputHeightPixels", (double) frame.heightPixels());
        metrics.put("boofCvConversionCopyCount", 1.0d);
        metrics.put("boofCvGrayscaleConversionCount", 1.0d);
        metrics.put("boofCvThreshold", (double) LIGHT_THRESHOLD);
        metrics.put("boofCvExternalContourCount", (double) contours.size());
        return new ProbeResult(Map.copyOf(metrics));
    }

    private Planar<GrayU8> toBoofRgb(MediaInputFrame frame) {
        Planar<GrayU8> rgb = new Planar<>(GrayU8.class, frame.widthPixels(), frame.heightPixels(), 3);
        int[] rowPixels = new int[frame.widthPixels()];
        for (int row = 0; row < frame.heightPixels(); row++) {
            frame.copyArgbRow(row, 0, rowPixels, 0, frame.widthPixels());
            for (int col = 0; col < frame.widthPixels(); col++) {
                int argb = rowPixels[col];
                rgb.getBand(0).set(col, row, (argb >>> 16) & 0xFF);
                rgb.getBand(1).set(col, row, (argb >>> 8) & 0xFF);
                rgb.getBand(2).set(col, row, argb & 0xFF);
            }
        }
        return rgb;
    }

    private record ProbeResult(Map<String, Double> metrics) {

        ProbeResult {
            metrics = Map.copyOf(Objects.requireNonNull(metrics, "metrics must not be null"));
        }
    }
}
