# API, Data and MySQL Contract

## Intake
Use server-generated case ID and authenticated analyst ID; accept only masked/tokenized customer references; positive amount and ISO currency; bounded/sanitized statement; `syntheticDataConfirmed=true`; `reviewRequired=true`. Never accept/store full card/account identifiers.

## APIs
- POST `/api/cases`
- GET `/api/cases/{caseId}`
- POST `/api/cases/{caseId}/investigations`
- GET `/api/cases/{caseId}/evidence`
- GET `/api/cases/{caseId}/tool-traces`
- GET `/api/cases/{caseId}/recommendation`
- GET `/api/cases/{caseId}/citations`
- POST `/api/cases/{caseId}/messages`
- POST `/api/cases/{caseId}/reviews`

## Recommendation contract
Required: `caseId`, classification, risk, route, confidence 0..1, `humanReviewRequired=true`, evidence IDs or explicit insufficient-evidence outcome, policy citations for procedural claims, missing information, traces/rules and concise rationale.

## Persistence
Tables/entities: `fraud_case`, `transaction_claim`, `evidence_item`, `recommendation`, `policy_citation`, `tool_trace`, `review_disposition`, `audit_event`.

## MySQL conventions
- InnoDB and `utf8mb4`.
- IDs are application-generated strings/UUIDs with deliberate `VARCHAR` sizes.
- Monetary values use `DECIMAL(19,2)` or an agreed precision; never float/double.
- Timestamps use a consistent application timezone strategy and `DATETIME(6)` or equivalent mapped type.
- Enums stored as readable strings.
- Foreign keys and indexes created by Flyway migrations.
- Audit events have no normal update/delete path.
- Use optimistic locking/version where concurrent case updates need protection.

## Configuration
Example only, with no secret committed:
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/fraud_capstone?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
    username: ${DB_USERNAME:fraud_app}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
```
For the VM, provide `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` externally. Do not hard-code VM credentials.

## Migration expectations
`V1__baseline_schema.sql` creates the domain schema; subsequent changes use new immutable migrations. Provide a schema-only database bootstrap command in README if the test operator needs to create `fraud_capstone` before the application account runs Flyway.
