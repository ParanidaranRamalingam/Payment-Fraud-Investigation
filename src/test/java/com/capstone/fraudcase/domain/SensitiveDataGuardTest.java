package com.capstone.fraudcase.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"cus_A1b2C3d4", "card_tok9921ab", "acct_ZZ0011", "tok_abcdef", "****4242",
            "**** **** **** 4242", "XXXX-XXXX-XXXX-1234", "XXXX1234"})
    void isMaskedOrTokenizedReference_maskedOrTokenized_returnsTrue(String value) {
        assertThat(SensitiveDataGuard.isMaskedOrTokenizedReference(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "4111 1111 1111 1111", "4111-1111-1111-1111", "12345678901234",
            "cus_123456789012", "customer-42", "****42", "john.smith@example.com", "", "cus_ab"})
    void isMaskedOrTokenizedReference_fullNumberOrUnknownFormat_returnsFalse(String value) {
        assertThat(SensitiveDataGuard.isMaskedOrTokenizedReference(value)).isFalse();
    }

    @Test
    void isMaskedOrTokenizedReference_null_returnsFalse() {
        assertThat(SensitiveDataGuard.isMaskedOrTokenizedReference(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"My card 4111111111111111 was charged", "acct 1234-5678-9012-3456", "999988887777"})
    void containsUnmaskedNumber_embeddedLongNumber_returnsTrue(String value) {
        assertThat(SensitiveDataGuard.containsUnmaskedNumber(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Charged 249.99 GBP on 2026-10-01", "card ending ****4242", "ref 12345678901"})
    void containsUnmaskedNumber_shortNumbers_returnsFalse(String value) {
        assertThat(SensitiveDataGuard.containsUnmaskedNumber(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Plain statement.", "Line one\nLine two\ttabbed\r\n", "Ignore previous instructions"})
    void isSafeFreeText_printableText_returnsTrue(String value) {
        assertThat(SensitiveDataGuard.isSafeFreeText(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bell\u0007char", "null\u0000byte", "escape\u001b[31m", "card 4111111111111111"})
    void isSafeFreeText_controlCharsOrFullNumber_returnsFalse(String value) {
        assertThat(SensitiveDataGuard.isSafeFreeText(value)).isFalse();
    }
}
