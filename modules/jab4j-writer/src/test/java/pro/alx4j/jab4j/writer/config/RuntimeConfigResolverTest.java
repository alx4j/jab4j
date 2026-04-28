package pro.alx4j.jab4j.writer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Runtime config resolution")
class RuntimeConfigResolverTest {

    private final StrictYamlConfigLoader yamlLoader = new StrictYamlConfigLoader();
    private final RuntimeConfigResolver resolver = new RuntimeConfigResolver();
    private final EffectiveConfigSerializer serializer = new EffectiveConfigSerializer();

    @Test
    @DisplayName("The default safe profile resolves successfully")
    void defaultSafeProfileResolvesSuccessfully() {
        RuntimeConfig config = resolver.resolve();

        assertAll(
                () -> assertEquals("desktop-1080p-safe", config.app().profile()),
                () -> assertTrue(config.app().strictValidation()),
                () -> assertEquals(2, config.layout().rows()),
                () -> assertEquals(2, config.layout().cols()),
                () -> assertEquals(RuntimeConfig.LayoutMode.FIXED, config.layout().mode()),
                () -> assertEquals("desktop-1080p-safe", config.layout().profileId()),
                () -> assertEquals("balanced-v1", config.codec().profileId()),
                () -> assertEquals(512, config.transport().chunkBytes()),
                () -> assertEquals(8, config.playback().fps()),
                () -> assertEquals(100_000, config.app().resourceLimits().maxFileCount())
        );
    }

    @Test
    @DisplayName("Selected profiles take precedence before file and CLI overrides")
    void mergePrecedenceUsesSelectedProfileBeforeFileAndCliOverrides() {
        RuntimeConfigPatch fileConfig = yaml("""
                app:
                  profile: debug-low-density
                layout:
                  cols: 4
                playback:
                  fps: 5
                transport:
                  chunkBytes: 1792
                """);
        RuntimeConfigPatch cliOverrides = yaml("""
                app:
                  profile: desktop-1440p-balanced
                transport:
                  chunkBytes: 3072
                """);

        RuntimeConfig config = resolver.resolve(fileConfig, cliOverrides);

        assertAll(
                () -> assertEquals("desktop-1440p-balanced", config.app().profile()),
                () -> assertEquals("desktop-1440p-balanced", config.layout().profileId()),
                () -> assertEquals(2, config.layout().rows()),
                () -> assertEquals(4, config.layout().cols()),
                () -> assertEquals(2560, config.layout().frameWidthPx()),
                () -> assertEquals(5, config.playback().fps()),
                () -> assertEquals(3072, config.transport().chunkBytes())
        );
    }

    @Test
    @DisplayName("Strict YAML loading rejects unknown fields")
    void strictYamlLoaderRejectsUnknownFields() {
        assertThrows(ConfigLoadingException.class, () -> yaml("""
                layout:
                  unexpectedField: 1
                """));
    }

    @Test
    @DisplayName("Invalid resolved configs fail fast")
    void invalidResolvedConfigFailsFast() {
        assertValidationFailure("""
                layout:
                  profileId: unsupported-layout
                """, "Unsupported layout.profileId: unsupported-layout");
    }

    @Test
    @DisplayName("Recognized auto-fit mode still fails until support exists")
    void recognizedAutoFitModeFailsFastUntilSupported() {
        assertValidationFailure("""
                layout:
                  mode: autoFit
                """, "layout.mode autoFit is recognized but not yet supported");
    }

    @Test
    @DisplayName("Layout geometry validation fails fast")
    void layoutGeometryValidationFailsFast() {
        assertValidationFailure("""
                layout:
                  tileGapPx: 0
                """, "layout.tileGapPx must be at least 1 to provide explicit separators");
    }

    @Test
    @DisplayName("Transport and playback validation fail fast")
    void transportAndPlaybackValidationFailFast() {
        assertAll(
                () -> assertValidationFailure("""
                        transport:
                          chunkBytes: 0
                        """, "transport.chunkBytes must be positive"),
                () -> assertValidationFailure("""
                        playback:
                          fps: 0
                        """, "playback.fps must be positive")
        );
    }

    @Test
    @DisplayName("Effective config output remains deterministic")
    void effectiveConfigOutputIsDeterministic() {
        RuntimeConfigPatch fileConfig = yaml("""
                input:
                  roots:
                    - path: /data/project-a
                      alias: root-001
                    - path: /data/project-b
                      alias: root-002
                diagnostics:
                  showOverlay: true
                """);

        RuntimeConfig config = resolver.resolve(fileConfig, RuntimeConfigPatch.empty());
        String first = serializer.serialize(config);
        String second = serializer.serialize(resolver.resolve(fileConfig, RuntimeConfigPatch.empty()));

        assertEquals(first, second);
        assertEquals("""
                {
                  "app" : {
                    "profile" : "desktop-1080p-safe",
                    "strictValidation" : true,
                    "resourceLimits" : {
                      "maxFileCount" : 100000,
                      "maxTotalBytes" : 10737418240,
                      "maxManifestBytes" : 4194304,
                      "maxFrameCount" : 100000,
                      "maxInMemoryBuffers" : 256
                    }
                  },
                  "input" : {
                    "roots" : [ {
                      "path" : "/data/project-a",
                      "alias" : "root-001"
                    }, {
                      "path" : "/data/project-b",
                      "alias" : "root-002"
                    } ]
                  },
                  "layout" : {
                    "mode" : "fixed",
                    "profileId" : "desktop-1080p-safe",
                    "rows" : 2,
                    "cols" : 2,
                    "frameWidthPx" : 1920,
                    "frameHeightPx" : 1080,
                    "outerMarginPx" : 48,
                    "tileGapPx" : 24,
                    "separatorStyle" : "solidWhite",
                    "topSyncBandPx" : 64,
                    "metadataBandPx" : 32,
                    "backgroundStyle" : "black",
                    "fitPolicy" : "preserveAspect"
                  },
                  "codec" : {
                    "profileId" : "balanced-v1",
                    "payloadMode" : "binary",
                    "conservativeDefaults" : true
                  },
                  "transport" : {
                    "protocolVersion" : 1,
                    "chunkBytes" : 512,
                    "dataShardsPerGroup" : 4,
                    "parityShardsPerGroup" : 2,
                    "syncEveryFrames" : 10,
                    "sessionHeaderRepeatEveryFrames" : 30,
                    "manifestRepeatEveryFrames" : 60
                  },
                  "playback" : {
                    "fps" : 8,
                    "holdFrames" : 1,
                    "warmupSyncFrames" : 6,
                    "endFrames" : 6,
                    "fullscreen" : true
                  },
                  "export" : {
                    "enabled" : false,
                    "mode" : "none"
                  },
                  "diagnostics" : {
                    "showOverlay" : true,
                    "writeFrameMetadataLog" : true,
                    "writeSessionPlan" : true
                  }
                }""", first);
    }

    private RuntimeConfigPatch yaml(String value) {
        return yamlLoader.load(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertValidationFailure(String yamlValue, String expectedMessage) {
        ConfigValidationException exception = assertThrows(
                ConfigValidationException.class,
                () -> resolver.resolve(yaml(yamlValue), RuntimeConfigPatch.empty())
        );

        assertEquals(expectedMessage, exception.getMessage());
    }
}
