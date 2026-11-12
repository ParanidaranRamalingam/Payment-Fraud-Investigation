package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

public class InvestigationPage extends BasePage {

    private static final By RUN_BUTTON = By.cssSelector(".case-strip__actions form button");
    private static final By TIMELINE_ITEMS = By.cssSelector("#plan-title ~ * .timeline__item, section[aria-labelledby='plan-title'] .timeline__item");
    private static final By GAPS_SECTION = By.cssSelector("section[aria-labelledby='gaps-title']");
    private static final By GAP_ITEMS = By.cssSelector("section[aria-labelledby='gaps-title'] li");
    private static final By NO_RUNS_ALERT = By.cssSelector("section[aria-labelledby='plan-title'] .alert--info");
    private static final By MESSAGE_BOX = By.id("message");
    private static final By SEND_MESSAGE = By.cssSelector("#conversation form button[type='submit']");
    private static final By MESSAGES = By.cssSelector(".message");
    private static final By CLAIM_CARD = By.cssSelector("section[aria-labelledby='claim-title']");

    public InvestigationPage(TestContext context) {
        super(context);
    }

    public InvestigationPage open(String caseId) {
        goCasePage("/ui/cases/" + caseId);
        waitForTitle("Investigation");
        return this;
    }

    public boolean canRunInvestigation() {
        return !all(RUN_BUTTON).isEmpty();
    }

    public InvestigationPage runInvestigation() {
        click(RUN_BUTTON);
        return this;
    }

    public int toolRunCount() {
        return all(TIMELINE_ITEMS).size();
    }

    public List<String> toolNames() {
        return all(By.cssSelector("section[aria-labelledby='plan-title'] .timeline__title span"))
                .stream().map(WebElement::getText).toList();
    }

    public List<String> toolStatuses() {
        return all(By.cssSelector("section[aria-labelledby='plan-title'] .timeline__item .badge"))
                .stream().map(WebElement::getText).toList();
    }

    public boolean noRunsYetInfoShown() {
        return !all(NO_RUNS_ALERT).isEmpty();
    }

    public List<String> gaps() {
        return all(GAP_ITEMS).stream().map(WebElement::getText).toList();
    }

    public String gapsSectionText() {
        return waitVisible(GAPS_SECTION).getText();
    }

    public String claimSummary() {
        return waitVisible(CLAIM_CARD).getText();
    }

    public InvestigationPage sendMessage(String text) {
        type(MESSAGE_BOX, text);
        click(SEND_MESSAGE);
        return this;
    }

    public List<String> messages() {
        return all(MESSAGES).stream().map(WebElement::getText).toList();
    }

    public boolean conversationReadOnly() {
        return all(MESSAGE_BOX).isEmpty();
    }

    /** Extract the case id from the current /ui/cases/{id} URL. */
    public String currentCaseId() {
        String path = currentPath();
        return path.substring("/ui/cases/".length());
    }
}
