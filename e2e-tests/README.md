# Fraud Investigation E2E Suite (Selenium + Cucumber BDD + POM)

End-to-end browser tests for the fraud investigation UI, written in Java with
Selenium WebDriver, Cucumber BDD feature files and the Page Object Model.

## How it works

- `AppLauncher` boots the packaged app jar (`../target/payment-fraud-investigation-*.jar`)
  on a random port once per run, against the throwaway `fraud_capstone_test` MySQL
  schema, with test-only `it-analyst` / `it-reviewer` / `it-lead` accounts and
  `FRAUD_AI_ENABLED=false` (deterministic rules processor).
- Between scenarios, case-scoped tables are wiped via JDBC; the seeded policy
  corpus is kept so citation scenarios are meaningful.
- Selenium Manager resolves chromedriver for the installed Chrome automatically
  (first run needs network; override offline with `-Dwebdriver.chrome.driver=`).
- A screenshot is captured after **every step** (synced via the `AfterStep` hook and
  the `StepTracker` event listener): embedded in the Extent report under each step
  node and saved to `target/e2e-screenshots/<scenario>/NN-<step-text>.png`. Failures
  get an extra final-state capture. The Extent report lands in
  `target/extent-report <timestamp>/target/extent-report/ExtentReport.html`; a plain
  Cucumber report lands in `target/cucumber-report.html`.

## Prerequisites

```bash
# 1. Package the app
mvn -DskipTests package

# 2. Provide DB credentials (never committed)
source ~/.config/fraud-capstone/db.env
```

## Run

```bash
cd e2e-tests
mvn test                              # whole suite, headless Chrome
mvn test -Dheadless=false             # watch the browser
mvn test -Dcucumber.filter.tags=@t01  # a single TEST_PLAN scenario
mvn test -Dcucumber.filter.tags=@review
```

Tags: `@security`, `@intake`, `@investigation`, `@recommendation`, `@evidence`,
`@citations`, `@review`, `@conversation`, `@testplan` (`@t01`..`@t12`).

## Layout

```
src/test/java/com/capstone/fraudcase/e2e/
  config/    AppLauncher, DriverFactory, TestContext
  hooks/     Hooks (app start/stop, DB wipe, screenshots)
  pages/     Page objects (one per screen, locators from template ids)
  steps/     Cucumber step definitions
  runners/   RunCucumberIT (JUnit Platform suite)
src/test/resources/features/   Gherkin scenarios, incl. T01–T12 matrix
```
