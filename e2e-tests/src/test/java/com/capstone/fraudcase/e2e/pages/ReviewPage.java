package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

public class ReviewPage extends BasePage {

    private static final By FORM = By.cssSelector("section[aria-labelledby='rev-form-title'] form");
    private static final By READONLY_NOTE = By.cssSelector("section[aria-labelledby='rev-form-title'] .text-muted");
    private static final By ACCEPT_RADIO = By.cssSelector("input[name='decision'][value='ACCEPT']");
    private static final By OVERRIDE_RADIO = By.cssSelector("input[name='decision'][value='OVERRIDE']");
    private static final By CLOSE_RADIO = By.cssSelector("input[name='decision'][value='CLOSE']");
    private static final By FINAL_CLASS = By.id("finalClassification");
    private static final By FINAL_ROUTE = By.id("finalRoute");
    private static final By RATIONALE = By.id("rationale");
    private static final By MISSING_ACK = By.id("missingInfoAcknowledged");
    private static final By SUBMIT = By.cssSelector("section[aria-labelledby='rev-form-title'] button[type='submit']");
    private static final By FIELD_ERRORS = By.cssSelector(".field__error");
    private static final By GLOBAL_ERRORS = By.cssSelector(".alert--danger");
    private static final By AUDIT_ITEMS = By.cssSelector("section[aria-labelledby='audit-title'] .timeline__item");
    private static final By RECOMMENDATION_SUMMARY = By.cssSelector(".review-summary");

    public ReviewPage(TestContext context) {
        super(context);
    }

    public ReviewPage open(String caseId) {
        goCasePage("/ui/cases/" + caseId + "/review");
        waitForTitle("Human review");
        return this;
    }

    public boolean formVisible() {
        return !all(FORM).isEmpty();
    }

    public String readOnlyNote() {
        List<WebElement> el = all(READONLY_NOTE);
        return el.isEmpty() ? null : el.getFirst().getText();
    }

    public ReviewPage decision(String decision) {
        By radio = switch (decision) {
            case "ACCEPT" -> ACCEPT_RADIO;
            case "OVERRIDE" -> OVERRIDE_RADIO;
            case "CLOSE" -> CLOSE_RADIO;
            default -> throw new IllegalArgumentException("unknown decision " + decision);
        };
        click(radio);
        return this;
    }

    public ReviewPage finalClassification(String value) {
        new Select(waitVisible(FINAL_CLASS)).selectByValue(value);
        return this;
    }

    public ReviewPage finalRoute(String value) {
        new Select(waitVisible(FINAL_ROUTE)).selectByValue(value);
        return this;
    }

    public ReviewPage rationale(String text) {
        type(RATIONALE, text);
        return this;
    }

    public boolean missingInfoAckShown() {
        return !all(MISSING_ACK).isEmpty();
    }

    /** Clicks the acknowledgement checkbox when rendered; a no-op when the case has no missing info. */
    public ReviewPage acknowledgeMissingInfo() {
        for (WebElement box : all(MISSING_ACK)) {
            if (!box.isSelected()) {
                box.click();
            }
        }
        return this;
    }

    /** HTML5-required fields may block submission natively; use JS submit to exercise server validation. */
    public ReviewPage submitBypassingClientValidation() {
        driver().findElements(FORM).forEach(f ->
                ((org.openqa.selenium.JavascriptExecutor) driver())
                        .executeScript("arguments[0].requestSubmit ? arguments[0].submit() : arguments[0].submit()", f));
        return this;
    }

    public ReviewPage submit() {
        click(SUBMIT);
        return this;
    }

    public List<String> errors() {
        return all(FIELD_ERRORS).stream().map(WebElement::getText)
                .filter(t -> !t.isBlank()).toList();
    }

    public List<String> globalErrors() {
        return all(GLOBAL_ERRORS).stream().map(WebElement::getText).toList();
    }

    public String recommendationSummary() {
        List<WebElement> el = all(RECOMMENDATION_SUMMARY);
        return el.isEmpty() ? null : el.getFirst().getText();
    }

    public int auditEventCount() {
        return all(AUDIT_ITEMS).size();
    }

    public boolean acceptDisabled() {
        List<WebElement> el = all(ACCEPT_RADIO);
        return el.isEmpty() || !el.getFirst().isEnabled();
    }
}
