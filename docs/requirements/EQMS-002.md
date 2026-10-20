---
title: EQMS-002 derived SQLite index requirements
status: draft
related: [EQMS-002, EQMS-005]
---

# EQMS-002 requirements

The SQLite index is disposable query state. Canonical Markdown and Git history
remain authoritative.

| ID | Requirement |
|---|---|
| R002-01 | The Rust index creates and upgrades its SQLite schema through ordered, transactional migrations; schema version and migration history must agree. |
| R002-02 | Rebuild indexes regular Markdown files under `documents/`, retaining their relative paths without rewriting valid filename characters or introducing path collisions. |
| R002-03 | Symlinked document directories/files are not followed; rebuild failure preserves the previously committed database transaction state. |
| R002-04 | Rebuilding the index does not mutate the canonical journal or Git history. |
| R002-05 | Unsupported future schema versions fail explicitly and do not downgrade the index. |

The implementation does not provide search ranking, a durable document store,
or historical snapshots; rebuilding from canonical files is the recovery path.
