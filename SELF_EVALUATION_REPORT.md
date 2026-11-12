# Capstone Self-Evaluation Report

**Participant:** Paranidaran Ramalingam
**Evaluator:** Devin (senior Java AI solution architect / BFS fraud-domain reviewer / secure-code reviewer / test architect)
**Date:** 2026-11-12

## 1. Executive Summary
- Project: Intelligent Payment Fraud Investigation and Dispute Resolution Agent (`com.capstone:payment-fraud-investigation`)
- Primary framework: Spring AI 2.0.2 (`ChatClient`/`ChatModel`, Anthropic provider starter via a Tekstac LLM gateway)
- Secondary framework: None (LangChain4j is not implemented)
- Build status: PASS — `mvn clean verify` completed successfully (exit 0)
- Test status: PASS — 829 tests run, 0 failures, 0 errors, 0 skipped (MySQL-backed tests executed against a real `fraud_capstone_test` schema)
- Overall score: 92/100
- Percentage: 92%
- Readiness: Demo Ready
- Scoring model used: Spring-AI-only model. Metric 4 evaluated out of 20; metric 5 marked Optional Not Implemented. Total remains 100.

## 2. Repository and Technology Overview
| Area | Finding | Evidence |
|---|---|---|
| Business objective | Advisory-only payment-fraud investigation and dispute triage assistant; deterministic evidence + policy RAG + schema-validated recommendation; mandatory human review | `pom.xml` description, `README.md`, `docs/REQUIREMENTS.md` |
| Java / Spring Boot | Java 21, Spring Boot 4.1.2, Hibernate 7 | `pom.xml` lines 10, 21 |
| Build tool | Maven (no wrapper; `mvn` used) | `pom.xml` |
| AI framework | **Spring AI only** (`spring-ai-client-chat` + `spring-ai-starter-model-anthropic`, BOM 2.0.2). No LangChain4j dependency or code | `pom.xml` lines 79–86 |
| Model provider | Anthropic via Tekstac gateway; `spring.ai.anthropic.*` env-driven; model `global.anthropic.claude-sonnet-4-6`, timeout 15s, max-tokens 2048 | `src/main/resources/application.yml` lines 38–57 |
| Database / persistence | MySQL 8.x, Spring Data JPA, Flyway migrations V1–V4, `ddl-auto=validate`, application-assigned UUIDs, optimistic locking, CHECK constraints (incl. `human_review_required = TRUE`) | `src/main/resources/db/migration/V1__baseline_schema.sql`–`V4__policy_knowledge.sql`, `application.yml` |
| Vector store | Custom `JpaVectorStore` over `policy_chunk` (cosine full-scan) behind framework-neutral `VectorStore`/`EmbeddingModel` interfaces; default `LexicalHashEmbeddingModel` (384-dim) | `knowledge/JpaVectorStore.java`, `knowledge/LexicalHashEmbeddingModel.java`, `knowledge/VectorStore.java` |
| UI | Thymeleaf analyst workspace (intake, investigation, evidence, recommendation, citations, review, dashboard, login) + token-based `static/css/app.css`, minimal progressive JS | `src/main/resources/templates/`, `ui/CaseUiController.java` |
| MCP / external integration | None | repository inspection |
| Entry point | `com.capstone.fraudcase.FraudCaseApplication` | `src/main/java/com/capstone/fraudcase/FraudCaseApplication.java` |
| Framework-neutral interfaces | `FraudCaseProcessor` (agent port), `EvidenceTool`/`ToolInvoker` (tool port), `VectorStore`/`EmbeddingModel` (RAG port), `CaseAssistant` (chat port), `AuditTrail` (audit port) | `agent/common/FraudCaseProcessor.java`, `tools/ToolInvoker.java`, `knowledge/VectorStore.java` |
| Configuration modes | `fraud.ai.enabled=false` (default → rules-only `RulesFraudCaseAgent`); `fraud.ai.enabled=true` → `SpringAiFraudCaseAgent` (`@Primary`, `@ConditionalOnProperty`) | `agent/rules/RulesFraudCaseAgent.java`, `agent/springai/SpringAiFraudCaseAgent.java` lines 59–62 |

