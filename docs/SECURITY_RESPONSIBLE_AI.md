# Security and Responsible AI Controls

## Data
Synthetic/masked data only. Field allow-lists, redaction, output encoding and retention limits. No committed credentials.

## MySQL
Use a least-privilege application account limited to the capstone schema. DB host/port/name/user/password come from environment/config outside source. Use encrypted transport when the VM environment supports it. Do not expose MySQL publicly solely for the demo. Backups/dumps used for demonstration must contain only synthetic data.

## Web
Case-level authorization, role-controlled review actions, CSRF, safe errors, bounded inputs, markup/script rejection and secure session/cookie settings if authentication is implemented.

## AI
Typed allow-listed read-only tools, provenance, schema validation, exact policy citations and explicit abstention. Treat user statements and retrieved documents as untrusted data.

## Audit
Append-only application contract; reviewer identity/timestamp/disposition; no ordinary update/delete endpoint; payload hashes rather than sensitive payloads.

## Observability
Correlation IDs across investigation/tool/retrieval/model spans; record status/duration and safe summaries; do not log case-bearing raw prompts/responses by default.
