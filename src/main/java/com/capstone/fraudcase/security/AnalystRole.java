package com.capstone.fraudcase.security;

/**
 * Deterministic authorization roles. {@code ANALYST} may create, investigate and query cases; {@code REVIEWER}
 * may submit the mandatory human review disposition. Both may read case artefacts.
 */
public enum AnalystRole {
    ANALYST,
    REVIEWER
}
