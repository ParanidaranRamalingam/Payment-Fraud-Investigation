# Capstone Final Technical Review

Payment Fraud Investigation & Dispute Resolution assistant — final review against
`docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/API_AND_DATA.md`, `docs/RAG_POLICY.md`,
`docs/SECURITY_RESPONSIBLE_AI.md`, `docs/UI_IMPLEMENTATION.md` and `docs/TEST_PLAN.md`.

Review date: 2026-11-12. Reviewer: final technical review session (Session 8).

---

## 1. Implementation summary

Spring Boot 4.1.2 / Java 21 application implementing an advisory-only fraud triage pipeline:

- **Deterministic evidence core** — `tools/` allow-lists six evidence tools
  (`getTransactionDetails`, `getDeviceSignals`, `compareRecentLocations`, `getMerchantRiskProfile`,
  `getPriorDisputes`, `calculateCaseSla`) behind a single `ToolInvoker` enforcing allow-list,
  per-call timeout and contract validation. Synthetic fixtures are seed-derived from identifiers
  only — identical inputs give byte-identical results and identical `content_hash` values.
- **Framework-neutral processor contract** — `FraudCaseProcessor.analyze(FraudCaseContext)` with two
  implementations: `RulesFraudCaseAgent` (default; ordered deterministic rule table, `rules-v1`) and
  `SpringAiFraudCaseAgent` (opt-in via `fraud.ai.enabled=true`, Spring AI 2.0 + Anthropic).
- **Policy RAG** — `knowledge/` ingestion (validate → section-aware chunk → embed → upsert),
  `LexicalHashEmbeddingModel` + `JpaVectorStore` defaults behind `EmbeddingModel`/`VectorStore`
  abstractions; `PolicyRetriever` applies Top-K, similarity threshold and effective-version
  filtering with per-candidate drop reasons and explicit abstention codes.
- **Case-scoped conversation memory** — `memory/CaseConversationMemory`, bounded window
  (`fraud.memory.window`, default 20, max 100), redacted, strictly case-isolated.
- **Application layer** — `FraudCaseService` + `InvestigationOrchestrator`; controllers never touch
  tools/model. `RecommendationValidator` re-checks every recommendation before persistence/display.
- **Persistence** — Spring Data JPA + MySQL 8, Flyway migrations V1–V4, `ddl-auto=validate`,
  optimistic locking on `fraud_case`, append-only audit enforced at the application layer.
- **Security** — stateless HTTP Basic for `/api/**`, session-backed form login for `/ui/**`,
  bcrypt-only env-configured accounts (fail closed with none), role split ANALYST/REVIEWER,
  session-backed CSRF on UI posts, output encoding via Thymeleaf, `SensitiveDataGuard` masking.
- **UI** — Thymeleaf analyst console: dashboard, intake, investigation, recommendation, evidence,
  policy citations, human review + login/error pages; token-based local CSS, no CDN, minimal
  progressive JS; responsive breakpoints at 900px/768px; skip link, focus rings, labelled forms.

## 2. Architecture summary

```
Browser/Client → Security (Basic | form login) → FraudCaseController | CaseUiController
  → FraudCaseService → InvestigationOrchestrator
      → ToolInvoker (allow-list + timeout + contract check) → synthetic EvidenceToolProviders
      → PolicyRetriever (Top-K, threshold, effective version, abstention)
      → FraudCaseProcessor (RULES default | SpringAiFraudCaseAgent when fraud.ai.enabled)
      → RecommendationValidator → JPA/MySQL → AuditTrail (append-only) → Human review
```

State machine: `NEW → ENRICHING → READY_FOR_REVIEW|INCOMPLETE → UNDER_REVIEW → ROUTED|CLOSED`,
with `INCOMPLETE → ENRICHING|CLOSED` and `ROUTED → CLOSED` (73-transition test coverage).
The model never transitions state; every safety failure resolves to `INCOMPLETE`/manual review.

## 3. Requirement coverage matrix

