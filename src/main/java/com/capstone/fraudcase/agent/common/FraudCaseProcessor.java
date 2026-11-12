package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.FraudCaseRecommendation;

/**
 * Framework-neutral analysis contract, implemented by the deterministic rules processor and (later) an AI
 * adapter. Implementations only interpret the case-scoped evidence they are given: they never invoke tools,
 * change case state or take any financial, account, compliance or customer-contact action. The returned
 * recommendation is advisory and is validated by the application before it is persisted or displayed.
 */
public interface FraudCaseProcessor {

    FraudCaseRecommendation analyze(FraudCaseContext context);
}
