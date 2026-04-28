package com.alx4j.jab4j.tile.internal;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.support.ChecksumUtils;
import com.alx4j.jab4j.support.HashingUtils;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileEncoder;

/**
 * Deterministic milestone-one encoder for the supported logical tile subset.
 */
public final class DeterministicTileEncoder implements TileEncoder {

    private static final int FINDER_SIZE = 3;
    private static final int FORMAT_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(DeterministicTileEncoder.class);

    private final DeterministicParity parity = new DeterministicParity();
    private final DeterministicInterleaver interleaver = new DeterministicInterleaver();

    @Override
    public LogicalTile encode(byte[] payload, TileCodecProfile profile) {
        try {
            TileCodecProfile supportedProfile = requireSupportedProfile(payload, profile);

            byte[] framed = framePayload(payload);
            byte[] withParity = parity.appendParity(framed, supportedProfile.parityBytes());
            byte[] interleaved = interleaver.interleave(withParity);
            int sideVersion = chooseSideVersion(interleaved, supportedProfile);
            int dimension = supportedProfile.dimensionForSideVersion(sideVersion);

            List<Integer> symbols = toSymbols(interleaved, supportedProfile.bitsPerModule(), dimension, supportedProfile);
            MatrixResult matrixResult = chooseBestMatrix(symbols, dimension, supportedProfile);

            Map<String, String> diagnostics = new LinkedHashMap<>();
            diagnostics.put("profileId", supportedProfile.profileId());
            diagnostics.put("codecProfileHash", SupportedTileCodecProfiles.CODEC_PROFILE_HASH);
            diagnostics.put("payloadLength", Integer.toString(payload.length));
            diagnostics.put("framedBytes", Integer.toString(framed.length));
            diagnostics.put("encodedBytes", Integer.toString(interleaved.length));
            diagnostics.put("sideVersion", Integer.toString(sideVersion));
            diagnostics.put("dimension", Integer.toString(dimension));
            diagnostics.put("maskPattern", Integer.toString(matrixResult.maskPattern()));
            diagnostics.put("interleaveSeed", Integer.toString(SupportedTileCodecProfiles.INTERLEAVE_SEED));
            diagnostics.put("messageParitySeed", Integer.toString(SupportedTileCodecProfiles.LDPC_MESSAGE_SEED));
            diagnostics.put("payloadCrc32c", Integer.toUnsignedString(ChecksumUtils.crc32c(payload)));
            diagnostics.put("matrixSha256", HashingUtils.sha256Hex(toByteArray(matrixResult.colors())));

            LOGGER.debug(
                    "Encoded logical tile profileId={} payloadBytes={} sideVersion={} dimension={} maskPattern={}",
                    supportedProfile.profileId(),
                    payload.length,
                    sideVersion,
                    dimension,
                    matrixResult.maskPattern()
            );
            return new LogicalTile(
                    dimension,
                    dimension,
                    supportedProfile.quietZoneModules(),
                    supportedProfile.profileId(),
                    matrixResult.colors(),
                    diagnostics
            );
        } catch (RuntimeException exception) {
            if (exception instanceof TileCodecException) {
                LOGGER.warn(
                        "Logical tile encoding failed profileId={} payloadBytes={} message={}",
                        safeProfileId(profile),
                        safePayloadLength(payload),
                        exception.getMessage()
                );
            } else {
                LOGGER.error(
                        "Logical tile encoding failed profileId={} payloadBytes={}",
                        safeProfileId(profile),
                        safePayloadLength(payload),
                        exception
                );
            }
            throw exception;
        }
    }

    private String safeProfileId(TileCodecProfile profile) {
        return profile == null ? null : profile.profileId();
    }

    private Integer safePayloadLength(byte[] payload) {
        return payload == null ? null : payload.length;
    }

    private TileCodecProfile requireSupportedProfile(byte[] payload, TileCodecProfile profile) {
        if (payload == null) {
            throw new TileCodecException("payload must not be null");
        }
        if (profile == null) {
            throw new TileCodecException("profile must not be null");
        }
        TileCodecProfile supportedProfile = SupportedTileCodecProfiles.resolve(profile.profileId());
        if (!supportedProfile.equals(profile)) {
            throw new TileCodecException("Unsupported profile parameters for profile " + profile.profileId());
        }
        return supportedProfile;
    }

    private byte[] framePayload(byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(SupportedTileCodecProfiles.HEADER_BYTES + payload.length);
        output.write(FORMAT_VERSION);
        output.write((payload.length >>> 8) & 0xFF);
        output.write(payload.length & 0xFF);
        output.writeBytes(payload);
        output.writeBytes(ByteBuffer.allocate(4).putInt(ChecksumUtils.crc32c(payload)).array());
        return output.toByteArray();
    }

    private int chooseSideVersion(byte[] encodedBytes, TileCodecProfile profile) {
        int requiredSymbols = (int) Math.ceil((encodedBytes.length * 8.0d) / profile.bitsPerModule());
        for (int sideVersion = profile.minSideVersion(); sideVersion <= profile.maxSideVersion(); sideVersion++) {
            int dimension = profile.dimensionForSideVersion(sideVersion);
            if (requiredSymbols <= dataPositions(dimension).size()) {
                return sideVersion;
            }
        }
        throw new TileCodecException("Payload exceeds supported subset capacity for profile " + profile.profileId());
    }

