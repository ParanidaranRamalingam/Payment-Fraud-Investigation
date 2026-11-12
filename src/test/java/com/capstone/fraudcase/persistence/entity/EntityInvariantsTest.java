package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.TestFixtures;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.InvalidCaseTransitionException;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityInvariantsTest {

    private static FraudCaseEntity newCase() {
        return new FraudCaseEntity("cus_A1b2C3d4", "****4242", "I do not recognise this.", "analyst-1");
    }

    @Test
    void fraudCase_new_startsNewWithGeneratedIdAndHumanReview() {
        FraudCaseEntity entity = newCase();

        assertThat(entity.getStatus()).isEqualTo(CaseStatus.NEW);
        assertThat(entity.getId()).hasSize(36);
        assertThat(entity.isNew()).isTrue();
        assertThat(entity.isHumanReviewRequired()).isTrue();
        assertThat(entity.isSyntheticDataConfirmed()).isTrue();
    }

    @Test
    void fraudCase_twoInstances_haveDistinctIds() {
        assertThat(newCase().getId()).isNotEqualTo(newCase().getId());
    }

    @Test
    void transitionTo_allowedPath_returnsPreviousAndUpdatesStatus() {
        FraudCaseEntity entity = newCase();

        assertThat(entity.transitionTo(CaseStatus.ENRICHING)).isEqualTo(CaseStatus.NEW);
        entity.transitionTo(CaseStatus.READY_FOR_REVIEW);
        entity.transitionTo(CaseStatus.UNDER_REVIEW);
        entity.transitionTo(CaseStatus.ROUTED);
        entity.transitionTo(CaseStatus.CLOSED);

        assertThat(entity.getStatus()).isEqualTo(CaseStatus.CLOSED);
    }

    @Test
    void transitionTo_skippingEnrichment_throwsAndKeepsStatus() {
        FraudCaseEntity entity = newCase();

        assertThatThrownBy(() -> entity.transitionTo(CaseStatus.ROUTED)).isInstanceOf(InvalidCaseTransitionException.class);
        assertThat(entity.getStatus()).isEqualTo(CaseStatus.NEW);
    }

    @Test
    void evidenceItem_toolResultWithoutTrace_cannotBeConstructed() {
        assertThatThrownBy(() -> new EvidenceItemEntity("case-1", EvidenceTool.DEVICE_SIGNALS,
                ProvenanceType.TOOL_RESULT, EvidenceStatus.AVAILABLE, "summary", null, Instant.now()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void toolTrace_complete_recordsStatusAndNonNegativeDuration() {
        Instant start = Instant.parse("2026-11-01T10:00:00Z");
        ToolTraceEntity trace = new ToolTraceEntity("case-1", EvidenceTool.MERCHANT_RISK_PROFILE, "merchant=M1",
                "corr-1", start);

        trace.complete(ToolTraceStatus.TIMEOUT, null, "TOOL_TIMEOUT", start.plusMillis(1500));

        assertThat(trace.getStatus()).isEqualTo(ToolTraceStatus.TIMEOUT);
        assertThat(trace.getDurationMs()).isEqualTo(1500L);
        assertThat(trace.getErrorCode()).isEqualTo("TOOL_TIMEOUT");
    }

    @Test
    void toolTrace_notCompleted_defaultsToErrorNotSuccess() {
        ToolTraceEntity trace = new ToolTraceEntity("case-1", EvidenceTool.CASE_SLA, null, null, Instant.now());

        assertThat(trace.getStatus()).isEqualTo(ToolTraceStatus.ERROR);
    }

    @Test
    void recommendation_fromDomain_copiesContract() {
        RecommendationEntity entity = new RecommendationEntity(TestFixtures.recommendation("case-1", List.of("ev-1")));

        assertThat(entity.isHumanReviewRequired()).isTrue();
        assertThat(entity.getEvidenceIds()).containsExactly("ev-1");
        assertThat(entity.toDomain(List.of()).traceIds()).containsExactly("rule:new-device", "trace-1");
    }

    @Test
    void auditEvent_mutationCallback_alwaysRejects() {
        AuditEventEntity event = new AuditEventEntity("case-1", AuditEventType.CASE_CREATED, ActorType.ANALYST,
                "analyst-1", null, CaseStatus.NEW, null, null);

        assertThatThrownBy(event::rejectMutation).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void auditEvent_overlongDetails_rejected() {
        String details = "d".repeat(AuditEventEntity.MAX_DETAILS_LENGTH + 1);

        assertThatThrownBy(() -> new AuditEventEntity("case-1", AuditEventType.CASE_CREATED, ActorType.SYSTEM,
                "system", null, null, details, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
