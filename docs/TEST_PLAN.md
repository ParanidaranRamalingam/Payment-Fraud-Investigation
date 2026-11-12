# Test and Evaluation Plan

## Required scenarios
T01 denied merchant + new device + location anomaly + policy match -> fraud/suspicious route, high risk, human review.
T02 recognised merchant + cancellation/fulfilment -> merchant dispute.
T03 duplicate fingerprint/posting -> duplicate processing.
T04 missing transaction/statement -> validation or insufficient evidence.
T05 no policy above threshold -> abstention/manual review.
T06 contradictory signals -> visible contradiction and lower confidence/manual review.
T07 model unavailable -> rules/safe manual review.
T08 tool timeout/not found -> structured unavailable evidence.
T09 malformed recommendation -> reject as invalid.
T10 Case A fact queried from Case B -> no recall.
T11 prompt injection -> treat as data; preserve boundaries.
T12 reviewer override without reason -> block submission.

## MySQL integration verification
- Run repository/integration tests against MySQL-compatible behavior; for CI/local automation prefer Testcontainers MySQL when Docker is available, but final verification must also run against the VM MySQL instance.
- Run Flyway from a clean schema and confirm all migrations apply.
- Restart application and confirm persisted cases/reviews remain.
- Verify foreign keys, indexes, decimal precision, enum string mapping and timestamp handling.
- Verify audit rows cannot be mutated through application paths.

## UI verification
Test the six required pages plus loading/empty/error states. Validate 1366x768 desktop usability, ~768px responsive layout, keyboard focus, labelled forms, non-color-only statuses, no primary horizontal overflow, CSRF/output encoding and mandatory review rationale.

## Layers
Unit, tool contract, repository/MySQL, processor contract, RAG, memory, web/UI, evaluation and security.

## Pass conditions
No autonomous action or sensitive logging; human-review flag 100%; deterministic fixtures reproduce; all safety failures reach controlled incomplete/manual review; migrations cleanly initialize MySQL; modern UI workflows remain usable and accessible.
