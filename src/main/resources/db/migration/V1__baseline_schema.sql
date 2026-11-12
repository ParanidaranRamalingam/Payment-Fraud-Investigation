-- Baseline schema for the Payment Fraud Investigation & Dispute Resolution assistant.
-- Target: MySQL 8.0.16+ (CHECK constraints enforced), InnoDB, utf8mb4.
-- IDs are application-generated UUID strings. Money is DECIMAL, never FLOAT/DOUBLE.
-- Timestamps are UTC, stored as DATETIME(6). Enums are stored as readable VARCHAR strings.
-- This migration is immutable once applied; schema changes go in new V<n>__*.sql files.

CREATE TABLE fraud_case (
    id                       VARCHAR(36)  NOT NULL,
    status                   VARCHAR(32)  NOT NULL,
    customer_reference       VARCHAR(64)  NOT NULL,
    card_reference           VARCHAR(64)  NULL,
    customer_statement       TEXT         NOT NULL,
    created_by               VARCHAR(64)  NOT NULL,
    synthetic_data_confirmed BOOLEAN      NOT NULL,
    human_review_required    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_fraud_case PRIMARY KEY (id),
    CONSTRAINT ck_fraud_case_status CHECK (status IN
        ('NEW', 'ENRICHING', 'READY_FOR_REVIEW', 'INCOMPLETE', 'UNDER_REVIEW', 'ROUTED', 'CLOSED')),
    CONSTRAINT ck_fraud_case_synthetic CHECK (synthetic_data_confirmed = TRUE),
    CONSTRAINT ck_fraud_case_human_review CHECK (human_review_required = TRUE)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_fraud_case_status_created ON fraud_case (status, created_at);
CREATE INDEX ix_fraud_case_created_at ON fraud_case (created_at);
CREATE INDEX ix_fraud_case_created_by ON fraud_case (created_by);

CREATE TABLE transaction_claim (
    id                     VARCHAR(36)    NOT NULL,
    case_id                VARCHAR(36)    NOT NULL,
    transaction_reference  VARCHAR(64)    NOT NULL,
    merchant_name          VARCHAR(120)   NOT NULL,
    merchant_category_code VARCHAR(4)     NULL,
    amount                 DECIMAL(19, 2) NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    transaction_at         DATETIME(6)    NOT NULL,
    claim_type             VARCHAR(32)    NOT NULL,
    created_at             DATETIME(6)    NOT NULL,
    CONSTRAINT pk_transaction_claim PRIMARY KEY (id),
    CONSTRAINT uq_transaction_claim_case UNIQUE (case_id),
    CONSTRAINT fk_transaction_claim_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT ck_transaction_claim_amount CHECK (amount > 0),
    CONSTRAINT ck_transaction_claim_type CHECK (claim_type IN
        ('UNRECOGNISED_TRANSACTION', 'MERCHANT_ISSUE', 'DUPLICATE_CHARGE', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_transaction_claim_reference ON transaction_claim (transaction_reference);

CREATE TABLE tool_trace (
    id              VARCHAR(36)   NOT NULL,
    case_id         VARCHAR(36)   NOT NULL,
    tool            VARCHAR(40)   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    request_summary VARCHAR(1000) NULL,
    result_summary  TEXT          NULL,
    error_code      VARCHAR(64)   NULL,
    correlation_id  VARCHAR(64)   NULL,
    started_at      DATETIME(6)   NOT NULL,
    completed_at    DATETIME(6)   NULL,
    duration_ms     BIGINT        NULL,
    CONSTRAINT pk_tool_trace PRIMARY KEY (id),
    CONSTRAINT fk_tool_trace_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT ck_tool_trace_status CHECK (status IN ('SUCCESS', 'NOT_FOUND', 'TIMEOUT', 'ERROR', 'REJECTED')),
    CONSTRAINT ck_tool_trace_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_tool_trace_case_started ON tool_trace (case_id, started_at);

CREATE TABLE evidence_item (
    id            VARCHAR(36) NOT NULL,
    case_id       VARCHAR(36) NOT NULL,
    tool_trace_id VARCHAR(36) NULL,
    tool          VARCHAR(40) NULL,
    provenance    VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    summary       TEXT        NOT NULL,
    collected_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_evidence_item PRIMARY KEY (id),
    CONSTRAINT fk_evidence_item_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT fk_evidence_item_trace FOREIGN KEY (tool_trace_id) REFERENCES tool_trace (id),
    CONSTRAINT ck_evidence_item_status CHECK (status IN ('AVAILABLE', 'NOT_FOUND', 'TIMEOUT', 'UNAVAILABLE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_evidence_item_case_collected ON evidence_item (case_id, collected_at);

CREATE TABLE recommendation (
    id                    VARCHAR(36)   NOT NULL,
    case_id               VARCHAR(36)   NOT NULL,
    classification        VARCHAR(40)   NOT NULL,
    risk_level            VARCHAR(16)   NOT NULL,
    route                 VARCHAR(32)   NOT NULL,
    confidence            DECIMAL(4, 3) NOT NULL,
    human_review_required BOOLEAN       NOT NULL DEFAULT TRUE,
    rationale             TEXT          NOT NULL,
    processor_mode        VARCHAR(16)   NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    CONSTRAINT pk_recommendation PRIMARY KEY (id),
    CONSTRAINT fk_recommendation_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT ck_recommendation_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_recommendation_human_review CHECK (human_review_required = TRUE),
    CONSTRAINT ck_recommendation_classification CHECK (classification IN
        ('UNAUTHORISED_TRANSACTION', 'MERCHANT_DISPUTE', 'DUPLICATE_PROCESSING',
         'SUSPICIOUS_TRANSACTION', 'INSUFFICIENT_EVIDENCE', 'GENUINE_TRANSACTION')),
    CONSTRAINT ck_recommendation_route CHECK (route IN
        ('FRAUD_INVESTIGATION', 'DISPUTE_OPERATIONS', 'PAYMENT_OPERATIONS', 'MANUAL_REVIEW'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_recommendation_case_created ON recommendation (case_id, created_at);

CREATE TABLE recommendation_evidence (
    recommendation_id VARCHAR(36) NOT NULL,
    item_order        INT         NOT NULL,
    evidence_item_id  VARCHAR(36) NOT NULL,
    CONSTRAINT pk_recommendation_evidence PRIMARY KEY (recommendation_id, item_order),
    CONSTRAINT uq_recommendation_evidence UNIQUE (recommendation_id, evidence_item_id),
    CONSTRAINT fk_recommendation_evidence_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation (id),
    CONSTRAINT fk_recommendation_evidence_item FOREIGN KEY (evidence_item_id) REFERENCES evidence_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_missing_info (
    recommendation_id VARCHAR(36)  NOT NULL,
    item_order        INT          NOT NULL,
    description       VARCHAR(500) NOT NULL,
    CONSTRAINT pk_recommendation_missing_info PRIMARY KEY (recommendation_id, item_order),
    CONSTRAINT fk_recommendation_missing_info_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_trace_ref (
    recommendation_id VARCHAR(36) NOT NULL,
    item_order        INT         NOT NULL,
    trace_ref         VARCHAR(64) NOT NULL,
    CONSTRAINT pk_recommendation_trace_ref PRIMARY KEY (recommendation_id, item_order),
    CONSTRAINT fk_recommendation_trace_ref_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE policy_citation (
    id                VARCHAR(36)   NOT NULL,
    recommendation_id VARCHAR(36)   NOT NULL,
    case_id           VARCHAR(36)   NOT NULL,
    document_id       VARCHAR(64)   NOT NULL,
    document_title    VARCHAR(200)  NOT NULL,
    document_version  VARCHAR(32)   NOT NULL,
    effective_date    DATE          NOT NULL,
    section           VARCHAR(120)  NOT NULL,
    chunk_id          VARCHAR(64)   NOT NULL,
    relevance_score   DECIMAL(5, 4) NOT NULL,
    excerpt           TEXT          NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    CONSTRAINT pk_policy_citation PRIMARY KEY (id),
    CONSTRAINT fk_policy_citation_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation (id),
    CONSTRAINT fk_policy_citation_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT ck_policy_citation_score CHECK (relevance_score >= 0 AND relevance_score <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_policy_citation_rec ON policy_citation (recommendation_id);
CREATE INDEX ix_policy_citation_case ON policy_citation (case_id);
CREATE INDEX ix_policy_citation_document ON policy_citation (document_id, document_version);

CREATE TABLE review_disposition (
    id                   VARCHAR(36) NOT NULL,
    case_id              VARCHAR(36) NOT NULL,
    recommendation_id    VARCHAR(36) NULL,
    reviewer_id          VARCHAR(64) NOT NULL,
    decision             VARCHAR(16) NOT NULL,
    final_classification VARCHAR(40) NULL,
    final_route          VARCHAR(32) NULL,
    rationale            TEXT        NOT NULL,
    reviewed_at          DATETIME(6) NOT NULL,
    CONSTRAINT pk_review_disposition PRIMARY KEY (id),
    CONSTRAINT fk_review_disposition_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT fk_review_disposition_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation (id),
    CONSTRAINT ck_review_disposition_decision CHECK (decision IN ('ACCEPT', 'OVERRIDE', 'CLOSE')),
    CONSTRAINT ck_review_disposition_override CHECK (decision <> 'OVERRIDE'
        OR (final_classification IS NOT NULL AND final_route IS NOT NULL)),
    CONSTRAINT ck_review_disposition_rationale CHECK (CHAR_LENGTH(TRIM(rationale)) >= 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_review_disposition_case_reviewed ON review_disposition (case_id, reviewed_at);

-- Append-only: the application exposes no update/delete path for audit rows.
CREATE TABLE audit_event (
    id             VARCHAR(36)   NOT NULL,
    case_id        VARCHAR(36)   NULL,
    event_type     VARCHAR(40)   NOT NULL,
    actor_type     VARCHAR(16)   NOT NULL,
    actor_id       VARCHAR(64)   NOT NULL,
    from_status    VARCHAR(32)   NULL,
    to_status      VARCHAR(32)   NULL,
    details        VARCHAR(2000) NULL,
    correlation_id VARCHAR(64)   NULL,
    occurred_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT fk_audit_event_case FOREIGN KEY (case_id) REFERENCES fraud_case (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_audit_event_case_occurred ON audit_event (case_id, occurred_at);
CREATE INDEX ix_audit_event_type_occurred ON audit_event (event_type, occurred_at);