## 3. Build and Test Results
| Command | Result | Details |
|---|---|---|
| `mvn clean verify` | PASS (exit 0) | Compiled with no reported warnings; unit + real-MySQL repository/migration/API tests all green |
| Test totals | 829 run / 0 failed / 0 errored / 0 skipped | Aggregated from `target/surefire-reports/*.txt`; `DB_TEST_URL` was exported so `@MySqlRepositoryTest`-tagged tests ran against a dedicated `fraud_capstone_test` schema (Flyway clean+migrate) |
| Coverage | Not configured | No JaCoCo/other coverage plugin in `pom.xml` — coverage percentage not measurable |
| External-credential dependency | None for the verified run | AI path (`fraud.ai.enabled`) is off by default and fully covered by mocked-`ChatModel` tests; no live model key required |

## 4. Evaluation Scorecard
| # | Metric | Maximum | Score | Status | Evaluator Comments | Evidence |
|---|---|---:|---:|---|---|---|
| 1 | BFS fraud and dispute use case | 10 | 10 | Implemented | Coherent dispute/fraud intake (`CreateCaseRequest`, `TransactionClaim`), synthetic IDs (`txn_fraud`/`txn_dispute`/`txn_dup`/…), full case lifecycle (`NEW→ENRICHING→READY_FOR_REVIEW/INCOMPLETE→UNDER_REVIEW→ROUTED/CLOSED`), all four required classifications plus `MERCHANT_DISPUTE`/`DUPLICATE_PROCESSING`, deterministic routing, human-owned final decision, no loan-underwriting residue | `domain/CaseStatus.java`, `domain/Classification.java`, `domain/CaseStateMachine.java`, `tools/synthetic/SyntheticScenario.java` |
| 2 | Architecture and maintainability | 12 | 11 | Implemented | Clean layered/hexagonal split: `api` / `application` / `domain` / `persistence` / `agent` / `tools` / `knowledge` / `security` / `ui` / `observability`. Framework-neutral ports with single AI adapter; no business-rule duplication between rules and AI paths (shared `RecommendationValidator`, `FallbackRecommendations`, `FraudCaseContext`). Bean Validation + custom validators, typed records/enums, `@Primary`+`@ConditionalOnProperty` config-driven selection. Minor: `SpringAiFraudCaseAgent` is a large class; a hand-rolled `ExecutorService` for timeouts is workable but non-standard | package tree, `agent/common/*`, `application/InvestigationOrchestrator.java` |
| 3 | Fraud investigation workflow and deterministic tools | 15 | 12 | Partially Implemented | Strong: allow-listed `EvidenceTool` enum, single `ToolInvoker` path with timeouts and structured `ToolOutcome`/`ToolFailure`, deterministic seed-derived synthetic fixtures independent of case ID/clock, typed sealed `EvidencePayload` DTOs, provenance (`source`, canonical payload, SHA-256 `content_hash`), model cannot trigger new tool executions. Gaps vs rubric tool list: **no dedicated transaction-velocity calculation, no amount-deviation calculation, and no synthetic sanctions/PEP screening tool** (velocity/amount signals exist only implicitly inside fixture payloads); AML escalation is a route, not a screening tool | `tools/ToolInvoker.java`, `tools/synthetic/*`, `domain/EvidenceTool.java`, `V2__evidence_provenance.sql`, `EvidenceToolContractTest` |
| 4 | Spring AI implementation | 20 | 18 | Implemented | `ChatClient` over injectable `ChatModel` (`ObjectProvider` → graceful absence), system + user `PromptTemplate`s from external `.st` files, read-only `@Tool` methods bound to pre-collected evidence, typed `.entity(ModelAssessment.class)` structured output, virtual-thread timeout, model-uncontrolled fields forced (`humanReviewRequired`, caseId, `ProcessorMode.AI`), cited-ID intersection to prevent fabricated references, every failure mode → manual-review fallback (verified by tests incl. timeout, malformed output, missing fields, unsafe rationale). Custom (non–Spring AI) memory/vector-store ports are used, which is acceptable but means Spring AI advisors/`VectorStore`/`ChatMemory` abstractions are not exercised | `agent/springai/SpringAiFraudCaseAgent.java`, `CaseEvidenceTools.java`, `prompts/fraud-system.st`, `prompts/fraud-user.st`, `SpringAiFraudCaseAgentTest` |
| 5 | LangChain4j implementation | — | N/A | Optional Not Implemented | Not present; 10 points redistributed to metric 4 per scoring rule | `pom.xml` |
| 6 | RAG, embeddings, vector retrieval, and citations | 15 | 14 | Implemented | End-to-end pipeline: ingestion (validate→chunk→embed→upsert), section-aware deterministic chunk IDs, rich metadata (title/version/section/owner/effective date/status/classification), configurable top-K + similarity threshold, retired/draft/not-yet-effective/stale-version exclusion with per-candidate drop reasons, verbatim citations returned to API/UI, explicit abstain codes (`NO_MATCH`/`BELOW_THRESHOLD`/`NO_EFFECTIVE_VERSION`/`STORE_ERROR`) forcing `MANUAL_REVIEW`, retrieval persisted to `policy_retrieval_trace`. Embedding is a lexical-hash model (defensible for a local capstone; not a neural embedder — minor limitation for paraphrase robustness) | `knowledge/PolicyRetriever.java`, `PolicyIngestionService.java`, `PolicyChunker.java`, `JpaVectorStore.java`, `V4__policy_knowledge.sql`, `PolicyRetrievalMySqlTest` |
| 7 | Structured output, memory, and human review | 8 | 8 | Implemented | Typed `FraudCaseRecommendation` + `RecommendationValidator` (schema re-check before persistence/display); bounded case-scoped `CaseConversationMemory` (default window 20, PAN redaction, isolation by construction); review decisions `ACCEPT`/`OVERRIDE`/`CLOSE` with mandatory override finals+rationale and `409 CASE_CONFLICT` on stale recommendation; DB CHECK enforces `human_review_required`; no autonomous adverse actions anywhere | `agent/common/RecommendationValidator.java`, `memory/CaseConversationMemory.java`, `application/FraudCaseService.java`, `V1__baseline_schema.sql` |
| 8 | Security, privacy, responsible AI, and auditability | 8 | 8 | Implemented | No secrets in source (env-only config; `{bcrypt}` enforcement rejects plaintext); `.env*` git-ignored; synthetic data only; `SafeText`/`MaskedReference`/`IsoCurrency` validators + `SensitiveDataGuard`; prompt-injection defenses (untrusted-data framing, read-only tools, verbatim-only citations, no 12+ digit sequences in output); least-privilege RBAC (ANALYST/REVIEWER), deny-by-default, CSRF on UI, safe error contract; correlation IDs on audit rows and tool traces; append-only audit with model/framework captured via `processorMode`; `docs/SECURITY_RESPONSIBLE_AI.md` | `security/SecurityConfiguration.java`, `domain/SensitiveDataGuard.java`, `persistence/audit/AuditTrail.java`, `observability/CorrelationIdFilter.java`, `.gitignore`, `docs/` |
| 9 | Testing, reliability, and observability | 8 | 7 | Implemented | 829 green tests: tool contract tests across all 9 synthetic scenarios, boundary/negative validation tests, real-MySQL API/persistence/migration tests, structured-output rejection tests, RAG grounding/retired/no-answer tests, memory-isolation test, state-machine tests, optimistic-locking/`CONCURRENT_MODIFICATION` tests. Correlation IDs + health probes + audit/tool/retrieval traces. Deductions: no coverage tooling (JaCoCo absent), no metrics/ Micrometer latency instruments beyond trace rows, no explicit idempotent-retry semantics for duplicate case submission (conflict protection exists, but not a documented retry-safe contract) | `src/test/**`, `CorrelationIdFilter.java`, `SchemaMigrationHealthIndicator.java` |
| 10 | UI, documentation, and developer experience | 4 | 4 | Implemented | Full analyst workflow UI (intake → investigation → evidence → recommendation → citations → review) with loading/empty/error states, labelled controls, token-based CSS, no loan terminology; excellent docs suite (requirements, architecture, API/data contract, RAG policy, security/RAI, test plan, UI spec) + precise README runbook | `templates/*`, `static/css/app.css`, `docs/*`, `README.md` |
| | **Total** | **100** | **92** | | | |

