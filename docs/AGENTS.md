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

## Project facts (learned in Session 2)
- Deterministic core: `agent/common` (`FraudCaseProcessor`, `FraudCaseContext`, `CollectedEvidence`, `RecommendationValidator`, `FallbackRecommendations`), `agent/rules/RulesFraudCaseAgent` (ordered rule table in its Javadoc, ruleset `rules-v1`), `tools/` (`ToolInvoker` = only path to tools; allow-list, timeout, structured failures), `tools/model` (typed sealed `EvidencePayload` DTOs), `tools/synthetic` (fixtures), `tools/sla`, `persistence/audit/AuditTrail` (only audit writer; redacts + bounds details), `application/InvestigationOrchestrator`.
- Synthetic scenarios are selected by transaction-reference prefix: `txn_fraud`, `txn_dispute`, `txn_dup`, `txn_missing`, `txn_conflict`, `txn_genuine`, `txn_timeout`, `txn_unavail`, `txn_partial`; anything else is hash-derived. Fixture values depend only on identifiers + `fraud.tools.fixture-seed` (default `fraud-training-v1`), never on case ID/clock. `calculateCaseSla` depends on case `createdAt` and `asOf` by design.
- Config: `fraud.tools.timeout` (default 2500ms), `fraud.tools.fixture-seed`. Defaults live in `ToolProperties`, not `application.yml`.
- Evidence provenance (V2 migration): `source`, `payload` (canonical sorted `key=value` lines), `content_hash` = SHA-256 of `TOOL_NAME + "\n" + payload`; only AVAILABLE evidence may carry payload/hash (domain + DB CHECK).
- Recommendation trace refs: `rule:<id>`, `signal:<id>` or a tool-trace UUID of the same case (validator enforces).
- Audit rows created in the same microsecond can tie on `occurred_at`; don't assert strict audit order in tests.
- Shared test helper: `src/test/java/.../support/SyntheticCases` builds requests/contexts without a DB. Integration test with the full stack on MySQL: `InvestigationMySqlTest` (`@MySqlRepositoryTest` + `@ComponentScan` test config).

