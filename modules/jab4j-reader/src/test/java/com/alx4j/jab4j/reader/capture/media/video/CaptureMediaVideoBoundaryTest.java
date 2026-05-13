package com.alx4j.jab4j.reader.capture.media.video;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;

@DisplayName("Capture media direct-video adapter boundary")
class CaptureMediaVideoBoundaryTest {

    @Test
    @DisplayName("Conservative direct-video limits are explicit and validated")
    void conservativeDirectVideoLimitsAreExplicitAndValidated() {
        CaptureMediaVideoLimits limits = CaptureMediaVideoLimits.conservativeDefaults();

        assertAll(
                () -> assertEquals(512L * 1024L * 1024L, limits.maxFileSizeBytes()),
                () -> assertEquals(10L * 60L * 1000L, limits.maxDurationMillis()),
                () -> assertEquals(3840, limits.maxWidthPixels()),
                () -> assertEquals(2160, limits.maxHeightPixels()),
                () -> assertEquals(36_000L, limits.maxContainerFrameCount()),
                () -> assertEquals(1_200, limits.maxSampledFrameCount()),
                () -> assertEquals(100L, limits.minSampleIntervalMillis()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaptureMediaVideoLimits(1L, 1L, 1, 1, 2L, 3, 0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaptureMediaVideoLimits(0L, 1L, 1, 1, 1L, 1, 0L))
        );
    }

    @Test
    @DisplayName("Video frame model preserves timing metadata and protects pixel buffers")
    void videoFrameModelPreservesTimingMetadataAndProtectsPixelBuffers() {
        int[] pixels = { 0xFF000000, 0xFFFFFFFF };
        CaptureMediaVideoFrame frame = new CaptureMediaVideoFrame(
                "capture.mp4#frame-12",
                0,
                400L,
                12L,
                2,
                1,
                "argb",
                pixels
        );
        pixels[0] = 0xFFFF0000;
        int[] copiedPixels = frame.copyArgbPixels();
        copiedPixels[1] = 0xFF00FF00;

        assertAll(
                () -> assertEquals("capture.mp4#frame-12", frame.sourceId()),
                () -> assertEquals(0, frame.callerOrder()),
                () -> assertEquals(400L, frame.timestampMillis()),
                () -> assertEquals(12L, frame.frameNumber()),
                () -> assertEquals(2, frame.widthPixels()),
                () -> assertEquals(1, frame.heightPixels()),
                () -> assertEquals("argb", frame.pixelFormat()),
                () -> assertEquals(0xFF000000, frame.argbPixelAt(0, 0)),
                () -> assertArrayEquals(new int[] { 0xFF000000, 0xFFFFFFFF }, frame.copyArgbPixels())
        );
    }

    @Test
    @DisplayName("Adapter result requires contiguous ordered frames and carries optional metadata")
    void adapterResultRequiresContiguousOrderedFramesAndCarriesOptionalMetadata() {
        CaptureMediaVideoFrame first = frame(0, 0L, 0L);
        CaptureMediaVideoFrame second = frame(1, 100L, 3L);

        CaptureMediaVideoFrameReadResult result = new CaptureMediaVideoFrameReadResult(
                List.of(first, second),
                List.of(),
                OptionalLong.of(1000L),
                OptionalLong.of(30L),
                Optional.of("mp4"),
                Optional.of("h264")
        );

        assertAll(
                () -> assertEquals(List.of(first, second), result.frames()),
                () -> assertEquals(1000L, result.durationMillis().orElseThrow()),
                () -> assertEquals(30L, result.containerFrameCount().orElseThrow()),
                () -> assertEquals("mp4", result.containerName().orElseThrow()),
                () -> assertEquals("h264", result.codecName().orElseThrow()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureMediaVideoFrameReadResult.fromFrames(List.of(second, first), List.of()))
        );
    }

    @Test
    @DisplayName("Unsupported adapter returns stable no-adapter diagnostics")
    void unsupportedAdapterReturnsStableNoAdapterDiagnostics() {
        CaptureMediaVideoFrameSourceAdapter adapter = CaptureMediaVideoFrameSourceAdapter.unsupported();

        CaptureMediaVideoFrameReadResult result = adapter.read(new CaptureMediaVideoFrameReadRequest(
                Path.of("phone-capture.mov"),
                2,
                CaptureMediaVideoLimits.conservativeDefaults()
        ));

        assertAll(
                () -> assertTrue(result.frames().isEmpty()),
                () -> assertEquals(1, result.diagnostics().size()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNSUPPORTED_CONTAINER,
                        result.diagnostics().get(0).code()),
                () -> assertEquals(CaptureMediaSourceKind.VIDEO_FILE,
                        result.diagnostics().get(0).sourceKind().orElseThrow()),
                () -> assertEquals(2, result.diagnostics().get(0).callerOrder().orElseThrow()),
                () -> assertTrue(result.diagnostics().get(0).message().contains("no adapter"))
        );
    }

    private CaptureMediaVideoFrame frame(int callerOrder, long timestampMillis, long frameNumber) {
        return new CaptureMediaVideoFrame(
                "capture.mp4#frame-%d".formatted(frameNumber),
                callerOrder,
                timestampMillis,
                frameNumber,
                1,
                1,
                "argb",
                new int[] { 0xFF000000 }
        );
    }
}
