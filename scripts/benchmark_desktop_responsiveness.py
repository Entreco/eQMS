#!/usr/bin/env python3
"""Create a disposable, verified project fixture for EQMS-094 desktop timings."""

import argparse
import json
import os
import subprocess
import tempfile
from pathlib import Path

from benchmark_search import generate


def run(binary, *arguments, input_text=None):
    result = subprocess.run(
        [str(binary), *map(str, arguments)],
        check=True,
        capture_output=True,
        text=True,
        input=input_text,
    )
    return json.loads(result.stdout)["data"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workspace", type=Path)
    parser.add_argument("--binary", type=Path, default=Path("target/release/qms"))
    parser.add_argument("--documents", type=int, default=8)
    parser.add_argument("--revisions", type=int, default=1)
    parser.add_argument("--body-bytes", type=int, default=128)
    parser.add_argument("--git-counts", action="store_true")
    parser.add_argument("--history-bundle", action="store_true")
    args = parser.parse_args()
    if min(args.documents, args.revisions, args.body_bytes) < 1:
        parser.error("document, revision, and body byte counts must be positive")

    binary = args.binary.resolve(strict=True)
    workspace = args.workspace.absolute()
    fixture = generate(binary, workspace, args.documents, args.body_bytes)
    document_id = "DOC-00000000000000000000000000000000"
    history = run(binary, "document", "history", "--json", workspace, document_id)
    initial_revisions = len(history)
    revisions = initial_revisions
    while revisions < args.revisions:
        current = history[0]
        body = f"{current['body']}\nEQMS-094 fixture revision {revisions + 1}.\n"
        run(
            binary,
            "document",
            "save",
            "--json",
            workspace,
            document_id,
            "--title",
            current["title"],
            "--expected",
            current["revision"],
            input_text=body,
        )
        history = run(binary, "document", "history", "--json", workspace, document_id)
        revisions = len(history)

    fixture.update({
        "dataset_version": "EQMS-094 synthetic v1",
        "documents": args.documents,
        "revisions_for_first_document": revisions,
        "historical_revisions": fixture["historical_revisions"] + revisions - initial_revisions,
        "body_bytes_per_initial_revision": args.body_bytes,
    })
    (workspace / ".eqms/search-benchmark.json").write_text(json.dumps(fixture, indent=2) + "\n")
    verified = run(binary, "verify", "--json", workspace)
    listed = run(binary, "document", "list", "--json", workspace)
    if verified["path"] != str(workspace.resolve()) or len(listed) != args.documents:
        raise RuntimeError("Generated fixture failed authoritative verification/listing.")
    if args.git_counts:
        history_commands = (
            [("history-snapshot", document_id)]
            if args.history_bundle
            else [("history", document_id), ("lifecycle-history", document_id)]
        )
        commands = [
            ("verify", "--json", workspace),
            ("document", "list", "--json", workspace),
            *(
                ("document", operation, "--json", workspace, identifier)
                for operation, identifier in history_commands
            ),
        ]
        counts = {}
        with tempfile.TemporaryDirectory(prefix="eqms-094-git-count-") as trace_directory:
            for arguments in commands:
                trace = Path(trace_directory) / "trace.json"
                trace.write_text("")
                environment = os.environ | {"GIT_TRACE2_EVENT": str(trace)}
                result = subprocess.run(
                    [str(binary), *map(str, arguments)],
                    check=True,
                    capture_output=True,
                    text=True,
                    env=environment,
                )
                events = [json.loads(line) for line in trace.read_text().splitlines()]
                operation = arguments[1] if arguments[0] == "document" else arguments[0]
                counts[operation] = sum(event.get("event") == "start" for event in events)
                if not result.stdout:
                    raise RuntimeError(f"No response from {operation}.")
        fixture["git_start_events_per_operation"] = counts
        (workspace / ".eqms/search-benchmark.json").write_text(json.dumps(fixture, indent=2) + "\n")
    print(json.dumps({"workspace": str(workspace), "fixture": fixture}, indent=2))


if __name__ == "__main__":
    main()
