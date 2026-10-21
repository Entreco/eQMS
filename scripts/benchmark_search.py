"""Generate real saved draft refs, prepare through qms, and measure warm CLI search.

The disposable fixture uses Git fast-import, not an authoritative-data bypass.
Production preparation verifies every generated history before it is indexed.
CLI elapsed time excludes desktop debounce/decoding/rendering; report those
separately, never label this alone as end-to-end UI evidence.
"""

import argparse
import hashlib
import json
import math
import platform
import statistics
import subprocess
import sys
import time
from pathlib import Path


def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, **kwargs).stdout


def generate(binary, root, count, body_bytes):
    root.mkdir(parents=True, exist_ok=False)
    run("git", "-C", str(root), "init", "--quiet")
    run("git", "-C", str(root), "config", "user.name", "Search Benchmark")
    run("git", "-C", str(root), "config", "user.email", "benchmark@example.invalid")
    run(str(binary), "init", str(root))
    base = run("git", "-C", str(root), "rev-parse", "HEAD").decode().strip()
    genesis_bytes = (root / ".eqms/journal.jsonl").read_bytes()
    genesis = json.loads(genesis_bytes.splitlines()[-1])
    process = subprocess.Popen(
        ["git", "-C", str(root), "fast-import", "--quiet"],
        stdin=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    historical = 0
    current_bytes = 0
    try:
        for number in range(count):
            doc_id = f"DOC-{number:032x}"
            ref = f"refs/heads/eqms-drafts/{doc_id}"
            path = f"documents/{doc_id}.md"
            journal = genesis_bytes
            previous = genesis
            parent = base
            revisions = 2 if number % 100 == 0 else 1
            historical += revisions - 1
            for ordinal in range(revisions):
                old = revisions == 2 and ordinal == 0
                title = (
                    f"Archived legacy {number}" if old else
                    f"{['And - Some Document', 'Research And Development', 'R & D', 'Risk Management Matrix', 'Quality Policy'][number % 5]} {number}"
                )
                phrase = (
                    "legacyobsolete clause archived guidance. " if old else
                    "This and That. Risk management controls traceability verification evidence. "
                )
                body = (phrase * (body_bytes // len(phrase) + 1))[:body_bytes]
                if not old:
                    current_bytes += len(body.encode())
                content = b"---\n" + encoded({
                    "schema_version": 1, "id": doc_id, "title": title, "status": "Draft"
                }) + b"\n---\n" + body.encode()
                payload = {"document_id": doc_id}
                if ordinal == 0:
                    payload = {"base_revision": base, "document_id": doc_id}
                timestamp = int(genesis["timestamp"].split(".")[0]) + number * 2 + ordinal + 1
                event = {
                    "schema_version": 1,
                    "sequence": previous["sequence"] + 1,
                    "previous_hash": previous["hash"],
                    "timestamp": str(timestamp),
                    "event_type": "document.created" if ordinal == 0 else "document.saved",
                    "payload": payload,
                    "content_hashes": {path: "sha256:" + hashlib.sha256(content).hexdigest()},
                }
                event["hash"] = hashlib.sha256(encoded(event)).hexdigest()
                journal += encoded(event) + b"\n"
                mark = number * 2 + ordinal + 1
                author = "Andi" if number % 3 == 0 else "Researcher"
                process.stdin.write(
                    f"commit {ref}\nmark :{mark}\nauthor {author} <benchmark@example.invalid> {timestamp} +0000\ncommitter {author} <benchmark@example.invalid> {timestamp} +0000\ndata 6\nDraft\n\nfrom {parent}\n".encode()
                )
                for filename, data in [(path, content), (".eqms/journal.jsonl", journal)]:
                    process.stdin.write(
                        f"M 100644 inline {filename}\ndata {len(data)}\n".encode()
                        + data + b"\n"
                    )
                process.stdin.write(b"\n")
                parent = f":{mark}"
                previous = event
        process.stdin.close()
        stderr = process.stderr.read().decode()
        if process.wait() != 0:
            raise RuntimeError(f"git fast-import failed: {stderr}")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()
    fixture = {"documents": count, "current_body_bytes": current_bytes,
               "historical_revisions": historical, "body_bytes_per_revision": body_bytes}
    (root / ".eqms/search-benchmark.json").write_text(json.dumps(fixture))
    return fixture


def measure(binary, root, query):
    started = time.perf_counter_ns()
    output = run(str(binary), "search", str(root), "--query", query, "--json")
    elapsed = (time.perf_counter_ns() - started) / 1_000_000
    response = json.loads(output)["data"]
    return elapsed, response


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workspace", type=Path)
    parser.add_argument("--binary", type=Path, default=Path("target/release/qms"))
    parser.add_argument("--documents", type=int, default=10_000)
    parser.add_argument("--body-bytes", type=int, default=10_486)
    parser.add_argument("--samples", type=int, default=30)
    parser.add_argument("--warmups", type=int, default=3)
    parser.add_argument("--reuse", action="store_true", help="Reuse the existing prepared fixture")
    args = parser.parse_args()
    if min(args.documents, args.body_bytes, args.samples) < 1 or args.warmups < 0:
        parser.error("counts/bytes/samples must be positive; warmups nonnegative")
    binary = args.binary.resolve(strict=True)
    root = args.workspace.absolute()
    fixture = None
    preparation_ms = None
    if not args.reuse:
        fixture = generate(binary, root, args.documents, args.body_bytes)
        print("Generated fixture; verifying and preparing saved histories.", file=sys.stderr, flush=True)
        start = time.perf_counter_ns()
        prepared = json.loads(run(str(binary), "search", str(root), "--prepare", "--json"))
        preparation_ms = (time.perf_counter_ns() - start) / 1_000_000
        if prepared["data"]["indexed_revisions"] != args.documents + fixture["historical_revisions"]:
            raise RuntimeError("Preparation did not index the expected fixture")
    else:
        fixture = json.loads((root / ".eqms/search-benchmark.json").read_text())
    hardware = platform.machine()
    if platform.system() == "Darwin":
        hardware = run("sysctl", "-n", "machdep.cpu.brand_string").decode().strip()
    report = {
        "fixture": fixture, "preparation_ms": preparation_ms,
        "hardware": hardware, "platform": f"{platform.system()} {platform.release()}",
        "revision": run("git", "rev-parse", "HEAD").decode().strip(),
        "binary_sha256": hashlib.sha256(binary.read_bytes()).hexdigest(),
        "worktree_dirty": bool(run("git", "status", "--porcelain")),
        "profile": "release", "samples": args.samples, "warmups": args.warmups,
        "method": "serial real CLI subprocess wall time; no cache eviction; excludes UI overhead",
        "queries": {},
    }
    for query in ["And", "risk", "managment", "trace", "legacyobsolete", "zzzznomatch"]:
        first, result = measure(binary, root, query)
        expected_history = query in ("legacyobsolete", "zzzznomatch")
        if result["historical"] != expected_history:
            raise RuntimeError(f"Unexpected fallback behavior for {query}")
        groups = ["documents", "authors", "content"] if query == "And" else ["content"]
        if query == "zzzznomatch":
            if any(result[name]["hits"] for name in ("documents", "authors", "content")):
                raise RuntimeError("No-match fixture query unexpectedly matched")
        elif any(not result[group]["hits"] for group in groups):
            raise RuntimeError(f"Expected grouped matches are missing for {query}")
        for _ in range(args.warmups):
            measure(binary, root, query)
        samples = [measure(binary, root, query)[0] for _ in range(args.samples)]
        report["queries"][query] = {
            "first_observed_ms": first, "p50_ms": statistics.median(samples),
            "p95_ms": sorted(samples)[math.ceil(len(samples) * .95) - 1],
            "historical": result["historical"], "samples_ms": samples,
        }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
