package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.FraudCaseRecommendation;
import com.capstone.fraudcase.domain.PolicyCitation;
import com.capstone.fraudcase.domain.ReviewDecision;
import com.capstone.fraudcase.domain.ReviewDisposition;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.ToolTrace;
import com.capstone.fraudcase.domain.TransactionClaim;
import com.capstone.fraudcase.persistence.audit.AuditTrail;
import com.capstone.fraudcase.persistence.entity.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.entity.FraudCaseEntity;
import com.capstone.fraudcase.persistence.entity.PolicyCitationEntity;
import com.capstone.fraudcase.persistence.entity.RecommendationEntity;
import com.capstone.fraudcase.persistence.entity.ReviewDispositionEntity;
import com.capstone.fraudcase.persistence.entity.ToolTraceEntity;
import com.capstone.fraudcase.persistence.entity.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.repository.EvidenceItemRepository;
import com.capstone.fraudcase.persistence.repository.FraudCaseRepository;
import com.capstone.fraudcase.persistence.repository.PolicyCitationRepository;
import com.capstone.fraudcase.persistence.repository.RecommendationRepository;
import com.capstone.fraudcase.persistence.repository.ReviewDispositionRepository;
import com.capstone.fraudcase.persistence.repository.ToolTraceRepository;
import com.capstone.fraudcase.persistence.repository.TransactionClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Application service behind the case REST API. Owns intake, case-scoped reads and the human review step;
 * investigations and assistant replies are delegated to {@link InvestigationOrchestrator}, the only component that
 * invokes tools or processors. Every state change goes through {@link CaseStateMachine}, is audited, and runs in
 * one transaction guarded by the case's optimistic-lock version, so a failure leaves no partial writes.
 *
 * <p>Review flow: {@code READY_FOR_REVIEW|INCOMPLETE -> UNDER_REVIEW -> ROUTED} (ACCEPT/OVERRIDE) or
 * {@code -> CLOSED} (CLOSE); {@code ROUTED -> CLOSED} (CLOSE). Reviews only record the human decision: no
 * financial, account, compliance or customer-contact action is ever taken.
 */
@Service
public class FraudCaseService {

    private static final Pattern CASE_ID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final FraudCaseRepository cases;
    private final TransactionClaimRepository claims;
    private final EvidenceItemRepository evidence;
    private final ToolTraceRepository traces;
    private final RecommendationRepository recommendations;
    private final PolicyCitationRepository citations;
    private final ReviewDispositionRepository reviews;
    private final InvestigationOrchestrator orchestrator;
    private final AuditTrail audit;
    private final Clock clock;

