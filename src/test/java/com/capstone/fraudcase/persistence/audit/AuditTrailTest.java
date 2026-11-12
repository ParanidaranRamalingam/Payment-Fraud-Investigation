package com.capstone.fraudcase.persistence.audit;

import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.persistence.entity.AuditEventEntity;
import com.capstone.fraudcase.persistence.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailTest {

    @Mock
    private AuditEventRepository repository;
    @InjectMocks
    private AuditTrail audit;

    private AuditEventEntity saved() {
        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void system_recordsSystemActorAndStatuses() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        audit.system("case-1", AuditEventType.STATUS_CHANGED, CaseStatus.NEW, CaseStatus.ENRICHING, "started", "corr-1");

        AuditEventEntity event = saved();
        assertThat(event.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.getActorId()).isEqualTo(AuditTrail.SYSTEM_ACTOR);
        assertThat(event.getFromStatus()).isEqualTo(CaseStatus.NEW);
        assertThat(event.getToStatus()).isEqualTo(CaseStatus.ENRICHING);
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void record_detailsWithUnmaskedCardNumber_redactedBeforeStorage() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        audit.record("case-1", AuditEventType.MESSAGE_RECEIVED, ActorType.ANALYST, "analyst-1", null, null,
                "customer quoted 4111 1111 1111 1111 and\u0007bell", null);

        assertThat(saved().getDetails()).isEqualTo("customer quoted [REDACTED] andbell");
    }

    @Test
    void record_overlongDetails_truncatedToColumnLimit() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        audit.system("case-1", AuditEventType.TOOL_INVOKED, null, null, "x".repeat(5000), null);

        assertThat(saved().getDetails()).hasSize(AuditEventEntity.MAX_DETAILS_LENGTH).endsWith("…");
    }

    @Test
    void record_overlongActorAndCorrelation_bounded() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        audit.record("case-1", AuditEventType.CASE_CREATED, ActorType.ANALYST, "a".repeat(100), null, null, null,
                "c".repeat(100));

        AuditEventEntity event = saved();
        assertThat(event.getActorId()).hasSize(64);
        assertThat(event.getCorrelationId()).hasSize(64);
        assertThat(event.getDetails()).isNull();
    }

    @Test
    void history_delegatesToCaseScopedOrderedQuery() {
        when(repository.findByCaseIdOrderByOccurredAtAsc("case-1")).thenReturn(List.of());

        assertThat(audit.history("case-1")).isEmpty();
        verify(repository).findByCaseIdOrderByOccurredAtAsc("case-1");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void sanitize_nullDetails_staysNull() {
        assertThat(AuditTrail.sanitize(null)).isNull();
    }
}
