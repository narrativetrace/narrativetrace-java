#!/usr/bin/env python3
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Pins the CORRECT behaviour of run-soak.sh's phase-1 baseline (see ../reports/soak/
2026-09-16-one-hour.md, defects D1-D3). run-soak.sh is never executed against real Docker here:
a fake `docker` shim is put first on PATH that records every invocation's argv and the
TRACING_ENABLED/STATS_FILE env it saw, then exits 0 immediately — this lets the whole script run
to completion in well under a second and keeps wall-clock/scheduler timing out of the test
entirely (a fake `uptime` shim reports a fixed low load average for the same reason: the real
host's load average is not a legitimate test input). The script itself, its guards, and
summarize.py are copied UNMODIFIED into a throwaway fixture "repo" (its own git checkout, its own
gradle.properties, stub jars matching run-soak.sh's precondition globs) so the test never depends
on — or dirties — this actual repository's working tree.

Run: python3 -m unittest narrativetrace-soak/test_run_soak.py -v
  (or, from narrativetrace-soak/: python3 -m unittest test_run_soak -v)
"""
import os
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

SOAK_DIR = Path(__file__).resolve().parent
REPO_ROOT = SOAK_DIR.parent
NARRATIVETRACE_VERSION = "0.2.3"

FAKE_DOCKER = textwrap.dedent(
    """\
    #!/usr/bin/env bash
    # Records the call (TRACING_ENABLED env + full argv) and exits 0 instantly — no real
    # container is ever started, so the harness under test runs in well under a second.
    {
      printf 'TRACING_ENABLED=%s\\t' "${TRACING_ENABLED-<unset>}"
      printf '%s\\n' "$*"
    } >> "$DOCKER_LOG"
    exit 0
    """
)

FAKE_UPTIME = textwrap.dedent(
    """\
    #!/usr/bin/env bash
    # A fixed, low load average — run-soak.sh's own load-average guard must never depend on the
    # test host's real, fluctuating load (that would make this test flaky by construction).
    echo "12:00  up 1 day,  1 user,  load average: 0.10, 0.20, 0.30"
    """
)


def _write_executable(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def build_fixture_repo(tmp_path: Path) -> Path:
    """Builds a throwaway repo laid out like this one (gradle.properties, the agent's standalone
    jar, the two app bootJars, run-soak.sh and summarize.py copied verbatim) so run-soak.sh's own
    guards (clean tree, jars present) pass without ever touching the real repository. Returns the
    fixture's narrativetrace-soak/ directory."""
    fixture_repo = tmp_path / "repo"
    fixture_repo.mkdir()
    (fixture_repo / "gradle.properties").write_text(
        f"narrativetraceVersion={NARRATIVETRACE_VERSION}\n", encoding="utf-8"
    )

    agent_libs = fixture_repo / "narrativetrace-agent" / "build" / "libs"
    agent_libs.mkdir(parents=True)
    (agent_libs / f"narrativetrace-agent-{NARRATIVETRACE_VERSION}-standalone.jar").write_bytes(b"stub")

    fixture_soak = fixture_repo / "narrativetrace-soak"
    fixture_soak.mkdir()
    # Mirrors this repo's own .gitignore (narrativetrace-soak/logs, results, .soak.lock,
    # .agent-jar) — without it, run-soak.sh's own `touch "$LOCK_FILE"` (before its git-dirty
    # guard runs) would make the fixture repo look dirty to itself.
    (fixture_soak / ".gitignore").write_text(
        "logs/\nresults/\n.soak.lock\n.agent-jar/\n", encoding="utf-8"
    )
    shutil.copy(SOAK_DIR / "run-soak.sh", fixture_soak / "run-soak.sh")
    (fixture_soak / "run-soak.sh").chmod(0o755)
    shutil.copy(SOAK_DIR / "summarize.py", fixture_soak / "summarize.py")

    shop_libs = fixture_soak / "app" / "shop" / "build" / "libs"
    shop_libs.mkdir(parents=True)
    (shop_libs / f"shop-{NARRATIVETRACE_VERSION}.jar").write_bytes(b"stub")
    notify_libs = fixture_soak / "app" / "notify" / "build" / "libs"
    notify_libs.mkdir(parents=True)
    (notify_libs / f"notify-{NARRATIVETRACE_VERSION}.jar").write_bytes(b"stub")

    # compose.yaml is never actually read (docker itself is a stub below), but run-soak.sh
    # references it by path on every invocation, so a placeholder keeps the fixture honest.
    (fixture_soak / "compose.yaml").write_text("services: {}\n", encoding="utf-8")

    git_env = {**os.environ, "GIT_AUTHOR_NAME": "fixture", "GIT_AUTHOR_EMAIL": "fixture@example.invalid",
               "GIT_COMMITTER_NAME": "fixture", "GIT_COMMITTER_EMAIL": "fixture@example.invalid"}
    subprocess.run(["git", "init", "-q"], cwd=fixture_repo, check=True, env=git_env)
    subprocess.run(["git", "add", "-A"], cwd=fixture_repo, check=True, env=git_env)
    subprocess.run(["git", "commit", "-q", "-m", "fixture"], cwd=fixture_repo, check=True, env=git_env)

    return fixture_soak


def run_soak_with_fake_docker(fixture_soak: Path, tmp_path: Path, profile: str = "smoke"):
    """Runs run-soak.sh to completion (real docker never invoked) and returns
    (completed_process, docker_log_lines, results_dir)."""
    bin_dir = tmp_path / "fakebin"
    bin_dir.mkdir()
    _write_executable(bin_dir / "docker", FAKE_DOCKER)
    _write_executable(bin_dir / "uptime", FAKE_UPTIME)

    docker_log = tmp_path / "docker-invocations.log"
    docker_log.write_text("", encoding="utf-8")

    env = dict(os.environ)
    env.pop("TRACING_ENABLED", None)
    env["PATH"] = f"{bin_dir}:{env['PATH']}"
    env["DOCKER_LOG"] = str(docker_log)

    completed = subprocess.run(
        ["bash", "run-soak.sh", profile],
        cwd=fixture_soak,
        env=env,
        capture_output=True,
        text=True,
        timeout=60,
    )
    lines = [line for line in docker_log.read_text(encoding="utf-8").splitlines() if line]
    return completed, lines, fixture_soak / "results"


class DockerCall:
    """One recorded `docker ...` invocation."""

    def __init__(self, line: str):
        env_part, _, argv_part = line.partition("\t")
        self.tracing_enabled = env_part.removeprefix("TRACING_ENABLED=")
        self.argv = argv_part
        self.tokens = argv_part.split()

    def is_up_shop_notify(self) -> bool:
        return {"up", "shop", "notify"}.issubset(self.tokens) and "compose" in self.tokens

    def is_sampler_run(self) -> bool:
        return "sampler" in self.tokens and "run" in self.tokens

    def is_k6_run(self) -> bool:
        return "k6" in self.tokens and "run" in self.tokens and "compose" in self.tokens

    def is_down(self) -> bool:
        return "down" in self.tokens and "up" not in self.tokens and "run" not in self.tokens

    def stops_sampler(self) -> bool:
        return "sampler" in self.tokens and ("stop" in self.tokens or "rm" in self.tokens)

    def __repr__(self):
        return f"DockerCall(TRACING_ENABLED={self.tracing_enabled!r}, argv={self.argv!r})"


class Phase1BaselineRunsFullyUntracedTest(unittest.TestCase):
    """D1 (reports/soak/2026-09-16-one-hour.md): the baseline phase must reach every compose
    invocation with TRACING_ENABLED=false, and the sampler/k6 invocations must not recreate
    shop/notify (docker compose run --no-deps)."""

    def test_phase1_baseline_runs_fully_untraced_without_recreating_dependencies(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fixture_soak = build_fixture_repo(tmp_path)
            _, lines, _ = run_soak_with_fake_docker(fixture_soak, tmp_path)
            calls = [DockerCall(line) for line in lines]

            # Phase 1 is everything logged before the first `docker compose down` (phase 1's own
            # teardown, run-soak.sh line 125) — phase 2's calls come after it.
            first_down_index = next(i for i, c in enumerate(calls) if c.is_down())
            phase1_calls = calls[:first_down_index]

            up_calls = [c for c in phase1_calls if c.is_up_shop_notify()]
            sampler_calls = [c for c in phase1_calls if c.is_sampler_run()]
            k6_calls = [c for c in phase1_calls if c.is_k6_run()]

            self.assertEqual(len(up_calls), 1, f"expected exactly one phase-1 up call, saw {phase1_calls}")
            self.assertEqual(len(sampler_calls), 1, f"expected exactly one phase-1 sampler run, saw {phase1_calls}")
            self.assertEqual(len(k6_calls), 1, f"expected exactly one phase-1 k6 run, saw {phase1_calls}")

            self.assertEqual(
                up_calls[0].tracing_enabled, "false",
                "phase 1's `docker compose up` must run with TRACING_ENABLED=false",
            )
            self.assertEqual(
                sampler_calls[0].tracing_enabled, "false",
                "phase 1's sampler `docker compose run` must see TRACING_ENABLED=false too — "
                "otherwise compose.yaml's ${TRACING_ENABLED:-true} default recreates shop/notify "
                "traced (D1)",
            )
            self.assertEqual(
                k6_calls[0].tracing_enabled, "false",
                "phase 1's k6 `docker compose run` must see TRACING_ENABLED=false too — same D1 "
                "config-drift recreate risk as the sampler run",
            )
            self.assertIn(
                "--no-deps", sampler_calls[0].tokens,
                "phase 1's sampler run must pass --no-deps so it cannot recreate shop/notify",
            )
            self.assertIn(
                "--no-deps", k6_calls[0].tokens,
                "phase 1's k6 run must pass --no-deps so it cannot recreate shop/notify",
            )


class ResultsDirectoryIsWorldWritableTest(unittest.TestCase):
    """D2: results/ must be writable by the k6 (uid 12345) and curl/sampler (uid 100) containers,
    neither of which shares a uid or group with the host user who creates the directory — so it
    must be created world-writable (the run's own worked-around fix was `chmod a+rwx results`)."""

    def test_results_directory_is_created_world_writable(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fixture_soak = build_fixture_repo(tmp_path)
            _, _, results_dir = run_soak_with_fake_docker(fixture_soak, tmp_path)

            self.assertTrue(results_dir.is_dir(), "run-soak.sh must have created results/")
            mode = stat.S_IMODE(results_dir.stat().st_mode)
            self.assertEqual(
                mode & 0o777, 0o777,
                f"results/ must be world-writable (mode a+rwx) so uid 12345 (k6) and uid 100 "
                f"(curl/sampler) can write their evidence into it; got {oct(mode)}",
            )


class SamplerDoesNotOutliveItsPhaseTest(unittest.TestCase):
    """D3: `docker compose run --rm -d sampler` starts a one-off container that plain
    `docker compose down` does not stop — phase 1's teardown must explicitly stop/remove it
    before phase 2 starts, or it keeps appending traced samples to the baseline's stats file."""

    def test_phase1_sampler_is_stopped_before_phase2_starts(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fixture_soak = build_fixture_repo(tmp_path)
            _, lines, _ = run_soak_with_fake_docker(fixture_soak, tmp_path)
            calls = [DockerCall(line) for line in lines]

            up_indices = [i for i, c in enumerate(calls) if c.is_up_shop_notify()]
            self.assertEqual(len(up_indices), 2, f"expected phase-1 and phase-2 up calls, saw {calls}")
            phase1_sampler_start = next(i for i, c in enumerate(calls) if c.is_sampler_run())
            phase2_up_index = up_indices[1]

            stop_indices = [
                i for i, c in enumerate(calls)
                if c.stops_sampler() and phase1_sampler_start < i < phase2_up_index
            ]
            self.assertTrue(
                stop_indices,
                "expected a docker invocation stopping/removing the sampler between phase 1's "
                f"sampler start and phase 2's up call, saw: {calls[phase1_sampler_start:phase2_up_index + 1]}",
            )


if __name__ == "__main__":
    unittest.main()