| Requirement | Implementation | Evidence / file | Test coverage | Status | Remaining gap | Priority |
|---|---|---|---|---|---|---|
| Clean build | `mvn clean verify` green | `pom.xml` | 829 tests, BUILD SUCCESS | PASS | — | — |
| Clean startup | Booted packaged jar against MySQL; health UP | `FraudCaseApplication`, `/actuator/health` | `SchemaMigrationHealthIndicatorTest`, live boot verified | PASS | — | — |
| MySQL datasource env vars | `DB_URL/DB_USERNAME/DB_PASSWORD` placeholders only | `application.yml`, README §2 | `MySqlRepositoryTest` family | PASS | — | — |
| Flyway clean-schema migration | V1–V4 apply cleanly from empty schema; `clean-disabled` in prod | `db/migration/V1–V4` | `SchemaMigrationMySqlTest`, live `flyway_schema_history` shows 4 applied | PASS | — | — |
| Persistence across restart | Runtime schema retains cases/reviews across app restarts | `fraud_case` rows predate latest boot | `CaseRepositoriesMySqlTest`; verified live (5 cases, 96 audit rows) | PASS | — | — |
| Schema validation | `ddl-auto=validate`, enum/string/decimal/DATETIME(6) mapping | `application.yml` | `SchemaMigrationMySqlTest`, `EntityInvariantsTest`, `OptimisticLockingMySqlTest` | PASS | — | — |
| Rules mode without model | `RulesFraudCaseAgent` sole processor when `fraud.ai.enabled=false` (default) | `agent/rules/`, `FraudAiProperties` | `RulesFraudCaseAgentTest`, `InvestigationMySqlTest` | PASS | Default was `true` in `application.yml` — **fixed** to `false` to match docs/properties | Done |
| ≥4 evidence tools | 6 tools implemented + registered check | `ToolInvoker.ALLOW_LIST`, `tools/synthetic`, `tools/sla` | `EvidenceToolContractTest`, `ToolInvokerTest` | PASS | — | — |
| Determinism | Seed-derived fixtures; equal inputs → equal output | `DeterministicSeed`, `SyntheticFixtureGenerator` | `DeterministicSeedTest`, `SyntheticFixtureGeneratorTest` (43), `t10_evidenceAndRecommendations_isolatedPerCase` | PASS | — | — |
| Provenance | `source` + canonical payload + SHA-256 `content_hash`; payload only on AVAILABLE | `EvidenceItem`, V2 CHECK constraints | `EvidenceItemProvenanceTest`, `v2CheckConstraint…` | PASS | — | — |
| Safe tool failures | TIMEOUT/NOT_FOUND/ERROR/REJECTED/CONTRACT_VIOLATION → structured unavailable evidence; never throws, never fabricates | `ToolInvoker`, `ToolFailure` | `ToolInvokerTest`, `t08_*` tests | PASS | — | — |
| AI structured output | `ModelAssessment` via `ChatClient .call().entity()` | `agent/springai/ModelAssessment` | `SpringAiFraudCaseAgentTest` (10) | PASS | — | — |
| Provider failure handling | No model / provider error / timeout → manual-review fallback, never thrown | `SpringAiFraudCaseAgent.analyze/callModel` | adapter tests incl. timeout stub | PASS | — | — |
| Invalid response handling | Unparseable/invalid output rejected, re-validated, fallback | `toRecommendation` + `RecommendationValidator` | adapter + validator tests | PASS | — | — |
| `humanReviewRequired=true` | Forced in rules, AI mapping and DB CHECK | `FraudCaseRecommendation`, V1 CHECK | `FraudCaseRecommendationTest`, DB CHECK tests | PASS | — | — |
| RAG policy metadata | documentId/title/version/effectiveDate/section/owner/classification/chunk | `PolicyDocument`, `PolicyChunkEntity` | `PolicyDocumentValidationTest`, `PolicyChunkerTest` | PASS | — | — |
| RAG citations | Exact chunk citations persisted + API/UI exposure | `policy_citation`, `PolicyCitation` | `PolicyRetrieverTest`, `PolicyRetrievalMySqlTest` | PASS | — | — |
| Top-K / threshold | Configurable `fraud.policy.top-k` (4) / `similarity-threshold` (0.72) | `PolicyProperties`, `PolicyRetriever` | `PolicyRetrieverTest` (14) | PASS | — | — |
| Version handling | Latest APPROVED `effectiveDate ≤ asOf` per document; stale/retired dropped | `PolicyRetriever.verdict` | retriever + MySQL tests | PASS | — | — |
| Abstention | `NO_MATCH/BELOW_THRESHOLD/NO_EFFECTIVE_VERSION/STORE_ERROR` → MANUAL_REVIEW + missing-info | `RetrievalOutcome`, orchestrator | abstention tests (T05) | PASS | — | — |
| Memory: case isolation | `case_message` keyed by caseId; replies cite same-case evidence only | `CaseConversationMemory`, V3 | `t10_caseIsolation_caseBMustNotRecallCaseA` | PASS | — | — |
| Memory: bounded retention | Configurable window (default 20, max 100) | `MemoryProperties` | `window_*` tests | PASS | — | — |
| UI six workflows | intake, investigation, recommendation, evidence, citations, review (+dashboard) | `CaseUiController`, `templates/` | `CaseUiControllerTest` (14) | PASS | — | — |
| Polished modern design | Token CSS, cards/badges/timeline/meter, banking aesthetic | `static/css/app.css` (687 lines), fragments | manual + controller tests | PASS | — | — |
| Loading/error/empty states | `data-loading` buttons, `alert`/`empty-state` components, error page | `app.js`, `components.html`, `error.html` | UI tests | PASS | — | — |
| RAG abstention / model-unavailable UI | Empty-state + advisory labels; mode chip shows RULES/AI | `case-citations.html`, `layout.html` | UI tests | PASS | — | — |
| Responsive 1366×768 / ~768px | Desktop-first grid, breakpoints 900px/768px, no horizontal overflow | `app.css` media queries | manual review | PASS | — | — |
| Keyboard accessibility | Skip link, `:focus-visible` ring, semantic landmarks, labelled controls | `app.css`, templates | manual review | PASS | — | — |
| No external CDN | All CSS/JS/icons local; only external refs are SVG xmlns namespaces | `layout.html` head | grep-verified | PASS | — | — |
| Advisory recommendation | Marked advisory in UI + contract; never authoritative | `case-recommendation.html`, review copy | UI tests | PASS | — | — |
| Mandatory reviewer rationale | ≥10 chars at validation + DB CHECK; override needs finals+rationale | `ReviewRequest`/`ReviewForm`, V1 CHECK | `ReviewRequestValidationTest`, `t12_*` | PASS | — | — |
| Audit trail | Append-only `audit_event`, actor/timestamp/correlation ID; no update path | `AuditTrail`, `AuditEventEntity` | `AuditTrailTest`, `AuditEventRepositoryMySqlTest` | PASS | — | — |
| Masking | PAN detection/rejection, masked/tokenized refs only | `SensitiveDataGuard`, validators | `SensitiveDataGuardTest` (32), API masking tests | PASS | — | — |
| Encoding | Thymeleaf output encoding; unknown JSON fields rejected | `fail-on-unknown-properties` | `unknownField_rejectedAsMalformedRequest` | PASS | — | — |
| Authorization | ANALYST writes, REVIEWER reviews, both read; fail closed | `SecurityConfiguration` | `FraudCaseApiMySqlTest` role tests | PASS | — | — |
| Prompt injection | Statements/policy treated as data; assistant refuses actions | `RulesCaseAssistant`, prompt templates | `t11_*` tests | PASS | — | — |
| Safe logging | Redaction on tool summaries/audit; health details `never` | `SensitiveDataGuard.redact`, config | `ToolInvokerTest` redaction, health-indicator test | PASS | — | — |
| No secrets committed | Env-only credentials; only throwaway test bcrypts in `application-test.yml` | `.gitignore`, config | grep-verified | PASS | — | — |
| MySQL integration tests | 7 `@MySqlRepositoryTest` classes + API end-to-end, gated on `DB_TEST_URL` | `persistence/*MySqlTest`, `FraudCaseApiMySqlTest` | ran green | PASS | — | — |
| T01–T12 scenarios | Named tests t01–t12 across rules/retriever/memory/API layers | see §4 | all green | PASS | — | — |
| No autonomous actions | Advisory-only; review dispositions only record decisions | domain + docs | pass conditions | PASS | — | — |
| No fabricated evidence/policy | Failures return structured "no data was inferred"; abstention never invents | `ToolInvoker`, `PolicyRetriever` | contract + abstention tests | PASS | — | — |

