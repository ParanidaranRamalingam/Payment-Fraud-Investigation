package com.capstone.fraudcase.api;

import com.capstone.fraudcase.api.dto.CaseMessageExchangeResponse;
import com.capstone.fraudcase.api.dto.CaseMessageRequest;
import com.capstone.fraudcase.api.dto.CaseResponse;
import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.api.dto.EvidenceItemResponse;
import com.capstone.fraudcase.api.dto.InvestigationResponse;
import com.capstone.fraudcase.api.dto.PolicyCitationResponse;
import com.capstone.fraudcase.api.dto.RecommendationResponse;
import com.capstone.fraudcase.api.dto.ReviewRequest;
import com.capstone.fraudcase.api.dto.ReviewResponse;
import com.capstone.fraudcase.api.dto.ToolTraceResponse;
import com.capstone.fraudcase.application.CaseView;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.observability.CorrelationIdFilter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.List;

/**
 * Case REST API. Thin by design: validates input, maps DTOs and delegates to {@link FraudCaseService}. It never
 * calls tools, processors or the assistant directly. The acting analyst/reviewer is always the authenticated
 * principal; IDs in request bodies are never trusted.
 */
@RestController
@RequestMapping(path = "/api/cases", produces = MediaType.APPLICATION_JSON_VALUE)
public class FraudCaseController {

    private static final String CORRELATION = CorrelationIdFilter.ATTRIBUTE;

    private final FraudCaseService service;

    public FraudCaseController(FraudCaseService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request, Principal principal,
                                                   @RequestAttribute(name = CORRELATION, required = false) String correlationId) {
        CaseView created = service.createCase(CaseApiMapper.toNewCase(request), principal.getName(), correlationId);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{caseId}")
                .buildAndExpand(created.caseId()).toUri()).body(CaseApiMapper.toResponse(created));
    }

    @GetMapping("/{caseId}")
    public CaseResponse getCase(@PathVariable String caseId) {
        return CaseApiMapper.toResponse(service.getCase(caseId));
    }

    @PostMapping("/{caseId}/investigations")
    public InvestigationResponse investigate(@PathVariable String caseId, Principal principal,
                                             @RequestAttribute(name = CORRELATION, required = false) String correlationId) {
        return CaseApiMapper.toResponse(service.startInvestigation(caseId, principal.getName(), correlationId));
    }

    @GetMapping("/{caseId}/evidence")
    public List<EvidenceItemResponse> evidence(@PathVariable String caseId) {
        return service.evidence(caseId).stream().map(EvidenceItemResponse::from).toList();
    }

    @GetMapping("/{caseId}/tool-traces")
    public List<ToolTraceResponse> toolTraces(@PathVariable String caseId) {
        return service.toolTraces(caseId).stream().map(ToolTraceResponse::from).toList();
    }

    @GetMapping("/{caseId}/recommendation")
    public RecommendationResponse recommendation(@PathVariable String caseId) {
        return CaseApiMapper.toResponse(service.latestRecommendation(caseId));
    }

    @GetMapping("/{caseId}/citations")
    public List<PolicyCitationResponse> citations(@PathVariable String caseId) {
        return service.citations(caseId).stream().map(PolicyCitationResponse::from).toList();
    }

    @PostMapping(path = "/{caseId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CaseMessageExchangeResponse postMessage(@PathVariable String caseId,
                                                   @Valid @RequestBody CaseMessageRequest request, Principal principal,
                                                   @RequestAttribute(name = CORRELATION, required = false) String correlationId) {
        return CaseApiMapper.toResponse(service.postMessage(caseId, request.message(), principal.getName(),
                correlationId));
    }

    @PostMapping(path = "/{caseId}/reviews", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse submitReview(@PathVariable String caseId, @Valid @RequestBody ReviewRequest request,
                                       Principal principal,
                                       @RequestAttribute(name = CORRELATION, required = false) String correlationId) {
        return CaseApiMapper.toResponse(service.submitReview(caseId, CaseApiMapper.toCommand(request),
                principal.getName(), correlationId));
    }
}
