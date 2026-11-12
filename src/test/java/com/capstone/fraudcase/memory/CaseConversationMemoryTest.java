package com.capstone.fraudcase.memory;

import com.capstone.fraudcase.agent.common.CaseMessageContext;
import com.capstone.fraudcase.domain.CaseMessage;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.MessageRole;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.persistence.entity.CaseMessageEntity;
import com.capstone.fraudcase.persistence.repository.CaseMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseConversationMemoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final CaseMessageRepository repository = mock(CaseMessageRepository.class);

    private CaseConversationMemory memory(int window) {
        return new CaseConversationMemory(repository, new MemoryProperties(window));
    }

    private static String newCaseId() {
        return UUID.randomUUID().toString();
    }

    private static List<CaseMessageEntity> history(String caseId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> CaseMessageEntity.analystMessage(caseId, "analyst-1", "message " + i,
                        T0.plusSeconds(i)))
                .toList();
    }

    @Test
    void window_returnsOnlyMostRecentMessages_oldestFirst() {
        String caseId = newCaseId();
        when(repository.findByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(history(caseId, 25));

        List<CaseMessage> recent = memory(20).recent(caseId);

        assertThat(recent).hasSize(20);
        assertThat(recent.getFirst().content()).isEqualTo("message 5");
        assertThat(recent.getLast().content()).isEqualTo("message 24");
        assertThat(recent).allSatisfy(m -> assertThat(m.caseId()).isEqualTo(caseId));
    }

    @Test
    void window_returnsEverythingWhenBelowLimit() {
        String caseId = newCaseId();
        when(repository.findByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(history(caseId, 3));

        assertThat(memory(20).recent(caseId)).hasSize(3);
    }

    @Test
    void t10_caseIsolation_caseBMustNotRecallCaseA() {
        String caseA = newCaseId();
        String caseB = newCaseId();
        String secret = "unique fact only discussed in case A";
        when(repository.findByCaseIdOrderByCreatedAtAsc(caseA))
                .thenReturn(List.of(CaseMessageEntity.analystMessage(caseA, "analyst-1", secret, T0)));
        when(repository.findByCaseIdOrderByCreatedAtAsc(caseB))
                .thenReturn(history(caseB, 2));

        CaseConversationMemory memory = memory(20);
        List<CaseMessage> memoryB = memory.recent(caseB);

        assertThat(memoryB).hasSize(2).allSatisfy(m -> assertThat(m.caseId()).isEqualTo(caseB));
        assertThat(memoryB.stream().map(CaseMessage::content)).noneMatch(c -> c.contains(secret));
    }

    @Test
    void context_rejectsMemoryFromAnotherCase() {
        CaseMessage foreign = new CaseMessage(UUID.randomUUID().toString(), newCaseId(), MessageRole.ANALYST,
                "analyst-1", "hello", null, List.of(), null, T0);
        assertThatThrownBy(() -> new CaseMessageContext(newCaseId(), CaseStatus.NEW, List.of(), null,
                "question", List.of(foreign)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("another case");
    }

    @Test
    void window_redactsUnmaskedNumbers_thePersistedContentIsNotReturnedToMemory() {
        String caseId = newCaseId();
        when(repository.findByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of(
                CaseMessageEntity.analystMessage(caseId, "analyst-1", "card 4111 1111 1111 1111 charged", T0)));

        List<CaseMessage> recent = memory(20).recent(caseId);

        assertThat(recent.getFirst().content()).contains("[REDACTED]")
                .doesNotContain("4111 1111 1111 1111");
    }

    @Test
    void assistantRepliesKeepRoleAndProcessorMetadata() {
        String caseId = newCaseId();
        CaseMessageEntity question = CaseMessageEntity.analystMessage(caseId, "analyst-1", "status?", T0);
        CaseMessageEntity reply = CaseMessageEntity.assistantReply(question, "Status: NEW.", List.of(),
                ProcessorMode.RULES, "system", T0.plusSeconds(1));
        when(repository.findByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of(question, reply));

        List<CaseMessage> recent = memory(20).recent(caseId);

        assertThat(recent.get(1).role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(recent.get(1).processorMode()).isEqualTo(ProcessorMode.RULES);
        assertThat(recent.get(1).replyToId()).isEqualTo(question.getId());
    }

    @Test
    void properties_rejectOutOfRangeWindow() {
        assertThatThrownBy(() -> new MemoryProperties(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryProperties(MemoryProperties.MAX_WINDOW + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
