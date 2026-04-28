package com.alx4j.jab4j.conformance.fixtures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.internal.DeferredTileDecoder;
import com.alx4j.jab4j.tile.internal.DeterministicTileEncoder;
import com.alx4j.jab4j.tile.internal.SupportedTileCodecProfiles;

@DisplayName("Codec fixture corpus conformance")
class CodecFixtureCorpusTest {

    private static final TileCodecProfile BALANCED_V1_PROFILE = SupportedTileCodecProfiles.balancedV1();

    private final DeterministicTileEncoder encoder = new DeterministicTileEncoder();
    private final DeferredTileDecoder decoder = new DeferredTileDecoder();

    @Test
    @DisplayName("Positive fixtures round-trip and reproduce committed logical tiles")
    void positiveFixturesRoundTripAndReproduceCommittedLogicalTiles() throws IOException {
        FixtureCorpus corpus = loadCorpus();

        for (PositiveFixture fixture : corpus.positiveFixtures()) {
            assertPositiveFixtureMetadata(fixture);

            LogicalTile expectedTile = fixture.logicalTile();
            LogicalTile encoded = encoder.encode(fixture.payloadBytes(), BALANCED_V1_PROFILE);

            assertAll(
                    fixture.fixtureId(),
                    () -> assertEquals(expectedTile, encoded),
                    () -> assertArrayEquals(fixture.payloadBytes(), decoder.decode(expectedTile, BALANCED_V1_PROFILE))
            );
        }
    }

    @Test
    @DisplayName("Negative fixtures fail with the committed error messages")
    void negativeFixturesFailClearly() throws IOException {
        FixtureCorpus corpus = loadCorpus();

        for (NegativeFixture fixture : corpus.negativeFixtures()) {
            assertNegativeFixtureMetadata(fixture);

            TileCodecException exception = assertExpectedNegativeFailure(corpus, fixture);

            assertEquals(fixture.expectedError(), exception.getMessage(), fixture.fixtureId());
        }
    }

