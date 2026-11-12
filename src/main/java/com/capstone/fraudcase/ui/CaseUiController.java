package com.capstone.fraudcase.ui;

import com.capstone.fraudcase.agent.springai.FraudAiProperties;
import com.capstone.fraudcase.application.CaseView;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.application.RecommendationNotFoundException;
import com.capstone.fraudcase.application.RecordedRecommendation;
import com.capstone.fraudcase.application.ReviewCommand;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.DomainValidationException;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.InvalidCaseTransitionException;
import com.capstone.fraudcase.application.CaseConflictException;
import com.capstone.fraudcase.application.CaseNotFoundException;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.application.CaseMessageExchange;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Server-rendered analyst workspace. Thin like the REST controller: all state changes go through
 * {@link FraudCaseService}, the actor is the authenticated principal and IDs come from the path, never the body.
 */
@Controller
public class CaseUiController {

    private static final String CORRELATION = CorrelationIdFilter.ATTRIBUTE;

    private final FraudCaseService service;
    private final FraudAiProperties ai;

    public CaseUiController(FraudCaseService service, FraudAiProperties ai) {
        this.service = service;
        this.ai = ai;
    }

    @ModelAttribute("appMode")
    String appMode() {
        return ai.enabled() ? "AI" : "RULES";
    }

