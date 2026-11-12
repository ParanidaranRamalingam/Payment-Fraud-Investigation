package com.capstone.fraudcase.e2e.config;

import org.openqa.selenium.WebDriver;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scenario shared state, injected into step definitions by Cucumber's
 * PicoContainer. Holds the driver, the application base URL and handles
 * created during the scenario (e.g. the current case id).
 */
public class TestContext {

    public static final String ANALYST = "it-analyst";
    public static final String ANALYST_PW = "analyst-test-pw";
    public static final String REVIEWER = "it-reviewer";
    public static final String REVIEWER_PW = "reviewer-test-pw";
    public static final String LEAD = "it-lead";
    public static final String LEAD_PW = "analyst-test-pw";

    private final WebDriver driver;
    private final String baseUrl;
    private final Map<String, Object> data = new HashMap<>();

    public TestContext() {
        this.baseUrl = "http://localhost:" + AppLauncher.startIfNeeded();
        this.driver = DriverFactory.create();
    }

    public WebDriver driver() {
        return driver;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public String caseId() {
        return get("caseId");
    }

    public void caseId(String id) {
        put("caseId", id);
    }

    public String namedCaseId(String key) {
        return get(key);
    }
}
