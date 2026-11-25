package com.repolens.core.algorithms;

import java.nio.charset.StandardCharsets;

/**
 * Hand-crafted Bloom Filter (Probabilistic Set Membership Data Structure).
 * <p>
 * Uses Kirsch-Mitzenmacher double hashing to simulate $k$ independent hash functions
 * from two 64-bit hash values:
 * $$g_i(x) = (h_1(x) + i \cdot h_2(x)) \pmod m$$
 *
 * <h3>CRITICAL BLOOM FILTER RULE (Project Hard Requirement):</h3>
 * <blockquote>
 * <b>Negative Result:</b> Guarantees the item is <i>definitely new / absent</i> from cache (0% false negative rate).
 * <br>
 * <b>Positive Result:</b> Indicates the item <i>might</i> be in cache (due to potential false positives).
 * <b>A positive result MUST be confirmed by a secondary cache/database lookup</b> before skipping any work!
 * </blockquote>
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Insertion (add):</b> $O(k)$ where $k$ is the number of hash functions. Computes 2 base hashes and sets $k$ bits.</li>
 *   <li><b>Membership Test (mightContain):</b> $O(k)$ bit tests. Early exit on first zero bit.</li>
 *   <li><b>Space Complexity:</b> $O(m)$ bits, drastically smaller than storing raw keys in RAM.</li>
 *   <li><b>Optimal Bit Array Size:</b> $m = -\frac{n \ln p}{(\ln 2)^2}$ where $n$ is expected insertions, $p$ is false positive probability.</li>
 *   <li><b>Optimal Hash Functions:</b> $k = \frac{m}{n} \ln 2$.</li>
 * </ul>
 *
 * @param <T> element type (serialized via {@code toString()} or custom hashing)
 */
public class BloomFilter<T> {

    private final long[] bitArray;
    private final long bitSize;
    private final int numHashFunctions;
    private long elementCount;

