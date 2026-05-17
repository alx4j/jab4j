package com.alx4j.jab4j.reader.capture.media.sample;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32C;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;

/**
 * Builds bounded reader-owned diagnostics for logical tiles that reached tile decode.
 */
final class CaptureMediaTileDecodeDiagnostics {

    private static final int FINDER_SIZE_MODULES = 3;
    private static final int TILE_HEADER_BYTES = 7;
    private static final int INTERLEAVE_SEED = 226_759;
    private static final int LDPC_MESSAGE_SEED = 785_465;
    private static final int FORMAT_VERSION = 1;
    private static final int COMPACT_DUMP_ROW_LIMIT = 8;
    private static final int DEINTERLEAVED_PREFIX_BYTES = 16;

    private CaptureMediaTileDecodeDiagnostics() {
    }

    /**
     * Returns debug-only tile-decode metadata without changing the authoritative decode decision.
     *
     * @param tile sampled logical tile that was passed to tile decode
     * @param profile requested tile codec profile
     * @param decodeFailureReason tile-decode failure reason reported by the authoritative decoder, when available
     * @return stable sidecar fields for the tile-decode attempt
     */
    static Map<String, String> inspect(
            LogicalTile tile,
            TileCodecProfile profile,
            Optional<String> decodeFailureReason
    ) {
        Objects.requireNonNull(tile, "tile must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(decodeFailureReason, "decodeFailureReason must not be null");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("profileId", profile.profileId());
        fields.put("logicalTileProfileId", tile.profileId());
        fields.put("logicalTileWidthModules", Integer.toString(tile.widthModules()));
        fields.put("logicalTileHeightModules", Integer.toString(tile.heightModules()));
        fields.put("logicalTileQuietZoneModules", Integer.toString(tile.quietZoneModules()));
        fields.put("logicalTileSha256", logicalTileHash(tile));
        fields.put("logicalTileCompactDump", compactDump(tile));
        fields.put("headerBytes", Integer.toString(TILE_HEADER_BYTES));
        fields.put("parityBytes", Integer.toString(profile.parityBytes()));
        fields.put("maskPatternCount", Integer.toString(profile.maskPatternCount()));

        try {
            bestAttempt(tile, profile, decodeFailureReason).ifPresent(attempt -> attempt.addTo(fields));
        } catch (RuntimeException exception) {
            fields.put("debugUnavailable", exception.getClass().getSimpleName());
        }
        return Map.copyOf(fields);
    }

    private static Optional<AttemptDiagnostic> bestAttempt(
            LogicalTile tile,
            TileCodecProfile profile,
            Optional<String> decodeFailureReason
    ) {
        String normalizedReason = decodeFailureReason.map(CaptureMediaTileDecodeDiagnostics::trimTileDecodePrefix)
                .orElse("");
        AttemptDiagnostic best = null;
        AttemptDiagnostic matchingFailure = null;
        for (int maskPattern : candidateMaskPatterns(tile, profile)) {
            List<Integer> symbols = extractDemaskedSymbols(tile, profile, maskPattern);
            byte[] bytes = symbolsToBytes(symbols, profile.bitsPerModule());
            for (int encodedBytes = TILE_HEADER_BYTES + profile.parityBytes();
                    encodedBytes <= bytes.length;
                    encodedBytes++) {
                AttemptDiagnostic attempt = inspectAttempt(bytes, encodedBytes, profile, maskPattern);
                if (best == null || attempt.specificity() > best.specificity()) {
                    best = attempt;
                }
                if (!normalizedReason.isBlank()
                        && normalizedReason.equals(attempt.reason())
                        && (matchingFailure == null || attempt.specificity() > matchingFailure.specificity())) {
                    matchingFailure = attempt;
                }
            }
        }
        return Optional.ofNullable(matchingFailure == null ? best : matchingFailure);
    }

    private static String trimTileDecodePrefix(String reason) {
        String prefix = "tileDecode: ";
        return reason.startsWith(prefix) ? reason.substring(prefix.length()) : reason;
    }

    private static AttemptDiagnostic inspectAttempt(
            byte[] bytes,
            int encodedBytes,
            TileCodecProfile profile,
            int maskPattern
    ) {
        byte[] deinterleaved = deinterleave(Arrays.copyOf(bytes, encodedBytes));
        String parsedHeaderHex = hex(Arrays.copyOf(deinterleaved, Math.min(TILE_HEADER_BYTES, deinterleaved.length)));
        String deinterleavedPrefixHex = hex(Arrays.copyOf(
                deinterleaved,
                Math.min(DEINTERLEAVED_PREFIX_BYTES, deinterleaved.length)
        ));
        if (deinterleaved.length < TILE_HEADER_BYTES + profile.parityBytes()) {
            return AttemptDiagnostic.withHeader(
                    maskPattern,
                    encodedBytes,
                    bytes.length,
                    "SHORT_HEADER",
                    "Encoded frame is shorter than the supported header",
                    1,
                    parsedHeaderHex,
                    deinterleavedPrefixHex
            );
        }

        int formatVersion = deinterleaved[0] & 0xFF;
        if (formatVersion != FORMAT_VERSION) {
            return AttemptDiagnostic.withFormat(
                    maskPattern,
                    encodedBytes,
                    bytes.length,
                    "FORMAT_VERSION",
                    "Logical tile uses unsupported format version " + formatVersion,
                    0,
                    parsedHeaderHex,
                    deinterleavedPrefixHex,
                    formatVersion
            );
        }

        int payloadLength = ((deinterleaved[1] & 0xFF) << 8) | (deinterleaved[2] & 0xFF);
        int framedLength = TILE_HEADER_BYTES + payloadLength;
        int expectedEncodedBytes = framedLength + profile.parityBytes();
        if (expectedEncodedBytes != encodedBytes) {
            return AttemptDiagnostic.withLength(
                    maskPattern,
                    encodedBytes,
                    bytes.length,
                    "HEADER_LENGTH",
                    "Logical tile header payload length does not match encoded length",
                    1,
                    parsedHeaderHex,
                    deinterleavedPrefixHex,
                    formatVersion,
                    payloadLength,
                    framedLength,
                    expectedEncodedBytes
            );
        }

        byte[] payload = Arrays.copyOfRange(deinterleaved, 3, 3 + payloadLength);
        int storedPayloadCrc32c = ByteBuffer.wrap(deinterleaved, 3 + payloadLength, Integer.BYTES).getInt();
        int calculatedPayloadCrc32c = crc32c(payload);
        if (storedPayloadCrc32c != calculatedPayloadCrc32c) {
            return AttemptDiagnostic.withCrc(
                    maskPattern,
                    encodedBytes,
                    bytes.length,
                    "PAYLOAD_CRC32C",
                    "Logical tile payload CRC32C mismatch",
                    3,
                    parsedHeaderHex,
                    deinterleavedPrefixHex,
                    formatVersion,
                    payloadLength,
                    framedLength,
                    expectedEncodedBytes,
                    storedPayloadCrc32c,
                    calculatedPayloadCrc32c
            );
        }
        if (!hasExpectedParity(deinterleaved, framedLength, profile.parityBytes())) {
            return AttemptDiagnostic.withCrc(
                    maskPattern,
                    encodedBytes,
                    bytes.length,
                    "PARITY",
                    "Logical tile parity bytes do not match the supported deterministic parity stage",
                    2,
                    parsedHeaderHex,
                    deinterleavedPrefixHex,
                    formatVersion,
                    payloadLength,
                    framedLength,
                    expectedEncodedBytes,
                    storedPayloadCrc32c,
                    calculatedPayloadCrc32c
            );
        }
        return AttemptDiagnostic.withCrc(
                maskPattern,
                encodedBytes,
                bytes.length,
                "ACCEPTED",
                "accepted",
                4,
                parsedHeaderHex,
                deinterleavedPrefixHex,
                formatVersion,
                payloadLength,
                framedLength,
                expectedEncodedBytes,
                storedPayloadCrc32c,
                calculatedPayloadCrc32c
        );
    }

    private static List<Integer> candidateMaskPatterns(LogicalTile tile, TileCodecProfile profile) {
        LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
        Integer diagnosticMaskPattern = parseOptionalInteger(tile.diagnostics().get("maskPattern"));
        if (diagnosticMaskPattern != null
                && diagnosticMaskPattern >= 0
                && diagnosticMaskPattern < profile.maskPatternCount()) {
            candidates.add(diagnosticMaskPattern);
        }
        for (int maskPattern = 0; maskPattern < profile.maskPatternCount(); maskPattern++) {
            candidates.add(maskPattern);
        }
        return List.copyOf(candidates);
    }

    private static Integer parseOptionalInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<Integer> extractDemaskedSymbols(LogicalTile tile, TileCodecProfile profile, int maskPattern) {
        int dimension = tile.widthModules();
        List<Integer> symbols = new ArrayList<>(dimension * dimension);
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                if (isReserved(row, col, dimension)) {
                    continue;
                }
                int value = tile.moduleColorAt(row, col);
                if (value >= profile.colorCount()) {
                    throw new TileCodecException("Logical tile contains module colors outside the supported palette");
                }
                symbols.add(reverseMask(value, row, col, maskPattern, profile.colorCount()));
            }
        }
        return symbols;
    }

    private static boolean isReserved(int row, int col, int dimension) {
        return insideFinder(row, col, 0, 0)
                || insideFinder(row, col, 0, dimension - FINDER_SIZE_MODULES)
                || insideFinder(row, col, dimension - FINDER_SIZE_MODULES, 0)
                || insideFinder(row, col, dimension - FINDER_SIZE_MODULES, dimension - FINDER_SIZE_MODULES);
    }

    private static boolean insideFinder(int row, int col, int startRow, int startCol) {
        return row >= startRow
                && row < startRow + FINDER_SIZE_MODULES
                && col >= startCol
                && col < startCol + FINDER_SIZE_MODULES;
    }

    private static int reverseMask(int value, int row, int col, int maskPattern, int colorCount) {
        boolean active = switch (maskPattern) {
            case 0 -> ((row + col) & 1) == 0;
            case 1 -> (row & 1) == 0;
            case 2 -> col % 3 == 0;
            case 3 -> (row + col) % 3 == 0;
            case 4 -> (((row / 2) + (col / 3)) & 1) == 0;
            case 5 -> ((row * col) % 2) + ((row * col) % 3) == 0;
            case 6 -> ((((row * col) % 2) + ((row * col) % 3)) & 1) == 0;
            case 7 -> ((((row + col) % 2) + ((row * col) % 3)) & 1) == 0;
            default -> throw new TileCodecException("Unsupported mask pattern " + maskPattern);
        };
        return active ? Math.floorMod(value - maskPattern - 1, colorCount) : value;
    }

    private static byte[] symbolsToBytes(List<Integer> symbols, int bitsPerModule) {
        int totalBytes = (symbols.size() * bitsPerModule) / Byte.SIZE;
        byte[] bytes = new byte[totalBytes];
        int current = 0;
        int currentBits = 0;
        int byteIndex = 0;
        for (int symbol : symbols) {
            if (symbol < 0 || symbol >= (1 << bitsPerModule)) {
                throw new TileCodecException("Logical tile contains symbol values outside the supported subset");
            }
            for (int bit = bitsPerModule - 1; bit >= 0; bit--) {
                current = (current << 1) | ((symbol >>> bit) & 1);
                currentBits++;
                if (currentBits == Byte.SIZE && byteIndex < bytes.length) {
                    bytes[byteIndex++] = (byte) current;
                    current = 0;
                    currentBits = 0;
                }
            }
        }
        return bytes;
    }

    private static byte[] deinterleave(byte[] input) {
        byte[] values = Arrays.copyOf(input, input.length);
        int[] index = new int[values.length];
        for (int position = 0; position < index.length; position++) {
            index[position] = position;
        }

        DeterministicPseudoRandom random = new DeterministicPseudoRandom(INTERLEAVE_SEED);
        int length = values.length;
        for (int iteration = 0; iteration < length; iteration++) {
            int remaining = length - iteration;
            long unsigned = Integer.toUnsignedLong(random.nextInt());
            int position = (int) Math.floor((unsigned / (double) 0xFFFFFFFFL) * remaining);
            if (position >= remaining) {
                position = remaining - 1;
            }
            int from = length - 1 - iteration;
            int tmp = index[from];
            index[from] = index[position];
            index[position] = tmp;
        }

        byte[] output = new byte[length];
        for (int position = 0; position < length; position++) {
            output[index[position]] = values[position];
        }
        return output;
    }

    private static boolean hasExpectedParity(byte[] encoded, int framedLength, int parityBytes) {
        if (framedLength < 0 || parityBytes < 0 || framedLength + parityBytes > encoded.length) {
            return false;
        }
        byte[] framed = Arrays.copyOf(encoded, framedLength);
        byte[] expected = appendParity(framed, parityBytes);
        return Arrays.equals(expected, Arrays.copyOf(encoded, framedLength + parityBytes));
    }

    private static byte[] appendParity(byte[] input, int parityBytes) {
        if (parityBytes == 0) {
            return Arrays.copyOf(input, input.length);
        }
        byte[] parity = new byte[parityBytes];
        DeterministicPseudoRandom random = new DeterministicPseudoRandom(LDPC_MESSAGE_SEED);
        for (int index = 0; index < input.length; index++) {
            int slot = Integer.remainderUnsigned(random.nextInt(), parityBytes);
            int neighbor = (slot + index + 1) % parityBytes;
            parity[slot] = (byte) (parity[slot] ^ input[index] ^ random.nextInt());
            parity[neighbor] = (byte) (parity[neighbor] + input[index] + index);
        }
        for (int index = 0; index < parity.length; index++) {
            parity[index] = (byte) (parity[index] ^ random.nextInt());
        }

        byte[] output = Arrays.copyOf(input, input.length + parityBytes);
        System.arraycopy(parity, 0, output, input.length, parity.length);
        return output;
    }

    private static String logicalTileHash(LogicalTile tile) {
        StringBuilder builder = new StringBuilder();
        builder.append(tile.profileId())
                .append('|')
                .append(tile.widthModules())
                .append('x')
                .append(tile.heightModules())
                .append('|')
                .append(tile.quietZoneModules());
        for (Integer moduleColor : tile.moduleColors()) {
            builder.append('|').append(moduleColor);
        }
        return sha256Hex(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String compactDump(LogicalTile tile) {
        int rows = Math.min(tile.heightModules(), COMPACT_DUMP_ROW_LIMIT);
        StringBuilder builder = new StringBuilder();
        builder.append(tile.widthModules()).append('x').append(tile.heightModules()).append(':');
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                builder.append('/');
            }
            for (int col = 0; col < tile.widthModules(); col++) {
                appendModuleColor(builder, tile.moduleColorAt(row, col));
            }
        }
        if (tile.heightModules() > rows) {
            builder.append("/...");
        }
        return builder.toString();
    }

    private static void appendModuleColor(StringBuilder builder, int value) {
        if (value >= 0 && value < 16) {
            builder.append(Character.toUpperCase(Character.forDigit(value, 16)));
        } else {
            builder.append('[').append(value).append(']');
        }
    }

    private static int crc32c(byte[] payload) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(payload, 0, payload.length);
        return (int) crc32c.getValue();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static String hexInt(int value) {
        return "0x%08X".formatted(value);
    }

    private record AttemptDiagnostic(
            int maskPattern,
            int encodedBytes,
            int logicalCapacityBytes,
            String failureStage,
            String reason,
            int specificity,
            Optional<Integer> formatVersion,
            Optional<Integer> payloadLength,
            Optional<Integer> framedLength,
            Optional<Integer> expectedEncodedBytes,
            Optional<String> parsedHeaderHex,
            Optional<String> deinterleavedPrefixHex,
            Optional<String> storedPayloadCrc32c,
            Optional<String> calculatedPayloadCrc32c
    ) {

        private AttemptDiagnostic {
            Objects.requireNonNull(failureStage, "failureStage must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(formatVersion, "formatVersion must not be null");
            Objects.requireNonNull(payloadLength, "payloadLength must not be null");
            Objects.requireNonNull(framedLength, "framedLength must not be null");
            Objects.requireNonNull(expectedEncodedBytes, "expectedEncodedBytes must not be null");
            Objects.requireNonNull(parsedHeaderHex, "parsedHeaderHex must not be null");
            Objects.requireNonNull(deinterleavedPrefixHex, "deinterleavedPrefixHex must not be null");
            Objects.requireNonNull(storedPayloadCrc32c, "storedPayloadCrc32c must not be null");
            Objects.requireNonNull(calculatedPayloadCrc32c, "calculatedPayloadCrc32c must not be null");
        }

        private static AttemptDiagnostic withHeader(
                int maskPattern,
                int encodedBytes,
                int logicalCapacityBytes,
                String failureStage,
                String reason,
                int specificity,
                String parsedHeaderHex,
                String deinterleavedPrefixHex
        ) {
            return new AttemptDiagnostic(
                    maskPattern,
                    encodedBytes,
                    logicalCapacityBytes,
                    failureStage,
                    reason,
                    specificity,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(parsedHeaderHex),
                    Optional.of(deinterleavedPrefixHex),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private static AttemptDiagnostic withFormat(
                int maskPattern,
                int encodedBytes,
                int logicalCapacityBytes,
                String failureStage,
                String reason,
                int specificity,
                String parsedHeaderHex,
                String deinterleavedPrefixHex,
                int formatVersion
        ) {
            return new AttemptDiagnostic(
                    maskPattern,
                    encodedBytes,
                    logicalCapacityBytes,
                    failureStage,
                    reason,
                    specificity,
                    Optional.of(formatVersion),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(parsedHeaderHex),
                    Optional.of(deinterleavedPrefixHex),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private static AttemptDiagnostic withLength(
                int maskPattern,
                int encodedBytes,
                int logicalCapacityBytes,
                String failureStage,
                String reason,
                int specificity,
                String parsedHeaderHex,
                String deinterleavedPrefixHex,
                int formatVersion,
                int payloadLength,
                int framedLength,
                int expectedEncodedBytes
        ) {
            return new AttemptDiagnostic(
                    maskPattern,
                    encodedBytes,
                    logicalCapacityBytes,
                    failureStage,
                    reason,
                    specificity,
                    Optional.of(formatVersion),
                    Optional.of(payloadLength),
                    Optional.of(framedLength),
                    Optional.of(expectedEncodedBytes),
                    Optional.of(parsedHeaderHex),
                    Optional.of(deinterleavedPrefixHex),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private static AttemptDiagnostic withCrc(
                int maskPattern,
                int encodedBytes,
                int logicalCapacityBytes,
                String failureStage,
                String reason,
                int specificity,
                String parsedHeaderHex,
                String deinterleavedPrefixHex,
                int formatVersion,
                int payloadLength,
                int framedLength,
                int expectedEncodedBytes,
                int storedPayloadCrc32c,
                int calculatedPayloadCrc32c
        ) {
            return new AttemptDiagnostic(
                    maskPattern,
                    encodedBytes,
                    logicalCapacityBytes,
                    failureStage,
                    reason,
                    specificity,
                    Optional.of(formatVersion),
                    Optional.of(payloadLength),
                    Optional.of(framedLength),
                    Optional.of(expectedEncodedBytes),
                    Optional.of(parsedHeaderHex),
                    Optional.of(deinterleavedPrefixHex),
                    Optional.of(hexInt(storedPayloadCrc32c)),
                    Optional.of(hexInt(calculatedPayloadCrc32c))
            );
        }

        private void addTo(Map<String, String> fields) {
            fields.put("failureStage", failureStage);
            fields.put("failureReason", reason);
            fields.put("specificity", Integer.toString(specificity));
            fields.put("maskPattern", Integer.toString(maskPattern));
            fields.put("encodedBytes", Integer.toString(encodedBytes));
            fields.put("logicalCapacityBytes", Integer.toString(logicalCapacityBytes));
            fields.put("deinterleavedBytes", Integer.toString(encodedBytes));
            formatVersion.ifPresent(value -> fields.put("formatVersion", Integer.toString(value)));
            payloadLength.ifPresent(value -> fields.put("payloadLength", Integer.toString(value)));
            framedLength.ifPresent(value -> fields.put("framedLength", Integer.toString(value)));
            expectedEncodedBytes.ifPresent(value -> {
                fields.put("expectedEncodedBytes", Integer.toString(value));
                fields.put("encodedLengthDelta", Integer.toString(value - encodedBytes));
            });
            parsedHeaderHex.ifPresent(value -> fields.put("parsedHeaderHex", value));
            deinterleavedPrefixHex.ifPresent(value -> fields.put("deinterleavedPrefixHex", value));
            storedPayloadCrc32c.ifPresent(value -> fields.put("storedPayloadCrc32c", value));
            calculatedPayloadCrc32c.ifPresent(value -> fields.put("calculatedPayloadCrc32c", value));
        }
    }

    private static final class DeterministicPseudoRandom {

        private long seed;

        private DeterministicPseudoRandom(long seed) {
            this.seed = seed;
        }

        private int nextInt() {
            seed = (6364136223846793005L * seed) + 1L;
            int value = (int) (seed >>> 32);
            value ^= value >>> 11;
            value ^= (value << 7) & 0x9D2C5680;
            value ^= (value << 15) & 0xEFC60000;
            value ^= value >>> 18;
            return value;
        }
    }
}
