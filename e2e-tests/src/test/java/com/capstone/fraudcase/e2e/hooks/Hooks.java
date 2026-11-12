package com.capstone.fraudcase.e2e.hooks;

import com.capstone.fraudcase.e2e.config.AppLauncher;
import com.capstone.fraudcase.e2e.config.TestContext;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

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

    @After
    public void tearDown(Scenario scenario) {
        if (context.driver() == null) {
            return;
        }
        try {
            if (scenario.isFailed() && context.driver() instanceof TakesScreenshot ts) {
                byte[] png = ts.getScreenshotAs(OutputType.BYTES);
                scenario.attach(png, "image/png", scenario.getName());
                Path dir = Path.of("target", "e2e-screenshots");
                Files.createDirectories(dir);
                Files.write(dir.resolve(scenario.getName().replaceAll("[^A-Za-z0-9_-]", "_") + ".png"), png);
            }
        } catch (IOException ignored) {
        } finally {
            context.driver().quit();
        }
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
