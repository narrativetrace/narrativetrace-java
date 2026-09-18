#!/usr/bin/env python3
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Builds SUMMARY.md from a completed soak run's artifacts. Invoked by run-soak.sh — see
README.md "what SUMMARY.md contains". Exits non-zero if the traced phase's k6 thresholds failed
or the poison count is off by more than 5%, per README.md / the brief's gate design."""
import glob
import gzip
import json
import os
import re
import sys
from datetime import datetime, timezone


def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return None


def metric_value(summary, metric_name, value_key):
    if summary is None:
        return None
    return summary.get("metrics", {}).get(metric_name, {}).get("values", {}).get(value_key)


def fmt(value, suffix=""):
    if isinstance(value, (int, float)):
        return f"{value:.1f}{suffix}"
    return "n/a"


def read_stats_jsonl(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return rows


def count_poison_markers(logs_dir):
    count = 0
    pattern = re.compile(r"!! poison")
    for path in glob.glob(os.path.join(logs_dir, "**", "*.log*"), recursive=True):
        opener = gzip.open if path.endswith(".gz") else open
        try:
            with opener(path, "rt", encoding="utf-8", errors="ignore") as f:
                count += sum(1 for line in f if pattern.search(line))
        except OSError:
            continue
    return count


def count_baseline_narration(results_dir):
    # D1's oracle: a baseline run is only meaningful untraced (see README.md "Baseline"). Any
    # narrativetrace narration in the copied baseline logs (run-soak.sh's `cp -r logs
    # results/baseline-logs`, right after phase 1) means the agent was attached and the baseline
    # ran traced — the same defect the one-hour report's own baseline had to be discarded by hand
    # for.
    count = 0
    pattern = re.compile(r"\bTRACE\s+narrativetrace\b")
    baseline_logs_dir = os.path.join(results_dir, "baseline-logs")
    if not os.path.isdir(baseline_logs_dir):
        return count
    for path in glob.glob(os.path.join(baseline_logs_dir, "**", "*.log*"), recursive=True):
        opener = gzip.open if path.endswith(".gz") else open
        try:
            with opener(path, "rt", encoding="utf-8", errors="ignore") as f:
                count += sum(1 for line in f if pattern.search(line))
        except OSError:
            continue
    return count


def count_rolls(status_path):
    if not os.path.exists(status_path):
        return 0
    with open(status_path, encoding="utf-8", errors="ignore") as f:
        return sum(1 for line in f if "roll" in line.lower())


def bytes_written(logs_dir):
    total = 0
    for root, _dirs, files in os.walk(logs_dir):
        for name in files:
            total += os.path.getsize(os.path.join(root, name))
    return total


def thresholds_passed(summary):
    # A summary that was never captured is not a summary whose thresholds held — evidence missing
    # is a failure, never a pass (D4).
    if summary is None:
        return False
    for metric in summary.get("metrics", {}).values():
        thresholds = metric.get("thresholds")
        if not thresholds:
            continue
        for outcome in thresholds.values():
            ok = outcome.get("ok", True) if isinstance(outcome, dict) else bool(outcome)
            if not ok:
                return False
    return True


def latency_row(label, summary):
    med = metric_value(summary, "http_req_duration", "med")
    p90 = metric_value(summary, "http_req_duration", "p(90)")
    p95 = metric_value(summary, "http_req_duration", "p(95)")
    p99 = metric_value(summary, "http_req_duration", "p(99)")
    return f"| {label} | {fmt(med)} | {fmt(p90)} | {fmt(p95)} | {fmt(p99)} |"


def throughput_line(label, summary):
    if summary is None:
        return f"- {label}: no summary captured"
    reqs = summary.get("metrics", {}).get("http_reqs", {}).get("values", {})
    count = reqs.get("count")
    rate = reqs.get("rate")
    return f"- {label}: {count if count is not None else 'n/a'} requests, {fmt(rate, '/s')}"


def main():
    results_dir, logs_dir, duration_seconds = sys.argv[1], sys.argv[2], int(sys.argv[3])

    baseline_summary = load_json(os.path.join(results_dir, "baseline-scenario-summary.json"))
    traced_summary = load_json(os.path.join(results_dir, "traced-scenario-summary.json"))
    # Only the traced phase's samples — see compose.yaml's sampler service comment: baseline and
    # traced write to separate files precisely so a "first vs last" comparison never crosses the
    # container restart between phases.
    stats_rows = read_stats_jsonl(os.path.join(results_dir, "traced-stats.jsonl"))

    expected_poison = max(1, duration_seconds // 30)
    observed_poison = count_poison_markers(logs_dir)
    poison_off_pct = (
        abs(observed_poison - expected_poison) / expected_poison * 100 if expected_poison else 0
    )

    # D4: missing evidence must fail the run, never be silently treated as a pass.
    missing_evidence = []
    if baseline_summary is None:
        missing_evidence.append("baseline-scenario-summary.json")
    if traced_summary is None:
        missing_evidence.append("traced-scenario-summary.json")
    if not stats_rows:
        missing_evidence.append("traced-stats.jsonl")

    baseline_narration_count = count_baseline_narration(results_dir)

    lines = [f"# SUMMARY — soak smoke run ({datetime.now(timezone.utc).isoformat()})", ""]

    lines += [
        "## Latency percentiles (http_req_duration, ms)",
        "",
        "| phase | p50 (med) | p90 | p95 | p99 |",
        "|---|---|---|---|---|",
        latency_row("tracing off (baseline)", baseline_summary),
        latency_row("tracing on (traced)", traced_summary),
        "",
        "## Throughput",
        "",
        throughput_line("baseline", baseline_summary),
        throughput_line("traced", traced_summary),
        "",
        "## Drop count over time (DualPathPipeline, via /soak/stats)",
        "",
    ]

    if stats_rows:
        first, last = stats_rows[0]["stats"], stats_rows[-1]["stats"]
        lines.append(f"- first sample: droppedEventCount={first.get('droppedEventCount')}")
        lines.append(f"- last sample: droppedEventCount={last.get('droppedEventCount')}")
        lines.append(f"- {len(stats_rows)} samples over the run")
    else:
        lines.append("- no stats.jsonl samples captured")

    lines += ["", "## Heap / thread / fd — first vs last sample", ""]
    if stats_rows:
        first, last = stats_rows[0]["stats"], stats_rows[-1]["stats"]
        for key in ("heapUsedAfterLastGcBytes", "liveThreadCount", "openFileDescriptorCount"):
            lines.append(f"- {key}: {first.get(key)} -> {last.get(key)}")
    else:
        lines.append("- no stats.jsonl samples captured")

    lines += [
        "",
        "## Poison probe",
        "",
        f"- expected ~{expected_poison} (one every 30s over {duration_seconds}s)",
        f'- observed {observed_poison} ("!! poison" markers across the rolled logs)',
        f"- off by {poison_off_pct:.1f}%",
        "",
        "## Rolling appender",
        "",
        f"- roll events (shop appender status log): "
        f"{count_rolls(os.path.join(logs_dir, 'shop', 'shop-appender-status.log'))}",
        f"- bytes written under {logs_dir}: {bytes_written(logs_dir)}",
    ]

    print("\n".join(lines))

    if missing_evidence:
        print(f"FAIL: missing evidence: {', '.join(missing_evidence)}", file=sys.stderr)
    if baseline_narration_count:
        print(
            f"FAIL (D1): the baseline's logs hold {baseline_narration_count} narrativetrace "
            "narration line(s) — the baseline ran traced, invalidating the comparison",
            file=sys.stderr,
        )

    if (
        missing_evidence
        or baseline_narration_count
        or not thresholds_passed(traced_summary)
        or poison_off_pct > 5
    ):
        sys.exit(1)


if __name__ == "__main__":
    main()
