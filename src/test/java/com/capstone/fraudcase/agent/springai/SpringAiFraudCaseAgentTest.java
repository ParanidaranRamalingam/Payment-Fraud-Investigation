package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.support.SyntheticCases;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiFraudCaseAgentTest {

    private static final ClassPathResource SYSTEM_PROMPT = new ClassPathResource("prompts/fraud-system.st");
    private static final ClassPathResource USER_PROMPT = new ClassPathResource("prompts/fraud-user.st");

    private final RecommendationValidator validator = new RecommendationValidator();

    private static ChatModel modelStub() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        return model;
    }

    private static ChatModel modelReturning(String text) {
        ChatModel model = modelStub();
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return model;
    }

    private static ChatModel modelThrowing(RuntimeException e) {
        ChatModel model = modelStub();
        when(model.call(any(Prompt.class))).thenThrow(e);
        return model;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ChatModel> provider(ChatModel model) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return provider;
    }

    private SpringAiFraudCaseAgent agent(ObjectProvider<ChatModel> provider, Duration timeout) {
        try {
            return new SpringAiFraudCaseAgent(provider, validator, new FraudAiProperties(true, timeout),
                    SYSTEM_PROMPT, USER_PROMPT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String json(FraudCaseContext context, Function<JsonBuilder, JsonBuilder> customise) {
        return customise.apply(new JsonBuilder(context)).build();
    }

    private static FraudCaseContext context() {
        return SyntheticCases.context("txn_fraud_9001", ClaimType.UNRECOGNISED_TRANSACTION);
    }

    @Test
    void validAssessmentProducesValidatedAiRecommendation() {
        FraudCaseContext context = context();
        String firstEvidence = context.evidenceIds().getFirst();
        String firstTrace = context.toolTraceIds().iterator().next();
        SpringAiFraudCaseAgent agent = agent(provider(modelReturning(json(context, j -> j
                .evidenceIds(List.of(firstEvidence))
                .traceIds(List.of(firstTrace))))), Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.processorMode()).isEqualTo(ProcessorMode.AI);
        assertThat(rec.humanReviewRequired()).isTrue();
        assertThat(rec.caseId()).isEqualTo(context.caseId());
        assertThat(rec.classification()).isEqualTo(Classification.UNAUTHORISED_TRANSACTION);
        assertThat(rec.route()).isEqualTo(Route.FRAUD_INVESTIGATION);
        assertThat(rec.evidenceIds()).containsExactly(firstEvidence);
        assertThat(rec.traceIds()).containsExactly(firstTrace);
        // passes the same application-boundary validation as every processor output
        assertThat(validator.validate(rec, context)).isSameAs(rec);
    }

    @Test
    void fabricatedEvidenceTraceAndCitationIdsAreDiscarded() {
        FraudCaseContext context = context();
        String json = json(context, j -> j
                .evidenceIds(List.of("ev-fabricated", context.evidenceIds().getFirst()))
                .traceIds(List.of("trace-fabricated"))
                .citedChunkIds(List.of("chunk-fabricated")));
        SpringAiFraudCaseAgent agent = agent(provider(modelReturning(json)), Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.processorMode()).isEqualTo(ProcessorMode.AI);
        assertThat(rec.evidenceIds()).containsExactly(context.evidenceIds().getFirst());
        assertThat(rec.traceIds()).isNotEmpty().allSatisfy(t -> assertThat(context.toolTraceIds()).contains(t));
        assertThat(rec.policyCitations()).isEmpty();
    }

    @Test
    void noConfiguredModelFallsBackToManualReview() {
        FraudCaseContext context = context();
        SpringAiFraudCaseAgent agent = agent(provider(null), Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(rec.route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(rec.humanReviewRequired()).isTrue();
        assertThat(rec.missingInformation()).anyMatch(m -> m.contains("model unavailable"));
        assertThat(validator.validate(rec, context)).isSameAs(rec);
    }

    @Test
    void providerErrorFallsBackToManualReview() {
        FraudCaseContext context = context();
        SpringAiFraudCaseAgent agent = agent(provider(modelThrowing(new RuntimeException("upstream down"))),
                Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(rec.route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(rec.humanReviewRequired()).isTrue();
    }

    @Test
    void malformedResponseFallsBackToManualReview() {
        FraudCaseContext context = context();
        SpringAiFraudCaseAgent agent = agent(provider(modelReturning("this is not json at all")),
                Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(rec.route()).isEqualTo(Route.MANUAL_REVIEW);
    }

    @Test
    void missingRequiredFieldsFallBackToManualReview() {
        FraudCaseContext context = context();
        SpringAiFraudCaseAgent agent = agent(provider(modelReturning("{\"rationale\":\"no fields\"}")),
                Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(rec.route()).isEqualTo(Route.MANUAL_REVIEW);
    }

    @Test
    void unsafeRationaleIsRejectedAndFallsBack() {
        FraudCaseContext context = context();
        String json = json(context, j -> j.rationale("call 4242424242424242 to verify"));
        SpringAiFraudCaseAgent agent = agent(provider(modelReturning(json)), Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.route()).isEqualTo(Route.MANUAL_REVIEW);
    }

    @Test
    void slowModelTimesOutAndFallsBack() {
        FraudCaseContext context = context();
        ChatModel slow = modelStub();
        when(slow.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(30_000);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))));
        });
        SpringAiFraudCaseAgent agent = agent(provider(slow), Duration.ofMillis(200));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(rec.route()).isEqualTo(Route.MANUAL_REVIEW);
    }

    @Test
    void insufficientEvidenceForcesManualReviewRouteAndMissingInformation() {
        FraudCaseContext context = context();
        String json = json(context, j -> j
                .classification(Classification.INSUFFICIENT_EVIDENCE)
                .route(Route.FRAUD_INVESTIGATION)
                .missingInformation(List.of()));
        SpringAiFraudCaseAgent agent = agent(provider(modelReturning(json)), Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(rec.route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(rec.missingInformation()).isNotEmpty();
        assertThat(rec.processorMode()).isEqualTo(ProcessorMode.AI);
    }

    @Test
    void confidenceIsClampedIntoRange() {
        FraudCaseContext context = context();
        String json = json(context, j -> j.confidence(new BigDecimal("1.7")));
        SpringAiFraudCaseAgent agent = agent(provider(modelReturning(json)), Duration.ofSeconds(10));

        FraudCaseRecommendation rec = agent.analyze(context);

        assertThat(rec.confidence()).isLessThanOrEqualTo(BigDecimal.ONE);
        assertThat(rec.processorMode()).isEqualTo(ProcessorMode.AI);
    }

    /** Minimal JSON builder so each test only overrides what it exercises. */
    private static final class JsonBuilder {
        private final FraudCaseContext context;
        private Classification classification = Classification.UNAUTHORISED_TRANSACTION;
        private String riskLevel = "HIGH";
        private Route route = Route.FRAUD_INVESTIGATION;
        private BigDecimal confidence = new BigDecimal("0.80");
        private List<String> evidenceIds;
        private List<String> citedChunkIds = List.of();
        private List<String> missingInformation = List.of();
        private List<String> traceIds;
        private String rationale = "Multiple independent fraud indicators corroborate an unrecognised transaction.";

        JsonBuilder(FraudCaseContext context) {
            this.context = context;
            this.evidenceIds = context.evidenceIds();
            this.traceIds = List.copyOf(context.toolTraceIds());
        }

        JsonBuilder classification(Classification v) { classification = v; return this; }
        JsonBuilder route(Route v) { route = v; return this; }
        JsonBuilder confidence(BigDecimal v) { confidence = v; return this; }
        JsonBuilder evidenceIds(List<String> v) { evidenceIds = v; return this; }
        JsonBuilder citedChunkIds(List<String> v) { citedChunkIds = v; return this; }
        JsonBuilder missingInformation(List<String> v) { missingInformation = v; return this; }
        JsonBuilder traceIds(List<String> v) { traceIds = v; return this; }
        JsonBuilder rationale(String v) { rationale = v; return this; }

        String build() {
            return """
                    {"classification":"%s","riskLevel":"%s","route":"%s","confidence":%s,
                     "evidenceIds":%s,"citedChunkIds":%s,"missingInformation":%s,"traceIds":%s,
                     "rationale":%s}""".formatted(classification, riskLevel, route, confidence.toPlainString(),
                    array(evidenceIds), array(citedChunkIds), array(missingInformation), array(traceIds),
                    quoted(rationale));
        }

        private static String array(List<String> values) {
            if (values == null) {
                return "null";
            }
            return values.stream().map(JsonBuilder::quoted).toList().toString();
        }

        private static String quoted(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
