package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class IntakePage extends BasePage {

    private static final By CUSTOMER_REF = By.id("customerReference");
    private static final By CARD_REF = By.id("cardReference");
    private static final By TXN_REF = By.id("transactionReference");
    private static final By MERCHANT = By.id("merchantName");
    private static final By MCC = By.id("merchantCategoryCode");
    private static final By TXN_AT = By.id("transactionAt");
    private static final By AMOUNT = By.id("amount");
    private static final By CURRENCY = By.id("currency");
    private static final By CLAIM_TYPE = By.id("claimType");
    private static final By STATEMENT = By.id("customerStatement");
    private static final By SYNTHETIC = By.id("syntheticDataConfirmed");
    private static final By REVIEW_REQ = By.id("reviewRequired");
    private static final By SUBMIT = By.cssSelector("main.app-main button[type='submit']");
    private static final By FIELD_ERRORS = By.cssSelector(".field__error");
    private static final By GLOBAL_ERRORS = By.cssSelector(".alert--danger");
    private static final By ERROR_FIELDS = By.cssSelector(".field--error");

    public IntakePage(TestContext context) {
        super(context);
    }

    public IntakePage open() {
        go("/ui/cases/new");
        waitForTitle("Case intake");
        return this;
    }

    public boolean isAt() {
        // Validation failures re-render at the POST path /ui/cases.
        String path = currentPath();
        return (path.equals("/ui/cases/new") || path.equals("/ui/cases"))
                && !all(CUSTOMER_REF).isEmpty();
    }

    /** Fills a complete valid form; txnRef selects the deterministic synthetic scenario. */
    public IntakePage fillValid(String txnRef, String claimType) {
        waitVisible(CUSTOMER_REF).sendKeys("cus_" + Long.toString(System.nanoTime(), 36));
        type(CARD_REF, "****4242");
        type(TXN_REF, txnRef);
        type(MERCHANT, "E2E Test Merchant");
        type(MCC, "5411");
        // datetime-local accepts only the ISO DOM value; set it via JS rather than keystrokes.
        ((org.openqa.selenium.JavascriptExecutor) driver()).executeScript(
                "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input', {bubbles:true}));",
                waitVisible(TXN_AT),
                LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
        type(AMOUNT, "129.99");
        type(CURRENCY, "USD");
        new Select(waitVisible(CLAIM_TYPE)).selectByValue(claimType);
        type(STATEMENT, "Synthetic E2E claim statement for " + txnRef + ".");
        check(SYNTHETIC);
        check(REVIEW_REQ);
        return this;
    }

    public IntakePage field(String fieldId, String value) {
        type(By.id(fieldId), value);
        return this;
    }

    public IntakePage claimType(String value) {
        new Select(waitVisible(CLAIM_TYPE)).selectByValue(value);
        return this;
    }

    public IntakePage uncheck(String checkboxId) {
        WebElement box = waitVisible(By.id(checkboxId));
        if (box.isSelected()) {
            box.click();
        }
        return this;
    }

    private void check(By locator) {
        WebElement box = waitVisible(locator);
        if (!box.isSelected()) {
            box.click();
        }
    }

    /** Submits and lands on the investigation page of the newly created case. */
    public InvestigationPage submitExpectingSuccess() {
        click(SUBMIT);
        return new InvestigationPage(context);
    }

    public IntakePage submitExpectingErrors() {
        click(SUBMIT);
        return this;
    }

    /** Submits via HTMLFormElement.submit(), bypassing HTML5 constraints so server-side validation runs. */
    public IntakePage submitRaw() {
        ((org.openqa.selenium.JavascriptExecutor) driver())
                .executeScript("document.querySelector('main.app-main form').submit()");
        return this;
    }

    public List<String> fieldErrors() {
        return all(FIELD_ERRORS).stream().map(WebElement::getText).filter(t -> !t.isBlank()).toList();
    }

    public List<String> globalErrors() {
        return all(GLOBAL_ERRORS).stream().map(WebElement::getText).toList();
    }

    public int errorFieldCount() {
        return all(ERROR_FIELDS).size();
    }
}
