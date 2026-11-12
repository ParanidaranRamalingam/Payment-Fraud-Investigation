package com.capstone.fraudcase.tools.synthetic;

import com.capstone.fraudcase.domain.ContentHash;

import java.util.List;

/**
 * Pure function of its inputs: a SHA-256 digest over a namespace and identifier parts, read as 16 independent
 * 16-bit slots. No {@code Random}, clock or JVM state is involved, so values are identical across runs and JVMs.
 */
final class DeterministicSeed {

    static final int SLOTS = 16;
    private static final char SEPARATOR = '\u001f';

    private final byte[] digest;

    private DeterministicSeed(byte[] digest) {
        this.digest = digest;
    }

    static DeterministicSeed of(String namespace, String... parts) {
        StringBuilder sb = new StringBuilder(namespace);
        for (String part : parts) {
            sb.append(SEPARATOR).append(part == null ? "" : part.strip());
        }
        return new DeterministicSeed(ContentHash.sha256(sb.toString()));
    }

    /** Value in {@code [0, bound)} taken from {@code slot}. */
    int nextInt(int slot, int bound) {
        if (slot < 0 || slot >= SLOTS) {
            throw new IllegalArgumentException("slot must be in [0, " + SLOTS + ")");
        }
        if (bound <= 0 || bound > 65_536) {
            throw new IllegalArgumentException("bound must be in (0, 65536]");
        }
        int value = ((digest[slot * 2] & 0xff) << 8) | (digest[slot * 2 + 1] & 0xff);
        return value % bound;
    }

    int between(int slot, int minInclusive, int maxInclusive) {
        return minInclusive + nextInt(slot, maxInclusive - minInclusive + 1);
    }

    boolean chance(int slot, int percent) {
        return nextInt(slot, 100) < percent;
    }

    <T> T pick(int slot, List<T> values) {
        return values.get(nextInt(slot, values.size()));
    }

    /** Letters-only token ({@code a}-{@code p} per nibble) so synthetic IDs never resemble card numbers. */
    String token(int length) {
        if (length <= 0 || length > digest.length * 2) {
            throw new IllegalArgumentException("token length must be in (0, " + digest.length * 2 + "]");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int nibble = (digest[i / 2] >> (i % 2 == 0 ? 4 : 0)) & 0x0f;
            sb.append((char) ('a' + nibble));
        }
        return sb.toString();
    }
}
