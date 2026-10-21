---
title: Git-backed workspace and derived index contract
status: draft
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

## Filesystem, mutation, and Git path safety

Initialization creates or validates the `.git` directory, acquires the
workspace lock there, and only then runs Git setup and rechecks that the
directory is still empty (or contains only an unborn repository). This
serializes Git setup as well as canonical-file creation across concurrent
initializers. The lock remains held through the initial commit and verification.
Canonical config, journal, manifest, and referenced content paths must be
regular files beneath the selected workspace; symlinked files or parent
directories are rejected. File access for mutations must remain anchored to
the workspace and must not follow substituted symlinks. Unix builds use
descriptor-relative `O_NOFOLLOW` access for each path component. Non-Unix
builds use reparse-point flags and component checks; concurrent parent-path
replacement has not been independently verified on those targets.

Content hashes describe the exact bytes in the working files. Before a journal
event or recovery commit advances `HEAD`, the bytes Git will store must match
those hashes; configured clean filters or line-ending conversion that changes
the bytes cause the operation to fail without advancing `HEAD`. Git path
arguments are treated literally so filenames containing glob characters cannot
stage unrelated files. On Unix, a backslash in a filename is preserved as a
filename character rather than rewritten as a directory separator.

Recovery validates the retained journal prefix, its relation to committed
history, and all files/config needed for the selected recovery path before
truncating or committing anything. Unsupported or divergent inputs remain
untouched. A pending update to `modules/manifest.json` is recoverable only when
the valid manifest is named with its exact content hash in the pending event.

## Risk assessment

Risk Management and Security Risk Management are applicable proportionately:
initialization and recovery mutate a local repository, while verification and
indexing consume local workspace data.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Concurrent initialization overwrites workspace state | Create the Git directory and lock before Git setup, recheck state while locked, hold through commit | External tools that ignore the lock can still modify the repository |
| Symlink substitution redirects reads or writes | Reject symlinked path components and use no-follow, workspace-anchored mutation handles | Full-history replacement by an actor with repository access is not prevented |
| Recovery truncates committed or unsupported journal data | Validate complete records, committed prefix, config, manifest, and pending hashes before writes | Recovery is limited to the documented interrupted operations |
| Git filters alter bytes after hashes are recorded | Compare Git's filtered blob bytes with the exact hashed bytes before commit | Workspaces with content-transforming filters are intentionally unsupported |
| Wildcard pathspec stages unreferenced files | Set literal pathspec handling for the shared Git command runner | Other Git clients and manual commands are outside this adapter's control |
| Index path normalization collides with valid names | Build index keys from platform path components; preserve Unix backslashes | Index rows remain derived and must be rebuilt after corruption |
| Git infers an unaccountable author identity | Require config-only author and committer identity | A configured identity is not independently authenticated |
| A non-Unix parent path is substituted between checks | Reject observed reparse points and use the platform open-reparse-point flag | Parent-swap resistance outside Unix has not been independently verified |

These controls reduce accidental or path-driven damage; they do not establish
identity, trusted time, independent backup, or protection against an attacker
who can replace the complete local Git history.

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

`--json` follows the shared schema-version-2 response contract in
[EQMS-101](EQMS-101.md), including structured command results and errors;
operational failures exit nonzero. Help remains human-readable.

## Document draft extension

[EQMS-023](EQMS-023.md) adds private `eqms-drafts/<document-id>` branches.
Authoring constructs committed blobs/trees directly under the existing
repository lock and advances only the document ref atomically; it never checks
out a draft or changes baseline working files/index entries. Every draft has
its own journal extension of its creation baseline. `qms verify` continues to
verify the selected baseline; document list/show/history/save verify the
selected drafts separately. The derived baseline index does not include private
draft contents. `qms recover` is unchanged: draft saves expose either a complete
revision or no ref update, not a partially appended baseline journal.