    private FixtureCorpus loadCorpus() throws IOException {
        Map<String, PositiveFixture> positives = new LinkedHashMap<>();
        List<NegativeFixture> negatives = new ArrayList<>();
        try (InputStream inputStream = resource("codec-fixtures/catalog.txt")) {
            String catalog = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : catalog.lines().map(String::trim).filter(lineValue -> !lineValue.isEmpty()).toList()) {
                Properties properties = loadProperties(line);
                String fixtureCategory = properties.getProperty("fixtureCategory");
                if ("codec-roundtrip".equals(fixtureCategory)) {
                    PositiveFixture positiveFixture = PositiveFixture.from(properties);
                    positives.put(positiveFixture.fixtureId(), positiveFixture);
                } else if ("negative".equals(fixtureCategory)) {
                    negatives.add(NegativeFixture.from(properties));
                } else {
                    throw new IllegalArgumentException("Unknown fixtureCategory: " + fixtureCategory);
                }
            }
        }
        return new FixtureCorpus(positives, negatives);
    }

    private Properties loadProperties(String resourcePath) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = resource(resourcePath)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private void assertPositiveFixtureMetadata(PositiveFixture fixture) {
        assertAll(
                fixture.fixtureId(),
                () -> assertEquals("codec-roundtrip", fixture.fixtureCategory()),
                () -> assertEquals("balanced-v1", fixture.codecProfileId()),
                () -> assertEquals("safe-v1", fixture.transportProfileId()),
                () -> assertFalse(fixture.generationToolVersion().isBlank()),
                () -> assertDoesNotThrow(() -> Instant.parse(fixture.generationDateUtc()))
        );
    }

    private void assertNegativeFixtureMetadata(NegativeFixture fixture) {
        assertAll(
                fixture.fixtureId(),
                () -> assertEquals("negative", fixture.fixtureCategory()),
                () -> assertEquals("balanced-v1", fixture.codecProfileId()),
                () -> assertEquals("safe-v1", fixture.transportProfileId()),
                () -> assertDoesNotThrow(() -> Instant.parse(fixture.generationDateUtc()))
        );
    }

    private TileCodecException assertExpectedNegativeFailure(FixtureCorpus corpus, NegativeFixture fixture) {
        return switch (fixture.negativeKind()) {
            case "unsupported-profile" -> assertThrows(
                    TileCodecException.class,
                    () -> decoder.decode(corpus.positiveFixture(fixture.baseFixtureId()).logicalTile(), fixture.requestedProfile()),
                    fixture.fixtureId()
            );
            case "mutated-tile" -> assertThrows(
                    TileCodecException.class,
                    () -> decoder.decode(
                            fixture.mutate(corpus.positiveFixture(fixture.baseFixtureId()).logicalTile(), BALANCED_V1_PROFILE),
                            BALANCED_V1_PROFILE
                    ),
                    fixture.fixtureId()
            );
            default -> throw new IllegalArgumentException("Unsupported negative fixture kind: " + fixture.negativeKind());
        };
    }

    private InputStream resource(String path) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IllegalArgumentException("Missing fixture resource: " + path);
        }
        return inputStream;
    }

    private record FixtureCorpus(Map<String, PositiveFixture> positiveFixturesById, List<NegativeFixture> negativeFixtures) {
        private FixtureCorpus {
            positiveFixturesById = Map.copyOf(positiveFixturesById);
            negativeFixtures = List.copyOf(negativeFixtures);
        }

        List<PositiveFixture> positiveFixtures() {
            return positiveFixturesById.values().stream().toList();
        }

        PositiveFixture positiveFixture(String fixtureId) {
            PositiveFixture fixture = positiveFixturesById.get(fixtureId);
            if (fixture == null) {
                throw new IllegalArgumentException("Unknown positive fixture id: " + fixtureId);
            }
            return fixture;
        }
    }

    private record PositiveFixture(
            String fixtureId,
            String fixtureCategory,
            String snapshotId,
            String generationDateUtc,
            String generationToolVersion,
            String codecProfileId,
            String transportProfileId,
            byte[] payloadBytes,
            LogicalTile logicalTile
    ) {

        static PositiveFixture from(Properties properties) {
            return new PositiveFixture(
                    require(properties, "fixtureId"),
                    require(properties, "fixtureCategory"),
                    require(properties, "snapshotId"),
                    require(properties, "generationDateUtc"),
                    require(properties, "generationToolVersion"),
                    require(properties, "codecProfileId"),
                    require(properties, "transportProfileId"),
                    HexFormat.of().parseHex(properties.getProperty("payloadHex", "")),
                    new LogicalTile(
                            Integer.parseInt(require(properties, "widthModules")),
                            Integer.parseInt(require(properties, "heightModules")),
                            Integer.parseInt(require(properties, "quietZoneModules")),
                            require(properties, "profileId"),
                            parseModuleColors(require(properties, "moduleColors")),
                            parseDiagnostics(properties)
                    )
            );
        }
    }

    private record NegativeFixture(
            String fixtureId,
            String fixtureCategory,
            String snapshotId,
            String generationDateUtc,
            String generationToolVersion,
            String codecProfileId,
            String transportProfileId,
            String baseFixtureId,
            String negativeKind,
            String expectedError,
            String mutationMode,
            int mutationIndex,
            int mutationValue,
            TileCodecProfile requestedProfile
    ) {

        static NegativeFixture from(Properties properties) {
            String kind = require(properties, "negativeKind");
            return new NegativeFixture(
                    require(properties, "fixtureId"),
                    require(properties, "fixtureCategory"),
                    require(properties, "snapshotId"),
                    require(properties, "generationDateUtc"),
                    require(properties, "generationToolVersion"),
                    require(properties, "codecProfileId"),
                    require(properties, "transportProfileId"),
                    require(properties, "baseFixtureId"),
                    kind,
                    require(properties, "expectedError"),
                    properties.getProperty("mutationMode", ""),
                    Integer.parseInt(properties.getProperty("mutationIndex", "-1")),
                    Integer.parseInt(properties.getProperty("mutationValue", "0")),
                    "unsupported-profile".equals(kind)
                            ? new TileCodecProfile(
                                    require(properties, "requestedProfileId"),
                                    require(properties, "requestedPayloadMode"),
                                    Integer.parseInt(require(properties, "requestedColorCount")),
                                    Integer.parseInt(require(properties, "requestedQuietZoneModules")),
                                    Integer.parseInt(require(properties, "requestedMinSideVersion")),
                                    Integer.parseInt(require(properties, "requestedMaxSideVersion")),
                                    Integer.parseInt(require(properties, "requestedParityBytes")),
                                    Integer.parseInt(require(properties, "requestedMaskPatternCount")),
                                    Integer.parseInt(require(properties, "requestedDefaultMaskReference"))
                            )
                            : null
            );
        }

        LogicalTile mutate(LogicalTile baseFixture, TileCodecProfile profile) {
            List<Integer> colors = new ArrayList<>(baseFixture.moduleColors());
            if ("set".equals(mutationMode)) {
                colors.set(mutationIndex, mutationValue);
            } else if ("increment-mod-color-count".equals(mutationMode)) {
                colors.set(mutationIndex, (colors.get(mutationIndex) + mutationValue) % profile.colorCount());
            } else {
                throw new IllegalArgumentException("Unsupported mutationMode: " + mutationMode);
            }
            return new LogicalTile(
                    baseFixture.widthModules(),
                    baseFixture.heightModules(),
                    baseFixture.quietZoneModules(),
                    baseFixture.profileId(),
                    colors,
                    baseFixture.diagnostics()
            );
        }
    }

    private static List<Integer> parseModuleColors(String encoded) {
        List<Integer> colors = new ArrayList<>(encoded.length());
        for (int index = 0; index < encoded.length(); index++) {
            colors.add(Integer.parseInt(String.valueOf(encoded.charAt(index))));
        }
        return colors;
    }

    private static Map<String, String> parseDiagnostics(Properties properties) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith("diag.")) {
                diagnostics.put(propertyName.substring("diag.".length()), properties.getProperty(propertyName));
            }
        }
        return diagnostics;
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property " + key);
        }
        return value;
    }
}