    /**
     * Constructs an optimal Bloom Filter sized for expected insertions and false positive probability.
     *
     * @param expectedInsertions estimated number of distinct elements to be inserted ($n > 0$)
     * @param falsePositiveRate  desired false positive probability ($0 < p < 1$), e.g. 0.01 for 1%
     */
    public BloomFilter(int expectedInsertions, double falsePositiveRate) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("Expected insertions must be > 0, got: " + expectedInsertions);
        }
        if (falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0) {
            throw new IllegalArgumentException("False positive rate must be between 0 and 1, got: " + falsePositiveRate);
        }

        // Optimal bit size m = - (n * ln(p)) / (ln(2)^2)
        long calculatedBits = (long) Math.ceil(-1.0 * expectedInsertions * Math.log(falsePositiveRate) / (Math.log(2) * Math.log(2)));
        this.bitSize = Math.max(64L, calculatedBits);

        // Optimal hash count k = (m / n) * ln(2)
        int calculatedHashes = (int) Math.round(((double) this.bitSize / expectedInsertions) * Math.log(2));
        this.numHashFunctions = Math.max(1, calculatedHashes);

        int numLongs = (int) Math.ceil((double) this.bitSize / 64.0);
        this.bitArray = new long[numLongs];
        this.elementCount = 0;
    }

    /**
     * Adds an element into the Bloom Filter by setting $k$ bit positions.
     * <p>Time Complexity: $O(k)$</p>
     *
     * @param element the element to add
     */
    public void add(T element) {
        if (element == null) {
            return;
        }
        byte[] bytes = element.toString().getBytes(StandardCharsets.UTF_8);
        long hash1 = murmurHash3_64(bytes, 0);
        long hash2 = murmurHash3_64(bytes, hash1);

        for (int i = 0; i < numHashFunctions; i++) {
            long combinedHash = hash1 + ((long) i * hash2);
            long bitIndex = Math.floorMod(combinedHash, bitSize);
            setBit(bitIndex);
        }
        elementCount++;
    }

    /**
     * Tests whether the element might be present in the set.
     * <p>
     * <b>RULE:</b> If this returns {@code false}, the item is guaranteed to be ABSENT (cache miss, definitely new).
     * If this returns {@code true}, the caller MUST verify with the database / cache lookup.
     * </p>
     * <p>Time Complexity: $O(k)$</p>
     *
     * @param element the element to test
     * @return false if DEFINITELY NEW, true if MIGHT BE PRESENT (must verify in DB)
     */
    public boolean mightContain(T element) {
        if (element == null) {
            return false;
        }
        byte[] bytes = element.toString().getBytes(StandardCharsets.UTF_8);
        long hash1 = murmurHash3_64(bytes, 0);
        long hash2 = murmurHash3_64(bytes, hash1);

        for (int i = 0; i < numHashFunctions; i++) {
            long combinedHash = hash1 + ((long) i * hash2);
            long bitIndex = Math.floorMod(combinedHash, bitSize);
            if (!getBit(bitIndex)) {
                return false; // Negative: definitely not present!
            }
        }
        return true; // Positive: might be present, must confirm with DB/cache!
    }

    /**
     * Returns the size of the bit array in bits ($m$).
     */
    public long getBitSize() {
        return bitSize;
    }

    /**
     * Returns the number of simulated hash functions ($k$).
     */
    public int getNumHashFunctions() {
        return numHashFunctions;
    }

    /**
     * Returns the number of elements inserted.
     */
    public long getElementCount() {
        return elementCount;
    }

    /**
     * Computes the current estimated false positive rate based on inserted elements:
     * $$p \approx \left(1 - e^{-k n / m}\right)^k$$
     */
    public double getCurrentFalsePositiveRate() {
        return Math.pow(1.0 - Math.exp(-1.0 * numHashFunctions * elementCount / bitSize), numHashFunctions);
    }

    /* ---------------- Bitwise Array Operations ---------------- */

    private void setBit(long index) {
        int wordIndex = (int) (index >>> 6); // divide by 64
        int bitOffset = (int) (index & 0x3F); // index % 64
        bitArray[wordIndex] |= (1L << bitOffset);
    }

    private boolean getBit(long index) {
        int wordIndex = (int) (index >>> 6);
        int bitOffset = (int) (index & 0x3F);
        return (bitArray[wordIndex] & (1L << bitOffset)) != 0;
    }

    /* ---------------- 64-bit MurmurHash3 Implementation ---------------- */

    /**
     * Hand-crafted 64-bit MurmurHash3 variant for fast, uniform hashing.
     */
    private static long murmurHash3_64(byte[] data, long seed) {
        long h = seed ^ (data.length * 0x517cc1b727220a95L);
        int length8 = data.length / 8;

        for (int i = 0; i < length8; i++) {
            int i8 = i * 8;
            long k = ((long) data[i8] & 0xff)
                    | (((long) data[i8 + 1] & 0xff) << 8)
                    | (((long) data[i8 + 2] & 0xff) << 16)
                    | (((long) data[i8 + 3] & 0xff) << 24)
                    | (((long) data[i8 + 4] & 0xff) << 32)
                    | (((long) data[i8 + 5] & 0xff) << 40)
                    | (((long) data[i8 + 6] & 0xff) << 48)
                    | (((long) data[i8 + 7] & 0xff) << 56);

            k *= 0xbf58476d1ce4e5b9L;
            k ^= (k >>> 31);
            k *= 0x94d049bb133111ebL;

            h ^= k;
            h = (h << 27) | (h >>> (64 - 27));
            h = h * 5 + 0x52dce729;
        }

        int rem = data.length % 8;
        if (rem > 0) {
            long k = 0;
            int offset = length8 * 8;
            for (int i = 0; i < rem; i++) {
                k |= ((long) data[offset + i] & 0xff) << (i * 8);
            }
            k *= 0xbf58476d1ce4e5b9L;
            k ^= (k >>> 31);
            k *= 0x94d049bb133111ebL;
            h ^= k;
        }

        // Avalanche finalization
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);

        return h;
    }
}
