---
title: Document drafts and saved revisions
status: draft
related: [EQMS-003, EQMS-005, EQMS-020, EQMS-023, EQMS-030, EQMS-090]
---

# EQMS-023: Document authoring

## Boundary and prerequisite contracts

Rust owns authoring; native `qms` is its adapter. Shared Compose in `core:ui`
provides the editor; desktop owns process execution. This slice defines only the
document identity/encoding and draft event contracts needed from EQMS-003/020/030.
It does not complete their plugin, general parser, or generalized ledger scope.
Those tickets remain open. Workspace initialization and desktop create/open
foundations already exist on main.

A document has a generated `DOC-` ID followed by 32 lowercase hexadecimal
characters, a nonblank single-line title, a Markdown body, schema version 1,
and status `Draft`. The canonical UTF-8 file is `documents/<id>.md`:

```text
---
{"schema_version":1,"id":"DOC-...","title":"Quality policy","status":"Draft"}
---
Markdown body
```

The metadata line is compact JSON (valid YAML flow syntax) with fixed field
order; it safely quotes titles without a general YAML dependency. Body line
endings normalize to LF. The title is trimmed. Other body whitespace, including
the absence of a final newline, is retained. Unknown metadata fields, unsupported
schemas, noncanonical encodings, or non-Draft states are rejected. Exact
canonical bytes determine SHA-256 content hashes.

## Isolation and transaction

One `refs/heads/eqms-drafts/<id>` per document; no user-facing Git controls.
Creation starts at the selected project's verified HEAD. Saves extend only that
draft's ref. No working files, index entries, baseline refs, or baseline journal
are modified. Draft discovery reads refs; SQLite remains disposable and
baseline-only, not a second history store.

The user selected Git object construction rather than linked worktrees. Reuse
the workspace OS lock, require configured Git identity, build blobs/trees and a
single-parent commit using Git plumbing, validate the candidate, then
`update-ref` with the expected old commit. Creation expects an absent ref.
Save requires an exact full revision token, including for unchanged saves.
Ref publication uses `update-ref --no-deref` so a symbolic draft ref cannot
redirect writes to another branch. History parses/hashes the head journal once,
then checks ancestor journals against its verified prefixes. A changed save
reuses the verified parent when validating its newly constructed candidate.
This removes repeated journal decoding/hash-chain validation, not all cumulative
history cost: ancestor journal bytes are still read and compared, and each
revision's content/tree/ancestry is still checked.
Unchanged canonical content returns the existing saved revision.

Only the document and `.eqms/journal.jsonl` change in a draft commit.
`document.created` and `document.saved` events include document ID; the creation
event also records its base revision. Events bind the exact document hash;
their containing commit ID is returned separately, avoiding circular hashes.
Every draft extends the baseline journal at creation but has its own subsequent
chain. History verifies each draft commit's event, content, parent linkage and
changed paths back to its creation. Revision selectors must be full commit IDs
from that document's verified history, never arbitrary Git expressions.

Interruption before the atomic ref update leaves unreachable objects, not a
partially saved document; normal Git garbage collection may reclaim them.
Interruption after the update leaves a complete saved revision discoverable by
list/show/history. Retrying a save with an old token reports a conflict, never
overwrites. If a creation response is lost, consult the document list before
creating another document. There is no claim of exactly-once creation across a
lost client response. Existing `qms recover` remains for baseline operations.
No automatic reset, history rewriting, or journal text merging is permitted.

## CLI and UI contracts

All commands accept the project `path` first and global `--json`:

| Command | Additional arguments |
|---|---|
| `qms document create <path>` | `--title <text>`; body is UTF-8 standard input |
| `qms document list <path>` | None |
| `qms document show <path> <id>` | Optional `--revision <full-commit>` |
| `qms document save <path> <id>` | `--title <text> --expected <full-commit>`; body on stdin |
| `qms document history <path> <id>` | None |
| `qms document diff <path> <id> <from> <to>` | Two full saved commit IDs |

JSON envelopes use `schema_version: 1`, `status: "ok"`, command
`document.<operation>`, and `data`. Documents return ID, title, body, Draft
status, revision, content hash, event hash, configured author and local
timestamp. List returns document snapshots; history returns newest-first
snapshots; diff returns a read-only unified textual diff of canonical content.
Errors follow the existing CLI error envelope and nonzero exit conventions
(`INVALID_ARGUMENT`/2, `OPERATION_FAILED`/1). Human output remains readable.
Git identity and local time are claims, not authenticated identity/trusted time.

The native CLI separates process I/O (`main.rs`), parsing/dispatch (`cli.rs`),
response rendering (`output.rs`), and document/workspace command handlers
(`commands/`). Document handlers receive a `Read` input rather than opening
global stdin, enabling in-memory body and read-failure tests. This internal
refactor does not change command syntax, output schemas, exit codes or core
persistence controls; regression coverage guards protocol drift.

Desktop opens a verified project, lists drafts, and offers New document,
Save, History, and Compare. A new unsaved editor writes nothing until Save.
The UI retains edits on failure, guards duplicate operations, and asks before
discarding unsaved edits on document/project changes or close. Revision
comparison is read-only. The browser adapter is not supplied.

## Risk Management and Security Risk Management

Both apply to quality records, untrusted content, Git execution, and edit loss.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Overwritten or lost edits | Expected-revision CAS, shared lock, dirty-state warnings, retained failed edits | External tools may ignore locks; unsaved edits are not crash recovery |
| Draft mistaken for approved content | Explicit Draft labels; no publish action | Approval and publication controls are deferred |
| Partial save or unrelated data captured | Object-only writes, exact tree paths, candidate verification, atomic ref update | Unreachable objects may remain; lost create response requires list inspection |
| Path or revision injection | Generated IDs, full verified revision membership, literal pathspecs, direct argv/stdin, existing no-follow workspace validation | Local Git config/executable remains trusted |
| Tampered/divergent history | Verify canonical encoding, event chains, parent relations, exact hashes and changed paths | Whole-history replacement still requires a trusted independent baseline to detect |
| Identity/time overstated | Label configured identity and local time claims | No signatures, authenticated identity, or trusted time |

## Deferred publication

Each *published revision*, not each document, will have an immutable publication
record binding exact content, real review evidence, and resolved versioned
references. EQMS-021/022 and EQMS-040/041/042 own those follow-ups. Divergent
draft journals must not be merged as text. No distributed blockchain, consensus,
compliance approval, or baseline publication is delivered here.