## 5. Framework Evaluation
### 5.1 Spring AI
Verified: `ChatClient`/`ChatModel` usage; external prompt templates; `@Tool` read-only case-evidence tools; `.entity(ModelAssessment.class)` typed output; timeout + total failure fallback to `manualReview`; cited-ID intersection; model/provider config entirely in `application.yml` + env vars. Not used: Spring AI advisors, `VectorStore`, `ChatMemory` (replaced by framework-neutral ports — an acceptable architectural trade-off, but Spring AI coverage is therefore partial).
### 5.2 LangChain4j
Not implemented. No LangChain4j dependency, AI Service, or `EmbeddingStore` code exists.
### 5.3 Framework Parity
Not applicable — single framework.

## 6. Acceptance Scenario Results
All scenarios verified through the automated suite (no live-model run; AI path covered by mocked `ChatModel` in `SpringAiFraudCaseAgentTest`, deterministic path via `EvidenceToolContractTest`/`RulesFraudCaseAgentTest`/`InvestigationMySqlTest`).

| Scenario | Mode | Expected | Actual | Result | Evidence |
|---|---|---|---|---|---|
| Known device / familiar merchant / normal amount (`txn_genuine`) | rules | Low-risk / genuine recommendation | `GENUINE_TRANSACTION` with cited evidence | Pass | `EvidenceToolContractTest`, `SyntheticFixtureGeneratorTest` |
| New device / unusual location / high amount (`txn_fraud`) | rules | Suspicious/unauthorised with evidence | `UNAUTHORISED_TRANSACTION`-route recommendation | Pass | `EvidenceToolContractTest`, `RulesFraudCaseAgentTest` |
| ≥5 txns in 10 min (velocity) | rules | Velocity indicator recorded | Recorded only as fixture payload content; **no dedicated velocity tool** | Partial | `tools/model/*`; gap noted in §4 #3 |
| Confirmed screening match | — | Mandatory escalation, no autonomous action | No sanctions/PEP screening tool exists; escalation routes exist (`FRAUD_INVESTIGATION`) but nothing auto-acts | Partial | `domain/Route.java`; gap in §4 #3 |
| Evidence service unavailable (`txn_unavail`, `txn_timeout`, `txn_partial`) | rules | `INSUFFICIENT_EVIDENCE` / request more info | `INCOMPLETE` status + missing-information entries | Pass | `EvidenceToolContractTest`, `SyntheticScenario.java` |
| Same case submitted twice | api | Conflict / duplicate protection | `409 CASE_CONFLICT` / `CONCURRENT_MODIFICATION` | Pass | `OptimisticLockingMySqlTest`, `FraudCaseApiMySqlTest` |
| Policy question with active source | rag | Grounded answer w/ title+section+version | Citations with title/version/section/owner/score | Pass | `PolicyRetrievalMySqlTest`, `PolicyRetrieverTest` |
| Question absent from corpus | rag | Explicit no-information response | `AbstainCode` outcome → `MANUAL_REVIEW` + missing info | Pass | `PolicyRetrieverTest` |
| Retired policy contains matching text | rag | Retired excluded | `NOT_APPROVED`/`STALE_VERSION` drop reasons recorded | Pass | `PolicyRetrievalMySqlTest` (per-candidate drop traces) |
| Model returns malformed output | spring-ai | Validated retry or safe failure | Manual-review fallback (timeout, null fields, unsafe rationale, missing rationale all tested) | Pass | `SpringAiFraudCaseAgentTest` |
| Two separate case IDs | memory | Isolated memory | Case-scoped queries only; isolation test green | Pass | `CaseConversationMemoryTest` |
| Prompt injection in narrative | rules/ai | Treated as data | `SafeText` validation + system-prompt untrusted-data rules + verbatim-only citations | Pass | `SafeTextValidator`, `prompts/fraud-system.st`, `SensitiveDataGuardTest` |

