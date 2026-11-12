package com.capstone.fraudcase.domain;

import java.time.Instant;
import java.util.List;

import static com.capstone.fraudcase.domain.DomainChecks.maxLength;
import static com.capstone.fraudcase.domain.DomainChecks.required;

/**
 * One entry of a case's conversation memory. Analyst messages are untrusted data; assistant replies answer exactly
 * one analyst message, state the processor that produced them and cite only evidence of the same case.
 */
public record CaseMessage(
        String messageId,
        String caseId,
        MessageRole role,
        String authorId,
        String content,
        String replyToId,
        List<String> evidenceIds,
        ProcessorMode processorMode,
        Instant createdAt) {

    public static final int MAX_ANALYST_CONTENT_LENGTH = 2000;
    public static final int MAX_REPLY_CONTENT_LENGTH = 4000;

    public CaseMessage {
        required(messageId, "messageId");
        required(caseId, "caseId");
        required(role, "role");
        required(authorId, "authorId");
        required(content, "content");
        required(createdAt, "createdAt");
        maxLength(content, role == MessageRole.ANALYST ? MAX_ANALYST_CONTENT_LENGTH : MAX_REPLY_CONTENT_LENGTH,
                "content");
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        boolean reply = role == MessageRole.ASSISTANT;
        if (reply != (replyToId != null) || reply != (processorMode != null)) {
            throw new DomainValidationException("only assistant replies carry replyToId and processorMode, and both are required");
        }
        if (!reply && !evidenceIds.isEmpty()) {
            throw new DomainValidationException("analyst messages do not cite evidence");
        }
    }
}
