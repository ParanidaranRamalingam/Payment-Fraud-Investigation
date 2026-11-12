package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

public class RecommendationPage extends BasePage {

    private static final By SUMMARY = By.cssSelector(".card--highlight");
    private static final By HUMAN_REVIEW_BADGE = By.cssSelector(".card--highlight .badge--warn");
    private static final By MISSING_ITEMS = By.cssSelector("section[aria-labelledby='missing-title'] li");
    private static final By MISSING_SECTION = By.cssSelector("section[aria-labelledby='missing-title']");
    private static final By RATIONALE = By.cssSelector("section[aria-labelledby='rationale-title']");
    private static final By EVIDENCE_LINKS = By.cssSelector("section[aria-labelledby='rec-evidence-title'] li a");
    private static final By PROCEED = By.linkText("Proceed to human review");
    private static final By CONFIDENCE_METER = By.cssSelector(".confidence__meter");

    public RecommendationPage(TestContext context) {
        super(context);
    }

    public RecommendationPage open(String caseId) {
        goCasePage("/ui/cases/" + caseId + "/recommendation");
        waitForTitle("Recommendation");
        return this;
    }

    public boolean summaryShown() {
        return !all(SUMMARY).isEmpty();
    }

    public String summaryText() {
        return waitVisible(SUMMARY).getText();
    }

    public String humanReviewLabel() {
        return waitVisible(HUMAN_REVIEW_BADGE).getText();
    }

    public String rationaleText() {
        return waitVisible(RATIONALE).getText();
    }

    public List<String> missingInformation() {
        return all(MISSING_ITEMS).stream().map(WebElement::getText).toList();
    }

    public String missingSectionText() {
        List<WebElement> el = all(MISSING_SECTION);
        return el.isEmpty() ? "" : el.getFirst().getText();
    }

    public int citedEvidenceCount() {
        return all(EVIDENCE_LINKS).size();
    }

    public String confidenceValue() {
        List<WebElement> m = all(CONFIDENCE_METER);
        return m.isEmpty() ? null : m.getFirst().getAttribute("value");
    }

    public ReviewPage proceedToReview() {
        click(PROCEED);
        return new ReviewPage(context);
    }

    public String classification() {
        return summaryField("Classification");
    }

    public String route() {
        return summaryField("Proposed route");
    }

    private String summaryField(String label) {
        List<WebElement> items = all(By.cssSelector(".card--highlight .case-strip__label"));
        for (WebElement item : items) {
            if (item.getText().trim().equalsIgnoreCase(label)) {
                return item.findElement(By.xpath("following-sibling::*[1]")).getText().trim();
            }
        }
        return null;
    }
}
