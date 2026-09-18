#!/usr/bin/env python3
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Pins the CORRECT behaviour of summarize.py's verdict (see ../reports/soak/
2026-09-16-one-hour.md, defects D1 and D4): missing evidence must fail the run, and a baseline
that ran traced (narration lines in its logs) must fail the run and say so. summarize.py is
invoked exactly as run-soak.sh invokes it (`python3 summarize.py <results_dir> <logs_dir>
<duration_seconds>`) against small, otherwise-healthy fixture result sets built per test, so each
test isolates exactly one missing/wrong piece of evidence.

Run: python3 -m unittest narrativetrace-soak/test_summarize.py -v
  (or, from narrativetrace-soak/: python3 -m unittest test_summarize -v)
"""
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SOAK_DIR = Path(__file__).resolve().parent
SUMMARIZE_PY = SOAK_DIR / "summarize.py"

# A duration that makes the expected poison count trivial to hit exactly (one probe every 30s).
DURATION_SECONDS = 30
EXPECTED_POISON = 1

# A logger line shaped exactly like the shop app's encoder pattern (see
# app/shop/src/main/resources/logback-spring.xml: "%d{ISO8601} [%thread] %-5level %logger{36}
# traceId=%X{traceId} spanId=%X{spanId} depth=%X{nt.depth} - %msg%n") for the "narrativetrace"
# logger at TRACE — i.e. real per-span narration, not merely the word "narrativetrace" appearing
# incidentally somewhere in a log line.
NARRATION_LINE = (
    "2026-09-16T20:07:56,123 [http-nio-8080-exec-1] TRACE narrativetrace "
    "traceId=4bf92f3577b34da6a3ce929d0e0e4736 spanId=00f067aa0ba902b7 depth=1 "
    "- ENTER CartLine.quantity()"
)


def _healthy_scenario_summary():
    # No "thresholds" key on any metric -> thresholds_passed() sees nothing to fail.
    return {
        "metrics": {
            "http_reqs": {"values": {"count": 10, "rate": 1.0}},
            "http_req_duration": {"values": {"med": 1.0, "p(90)": 2.0, "p(95)": 3.0, "p(99)": 4.0}},
        }
    }


def _write_json(path: Path, data) -> None:
    path.write_text(json.dumps(data), encoding="utf-8")


def _build_healthy_fixture(tmp_path: Path):
    """A results/ + logs/ pair for which today's summarize.py exits 0 — every test below starts
    from this and removes/corrupts exactly the one piece of evidence its behaviour is about, so a
    failure can only be attributed to that one thing."""
    results_dir = tmp_path / "results"
    logs_dir = tmp_path / "logs"
    results_dir.mkdir()
    logs_dir.mkdir()

    _write_json(results_dir / "baseline-scenario-summary.json", _healthy_scenario_summary())
    _write_json(results_dir / "traced-scenario-summary.json", _healthy_scenario_summary())
    (results_dir / "traced-stats.jsonl").write_text(
        json.dumps({"timestamp": "2026-09-16T20:14:05Z", "stats": {"droppedEventCount": 0}}) + "\n",
        encoding="utf-8",
    )

    shop_logs = logs_dir / "shop"
    shop_logs.mkdir()
    poison_lines = "\n".join(["some ordinary line"] + ["!! poison quantity=2000000000"] * EXPECTED_POISON)
    (shop_logs / "shop.log").write_text(poison_lines + "\n", encoding="utf-8")

    return results_dir, logs_dir


def _run_summarize(results_dir: Path, logs_dir: Path, duration_seconds: int = DURATION_SECONDS):
    return subprocess.run(
        [sys.executable, str(SUMMARIZE_PY), str(results_dir), str(logs_dir), str(duration_seconds)],
        capture_output=True,
        text=True,
        timeout=30,
    )


class ThresholdsPassedOfMissingSummaryTest(unittest.TestCase):
    """D4: `thresholds_passed(None)` must not report a pass — a summary that was never captured
    is not a summary whose thresholds held."""

    def test_thresholds_passed_of_a_missing_summary_is_not_true(self):
        spec = importlib.util.spec_from_file_location("summarize", SUMMARIZE_PY)
        summarize = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(summarize)
        self.assertIsNot(
            summarize.thresholds_passed(None), True,
            "thresholds_passed(None) reported a pass for a summary that was never captured",
        )


class MissingBaselineSummaryTest(unittest.TestCase):
    """D4: a missing baseline k6 summary (e.g. because k6 could not write its evidence, D2) must
    fail the run, not be silently treated as a pass."""

    def test_missing_baseline_summary_fails_the_run_and_names_the_missing_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            results_dir, logs_dir = _build_healthy_fixture(Path(tmp))
            (results_dir / "baseline-scenario-summary.json").unlink()

            completed = _run_summarize(results_dir, logs_dir)

            self.assertNotEqual(
                completed.returncode, 0,
                "summarize.py exited 0 despite a missing baseline-scenario-summary.json",
            )
            combined = completed.stdout + completed.stderr
            self.assertIn(
                "baseline-scenario-summary.json", combined,
                f"expected the missing file to be named in the output, got:\n{combined}",
            )


class MissingSamplerStatsTest(unittest.TestCase):
    """D4: missing sampler evidence (traced-stats.jsonl never written, e.g. because the sampler
    could not append to it, D2) must fail the run, not be silently treated as a pass."""

    def test_missing_sampler_stats_fails_the_run_and_names_the_missing_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            results_dir, logs_dir = _build_healthy_fixture(Path(tmp))
            (results_dir / "traced-stats.jsonl").unlink()

            completed = _run_summarize(results_dir, logs_dir)

            self.assertNotEqual(
                completed.returncode, 0,
                "summarize.py exited 0 despite a missing traced-stats.jsonl (no sampler evidence)",
            )
            combined = completed.stdout + completed.stderr
            self.assertIn(
                "traced-stats.jsonl", combined,
                f"expected the missing file to be named in the output, got:\n{combined}",
            )


class BaselineNarrationFailsTheRunTest(unittest.TestCase):
    """D1's oracle: a baseline run is only meaningful untraced (see README.md "Baseline"). If the
    baseline's shop log holds even one line of narrativetrace narration, the baseline ran traced
    and the whole comparison is invalid — summarize.py must fail the run and name D1, the same way
    the one-hour report's own baseline had to be discarded by hand."""

    def test_narration_in_the_baseline_log_fails_the_run_and_names_d1(self):
        with tempfile.TemporaryDirectory() as tmp:
            results_dir, logs_dir = _build_healthy_fixture(Path(tmp))
            baseline_shop_logs = results_dir / "baseline-logs" / "shop"
            baseline_shop_logs.mkdir(parents=True)
            (baseline_shop_logs / "shop.log").write_text(NARRATION_LINE + "\n", encoding="utf-8")

            completed = _run_summarize(results_dir, logs_dir)

            self.assertNotEqual(
                completed.returncode, 0,
                "summarize.py exited 0 despite narration lines in the baseline's shop log "
                "(the baseline ran traced)",
            )
            combined = completed.stdout + completed.stderr
            self.assertIn(
                "D1", combined,
                f"expected the failure to name D1 (the baseline-ran-traced defect), got:\n{combined}",
            )


if __name__ == "__main__":
    unittest.main()
