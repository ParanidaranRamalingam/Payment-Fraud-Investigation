package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

public class CitationsPage extends BasePage {

    private static final By ROWS = By.cssSelector(".data-table tbody tr");
    private static final By ABSTAIN_ALERT = By.cssSelector(".alert--warn");
    private static final By PASSAGES = By.cssSelector(".citation-passage");
    private static final By NO_REC_CARD = By.cssSelector(".card .empty-state, .card");

    public CitationsPage(TestContext context) {
        super(context);
    }

    public CitationsPage open(String caseId) {
        goCasePage("/ui/cases/" + caseId + "/citations");
        waitForTitle("Policy citations");
        return this;
    }

    public int citationCount() {
        return all(ROWS).size();
    }

    public List<String> passages() {
        return all(PASSAGES).stream().map(WebElement::getText).toList();
    }

    public boolean abstentionShown() {
        List<WebElement> alerts = all(ABSTAIN_ALERT);
        return !alerts.isEmpty() && alerts.getFirst().getText().toLowerCase().contains("policy");
    }

    public String pageBody() {
        return pageText();
    }
}
