package com.capstone.fraudcase.tools.synthetic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicSeedTest {

    @Test
    void of_sameInputs_produceIdenticalValues() {
        DeterministicSeed a = DeterministicSeed.of("device", "****4242", "txn_000123");
        DeterministicSeed b = DeterministicSeed.of("device", "****4242", "txn_000123");

        assertThat(IntStream.range(0, DeterministicSeed.SLOTS).map(i -> a.nextInt(i, 1000)).toArray())
                .containsExactly(IntStream.range(0, DeterministicSeed.SLOTS).map(i -> b.nextInt(i, 1000)).toArray());
        assertThat(a.token(20)).isEqualTo(b.token(20));
    }

    @Test
    void of_knownInput_producesPinnedValueAcrossRunsAndJvms() {
        DeterministicSeed seed = DeterministicSeed.of("pin", "cus_A1b2C3d4");

        assertThat(seed.token(12)).isEqualTo("bjlhfcllibfg");
        assertThat(seed.nextInt(0, 1000)).isEqualTo(583);
    }

    @Test
    void of_surroundingWhitespace_isIgnored() {
        assertThat(DeterministicSeed.of("pin", " cus_A1b2C3d4 ").token(32))
                .isEqualTo(DeterministicSeed.of("pin", "cus_A1b2C3d4").token(32));
    }

    @Test
    void of_differentNamespaceOrParts_produceDifferentDigests() {
        String base = DeterministicSeed.of("device", "a", "b").token(32);

        assertThat(DeterministicSeed.of("location", "a", "b").token(32)).isNotEqualTo(base);
        assertThat(DeterministicSeed.of("device", "ab", "").token(32)).isNotEqualTo(base);
        assertThat(DeterministicSeed.of("device", "b", "a").token(32)).isNotEqualTo(base);
    }

    @Test
    void token_containsLettersOnly_neverResemblesCardNumber() {
        assertThat(DeterministicSeed.of("token", "x").token(32)).matches("[a-p]{32}");
    }

    @Test
    void between_staysWithinInclusiveBounds() {
        DeterministicSeed seed = DeterministicSeed.of("bounds", "x");

        assertThat(IntStream.range(0, DeterministicSeed.SLOTS).map(i -> seed.between(i, 5, 9)))
                .allSatisfy(v -> assertThat(v).isBetween(5, 9));
        assertThat(seed.pick(0, List.of("only"))).isEqualTo("only");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, DeterministicSeed.SLOTS})
    void nextInt_slotOutOfRange_throws(int slot) {
        assertThatThrownBy(() -> DeterministicSeed.of("x").nextInt(slot, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextInt_nonPositiveBound_throws() {
        assertThatThrownBy(() -> DeterministicSeed.of("x").nextInt(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
