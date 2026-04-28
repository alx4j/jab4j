package pro.alx4j.jab4j.tile.internal;

import java.util.Arrays;

/**
 * Small deterministic parity stage seeded from codec profile constants.
 */
final class DeterministicParity {

    byte[] appendParity(byte[] input, int parityBytes) {
        if (parityBytes == 0) {
            return Arrays.copyOf(input, input.length);
        }
        byte[] parity = new byte[parityBytes];
        DeterministicPseudoRandom random = new DeterministicPseudoRandom(SupportedTileCodecProfiles.LDPC_MESSAGE_SEED);
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

    boolean hasExpectedParity(byte[] encoded, int framedLength, int parityBytes) {
        if (framedLength < 0 || parityBytes < 0 || framedLength + parityBytes > encoded.length) {
            return false;
        }
        byte[] framed = Arrays.copyOf(encoded, framedLength);
        byte[] expected = appendParity(framed, parityBytes);
        return Arrays.equals(expected, Arrays.copyOf(encoded, framedLength + parityBytes));
    }
}
