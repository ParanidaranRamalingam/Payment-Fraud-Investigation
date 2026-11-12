# Agent Implementation Rules

## Role and safety
The AI is a payment-fraud/dispute triage assistant, not a final decision maker. Use only validated facts, typed tool results, approved policy passages and case-scoped context. Never fabricate evidence/policy/tool execution. Always set `humanReviewRequired=true`. Never implement autonomous financial, account-control, compliance or customer-contact actions.

## Architecture rules
Preserve the framework-neutral `FraudCaseProcessor`. Controllers do not directly call model/tool adapters. State transitions and authorization stay deterministic. Validate model output before persistence/display.

## Database rules
Final runtime target is a VM running MySQL. Use Spring Data JPA + MySQL JDBC + Flyway. Never rely on H2-specific behavior for final verification. Never commit DB passwords. `ddl-auto` must be `validate` (or `none` with equivalent migration discipline), not `create`/`update` for the final profile.

## UI rules
Build a modern professional analyst workspace using Thymeleaf plus a maintainable CSS system and minimal progressive JavaScript unless the repository explicitly chooses another approved front end. Do not ship raw/default Bootstrap-looking pages or generic CRUD tables.

Design language:
- clean banking/operations aesthetic; light neutral canvas, white/elevated cards, navy/slate text, teal/blue primary accent;
- persistent top header plus desktop sidebar or compact navigation;
- max-width content grid, consistent 8px spacing rhythm, 10-14px card radius, subtle borders/shadows;
- typography hierarchy with clear page title, section title, labels and secondary metadata;
- reusable components: status/risk badges, metric cards, evidence cards, timeline/stepper, data table, citation drawer/panel, skeleton/loading state, toast/inline alert, modal only when justified;
- status cannot rely on color alone; use text/icon/shape;
- responsive desktop-first layout that remains usable at tablet widths;
- visible focus, keyboard navigation, labelled controls, adequate contrast and semantic HTML;
- preserve provenance on every important item;
- never expose chain-of-thought.

Use CSS variables/design tokens. Avoid excessive animation; only subtle 150-250ms interaction transitions. Support loading, empty, partial, timeout, unavailable, success and validation-error states.

## Completion rule for every session
Run the relevant build/tests. Report what changed, tests executed/results, assumptions and remaining gaps. Do not weaken tests to get green results.

## Project facts (learned in Session 1)
- Base package `com.capstone.fraudcase`; Java 21, Spring Boot 4.1.2 (modular starters: `spring-boot-starter-flyway`, `-webmvc`, `-data-jpa-test`), Hibernate 7.
- Build + full verification: `. ~/.config/fraud-capstone/db.env && export DB_TEST_URL && mvn clean verify`. Credentials file lives outside the repo (mode 600); never copy it in.
- MySQL tests use `@MySqlRepositoryTest` (real MySQL, `fraud_capstone_test`, Flyway clean+migrate; refuses to clean a schema not ending in `_test`). Skipped when `DB_TEST_URL` is unset.
- Enum fields need `@Enumerated(STRING)` **and** `@JdbcTypeCode(SqlTypes.VARCHAR)`; otherwise Hibernate expects MySQL native `ENUM` and `validate` fails. `Instant` maps to `DATETIME(6)` via `hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`.
- MySQL CHECK violations (error 3819) surface as `UncategorizedSQLException`, not `DataIntegrityViolationException`; catch `DataAccessException`.
- No DB triggers: the app user lacks `SUPER` with binlog enabled. Append-only audit is enforced in the application layer.
- Entities use application-assigned UUIDs via `AssignedIdEntity` (implements `Persistable`) so `save()` does a plain INSERT.
