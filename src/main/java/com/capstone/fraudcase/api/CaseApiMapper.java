package com.capstone.fraudcase.api;

import com.capstone.fraudcase.api.dto.CaseMessageExchangeResponse;
import com.capstone.fraudcase.api.dto.CaseMessageResponse;
import com.capstone.fraudcase.api.dto.CaseResponse;
import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.api.dto.EvidenceItemResponse;
import com.capstone.fraudcase.api.dto.InvestigationResponse;
import com.capstone.fraudcase.api.dto.RecommendationResponse;
import com.capstone.fraudcase.api.dto.ReviewRequest;
import com.capstone.fraudcase.api.dto.ReviewResponse;
import com.capstone.fraudcase.api.dto.TransactionClaimRequest;
import com.capstone.fraudcase.api.dto.TransactionClaimResponse;
import com.capstone.fraudcase.application.CaseMessageExchange;
import com.capstone.fraudcase.application.CaseView;
import com.capstone.fraudcase.application.InvestigationOutcome;
import com.capstone.fraudcase.application.NewCase;
import com.capstone.fraudcase.application.RecordedRecommendation;
import com.capstone.fraudcase.application.RecordedReview;
import com.capstone.fraudcase.application.ReviewCommand;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.ReviewDisposition;
import com.capstone.fraudcase.domain.TransactionClaim;

import java.util.Currency;

/** Pure translation between API DTOs and application models; no business logic. */
final class CaseApiMapper {

    private CaseApiMapper() {
    }

    static NewCase toNewCase(CreateCaseRequest request) {
        TransactionClaimRequest t = request.transaction();
        return new NewCase(request.customerReference(), request.cardReference(),
                new TransactionClaim(t.transactionReference(), t.merchantName(), t.merchantCategoryCode(), t.amount(),
                        Currency.getInstance(t.currency()), t.transactionAt(), t.claimType()),
                request.customerStatement());
    }

    static ReviewCommand toCommand(ReviewRequest request) {
        return new ReviewCommand(request.recommendationId(), request.decision(), request.finalClassification(),
                request.finalRoute(), request.rationale());
    }

    static CaseResponse toResponse(CaseView c) {
        TransactionClaim t = c.claim();
        return new CaseResponse(c.caseId(), c.status(), CaseStateMachine.allowedTargets(c.status()),
                c.customerReference(), c.cardReference(),
                t == null ? null : new TransactionClaimResponse(t.transactionReference(), t.merchantName(),
                        t.merchantCategoryCode(), t.amount(), t.currency().getCurrencyCode(), t.transactionAt(),
                        t.claimType()),
                c.customerStatement(), c.createdBy(), c.humanReviewRequired(), c.createdAt(), c.updatedAt());
    }

    static RecommendationResponse toResponse(RecordedRecommendation r) {
        return RecommendationResponse.from(r.recommendationId(), r.recommendation(), r.createdAt());
    }

    static InvestigationResponse toResponse(InvestigationOutcome o) {
        RecommendationResponse rec = toResponse(o.recommendation());
        return new InvestigationResponse(o.caseId(), o.status(), rec.humanReviewRequired(), o.fallbackUsed(), rec,
                o.evidence().stream().map(EvidenceItemResponse::from).toList());
    }

    static CaseMessageExchangeResponse toResponse(CaseMessageExchange e) {
        return new CaseMessageExchangeResponse(CaseMessageResponse.from(e.message()),
                CaseMessageResponse.from(e.reply()), e.fallbackUsed(), true);
    }

    static ReviewResponse toResponse(RecordedReview r) {
        ReviewDisposition d = r.disposition();
        return new ReviewResponse(r.reviewId(), d.caseId(), d.recommendationId(), d.reviewerId(), d.decision(),
                d.finalClassification(), d.finalRoute(), d.rationale(), r.resultingStatus(), d.reviewedAt());
    }
}
