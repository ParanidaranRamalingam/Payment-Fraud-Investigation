package com.capstone.fraudcase.e2e.steps;

import com.capstone.fraudcase.e2e.config.TestContext;
import com.capstone.fraudcase.e2e.pages.ReviewPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReviewSteps {

    private final TestContext context;
    private ReviewPage page;

    public ReviewSteps(TestContext context) {
        this.context = context;
    }

    @When("I open the review page")
    public void openReview() {
        page = new ReviewPage(context).open(context.caseId());
        context.put("reviewPage", page);
    }


    @When("I choose decision {string}")
    public void decision(String d) {
        reviewPage().decision(d);
    }

    @When("I set final classification {string} and final route {string}")
    public void finals(String classification, String route) {
        reviewPage().finalClassification(classification).finalRoute(route);
    }

    @When("I enter rationale {string}")
    public void rationale(String text) {
        reviewPage().rationale(text);
    }

    @When("I acknowledge the missing information")
    public void ackMissing() {
        reviewPage().acknowledgeMissingInfo();
    }

    @When("I submit the review")
    public void submit() {
        reviewPage().submit();
    }

    @When("I submit the review bypassing client-side required fields")
    public void submitRaw() {
        reviewPage().submitBypassingClientValidation();
    }

    @Then("the review form should be visible")
    public void formVisible() {
        assertTrue(reviewPage().formVisible());
    }

    @Then("the review form should not be editable")
    public void formReadOnly() {
        assertFalse(reviewPage().formVisible());
        assertTrue(reviewPage().readOnlyNote() != null || reviewPage().acceptDisabled());
    }

    @Then("the review should be recorded")
    public void recorded() {
        String flash = new com.capstone.fraudcase.e2e.pages.InvestigationPage(context).flashNotice();
        assertTrue(flash != null && reviewPage().auditEventCount() > 0,
                "review was not recorded. Field errors: " + reviewPage().errors()
                        + " | global: " + reviewPage().globalErrors());
    }

    @Then("the review should be rejected with a validation error")
    public void rejected() {
        ReviewPage p = reviewPage();
        assertFalse(p.errors().isEmpty() && p.globalErrors().isEmpty(),
                "expected a validation error on the review form");
    }

    @Then("the case status should be {string}")
    public void statusIs(String expected) {
        String badge = reviewPage().caseStatusBadge();
        assertTrue(badge.toUpperCase().contains(expected.toUpperCase()),
                "status '" + badge + "' does not contain '" + expected + "'");
    }

    @Then("the missing-information acknowledgement should be required")
    public void ackRequired() {
        ReviewPage p = reviewPage();
        assertTrue(p.missingInfoAckShown(), "acknowledgement checkbox not rendered");
        assertFalse(p.errors().isEmpty() && p.globalErrors().isEmpty(),
                "expected a validation error requiring acknowledgement");
    }

    private ReviewPage reviewPage() {
        ReviewPage p = context.get("reviewPage");
        return p != null ? p : new ReviewPage(context);
    }
}
