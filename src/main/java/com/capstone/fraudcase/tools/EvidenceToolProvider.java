package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.tools.model.EvidencePayload;

/**
 * Contract for an allow-listed, deterministic evidence tool. Implementations are read-only: they must never
 * change customer/account state or perform any external action. Expected failures are reported by throwing
 * {@link ToolException}; {@link ToolInvoker} converts every outcome into a traced, structured result.
 */
public interface EvidenceToolProvider {

    EvidenceTool tool();

    /** Versioned identity of the data source, recorded as evidence provenance (max 64 chars). */
    String source();

    EvidencePayload fetch(ToolRequest request);
}
