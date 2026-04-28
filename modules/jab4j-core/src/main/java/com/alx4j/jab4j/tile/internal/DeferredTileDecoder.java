package com.alx4j.jab4j.tile.internal;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.support.ChecksumUtils;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileDecoder;

/**
 * Bounded logical-tile decoder used for milestone-one fixture validation only.
 */
public final class DeferredTileDecoder implements TileDecoder {

    private static final int FINDER_SIZE = 3;
    private static final int FORMAT_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(DeferredTileDecoder.class);

    private final DeterministicParity parity = new DeterministicParity();
    private final DeterministicInterleaver interleaver = new DeterministicInterleaver();

    @Override
    public byte[] decode(LogicalTile tile, TileCodecProfile profile) {
        try {
            TileCodecProfile supportedProfile = requireSupportedProfile(tile, profile);
            int sideVersion = requireSupportedGeometry(tile, supportedProfile);
            requireFinderPatterns(tile, supportedProfile.dimensionForSideVersion(sideVersion));

            Integer diagnosticMaskPattern = parseOptionalInteger(tile.diagnostics(), "maskPattern");
            Integer diagnosticEncodedBytes = parseOptionalInteger(tile.diagnostics(), "encodedBytes");
            List<Integer> symbols = extractDemaskedSymbols(tile, supportedProfile, firstMask(diagnosticMaskPattern));

            if (diagnosticMaskPattern != null && diagnosticEncodedBytes != null) {
                byte[] payload = decodeWithExactMaskAndLength(symbols, supportedProfile, diagnosticMaskPattern, diagnosticEncodedBytes);
                LOGGER.debug(
                        "Decoded logical tile profileId={} dimension={} sideVersion={} payloadBytes={} decodePath=diagnostics",
                        supportedProfile.profileId(),
                        tile.widthModules(),
                        sideVersion,
                        payload.length
                );
                return payload;
            }

            byte[] payload = decodeWithinSupportedSubset(tile, supportedProfile, diagnosticMaskPattern);
            LOGGER.debug(
                    "Decoded logical tile profileId={} dimension={} sideVersion={} payloadBytes={} decodePath=subsetSearch diagnosticMaskPresent={} diagnosticEncodedBytesPresent={}",
                    supportedProfile.profileId(),
                    tile.widthModules(),
                    sideVersion,
                    payload.length,
                    diagnosticMaskPattern != null,
                    diagnosticEncodedBytes != null
            );
            return payload;
        } catch (RuntimeException exception) {
            if (exception instanceof TileCodecException) {
                LOGGER.warn(
                        "Logical tile decoding failed requestedProfileId={} tileProfileId={} tileDimension={} message={}",
                        safeProfileId(profile),
                        safeTileProfileId(tile),
                        safeTileDimension(tile),
                        exception.getMessage()
                );
            } else {
                LOGGER.error(
                        "Logical tile decoding failed requestedProfileId={} tileProfileId={} tileDimension={}",
                        safeProfileId(profile),
                        safeTileProfileId(tile),
                        safeTileDimension(tile),
                        exception
                );
            }
            throw exception;
        }
    }

    private String safeProfileId(TileCodecProfile profile) {
        return profile == null ? null : profile.profileId();
    }

    private String safeTileProfileId(LogicalTile tile) {
        return tile == null ? null : tile.profileId();
    }

    private Integer safeTileDimension(LogicalTile tile) {
        return tile == null ? null : tile.widthModules();
    }

    private TileCodecProfile requireSupportedProfile(LogicalTile tile, TileCodecProfile profile) {
        if (tile == null) {
            throw new TileCodecException("tile must not be null");
        }
        if (profile == null) {
            throw new TileCodecException("profile must not be null");
        }
        TileCodecProfile supportedProfile = SupportedTileCodecProfiles.resolve(profile.profileId());
        if (!supportedProfile.equals(profile)) {
            throw new TileCodecException("Unsupported profile parameters for profile " + profile.profileId());
        }
        if (!supportedProfile.profileId().equals(tile.profileId())) {
            throw new TileCodecException("Logical tile profileId does not match requested profile " + supportedProfile.profileId());
        }
        if (tile.quietZoneModules() != supportedProfile.quietZoneModules()) {
            throw new TileCodecException("Logical tile quiet-zone width does not match supported profile " + supportedProfile.profileId());
        }
        return supportedProfile;
    }

