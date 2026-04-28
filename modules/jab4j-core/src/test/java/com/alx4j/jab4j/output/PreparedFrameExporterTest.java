package com.alx4j.jab4j.output;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.render.frame.RenderedFrame;

@DisplayName("Prepared frame exporting")
class PreparedFrameExporterTest {

    private static final SessionId FIXED_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final String FIXED_DIGEST = "digest-123";

    @TempDir
    Path tempDir;

    private final PreparedFrameExporter exporter = new PreparedFrameExporter();

    @Test
    @DisplayName("Frame-sequence mode writes a deterministic ordered sequence file")
    void frameSequenceModeWritesDeterministicOrderedSequenceFile() throws Exception {
        ExportArtifacts exportArtifacts = exporter.export(
                ExportMode.FRAME_SEQUENCE,
                FIXED_SESSION_ID,
                FIXED_DIGEST,
                sampleFrames(),
                tempDir.resolve("exports")
        );

        Path sequenceFile = exportArtifacts.exportedFiles().get("frameSequence");
        assertAll(
                () -> assertEquals(List.of("frameSequence"), exportedKeys(exportArtifacts)),
                () -> assertTrue(Files.exists(sequenceFile)),
                () -> assertEquals("""
                        sessionId=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
                        finalSessionDigest=digest-123
                        frameCount=2
                        frame=0\tSYNC\tfixture-sync
                        frame=1\tDATA\tfixture-data
                        """, Files.readString(sequenceFile))
        );
    }

    @Test
    @DisplayName("Image-sequence mode writes stable names and bytes")
    void imageSequenceModeWritesStableNamesAndBytes() throws Exception {
        ExportArtifacts first = exporter.export(
                ExportMode.IMAGE_SEQUENCE,
                FIXED_SESSION_ID,
                FIXED_DIGEST,
                sampleFrames(),
                tempDir.resolve("first")
        );
        ExportArtifacts second = exporter.export(
                ExportMode.IMAGE_SEQUENCE,
                FIXED_SESSION_ID,
                FIXED_DIGEST,
                sampleFrames(),
                tempDir.resolve("second")
        );

        assertAll(
                () -> assertEquals(exportedKeys(first), exportedKeys(second)),
                () -> assertEquals(List.of("frameSequence", "image-0000", "image-0001"), exportedKeys(first)),
                () -> assertEquals("frame-0000-sync.png", first.exportedFiles().get("image-0000").getFileName().toString()),
                () -> assertEquals("frame-0001-data.png", first.exportedFiles().get("image-0001").getFileName().toString()),
                () -> assertEquals(
                        Files.readString(first.exportedFiles().get("frameSequence")),
                        Files.readString(second.exportedFiles().get("frameSequence"))
                ),
                () -> assertTrue(Files.size(first.exportedFiles().get("image-0000")) > 0),
                () -> assertEquals(
                        Files.readAllBytes(first.exportedFiles().get("image-0000")).length,
                        Files.readAllBytes(second.exportedFiles().get("image-0000")).length
                ),
                () -> assertEquals(
                        Files.readAllBytes(first.exportedFiles().get("image-0001")).length,
                        Files.readAllBytes(second.exportedFiles().get("image-0001")).length
                )
        );
    }

    private List<String> exportedKeys(ExportArtifacts exportArtifacts) {
        return exportArtifacts.exportedFiles().keySet().stream().toList();
    }

    private List<RenderedFrame> sampleFrames() {
        return List.of(
                frame(0L, FrameType.SYNC, 0xFF112233, "fixture-sync"),
                frame(1L, FrameType.DATA, 0xFF445566, "fixture-data")
        );
    }

    private RenderedFrame frame(long frameIndex, FrameType frameType, int color, String hash) {
        return new RenderedFrame(
                frameIndex,
                frameType,
                2,
                2,
                List.of(color, color, color, color),
                Map.of("pixelSha256", hash)
        );
    }
}
