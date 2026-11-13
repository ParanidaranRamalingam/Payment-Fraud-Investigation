package com.capstone.fraudcase.e2e.hooks;

import com.capstone.fraudcase.e2e.config.AppLauncher;
import com.capstone.fraudcase.e2e.config.TestContext;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

public class Hooks {

    /** Case-scoped tables wiped between scenarios; policy corpus (seeded at boot) is kept. */
    private static final List<String> CASE_TABLES = List.of(
            "case_message_evidence", "case_message",
            "recommendation_evidence", "recommendation_missing_info", "recommendation_trace_ref",
            "policy_citation", "policy_retrieval_hit", "policy_retrieval_trace",
            "recommendation", "review_disposition", "evidence_item", "tool_trace",
            "transaction_claim", "audit_event", "fraud_case");

    private final TestContext context;
    private int stepIndex;

    public Hooks(TestContext context) {
        this.context = context;
    }

    @BeforeAll
    public static void startApp() {
        AppLauncher.startIfNeeded();
    }

    @AfterAll
    public static void stopApp() {
        AppLauncher.stop();
    }

    @Before
    public void resetStepIndex() {
        stepIndex = 0;
    }

    @Before
    public void wipeCaseData() throws Exception {
        String url = env("DB_TEST_URL",
                "jdbc:mysql://localhost:3306/fraud_capstone_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        try (Connection c = DriverManager.getConnection(url, env("DB_USERNAME", "fraud_app"),
                System.getenv("DB_PASSWORD"));
             Statement s = c.createStatement()) {
            s.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : CASE_TABLES) {
                s.executeUpdate("DELETE FROM " + table);
            }
            s.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    /** Captures a screenshot after every step, named after the step that just ran. */
    @AfterStep
    public void captureStep(Scenario scenario) {
        WebDriver driver = context.driver();
        if (driver == null || !(driver instanceof TakesScreenshot ts)) {
            return;
        }
        try {
            byte[] png = ts.getScreenshotAs(OutputType.BYTES);
            String stepText = StepTracker.currentStep();
            scenario.attach(png, "image/png", stepText);
            Path dir = Path.of("target", "e2e-screenshots", slug(scenario.getName()));
            Files.createDirectories(dir);
            Files.write(dir.resolve(String.format("%02d-%s.png", ++stepIndex, slug(stepText))), png);
        } catch (Exception ignored) {
        }
    }

    @After
    public void tearDown(Scenario scenario) {
        if (context.driver() == null) {
            return;
        }
        try {
            if (scenario.isFailed() && context.driver() instanceof TakesScreenshot ts) {
                scenario.attach(ts.getScreenshotAs(OutputType.BYTES), "image/png", "FAILURE: " + scenario.getName());
            }
        } catch (Exception ignored) {
        } finally {
            context.driver().quit();
        }
    }

    private static String slug(String text) {
        String s = text.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_");
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
