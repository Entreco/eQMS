"""Measure observed CLI startup; not a controlled cold-cache experiment."""

import argparse
import json
import math
import platform
import statistics
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--binary", type=Path, default=Path("target/release/qms"))
parser.add_argument("--samples", type=int, default=50)
parser.add_argument("--warmups", type=int, default=5)
args = parser.parse_args()
if args.samples < 1 or args.warmups < 0:
    parser.error("samples must be positive and warmups nonnegative")
binary = args.binary.resolve(strict=True)


def measure(flag):
    start = time.perf_counter_ns()
    subprocess.run([str(binary), flag], check=True, stdout=subprocess.DEVNULL)
    return (time.perf_counter_ns() - start) / 1_000_000


hardware = platform.machine()
if platform.system() == "Darwin":
    hardware = subprocess.check_output(
        ["sysctl", "-n", "machdep.cpu.brand_string"], text=True
    ).strip()
result = {
    "platform": f"{platform.system()} {platform.release()}",
    "architecture": platform.machine(),
    "hardware": hardware,
    "compiler": subprocess.check_output(["rustc", "--version"], text=True).strip(),
    "revision": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "profile": "release (build with cargo build --workspace --release --locked)",
    "method": "serial subprocess elapsed wall time, output discarded; no cache eviction",
    "warmups": args.warmups,
    "sample_count": args.samples,
    "operations": {},
}
for flag in ["--version", "--help"]:
    first = measure(flag)
    for _ in range(args.warmups):
        measure(flag)
    samples = [measure(flag) for _ in range(args.samples)]
    result["operations"][flag] = {
        "first_observed_ms": first,
        "samples_ms": samples,
        "median_ms": statistics.median(samples),
        "p95_ms": sorted(samples)[math.ceil(len(samples) * 0.95) - 1],
    }
print(json.dumps(result, indent=2))
