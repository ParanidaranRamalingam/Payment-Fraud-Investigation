package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.ProcessorMode;

import java.util.List;

/**
 * Advisory answer to an analyst message.
 *
 * @param evidenceIds evidence of the same case the answer is grounded on (validated before persistence)
 */
public record CaseAssistantReply(String content, List<String> evidenceIds, ProcessorMode processorMode) {

    public CaseAssistantReply {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
