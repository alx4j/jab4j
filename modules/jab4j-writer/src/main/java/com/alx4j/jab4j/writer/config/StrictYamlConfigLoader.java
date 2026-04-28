package com.alx4j.jab4j.writer.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strict YAML loader for runtime config files.
 */
public final class StrictYamlConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(StrictYamlConfigLoader.class);
    private static final ObjectMapper YAML_MAPPER = createMapper();

    /**
     * Loads a config patch from a filesystem path.
     *
     * @param path config file path
     * @return parsed config patch
     */
    public RuntimeConfigPatch load(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        Path normalizedPath = path.toAbsolutePath().normalize();
        LOGGER.debug("Loading runtime config patch from file={}", normalizedPath);
        try (InputStream inputStream = Files.newInputStream(normalizedPath)) {
            return parse(inputStream.readAllBytes(), "file=" + normalizedPath);
        } catch (IOException exception) {
            LOGGER.warn("Failed to read runtime config file {}: {}", normalizedPath, exception.getMessage());
            throw new ConfigLoadingException("Failed to load runtime config from " + normalizedPath, exception);
        }
    }

    /**
     * Loads a config patch from an input stream.
     *
     * @param inputStream YAML config input
     * @return parsed config patch
     */
    public RuntimeConfigPatch load(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try {
            return parse(inputStream.readAllBytes(), "provided stream");
        } catch (IOException exception) {
            LOGGER.warn("Failed to read runtime config stream: {}", exception.getMessage());
            throw new ConfigLoadingException("Failed to load runtime config from stream", exception);
        }
    }

    private RuntimeConfigPatch parse(byte[] bytes, String sourceDescription) {
        if (new String(bytes, StandardCharsets.UTF_8).isBlank()) {
            LOGGER.debug("Runtime config source={} was blank; using empty patch", sourceDescription);
            return RuntimeConfigPatch.empty();
        }
        try {
            RuntimeConfigPatch patch = YAML_MAPPER.readValue(new ByteArrayInputStream(bytes), RuntimeConfigPatch.class);
            LOGGER.debug("Loaded runtime config patch source={} byteCount={}", sourceDescription, bytes.length);
            return patch;
        } catch (IOException exception) {
            LOGGER.warn(
                    "Failed to parse runtime config YAML source={} byteCount={} message={}",
                    sourceDescription,
                    bytes.length,
                    exception.getMessage()
            );
            throw new ConfigLoadingException("Failed to parse runtime config YAML", exception);
        }
    }

    private static ObjectMapper createMapper() {
        return YAMLMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
    }
}
