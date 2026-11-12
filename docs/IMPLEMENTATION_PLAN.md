# Multi-Session Implementation Plan

## How to use these files
Give `AGENTS.md` to every coding session. Add only the task-focused MD files listed below. Preserve the same repository between sessions. Each new session first inspects existing code/tests and continues rather than recreating the project.

## Session 1 - Foundation + MySQL
Read: `AGENTS.md`, `REQUIREMENTS.md`, `ARCHITECTURE.md`, `API_AND_DATA.md`, `TEST_PLAN.md`.
Implement domain/enums/DTO validation, Spring Boot structure, JPA entities/repositories, MySQL driver/config, Flyway baseline, state transitions and health. Do not implement AI/RAG/UI. Exit: project starts against MySQL and migrations/tests pass.

### Prompt
```text
Continue/build the Payment Fraud capstone. Follow AGENTS.md as mandatory.
Read REQUIREMENTS.md, ARCHITECTURE.md, API_AND_DATA.md and TEST_PLAN.md.
Implement Session 1 only: Spring Boot foundation, domain/enums, DTO validation, state machine, JPA entities/repositories, MySQL 8.x-compatible datasource, Flyway baseline migrations, safe example configuration and health checks.
The target VM already has MySQL. Read DB_URL, DB_USERNAME and DB_PASSWORD from environment; never commit credentials. Configure JPA schema validation and Flyway migrations; do not use ddl-auto=create/update for the final runtime path.
Do not implement AI, RAG or UI yet. Add tests, run build/tests, and report files changed, test results and remaining Session 2 work.
```

## Session 2 - Deterministic Core
Read: `AGENTS.md`, `REQUIREMENTS.md`, `ARCHITECTURE.md`, `TEST_PLAN.md`.
Implement `FraudCaseProcessor`, rules processor, stable fixtures, 4+ evidence tools, trace/audit and safe failures. Exit: rules path works with model disabled.

### Prompt
```text
Follow AGENTS.md. Inspect the existing repository first.
Implement only the deterministic investigation core defined in REQUIREMENTS.md and ARCHITECTURE.md: FraudCaseProcessor, RulesFraudCaseAgent, deterministic fixture generator, approved evidence tools, evidence provenance, ToolTrace and append-only audit events. Handle timeout/not-found as structured unavailable results.
Persist through the existing MySQL/JPA model and Flyway schema. Do not add an LLM yet. Add unit/tool-contract/repository tests and run them.
```

## Session 3 - API + Orchestration
Read: `AGENTS.md`, `ARCHITECTURE.md`, `API_AND_DATA.md`, `TEST_PLAN.md`.
Implement the required endpoints, `FraudCaseService`, `InvestigationOrchestrator`, state enforcement and integration tests.

### Prompt
```text
Follow AGENTS.md and implement Session 3 only. Read ARCHITECTURE.md, API_AND_DATA.md and TEST_PLAN.md.
Implement all required case/investigation/evidence/trace/recommendation/citation/message/review APIs and application orchestration. Controllers must call application services, never model/tool adapters directly. Preserve state-machine rules and transactional MySQL persistence. Continue in rules mode. Add integration/API tests and run the full existing suite.
```

## Session 4 - Policy RAG
Read: `AGENTS.md`, `RAG_POLICY.md`, `TEST_PLAN.md`, `SECURITY_RESPONSIBLE_AI.md`.
Implement ingestion, chunking, metadata, embeddings/vector abstraction, retrieval, threshold/version filtering, citations and abstention.

### Prompt
```text
Follow AGENTS.md. Read RAG_POLICY.md, TEST_PLAN.md and SECURITY_RESPONSIBLE_AI.md.
Implement policy ingestion/retrieval with exact citation metadata. Baseline top-k=4 and threshold=0.72 must remain configurable. If no approved passage meets threshold, abstain and drive MANUAL_REVIEW; never invent procedure. Persist citation/result metadata needed for audit in MySQL. Add retrieval/no-match/version/injection tests and run them.
```

## Session 5 - AI Adapter
Read: `AGENTS.md`, `REQUIREMENTS.md`, `ARCHITECTURE.md`, `API_AND_DATA.md`, `RAG_POLICY.md`, `SECURITY_RESPONSIBLE_AI.md`.
Implement one AI adapter, preferably Spring AI for a Spring Boot-first solution, without changing domain contracts.