## 4. Test results

Final run: `mvn clean verify` with operator env sourced and `DB_TEST_URL` exported:

```
Tests run: 829, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS (1m24s)
```

Prior run had **17 failures** in `FraudCaseApiMySqlTest` (401 on every authenticated call).
Root cause: OS env vars `FRAUD_SECURITY_ACCOUNTS_0..2` (operator accounts analyst1/reviewer1/lead1)
override `application-test.yml`, so the `it-*` test accounts never bound. Fixed by pinning the
accounts via `@SpringBootTest(properties=…)` — inlined test properties outrank OS env vars.
All 19 tests now pass.

T01–T12 mapping: T01 `t01_fraudScenario…` (rules); T02 `t02_merchantDispute…`; T03
`duplicateClaimed_singlePosting…` / `duplicatePosting_withStrongFraudSignals…`; T04
`t04_missingTransaction…` + `t04_caseWithoutClaim…`; T05 retriever abstention tests
(`PolicyRetrieverTest`, `PolicyRetrievalMySqlTest`); T06 `t06_contradictorySignals…`; T07
adapter no-model/provider-failure tests; T08 `t08_toolTimeout…`, `t08_unavailableSignalTools…`,
`t08_toolTimeout_persisted…`; T09 `RecommendationValidatorTest` + adapter invalid-output tests;
T10 `t10_caseIsolation_caseBMustNotRecallCaseA`, `t10_evidenceAndRecommendations_isolatedPerCase`;
T11 `t11_promptInjectionInStatement…`, `t11_injectionMessageTreatedAsData…`; T12
`t12_overrideWithoutFinalFieldsOrRationale_returns400`.

