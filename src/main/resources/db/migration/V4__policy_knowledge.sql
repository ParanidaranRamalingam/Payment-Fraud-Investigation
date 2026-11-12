-- Session 4: policy knowledge base (RAG) and retrieval traces.
--   policy_document         one row per (document_id, version); only APPROVED rows are retrievable
--   policy_chunk            document-aware chunks with a stored embedding (comma-separated floats in TEXT)
--   policy_retrieval_trace  one row per retrieval run during an investigation (auditable abstention)
--   policy_retrieval_hit    ranked candidates the retriever saw, each kept or dropped with a reason
-- Embeddings are stored as TEXT, never FLOAT/DOUBLE columns (see V2 note on DECIMAL discipline).
-- policy_citation gains `owner` so a citation preserves the accountable policy owner.

ALTER TABLE policy_citation
    ADD COLUMN owner VARCHAR(120) NOT NULL AFTER section;

CREATE TABLE policy_document (
    id             VARCHAR(36)  NOT NULL,
    document_id    VARCHAR(64)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    version        VARCHAR(32)  NOT NULL,
    effective_date DATE         NOT NULL,
    owner          VARCHAR(120) NOT NULL,
    classification VARCHAR(16)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    content_hash   VARCHAR(64)  NOT NULL,
    ingested_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_policy_document PRIMARY KEY (id),
    CONSTRAINT uq_policy_document_version UNIQUE (document_id, version),
    CONSTRAINT ck_policy_document_classification CHECK (classification IN ('INTERNAL', 'RESTRICTED')),
    CONSTRAINT ck_policy_document_status CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_policy_document_effective ON policy_document (document_id, effective_date);

CREATE TABLE policy_chunk (
    id               VARCHAR(36)  NOT NULL,
    document_row_id  VARCHAR(36)  NOT NULL,
    document_id      VARCHAR(64)  NOT NULL,
    document_version VARCHAR(32)  NOT NULL,
    chunk_id         VARCHAR(64)  NOT NULL,
    section          VARCHAR(120) NOT NULL,
    chunk_index      INT          NOT NULL,
    passage          TEXT         NOT NULL,
    content_hash     VARCHAR(64)  NOT NULL,
    embedding_model  VARCHAR(64)  NOT NULL,
    embedding        TEXT         NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT pk_policy_chunk PRIMARY KEY (id),
    CONSTRAINT uq_policy_chunk_chunk UNIQUE (chunk_id),
    CONSTRAINT fk_policy_chunk_document FOREIGN KEY (document_row_id) REFERENCES policy_document (id),
    CONSTRAINT ck_policy_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_policy_chunk_passage CHECK (CHAR_LENGTH(TRIM(passage)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_policy_chunk_document ON policy_chunk (document_id, document_version);
CREATE INDEX ix_policy_chunk_row ON policy_chunk (document_row_id);

CREATE TABLE policy_retrieval_trace (
    id                    VARCHAR(36)   NOT NULL,
    case_id               VARCHAR(36)   NOT NULL,
    query_hash            VARCHAR(64)   NOT NULL,
    query_term_count      INT           NOT NULL,
    top_k                 INT           NOT NULL,
    similarity_threshold  DECIMAL(5, 4) NOT NULL,
    candidates            INT           NOT NULL,
    matched               INT           NOT NULL,
    grounded              BOOLEAN       NOT NULL,
    abstain_code          VARCHAR(32)   NULL,
    embedding_model       VARCHAR(64)   NOT NULL,
    correlation_id        VARCHAR(64)   NULL,
    created_at            DATETIME(6)   NOT NULL,
    CONSTRAINT pk_policy_retrieval_trace PRIMARY KEY (id),
    CONSTRAINT fk_policy_retrieval_case FOREIGN KEY (case_id) REFERENCES fraud_case (id),
    CONSTRAINT ck_policy_retrieval_top_k CHECK (top_k >= 1),
    CONSTRAINT ck_policy_retrieval_threshold CHECK (similarity_threshold >= 0 AND similarity_threshold <= 1),
    CONSTRAINT ck_policy_retrieval_counts CHECK (candidates >= 0 AND matched >= 0 AND matched <= candidates),
    CONSTRAINT ck_policy_retrieval_abstain CHECK ((grounded = TRUE) = (abstain_code IS NULL)),
    CONSTRAINT ck_policy_retrieval_abstain_code CHECK (abstain_code IS NULL OR abstain_code IN
        ('NO_MATCH', 'BELOW_THRESHOLD', 'NO_EFFECTIVE_VERSION', 'STORE_ERROR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_policy_retrieval_case ON policy_retrieval_trace (case_id, created_at);

CREATE TABLE policy_retrieval_hit (
    trace_id         VARCHAR(36)   NOT NULL,
    item_order       INT           NOT NULL,
    chunk_id         VARCHAR(64)   NOT NULL,
    document_id      VARCHAR(64)   NOT NULL,
    document_version VARCHAR(32)   NOT NULL,
    similarity       DECIMAL(5, 4) NOT NULL,
    included         BOOLEAN       NOT NULL,
    drop_reason      VARCHAR(32)   NULL,
    CONSTRAINT pk_policy_retrieval_hit PRIMARY KEY (trace_id, item_order),
    CONSTRAINT fk_policy_retrieval_hit_trace FOREIGN KEY (trace_id) REFERENCES policy_retrieval_trace (id),
    CONSTRAINT ck_policy_retrieval_hit_score CHECK (similarity >= 0 AND similarity <= 1),
    CONSTRAINT ck_policy_retrieval_hit_reason CHECK ((included = TRUE) = (drop_reason IS NULL)),
    CONSTRAINT ck_policy_retrieval_hit_drop CHECK (drop_reason IS NULL OR drop_reason IN
        ('BELOW_THRESHOLD', 'NOT_APPROVED', 'STALE_VERSION', 'NOT_YET_EFFECTIVE', 'BEYOND_TOP_K'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
