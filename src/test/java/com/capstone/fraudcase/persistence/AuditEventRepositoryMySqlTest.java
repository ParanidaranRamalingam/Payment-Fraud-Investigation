package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.persistence.entity.AuditEventEntity;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.repository.AuditEventRepository;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlRepositoryTest
class AuditEventRepositoryMySqlTest {

    @Autowired
    private AuditEventRepository audit;
    @Autowired
    private FraudCaseRepository cases;
    @Autowired
    private EntityManager em;

    private String persistCase() {
        return cases.saveAndFlush(new FraudCaseEntity("cus_A1b2C3d4", null, "statement", "analyst-audit")).getId();
    }

    private AuditEventEntity persistEvent(String caseId) {
        AuditEventEntity saved = audit.save(new AuditEventEntity(caseId, AuditEventType.STATUS_CHANGED,
                ActorType.SYSTEM, "system", CaseStatus.NEW, CaseStatus.ENRICHING, "enrichment started", "corr-9"));
        em.flush();
        em.clear();
        return saved;
    }

    @Test
    void save_thenReadByCase_returnsEventsInOrder() {
        String caseId = persistCase();
        AuditEventEntity first = persistEvent(caseId);
        AuditEventEntity second = persistEvent(caseId);

        assertThat(audit.findByCaseIdOrderByOccurredAtAsc(caseId)).extracting(AuditEventEntity::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(audit.findById(first.getId())).get().satisfies(e -> {
            assertThat(e.getFromStatus()).isEqualTo(CaseStatus.NEW);
            assertThat(e.getToStatus()).isEqualTo(CaseStatus.ENRICHING);
            assertThat(e.getActorType()).isEqualTo(ActorType.SYSTEM);
        });
    }

    @Test
    void remove_viaEntityManager_rejected() {
        AuditEventEntity saved = persistEvent(persistCase());
        AuditEventEntity managed = em.find(AuditEventEntity.class, saved.getId());

        assertThatThrownBy(() -> {
            em.remove(managed);
            em.flush();
        }).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("append-only");
    }

    @Test
    void bulkUpdateQuery_onImmutableAudit_rejected() {
        AuditEventEntity saved = persistEvent(persistCase());

        assertThatThrownBy(() -> em.createQuery("UPDATE AuditEventEntity a SET a.details = 'tampered' WHERE a.id = :id")
                .setParameter("id", saved.getId())
                .executeUpdate()).isInstanceOf(Exception.class);
        em.clear();
        assertThat(audit.findById(saved.getId())).get().extracting(AuditEventEntity::getDetails)
                .isEqualTo("enrichment started");
    }

    @Test
    void repository_exposesNoUpdateOrDeleteMethods() {
        assertThat(Arrays.stream(AuditEventRepository.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("update") || name.startsWith("remove")
                        || name.equals("saveAll") || name.equals("saveAndFlush"));
    }
}
