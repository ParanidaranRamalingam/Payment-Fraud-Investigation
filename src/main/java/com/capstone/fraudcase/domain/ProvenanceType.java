package com.capstone.fraudcase.domain;

/**
 * Where a material fact came from, so the UI and reviewer can distinguish provenance.
 */
public enum ProvenanceType {
    TOOL_RESULT,
    CUSTOMER_STATEMENT,
    ANALYST_NOTE,
    POLICY_PASSAGE,
    RULE_ENGINE,
    MODEL_INTERPRETATION
}
