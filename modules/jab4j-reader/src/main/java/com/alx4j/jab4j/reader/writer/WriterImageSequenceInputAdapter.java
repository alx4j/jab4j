package com.alx4j.jab4j.reader.writer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.reader.app.ReaderInputException;
import com.alx4j.jab4j.reader.frame.ReaderFrame;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
import com.alx4j.jab4j.reader.frame.ReaderWarning;
import com.alx4j.jab4j.reader.frame.ReaderWarningCode;

/**
 * Validates current writer imageSequence exports and normalizes them into reader frames.
 */
public final class WriterImageSequenceInputAdapter {

    private static final String IMAGE_SEQUENCE_DIRECTORY = "imageSequence";
    private static final String FRAME_SEQUENCE_FILE = "frame-sequence.txt";
    private static final String FRAME_NAME_PREFIX = "frame-";
    private static final String PNG_SUFFIX = ".png";
    private static final Pattern FRAME_INDEX_PATTERN = Pattern.compile("\\d{4,}");
    private static final Pattern SHA_256_HEX_PATTERN = Pattern.compile("[0-9a-f]{64}");

    /**
     * Validates one requested reader input path.
     *
     * @param inputPath exact imageSequence directory or parent session directory
     * @return validated imageSequence directory, accepted frame set, and warnings
     */
    public ValidatedInput validate(Path inputPath) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Path normalizedPath = inputPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedPath) || !Files.isReadable(normalizedPath)) {
            throw new ReaderInputException("Reader input must be a readable directory: " + normalizedPath);
        }

        Path imageSequenceDirectory = resolveImageSequenceDirectory(normalizedPath);
        SequenceMetadata metadata = parseMetadata(imageSequenceDirectory.resolve(FRAME_SEQUENCE_FILE));
        DiscoveredFrames discoveredFrames = discoverFrames(imageSequenceDirectory);
        List<ReaderWarning> warnings = new ArrayList<>(discoveredFrames.warnings());
        ReaderFrameSet frameSet = validateAndOrderFrames(metadata, discoveredFrames.framesByIdentity());
        return new ValidatedInput(imageSequenceDirectory, frameSet, warnings);
    }

    private Path resolveImageSequenceDirectory(Path inputPath) {
        if (IMAGE_SEQUENCE_DIRECTORY.equals(fileName(inputPath))) {
            return inputPath;
        }

        List<Path> imageSequenceChildren = childDirectories(inputPath).stream()
                .filter(path -> IMAGE_SEQUENCE_DIRECTORY.equals(fileName(path)))
                .toList();
        if (imageSequenceChildren.size() == 1) {
            return imageSequenceChildren.get(0).toAbsolutePath().normalize();
        }
        if (imageSequenceChildren.size() > 1) {
            throw new ReaderInputException(
                    "Reader input is ambiguous: provide the exact imageSequence directory or a parent with exactly one imageSequence child"
            );
        }
        if (Files.exists(inputPath.resolve(FRAME_SEQUENCE_FILE))) {
            throw new ReaderInputException(
                    "PNG frames are required for reader input; metadata-only frameSequence exports are not supported"
            );
        }
        boolean hasMetadataOnlyExport = childDirectories(inputPath).stream()
                .anyMatch(path -> "frameSequence".equals(fileName(path)));
        if (hasMetadataOnlyExport) {
            throw new ReaderInputException(
                    "PNG frames are required for reader input; provide a writer imageSequence export, not frameSequence metadata only"
            );
        }
        throw new ReaderInputException(
                "Reader input must be the imageSequence directory or a parent session directory with exactly one imageSequence child; broader roots are not scanned"
        );
    }

    private List<Path> childDirectories(Path inputPath) {
        try (Stream<Path> children = Files.list(inputPath)) {
            return children.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(this::fileName))
                    .toList();
        } catch (IOException exception) {
            throw new ReaderInputException("Failed to list reader input directory " + inputPath, exception);
        }
    }

    private SequenceMetadata parseMetadata(Path metadataPath) {
        if (!Files.isRegularFile(metadataPath) || !Files.isReadable(metadataPath)) {
            throw new ReaderInputException("MVP writer-exported PNG folders require frame-sequence.txt metadata");
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(metadataPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ReaderInputException("Failed to read frame-sequence.txt metadata", exception);
        }

        String sessionIdValue = null;
        String finalSessionDigest = null;
        Integer frameCount = null;
        List<MetadataFrame> frames = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("sessionId=")) {
                sessionIdValue = singleRequiredValue(sessionIdValue, line, "sessionId", lineIndex);
            } else if (line.startsWith("finalSessionDigest=")) {
                finalSessionDigest = singleRequiredValue(finalSessionDigest, line, "finalSessionDigest", lineIndex);
            } else if (line.startsWith("frameCount=")) {
                if (frameCount != null) {
                    throw new ReaderInputException(
                            "frame-sequence.txt contains duplicate frameCount at line " + (lineIndex + 1)
                    );
                }
                frameCount = parseFrameCount(singleRequiredValue(null, line, "frameCount", lineIndex), lineIndex);
            } else if (line.startsWith("frame=")) {
                frames.add(parseMetadataFrame(line.substring("frame=".length()), lineIndex));
            } else {
                throw new ReaderInputException("Invalid frame-sequence.txt line " + (lineIndex + 1) + ": " + line);
            }
        }

        if (sessionIdValue == null) {
            throw new ReaderInputException("frame-sequence.txt is missing required sessionId");
        }
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new ReaderInputException("frame-sequence.txt is missing required finalSessionDigest");
        }
        if (frameCount == null) {
            throw new ReaderInputException("frame-sequence.txt is missing required frameCount");
        }
        if (frameCount != frames.size()) {
            throw new ReaderInputException(
                    "frame-sequence.txt declares frameCount=" + frameCount + " but contains " + frames.size() + " frame rows"
            );
        }

        SessionId sessionId = parseSessionId(sessionIdValue);
        validateFrameOrder(frames);
        return new SequenceMetadata(sessionId, finalSessionDigest, frames);
    }

    private String singleRequiredValue(String existingValue, String line, String key, int lineIndex) {
        if (existingValue != null) {
            throw new ReaderInputException("frame-sequence.txt contains duplicate " + key + " at line " + (lineIndex + 1));
        }
        String value = line.substring((key + "=").length());
        if (value.isBlank()) {
            throw new ReaderInputException("frame-sequence.txt " + key + " must not be blank");
        }
        return value;
    }

    private int parseFrameCount(String value, int lineIndex) {
        try {
            int frameCount = Integer.parseInt(value);
            if (frameCount <= 0) {
                throw new ReaderInputException("frame-sequence.txt frameCount must be positive");
            }
            return frameCount;
        } catch (NumberFormatException exception) {
            throw new ReaderInputException(
                    "frame-sequence.txt frameCount is invalid at line " + (lineIndex + 1),
                    exception
            );
        }
    }

    private MetadataFrame parseMetadataFrame(String body, int lineIndex) {
        String[] parts = body.split("\t", -1);
        if (parts.length != 3) {
            throw new ReaderInputException("frame-sequence.txt frame row is invalid at line " + (lineIndex + 1));
        }
        long frameIndex = parseMetadataFrameIndex(parts[0], lineIndex);
        FrameType frameType = parseMetadataFrameType(parts[1], lineIndex);
        String pixelSha256 = parts[2];
        if (!SHA_256_HEX_PATTERN.matcher(pixelSha256).matches()) {
            throw new ReaderInputException(
                    "frame-sequence.txt frame hash must be lowercase SHA-256 hex at line " + (lineIndex + 1)
            );
        }
        return new MetadataFrame(new FrameIdentity(frameIndex, frameType), pixelSha256);
    }

    private long parseMetadataFrameIndex(String value, int lineIndex) {
        try {
            long frameIndex = Long.parseLong(value);
            if (frameIndex < 0) {
                throw new ReaderInputException("frame-sequence.txt frame index must be non-negative");
            }
            return frameIndex;
        } catch (NumberFormatException exception) {
            throw new ReaderInputException(
                    "frame-sequence.txt frame index is invalid at line " + (lineIndex + 1),
                    exception
            );
        }
    }

    private FrameType parseMetadataFrameType(String value, int lineIndex) {
        try {
            return FrameType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ReaderInputException(
                    "frame-sequence.txt frame type is invalid at line " + (lineIndex + 1) + ": " + value,
                    exception
            );
        }
    }

    private SessionId parseSessionId(String value) {
        try {
            return new SessionId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new ReaderInputException("frame-sequence.txt sessionId is not a valid UUID: " + value, exception);
        }
    }

    private void validateFrameOrder(List<MetadataFrame> frames) {
        Map<FrameIdentity, Integer> identities = new HashMap<>();
        long previousFrameIndex = -1;
        for (int index = 0; index < frames.size(); index++) {
            MetadataFrame frame = frames.get(index);
            if (frame.identity().frameIndex() <= previousFrameIndex) {
                throw new ReaderInputException("frame-sequence.txt frame rows must be ordered by increasing frame index");
            }
            Integer previous = identities.put(frame.identity(), index);
            if (previous != null) {
                throw new ReaderInputException(
                        "frame-sequence.txt contains duplicate frame identity " + frame.identity().displayName()
                );
            }
            previousFrameIndex = frame.identity().frameIndex();
        }
    }

    private DiscoveredFrames discoverFrames(Path imageSequenceDirectory) {
        List<Path> frameFiles = new ArrayList<>();
        try (Stream<Path> children = Files.list(imageSequenceDirectory)) {
            List<Path> regularFiles = children.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(this::fileName))
                    .toList();
            for (Path file : regularFiles) {
                String fileName = file.getFileName().toString();
                Optional<FrameIdentity> frameIdentity = parseFrameIdentity(fileName);
                if (fileName.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX)) {
                    if (frameIdentity.isEmpty()) {
                        throw new ReaderInputException(
                                "Unrecognized PNG frame file " + fileName + " is not present in frame-sequence.txt"
                        );
                    }
                }
                if (frameIdentity.isPresent()) {
                    frameFiles.add(file);
                }
            }
        } catch (IOException exception) {
            throw new ReaderInputException("Failed to list imageSequence directory " + imageSequenceDirectory, exception);
        }

        Map<FrameIdentity, LoadedFrame> framesByIdentity = new LinkedHashMap<>();
        List<ReaderWarning> warnings = new ArrayList<>();
        for (Path frameFile : frameFiles) {
            FrameIdentity identity = parseFrameIdentity(frameFile.getFileName().toString()).orElseThrow();
            LoadedFrame loadedFrame = loadFrame(frameFile, identity);
            LoadedFrame existingFrame = framesByIdentity.get(identity);
            if (existingFrame == null) {
                framesByIdentity.put(identity, loadedFrame);
            } else if (loadedFrame.hasSameBytesAs(existingFrame)) {
                warnings.add(new ReaderWarning(
                        ReaderWarningCode.DUPLICATE_FRAME_IGNORED,
                        "Ignored byte-identical duplicate frame "
                                + identity.displayName()
                                + " at "
                                + frameFile.getFileName()
                ));
            } else {
                throw new ReaderInputException(
                        "Duplicate frame identity " + identity.displayName() + " has conflicting PNG bytes"
                );
            }
        }
        return new DiscoveredFrames(framesByIdentity, warnings);
    }

    private String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private Optional<FrameIdentity> parseFrameIdentity(String fileName) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerFileName.startsWith(FRAME_NAME_PREFIX) || !lowerFileName.endsWith(PNG_SUFFIX)) {
            return Optional.empty();
        }

        String body = lowerFileName.substring(FRAME_NAME_PREFIX.length(), lowerFileName.length() - PNG_SUFFIX.length());
        int separatorIndex = body.indexOf('-');
        if (separatorIndex < 0) {
            return Optional.empty();
        }
        String indexValue = body.substring(0, separatorIndex);
        if (!FRAME_INDEX_PATTERN.matcher(indexValue).matches()) {
            return Optional.empty();
        }

        long frameIndex;
        try {
            frameIndex = Long.parseLong(indexValue);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        String typeAndSuffix = body.substring(separatorIndex + 1);
        for (FrameType frameType : FrameType.values()) {
            String wireValue = frameType.name().toLowerCase(Locale.ROOT);
            if (typeAndSuffix.equals(wireValue) || hasDuplicateSuffix(typeAndSuffix, wireValue)) {
                return Optional.of(new FrameIdentity(frameIndex, frameType));
            }
        }
        return Optional.empty();
    }

    private boolean hasDuplicateSuffix(String typeAndSuffix, String wireValue) {
        return typeAndSuffix.startsWith(wireValue + "-")
                || typeAndSuffix.startsWith(wireValue + " ")
                || typeAndSuffix.startsWith(wireValue + ".");
    }

    private LoadedFrame loadFrame(Path path, FrameIdentity identity) {
        byte[] pngBytes;
        try {
            pngBytes = Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new ReaderInputException("Failed to read PNG frame " + path, exception);
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        } catch (IOException exception) {
            throw new ReaderInputException("Failed to decode PNG frame " + path, exception);
        }
        if (image == null) {
            throw new ReaderInputException("Frame file is not a readable PNG image: " + path);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        String pixelSha256 = sha256Hex(toArgbBytes(pixels));
        return new LoadedFrame(
                identity,
                path,
                pngBytes,
                width,
                height,
                pixels,
                pixelSha256
        );
    }

    private ReaderFrameSet validateAndOrderFrames(
            SequenceMetadata metadata,
            Map<FrameIdentity, LoadedFrame> framesByIdentity
    ) {
        List<ReaderFrame> orderedFrames = new ArrayList<>(metadata.frames().size());
        for (MetadataFrame metadataFrame : metadata.frames()) {
            LoadedFrame loadedFrame = framesByIdentity.get(metadataFrame.identity());
            if (loadedFrame == null) {
                throw new ReaderInputException(
                        "frame-sequence.txt frame "
                                + metadataFrame.identity().displayName()
                                + " has no matching PNG frame"
                );
            }
            if (!metadataFrame.pixelSha256().equals(loadedFrame.pixelSha256())) {
                throw new ReaderInputException(
                        "PNG frame "
                                + metadataFrame.identity().displayName()
                                + " pixel hash "
                                + loadedFrame.pixelSha256()
                                + " does not match frame-sequence.txt hash "
                                + metadataFrame.pixelSha256()
                );
            }
            orderedFrames.add(loadedFrame.toReaderFrame());
        }

        for (FrameIdentity identity : framesByIdentity.keySet()) {
            boolean presentInMetadata = metadata.frames().stream()
                    .anyMatch(metadataFrame -> metadataFrame.identity().equals(identity));
            if (!presentInMetadata) {
                throw new ReaderInputException(
                        "Discovered PNG frame " + identity.displayName() + " is not present in frame-sequence.txt"
                );
            }
        }
        return new ReaderFrameSet(metadata.sessionId(), metadata.finalSessionDigest(), orderedFrames);
    }

    private byte[] toArgbBytes(int[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        return buffer.array();
    }

    private String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Validated writer imageSequence input ready for reader decode.
     *
     * @param imageSequenceDirectory normalized imageSequence directory
     * @param frameSet accepted frame set
     * @param warnings structured non-fatal warnings
     */
    public record ValidatedInput(Path imageSequenceDirectory, ReaderFrameSet frameSet, List<ReaderWarning> warnings) {

        /**
         * Creates a validated writer input snapshot.
         *
         * @param imageSequenceDirectory normalized imageSequence directory
         * @param frameSet accepted frame set
         * @param warnings structured non-fatal warnings
         */
        public ValidatedInput {
            Objects.requireNonNull(imageSequenceDirectory, "imageSequenceDirectory must not be null");
            Objects.requireNonNull(frameSet, "frameSet must not be null");
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        }
    }

    private record SequenceMetadata(SessionId sessionId, String finalSessionDigest, List<MetadataFrame> frames) {

        private SequenceMetadata {
            frames = List.copyOf(frames);
        }
    }

    private record MetadataFrame(FrameIdentity identity, String pixelSha256) {
    }

    private record DiscoveredFrames(Map<FrameIdentity, LoadedFrame> framesByIdentity, List<ReaderWarning> warnings) {

        private DiscoveredFrames {
            framesByIdentity = Collections.unmodifiableMap(new LinkedHashMap<>(framesByIdentity));
            warnings = List.copyOf(warnings);
        }
    }

    private record LoadedFrame(
            FrameIdentity identity,
            Path path,
            byte[] pngBytes,
            int widthPixels,
            int heightPixels,
            int[] argbPixels,
            String pixelSha256
    ) {

        private boolean hasSameBytesAs(LoadedFrame other) {
            return MessageDigest.isEqual(pngBytes, other.pngBytes);
        }

        private ReaderFrame toReaderFrame() {
            List<Integer> pixels = new ArrayList<>(argbPixels.length);
            for (int pixel : argbPixels) {
                pixels.add(pixel);
            }
            return new ReaderFrame(
                    identity.frameIndex(),
                    identity.frameType(),
                    widthPixels,
                    heightPixels,
                    pixels,
                    pixelSha256
            );
        }
    }

    private record FrameIdentity(long frameIndex, FrameType frameType) {

        private String displayName() {
            return String.format(Locale.ROOT, "frame-%04d-%s", frameIndex, frameType.name());
        }
    }
}
