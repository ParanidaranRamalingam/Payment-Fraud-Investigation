package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.agent.common.CollectedEvidence;
import com.capstone.fraudcase.agent.common.FallbackRecommendations;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.InvalidRecommendationException;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.SensitiveDataGuard;
import com.capstone.fraudcase.domain.TransactionClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Spring AI adapter behind the framework-neutral {@link FraudCaseProcessor}. Active only when
 * {@code fraud.ai.enabled=true}; otherwise the rules processor remains the sole processor.
 *
 * <p>The model only ever sees validated case facts, approved evidence-tool output (inline and via read-only
 * {@link CaseEvidenceTools}), retrieved policy passages and the case-scoped customer statement — all treated
 * as untrusted data. It returns a structured {@link ModelAssessment} that is mapped onto the domain
 * recommendation with model-uncontrolled fields forced ({@code humanReviewRequired=true}, case id, processor
 * mode) and every cited identifier intersected with what was actually supplied. The result is checked by
 * {@link RecommendationValidator} before it leaves the adapter.
 *
 * <p>The model never transitions state, performs financial/account/customer actions or fabricates evidence or
 * policy. Any failure — no configured model, provider error, timeout, unparseable or invalid output — degrades
 * to the shared manual-review fallback; this adapter never throws for a model problem.
 */
@Component
@Primary
@ConditionalOnProperty(name = "fraud.ai.enabled", havingValue = "true")
public class SpringAiFraudCaseAgent implements FraudCaseProcessor {

    private static final Logger log = LoggerFactory.getLogger(SpringAiFraudCaseAgent.class);