## Project facts (learned in Session 3)
- API layer: `FraudCaseController` (thin; actor = authenticated principal, never request body) -> `FraudCaseService` (intake, case-scoped reads, review) -> `InvestigationOrchestrator` (also the only path to `CaseAssistant` via `respond()`). `CaseApiMapper` does DTO translation; controllers never touch tools/processors.
- Error contract: `ApiExceptionHandler` -> `ApiErrorResponse` envelope. `400 VALIDATION_FAILED`/`MALFORMED_REQUEST`, `404 CASE_NOT_FOUND`/`RECOMMENDATION_NOT_FOUND`, `409 INVALID_TRANSITION`/`CASE_CONFLICT`/`CONCURRENT_MODIFICATION`, `422 BUSINESS_RULE_VIOLATION`. `fail-on-unknown-properties: true` rejects smuggled fields (e.g. `caseId`) as MALFORMED_REQUEST.
- Security: stateless HTTP Basic; accounts only from `FRAUD_SECURITY_ACCOUNTS_<n>_USERNAME/_PASSWORD/{bcrypt}_ROLES` (relaxed binding; `{bcrypt}` hash required; no accounts = fail closed). `ANALYST` does all writes except reviews; `REVIEWER` posts reviews; both read. Public: `/actuator/health/**`, `/actuator/info`, `/error`.
- Review flow: `READY_FOR_REVIEW|INCOMPLETE -> UNDER_REVIEW -> ROUTED` (ACCEPT/OVERRIDE) or `-> CLOSED`; `ROUTED -> CLOSED`. ACCEPT keeps recommendation's classification/route; OVERRIDE requires both finals + rationale; stale recommendationId -> `409 CASE_CONFLICT`.
- Conversation memory: `V3__case_message.sql` (`case_message` + `case_message_evidence`). `CaseAssistant`/`RulesCaseAssistant` answer from case-scoped persisted facts; `RecommendationValidator.validateReply` enforces bounded safe text citing only this case's evidence; replies carry `replyToId` + `processorMode`; terminal cases reject messages. Rejected replies -> audited fallback reply.
- `CorrelationIdFilter` (`X-Correlation-Id`, valid pattern `^[A-Za-z0-9._-]{8,64}$` else regenerated) runs before security; ID lands on audit rows and tool traces.
- Spring Boot 4 test packages: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`; `@MockitoBean` in `org.springframework.test.context.bean.override.mockito`.
- `FraudCaseApiMySqlTest` = `@SpringBootTest` + `@AutoConfigureMockMvc` + real MySQL (`@ActiveProfiles("test")` + `MySqlTestConfiguration`); throwaway bcrypt test accounts live in `src/test/resources/application-test.yml` (packaged jar unaffected).

## Project facts (learned in Session 4)
- Policy RAG lives in `knowledge/`: `PolicyDocument` (metadata contract at construction; `PolicyStatus` DRAFT/APPROVED/RETIRED, `PolicyClassification` INTERNAL/RESTRICTED), `PolicyChunker` (section-aware, sentence-packed, deterministic chunk ids `documentId:version:index`), `EmbeddingModel` + `VectorStore` abstractions with defaults `LexicalHashEmbeddingModel` (`lexical-hash:v1`, 384-dim curated-vocab + hashed-residual cosine) and `JpaVectorStore` (full-scan cosine over `policy_chunk`; replaceable by an ANN store). `PolicyIngestionService` = validate -> chunk -> embed -> upsert (re-ingest replaces a version's chunks atomically).
- `PolicyRetriever` builds a deterministic query from claim-type terms + merchant + (untrusted) customer statement; drops are `BELOW_THRESHOLD|NOT_APPROVED|NOT_YET_EFFECTIVE|STALE_VERSION|BEYOND_TOP_K`; effective version = latest APPROVED `effectiveDate <= asOf` per document. Abstain codes `NO_MATCH|BELOW_THRESHOLD|NO_EFFECTIVE_VERSION|STORE_ERROR`; ungrounded outcome forces `MANUAL_REVIEW` + a missing-info entry, re-validated by `RecommendationValidator`.
- Config `fraud.policy.*` (`PolicyProperties`, defaults not in `application.yml`): top-k 4, similarity-threshold 0.72, max-chunk-chars, max-chunks-per-document, seed-sample-corpus.
- `PolicyCorpusSeeder` is an `ApplicationRunner` and **does run inside Spring test contexts** (any `@ComponentScan` of `knowledge/`), committing sample docs outside test transactions — wipe `policy_chunk`/`policy_document` per test when a test must own the corpus (see `PolicyRetrievalMySqlTest.clearCorpus`).
- V4 migration: `policy_document`, `policy_chunk` (embedding stored as comma-separated floats in TEXT), `policy_retrieval_trace` + `policy_retrieval_hit` (per-candidate drop reasons, CHECK `(grounded)=(abstain_code IS NULL)`), `policy_citation` gains `owner`.
- Policy citations keep domain names `documentTitle/documentVersion/relevanceScore/excerpt` (API DTOs rename to title/version/similarityScore/passage); retrieved passages are quoted verbatim — never interpreted as instructions.

## Project facts (learned in Session 5)
- AI adapter lives in `agent/springai/`: `SpringAiFraudCaseAgent` (`@Primary`, `@ConditionalOnProperty(fraud.ai.enabled=true)`, default off so rules mode stays the sole processor), `ModelAssessment` (structured output DTO), `CaseEvidenceTools` (per-context `@Tool` methods returning already-collected payloads — read-only, no new tool execution), `FraudAiProperties` (`fraud.ai.enabled`, `fraud.ai.timeout` default 15s). Prompts: `resources/prompts/fraud-system.st` + `fraud-user.st`.
- Only `spring-ai-client-chat` is on the classpath (spring-ai-bom 2.0.2) — no provider starter. `ChatModel` is injected via `ObjectProvider`; absent bean, provider error, timeout, unparseable or invalid output all degrade to `FallbackRecommendations.manualReview` (never thrown for model problems).
- Spring AI 2.0 API notes: `ToolCallbacks` moved to `org.springframework.ai.support`; prefer `.tools(obj)` over deprecated `.toolCallbacks(varargs)` on the request spec; structured output is `.call().entity(Class)`; a mocked `ChatModel` must also stub `getOptions()` (NPE in `DefaultChatClientUtils` otherwise); `ChatResponse` = `new ChatResponse(List.of(new Generation(new AssistantMessage(text))))`.
- Mapping discipline in the adapter: caseId/`humanReviewRequired`/`processorMode=AI` are forced (never model fields); model-cited evidenceIds/traceIds/chunkIds are intersected with what was supplied; `INSUFFICIENT_EVIDENCE` forces `MANUAL_REVIEW` + injected gaps; rationale must be non-blank/safe and is truncated to 2000; `RecommendationValidator` re-checks everything before return.
