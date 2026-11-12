package com.capstone.fraudcase.tools.model;

import com.capstone.fraudcase.domain.ContentHash;
import com.capstone.fraudcase.domain.EvidenceTool;

import java.util.Map;
import java.util.TreeMap;

/**
 * Typed, deterministic result of an allow-listed evidence tool. These DTOs are shared by the rules processor and
 * any AI adapter so business rules see the same facts regardless of processor mode.
 */
public sealed interface EvidencePayload
        permits TransactionDetails, DeviceSignals, LocationComparison, MerchantRiskProfile, PriorDisputes, CaseSla {

    EvidenceTool tool();

    /** Short, human-readable statement of the facts; contains only masked/synthetic identifiers. */
    String summary();

    /** Typed facts as string key/value pairs. */
    Map<String, String> attributes();

    /** Stable serialisation: attributes sorted by key, one {@code key=value} per line. */
    default String canonicalForm() {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(attributes()).forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    default String contentHash() {
        return ContentHash.sha256Hex(tool().name() + "\n" + canonicalForm());
    }
}
