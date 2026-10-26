---
title: Complete recorded ledger verification
status: draft
related: [EQMS-005, EQMS-016, EQMS-023, EQMS-030, EQMS-031]
---

# EQMS-031: Ledger verification

## Scope and trust

Rust core owns `audit::verify_ledger`; native CLI exposes
`qms audit verify-ledger <path> [--checkpoint <full-commit>] [--json]`.
Existing workspace/draft journal producers implement the minimum EQMS-030
contracts consumed here. Future trace, signature and release producers remain
outside this issue. Generic chain/content verification does not authenticate
their future event semantics, signatures or approvals.

Verification holds the existing workspace coordination lock, snapshots HEAD and
draft refs, then reads Git objects. It does not recover, rewrite, initialize an
index, contact remotes or publish drafts. External tools may ignore the lock.
Reject a shallow repository, replacement refs, legacy grafts and non-linear
baseline ancestry explicitly; complete verification of those history forms is
not implemented. Reject history-override environment variables
`GIT_SHALLOW_FILE`, `GIT_GRAFT_FILE` and `GIT_REPLACE_REF_BASE` as well.
Reject checked-out internal draft branches.

The optional checkpoint is a literal full lowercase SHA-1/SHA-256 commit ID,
not a ref/expression; it must occur in verified baseline ancestry. A caller must
obtain/preserve it independently. It pins ancestry only: it is not a trusted
timestamp, signature, approved release, or inventory of all prior drafts.
Without it, verification explicitly reports local consistency only. Deleted
draft refs and complete local history replacement cannot be detected without
an independent reference/inventory. No externally supplied trust is invented.

## Baseline algorithm

1. Snapshot the baseline commit and enumerate its complete linear ancestry,
   oldest first. Each commit must contain a regular journal blob.
2. Parse/verify each journal's canonical JSONL encoding, versions, zero-based
   sequence, previous hash and event hashes with the shared verifier.
3. Require each committed journal to extend the exact prior byte prefix; no
   truncation, divergent rewrite or removal of prior events is allowed. Empty
   extensions (commits without journal changes) are valid.
4. Maintain the latest declared content hash per canonical path. At each
   commit, check all current bindings against regular Git blobs, including
   every newly appended event's content references at that commit. Older
   bindings may be superseded by later events; there is no deletion-event
   semantics in this milestone, so silently deleting a bound file fails.
5. Apply existing current workspace config/manifest/journal verification after
   the chronological checks. Dirty config/manifest/journal state remains an
   explicit failure, not automatically repaired.

Multiple events in one commit must each bind to that commit's content; the
command does not invent intermediate snapshots. First encountered errors carry
baseline commit and available sequence/path context.

## Draft algorithm and diagnostics

Reuse the existing full draft history verifier against each captured draft
head in sorted document-ID order. It checks exact document bytes/hash, event
payloads, commit paths, single-parent history, journal ancestry and revisions.
Each document's creation baseline must belong to the verified baseline ancestry.
Drafts remain separate chains, not one merged or globally ordered journal.

Errors include document ID, captured head, and underlying verifier context.
Shared draft verification adds revision context to individual commit failures.
The shared journal parser reports the zero-based record/sequence position;
incomplete tails identify their first incomplete line. No success-shaped
partial report is returned. "First" means baseline chronology first, then
sorted documents and their existing newest-first verifier traversal, not a
claimed universal chronological order across divergent drafts.

## CLI report

Success JSON uses EQMS-101's schema-version-2 envelope, command
`audit.verify-ledger`, and `data` fields:

- `baseline_revision`, `baseline_commits`, `baseline_records`;
- `drafts`: sorted entries with `id`, `revision`, `saved_revisions`;
- `checkpoint`: supplied full ID or null;
- `trust`: `checkpoint_ancestry` or `local_consistency`.

Human output lists verified counts and explains that neither mode proves
identity/approval or detects deleted draft refs. No checkpoint means no
independent trust anchor. Usage errors exit 2; verification/I/O errors exit 1
with existing `OPERATION_FAILED` JSON errors on stderr. Errors leave stdout
empty. Help does not read stdin.

## Risk Management and Security Risk Management

Both apply because integrity results may influence reliance on quality records.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| A valid current chain hides historical corruption | Check all baseline prefixes/bindings and full saved draft histories | External writers may ignore the lock |
| Self-consistent replacement implies trust | Optional independent literal checkpoint; explicit trust labels | No identity/trusted time; deleted draft refs need independent inventory |
| Divergent drafts treated as one ledger | Verify isolated chains and known baseline ancestry | No global ordering/publication |
| Partial/rewritten Git graph hides evidence | Reject shallow/replacement/graft/merge history | Such graphs need future defined support |
| Verification silently repairs evidence | Read-only checks; coordination lock only | Git/filesystem remain trusted local infrastructure |

Manual review/testing is required; this draft is not approved evidence.
