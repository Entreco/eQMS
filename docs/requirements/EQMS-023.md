---
title: Document authoring requirements
status: draft
related: [EQMS-023, UN-011, UN-012, UN-015]
---

# EQMS-023 requirements

| ID | Requirement |
|---|---|
| REQ-023-001 | Create Draft documents with generated stable IDs, required titles, canonical Markdown and reproducible hashes. Cancelled unsaved creation writes nothing. |
| REQ-023-002 | Isolate each document draft; create/save must preserve baseline refs, journal, working files and staged changes. |
| REQ-023-003 | Persist content and journal linkage atomically; reject stale saves and avoid revisions for unchanged content. |
| REQ-023-004 | Reconstruct document list, exact saved content, newest-first history and read-only diffs after restart. Reject revisions outside that document. |
| REQ-023-005 | Reject invalid schemas/metadata/IDs, unsafe paths, corrupt histories, missing identity and process failures explicitly; do not claim incomplete writes succeeded. |
| REQ-023-006 | Provide native human/JSON create/list/show/save/history/diff commands, with versioned envelopes and nonzero errors. |
| REQ-023-007 | Provide shared Compose document list/editor/history/compare; retain edits on save failure and warn on dirty navigation/project switch/close. |
| REQ-023-008 | Preserve existing init/verify/recover/index behavior and shared JVM/Wasm compilation; never present Draft as approved or published. |

These requirements support compact authoring (UN-011), exact diff/audit linkage
(UN-012), and native CLI dogfooding (UN-015). Review/signatures/publication and
reference integrity are excluded; this draft is not approved compliance evidence.
