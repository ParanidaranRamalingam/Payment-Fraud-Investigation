package com.capstone.fraudcase.tools.synthetic;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.tools.EvidenceToolProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the synthetic evidence tools backed by {@link SyntheticFixtureGenerator}. */
@Configuration(proxyBeanMethods = false)
public class SyntheticToolConfiguration {

    @Bean
    EvidenceToolProvider getTransactionDetails(SyntheticFixtureGenerator fixtures) {
        return new SyntheticEvidenceTool(EvidenceTool.TRANSACTION_DETAILS, fixtures.source(), fixtures::transactionDetails);
    }

    @Bean
    EvidenceToolProvider getDeviceSignals(SyntheticFixtureGenerator fixtures) {
        return new SyntheticEvidenceTool(EvidenceTool.DEVICE_SIGNALS, fixtures.source(), fixtures::deviceSignals);
    }

    @Bean
    EvidenceToolProvider compareRecentLocations(SyntheticFixtureGenerator fixtures) {
        return new SyntheticEvidenceTool(EvidenceTool.RECENT_LOCATIONS, fixtures.source(), fixtures::recentLocations);
    }

    @Bean
    EvidenceToolProvider getMerchantRiskProfile(SyntheticFixtureGenerator fixtures) {
        return new SyntheticEvidenceTool(EvidenceTool.MERCHANT_RISK_PROFILE, fixtures.source(), fixtures::merchantRiskProfile);
    }

    @Bean
    EvidenceToolProvider getPriorDisputes(SyntheticFixtureGenerator fixtures) {
        return new SyntheticEvidenceTool(EvidenceTool.PRIOR_DISPUTES, fixtures.source(), fixtures::priorDisputes);
    }
}