## 5. MySQL/VM readiness

- MySQL 8.0.46 running locally; `fraud_capstone` + `fraud_capstone_test` schemas exist with
  least-privilege `fraud_app` grants; credentials live in `~/.config/fraud-capstone/db.env` (mode 600,
  outside the repo).
- `flyway_schema_history` on the runtime schema shows V1–V4 applied successfully; the app booted
  with `ddl-auto=validate` with no schema mismatch.
- Restart persistence verified: 5 cases / 5 recommendations / 96 audit events / 2 reviews persisted
  from earlier runs and were read by a fresh process boot.
- Test schema is cleaned + re-migrated on every test run; `DB_TEST_URL` gating refuses non-`*_test`
  schemas, protecting the runtime schema.
- Health groups: `/actuator/health/readiness` includes `db` + `schemaMigrations` (verified UP).

## 6. UI readiness

- All six required workflows plus a dashboard are implemented with a coherent token-based design
  system (light neutral canvas, navy/slate text, teal accent, cards, badges, timeline, confidence
  meter, citation panel, audit timeline).
- Accessibility: skip link, semantic landmarks, labelled controls, visible focus ring,
  non-color-only statuses (badge text + icon), `prefers-reduced-motion` support.
- Responsive: breakpoints at 900px and 768px collapse the sidebar to a strip and stack grids;
  content max-width keeps 1366×768 density comfortable.
- States: loading (`data-loading` progressive JS only — forms work without JS), empty-state
  components, inline/global validation errors, unavailable/timeout badges on evidence and
  citations, abstention messaging on citations page, reviewer-role read-only state on review page.
- No runtime CDN/internet dependency: single local stylesheet, 28-line JS, inline SVG favicon,
  Unicode icons.
- Session-backed CSRF tokens on all UI form posts; `/api/**` remains CSRF-exempt and stateless.

## 7. Security findings

- **Fixed**: `fraud.ai.enabled` defaulted to `true` in `application.yml` while docs, the
  properties record and the example file all define rules-only as the default. Changed default to
  `false` (AI remains opt-in via `FRAUD_AI_ENABLED=true`).
