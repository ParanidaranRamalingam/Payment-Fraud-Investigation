package com.capstone.fraudcase.tools.sla;

import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.ToolException;
import com.capstone.fraudcase.tools.ToolFailure;
import com.capstone.fraudcase.tools.ToolRequest;
import com.capstone.fraudcase.tools.model.CaseSla;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseSlaCalculatorTest {

    private final CaseSlaCalculator calculator = new CaseSlaCalculator();

    @ParameterizedTest
    @CsvSource({
            "UNRECOGNISED_TRANSACTION, 1000.00, P1, 24",
            "UNRECOGNISED_TRANSACTION, 999.99, P2, 48",
            "DUPLICATE_CHARGE, 5000.00, P2, 72",
            "MERCHANT_ISSUE, 20.00, P3, 120",
            "OTHER, 20.00, P3, 120"})
    void fetch_claimTypeAndAmount_determinePriorityAndSla(ClaimType type, BigDecimal amount, CaseSla.Priority priority,
                                                          int hours) {
        CaseSla sla = calculator.fetch(SyntheticCases.request("txn_sla_01", type, amount));

        assertThat(sla.priority()).isEqualTo(priority);
        assertThat(sla.slaHours()).isEqualTo(hours);
        assertThat(sla.dueAt()).isEqualTo(SyntheticCases.CASE_CREATED_AT.plus(Duration.ofHours(hours)));
    }

    @Test
    void fetch_beforeDeadline_reportsRemainingHours() {
        CaseSla sla = calculator.fetch(SyntheticCases.request("txn_sla_01", ClaimType.UNRECOGNISED_TRANSACTION));

        assertThat(sla.breached()).isFalse();
        assertThat(sla.hoursRemaining()).isEqualTo(46);
        assertThat(sla.summary()).contains("46 h remaining");
    }

    @Test
    void fetch_afterDeadline_reportsBreach() {
        ToolRequest base = SyntheticCases.request("txn_sla_01", ClaimType.UNRECOGNISED_TRANSACTION);
        Instant late = SyntheticCases.CASE_CREATED_AT.plus(Duration.ofHours(50));

        CaseSla sla = calculator.fetch(new ToolRequest(base.caseId(), base.customerReference(), base.cardReference(),
                base.claim(), base.caseCreatedAt(), late));

        assertThat(sla.breached()).isTrue();
        assertThat(sla.hoursRemaining()).isEqualTo(-2);
        assertThat(sla.summary()).contains("SLA breached by 2 h");
    }

    @Test
    void fetch_sameInputs_isDeterministic() {
        ToolRequest request = SyntheticCases.request("txn_sla_01", ClaimType.MERCHANT_ISSUE);

        assertThat(calculator.fetch(request).contentHash()).isEqualTo(calculator.fetch(request).contentHash());
    }

    @Test
    void fetch_unknownCaseCreationTime_isUnavailable() {
        ToolRequest base = SyntheticCases.request("txn_sla_01", ClaimType.OTHER);
        ToolRequest noCreated = new ToolRequest(base.caseId(), base.customerReference(), base.cardReference(),
                base.claim(), null, base.asOf());

        assertThatThrownBy(() -> calculator.fetch(noCreated)).isInstanceOf(ToolException.class)
                .extracting(e -> ((ToolException) e).failure()).isEqualTo(ToolFailure.UNAVAILABLE);
    }

    @Test
    void caseSla_inconsistentBreachFlag_rejected() {
        Instant now = SyntheticCases.AS_OF;

        assertThatThrownBy(() -> new CaseSla(CaseSla.Priority.P1, 24, now, now.plusSeconds(3600), now, 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
