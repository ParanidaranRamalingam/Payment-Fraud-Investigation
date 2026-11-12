package com.capstone.fraudcase.e2e.steps;

import com.capstone.fraudcase.e2e.config.TestContext;
import com.capstone.fraudcase.e2e.pages.DashboardPage;
import com.capstone.fraudcase.e2e.pages.IntakePage;
import com.capstone.fraudcase.e2e.pages.InvestigationPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntakeSteps {

    private final TestContext context;
    private IntakePage intake;

    public IntakeSteps(TestContext context) {
        this.context = context;
    }

    @When("I start a new case")
    public void startNewCase() {
        intake = new DashboardPage(context).open().newCase();
        assertTrue(intake.isAt());
    }

    @When("I fill a valid intake for scenario {string} with claim type {string}")
    public void fillValid(String txnRef, String claimType) {
        intake.fillValid(txnRef, claimType);
    }

    @When("I set intake field {string} to {string}")
    public void setField(String fieldId, String value) {
        intake.field(fieldId, value);
    }

    @When("I clear intake field {string}")
    public void clearField(String fieldId) {
        intake.field(fieldId, "");
    }

    @When("I uncheck intake confirmation {string}")
    public void uncheck(String checkboxId) {
        intake.uncheck(checkboxId);
    }

    @When("I set claim type {string}")
    public void claimType(String value) {
        intake.claimType(value);
    }

    @When("I submit the intake")
    public void submit() {
        // JS submit bypasses HTML5 constraints so server-side validation is exercised.
        intake.submitRaw();
        context.put("intakeResult", intake);
    }

    @When("I submit the intake expecting a case")
    public void submitExpectingCase() {
        InvestigationPage page = intake.submitExpectingSuccess();
        String id = page.waitForCaseId();
        if (id == null) {
            throw new AssertionError("intake did not create a case (still at " + page.currentPath() + "). "
                    + "Field errors: " + intake.fieldErrors()
                    + " | global errors: " + intake.globalErrors());
        }
        context.caseId(id);
    }

    @Then("a case should be created and the investigation page shown")
    public void caseCreated() {
        assertNotNull(context.caseId());
        assertTrue(context.caseId().length() > 8);
        assertNotNull(new InvestigationPage(context).flashNotice());
    }

    @Then("the intake should show field errors")
    public void fieldErrors() {
        IntakePage page = (IntakePage) context.get("intakeResult");
        if (page == null) {
            page = intake;
        }
        assertFalse(page.fieldErrors().isEmpty(), "expected validation errors");
        assertTrue(page.isAt(), "should stay on intake after validation failure");
    }

    @Then("the intake should report a global error")
    public void globalError() {
        assertFalse(intake.globalErrors().isEmpty());
    }

    @Then("the intake should show an error for {string}")
    public void errorFor(String fieldId) {
        assertTrue(context.driver().findElements(
                org.openqa.selenium.By.cssSelector("#" + fieldId)).stream()
                .findFirst()
                .map(el -> el.findElement(org.openqa.selenium.By.xpath("ancestor::div[contains(@class,'field')]"))
                        .getAttribute("class").contains("field--error"))
                .orElse(false), "expected field--error on " + fieldId);
    }
}
