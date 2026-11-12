-- Session 2: evidence provenance for deterministic tool results.
--   source       versioned provider that served the fact, e.g. 'synthetic-fixture:v1'
--   payload      canonical typed tool result (sorted key=value lines); only for AVAILABLE evidence
--   content_hash SHA-256 (lowercase hex) of tool + payload; identical synthetic inputs give identical hashes
-- Columns are nullable so evidence recorded before this migration stays valid.

ALTER TABLE evidence_item
    ADD COLUMN source       VARCHAR(64) NULL AFTER tool,
    ADD COLUMN content_hash VARCHAR(64) NULL AFTER summary,
    ADD COLUMN payload      TEXT        NULL AFTER content_hash,
    ADD CONSTRAINT ck_evidence_item_payload_available CHECK (content_hash IS NULL OR status = 'AVAILABLE'),
    ADD CONSTRAINT ck_evidence_item_payload_pair CHECK ((content_hash IS NULL) = (payload IS NULL));

CREATE INDEX ix_evidence_item_content_hash ON evidence_item (content_hash);
CREATE INDEX ix_tool_trace_case_tool ON tool_trace (case_id, tool);
