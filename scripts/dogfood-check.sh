#!/usr/bin/env bash
# EQMS-016: import this repository's Markdown into a fresh workspace with the native CLI,
# validate every canonical draft and verify the ledger against a declared genesis checkpoint.
# The workspace is ephemeral: this checks tooling and importability, not a persistent audit trail.
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
qms=${QMS:-$root/target/release/qms}
[ -x "$qms" ] || { echo "qms binary not found: $qms (cargo build --release -p eqms-cli)" >&2; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
ws=$work/workspace

export GIT_AUTHOR_NAME="eQMS dogfood check" GIT_AUTHOR_EMAIL="dogfood@example.invalid"
export GIT_COMMITTER_NAME="$GIT_AUTHOR_NAME" GIT_COMMITTER_EMAIL="$GIT_AUTHOR_EMAIL"

cd "$root"
sources=(PLANNING.md AGENTS.md docs/requirements/*.md docs/architecture/*.md
  docs/operations/*.md docs/testing/*.md)

"$qms" init "$ws" >/dev/null
checkpoint=$(git -C "$ws" rev-list --max-parents=0 HEAD)
echo "Declared trust baseline (genesis): $checkpoint"

"$qms" --json document import "$ws" "${sources[@]}" >"$work/import.json"
"$qms" --json document import "$ws" "${sources[@]}" >"$work/reimport.json"

python3 - "$work/import.json" "$work/reimport.json" "${#sources[@]}" <<'PY'
import json, sys
first, again = (json.load(open(p))["data"]["imported"] for p in sys.argv[1:3])
count = int(sys.argv[3])
assert len(first) == len(again) == count, "imported count differs from source count"
assert not any(d["already_imported"] for d in first), "first import found existing documents"
assert all(d["already_imported"] for d in again), "re-import is not idempotent"
assert [d["id"] for d in first] == [d["id"] for d in again], "re-import changed document IDs"
assert all(d["status"] == "Draft" for d in first), "imported document is not Draft"
assert len({d["id"] for d in first}) == count, "duplicate document IDs"
print(f"Imported {count} documents as Draft; re-import idempotent")
PY

for id in $(python3 -c 'import json,sys;print(*[d["id"] for d in json.load(open(sys.argv[1]))["data"]["imported"]])' "$work/import.json"); do
  git -C "$ws" show "refs/heads/eqms-drafts/$id:documents/$id.md" |
    "$qms" document validate - >/dev/null || { echo "canonical validation failed: $id" >&2; exit 1; }
done
echo "All canonical draft files validate"

"$qms" verify "$ws" >/dev/null
"$qms" --json audit verify-ledger "$ws" --checkpoint "$checkpoint" >"$work/ledger.json"
python3 - "$work/ledger.json" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))["data"]
assert data["trust"] == "checkpoint_ancestry", data["trust"]
print(f"Ledger verified: {len(data['drafts'])} drafts, trust={data['trust']}")
PY