    private final ObjectProvider<ChatModel> chatModel;
    private final RecommendationValidator validator;
    private final FraudAiProperties properties;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SpringAiFraudCaseAgent(ObjectProvider<ChatModel> chatModel, RecommendationValidator validator,
                                  FraudAiProperties properties,
                                  @Value("classpath:prompts/fraud-system.st") Resource systemPrompt,
                                  @Value("classpath:prompts/fraud-user.st") Resource userPromptTemplate)
            throws IOException {
        this.chatModel = chatModel;
        this.validator = validator;
        this.properties = properties;
        this.systemPrompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.userPromptTemplate = userPromptTemplate.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        Objects.requireNonNull(context, "context");
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return FallbackRecommendations.manualReview(context, "model unavailable: no ChatModel configured");
        }
        ModelAssessment assessment;
        try {
            assessment = callModel(model, context);
        } catch (RuntimeException e) {
            log.warn("AI analysis failed for case {} ({}); using manual-review fallback",
                    context.caseId(), e.getClass().getSimpleName(), e);
            return FallbackRecommendations.manualReview(context, "model execution failed or timed out");
        }
        if (assessment == null) {
            return FallbackRecommendations.manualReview(context, "model returned no assessment");
        }
        try {
            return validator.validate(toRecommendation(assessment, context), context);
        } catch (InvalidRecommendationException | IllegalArgumentException e) {
            log.warn("AI assessment rejected for case {} ({}); using manual-review fallback",
                    context.caseId(), e.getMessage());
            return FallbackRecommendations.manualReview(context, "model output failed validation");
        }
    }

    private ModelAssessment callModel(ChatModel model, FraudCaseContext context) {
        Future<ModelAssessment> future = executor.submit(() -> ChatClient.builder(model).build()
                .prompt()
                .system(systemPrompt)
                .user(user -> user.text(userPromptTemplate).params(promptParams(context)))
                .tools(new CaseEvidenceTools(context))
                .call()
                .entity(ModelAssessment.class));
        try {
            return future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("model call timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new IllegalStateException("model call interrupted", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException("model call failed", e);
        }
    }

    private static Map<String, Object> promptParams(FraudCaseContext context) {
        return Map.of(
                "caseId", context.caseId(),
                "claim", describeClaim(context.claim()),
                "customerStatement", context.customerStatement() == null ? "(none)" : context.customerStatement(),
                "evidence", describeEvidence(context),
                "policyPassages", describePolicies(context),
                "evidenceIds", String.join(", ", context.evidenceIds()),
                "toolTraceIds", String.join(", ", context.toolTraceIds()));
    }

    private static String describeClaim(TransactionClaim claim) {
        if (claim == null) {
            return "No transaction claim is on file.";
        }
        return "claimType=%s, merchant=%s, mcc=%s, amount=%s %s, transactionAt=%s, transactionReference=%s"
                .formatted(claim.claimType(), claim.merchantName(), claim.merchantCategoryCode(),
                        claim.amount().toPlainString(), claim.currency().getCurrencyCode(),
                        claim.transactionAt(), claim.transactionReference());
    }

    private static String describeEvidence(FraudCaseContext context) {
        if (context.evidence().isEmpty()) {
            return "No evidence was collected.";
        }
        return context.evidence().stream()
                .map(SpringAiFraudCaseAgent::describeEvidence)
                .collect(Collectors.joining("\n"));
    }

    private static String describeEvidence(CollectedEvidence collected) {
        var item = collected.item();
        return "- evidenceId=%s tool=%s status=%s toolTraceId=%s | %s".formatted(
                item.evidenceId(), item.tool() == null ? item.provenance().name() : item.tool().toolId(),
                item.status(), item.toolTraceId(), item.summary());
    }

    private static String describePolicies(FraudCaseContext context) {
        if (context.policyCitations().isEmpty()) {
            return "No approved policy passages were retrieved for this case.";
        }
        return context.policyCitations().stream()
                .map(c -> "- chunkId=%s document=%s version=%s effectiveDate=%s section=%s relevance=%.4f\n  passage: %s"
                        .formatted(c.chunkId(), c.documentTitle(), c.documentVersion(), c.effectiveDate(),
                                c.section(), c.relevanceScore(), c.excerpt()))
                .collect(Collectors.joining("\n"));
    }

    /** Maps the structured assessment onto the domain recommendation, discarding anything not supplied. */
    static FraudCaseRecommendation toRecommendation(ModelAssessment assessment, FraudCaseContext context) {
        if (assessment.classification() == null || assessment.riskLevel() == null || assessment.route() == null
                || assessment.confidence() == null) {
            throw new InvalidRecommendationException("model assessment is missing required fields");
        }
        BigDecimal confidence = assessment.confidence().max(BigDecimal.ZERO).min(BigDecimal.ONE);

        List<String> knownEvidence = context.evidenceIds();
        Set<String> evidenceIds = new LinkedHashSet<>(knownEvidence);
        evidenceIds.retainAll(assessment.evidenceIds() == null ? List.of() : assessment.evidenceIds());
        if (evidenceIds.isEmpty()) {
            evidenceIds.addAll(knownEvidence);
        }

        List<PolicyCitation> citations = citedPolicies(assessment.citedChunkIds(), context);

        List<String> missing = new ArrayList<>();
        if (assessment.missingInformation() != null) {
            assessment.missingInformation().stream()
                    .filter(m -> m != null && !m.isBlank() && m.length() <= RecommendationValidator.MAX_MISSING_INFO_LENGTH
                            && SensitiveDataGuard.isSafeFreeText(m))
                    .forEach(missing::add);
        }
        if (assessment.classification() == Classification.INSUFFICIENT_EVIDENCE && missing.isEmpty()) {
            missing.addAll(context.evidenceGaps());
            if (missing.isEmpty()) {
                missing.add("further information needed to classify this case");
            }
        }

        Set<String> knownTraces = context.toolTraceIds();
        List<String> traceIds = new ArrayList<>();
        if (assessment.traceIds() != null) {
            assessment.traceIds().stream().filter(knownTraces::contains).forEach(traceIds::add);
        }
        if (traceIds.isEmpty()) {
            traceIds.addAll(knownTraces);
        }

        String rationale = assessment.rationale();
        if (rationale == null || rationale.isBlank()) {
            throw new InvalidRecommendationException("model assessment has no rationale");
        }
        if (rationale.length() > FraudCaseRecommendation.MAX_RATIONALE_LENGTH) {
            rationale = rationale.substring(0, FraudCaseRecommendation.MAX_RATIONALE_LENGTH);
        }

        Route route = assessment.classification() == Classification.INSUFFICIENT_EVIDENCE
                ? Route.MANUAL_REVIEW : assessment.route();
        return new FraudCaseRecommendation(context.caseId(), assessment.classification(), assessment.riskLevel(),
                route, confidence, true, List.copyOf(evidenceIds), citations, List.copyOf(missing), traceIds,
                rationale, ProcessorMode.AI);
    }

    private static List<PolicyCitation> citedPolicies(List<String> citedChunkIds, FraudCaseContext context) {
        if (citedChunkIds == null || citedChunkIds.isEmpty()) {
            return List.of();
        }
        Set<String> cited = new LinkedHashSet<>(citedChunkIds);
        return context.policyCitations().stream().filter(c -> cited.contains(c.chunkId())).toList();
    }
}