    public FraudCaseService(FraudCaseRepository cases, TransactionClaimRepository claims,
                            EvidenceItemRepository evidence, ToolTraceRepository traces,
                            RecommendationRepository recommendations, PolicyCitationRepository citations,
                            ReviewDispositionRepository reviews, InvestigationOrchestrator orchestrator,
                            AuditTrail audit, Clock clock) {
        this.cases = cases;
        this.claims = claims;
        this.evidence = evidence;
        this.traces = traces;
        this.recommendations = recommendations;
        this.citations = citations;
        this.reviews = reviews;
        this.orchestrator = orchestrator;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CaseView createCase(NewCase newCase, String analystId, String correlationId) {
        FraudCaseEntity fraudCase = cases.save(new FraudCaseEntity(newCase.customerReference(),
                newCase.cardReference(), newCase.customerStatement(), analystId));
        TransactionClaim claim = newCase.claim();
        claims.save(new TransactionClaimEntity(fraudCase.getId(), claim));
        audit.record(fraudCase.getId(), AuditEventType.CASE_CREATED, ActorType.ANALYST, analystId, null,
                CaseStatus.NEW, "claimType=%s currency=%s syntheticDataConfirmed=true humanReviewRequired=true"
                        .formatted(claim.claimType(), claim.currency().getCurrencyCode()), correlationId);
        return view(fraudCase, claim);
    }

    @Transactional(readOnly = true)
    public CaseView getCase(String caseId) {
        FraudCaseEntity fraudCase = load(caseId);
        return view(fraudCase, claims.findByCaseId(caseId).map(TransactionClaimEntity::toDomain).orElse(null));
    }

    @Transactional
    public InvestigationOutcome startInvestigation(String caseId, String analystId, String correlationId) {
        requireWellFormed(caseId);
        InvestigationResult result = orchestrator.investigate(caseId, analystId, correlationId);
        RecommendationEntity saved = recommendations.findById(result.recommendationId())
                .orElseThrow(() -> new IllegalStateException("recorded recommendation not found"));
        return new InvestigationOutcome(caseId, result.status(), result.fallbackUsed(),
                new RecordedRecommendation(saved.getId(), result.recommendation(), saved.getCreatedAt()),
                result.evidence());
    }

    @Transactional(readOnly = true)
    public List<EvidenceItem> evidence(String caseId) {
        requireCase(caseId);
        return evidence.findByCaseIdOrderByCollectedAtAsc(caseId).stream().map(EvidenceItemEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<ToolTrace> toolTraces(String caseId) {
        requireCase(caseId);
        return traces.findByCaseIdOrderByStartedAtAsc(caseId).stream().map(ToolTraceEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public RecordedRecommendation latestRecommendation(String caseId) {
        requireCase(caseId);
        return latest(caseId).orElseThrow(() -> new RecommendationNotFoundException(caseId));
    }

    /** Citations supporting the latest recommendation; empty when none was grounded or none exists yet. */
    @Transactional(readOnly = true)
    public List<PolicyCitation> citations(String caseId) {
        requireCase(caseId);
        return latest(caseId).map(r -> r.recommendation().policyCitations()).orElse(List.of());
    }

    @Transactional
    public CaseMessageExchange postMessage(String caseId, String message, String analystId, String correlationId) {
        requireWellFormed(caseId);
        return orchestrator.respond(caseId, message, analystId, correlationId);
    }

    @Transactional
    public RecordedReview submitReview(String caseId, ReviewCommand command, String reviewerId, String correlationId) {
        FraudCaseEntity fraudCase = load(caseId);
        CaseStatus original = fraudCase.getStatus();
        ReviewDecision decision = command.decision();
        CaseStatus target = decision == ReviewDecision.CLOSE ? CaseStatus.CLOSED : CaseStatus.ROUTED;
        boolean viaUnderReview = CaseStateMachine.canTransition(original, CaseStatus.UNDER_REVIEW);
        CaseStateMachine.requireTransition(viaUnderReview ? CaseStatus.UNDER_REVIEW : original, target);

        RecordedRecommendation reviewed = command.recommendationId() == null ? null
                : requireLatest(caseId, command.recommendationId());
        if (reviewed == null && decision != ReviewDecision.CLOSE) {
            throw new DomainValidationException(decision + " requires the reviewed recommendationId");
        }
        Classification finalClassification = command.finalClassification();
        Route finalRoute = command.finalRoute();
        if (decision == ReviewDecision.ACCEPT) {
            FraudCaseRecommendation rec = reviewed.recommendation();
            if ((finalClassification != null && finalClassification != rec.classification())
                    || (finalRoute != null && finalRoute != rec.route())) {
                throw new DomainValidationException(
                        "ACCEPT keeps the recommended classification and route; use OVERRIDE to change them");
            }
            finalClassification = rec.classification();
            finalRoute = rec.route();
        }
        ReviewDisposition disposition = new ReviewDisposition(caseId, command.recommendationId(), reviewerId,
                decision, finalClassification, finalRoute, command.rationale().strip(), clock.instant());

        if (viaUnderReview) {
            transition(fraudCase, CaseStatus.UNDER_REVIEW, reviewerId, "review started", correlationId);
        }
        transition(fraudCase, target, reviewerId, "review decision " + decision, correlationId);
        ReviewDispositionEntity saved = reviews.save(new ReviewDispositionEntity(disposition));
        cases.save(fraudCase);
        audit.record(caseId, AuditEventType.REVIEW_SUBMITTED, ActorType.ANALYST, reviewerId, original, target,
                ("reviewId=%s decision=%s recommendationId=%s recommended=%s/%s final=%s/%s rationale=%s").formatted(
                        saved.getId(), decision, command.recommendationId(),
                        reviewed == null ? null : reviewed.recommendation().classification(),
                        reviewed == null ? null : reviewed.recommendation().route(),
                        finalClassification, finalRoute, disposition.rationale()), correlationId);
        return new RecordedReview(saved.getId(), saved.toDomain(), fraudCase.getStatus());
    }

    private RecordedRecommendation requireLatest(String caseId, String recommendationId) {
        Optional<RecordedRecommendation> latest = latest(caseId);
        if (latest.isPresent() && latest.get().recommendationId().equals(recommendationId)) {
            return latest.get();
        }
        if (recommendations.findByIdAndCaseId(recommendationId, caseId).isPresent()) {
            throw new CaseConflictException("The reviewed recommendation is no longer the latest for this case; "
                    + "reload and review the current recommendation");
        }
        throw new DomainValidationException("recommendationId does not identify a recommendation of this case");
    }

    private Optional<RecordedRecommendation> latest(String caseId) {
        return recommendations.findFirstByCaseIdOrderByCreatedAtDesc(caseId).map(r -> new RecordedRecommendation(
                r.getId(), r.toDomain(citations.findByRecommendationIdOrderByRelevanceScoreDesc(r.getId()).stream()
                .map(PolicyCitationEntity::toDomain).toList()), r.getCreatedAt()));
    }

    private void transition(FraudCaseEntity fraudCase, CaseStatus target, String reviewerId, String details,
                            String correlationId) {
        CaseStatus previous = fraudCase.transitionTo(target);
        audit.record(fraudCase.getId(), AuditEventType.STATUS_CHANGED, ActorType.ANALYST, reviewerId, previous,
                target, details, correlationId);
    }

    private FraudCaseEntity load(String caseId) {
        requireWellFormed(caseId);
        return cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    private void requireCase(String caseId) {
        requireWellFormed(caseId);
        if (!cases.existsById(caseId)) {
            throw new CaseNotFoundException(caseId);
        }
    }

    private static void requireWellFormed(String caseId) {
        if (caseId == null || !CASE_ID.matcher(caseId).matches()) {
            throw new CaseNotFoundException(String.valueOf(caseId));
        }
    }

    private static CaseView view(FraudCaseEntity c, TransactionClaim claim) {
        return new CaseView(c.getId(), c.getStatus(), c.getCustomerReference(), c.getCardReference(), claim,
                c.getCustomerStatement(), c.getCreatedBy(), c.isHumanReviewRequired(), c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
