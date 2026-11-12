package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.CaseMessage;
import com.capstone.fraudcase.domain.MessageRole;
import com.capstone.fraudcase.domain.ProcessorMode;

import java.time.Instant;
import java.util.List;

public record CaseMessageResponse(
        String messageId,
        String caseId,
        MessageRole role,
        String authorId,
        String content,
        String replyToId,
        List<String> evidenceIds,
        ProcessorMode processorMode,
        Instant createdAt) {

    public static CaseMessageResponse from(CaseMessage m) {
        return new CaseMessageResponse(m.messageId(), m.caseId(), m.role(), m.authorId(), m.content(), m.replyToId(),
                m.evidenceIds(), m.processorMode(), m.createdAt());
    }
}