    @ModelAttribute("actor")
    String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/ui";
    }

    @GetMapping("/login")
    public String login(Principal principal) {
        return principal == null ? "login" : "redirect:/ui";
    }

    @GetMapping("/ui")
    public String dashboard(Model model) {
        List<CaseView> cases = service.listCases();
        Map<CaseStatus, Long> counts = new TreeMap<>();
        cases.forEach(c -> counts.merge(c.status(), 1L, Long::sum));
        model.addAttribute("cases", cases);
        model.addAttribute("counts", counts);
        return "dashboard";
    }

    // ---------- 1. Case intake ----------

    @GetMapping("/ui/cases/new")
    public String intake(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CaseIntakeForm());
        }
        model.addAttribute("claimTypes", com.capstone.fraudcase.domain.ClaimType.values());
        return "intake";
    }

    @PostMapping("/ui/cases")
    public String createCase(@Valid @ModelAttribute("form") CaseIntakeForm form, BindingResult binding,
                             Principal principal, @RequestAttribute(name = CORRELATION, required = false) String cid,
                             Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("claimTypes", com.capstone.fraudcase.domain.ClaimType.values());
            return "intake";
        }
        try {
            CaseView created = service.createCase(form.toNewCase(), principal.getName(), cid);
            redirect.addFlashAttribute("notice", "Case " + created.caseId() + " created. Human review is required.");
            return "redirect:/ui/cases/" + created.caseId();
        } catch (DomainValidationException | IllegalArgumentException e) {
            binding.reject("intake", safe(e.getMessage()));
            model.addAttribute("claimTypes", com.capstone.fraudcase.domain.ClaimType.values());
            return "intake";
        }
    }

    // ---------- 2. Investigation / tool execution ----------

    @GetMapping("/ui/cases/{caseId}")
    public String investigation(@PathVariable String caseId, Model model) {
        workspace(model, caseId);
        return "case-investigation";
    }

    @PostMapping("/ui/cases/{caseId}/investigate")
    public String investigate(@PathVariable String caseId, Principal principal,
                              @RequestAttribute(name = CORRELATION, required = false) String cid,
                              RedirectAttributes redirect) {
        try {
            var outcome = service.startInvestigation(caseId, principal.getName(), cid);
            redirect.addFlashAttribute("notice",
                    "Investigation finished: " + outcome.status() + ". Recommendation is advisory only.");
        } catch (InvalidCaseTransitionException | CaseConflictException e) {
            redirect.addFlashAttribute("error", "Investigation cannot run in the current state: " + e.getMessage());
        }
        return "redirect:/ui/cases/" + caseId;
    }

    // ---------- 3. Recommendation ----------

    @GetMapping("/ui/cases/{caseId}/recommendation")
    public String recommendation(@PathVariable String caseId, Model model) {
        workspace(model, caseId);
        return "case-recommendation";
    }

    // ---------- 4. Evidence workspace ----------

    @GetMapping("/ui/cases/{caseId}/evidence")
    public String evidence(@PathVariable String caseId, Model model) {
        workspace(model, caseId);
        return "case-evidence";
    }

    // ---------- 5. Policy citations ----------

    @GetMapping("/ui/cases/{caseId}/citations")
    public String citations(@PathVariable String caseId, Model model) {
        workspace(model, caseId);
        return "case-citations";
    }

    // ---------- 6. Human review ----------

    @GetMapping("/ui/cases/{caseId}/review")
    public String review(@PathVariable String caseId, Model model, HttpServletRequest request) {
        workspace(model, caseId);
        if (!model.containsAttribute("form")) {
            ReviewForm form = new ReviewForm();
            RecordedRecommendation rec = (RecordedRecommendation) model.getAttribute("recommendation");
            if (rec != null) {
                form.setRecommendationId(rec.recommendationId());
            }
            model.addAttribute("form", form);
        }
        model.addAttribute("reviewer", request.isUserInRole("REVIEWER"));
        model.addAttribute("classifications", Classification.values());
        model.addAttribute("routes", Route.values());
        return "case-review";
    }

    @PostMapping("/ui/cases/{caseId}/reviews")
    public String submitReview(@PathVariable String caseId,
                               @Valid @ModelAttribute("form") ReviewForm form, BindingResult binding,
                               Principal principal, HttpServletRequest request,
                               @RequestAttribute(name = CORRELATION, required = false) String cid,
                               Model model, RedirectAttributes redirect) {
        CaseView c = service.getCase(caseId);
        RecordedRecommendation rec = recommendationOrNull(caseId);
        if (!binding.hasErrors()) {
            validateReviewRules(form, rec, binding);
        }
        if (binding.hasErrors()) {
            workspace(model, caseId);
            model.addAttribute("reviewer", request.isUserInRole("REVIEWER"));
            model.addAttribute("classifications", Classification.values());
            model.addAttribute("routes", Route.values());
            return "case-review";
        }
        try {
            var recorded = service.submitReview(caseId,
                    new ReviewCommand(blankToNull(form.getRecommendationId()), form.getDecision(),
                            form.getFinalClassification(), form.getFinalRoute(), form.getRationale()),
                    principal.getName(), cid);
            redirect.addFlashAttribute("notice", "Review recorded: " + recorded.disposition().decision()
                    + " — case is now " + recorded.resultingStatus() + ".");
            return "redirect:/ui/cases/" + caseId + "/review";
        } catch (DomainValidationException | InvalidCaseTransitionException | CaseConflictException e) {
            binding.reject("review", safe(e.getMessage()));
            workspace(model, caseId);
            model.addAttribute("reviewer", request.isUserInRole("REVIEWER"));
            model.addAttribute("classifications", Classification.values());
            model.addAttribute("routes", Route.values());
            return "case-review";
        }
    }

    // ---------- Assistant conversation (case-scoped memory) ----------

    @PostMapping("/ui/cases/{caseId}/messages")
    public String postMessage(@PathVariable String caseId, @RequestParam("message") String message,
                              Principal principal, @RequestAttribute(name = CORRELATION, required = false) String cid,
                              RedirectAttributes redirect) {
        try {
            CaseMessageExchange exchange = service.postMessage(caseId, message, principal.getName(), cid);
            if (exchange.fallbackUsed()) {
                redirect.addFlashAttribute("error", "The assistant could not produce a validated answer; a safe "
                        + "fallback reply was recorded instead.");
            }
        } catch (CaseConflictException | DomainValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/cases/" + caseId + "#conversation";
    }

    // ---------- shared workspace model ----------

    private void workspace(Model model, String caseId) {
        CaseView c = service.getCase(caseId);
        RecordedRecommendation rec = recommendationOrNull(caseId);
        var traces = service.toolTraces(caseId);
        var evidence = service.evidence(caseId);
        Map<EvidenceTool, Object> latestTraceByTool = new java.util.EnumMap<>(EvidenceTool.class);
        traces.forEach(t -> latestTraceByTool.put(t.tool(), t));

        model.addAttribute("c", c);
        model.addAttribute("traces", traces);
        model.addAttribute("latestTraceByTool", latestTraceByTool);
        model.addAttribute("planTools", InvestigationOrchestrator.EVIDENCE_PLAN);
        model.addAttribute("evidence", evidence);
        model.addAttribute("recommendation", rec);
        model.addAttribute("citations", service.citations(caseId));
        model.addAttribute("messages", service.conversation(caseId));
        model.addAttribute("messageCount", service.conversationSize(caseId));
        model.addAttribute("audit", service.auditTrail(caseId));
        model.addAttribute("allowedTargets", CaseStateMachine.allowedTargets(c.status()));
        model.addAttribute("canInvestigate",
                CaseStateMachine.canTransition(c.status(), CaseStatus.ENRICHING));
        model.addAttribute("canReview", reviewable(c.status()));
        model.addAttribute("unavailableEvidence",
                evidence.stream().filter(e -> !e.isAvailable()).toList());
    }

    private RecordedRecommendation recommendationOrNull(String caseId) {
        try {
            return service.latestRecommendation(caseId);
        } catch (RecommendationNotFoundException e) {
            return null;
        }
    }

    private static boolean reviewable(CaseStatus status) {
        return REVIEWABLE.contains(status);
    }

    private static final Set<CaseStatus> REVIEWABLE = EnumSet.of(CaseStatus.READY_FOR_REVIEW,
            CaseStatus.INCOMPLETE, CaseStatus.UNDER_REVIEW, CaseStatus.ROUTED);

    private void validateReviewRules(ReviewForm form, RecordedRecommendation rec, BindingResult binding) {
        if (form.getDecision() == null) {
            return;
        }
        switch (form.getDecision()) {
            case ACCEPT, OVERRIDE -> {
                if (rec == null) {
                    binding.reject("review", "No recommendation exists to accept or override");
                } else if (form.getRecommendationId() == null || form.getRecommendationId().isBlank()) {
                    binding.rejectValue("recommendationId", "required",
                            "is required when accepting or overriding");
                }
                if (form.getDecision() == com.capstone.fraudcase.domain.ReviewDecision.OVERRIDE) {
                    if (form.getFinalClassification() == null) {
                        binding.rejectValue("finalClassification", "required",
                                "is required when overriding the recommendation");
                    }
                    if (form.getFinalRoute() == null) {
                        binding.rejectValue("finalRoute", "required",
                                "is required when overriding the recommendation");
                    }
                }
            }
            case CLOSE -> { }
        }
        if (rec != null && !rec.recommendation().missingInformation().isEmpty()
                && !Boolean.TRUE.equals(form.getMissingInfoAcknowledged())) {
            binding.rejectValue("missingInfoAcknowledged", "required",
                    "must be acknowledged: this recommendation lists missing information");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Never leak stack details into the page; domain messages are already safe. */
    private static String safe(String message) {
        return message == null || message.isBlank() ? "The request was rejected." : message;
    }

    // ---------- error pages ----------

    @ExceptionHandler(CaseNotFoundException.class)
    public String caseNotFound(CaseNotFoundException e, Model model, jakarta.servlet.http.HttpServletResponse res) {
        res.setStatus(404);
        model.addAttribute("errorTitle", "Case not found");
        model.addAttribute("errorDetail", safe(e.getMessage()));
        return "error";
    }
}
