package com.alx4j.jab4j.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.writer.config.RuntimeConfig;
import com.alx4j.jab4j.writer.config.RuntimeConfigPatch;
import com.alx4j.jab4j.writer.app.WriterRunRequest;

final class WriterCliParser {

    WriterRunRequest parse(String[] args) {
        Objects.requireNonNull(args, "args must not be null");

        List<RuntimeConfig.InputRootConfig> inputRoots = new ArrayList<>();
        String profile = null;
        Integer rows = null;
        Integer cols = null;
        Integer fps = null;
        Integer chunkBytes = null;
        Boolean fullscreen = null;
        Boolean exportEnabled = null;
        String exportMode = null;
        boolean dryRun = false;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--input" -> inputRoots.add(new RuntimeConfig.InputRootConfig(requireValue(args, ++index, argument), null));
                case "--profile" -> profile = requireValue(args, ++index, argument);
                case "--grid" -> {
                    String grid = requireValue(args, ++index, argument);
                    String[] parts = grid.toLowerCase(java.util.Locale.ROOT).split("x", -1);
                    if (parts.length != 2) {
                        throw new WriterCliException("Invalid value for --grid: " + grid);
                    }
                    rows = parsePositiveInt(parts[0], "--grid", grid);
                    cols = parsePositiveInt(parts[1], "--grid", grid);
                }
                case "--fps" -> fps = parsePositiveInt(requireValue(args, ++index, argument), argument, null);
                case "--chunk-bytes" -> chunkBytes = parsePositiveInt(requireValue(args, ++index, argument), argument, null);
                case "--fullscreen" -> fullscreen = Boolean.TRUE;
                case "--export-frames" -> {
                    exportEnabled = Boolean.TRUE;
                    exportMode = "imageSequence";
                }
                case "--dry-run" -> dryRun = true;
                default -> throw new WriterCliException("Unknown argument: " + argument);
            }
        }

        if (inputRoots.isEmpty()) {
            throw new WriterCliException("At least one --input path is required");
        }

        RuntimeConfigPatch cliOverrides = new RuntimeConfigPatch(
                profile == null ? null : new RuntimeConfigPatch.AppPatch(profile, null, null),
                new RuntimeConfigPatch.InputPatch(inputRoots),
                (rows == null && cols == null)
                        ? null
                        : new RuntimeConfigPatch.LayoutPatch(null, null, rows, cols, null, null, null, null, null, null, null, null, null),
                null,
                chunkBytes == null
                        ? null
                        : new RuntimeConfigPatch.TransportPatch(null, chunkBytes, null, null, null, null, null),
                (fps == null && fullscreen == null)
                        ? null
                        : new RuntimeConfigPatch.PlaybackPatch(fps, null, null, null, fullscreen),
                (exportEnabled == null && exportMode == null)
                        ? null
                        : new RuntimeConfigPatch.ExportPatch(exportEnabled, exportMode),
                null
        );
        return new WriterRunRequest(cliOverrides, dryRun);
    }

    private String requireValue(String[] args, int index, String argumentName) {
        if (index >= args.length) {
            throw new WriterCliException("Missing value for " + argumentName);
        }
        return args[index];
    }

    private int parsePositiveInt(String value, String argumentName, String originalToken) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            String token = originalToken == null ? value : originalToken;
            throw new WriterCliException("Invalid value for " + argumentName + ": " + token);
        }
    }
}
