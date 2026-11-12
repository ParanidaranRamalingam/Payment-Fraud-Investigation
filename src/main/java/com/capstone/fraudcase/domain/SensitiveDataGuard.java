package com.capstone.fraudcase.domain;

import java.util.regex.Pattern;

/**
 * Framework-neutral checks that keep full card/account numbers and unsafe characters out of the system.
 * Inputs are rejected rather than silently rewritten so the analyst sees what was refused.
 */
public final class SensitiveDataGuard {

    /** 12+ digits, optionally separated by single spaces or dashes: looks like a full PAN or account number. */
    private static final Pattern UNMASKED_NUMBER = Pattern.compile("\\d(?:[ -]?\\d){11,}");

    /** Tokenized reference, e.g. {@code cus_8f3kA21x}, {@code card_tok9921ab}. */
    private static final Pattern TOKEN_REFERENCE = Pattern.compile("^(?:tok|cus|card|acct)_[A-Za-z0-9]{6,48}$");

    /** Masked reference showing at most the last four digits, e.g. {@code ****1234}, {@code XXXX-XXXX-XXXX-1234}. */
    private static final Pattern MASKED_REFERENCE = Pattern.compile("^(?:[*X]{4}[ -]?){1,4}\\d{4}$");

    /** Control characters other than tab, LF and CR. */
    private static final Pattern UNSAFE_CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t\\n\\r]]");

    private SensitiveDataGuard() {
    }

    public static boolean containsUnmaskedNumber(String value) {
        return value != null && UNMASKED_NUMBER.matcher(value).find();
    }

    public static boolean isMaskedOrTokenizedReference(String value) {
        if (value == null || containsUnmaskedNumber(value)) {
            return false;
        }
        return TOKEN_REFERENCE.matcher(value).matches() || MASKED_REFERENCE.matcher(value).matches();
    }

    public static boolean isSafeFreeText(String value) {
        return value == null
                || (!UNSAFE_CONTROL_CHARS.matcher(value).find() && !containsUnmaskedNumber(value));
    }

    /**
     * For system-generated text written to logs/audit (never for user input, which is rejected instead):
     * replaces anything that looks like a full card/account number and drops unsafe control characters.
     */
    public static String redact(String value) {
        if (value == null) {
            return null;
        }
        String withoutControls = UNSAFE_CONTROL_CHARS.matcher(value).replaceAll("");
        return UNMASKED_NUMBER.matcher(withoutControls).replaceAll("[REDACTED]");
    }
}
