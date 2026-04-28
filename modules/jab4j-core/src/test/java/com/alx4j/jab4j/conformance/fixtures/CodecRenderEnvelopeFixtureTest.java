package com.alx4j.jab4j.conformance.fixtures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.support.HashingUtils;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.internal.DeterministicTileEncoder;
import com.alx4j.jab4j.tile.internal.SupportedTileCodecProfiles;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

@DisplayName("Codec render and envelope fixtures")
class CodecRenderEnvelopeFixtureTest {

    private static final Map<String, String> EXPECTED_RASTER_HASHES = Map.of(
            "codec-roundtrip-a150", "3eb2788ffc0f98d26aa398612fc5fe01125c030b79efd7590943b44c59dffe5a",
            "codec-roundtrip-empty", "8ffdc02cd45a7807c7008555306392cda5d7bd366a4b4ee6f08dd18323389d91",
            "codec-roundtrip-hello", "516eb00cfd06fb41912c6a7c1072447dfd73622b91a3dbbeade5b13f24f26ea1"
    );
    private static final Map<String, String> EXPECTED_ENVELOPE_HASHES = Map.of(
            "codec-roundtrip-a150", "272b66d830b25ec643f068884a43ad908a04a991701c51054a2b9faf24d2961a",
            "codec-roundtrip-empty", "5a69a753d748c10dc047df47eb99ae738341fa74ff76d54a4d5976a3bbbc671b",
            "codec-roundtrip-hello", "9f515bca265b6a008c60e996b3e78e0846b97402c8d135c7c69bae19ff8cc178"
    );
    private static final TileCodecProfile BALANCED_V1_PROFILE = SupportedTileCodecProfiles.balancedV1();
    private static final SessionId FIXTURE_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

    private final DeterministicTileEncoder encoder = new DeterministicTileEncoder();
    private final TileRasterRenderer tileRasterRenderer = new TileRasterRenderer();
    private final TilePayloadEnvelopeCodec envelopeCodec = new TilePayloadEnvelopeCodec();
    private final FixedLayoutPlan layoutPlan = new FixedLayoutPlanner().plan(new LayoutProfile(
            "desktop-1080p-safe",
            2,
            2,
            1920,
            1080,
            24,
            48,
            "solidWhite",
            64,
            32,
            "black",
            "preserveAspect"
    ));

    @Test
    @DisplayName("Positive fixtures freeze deterministic raster and envelope hashes")
    void positiveFixturesFreezeDeterministicRasterAndEnvelopeHashes() throws IOException {
        FixtureCorpus corpus = loadCorpus();

        for (PositiveFixture fixture : corpus.positiveFixtures()) {
            assertFixtureMetadata(fixture);

            LogicalTile logicalTile = encoder.encode(fixture.payloadBytes(), BALANCED_V1_PROFILE);
            RenderedTile firstRenderedTile = tileRasterRenderer.render(logicalTile, layoutPlan);
            RenderedTile secondRenderedTile = tileRasterRenderer.render(logicalTile, layoutPlan);

            TilePayload payload = canonicalPayload(fixture.payloadBytes(), fixture.fixtureId());
            byte[] firstEnvelope = envelopeCodec.serialize(payload);
            byte[] secondEnvelope = envelopeCodec.serialize(payload);

            assertAll(
                    fixture.fixtureId(),
                    () -> assertEquals(firstRenderedTile, secondRenderedTile),
                    () -> assertEquals(
                            EXPECTED_RASTER_HASHES.get(fixture.fixtureId()),
                            firstRenderedTile.diagnostics().get("pixelSha256"),
                            fixture.fixtureId()
                    ),
                    () -> assertArrayEquals(firstEnvelope, secondEnvelope),
                    () -> assertEquals(
                            EXPECTED_ENVELOPE_HASHES.get(fixture.fixtureId()),
                            HashingUtils.sha256Hex(firstEnvelope),
                            fixture.fixtureId()
                    ),
                    () -> assertEquals(payload, envelopeCodec.parse(firstEnvelope, payload.protocolCompatibilityVersion()))
            );
        }
    }

    private TilePayload canonicalPayload(byte[] payloadBytes, String fixtureId) {
        byte[] body = payloadBytes.clone();
        return new TilePayload(
                1,
                FIXTURE_SESSION_ID,
                FrameType.DATA,
                42L,
                new TileIndex(0),
                4,
                layoutPlan.profile().profileId(),
                PayloadKind.FILE_CHUNK,
                100L + fixtureId.length(),
                body.length,
                com.alx4j.jab4j.support.ChecksumUtils.crc32c(body),
                0,
                body
        );
    }

    private FixtureCorpus loadCorpus() throws IOException {
        Map<String, PositiveFixture> positives = new LinkedHashMap<>();
        try (InputStream inputStream = resource("codec-fixtures/catalog.txt")) {
            String catalog = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : catalog.lines().map(String::trim).filter(lineValue -> !lineValue.isEmpty()).toList()) {
                Properties properties = loadProperties(line);
                if (!"codec-roundtrip".equals(properties.getProperty("fixtureCategory"))) {
                    continue;
                }
                PositiveFixture positiveFixture = PositiveFixture.from(properties);
                positives.put(positiveFixture.fixtureId(), positiveFixture);
            }
        }
        return new FixtureCorpus(positives);
    }

    private Properties loadProperties(String resourcePath) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = resource(resourcePath)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private void assertFixtureMetadata(PositiveFixture fixture) {
        assertAll(
                fixture.fixtureId(),
                () -> assertEquals("balanced-v1", fixture.codecProfileId()),
                () -> assertEquals("safe-v1", fixture.transportProfileId()),
                () -> assertFalse(fixture.generationToolVersion().isBlank()),
                () -> assertDoesNotThrow(() -> Instant.parse(fixture.generationDateUtc()))
        );
    }

    private InputStream resource(String path) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IllegalArgumentException("Missing fixture resource: " + path);
        }
        return inputStream;
    }

    private record FixtureCorpus(Map<String, PositiveFixture> positiveFixturesById) {
        private FixtureCorpus {
            positiveFixturesById = Map.copyOf(positiveFixturesById);
        }

        List<PositiveFixture> positiveFixtures() {
            return positiveFixturesById.values().stream()
                    .sorted(java.util.Comparator.comparing(PositiveFixture::fixtureId))
                    .toList();
        }
    }

    private record PositiveFixture(
            String fixtureId,
            String snapshotId,
            String generationDateUtc,
            String generationToolVersion,
            String codecProfileId,
            String transportProfileId,
            byte[] payloadBytes
    ) {

        static PositiveFixture from(Properties properties) {
            Instant.parse(require(properties, "generationDateUtc"));
            return new PositiveFixture(
                    require(properties, "fixtureId"),
                    require(properties, "snapshotId"),
                    require(properties, "generationDateUtc"),
                    require(properties, "generationToolVersion"),
                    require(properties, "codecProfileId"),
                    require(properties, "transportProfileId"),
                    HexFormat.of().parseHex(properties.getProperty("payloadHex", ""))
            );
        }
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property " + key);
        }
        return value;
    }
}
