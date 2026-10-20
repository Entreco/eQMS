---
title: Git-backed workspace and derived index contract
status: implemented
related: [EQMS-002, EQMS-005]
---

# EQMS-005 / EQMS-002 — Workspace and index contract

## Canonical workspace

`qms init <path>` creates a standalone Git repository in an empty directory (or
an existing directory containing only an empty `.git` repository). It requires
Git author identity to be configured; it does not infer or invent an accountable
user. The initial commit records the workspace config and genesis journal entry.
Initialization does not imply signing, approval, or verified identity.

| Path | Purpose | Authority |
|---|---|---|
| `.eqms/config.json` | Workspace schema version (`1`) | Canonical, committed |
| `.eqms/journal.jsonl` | Append-only versioned audit events | Canonical, committed |
| `documents/**/*.md` | UTF-8 Markdown body (document schema version `1` is declared in config) | Canonical, committed when recorded |
| `modules/manifest.json` | Module manifest schema version (`1`) and module entries | Canonical, committed |
| `modules/` | Versioned module content | Canonical, committed |
| `.eqms/index.sqlite3` | Rebuildable document query index | Derived, ignored by Git |

The JSONL journal uses one compact JSON object per newline-terminated record.
Object fields have a fixed serialization order; map values use sorted keys.
Each record has a schema version, zero-based sequence, Unix-seconds timestamp
with nanosecond precision, event type, payload, canonical-path-to-content-hash
map, previous record hash, and its own hash. The SHA-256 hash covers the
deterministically serialized event fields excluding the hash field. Genesis has
sequence zero, no previous hash, and event type `workspace.genesis`. Content
hash references use `sha256:<64 lowercase hex digits>` and are restricted to
paths under `documents/` or `modules/`. A referenced file and its Git blob are
checked against the declared hash when recording the event.

Git commit IDs are deliberately not embedded in events, avoiding a circular
dependency between the journal event and the commit containing it. The event
hash chain detects changes relative to a trusted copy; replacing all Git and
journal history remains possible and no signer identity is established.
Timestamps are local system claims and are not trusted time. A trusted
checkpoint or independent copy is required to detect whole-history replacement;
this contract does not provide one.

## Mutations and recovery

Workspace journal mutations and index rebuilds take an exclusive OS file lock
stored inside `.git`. A journal update is appended and synced, then the journal
and all referenced content paths are committed together. A failed append/commit
is reported and is not treated as success.

`qms recover <path>` only recovers a newline-truncated final journal record or a
valid journal extension of committed history. It commits a complete pending
event with its referenced content. It refuses a divergent journal or a journal
with no complete record rather than discarding committed history. It can also
complete an interrupted initial commit if the config and genesis journal are
valid. Manual repair is required for other partial initialization states.

`qms verify <path>` checks config/journal schema and canonical encoding, the
complete hash chain, the Git root, and equality of config/journal with `HEAD`.
It also checks the latest event's referenced content hashes against Git `HEAD`.
It does not prove that the Git history has never been replaced, establish signer
identity, or validate the quality/compliance of document content.

## Derived SQLite index

SQLite opens or creates `.eqms/index.sqlite3` only when `qms index rebuild` or
the Rust index API is used. The database is ignored by Git and is disposable.
Schema version is tracked with SQLite `user_version` and a `schema_migrations`
table. Ordered migrations run one at a time in transactions; an unsupported
future version is rejected rather than downgraded.

Migration 1 creates a document table containing relative Markdown path, text,
and SHA-256 content hash. Rebuild scans regular Markdown files under
`documents/`, replaces indexed rows in one transaction, and leaves config,
journal, and Git history untouched. A rebuild failure rolls back the row
replacement. The index is a query substrate, not a second source of truth or a
historical store.

## Commands

- `qms init <path>` — initialize and commit a workspace
- `qms verify <path>` — validate the current workspace/journal state
- `qms recover <path>` — recover only supported interrupted operations
- `qms index rebuild <path>` — create/upgrade and rebuild the derived index

`--json` emits schema-versioned command results and errors; operational failures
exit nonzero. Help remains human-readable.
