package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlRepositoryTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OptimisticLockingMySqlTest {

    @Autowired
    private FraudCaseRepository cases;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentTransitions_staleWriter_rejectedByVersion() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String caseId = tx.execute(s -> cases.save(
                new FraudCaseEntity("cus_A1b2C3d4", null, "statement", "analyst-locking")).getId());

        FraudCaseEntity first = tx.execute(s -> cases.findById(caseId).orElseThrow());
        FraudCaseEntity stale = tx.execute(s -> cases.findById(caseId).orElseThrow());

        first.transitionTo(CaseStatus.ENRICHING);
        tx.executeWithoutResult(s -> cases.save(first));
        stale.transitionTo(CaseStatus.ENRICHING);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> cases.save(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        Long persistedVersion = tx.execute(s -> cases.findById(caseId).orElseThrow().getVersion());
        assertThat(persistedVersion).isEqualTo(1L);
    }
}
