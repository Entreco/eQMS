# EQMS-016: Dogfooding project documentation

The native `qms` CLI can hold this repository's own User Needs, design
decisions, requirements, test specifications, roadmap and process documents as
controlled drafts. The Markdown files in this repository remain the editing
source; a qms workspace is a separate Git repository. Imported documents are
**Draft** only: import is not review, approval or compliance evidence.

```text
qms init <workspace>                                   # empty dir or unborn Git repo; Git identity required
qms document import <workspace> <file.md>...           # lossless, idempotent Draft import
qms document list <workspace>
qms document validate <canonical-file|->               # exact canonical format check
qms document save <workspace> <id> --title <t> --expected <revision>   # new body on stdin
qms document history <workspace> <id>
qms document diff <workspace> <id> <from-revision> <to-revision>
qms verify <workspace>
qms audit verify-ledger <workspace> --checkpoint <full-genesis-commit>
```

## 1. Import

`import` keeps each file's content verbatim as the document body (including
front matter, headings and stable identifiers such as `UN-001`, `EQMS-016`,
`R016-01`, `T016-01`); only CR/CRLF line endings become LF. The title comes
from front-matter `title:`, else the first `# ` heading, else the file name.
The command reports each source's SHA-256, the new document ID/revision and the
identifiers found, and fails if stored content or identifiers differ from the
source. Re-running with unchanged files reports `already_imported` and creates
nothing. A changed source file imports as a new document; to record a new
revision of an existing one, use `save` with its current revision.

## 2. Validate

Saved canonical files live on each document's draft ref:

```text
git -C <workspace> show refs/heads/eqms-drafts/<id>:documents/<id>.md | qms document validate -
```

## 3. History and diff

`document history` lists newest-first saved revisions; `document diff` shows a
read-only unified diff between two full revision IDs from that history.

## 4. Integrity

`qms verify` checks the workspace baseline. `qms audit verify-ledger` checks
the complete recorded baseline and every draft history. Record the genesis
commit (`git -C <workspace> rev-list --max-parents=0 HEAD`) when the workspace
is created and store it independently; passing it as `--checkpoint` pins
ancestry. Without it only local consistency is reported. Neither mode proves
identity or approval, or detects deleted draft refs.

## 5. CI check

`scripts/dogfood-check.sh` (run by the Rust workflow after the release build;
set `QMS` to use another binary) performs steps 1–4 against a throwaway
workspace, using the genesis commit as the declared checkpoint, and fails on any
import, canonical-validation, idempotency, status or ledger error. It
demonstrates that the repository's documents remain importable and verifiable by
the shipped tooling. The workspace is regenerated every run, so it is not a
persistent audit trail; a long-lived documentation workspace with an externally
held checkpoint, real review/approval and signatures are future work.

See [design and risk assessment](../architecture/EQMS-016.md),
[requirements](../requirements/EQMS-016.md) and
[tests](../testing/EQMS-016.md).
