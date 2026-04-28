package pro.alx4j.jab4j.tile.internal;

import java.util.Arrays;

/**
 * Deterministic byte interleaver used by the supported codec profile.
 */
final class DeterministicInterleaver {

    byte[] interleave(byte[] input) {
        byte[] values = Arrays.copyOf(input, input.length);
        DeterministicPseudoRandom random = new DeterministicPseudoRandom(SupportedTileCodecProfiles.INTERLEAVE_SEED);
        int length = values.length;
        for (int index = 0; index < length; index++) {
            int remaining = length - index;
            long unsigned = Integer.toUnsignedLong(random.nextInt());
            int position = (int) Math.floor((unsigned / (double) 0xFFFFFFFFL) * remaining);
            if (position >= remaining) {
                position = remaining - 1;
            }
            int from = length - 1 - index;
            byte tmp = values[from];
            values[from] = values[position];
            values[position] = tmp;
        }
        return values;
    }

    byte[] deinterleave(byte[] input) {
        byte[] values = Arrays.copyOf(input, input.length);
        int[] index = new int[values.length];
        for (int position = 0; position < index.length; position++) {
            index[position] = position;
        }

        DeterministicPseudoRandom random = new DeterministicPseudoRandom(SupportedTileCodecProfiles.INTERLEAVE_SEED);
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
}
