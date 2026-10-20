#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier (skill-harness-design.md principle 1: assert the world, never output text
# equality). Exit 0 = gate passed. Run with cwd set to the scaffolded fixture copy;
# $NARRATIVETRACE_CLI_JAR is the built, zero-dependency narrativetrace-cli jar the runner points at
# (build it first with `./gradlew :narrativetrace-cli:jar`).
#
# This fixture already has tests (B-92's other branch): the skill's step 3 runs one EXISTING test
# through the test integration, which writes the per-test narrative files under
# build/narrativetrace/ — never a console demo main. This grader runs the suite itself and reads
# the files it actually left on disk, never the agent's transcript.
set -e

grep -q "narrativetrace-proxy" build.gradle.kts || {
  echo "expected build.gradle.kts to declare narrativetrace-proxy" >&2
  exit 1
}

grep -q "narrativetrace-junit5" build.gradle.kts || {
  echo "expected build.gradle.kts to declare the JUnit 5 test integration (narrativetrace-junit5)" >&2
  exit 1
}

./gradlew test -q || {
  echo "expected ./gradlew test -q to exit 0 — the fixture's own test must still pass" >&2
  exit 1
}

find build/narrativetrace -name "*.md" 2>/dev/null | grep -q . || {
  echo "expected at least one rendered .md trace under build/narrativetrace" >&2
  exit 1
}

find build/narrativetrace -name "*.nt" 2>/dev/null | grep -q . || {
  echo "expected at least one structural .nt trace under build/narrativetrace" >&2
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
print("verify.sh: an existing test produced a rendered trace file and a clean toolchain")
'
