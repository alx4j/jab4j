package pro.alx4j.jab4j.tile.internal;

/**
 * Small deterministic pseudo-random primitive used by interleave and parity helpers.
 */
final class DeterministicPseudoRandom {

    private long seed;

    DeterministicPseudoRandom(long seed) {
        this.seed = seed;
    }

    int nextInt() {
        seed = (6364136223846793005L * seed) + 1L;
        int value = (int) (seed >>> 32);
        value ^= value >>> 11;
        value ^= (value << 7) & 0x9D2C5680;
        value ^= (value << 15) & 0xEFC60000;
        value ^= value >>> 18;
        return value;
    }
}
