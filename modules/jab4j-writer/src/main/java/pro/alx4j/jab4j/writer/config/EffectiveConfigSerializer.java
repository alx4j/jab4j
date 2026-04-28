package pro.alx4j.jab4j.writer.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serializes resolved runtime configuration into a deterministic artifact-friendly JSON snapshot.
 */
public final class EffectiveConfigSerializer {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Serializes the resolved config into deterministic JSON.
     *
     * @param config resolved config
     * @return canonical JSON snapshot
     */
    public String serialize(RuntimeConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        try {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toMap(config));
        } catch (JsonProcessingException exception) {
            throw new ConfigValidationException("Failed to serialize effective runtime config");
        }
    }

    private Map<String, Object> toMap(RuntimeConfig config) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", appMap(config.app()));
        root.put("input", inputMap(config.input()));
        root.put("layout", layoutMap(config.layout()));
        root.put("codec", codecMap(config.codec()));
        root.put("transport", transportMap(config.transport()));
        root.put("playback", playbackMap(config.playback()));
        root.put("export", exportMap(config.export()));
        root.put("diagnostics", diagnosticsMap(config.diagnostics()));
        return root;
    }

    private Map<String, Object> appMap(RuntimeConfig.AppConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("profile", config.profile());
        map.put("strictValidation", config.strictValidation());
        Map<String, Object> resourceLimits = new LinkedHashMap<>();
        resourceLimits.put("maxFileCount", config.resourceLimits().maxFileCount());
        resourceLimits.put("maxTotalBytes", config.resourceLimits().maxTotalBytes());
        resourceLimits.put("maxManifestBytes", config.resourceLimits().maxManifestBytes());
        resourceLimits.put("maxFrameCount", config.resourceLimits().maxFrameCount());
        resourceLimits.put("maxInMemoryBuffers", config.resourceLimits().maxInMemoryBuffers());
        map.put("resourceLimits", resourceLimits);
        return map;
    }

    private Map<String, Object> inputMap(RuntimeConfig.InputConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> roots = config.roots().stream()
                .map(root -> {
                    Map<String, Object> rootMap = new LinkedHashMap<>();
                    rootMap.put("path", root.path());
                    if (root.alias() != null) {
                        rootMap.put("alias", root.alias());
                    }
                    return rootMap;
                })
                .toList();
        map.put("roots", roots);
        return map;
    }

    private Map<String, Object> layoutMap(RuntimeConfig.LayoutConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", config.mode().wireValue());
        map.put("profileId", config.profileId());
        map.put("rows", config.rows());
        map.put("cols", config.cols());
        map.put("frameWidthPx", config.frameWidthPx());
        map.put("frameHeightPx", config.frameHeightPx());
        map.put("outerMarginPx", config.outerMarginPx());
        map.put("tileGapPx", config.tileGapPx());
        map.put("separatorStyle", config.separatorStyle());
        map.put("topSyncBandPx", config.topSyncBandPx());
        map.put("metadataBandPx", config.metadataBandPx());
        map.put("backgroundStyle", config.backgroundStyle());
        map.put("fitPolicy", config.fitPolicy());
        return map;
    }

    private Map<String, Object> codecMap(RuntimeConfig.CodecConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("profileId", config.profileId());
        map.put("payloadMode", config.payloadMode());
        map.put("conservativeDefaults", config.conservativeDefaults());
        return map;
    }

    private Map<String, Object> transportMap(RuntimeConfig.TransportConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("protocolVersion", config.protocolVersion());
        map.put("chunkBytes", config.chunkBytes());
        map.put("dataShardsPerGroup", config.dataShardsPerGroup());
        map.put("parityShardsPerGroup", config.parityShardsPerGroup());
        map.put("syncEveryFrames", config.syncEveryFrames());
        map.put("sessionHeaderRepeatEveryFrames", config.sessionHeaderRepeatEveryFrames());
        map.put("manifestRepeatEveryFrames", config.manifestRepeatEveryFrames());
        return map;
    }

    private Map<String, Object> playbackMap(RuntimeConfig.PlaybackConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fps", config.fps());
        map.put("holdFrames", config.holdFrames());
        map.put("warmupSyncFrames", config.warmupSyncFrames());
        map.put("endFrames", config.endFrames());
        map.put("fullscreen", config.fullscreen());
        return map;
    }

    private Map<String, Object> exportMap(RuntimeConfig.ExportConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", config.enabled());
        map.put("mode", config.mode());
        return map;
    }

    private Map<String, Object> diagnosticsMap(RuntimeConfig.DiagnosticsConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("showOverlay", config.showOverlay());
        map.put("writeFrameMetadataLog", config.writeFrameMetadataLog());
        map.put("writeSessionPlan", config.writeSessionPlan());
        return map;
    }
}
