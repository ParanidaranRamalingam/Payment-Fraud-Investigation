# Intelligent Payment Fraud Investigation & Dispute Resolution Agent

## Purpose
Human-controlled, explainable fraud/dispute triage using deterministic evidence tools, policy RAG, structured recommendations, case memory, MySQL persistence and append-only audit.

## Target runtime
Spring Boot application tested on a VM with MySQL. Configure DB access from environment variables. The UI is self-contained and must not require public CDN access at runtime.

## Safety
Synthetic/masked training data only. Recommendations are advisory; human review is mandatory. No autonomous financial/account/customer-contact actions.

## Documentation map
- `AGENTS.md` - universal coding-agent rules including MySQL and modern UI design system.
- `REQUIREMENTS.md` - functional/non-functional requirements and Definition of Done.
- `ARCHITECTURE.md` - layers, processor abstraction, MySQL persistence and repository shape.
- `API_AND_DATA.md` - APIs, schema/data mapping and VM MySQL configuration.
- `RAG_POLICY.md` - ingestion/retrieval/citation/abstention.
- `UI_IMPLEMENTATION.md` - detailed modern analyst-console design and six pages.
- `TEST_PLAN.md` - T01-T12, MySQL and UI verification.
- `SECURITY_RESPONSIBLE_AI.md` - security, DB, web, AI, audit and observability controls.
- `IMPLEMENTATION_PLAN.md` - 8-session agent workflow with ready-to-paste prompts.

## Recommended agent sequence
`Foundation+MySQL -> Rules/Tools -> APIs -> RAG -> Spring AI -> Modern UI+Memory -> VM/Security Hardening -> Final Review`

## MySQL runtime example
```bash
export DB_URL='jdbc:mysql://localhost:3306/fraud_capstone?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USERNAME='fraud_app'
export DB_PASSWORD='set-outside-source-control'
./mvnw spring-boot:run
```
Adjust SSL options to the VM policy. Do not copy real credentials into README or `application.yml`.

## Baseline application settings
Processor modes: `rules | spring-ai | langchain4j`; human review enabled; financial/customer-contact actions disabled; Top-K 4; similarity threshold 0.72; memory max 12; tool timeout 2500 ms; deterministic seed `fraud-training-v1`.

## UI target
Modern banking/operations analyst console using locally bundled assets, responsive around 1366x768 desktop and ~768px tablet width, with consistent design tokens, cards/badges/timelines, provenance and all loading/error/abstention states.
