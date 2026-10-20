---
title: EQMS-005 workspace and journal requirements
status: draft
related: [EQMS-005, EQMS-010, EQMS-101]
---

# EQMS-005 requirements

| ID | Requirement |
|---|---|
| R005-01 | Initialization creates the versioned workspace layout and genesis journal in an empty directory or an unborn Git repository, then records the canonical files in the initial commit. |
| R005-02 | Initialization requires explicitly configured Git author and committer identities; Git's operating-system identity inference is not accepted. |
| R005-03 | Concurrent initialization and workspace mutations are serialized by the workspace lock; initialization rechecks emptiness under the lock and holds it through verification. |
| R005-04 | On Unix, canonical workspace files and parent components are opened as regular, non-symlink paths through workspace-anchored no-follow handles. Non-Unix targets reject observed reparse points; concurrent parent-path replacement remains a documented, unverified limitation. |
| R005-05 | Journal records use canonical JSONL encoding, sequential IDs, a previous-record hash, and a SHA-256 event hash; declared content hashes match both working bytes and the exact Git blob bytes before `HEAD` advances. |
| R005-06 | Valid updates to `modules/manifest.json` are journaled and verified like other module content; unrelated or unreferenced modifications are rejected. |
| R005-07 | Recovery validates retained records, committed history, workspace metadata, and pending content before any truncation or commit; unsupported inputs remain unchanged. |
| R005-08 | Git pathspec arguments are literal so wildcard characters in filenames cannot stage unrelated paths. |
| R005-09 | CLI success and errors follow the versioned JSON contract when `--json` is requested; operational failures are nonzero and never reported as success. |
| R005-10 | Verification describes local repository consistency only; it does not claim authenticated identity, trusted time, protection from full-history replacement, or product compliance. |

Initialization/recovery mutate local files and Git state; verification reads
workspace data. Risk and Security Risk Management are applicable as recorded in
the [workspace design and risk assessment](../architecture/EQMS-005.md).
