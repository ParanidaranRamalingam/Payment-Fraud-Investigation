package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

public class DashboardPage extends BasePage {

    private static final By NEW_CASE = By.linkText("New case");
    private static final By ROWS = By.cssSelector(".data-table tbody tr");
    private static final By METRICS = By.cssSelector(".metric");
    private static final By EMPTY = By.cssSelector(".empty-state");

    public DashboardPage(TestContext context) {
        super(context);
    }

    public DashboardPage open() {
        go("/ui");
        waitForTitle("Case queue");
        return this;
    }

    public boolean isAt() {
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver(), java.time.Duration.ofSeconds(10))
                    .until(d -> currentPath().equals("/ui"));
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    public IntakePage newCase() {
        click(NEW_CASE);
        return new IntakePage(context);
    }

    public int caseRowCount() {
        return all(ROWS).size();
    }

    public int metricCount() {
        return all(METRICS).size();
    }

    public boolean emptyStateShown() {
        return !all(EMPTY).isEmpty();
    }

    /** Opens a case by clicking its row link containing the given id fragment. */
    public InvestigationPage openCase(String caseIdFragment) {
        List<WebElement> links = all(By.cssSelector(".data-table tbody tr td a"));
        for (WebElement link : links) {
            if (link.getText().contains(caseIdFragment.substring(0, 8))) {
                link.click();
                return new InvestigationPage(context);
            }
        }
        throw new AssertionError("Case " + caseIdFragment + " not found on dashboard");
    }
}
