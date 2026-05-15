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
 * Test-scoped BoofCV capture-media backend skeleton used for explicit adapter smoke selection.
 */
final class BoofCvCaptureMediaCvBackend implements CaptureMediaCvBackend {

    private static final String FAILURE_MESSAGE = "Capture-media CV backend failed while evaluating the frame";
    private static final String NOT_FOUND_MESSAGE =
            "Media normalization did not find a clean supported rendered frame region";
    private static final CvBackendIdentity IDENTITY = new CvBackendIdentity(
            "boofcv",
            Optional.of("org.boofcv:boofcv-feature"),
            Optional.ofNullable(Planar.class.getPackage().getImplementationVersion()),
            List.of(
                    "test-scope-adapter-skeleton",
                    "argb-to-planar-rgb-copy",
                    "grayscale-threshold-contour-smoke",
                    "stable-backend-failure-mapping"
            )
    );

    private final boolean failBeforeDetection;

    /**
     * Creates a BoofCV skeleton backend for deterministic test and manual smoke selection.
     */
    BoofCvCaptureMediaCvBackend() {
        this(false);
    }

    /**
     * Creates a BoofCV skeleton backend with an optional forced runtime failure path for tests.
     *
     * @param failBeforeDetection whether detection should fail before invoking BoofCV primitives
     */
    BoofCvCaptureMediaCvBackend(boolean failBeforeDetection) {
        this.failBeforeDetection = failBeforeDetection;
    }

    /**
     * Returns metadata for the test-scoped BoofCV adapter skeleton.
     *
     * @return BoofCV backend identity metadata
     */
    @Override
    public CvBackendIdentity identity() {
        return IDENTITY;
    }

    /**
     * Converts the frame to BoofCV RGB, runs a deterministic primitive smoke path, and rejects until real detection is
     * implemented.
     *
     * @param frame decoded media input frame
     * @return stable rejection or backend-failure result
     */
    @Override
    public CvDetectionResult detect(MediaInputFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        try {
            if (failBeforeDetection) {
                throw new IllegalStateException("forced BoofCV adapter skeleton failure");
            }
            BoofCvArgbToPlanarRgbConverter.ConvertedArgbImage converted =
                    BoofCvArgbToPlanarRgbConverter.convert(frame);
            BoofCvArgbToPlanarRgbConverter.ConversionMetadata conversion = converted.metadata();
            GrayU8 gray = new GrayU8(conversion.widthPixels(), conversion.heightPixels());
            GConvertImage.average(converted.image(), gray);
            GrayU8 binary = ThresholdImageOps.threshold(gray, null, 128, false);
            List<Contour> contours = BinaryImageOps.contourExternal(binary, ConnectRule.EIGHT);

            return CvDetectionResult.rejected(
                    CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                    rejectionMetrics(conversion, contours),
                    NOT_FOUND_MESSAGE
            );
        } catch (RuntimeException exception) {
            return CvDetectionResult.backendFailure(
                    Map.of("backendFailureCount", 1.0d),
                    FAILURE_MESSAGE
            );
        }
    }

    private Map<String, Double> rejectionMetrics(
            BoofCvArgbToPlanarRgbConverter.ConversionMetadata conversion,
            List<Contour> contours
    ) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("boofCvInputWidthPixels", (double) conversion.widthPixels());
        metrics.put("boofCvInputHeightPixels", (double) conversion.heightPixels());
        metrics.put("boofCvConversionCopyCount", 1.0d);
        metrics.put("boofCvThreshold", 128.0d);
        metrics.put("boofCvExternalContourCount", (double) contours.size());
        return Map.copyOf(metrics);
    }
}