## 7. Strengths
1. Defense-in-depth AI safety: model output is intersected with actually-supplied evidence/trace/chunk IDs, model-uncontrolled fields are forced, `RecommendationValidator` re-validates, and *every* model failure mode degrades to a tested manual-review fallback — fabrication-resistant by construction.
2. Genuinely deterministic evidence layer: allow-listed tools behind a single `ToolInvoker`, seeded fixtures independent of case ID/clock, provenance + SHA-256 content hashes enforced at domain *and* DB CHECK level.
3. Audit-grade RAG: versioned policies with effective-date/status gating, per-candidate drop reasons persisted to `policy_retrieval_trace`, explicit abstain codes, verbatim citations — unusually rigorous for a capstone.

## 8. Gaps and Risks
| Priority | Gap or Risk | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|
| High | No dedicated transaction-velocity or amount-deviation tools; velocity scenario only implicitly covered | Rubric-listed evidence types missing; fraud signal quality limited | `domain/EvidenceTool.java` (7 tools only) | Add `calculateVelocity` and `calculateAmountDeviation` tools with typed payloads + contract tests |
| High | No synthetic sanctions/PEP screening tool → rubric "confirmed screening match → mandatory escalation" scenario cannot be demonstrated end-to-end | Missing required escalation evidence path | `EvidenceTool` enum, tool list above | Add `screenSanctionsPep` synthetic tool returning deterministic match/no-match; wire escalation as *advisory* route only |
| Medium | No test-coverage tooling (JaCoCo) | Coverage claims unverifiable | `pom.xml` (no coverage plugin) | Add `jacoco-maven-plugin` with a modest threshold |
| Medium | No Micrometer metrics/latency timers beyond persisted traces; actuator exposes health+info only | Limited runtime observability evidence | `application.yml` management block | Add timers on tool/model/retrieval paths and a `metrics` endpoint |
| Low | Lexical-hash embedding is keyword-bound; paraphrase recall weaker than a neural embedder | Retrieval robustness on paraphrased policy questions | `knowledge/LexicalHashEmbeddingModel.java` | Keep port; optionally add a real embedding model behind `EmbeddingModel` when a provider key is available |
| Low | Anthropic `temperature` default 0.7 | Higher-than-needed nondeterminism for structured output | `application.yml` line 48 | Default to 0–0.2 for classification tasks |

