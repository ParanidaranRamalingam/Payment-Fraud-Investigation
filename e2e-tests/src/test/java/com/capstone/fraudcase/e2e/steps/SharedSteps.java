package com.capstone.fraudcase.e2e.steps;

import com.capstone.fraudcase.e2e.config.TestContext;
import com.capstone.fraudcase.e2e.pages.DashboardPage;
import com.capstone.fraudcase.e2e.pages.IntakePage;
import com.capstone.fraudcase.e2e.pages.InvestigationPage;
import com.capstone.fraudcase.e2e.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SharedSteps {

    private final TestContext context;

    public SharedSteps(TestContext context) {
        this.context = context;
    }

    // ---------- authentication ----------

    @Given("I am signed in as an analyst")
    public void signedInAsAnalyst() {
        signIn(TestContext.ANALYST, TestContext.ANALYST_PW);
    }

    @Given("I am signed in as a reviewer")
    public void signedInAsReviewer() {
        signIn(TestContext.REVIEWER, TestContext.REVIEWER_PW);
    }

    @Given("I am signed in as a lead")
    public void signedInAsLead() {
        signIn(TestContext.LEAD, TestContext.LEAD_PW);
    }

    /** Idempotent: signs out first if a previous step left an authenticated session. */
    private void signIn(String user, String password) {
        context.driver().get(context.baseUrl() + "/ui");
        if (!context.driver().findElements(org.openqa.selenium.By.id("username")).isEmpty()) {
            new LoginPage(context).signIn(user, password);
            return;
        }
        // Already authenticated — sign out, then sign in as the requested user.
        if (!context.driver().findElements(
                org.openqa.selenium.By.cssSelector(".app-header form button.logout-btn")).isEmpty()) {
            new DashboardPage(context).signOut();
        }
        new LoginPage(context).open().signIn(user, password);
    }

    @When("I sign in as {string} with password {string}")
    public void signInAs(String user, String password) {
        new LoginPage(context).open().signIn(user, password);
    }

    @When("I try to sign in as {string} with password {string}")
    public void signInFailing(String user, String password) {
        context.put("loginPage",
                new LoginPage(context).open().signInExpectingFailure(user, password));
    }

    @When("I sign out")
    public void signOut() {
        new DashboardPage(context).signOut();
    }

    // ---------- navigation ----------

    @When("I open the dashboard")
    public void openDashboard() {
        new DashboardPage(context).open();
    }

    @When("I open the page {string}")
    public void openPath(String path) {
        context.driver().get(context.baseUrl() + path);
    }

    @When("I open the current case investigation page")
    public void openCurrentCase() {
        context.driver().get(context.baseUrl() + "/ui/cases/" + context.caseId());
    }

    @Then("I should be on the login page")
    public void onLoginPage() {
        assertEquals("/login", java.net.URI.create(context.driver().getCurrentUrl()).getPath());
    }

    @Then("I should be on the dashboard")
    public void onDashboard() {
        assertTrue(new DashboardPage(context).isAt());
    }

    // ---------- case fixtures through the real UI ----------

    @Given("a case exists for scenario {string} with claim type {string}")
    public void caseExists(String txnRef, String claimType) {
        signedInAsAnalyst();
        String id = createCase(txnRef, claimType);
        context.caseId(id);
    }

    @Given("a case {string} exists for scenario {string} with claim type {string}")
    public void namedCaseExists(String key, String txnRef, String claimType) {
        signedInAsAnalyst();
        context.put(key, createCase(txnRef, claimType));
    }

    /** Combined fixture: creates a case through the UI and runs its investigation. */
    @Given("an investigated case exists for scenario {string} with claim type {string}")
    public void investigatedCaseExists(String txnRef, String claimType) {
        caseExists(txnRef, claimType);
        caseInvestigated();
    }

    @Given("the case has been investigated")
    public void caseInvestigated() {
        new InvestigationPage(context).open(context.caseId()).runInvestigation();
    }

    @Given("the named case {string} has been investigated")
    public void namedCaseInvestigated(String key) {
        new InvestigationPage(context).open(context.namedCaseId(key)).runInvestigation();
    }

    /** Creates a case entirely through the UI and returns its id. */
    private String createCase(String txnRef, String claimType) {
        IntakePage intake = new DashboardPage(context).open().newCase().fillValid(txnRef, claimType);
        InvestigationPage investigation = intake.submitExpectingSuccess();
        String id = investigation.waitForCaseId();
        if (id == null) {
            throw new AssertionError("intake did not create a case (still at "
                    + investigation.currentPath() + "). Errors: " + intake.fieldErrors());
        }
        return id;
    }
}
