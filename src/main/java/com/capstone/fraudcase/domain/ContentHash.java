package com.capstone.fraudcase.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** SHA-256 helpers used for evidence content hashes and deterministic fixture seeds. */
public final class ContentHash {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private ContentHash() {
    }

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    public static boolean isSha256Hex(String value) {
        return value != null && SHA256_HEX.matcher(value).matches();
    }
}
