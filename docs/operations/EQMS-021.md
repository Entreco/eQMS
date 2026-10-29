# EQMS-021: Review and publish a document

The native `qms` CLI can move an existing draft through its lifecycle. Start
from the project baseline and use the exact current revision printed by
`qms document status` as the expected revision:

```text
qms document status <project-path> <document-id>
qms document transition <project-path> <document-id> --expected <revision> --to "In Review"
qms document transition <project-path> <document-id> --expected <revision> --to Approved
qms document transition <project-path> <document-id> --expected <revision> --to Effective
qms document lifecycle-history <project-path> <document-id>
```

Each successful transition returns the updated document and its new revision.
Use that revision for the next operation. `Effective` is the internal
publication state. From Effective, a document may transition to `Obsolete` or
`Superseded`; those states are terminal.

The allowed transitions are Draft -> In Review -> Approved -> Effective ->
Obsolete/Superseded. Any other transition, a stale expected revision, or a
content save after leaving Draft fails explicitly. Lifecycle events preserve
the canonical document bytes and are stored in the isolated draft history,
without changing the controlled baseline, project working tree, or index.
Commands support the CLI's normal human-readable and versioned JSON output.

Lifecycle events record a status change, not a cryptographic signature or
proof of reviewer authority. They do not provide authenticated identity,
trusted time, external distribution, or regulatory approval. Those controls
remain separate work.

The desktop Review tool provides these actions through the same native
contracts; see [document workspace usage](BuildAndRun.md#draft-documents).
