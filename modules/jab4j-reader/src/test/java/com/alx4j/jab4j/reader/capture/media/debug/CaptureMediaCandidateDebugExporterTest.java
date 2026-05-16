package com.alx4j.jab4j.reader.capture.media.debug;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.cv.CvTileSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler;

@DisplayName("Capture media candidate debug exporter")
class CaptureMediaCandidateDebugExporterTest {

    private static final int FRAME_WIDTH = 1280;
    private static final int FRAME_HEIGHT = 720;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Sidecar includes backend, candidate, sampling evidence, and diagnostic fields")
    void sidecarIncludesBackendCandidateSamplingEvidenceAndDiagnosticFields() throws Exception {
        CaptureMediaCandidateDebugExporter exporter = new CaptureMediaCandidateDebugExporter(
                new CaptureMediaTilePayloadSampler((frame, layoutPlan) -> Optional.of(samplingEvidence()))
        );
        NormalizedCaptureFrame frame = normalizedFrame();

        CaptureMediaCandidateDebugExporter.CandidateDebugExport exported = exporter.export(
                frame,
                tempDir,
                "boofcv-test",
                "1.2.3"
        );

        String metadata = Files.readString(exported.metadataPath());
        assertAll(
                () -> assertTrue(metadata.contains("cv.backendId=boofcv-test")),
                () -> assertTrue(metadata.contains("cv.backendVersion=1.2.3")),
                () -> assertTrue(metadata.contains("candidate.rank=1")),
                () -> assertTrue(metadata.contains("candidate.sourceBounds.rightExclusivePx=1280.0")),
                () -> assertTrue(metadata.contains("candidate.corners.bottomRightY=720.0")),
                () -> assertTrue(metadata.contains("perspective.frameCoverageRatio=1.0")),
                () -> assertTrue(metadata.contains("sampler.evidence.available=true")),
                () -> assertTrue(metadata.contains("sampler.evidence.backendId=boofcv-test")),
                () -> assertTrue(metadata.contains("sampler.gridPhase.available=true")),
                () -> assertTrue(metadata.contains("sampler.gridPhase.offsetXPx=1.25")),
                () -> assertTrue(metadata.contains("sampler.evidence.tile.0.moduleCenterOffsetXPx=0.5")),
                () -> assertTrue(metadata.contains("sampler.evidence.metric.gridSharpness=0.73")),
                () -> assertTrue(metadata.contains("sampler.tileDecode.attemptCount=")),
                () -> assertTrue(metadata.contains("sampler.envelope.rejectedAttemptCount=")),
                () -> assertTrue(metadata.contains("diagnostic.selectedPublicCode=SCREEN_OR_FRAME_NOT_FOUND"))
        );
    }

    private NormalizedCaptureFrame normalizedFrame() {
        int[] pixels = new int[FRAME_WIDTH * FRAME_HEIGHT];
        Arrays.fill(pixels, 0xFF000000);
        return new NormalizedCaptureFrame(
                "candidate-source.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                "png",
                "abc123",
                "debug-low-density",
                FrameCorners.exactFrame(FRAME_WIDTH, FRAME_HEIGHT),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private CvSamplingEvidence samplingEvidence() {
        return new CvSamplingEvidence(
                "boofcv-test",
                Optional.of(new CvGridPhase(
                        1.25d,
                        -0.75d,
                        0.91d,
                        0.86d,
                        OptionalInt.of(0xFFFFFFFF),
                        OptionalInt.of(0xFF000000)
                )),
                List.of(new CvTileSamplingEvidence(
                        0,
                        0.50d,
                        -0.25d,
                        0.93d,
                        0.88d,
                        OptionalInt.of(0xFFFFFFFF),
                        OptionalInt.of(0xFF000000),
                        Map.of("tileSharpness", 0.81d)
                )),
                Map.of("gridSharpness", 0.73d)
        );
    }
}
