#!/usr/bin/env bash
# Creates a sample eQMS project for manually testing template styles (EQMS-115).
# Usage: scripts/eqms-115-sample-project.sh <new-folder>
# Requires: a built `qms` (EQMS_QMS_PATH or target/release/qms), git and python3.
set -euo pipefail

target="${1:?usage: $0 <new-folder>}"
root="$(cd "$(dirname "$0")/.." && pwd)"
qms="${EQMS_QMS_PATH:-$root/target/release/qms}"
[ -x "$qms" ] || { echo "qms not found at $qms; run 'cargo build --release' or set EQMS_QMS_PATH" >&2; exit 1; }
[ ! -e "$target" ] || { echo "$target already exists" >&2; exit 1; }

mkdir -p "$target"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

git -C "$target" init --quiet
git -C "$target" config user.name "Sample User"
git -C "$target" config user.email "sample@example.invalid"
"$qms" init "$target" --name "Acme Devices" --accent "#C2410C" --seed acme >/dev/null

python3 - "$work" "$qms" "$target" <<'PY'
import hashlib, json, struct, subprocess, sys, zlib

work, qms, target = sys.argv[1:4]

def png(width, height, rgb):
    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
    rows = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))

mark = png(96, 48, (31, 58, 95))
open(f"{work}/mark.png", "wb").write(mark)

shown = json.loads(subprocess.check_output([qms, "template", "show", target, "generic", "--json"]))
template = shown["data"]["template"]
template.update(id="formal-sop", name="Formal SOP", version=1, document_type="SOP")
template["style"].update(
    body_font="serif", heading_font="sans", body_size_pt=12, heading_numbering=True,
    heading_color="#1F3A5F", table="banded", image_max_width_percent=60, image_align="left",
    cover_page=True, page_break_before_level=1,
    toc={"enabled": True, "depth": 2, "leaders": True, "placement": "after_cover"},
)
template["regions"]["header"]["centre"] = {"text": "", "image": {"kind": "asset", "sha256": hashlib.sha256(mark).hexdigest()}}
template["regions"]["header"]["right"] = {"text": "", "image": {"kind": "project_logo"}}
template["regions"]["footer"]["left"] = {"text": "{title} - {status}", "image": None}
json.dump(template, open(f"{work}/formal-sop.json", "w"), indent=2)
PY

"$qms" template install "$target" "$work/formal-sop.json" --asset "$work/mark.png" >/dev/null

body='## Purpose

This sample shows how a template changes the preview.

## Scope

| Item | Owner |
| --- | --- |
| Review | Quality |
| Approval | Management |

### Details

Plain text under a sub-heading.

## Content

Nothing else to see here.
'
printf '%s' "$body" | "$qms" document create "$target" --title "Formal sample (pinned)" --template formal-sop >/dev/null
printf '%s' "$body" | "$qms" document create "$target" --title "Plain sample (no template)" >/dev/null

echo "Sample project ready: $target"
echo "Open it in the desktop app: 'Formal sample (pinned)' renders with the Formal SOP style; 'Plain sample' uses the default."
