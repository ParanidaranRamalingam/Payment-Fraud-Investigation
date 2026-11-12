-- Session 3: case-scoped conversation memory for POST /api/cases/{caseId}/messages.
--   Every row belongs to exactly one case; replies may only reference evidence of that same case
--   (enforced by the application validator; the FK guarantees the evidence row exists).
--   role ANALYST   = authenticated analyst's message (untrusted data, never instructions)
--   role ASSISTANT = deterministic/AI reply; must point at the analyst message it answers and its processor mode
-- Rows are immutable once written (no update/delete path in the application).

CREATE TABLE case_message (
    id             VARCHAR(36) NOT NULL,
    case_id        VARCHAR(36) NOT NULL,
    role           VARCHAR(16) NOT NULL,
    author_id      VARCHAR(64) NOT NULL,
    content        TEXT        NOT NULL,
    reply_to_id    VARCHAR(36) NULL,
    processor_mode VARCHAR(16) NULL,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT pk_case_message PRIMARY KEY (id),
    CONSTRAINT fk_case_message_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT fk_case_message_reply_to FOREIGN KEY (reply_to_id) REFERENCES case_message (id),
    CONSTRAINT ck_case_message_role CHECK (role IN ('ANALYST', 'ASSISTANT')),
    CONSTRAINT ck_case_message_reply_to CHECK ((role = 'ASSISTANT') = (reply_to_id IS NOT NULL)),
    CONSTRAINT ck_case_message_processor CHECK ((role = 'ASSISTANT') = (processor_mode IS NOT NULL)),
    CONSTRAINT ck_case_message_processor_mode CHECK (processor_mode IS NULL OR processor_mode IN ('RULES', 'AI')),
    CONSTRAINT ck_case_message_content CHECK (CHAR_LENGTH(TRIM(content)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_case_message_case_created ON case_message (case_id, created_at);

CREATE TABLE case_message_evidence (
    message_id       VARCHAR(36) NOT NULL,
    item_order       INT         NOT NULL,
    evidence_item_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_case_message_evidence PRIMARY KEY (message_id, item_order),
    CONSTRAINT uq_case_message_evidence UNIQUE (message_id, evidence_item_id),
    CONSTRAINT fk_case_message_evidence_msg FOREIGN KEY (message_id) REFERENCES case_message (id),
    CONSTRAINT fk_case_message_evidence_item FOREIGN KEY (evidence_item_id) REFERENCES evidence_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