    private int requireSupportedGeometry(LogicalTile tile, TileCodecProfile profile) {
        if (tile.widthModules() != tile.heightModules()) {
            throw new TileCodecException("Logical tile must be square for the supported subset");
        }
        for (int sideVersion = profile.minSideVersion(); sideVersion <= profile.maxSideVersion(); sideVersion++) {
            if (profile.dimensionForSideVersion(sideVersion) == tile.widthModules()) {
                return sideVersion;
            }
        }
        throw new TileCodecException("Unsupported logical tile dimension " + tile.widthModules() + " for profile " + profile.profileId());
    }

    private void requireFinderPatterns(LogicalTile tile, int dimension) {
        requireFinderColor(tile, 0, 0, 0);
        requireFinderColor(tile, 0, dimension - FINDER_SIZE, 0);
        requireFinderColor(tile, dimension - FINDER_SIZE, 0, 6);
        requireFinderColor(tile, dimension - FINDER_SIZE, dimension - FINDER_SIZE, 3);
    }

    private void requireFinderColor(LogicalTile tile, int startRow, int startCol, int expectedColor) {
        for (int row = startRow; row < startRow + FINDER_SIZE; row++) {
            for (int col = startCol; col < startCol + FINDER_SIZE; col++) {
                if (tile.moduleColorAt(row, col) != expectedColor) {
                    throw new TileCodecException("Logical tile does not match the supported finder patterns");
                }
            }
        }
    }

    private List<Integer> firstMask(Integer diagnosticMaskPattern) {
        if (diagnosticMaskPattern == null) {
            return List.of(0);
        }
        return List.of(diagnosticMaskPattern);
    }

    private byte[] decodeWithExactMaskAndLength(
            List<Integer> symbols,
            TileCodecProfile profile,
            int diagnosticMaskPattern,
            int diagnosticEncodedBytes
    ) {
        if (diagnosticMaskPattern < 0 || diagnosticMaskPattern >= profile.maskPatternCount()) {
            throw new TileCodecException("Logical tile diagnostics contain unsupported maskPattern " + diagnosticMaskPattern);
        }
        if (diagnosticEncodedBytes < SupportedTileCodecProfiles.HEADER_BYTES + profile.parityBytes()) {
            throw new TileCodecException("Logical tile diagnostics contain unsupported encodedBytes " + diagnosticEncodedBytes);
        }
        byte[] bytes = symbolsToBytes(symbols, profile.bitsPerModule());
        if (diagnosticEncodedBytes > bytes.length) {
            throw new TileCodecException("Logical tile diagnostics contain encodedBytes beyond the logical tile capacity");
        }
        DecodingAttempt attempt = tryDecode(bytes, diagnosticEncodedBytes, profile);
        if (attempt.payload() != null) {
            return attempt.payload();
        }
        throw new TileCodecException(attempt.failureMessage());
    }

    private byte[] decodeWithinSupportedSubset(LogicalTile tile, TileCodecProfile profile, Integer diagnosticMaskPattern) {
        DecodingAttempt bestFailure = null;
        byte[] successfulPayload = null;

        for (int maskPattern : candidateMaskPatterns(profile, diagnosticMaskPattern)) {
            List<Integer> symbols = extractDemaskedSymbols(tile, profile, maskPattern);
            byte[] bytes = symbolsToBytes(symbols, profile.bitsPerModule());
            for (int encodedBytes = SupportedTileCodecProfiles.HEADER_BYTES + profile.parityBytes(); encodedBytes <= bytes.length; encodedBytes++) {
                DecodingAttempt attempt = tryDecode(bytes, encodedBytes, profile);
                if (attempt.payload() != null) {
                    if (successfulPayload != null && !Arrays.equals(successfulPayload, attempt.payload())) {
                        throw new TileCodecException("Logical tile decode is ambiguous within the supported subset");
                    }
                    successfulPayload = attempt.payload();
                } else if (bestFailure == null || attempt.specificity() > bestFailure.specificity()) {
                    bestFailure = attempt;
                }
            }
        }

        if (successfulPayload != null) {
            return successfulPayload;
        }
        if (bestFailure != null) {
            throw new TileCodecException(bestFailure.failureMessage());
        }
        throw new TileCodecException("Logical tile cannot be decoded within the supported subset");
    }

