package com.capstone.fraudcase.ui;

import com.capstone.fraudcase.TestFixtures;
import com.capstone.fraudcase.agent.springai.FraudAiProperties;
import com.capstone.fraudcase.application.CaseView;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.application.RecordedRecommendation;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.AuditEventType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.ToolTrace;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import com.capstone.fraudcase.persistence.entity.AuditEventEntity;
import com.capstone.fraudcase.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-rendered UI tests against a minimal context: the real templates, the real security filter chain
 * (CSRF + role checks) and a mocked {@link FraudCaseService}. Covers intake validation, CSRF, mandatory
 * reviewer rationale, the policy-abstention state, tool-trace rendering and provenance labels.
 */
@SpringBootTest(classes = {
        CaseUiController.class, UiFormat.class, SecurityConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, WebMvcAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class, ThymeleafAutoConfiguration.class,
        CaseUiControllerTest.TestBeans.class})
@AutoConfigureMockMvc
class CaseUiControllerTest {

    private static final String CASE_ID = UUID.randomUUID().toString();

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        FraudAiProperties fraudAiProperties() {
            return new FraudAiProperties(false, Duration.ofSeconds(15));
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FraudCaseService service;

    private static CaseView caseView(CaseStatus status) {
        return new CaseView(CASE_ID, status, "cus_A1b2C3d4", "****4242", TestFixtures.transactionClaim(),
                "I do not recognise this purchase.", "analyst-1", true, Instant.now(), Instant.now());
    }

    private void stubWorkspace(CaseStatus status, boolean withRecommendation) {
        ToolTrace trace = new ToolTrace(UUID.randomUUID().toString(), CASE_ID, EvidenceTool.DEVICE_SIGNALS,
                ToolTraceStatus.SUCCESS, "device lookup for cus_A1b2C3d4", "new device observed", null,
                "corr-1", Instant.now(), Instant.now(), 12L);
        EvidenceItem item = new EvidenceItem(UUID.randomUUID().toString(), CASE_ID, EvidenceTool.DEVICE_SIGNALS,
                ProvenanceType.TOOL_RESULT, EvidenceStatus.AVAILABLE, "Device not seen before.", trace.traceId(),
                Instant.now());
        when(service.getCase(CASE_ID)).thenReturn(caseView(status));
        when(service.toolTraces(CASE_ID)).thenReturn(List.of(trace));
        when(service.evidence(CASE_ID)).thenReturn(List.of(item));
        when(service.citations(CASE_ID)).thenReturn(List.of());
        when(service.conversation(CASE_ID)).thenReturn(List.of());
        when(service.conversationSize(CASE_ID)).thenReturn(0L);
        when(service.auditTrail(CASE_ID)).thenReturn(List.of(new AuditEventEntity(CASE_ID,
                AuditEventType.CASE_CREATED, ActorType.ANALYST, "analyst-1", null, CaseStatus.NEW,
                "claimType=UNRECOGNISED_TRANSACTION", "corr-1")));
        if (withRecommendation) {
            when(service.latestRecommendation(CASE_ID)).thenReturn(new RecordedRecommendation(
                    UUID.randomUUID().toString(), TestFixtures.recommendation(CASE_ID, List.of(item.evidenceId())),
                    Instant.now()));
        } else {
            when(service.latestRecommendation(CASE_ID))
                    .thenThrow(new com.capstone.fraudcase.application.RecommendationNotFoundException(CASE_ID));
        }
    }

    @Test
    void unauthenticated_isChallenged() throws Exception {
        mvc.perform(get("/ui")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void dashboard_listsCases() throws Exception {
        when(service.listCases()).thenReturn(List.of(caseView(CaseStatus.READY_FOR_REVIEW)));

        mvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Case queue")))
                .andExpect(content().string(containsString("Ready For Review")))
                .andExpect(content().string(containsString("Synthetic Electronics Ltd")));
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void intakeForm_showsSyntheticNoticeAndMaskedExamples() throws Exception {
        mvc.perform(get("/ui/cases/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Synthetic data only")))
                .andExpect(content().string(containsString("cus_8f3kA21x")))
                .andExpect(content().string(containsString("****1234")));
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void intakePost_withoutCsrf_isForbidden() throws Exception {
        mvc.perform(post("/ui/cases"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void intakePost_valid_createsCaseAndRedirects() throws Exception {
        when(service.createCase(any(), eq("analyst-1"), any())).thenReturn(caseView(CaseStatus.NEW));

        mvc.perform(post("/ui/cases").with(csrf())
                        .param("customerReference", "cus_A1b2C3d4")
                        .param("cardReference", "****4242")
                        .param("transactionReference", "txn_000123")
                        .param("merchantName", "Synthetic Electronics Ltd")
                        .param("merchantCategoryCode", "5732")
                        .param("amount", "249.99")
                        .param("currency", "GBP")
                        .param("transactionAt", "2026-10-01T09:15")
                        .param("claimType", "UNRECOGNISED_TRANSACTION")
                        .param("customerStatement", "I do not recognise this purchase.")
                        .param("syntheticDataConfirmed", "true")
                        .param("reviewRequired", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/ui/cases/*"));

        verify(service).createCase(any(), eq("analyst-1"), any());
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void intakePost_missingFields_rerendersWithInlineErrors() throws Exception {
        mvc.perform(post("/ui/cases").with(csrf())
                        .param("customerReference", "")
                        .param("merchantName", "Synthetic Electronics Ltd")
                        .param("customerStatement", "I do not recognise this purchase."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("field--error")))
                .andExpect(content().string(containsString("must be confirmed")));
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void investigationPage_showsPlanProgressAndToolTimeline() throws Exception {
        stubWorkspace(CaseStatus.INCOMPLETE, false);

        mvc.perform(get("/ui/cases/{id}", CASE_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bounded plan")))
                .andExpect(content().string(containsString("getDeviceSignals")))
                .andExpect(content().string(containsString("Redacted result")))
                .andExpect(content().string(containsString("Customer claim")))
                .andExpect(content().string(containsString("trace")));
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void recommendationPage_showsAdvisoryClassificationRiskConfidence() throws Exception {
        stubWorkspace(CaseStatus.READY_FOR_REVIEW, true);

        mvc.perform(get("/ui/cases/{id}/recommendation", CASE_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Advisory only")))
                .andExpect(content().string(containsString("Unauthorised Transaction")))
                .andExpect(content().string(containsString("Risk: High")))
                .andExpect(content().string(containsString("Fraud Investigation")))
                .andExpect(content().string(containsString("Required")));
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void citationsPage_noGroundedPolicy_showsExplicitAbstention() throws Exception {
        stubWorkspace(CaseStatus.INCOMPLETE, true);

        mvc.perform(get("/ui/cases/{id}/citations", CASE_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Policy abstention")));
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void evidencePage_distinguishesClaimFromObservedEvidence() throws Exception {
        stubWorkspace(CaseStatus.READY_FOR_REVIEW, true);

        mvc.perform(get("/ui/cases/{id}/evidence", CASE_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Customer claim")))
                .andExpect(content().string(containsString("Observed evidence")))
                .andExpect(content().string(containsString("Device not seen before.")));
    }

    @Test
    @WithMockUser(username = "rev-1", roles = "REVIEWER")
    void reviewPost_withoutRationale_isBlocked() throws Exception {
        stubWorkspace(CaseStatus.READY_FOR_REVIEW, true);

        mvc.perform(post("/ui/cases/{id}/reviews", CASE_ID).with(csrf())
                        .param("decision", "CLOSE")
                        .param("rationale", "  "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rationale")));
    }

    @Test
    @WithMockUser(username = "rev-1", roles = "REVIEWER")
    void reviewPost_overrideWithoutFinals_isBlocked() throws Exception {
        stubWorkspace(CaseStatus.READY_FOR_REVIEW, true);

        mvc.perform(post("/ui/cases/{id}/reviews", CASE_ID).with(csrf())
                        .param("recommendationId", UUID.randomUUID().toString())
                        .param("decision", "OVERRIDE")
                        .param("rationale", "Overriding based on merchant confirmation."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("required when overriding")));
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void reviewPost_asAnalyst_isForbidden() throws Exception {
        mvc.perform(post("/ui/cases/{id}/reviews", CASE_ID).with(csrf())
                        .param("decision", "CLOSE")
                        .param("rationale", "Analysts may not record dispositions."))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "analyst-1", roles = "ANALYST")
    void investigationPage_escapesUntrustedContent() throws Exception {
        stubWorkspace(CaseStatus.INCOMPLETE, false);
        when(service.getCase(CASE_ID)).thenReturn(new CaseView(CASE_ID, CaseStatus.INCOMPLETE,
                "cus_A1b2C3d4", "****4242", TestFixtures.transactionClaim(),
                "<script>alert(1)</script>", "analyst-1", true, Instant.now(), Instant.now()));

        mvc.perform(get("/ui/cases/{id}", CASE_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }
}
