package com.capstone.fraudcase.e2e.steps;

import com.capstone.fraudcase.e2e.config.TestContext;
import com.capstone.fraudcase.e2e.pages.CitationsPage;
import com.capstone.fraudcase.e2e.pages.EvidencePage;
import com.capstone.fraudcase.e2e.pages.InvestigationPage;
import com.capstone.fraudcase.e2e.pages.RecommendationPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspaceSteps {

    private final TestContext context;

    public WorkspaceSteps(TestContext context) {
        this.context = context;
    }

    // ---------- investigation ----------

    @When("I run the investigation")
    public void runInvestigation() {
        new InvestigationPage(context).runInvestigation();
    }

    @Then("the tool execution timeline should show runs")
    public void timelineHasRuns() {
        assertTrue(new InvestigationPage(context).toolRunCount() > 0);
    }

    @Then("every planned tool should appear in the timeline")
    public void allPlannedToolsRan() {
        InvestigationPage page = new InvestigationPage(context);
        assertFalse(page.toolNames().isEmpty());
        assertTrue(page.toolRunCount() >= 6, "bounded plan should run at least 6 tools");
    }

    @Then("the investigation should show unavailable or timed-out tools")
    public void unavailableToolsShown() {
        InvestigationPage page = new InvestigationPage(context);
        assertTrue(page.toolStatuses().stream()
                .anyMatch(s -> s.toUpperCase().contains("UNAVAILABLE") || s.toUpperCase().contains("TIMEOUT")
                        || s.toUpperCase().contains("FAILED")),
                "expected an unavailable/timeout tool status, got " + page.toolStatuses());
    }

    @Then("the gaps panel should list unresolved information")
    public void gapsListed() {
        InvestigationPage page = new InvestigationPage(context);
        assertFalse(page.gaps().isEmpty(), "expected gaps, got: " + page.gapsSectionText());
    }

    @Then("the run-investigation action should not be available")
    public void noRunButton() {
        assertFalse(new InvestigationPage(context).canRunInvestigation());
    }

    @Then("the customer claim should show provenance")
    public void claimProvenance() {
        String claim = new InvestigationPage(context).claimSummary();
        assertTrue(claim.toLowerCase().contains("claim"), claim);
    }

    // ---------- recommendation ----------

    @When("I open the recommendation page")
    public void openRecommendation() {
        context.put("recPage", new RecommendationPage(context).open(context.caseId()));
    }

    @When("I open the recommendation page of the named case {string}")
    public void openNamedRecommendation(String key) {
        context.put("recPage", new RecommendationPage(context).open(context.namedCaseId(key)));
    }

    @Then("the advisory assessment should be displayed")
    public void assessmentShown() {
        assertTrue(recPage().summaryShown());
    }

    @Then("human review should be marked required")
    public void humanReviewRequired() {
        assertEquals("Required", recPage().humanReviewLabel());
    }

    @Then("the recommended route should be {string}")
    public void routeIs(String expected) {
        String route = recPage().route();
        assertNotNull(route, "route not rendered");
        assertTrue(route.toUpperCase().contains(expected.toUpperCase()),
                "route '" + route + "' does not contain '" + expected + "'");
    }

    @Then("the classification should be {string}")
    public void classificationIs(String expected) {
        String c = recPage().classification();
        assertNotNull(c, "classification not rendered");
        assertTrue(c.toUpperCase().contains(expected.toUpperCase()),
                "classification '" + c + "' does not contain '" + expected + "'");
    }

    @Then("the recommendation should list missing or contradictory information")
    public void missingListed() {
        assertFalse(recPage().missingInformation().isEmpty(),
                "expected missing/contradictory entries, section: " + recPage().missingSectionText());
    }

    @Then("the recommendation should cite evidence")
    public void evidenceCited() {
        assertTrue(recPage().citedEvidenceCount() > 0);
    }

    @Then("the confidence should be visualised")
    public void confidenceShown() {
        assertNotNull(recPage().confidenceValue());
    }

    // ---------- evidence ----------

    @When("I open the evidence workspace")
    public void openEvidence() {
        context.put("evidencePage", new EvidencePage(context).open(context.caseId()));
    }

    @Then("evidence cards should be displayed with provenance")
    public void evidenceCards() {
        EvidencePage page = evidencePage();
        assertTrue(page.cardCount() > 0);
        assertFalse(page.provenanceLabels().isEmpty());
    }

    @Then("unavailable evidence should be flagged")
    public void unavailableFlagged() {
        EvidencePage page = evidencePage();
        assertTrue(page.unavailableWarningShown()
                || page.statuses().stream().anyMatch(s -> !s.equalsIgnoreCase("available")),
                "statuses: " + page.statuses());
    }

    // ---------- citations ----------

    @When("I open the policy citations page")
    public void openCitations() {
        context.put("citPage", new CitationsPage(context).open(context.caseId()));
    }

    @Then("policy citations should be listed")
    public void citationsListed() {
        assertTrue(citPage().citationCount() > 0, "expected citations: " + citPage().pageBody());
    }

    @Then("the abstention or empty state should be shown")
    public void abstentionShown() {
        assertTrue(citPage().citationCount() == 0 || citPage().abstentionShown());
    }

    private RecommendationPage recPage() {
        RecommendationPage p = context.get("recPage");
        return p != null ? p : new RecommendationPage(context);
    }

    private EvidencePage evidencePage() {
        EvidencePage p = context.get("evidencePage");
        return p != null ? p : new EvidencePage(context);
    }

    private CitationsPage citPage() {
        CitationsPage p = context.get("citPage");
        return p != null ? p : new CitationsPage(context);
    }
}