### Prompt
```text
Follow AGENTS.md. Implement a Spring AI FraudCaseProcessor adapter behind the existing framework-neutral interface. Integrate only approved tool evidence, PolicyRetriever, case context, prompt resources and typed FraudCaseRecommendation mapping. The model never controls state transitions. Validate schema before MySQL persistence and always enforce humanReviewRequired=true. Implement safe model-unavailable and malformed-output behavior. Never expose chain-of-thought. Add adapter tests and run all tests.
```

## Session 6 - Modern UI + Memory
Read: `AGENTS.md`, `API_AND_DATA.md`, `UI_IMPLEMENTATION.md`, `SECURITY_RESPONSIBLE_AI.md`, `TEST_PLAN.md`.
This is the primary UI session.

### Prompt
```text
Follow AGENTS.md. Read UI_IMPLEMENTATION.md carefully before coding.
Implement case-scoped bounded conversation memory first and prove Case A cannot leak into Case B.
Then build a polished modern Thymeleaf analyst UI for all six required capabilities: Case Intake, Investigation/Tool Execution, Recommendation, Evidence, Policy Citations and Human Review.
Use the design system in UI_IMPLEMENTATION.md: banking-style light neutral canvas, navy/slate typography, teal/blue accent, reusable cards/badges/timeline/tables, 8px spacing rhythm, consistent rounded surfaces, subtle shadows, clear focus states and responsive layouts. No default scaffolded CRUD appearance and no external CDN runtime dependency.
Create reusable Thymeleaf fragments and CSS design tokens. Ensure provenance, advisory recommendation labeling, mandatory review rationale, loading/empty/partial/timeout states, keyboard accessibility and responsive use at 1366x768 and around 768px width. Connect pages to existing APIs/application services and MySQL-backed data. Add UI/controller tests and run the suite.
```

## Session 7 - VM/MySQL + Security + Hardening
Read: `AGENTS.md`, `TEST_PLAN.md`, `SECURITY_RESPONSIBLE_AI.md`, `UI_IMPLEMENTATION.md`, `README.md`.
Verify MySQL, security, UI behavior and T01-T12.

### Prompt
```text
Perform production-like capstone hardening without adding autonomous actions. Follow AGENTS.md, TEST_PLAN.md and SECURITY_RESPONSIBLE_AI.md.
Verify Flyway from a clean MySQL schema, restart persistence, indexes/FKs/decimal/timestamp mapping and environment-only credentials. Execute T01-T12. Verify CSRF, encoding, prompt injection, authorization, masking, secret scanning, append-only audit behavior, correlation IDs and safe logging.
Review all six UI screens against UI_IMPLEMENTATION.md at 1366x768 and ~768px responsive width; fix layout/accessibility issues, missing states and inconsistent design components. Do not weaken tests. Update README VM run instructions.
```

## Session 8 - Final Review
Read ALL MD files. Inspect actual code first and create a requirement -> implementation -> test gap matrix. Fix only verified gaps and produce `CAPSTONE_REVIEW.md`.

### Prompt
```text
Act as final technical reviewer. Read every project MD file, then inspect actual code/config/migrations/tests/UI before making changes.
Verify: clean VM/MySQL setup, rules mode, AI mode, deterministic evidence, 6+ cases, 4+ tools, RAG citations/abstention, structured-output validation, case memory isolation, human review/audit, six polished UI capabilities, responsive/accessibility behavior, no remote UI dependency needed at runtime, injection protection, no secrets and no autonomous actions.
Produce a gap matrix. Fix only verified gaps; run full build/tests; create CAPSTONE_REVIEW.md with coverage, test evidence, known limitations, residual risks and demo readiness.
```

## Common footer for every agent session
```text
WORKING RULES
1. Follow AGENTS.md.
2. Inspect current implementation before editing.
3. Do not change unrelated code.
4. Preserve framework-neutral domain contracts.
5. Use synthetic/masked data only.
6. Never fabricate evidence/policy or implement autonomous financial/customer actions.
7. MySQL is the final VM database; never commit credentials.
8. Add/update tests for every change.
9. Run relevant existing tests; never weaken assertions to pass.
10. Finish with implemented items, files changed, tests/results, assumptions and remaining gaps.
```
