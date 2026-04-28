package com.alx4j.jab4j.writer.config;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the final runtime config from defaults, selected profile, file config, and CLI overrides.
 */
public final class RuntimeConfigResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeConfigResolver.class);

    private final BuiltInProfiles builtInProfiles;
    private final StrictYamlConfigLoader yamlLoader;
    private final RuntimeConfigValidator validator;

    /**
     * Creates a resolver with built-in profiles, strict YAML loading, and fail-fast validation.
     */
    public RuntimeConfigResolver() {
        this(new BuiltInProfiles(), new StrictYamlConfigLoader(), new RuntimeConfigValidator());
    }

    RuntimeConfigResolver(
            BuiltInProfiles builtInProfiles,
            StrictYamlConfigLoader yamlLoader,
            RuntimeConfigValidator validator
    ) {
        this.builtInProfiles = Objects.requireNonNull(builtInProfiles, "builtInProfiles must not be null");
        this.yamlLoader = Objects.requireNonNull(yamlLoader, "yamlLoader must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    /**
     * Resolves the default safe config with no file config or CLI overrides.
     *
     * @return resolved default config
     */
    public RuntimeConfig resolve() {
        return resolve(RuntimeConfigPatch.empty(), RuntimeConfigPatch.empty());
    }

    /**
     * Resolves config from a YAML file and optional CLI overrides.
     *
     * @param configFile YAML config path
     * @param cliOverrides typed CLI overrides
     * @return resolved runtime config
     */
    public RuntimeConfig resolve(Path configFile, RuntimeConfigPatch cliOverrides) {
        Path normalizedConfigFile = Objects.requireNonNull(configFile, "configFile must not be null")
                .toAbsolutePath()
                .normalize();
        LOGGER.debug(
                "Resolving runtime config from file={} cliOverridesPresent={}",
                normalizedConfigFile,
                hasOverrides(cliOverrides)
        );
        return resolve(yamlLoader.load(normalizedConfigFile), cliOverrides);
    }

    /**
     * Resolves config from a YAML input stream and optional CLI overrides.
     *
     * @param configStream YAML config input
     * @param cliOverrides typed CLI overrides
     * @return resolved runtime config
     */
    public RuntimeConfig resolve(InputStream configStream, RuntimeConfigPatch cliOverrides) {
        LOGGER.debug("Resolving runtime config from provided stream cliOverridesPresent={}", hasOverrides(cliOverrides));
        return resolve(yamlLoader.load(configStream), cliOverrides);
    }

    /**
     * Resolves config from file and CLI patches.
     *
     * @param fileConfig config file patch
     * @param cliOverrides CLI override patch
     * @return resolved runtime config
     */
    public RuntimeConfig resolve(RuntimeConfigPatch fileConfig, RuntimeConfigPatch cliOverrides) {
        RuntimeConfigPatch filePatch = fileConfig == null ? RuntimeConfigPatch.empty() : fileConfig;
        RuntimeConfigPatch cliPatch = cliOverrides == null ? RuntimeConfigPatch.empty() : cliOverrides;

        String selectedProfileId = selectedProfileId(filePatch, cliPatch);
        String selectedProfileSource = selectedProfileSource(filePatch, cliPatch);
        try {
            LOGGER.debug(
                    "Resolving runtime config selectedProfile={} selectedProfileSource={} fileOverridesPresent={} cliOverridesPresent={}",
                    selectedProfileId,
                    selectedProfileSource,
                    hasOverrides(filePatch),
                    hasOverrides(cliPatch)
            );
            RuntimeConfigPatch merged = builtInProfiles.defaults()
                    .merge(builtInProfiles.profile(selectedProfileId))
                    .merge(filePatch)
                    .merge(cliPatch);

            RuntimeConfig config = merged.materialize();
            validator.validate(config);
            LOGGER.debug(
                    "Resolved runtime config: profile={}, layoutProfile={}, grid={}x{}, fps={}, chunkBytes={}, inputRoots={}, exportEnabled={}",
                    selectedProfileId,
                    config.layout().profileId(),
                    config.layout().rows(),
                    config.layout().cols(),
                    config.playback().fps(),
                    config.transport().chunkBytes(),
                    config.input().roots().size(),
                    config.export().enabled()
            );
            return config;
        } catch (ConfigValidationException exception) {
            LOGGER.warn(
                    "Runtime config resolution failed selectedProfile={} selectedProfileSource={} fileOverridesPresent={} cliOverridesPresent={} message={}",
                    selectedProfileId,
                    selectedProfileSource,
                    hasOverrides(filePatch),
                    hasOverrides(cliPatch),
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Runtime config resolution failed unexpectedly selectedProfile={} selectedProfileSource={} fileOverridesPresent={} cliOverridesPresent={}",
                    selectedProfileId,
                    selectedProfileSource,
                    hasOverrides(filePatch),
                    hasOverrides(cliPatch),
                    exception
            );
            throw exception;
        }
    }

    private boolean hasOverrides(RuntimeConfigPatch patch) {
        return patch != null && !RuntimeConfigPatch.empty().equals(patch);
    }

    private String selectedProfileId(RuntimeConfigPatch filePatch, RuntimeConfigPatch cliPatch) {
        String cliProfile = cliPatch.app() == null ? null : cliPatch.app().profile();
        if (cliProfile != null && !cliProfile.isBlank()) {
            return cliProfile;
        }

        String fileProfile = filePatch.app() == null ? null : filePatch.app().profile();
        if (fileProfile != null && !fileProfile.isBlank()) {
            return fileProfile;
        }

        return builtInProfiles.defaultProfileId();
    }

    private String selectedProfileSource(RuntimeConfigPatch filePatch, RuntimeConfigPatch cliPatch) {
        String cliProfile = cliPatch.app() == null ? null : cliPatch.app().profile();
        if (cliProfile != null && !cliProfile.isBlank()) {
            return "cli";
        }

        String fileProfile = filePatch.app() == null ? null : filePatch.app().profile();
        if (fileProfile != null && !fileProfile.isBlank()) {
            return "file";
        }

        return "default";
    }
}