    private List<Integer> candidateMaskPatterns(TileCodecProfile profile, Integer diagnosticMaskPattern) {
        LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
        if (diagnosticMaskPattern != null && diagnosticMaskPattern >= 0 && diagnosticMaskPattern < profile.maskPatternCount()) {
            candidates.add(diagnosticMaskPattern);
        }
        for (int maskPattern = 0; maskPattern < profile.maskPatternCount(); maskPattern++) {
            candidates.add(maskPattern);
        }
        return List.copyOf(candidates);
    }

    private List<Integer> extractDemaskedSymbols(LogicalTile tile, TileCodecProfile profile, List<Integer> maskPatterns) {
        if (maskPatterns.size() != 1) {
            throw new IllegalArgumentException("exactly one mask pattern is required");
        }
        return extractDemaskedSymbols(tile, profile, maskPatterns.get(0));
    }

    private List<Integer> extractDemaskedSymbols(LogicalTile tile, TileCodecProfile profile, int maskPattern) {
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

    private boolean isReserved(int row, int col, int dimension) {
        return insideFinder(row, col, 0, 0)
                || insideFinder(row, col, 0, dimension - FINDER_SIZE)
                || insideFinder(row, col, dimension - FINDER_SIZE, 0)
                || insideFinder(row, col, dimension - FINDER_SIZE, dimension - FINDER_SIZE);
    }

    private boolean insideFinder(int row, int col, int startRow, int startCol) {
        return row >= startRow && row < startRow + FINDER_SIZE && col >= startCol && col < startCol + FINDER_SIZE;
    }

    private int reverseMask(int value, int row, int col, int maskPattern, int colorCount) {
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

    private byte[] symbolsToBytes(List<Integer> symbols, int bitsPerModule) {
        int totalBytes = (symbols.size() * bitsPerModule) / 8;
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
                if (currentBits == 8 && byteIndex < bytes.length) {
                    bytes[byteIndex++] = (byte) current;
                    current = 0;
                    currentBits = 0;
                }
            }
        }
        return bytes;
    }

    private DecodingAttempt tryDecode(byte[] bytes, int encodedBytes, TileCodecProfile profile) {
        if (encodedBytes > bytes.length) {
            return new DecodingAttempt(null, "Logical tile cannot be decoded within the supported subset", 0);
        }
        byte[] deinterleaved = interleaver.deinterleave(Arrays.copyOf(bytes, encodedBytes));
        if (deinterleaved.length < SupportedTileCodecProfiles.HEADER_BYTES + profile.parityBytes()) {
            return new DecodingAttempt(null, "Encoded frame is shorter than the supported header", 1);
        }

        int formatVersion = deinterleaved[0] & 0xFF;
        if (formatVersion != FORMAT_VERSION) {
            return new DecodingAttempt(null, "Logical tile uses unsupported format version " + formatVersion, 0);
        }

        int payloadLength = ((deinterleaved[1] & 0xFF) << 8) | (deinterleaved[2] & 0xFF);
        int framedLength = SupportedTileCodecProfiles.HEADER_BYTES + payloadLength;
        int expectedEncodedBytes = framedLength + profile.parityBytes();
        if (expectedEncodedBytes != encodedBytes) {
            return new DecodingAttempt(null, "Logical tile header payload length does not match encoded length", 1);
        }

        byte[] framed = Arrays.copyOf(deinterleaved, framedLength);
        byte[] payload = Arrays.copyOfRange(deinterleaved, 3, 3 + payloadLength);
        int storedPayloadCrc32c = ByteBuffer.wrap(deinterleaved, 3 + payloadLength, 4).getInt();
        int calculatedPayloadCrc32c = ChecksumUtils.crc32c(payload);
        if (storedPayloadCrc32c != calculatedPayloadCrc32c) {
            return new DecodingAttempt(null, "Logical tile payload CRC32C mismatch", 3);
        }
        if (!parity.hasExpectedParity(deinterleaved, framedLength, profile.parityBytes())) {
            return new DecodingAttempt(null, "Logical tile parity bytes do not match the supported deterministic parity stage", 2);
        }

        return new DecodingAttempt(payload, null, 4);
    }

    private Integer parseOptionalInteger(Map<String, String> diagnostics, String key) {
        String value = diagnostics.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new TileCodecException("Logical tile diagnostics contain non-numeric " + key);
        }
    }

    private record DecodingAttempt(byte[] payload, String failureMessage, int specificity) {
    }
}
