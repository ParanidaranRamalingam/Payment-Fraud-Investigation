package com.capstone.fraudcase.application;

import com.capstone.fraudcase.agent.common.CaseAssistant;
import com.capstone.fraudcase.agent.common.CaseAssistantReply;
import com.capstone.fraudcase.agent.common.CaseMessageContext;
import com.capstone.fraudcase.agent.common.CollectedEvidence;
import com.capstone.fraudcase.agent.common.FallbackRecommendations;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.TransactionClaim;
import com.capstone.fraudcase.knowledge.PolicyRetriever;
import com.capstone.fraudcase.knowledge.RetrievalOutcome;
import com.capstone.fraudcase.persistence.audit.AuditTrail;
import com.capstone.fraudcase.persistence.entity.CaseMessageEntity;
import com.capstone.fraudcase.persistence.entity.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.entity.PolicyCitationEntity;
import com.capstone.fraudcase.persistence.entity.PolicyRetrievalTraceEntity;
import com.capstone.fraudcase.persistence.entity.RecommendationEntity;
import com.capstone.fraudcase.persistence.entity.ToolTraceEntity;
import com.capstone.fraudcase.persistence.entity.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.repository.CaseMessageRepository;
import com.capstone.fraudcase.persistence.repository.EvidenceItemRepository;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import com.capstone.fraudcase.persistence.repository.PolicyCitationRepository;
import com.capstone.fraudcase.persistence.repository.PolicyRetrievalTraceRepository;
import com.capstone.fraudcase.persistence.repository.RecommendationRepository;
import com.capstone.fraudcase.persistence.repository.ToolTraceRepository;
import com.capstone.fraudcase.persistence.repository.TransactionClaimRepository;
import com.capstone.fraudcase.tools.ToolInvoker;
import com.capstone.fraudcase.tools.ToolOutcome;
import com.capstone.fraudcase.tools.ToolRequest;
import com.capstone.fraudcase.tools.model.EvidencePayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs one bounded investigation: {@code NEW|INCOMPLETE -> ENRICHING}, invoke the fixed evidence plan through
 * {@link ToolInvoker}, analyse with the configured {@link FraudCaseProcessor}, validate, persist and move to
 * {@code READY_FOR_REVIEW} or {@code INCOMPLETE}. Every step writes an append-only audit event. State transitions
 * are decided here from deterministic facts, never from processor output. The whole run is one transaction and
 * is protected by the case's optimistic-lock version.
 *
 * <p>It is also the only path to the {@link CaseAssistant}: {@link #respond} answers an analyst message from the
 * case's own persisted facts, validates the reply and records both in the case's conversation memory.
 */
@Service
public class InvestigationOrchestrator {

    static final String FALLBACK_REPLY = "The assistant could not produce a validated answer for this message. "
            + "No facts were inferred; review the case evidence and recommendation directly.";

    /** Bounded, ordered investigation plan; only allow-listed tools. */
    public static final List<EvidenceTool> EVIDENCE_PLAN = List.of(
            EvidenceTool.TRANSACTION_DETAILS, EvidenceTool.DEVICE_SIGNALS, EvidenceTool.RECENT_LOCATIONS,
            EvidenceTool.MERCHANT_RISK_PROFILE, EvidenceTool.PRIOR_DISPUTES, EvidenceTool.CASE_SLA);

    private final FraudCaseRepository cases;
    private final TransactionClaimRepository claims;
    private final ToolTraceRepository traces;
    private final EvidenceItemRepository evidence;
    private final RecommendationRepository recommendations;
    private final PolicyCitationRepository citations;
    private final ToolInvoker toolInvoker;
    private final FraudCaseProcessor processor;
    private final RecommendationValidator validator;
    private final AuditTrail audit;
    private final Clock clock;
    private final CaseAssistant assistant;
    private final CaseMessageRepository messages;
    private final PolicyRetriever policyRetriever;
    private final PolicyRetrievalTraceRepository retrievalTraces;

    public InvestigationOrchestrator(FraudCaseRepository cases, TransactionClaimRepository claims,
                                     ToolTraceRepository traces, EvidenceItemRepository evidence,
                                     RecommendationRepository recommendations, PolicyCitationRepository citations,
                                     ToolInvoker toolInvoker, FraudCaseProcessor processor,
                                     RecommendationValidator validator, AuditTrail audit, Clock clock,
                                     CaseAssistant assistant, CaseMessageRepository messages,
                                     PolicyRetriever policyRetriever,
                                     PolicyRetrievalTraceRepository retrievalTraces) {
        this.assistant = assistant;
        this.messages = messages;
        this.policyRetriever = policyRetriever;
        this.retrievalTraces = retrievalTraces;
        this.cases = cases;
        this.claims = claims;
        this.traces = traces;
        this.evidence = evidence;
        this.recommendations = recommendations;
        this.citations = citations;
        this.toolInvoker = toolInvoker;
        this.processor = processor;
        this.validator = validator;
        this.audit = audit;
        this.clock = clock;
    }

    private record Analysis(FraudCaseRecommendation recommendation, boolean fallbackUsed) {
    }

    @Transactional
    public InvestigationResult investigate(String caseId, String actorId, String correlationId) {
        FraudCaseEntity fraudCase = cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
        transition(fraudCase, CaseStatus.ENRICHING, ActorType.ANALYST, actorId, "investigation started", correlationId);

        TransactionClaim claim = claims.findByCaseId(caseId).map(TransactionClaimEntity::toDomain).orElse(null);
        List<CollectedEvidence> collected = claim == null
                ? List.of()
                : collectEvidence(fraudCase, claim, correlationId);
        RetrievalOutcome retrieval = retrievePolicies(fraudCase, claim, correlationId);
        FraudCaseContext context = new FraudCaseContext(caseId, claim, fraudCase.getCustomerStatement(), collected,
                retrieval.citations());

        Analysis analysis = analyze(context, correlationId);
        FraudCaseRecommendation recommendation = analysis.recommendation();
        if (!retrieval.grounded()) {
            recommendation = validator.validate(policyAbstention(recommendation, retrieval), context);
        }
        RecommendationEntity saved = recommendations.save(new RecommendationEntity(recommendation));
        recommendation.policyCitations().forEach(c -> citations.save(new PolicyCitationEntity(saved.getId(), caseId, c)));
        audit.system(caseId, AuditEventType.RECOMMENDATION_RECORDED, null, null,
                "classification=%s route=%s risk=%s confidence=%s mode=%s fallback=%s".formatted(
                        recommendation.classification(), recommendation.route(), recommendation.riskLevel(),
                        recommendation.confidence().toPlainString(), recommendation.processorMode(),
                        analysis.fallbackUsed()), correlationId);

        boolean complete = claim != null
                && collected.size() == EVIDENCE_PLAN.size()
                && collected.stream().allMatch(e -> e.item().isAvailable())
                && recommendation.classification() != Classification.INSUFFICIENT_EVIDENCE
                && !analysis.fallbackUsed();
        CaseStatus target = complete ? CaseStatus.READY_FOR_REVIEW : CaseStatus.INCOMPLETE;
        transition(fraudCase, target, ActorType.SYSTEM, AuditTrail.SYSTEM_ACTOR,
                complete ? "investigation complete" : "investigation incomplete: evidence gaps or no classification",
                correlationId);
        cases.save(fraudCase);

        return new InvestigationResult(caseId, fraudCase.getStatus(), saved.getId(), recommendation,
                analysis.fallbackUsed(), retrieval.grounded(),
                collected.stream().map(CollectedEvidence::item).toList());
    }

    /**
     * Answers one analyst message. The assistant sees only this case's persisted evidence and latest
     * recommendation; the message is stored as data and never alters case state. A reply that fails validation
     * is replaced by a fixed safe reply. Closed cases accept no further messages.
     */
    @Transactional
    public CaseMessageExchange respond(String caseId, String message, String actorId, String correlationId) {
        FraudCaseEntity fraudCase = cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
        if (fraudCase.getStatus().isTerminal()) {
            throw new CaseConflictException("Case is closed; no further messages can be added");
        }
        List<EvidenceItem> caseEvidence = evidence.findByCaseIdOrderByCollectedAtAsc(caseId).stream()
                .map(EvidenceItemEntity::toDomain).toList();
        FraudCaseRecommendation latest = recommendations.findFirstByCaseIdOrderByCreatedAtDesc(caseId)
                .map(r -> r.toDomain(citations.findByRecommendationIdOrderByRelevanceScoreDesc(r.getId()).stream()
                        .map(PolicyCitationEntity::toDomain).toList()))
                .orElse(null);
        CaseMessageContext context = new CaseMessageContext(caseId, fraudCase.getStatus(), caseEvidence, latest,
                message);
        CaseMessageEntity question = messages.save(CaseMessageEntity.analystMessage(caseId, actorId, message,
                clock.instant()));

        CaseAssistantReply reply;
        boolean fallbackUsed = false;
        try {
            reply = validator.validateReply(assistant.reply(context), context);
        } catch (RuntimeException e) {
            fallbackUsed = true;
            audit.system(caseId, AuditEventType.RECOMMENDATION_REJECTED, null, null,
                    "reason=reply failed validation error=" + e.getClass().getSimpleName(), correlationId);
            reply = new CaseAssistantReply(FALLBACK_REPLY, List.of(), ProcessorMode.RULES);
        }
        CaseMessageEntity answer = messages.save(CaseMessageEntity.assistantReply(question, reply.content(),
                reply.evidenceIds(), reply.processorMode(), AuditTrail.SYSTEM_ACTOR, clock.instant()));
        audit.record(caseId, AuditEventType.MESSAGE_RECEIVED, ActorType.ANALYST, actorId, null, null,
                "messageId=%s replyId=%s length=%d citedEvidence=%d mode=%s fallback=%s".formatted(question.getId(),
                        answer.getId(), message.length(), reply.evidenceIds().size(), reply.processorMode(),
                        fallbackUsed), correlationId);
        return new CaseMessageExchange(question.toDomain(), answer.toDomain(), fallbackUsed);
    }

    private List<CollectedEvidence> collectEvidence(FraudCaseEntity fraudCase, TransactionClaim claim,
                                                    String correlationId) {
        String caseId = fraudCase.getId();
        ToolRequest request = new ToolRequest(caseId, fraudCase.getCustomerReference(), fraudCase.getCardReference(),
                claim, fraudCase.getCreatedAt(), clock.instant());
        List<CollectedEvidence> collected = new ArrayList<>();
        for (EvidenceTool tool : EVIDENCE_PLAN) {
            ToolOutcome outcome = toolInvoker.invoke(tool, request);
            ToolTraceEntity trace = new ToolTraceEntity(caseId, tool, outcome.requestSummary(), correlationId,
                    outcome.startedAt());
            trace.complete(outcome.traceStatus(), outcome.summary(), outcome.errorCode(), outcome.completedAt());
            traces.save(trace);
            audit.system(caseId, AuditEventType.TOOL_INVOKED, null, null,
                    "tool=%s status=%s errorCode=%s durationMs=%d".formatted(tool.toolId(), outcome.traceStatus(),
                            outcome.errorCode() == null ? "none" : outcome.errorCode(), trace.getDurationMs()),
                    correlationId);

            EvidencePayload payload = outcome.payload();
            EvidenceItemEntity item = evidence.save(new EvidenceItemEntity(caseId, tool, ProvenanceType.TOOL_RESULT,
                    outcome.evidenceStatus(), outcome.summary(), trace.getId(), outcome.completedAt(),
                    outcome.source(), payload == null ? null : payload.contentHash(),
                    payload == null ? null : payload.canonicalForm()));
            audit.system(caseId, AuditEventType.EVIDENCE_RECORDED, null, null,
                    "tool=%s evidenceStatus=%s source=%s".formatted(tool.toolId(), outcome.evidenceStatus(),
                            outcome.source() == null ? "none" : outcome.source()), correlationId);
            collected.add(new CollectedEvidence(item.toDomain(), payload));
        }
        return collected;
    }

    /**
     * Retrieves approved, currently effective policy passages for the case and persists the retrieval trace
     * before the processor runs. An ungrounded outcome is not an error: the recommendation is corrected to a
     * safe {@code MANUAL_REVIEW} route and the missing policy evidence is recorded on it.
     */
    private RetrievalOutcome retrievePolicies(FraudCaseEntity fraudCase, TransactionClaim claim,
                                              String correlationId) {
        java.time.Instant created = fraudCase.getCreatedAt() == null ? clock.instant() : fraudCase.getCreatedAt();
        RetrievalOutcome outcome = policyRetriever.retrieve(fraudCase.getId(), claim,
                fraudCase.getCustomerStatement(), created.atOffset(ZoneOffset.UTC).toLocalDate());
        retrievalTraces.save(new PolicyRetrievalTraceEntity(fraudCase.getId(), outcome, correlationId));
        audit.system(fraudCase.getId(), AuditEventType.POLICY_RETRIEVED, null, null,
                "grounded=%s citations=%d candidates=%d abstain=%s model=%s topK=%d threshold=%s".formatted(
                        outcome.grounded(), outcome.citations().size(), outcome.candidates().size(),
                        outcome.abstainCode(), outcome.embeddingModel(), outcome.topK(),
                        outcome.similarityThreshold()), correlationId);
        return outcome;
    }

    /**
     * RAG abstention: no approved passage met the threshold, so no procedural grounding exists. The route is
     * driven to {@code MANUAL_REVIEW} and the missing policy evidence joins the recommendation's missing
     * information; classification, evidence and rationale are otherwise untouched. Re-validated by the caller.
     */
    private static FraudCaseRecommendation policyAbstention(FraudCaseRecommendation rec,
                                                          RetrievalOutcome retrieval) {
        List<String> missing = new ArrayList<>(rec.missingInformation());
        missing.add(retrieval.missingPolicyDescription());
        return new FraudCaseRecommendation(rec.caseId(), rec.classification(), rec.riskLevel(),
                Route.MANUAL_REVIEW, rec.confidence(), true, rec.evidenceIds(), rec.policyCitations(),
                List.copyOf(missing), rec.traceIds(), rec.rationale(), rec.processorMode());
    }

    private Analysis analyze(FraudCaseContext context, String correlationId) {
        try {
            return new Analysis(validator.validate(processor.analyze(context), context), false);
        } catch (RuntimeException e) {
            String reason = e instanceof DomainValidationException
                    ? "recommendation failed validation" : "processor failure";
            audit.system(context.caseId(), AuditEventType.RECOMMENDATION_REJECTED, null, null,
                    "reason=%s error=%s".formatted(reason, e.getClass().getSimpleName()), correlationId);
            return new Analysis(FallbackRecommendations.manualReview(context, reason), true);
        }
    }

    private void transition(FraudCaseEntity fraudCase, CaseStatus target, ActorType actorType, String actorId,
                            String details, String correlationId) {
        CaseStatus previous = fraudCase.transitionTo(target);
        audit.record(fraudCase.getId(), AuditEventType.STATUS_CHANGED, actorType,
                actorId == null ? AuditTrail.SYSTEM_ACTOR : actorId, previous, target, details, correlationId);
    }
}
