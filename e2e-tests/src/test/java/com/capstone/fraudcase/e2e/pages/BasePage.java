package com.capstone.fraudcase.e2e.pages;

import com.capstone.fraudcase.e2e.config.TestContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** Base class for all page objects: navigation, waits and shared UI elements. */
public abstract class BasePage {

    protected static final Duration WAIT = Duration.ofSeconds(15);

    protected final TestContext context;

    protected BasePage(TestContext context) {
        this.context = context;
    }

    protected WebDriver driver() {
        return context.driver();
    }

    protected String url(String path) {
        return context.baseUrl() + path;
    }

    protected void go(String path) {
        driver().get(url(path));
    }

    /** Case GETs occasionally race the creating transaction's visibility; reload a few times on 404. */
    protected void goCasePage(String path) {
        go(path);
        for (int i = 0; i < 4 && pageText().contains("Case not found"); i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            go(path);
        }
        waitVisible(By.cssSelector("main.app-main"));
    }

    /** Waits for the page's main heading, guarding against reads before render. */
    protected void waitForTitle(String expected) {
        new WebDriverWait(driver(), WAIT).until(d -> {
            List<WebElement> h1 = d.findElements(By.cssSelector("h1.page-title, h1.login-title"));
            return !h1.isEmpty() && h1.getFirst().getText().contains(expected);
        });
    }

    /** Waits until the browser lands on a /ui/cases/{id} URL and returns the id, or null on timeout. */
    public String waitForCaseId() {
        try {
            new WebDriverWait(driver(), WAIT).until(d ->
                    currentPath().matches("/ui/cases/[0-9a-fA-F-]{8,}.*"));
        } catch (org.openqa.selenium.TimeoutException e) {
            return null;
        }
        String path = currentPath();
        String rest = path.substring("/ui/cases/".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    protected WebElement waitVisible(By locator) {
        return new WebDriverWait(driver(), WAIT).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement waitClickable(By locator) {
        return new WebDriverWait(driver(), WAIT).until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected List<WebElement> all(By locator) {
        return driver().findElements(locator);
    }

    protected void type(By locator, String value) {
        WebElement el = waitVisible(locator);
        el.clear();
        el.sendKeys(value);
    }

    protected void click(By locator) {
        waitClickable(locator).click();
    }

    protected String pageText() {
        return driver().findElement(By.tagName("body")).getText();
    }

    public String flashNotice() {
        try {
            return new WebDriverWait(driver(), Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".alert--ok")))
                    .getText();
        } catch (org.openqa.selenium.TimeoutException e) {
            return null;
        }
    }

    public String flashError() {
        List<WebElement> el = all(By.cssSelector(".alert--danger"));
        return el.isEmpty() ? null : el.getFirst().getText();
    }

    public String currentPath() {
        return java.net.URI.create(driver().getCurrentUrl()).getPath();
    }

    public String title() {
        return driver().getTitle();
    }

    /** Case summary strip shown on every case page. */
    public String caseStatusBadge() {
        return waitVisible(By.cssSelector(".case-strip__item .badge")).getText().trim();
    }

    public void signOut() {
        click(By.cssSelector(".app-header form button.logout-btn"));
    }
}