    private List<Integer> toSymbols(byte[] encodedBytes, int bitsPerModule, int dimension, TileCodecProfile profile) {
        int requiredSymbols = dataPositions(dimension).size();
        List<Integer> symbols = new ArrayList<>(requiredSymbols);
        int current = 0;
        int currentBits = 0;
        for (byte value : encodedBytes) {
            for (int bit = 7; bit >= 0; bit--) {
                current = (current << 1) | ((value >>> bit) & 1);
                currentBits++;
                if (currentBits == bitsPerModule) {
                    symbols.add(current);
                    current = 0;
                    currentBits = 0;
                }
            }
        }
        if (currentBits > 0) {
            current <<= (bitsPerModule - currentBits);
            symbols.add(current);
        }
        DeterministicPseudoRandom random = new DeterministicPseudoRandom(
                SupportedTileCodecProfiles.INTERLEAVE_SEED ^ SupportedTileCodecProfiles.LDPC_MESSAGE_SEED
        );
        while (symbols.size() < requiredSymbols) {
            symbols.add(Integer.remainderUnsigned(random.nextInt(), profile.colorCount()));
        }
        return symbols;
    }

    private MatrixResult chooseBestMatrix(List<Integer> symbols, int dimension, TileCodecProfile profile) {
        List<Coordinate> dataPositions = dataPositions(dimension);
        List<Integer> bestColors = null;
        long bestScore = Long.MAX_VALUE;
        int bestMask = profile.defaultMaskReference();
        for (int maskPattern = 0; maskPattern < profile.maskPatternCount(); maskPattern++) {
            int[] matrix = createBaseMatrix(dimension);
            for (int index = 0; index < dataPositions.size(); index++) {
                Coordinate coordinate = dataPositions.get(index);
                int symbol = symbols.get(index);
                matrix[(coordinate.row() * dimension) + coordinate.col()] = applyMask(symbol, coordinate.row(), coordinate.col(), maskPattern, profile.colorCount());
            }
            long score = scoreMatrix(matrix, dimension, profile.colorCount());
            if (score < bestScore || (score == bestScore && preferMask(maskPattern, bestMask, profile.defaultMaskReference()))) {
                bestScore = score;
                bestMask = maskPattern;
                bestColors = toList(matrix);
            }
        }
        return new MatrixResult(bestMask, bestColors);
    }

    private int[] createBaseMatrix(int dimension) {
        int[] matrix = new int[dimension * dimension];
        paintFinder(matrix, dimension, 0, 0, 0);
        paintFinder(matrix, dimension, 0, dimension - FINDER_SIZE, 0);
        paintFinder(matrix, dimension, dimension - FINDER_SIZE, 0, 6);
        paintFinder(matrix, dimension, dimension - FINDER_SIZE, dimension - FINDER_SIZE, 3);
        return matrix;
    }

    private void paintFinder(int[] matrix, int dimension, int startRow, int startCol, int color) {
        for (int row = startRow; row < startRow + FINDER_SIZE; row++) {
            for (int col = startCol; col < startCol + FINDER_SIZE; col++) {
                matrix[(row * dimension) + col] = color;
            }
        }
    }

    private List<Coordinate> dataPositions(int dimension) {
        List<Coordinate> coordinates = new ArrayList<>(dimension * dimension);
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                if (isReserved(row, col, dimension)) {
                    continue;
                }
                coordinates.add(new Coordinate(row, col));
            }
        }
        return coordinates;
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

    private int applyMask(int color, int row, int col, int maskPattern, int colorCount) {
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
        return active ? (color + maskPattern + 1) % colorCount : color;
    }

    private long scoreMatrix(int[] matrix, int dimension, int colorCount) {
        long score = 0;
        for (int row = 0; row < dimension; row++) {
            int runLength = 1;
            for (int col = 1; col < dimension; col++) {
                int current = matrix[(row * dimension) + col];
                int previous = matrix[(row * dimension) + col - 1];
                if (current == previous) {
                    runLength++;
                } else {
                    if (runLength >= 3) {
                        score += (runLength - 2L) * 3L;
                    }
                    runLength = 1;
                }
            }
            if (runLength >= 3) {
                score += (runLength - 2L) * 3L;
            }
        }
        for (int col = 0; col < dimension; col++) {
            int runLength = 1;
            for (int row = 1; row < dimension; row++) {
                int current = matrix[(row * dimension) + col];
                int previous = matrix[((row - 1) * dimension) + col];
                if (current == previous) {
                    runLength++;
                } else {
                    if (runLength >= 3) {
                        score += (runLength - 2L) * 3L;
                    }
                    runLength = 1;
                }
            }
            if (runLength >= 3) {
                score += (runLength - 2L) * 3L;
            }
        }
        int[] counts = new int[colorCount];
        for (int value : matrix) {
            counts[value % colorCount]++;
        }
        int expected = matrix.length / colorCount;
        for (int count : counts) {
            score += Math.abs(count - expected);
        }
        return score;
    }

    private boolean preferMask(int candidate, int existing, int preferred) {
        int candidateDistance = Math.abs(candidate - preferred);
        int existingDistance = Math.abs(existing - preferred);
        return candidateDistance < existingDistance || (candidateDistance == existingDistance && candidate < existing);
    }

    private List<Integer> toList(int[] matrix) {
        List<Integer> values = new ArrayList<>(matrix.length);
        for (int color : matrix) {
            values.add(color);
        }
        return values;
    }

    private byte[] toByteArray(List<Integer> colors) {
        byte[] bytes = new byte[colors.size()];
        for (int index = 0; index < colors.size(); index++) {
            bytes[index] = colors.get(index).byteValue();
        }
        return bytes;
    }

    private record Coordinate(int row, int col) {
    }

    private record MatrixResult(int maskPattern, List<Integer> colors) {
    }
}
