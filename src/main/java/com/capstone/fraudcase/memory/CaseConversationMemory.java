package com.capstone.fraudcase.memory;

import com.capstone.fraudcase.domain.CaseMessage;
import com.capstone.fraudcase.domain.SensitiveDataGuard;
import com.capstone.fraudcase.persistence.entity.CaseMessageEntity;
import com.capstone.fraudcase.persistence.repository.CaseMessageRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bounded, case-scoped conversation memory. The memory key is the case ID: every lookup is a case-scoped
 * query and there is no cross-case recall by construction. Only the {@code fraud.memory.window} most recent
 * messages are surfaced, and message content is redacted so the working memory never carries a full
 * card/account number even though the persisted record keeps the validated original. Nothing is stored
 * outside {@code case_message}: there is no long-term profile or shared summary payload.
 */
@Component
public class CaseConversationMemory {

    private final CaseMessageRepository messages;
    private final int window;

    public CaseConversationMemory(CaseMessageRepository messages, MemoryProperties properties) {
        this.messages = messages;
        this.window = properties.window();
    }

    /** The most recent {@code window} messages of this one case, oldest first. */
    public List<CaseMessage> recent(String caseId) {
        List<CaseMessage> all = messages.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
                .map(CaseMessageEntity::toDomain)
                .toList();
        int from = Math.max(0, all.size() - window);
        return all.subList(from, all.size()).stream().map(CaseConversationMemory::redacted).toList();
    }

    public long size(String caseId) {
        return messages.countByCaseId(caseId);
    }

    public int window() {
        return window;
    }

    /** Working-memory view only: mask anything resembling a full PAN/account number. */
    private static CaseMessage redacted(CaseMessage message) {
        String content = SensitiveDataGuard.redact(message.content());
        if (content.equals(message.content())) {
            return message;
        }
        return new CaseMessage(message.messageId(), message.caseId(), message.role(), message.authorId(), content,
                message.replyToId(), message.evidenceIds(), message.processorMode(), message.createdAt());
    }
}
