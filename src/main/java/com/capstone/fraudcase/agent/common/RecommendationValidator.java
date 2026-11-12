package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.InvalidRecommendationException;
import com.capstone.fraudcase.domain.SensitiveDataGuard;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Application-boundary check applied to every processor output before persistence/display. The recommendation
 * record already enforces its own schema; this adds the context-dependent rules: it must be about this case and
 * may only reference this case's evidence, traces and supplied policy passages.
 */
@Component
public class RecommendationValidator {

    public static final String RULE_TRACE_PREFIX = "rule:";
    public static final String SIGNAL_TRACE_PREFIX = "signal:";
    public static final int MAX_MISSING_INFO_LENGTH = 500;
    public static final int MAX_TRACE_REF_LENGTH = 64;

    public FraudCaseRecommendation validate(FraudCaseRecommendation recommendation, FraudCaseContext context) {
        if (recommendation == null) {
            throw new InvalidRecommendationException("recommendation is required");
        }
        if (!context.caseId().equals(recommendation.caseId())) {
            throw new InvalidRecommendationException("recommendation is for a different case");
        }
        List<String> knownEvidence = context.evidenceIds();
        if (!knownEvidence.containsAll(recommendation.evidenceIds())) {
            throw new InvalidRecommendationException("recommendation cites evidence outside this case");
        }
        if (recommendation.evidenceIds().stream().distinct().count() != recommendation.evidenceIds().size()) {
            throw new InvalidRecommendationException("recommendation cites the same evidence more than once");
        }
        Set<String> knownTraces = context.toolTraceIds();
        for (String trace : recommendation.traceIds()) {
            boolean ruleRef = trace.startsWith(RULE_TRACE_PREFIX) || trace.startsWith(SIGNAL_TRACE_PREFIX);
            if (trace.length() > MAX_TRACE_REF_LENGTH || (!ruleRef && !knownTraces.contains(trace))) {
                throw new InvalidRecommendationException("recommendation references an unknown trace");
            }
        }
        if (!context.policyCitations().containsAll(recommendation.policyCitations())) {
            throw new InvalidRecommendationException("recommendation cites a policy passage that was not supplied");
        }
        if (!SensitiveDataGuard.isSafeFreeText(recommendation.rationale())) {
            throw new InvalidRecommendationException("rationale contains unsafe content");
        }
        for (String missing : recommendation.missingInformation()) {
            if (missing.length() > MAX_MISSING_INFO_LENGTH || !SensitiveDataGuard.isSafeFreeText(missing)) {
                throw new InvalidRecommendationException("missing-information entry is too long or unsafe");
            }
        }
        return recommendation;
    }
}
