package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

public class EvidencePage extends BasePage {

    private static final By CARDS = By.cssSelector(".evidence-card");
    private static final By TYPES = By.cssSelector(".evidence-card__type");
    private static final By STATUSES = By.cssSelector(".evidence-card .badge");
    private static final By PROVENANCE = By.cssSelector(".evidence-card .provenance-tag");
    private static final By UNAVAILABLE_ALERT = By.cssSelector(".alert--warn");
    private static final By EMPTY = By.cssSelector(".empty-state");

    public EvidencePage(TestContext context) {
        super(context);
    }

    public EvidencePage open(String caseId) {
        goCasePage("/ui/cases/" + caseId + "/evidence");
        waitForTitle("Evidence workspace");
        return this;
    }

    public int cardCount() {
        return all(CARDS).size();
    }

    public List<String> evidenceTypes() {
        return all(TYPES).stream().map(WebElement::getText).toList();
    }

    public List<String> statuses() {
        return all(STATUSES).stream().map(WebElement::getText).toList();
    }

    public List<String> provenanceLabels() {
        return all(PROVENANCE).stream().map(WebElement::getText).toList();
    }

    public boolean unavailableWarningShown() {
        return !all(UNAVAILABLE_ALERT).isEmpty();
    }

    public boolean emptyStateShown() {
        return !all(EMPTY).isEmpty();
    }
}
