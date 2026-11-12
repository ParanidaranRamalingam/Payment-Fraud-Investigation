# Modern UI Implementation Specification

## Product experience
Create a modern fraud-operations analyst console. It should look intentional and review-ready, not like a generated admin template. Favor information density with strong hierarchy, short readable lines, clear provenance and calm banking-style colors.

## Design system
### Tokens
Define CSS variables for background/surface, text/muted text, primary blue/teal, success, warning, danger, borders, radius, shadow, spacing and focus ring. Use one coherent sans-serif font stack available in the VM/browser with no required external CDN.

### Layout
- Top application header: product name, current mode (`RULES`/`AI`), health indicator and reviewer identity placeholder.
- Left navigation on desktop: Cases, New Case, Investigation, Policy, Audit; collapse gracefully at narrower widths.
- Main content constrained to a comfortable max width with responsive 12-column/grid layouts.
- Case pages get a persistent case summary strip with Case ID, state, route/risk and last updated.

### Components
Cards, badges, buttons, labelled fields, tabs where useful, timeline/stepper, responsive tables, evidence tiles, confidence meter/progress treatment, citation panel, audit timeline, inline validation, skeleton loaders and accessible toast/alert patterns.

## Pages
### 1. Case Intake
Two-column desktop form grouped into Customer, Transaction and Claim cards. Show synthetic-data notice prominently. Use field hints, masked examples, inline validation, sticky/clear primary `Start investigation` CTA and secondary save-draft action.

### 2. Investigation Workspace
Header contains case status and bounded-plan progress. Use a vertical timeline or stepper for tool execution. Each tool row/card shows icon, name, status badge, duration, trace ID and expandable redacted result. Place missing/unresolved information in a visible side panel. Never show hidden reasoning.

### 3. Recommendation
Use a high-priority summary card with classification, risk badge, proposed route, confidence visualization and `Human review required`. Under it show evidence links, policy checks, missing/contradictory information and concise rationale. Make the recommendation visibly advisory.

### 4. Evidence Workspace
Responsive grid of evidence cards by transaction/device/location/merchant/history/customer claim. Every card shows type, severity, concise result, provenance and timestamp. Use filters/chips for evidence source/severity without hiding IDs.

### 5. Policy Citations
Split view: citation list/table on left or top and selected passage detail on right/bottom. Show document ID, version, effective date, section, chunk, similarity score and passage. Make stale/unavailable/under-threshold results visually distinct. Explicit empty/abstention state.

### 6. Human Review
Two-pane review workspace where feasible: recommendation/evidence summary and reviewer action form. Offer allowed dispositions, require rationale, require acknowledgement of missing information where applicable, display final audit preview, and make submission the only authoritative decision action.

### Optional dashboard/landing page
If time permits, provide a case-list dashboard with summary counts and recent cases. It must not replace any required six screens.

## Responsive/accessibility
Desktop target should work cleanly around 1366x768 and common larger desktop sizes; remain usable at ~768px width. Avoid horizontal overflow for primary actions. Keyboard navigation, visible focus, field labels, semantic landmarks, sufficient contrast and non-color-only status are required.

## Asset/runtime constraint
The VM demonstration should not depend on an internet connection for CSS/fonts/icons. Bundle required static assets locally or use lightweight CSS/Unicode/inline accessible icons as appropriate. Do not use remote CDN dependencies as a runtime requirement.

## UX state matrix
Every async area supports: initial, loading, success, empty, partial, timeout/unavailable and retry. Errors explain what is unavailable without fabricating substitute evidence.

## UI tests
Verify intake validation, CSRF, encoding, reviewer rationale requirement, major keyboard flow, responsive primary controls, missing-policy abstention rendering, tool timeout rendering and provenance labels.
