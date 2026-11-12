# Architecture and Implementation Blueprint

## Layering
- `api/`: REST controllers, DTOs and validation.
- `application/`: `FraudCaseService`, `InvestigationOrchestrator`, state transitions and processor selection.
- `domain/`: framework-neutral case, claim, evidence, recommendation and review models.
- `agent/common/`: processor/context contracts and recommendation validator.
- `agent/rules/`: deterministic fallback rules.
- `agent/springai/` or `agent/langchain4j/`: AI adapter only.
- `tools/`: allow-listed synthetic evidence providers.
- `knowledge/`: policy ingestion/retrieval/citation mapping.
- `persistence/`: MySQL/JPA entities, repositories, Flyway migrations and audit writer.
- `web/`: Thymeleaf pages, fragments, web controllers and view models.
- `observability/`: correlation IDs, metrics/traces and redacted logging.

## Core contract
```java
public interface FraudCaseProcessor {
    FraudCaseRecommendation analyze(FraudCaseContext context);
}
```
State transitions and authorization stay in application services, never in LLM output.

## Processing sequence
`Browser -> Web/API Controller -> FraudCaseService -> InvestigationOrchestrator -> Tools + PolicyRetriever -> FraudCaseProcessor -> RecommendationValidator -> MySQL/Audit -> Human Review`.

## MySQL persistence
Target MySQL 8.x-compatible SQL/JDBC. Use Spring Data JPA and Flyway. Prefer portable types: `VARCHAR`, `TEXT`, `DECIMAL`, `DATETIME(6)`, `BIGINT`. Store enums as strings. Define indexes for case IDs, status, timestamps and foreign keys. Use `utf8mb4`. Do not rely on automatic destructive schema generation.

## Recommended tree
```text
src/main/java/.../fraudcase/
  api/
  application/
  domain/
  agent/common/
  agent/rules/
  agent/springai/
  tools/
  knowledge/
  persistence/
  web/
src/main/resources/
  db/migration/
  prompts/
  policies/
  templates/
  static/css/
  static/js/
README.md
AGENTS.md
application-example.yml
```

## Design decisions
1. Evidence collection is deterministic and separated from model interpretation.
2. Rules mode is runnable without any model.
3. Provider/model/vector details are adapters selected by config.
4. Provenance accompanies every material fact.
5. Output validation occurs at the application boundary.
6. RAG explicitly abstains when grounding is inadequate.
7. Human review is mandatory.
8. MySQL is the single final runtime database contract for the VM demonstration.