- **Fixed**: README/application-example documented `claude-sonnet-4-5` while config defaults to
  `global.anthropic.claude-sonnet-4-6` — docs updated to match.
- No committed secrets. Test bcrypts are throwaway-only and documented as such.
- `spring.ai.anthropic.api-key` is env-only; with the Anthropic starter present, an absent key does
  not block startup — the adapter's `ObjectProvider<ChatModel>`/failure path degrades to manual review.
- Auth fails closed with zero configured accounts; bcrypt-hash enforcement rejects plaintext at bind.
- Audit events are immutable at the application layer (no update/delete repository path; removal
  callback rejects). No DB triggers are used (documented: binlog SUPER restriction).
- Correlation-ID filter validates the header pattern and regenerates invalid values; IDs land on
  audit rows and tool traces.
- Error surface is minimal (`include-message/binding-errors/stacktrace` off); API errors use a
  stable envelope.

## 8. Known limitations

- Synthetic fixture data only (by design); no real payment-rail integration.
- `JpaVectorStore` does a full-scan cosine over `policy_chunk` — fine at corpus scale, not an ANN.
- Embeddings stored as comma-separated floats in `TEXT` (documented decision).
- `listCases()` is capped at 200 newest rows — no pagination beyond that bound.
- AI mode depends on the Tekstac LLM gateway (`FRAUD_AI_BASE_URL`) or an Anthropic-compatible
  endpoint; network egress is required for AI mode only (rules/UI work fully offline).
- Append-only audit is enforced in the application layer, not by DB triggers (documented reason).
- HTTP Basic for the API is acceptable for the capstone; production would want stronger auth.
- An in-flight instance (port 8081) predates this review; restart it to pick up the new jar/default
  rules-mode setting if AI mode was previously implicit.

## 9. Residual risks

- UI polish/responsiveness was verified by code inspection and controller tests, not a rendered
  browser walkthrough — a quick manual pass at 1366×768 and 768px is still recommended before demo.
- Prompt-injection resilience of the AI adapter is bounded by the model itself; the deterministic
  controls (citation intersection, forced fields, output validation) mitigate but cannot eliminate
  model-level leakage. Rules mode has no such exposure.
- `FRAUD_SECURITY_ACCOUNTS_*` env values silently override any file-based account config (standard
  Spring precedence) — now covered for tests, but operators should treat env as the single source.
- Anthropic starter presence means an empty `FRAUD_AI_API_KEY` + `FRAUD_AI_ENABLED=true` produces
  manual-review fallbacks on every case (safe but visibly degraded) — the mode chip shows `AI`,
  which is honest but worth noting in a demo.

## 10. Demo readiness

- `mvn clean verify` green (829 tests) with the documented env workflow.
- Packaged jar boots cleanly against VM MySQL; readiness health UP; `/ui` redirects to login;
  unauthenticated API calls return the 401 envelope.
- Default mode is deterministic rules (no model needed); enabling AI requires
  `FRAUD_AI_ENABLED=true` + `FRAUD_AI_API_KEY`.
- Pre-seeded runtime data (5 cases incl. reviews) plus 9 synthetic intake prefixes
  (`txn_fraud`, `txn_dispute`, `txn_dup`, `txn_missing`, `txn_conflict`, `txn_genuine`,
  `txn_timeout`, `txn_unavail`, `txn_partial`) give deterministic demo paths for every scenario
  in T01–T12.
- Demo checklist suggestion: sign in as `analyst1` → intake a `txn_fraud` case → investigation →
  recommendation → evidence/citations → sign in as `reviewer1` → accept/override with rationale →
  show audit timeline.

## 11. Final assessment

**PASS.**

All mandatory requirements are implemented and verified against code, configuration, migrations,
live MySQL and the full test suite. Two verified gaps were found and fixed during this review —
the non-hermetic security-account binding in `FraudCaseApiMySqlTest` and the `fraud.ai.enabled`
default contradicting the documented rules-first contract — plus a documentation model-name drift.
No remaining blocking gaps; residual risks are documented limitations rather than defects.
