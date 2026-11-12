# Capstone Requirements

## Goal
Build an explainable payment-fraud investigation and dispute-resolution assistant that collects deterministic evidence, retrieves policy, produces a schema-validated recommendation, and always requires human review.

## Runtime target
The final application will be demonstrated and tested on a VM with MySQL. MySQL is the primary runtime database. Do not depend on H2 for the final path. Keep credentials externalized through environment variables and provide schema migrations.

## Safety boundary
Advisory only. Never block cards, issue refunds/credits, move funds, file compliance reports, change customer/account state, or contact customers automatically.

## Functional flow
1. Intake and validate masked/synthetic identifiers.
2. Create a bounded investigation plan.
3. Invoke allow-listed deterministic evidence tools.
4. Retrieve versioned policy passages with citation metadata.
5. Assess facts, evidence, contradictions and gaps.
6. Produce typed classification/risk/route/confidence/rationale.
7. Require reviewer disposition and rationale.
8. Persist state, evidence, traces, citations, recommendation, review and append-only audit events.

## Required states
`NEW -> ENRICHING -> READY_FOR_REVIEW|INCOMPLETE -> UNDER_REVIEW -> ROUTED|CLOSED`, with `INCOMPLETE -> ENRICHING|CLOSED` and `ROUTED -> CLOSED`.

## Required classifications
`UNAUTHORISED_TRANSACTION`, `MERCHANT_DISPUTE`, `DUPLICATE_PROCESSING`, `SUSPICIOUS_TRANSACTION`, `INSUFFICIENT_EVIDENCE`, `GENUINE_TRANSACTION`.

## Routes
`FRAUD_INVESTIGATION`, `DISPUTE_OPERATIONS`, `PAYMENT_OPERATIONS`, `MANUAL_REVIEW`.

## Evidence tools
Implement at least four, preferably all: `getTransactionDetails`, `getDeviceSignals`, `compareRecentLocations`, `getMerchantRiskProfile`, `getPriorDisputes`, `calculateCaseSla`, plus policy retrieval and audit. Results must be deterministic, typed, traceable and fail safely.

## AI/rules contract
A framework-neutral `FraudCaseProcessor.analyze(FraudCaseContext)` is implemented by rules mode plus either Spring AI or LangChain4j. Business rules, tool DTOs and output schema remain common.

## Recommendation invariants
Include case ID, classification, risk, route, confidence 0..1, `humanReviewRequired=true`, evidence IDs, policy citations for procedural claims, missing information, rule/tool trace IDs, and concise rationale. Validate before persistence/display.

## RAG
Preserve document ID, title/version/effective date/section/chunk metadata. Retrieval uses configurable Top-K and threshold. No approved passage above threshold means explicit abstention/manual review.

## UI requirement
Deliver a polished modern analyst workspace, not a default scaffolded CRUD UI. Six capabilities are required: intake, investigation, recommendation, evidence, citations and human review. UI must be responsive, accessible and clearly distinguish provenance.

## Definition of Done
Clean VM build/run; MySQL connectivity/migrations verified; 6+ synthetic scenarios; 4+ evidence tools; rules mode without model; safe AI failure; RAG citations/abstention; case memory isolation; no secrets/production data/autonomous actions; auditable reviewer rationale; all six UI capabilities; unit/integration/contract/evaluation/security tests.
