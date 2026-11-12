# Payment Fraud Investigation & Dispute Resolution

Advisory-only assistant that collects deterministic evidence, retrieves policy, produces a schema-validated
recommendation and **always** requires human review. It never blocks cards, moves funds, files reports or
contacts customers. See `docs/` for requirements, architecture, API/data contract and test plan.

## Stack
Java 21, Spring Boot 4.1.2, Spring Data JPA (Hibernate 7), Flyway, MySQL 8.x (Connector/J), Bean Validation,
Spring Boot Actuator. Build with Maven.

## 1. Database bootstrap (one-time, run as a MySQL admin)
The application account runs Flyway, so it needs DDL rights on its own schema only.

```sql
CREATE DATABASE IF NOT EXISTS fraud_capstone      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- optional: dedicated schema for the MySQL test suite (it is CLEANED on every test run)
CREATE DATABASE IF NOT EXISTS fraud_capstone_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'fraud_app'@'localhost' IDENTIFIED BY '<choose-a-password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
      ON fraud_capstone.*      TO 'fraud_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
      ON fraud_capstone_test.* TO 'fraud_app'@'localhost';
```

On Ubuntu with `sudo`, this can be run via `sudo mysql --defaults-file=/etc/mysql/debian.cnf`.

## 2. Configuration (environment only — never commit credentials)

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | Runtime JDBC URL | `jdbc:mysql://localhost:3306/fraud_capstone?...&connectionTimeZone=UTC` |
| `DB_USERNAME` | DB user | `fraud_app` |
| `DB_PASSWORD` | DB password | *(empty)* |
| `DB_TEST_URL` | JDBC URL of the **test** schema; enables MySQL tests | *(unset → MySQL tests skipped)* |
| `DB_POOL_SIZE`, `SERVER_PORT`, `HEALTH_SHOW_DETAILS` | Optional tuning | `10`, `8080`, `never` |

Keep these in a private file outside the repo, for example `~/.config/fraud-capstone/db.env` (mode `600`):

```bash
export DB_URL='jdbc:mysql://localhost:3306/fraud_capstone?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
export DB_TEST_URL='jdbc:mysql://localhost:3306/fraud_capstone_test?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
export DB_USERNAME='fraud_app'
export DB_PASSWORD='...'
```

See `application-example.yml` for the shape of the configuration. `spring.jpa.hibernate.ddl-auto` is `validate`:
the schema is owned exclusively by Flyway migrations in `src/main/resources/db/migration`.

## 3. Build, test, run

```bash
. ~/.config/fraud-capstone/db.env && export DB_TEST_URL
mvn clean verify                                   # unit + MySQL repository/migration tests
java -jar target/payment-fraud-investigation-0.1.0-SNAPSHOT.jar
```

Without `DB_TEST_URL` the MySQL-tagged tests are reported as **skipped** (never silently passed); the final
verification must run them against MySQL.

## 4. Health checks
- `GET /actuator/health` — overall status (component names only; details hidden by default)
- `GET /actuator/health/readiness` — `db` + `schemaMigrations` (DOWN on failed/pending Flyway migrations)
- `GET /actuator/health/liveness`
- `GET /actuator/info`

No other actuator endpoints are exposed.

## Schema notes
InnoDB, utf8mb4, `DECIMAL(19,2)` money, `DATETIME(6)` UTC timestamps, enums as `VARCHAR`, FKs/indexes and
`CHECK` constraints (e.g. `human_review_required = TRUE`) created by `V1__baseline_schema.sql`. Applied
migrations are immutable; add `V2__...sql` etc. for changes. `audit_event` is append-only at the application
layer (immutable entity, insert/read-only repository, remove callback rejects); DB triggers are not used because
MySQL with binary logging requires `SUPER` to create them.
