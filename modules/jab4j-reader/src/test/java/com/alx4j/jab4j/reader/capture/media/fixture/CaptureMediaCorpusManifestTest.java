package com.alx4j.jab4j.reader.capture.media.fixture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Capture media corpus manifest")
class CaptureMediaCorpusManifestTest {

    private static final List<String> EXPECTED_HEADER = List.of(
            "scenario_id",
            "story_ref",
            "source_kind",
            "asset_availability",
            "format",
            "capture_condition",
            "expected_status",
            "expected_diagnostics",
            "restore_expectation",
            "asset_policy",
            "notes"
    );

    @Test
    @DisplayName("Manifest is well-formed and covers all first-slice fixture scenarios")
    void manifestIsWellFormedAndCoversAllFirstSliceFixtureScenarios() throws Exception {
        ManifestData manifest = readManifest();
        Set<String> scenarioIds = manifest.rowsByScenarioId().keySet();

        assertAll(
                () -> assertEquals(EXPECTED_HEADER, manifest.header()),
                () -> assertEquals(CaptureMediaCorpusFixtures.plannedScenarioIds(), scenarioIds),
                () -> assertEquals(scenarioIds.size(), manifest.rowsByScenarioId().size()),
                () -> assertTrue(manifest.rowsByScenarioId().values().stream()
                        .flatMap(row -> row.values().values().stream())
                        .noneMatch(String::isBlank))
        );
    }

    @Test
    @DisplayName("Manifest documents support boundary and expected diagnostics")
    void manifestDocumentsSupportBoundaryAndExpectedDiagnostics() throws Exception {
        Map<String, ManifestRow> rows = readManifest().rowsByScenarioId();

        assertAll(
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.GENERATED_EXACT_PNG, "asset_availability", "generated"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.GENERATED_EXACT_PNG, "restore_expectation", "yes"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.GENERATED_UNCROPPED_INSET, "source_kind", "generated_still_photo_sequence"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.DUPLICATE_FRAMES, "expected_diagnostics", "duplicate_media_frame"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.MISSING_UNIQUE_FRAMES, "expected_status", "incomplete"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.MISSING_UNIQUE_FRAMES, "expected_diagnostics", "missing_unique_frames"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.NO_JAB_FRAME, "expected_diagnostics", "screen_or_frame_not_found"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.UNSUPPORTED_HEIC_PLACEHOLDER, "format", "HEIC"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.UNSUPPORTED_HEIC_PLACEHOLDER, "expected_diagnostics", "unsupported_image_format"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.CORRUPTED_UNREADABLE_IMAGE, "expected_diagnostics", "unreadable_media"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.EXTERNAL_IPHONE_STILLS, "asset_availability", "external_private"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.EXTRACTED_VIDEO_FRAMES, "source_kind", "extracted_video_frame_folder"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.EXTRACTED_VIDEO_QUALITY_MIX, "expected_status", "eligible_with_warning"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.EXTRACTED_VIDEO_QUALITY_MIX, "expected_diagnostics",
                        "duplicate_media_frame;color_or_compression_shift;glare_or_overexposure;blur"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.FUTURE_DIRECT_VIDEO, "format", "MOV_OR_MP4"),
                () -> assertColumn(rows, CaptureMediaCorpusFixtures.FUTURE_DIRECT_VIDEO, "expected_diagnostics", "unsupported_container_or_codec")
        );
    }

    private static ManifestData readManifest() throws Exception {
        URL manifestResource = CaptureMediaCorpusManifestTest.class
                .getClassLoader()
                .getResource("capture-media-corpus/manifest.tsv");
        assertNotNull(manifestResource);
        Path manifest = Path.of(manifestResource.toURI());
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());

        List<String> header = List.of(lines.get(0).split("\t", -1));
        Map<String, ManifestRow> rows = new LinkedHashMap<>();
        Map<String, Integer> seenCounts = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            List<String> values = List.of(line.split("\t", -1));
            assertEquals(header.size(), values.size(), "Malformed manifest row: " + line);
            ManifestRow row = ManifestRow.from(header, values);
            rows.put(row.scenarioId(), row);
            seenCounts.merge(row.scenarioId(), 1, Integer::sum);
        }

        Set<String> duplicateIds = seenCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertTrue(duplicateIds.isEmpty(), "Duplicate manifest scenario ids: " + duplicateIds);
        return new ManifestData(header, rows);
    }

    private static void assertColumn(Map<String, ManifestRow> rows, String scenarioId, String column, String expectedValue) {
        assertTrue(rows.containsKey(scenarioId), "Missing manifest scenario: " + scenarioId);
        assertEquals(expectedValue, rows.get(scenarioId).values().get(column));
    }

    private record ManifestData(
            List<String> header,
            Map<String, ManifestRow> rowsByScenarioId
    ) {

        private ManifestData {
            header = List.copyOf(header);
            rowsByScenarioId = Map.copyOf(rowsByScenarioId);
        }
    }

    private record ManifestRow(Map<String, String> values) {

        private ManifestRow {
            values = Map.copyOf(values);
        }

        private String scenarioId() {
            return values.get("scenario_id");
        }

        private static ManifestRow from(List<String> header, List<String> values) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int index = 0; index < header.size(); index++) {
                row.put(header.get(index), values.get(index));
            }
            return new ManifestRow(row);
        }
    }
}