## 9. Security and Responsible AI Findings
| Severity | Finding | File or Component | Remediation |
|---|---|---|---|
| Info | No committed secrets found. DB/API-key values are env-only; analyst passwords must be `{bcrypt}` hashes (plaintext rejected at bind time) | `application.yml`, `FraudSecurityProperties.java` | None |
| Info | Test-only bcrypt hashes committed for throwaway in-suite accounts | `src/test/resources/application-test.yml` | Acceptable — clearly scoped, never usable at runtime; document in README (already covered) |
| Info | Prompt-injection surface handled: untrusted-data system prompt, `SafeText` validation, verbatim-only citations, output digit-length cap | `prompts/fraud-system.st`, `api/validation/*` | Consider adding explicit tests that assert injected tool/narrative text never alters the forced `humanReviewRequired` |
| Info | Advisory-only contract enforced (no account/card/customer actions; DB CHECK `human_review_required = TRUE`) | `V1__baseline_schema.sql`, `SpringAiFraudCaseAgent` | None |
| Info | Default LLM gateway `base-url` is committed (non-secret endpoint, Tekstac) | `application.yml` line 42 | Acceptable; keep gateway reachability out of the trust boundary in docs |

## 10. Recommended Improvements
### Critical
- None.
### High
- Add `calculateVelocity` and `calculateAmountDeviation` deterministic tools (typed payloads, `ToolInvoker` registration, contract tests, fixture scenarios).
- Add a synthetic `screenSanctionsPep` tool + advisory escalation path so the screening-match acceptance scenario is demonstrable.
### Medium
- Add JaCoCo coverage reporting and wire into `verify`.
- Add Micrometer timers for tool invocation, model call, and policy retrieval latency.
- Document an idempotent case-intake contract (e.g., client-supplied idempotency key) on top of the existing `CASE_CONFLICT` protection.
### Low
- Lower default model `temperature` for classification.
- Consider exercising Spring AI `ChatMemory`/advisor abstractions or document the deliberate port-based choice in `ARCHITECTURE.md`.

## 11. Definition-of-Done Checklist
- [x] Fraud case intake works with synthetic data
- [x] Deterministic tools provide traceable evidence
- [x] Structured recommendations are validated
- [x] Policy answers are grounded and cited
- [x] Human review is mandatory
- [x] Conversation/case memory is isolated
- [x] Secrets are not stored in the repository
- [x] Error and no-information states are handled safely
- [x] Automated tests pass
- [x] README enables another participant to run the project

## 12. Final Verdict
**Demo Ready.** The capstone is a well-architected, safety-first fraud-investigation assistant: layered hexagonal design behind framework-neutral ports, a deterministic provenance-hashed evidence toolchain, an audit-grade versioned policy RAG with explicit abstention, schema-validated recommendations with mandatory human review, and 829 green tests including real-MySQL integration coverage — all verified by execution (`mvn clean verify`, exit 0). The main shortfalls are scope gaps, not defects: missing dedicated velocity/amount-deviation and sanctions/PEP screening tools, and absent coverage/metrics instrumentation. With the two High-priority tools added, this would be a complete rubric exemplar.
