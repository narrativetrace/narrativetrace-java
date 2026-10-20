#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier (skill-harness-design.md principle 1: assert the world, never output text
# equality). Exit 0 = gate passed. Run with cwd set to the scaffolded fixture copy;
# $NARRATIVETRACE_CLI_JAR is the built, zero-dependency narrativetrace-cli jar the runner points at
# (build it first with `./gradlew :narrativetrace-cli:jar`).
#
# This fixture has no test source set (B-92): the skill's step 3 never writes a file here, only
# prints the rendered trace to the console (the application's own log is the record once the
# SLF4J consumer is wired). So this grader runs the demo itself and reads its own run's output —
# world state of the grader's own run, never the agent's transcript.
set -e

grep -q "narrativetrace-proxy" build.gradle.kts || {
  echo "expected build.gradle.kts to declare narrativetrace-proxy" >&2
  exit 1
}

run_output=$(./gradlew run -q 2>&1) || {
  echo "expected ./gradlew run -q to exit 0; output was:" >&2
  printf '%s\n' "$run_output" >&2
  exit 1
}

printf '%s\n' "$run_output" | grep -q "OrderService.placeOrder" || {
  echo "expected the run's own output to contain a trace of the wrapped call (OrderService.placeOrder)" >&2
  exit 1
}

report=$(java -jar "$NARRATIVETRACE_CLI_JAR" doctor --json || true)

printf '%s\n' "$report" | python3 -c '
import json, sys
report = json.load(sys.stdin)
bad = [f for f in report.get("findings", []) if f["id"].startswith("toolchain.") and f.get("status") != "pass"]
if bad:
    print("expected every toolchain.* finding to hold:", bad, file=sys.stderr)
    sys.exit(1)
print("verify.sh: cold install produced a rendered trace and a clean toolchain")
'
