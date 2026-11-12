# RAG and Policy Grounding

## Metadata
Each policy/chunk preserves `documentId`, `title`, `version`, `effectiveDate`, `section`, `owner`, classification and chunk ID/index.

## Pipeline
Validate -> semantic chunking -> attach metadata -> embed/store -> build case-aware query -> retrieve configurable Top-K above threshold -> filter effective versions -> inject relevant passages -> persist/display exact citations.

## Baseline
`top-k: 4`; `similarity-threshold: 0.72`, configurable.

## Abstention
No approved passage above threshold means no procedural claim. Add the gap to missing information and route safely to manual review.

## Tests
Relevant match, paraphrase, no-match abstention, stale/version filtering, citation mapping and prompt-injection content treated as untrusted data.
