#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier (skill-harness-design.md principle 1: assert the world, never output text
# equality). Exit 0 = gate passed. Run with cwd set to the scaffolded fixture copy;
# $NARRATIVETRACE_CLI_JAR is the built, zero-dependency narrativetrace-cli jar the runner points at
# (build it first with `./gradlew :narrativetrace-cli:jar`).
set -e

grep -q "narrativetrace-proxy" build.gradle.kts || {
  echo "expected build.gradle.kts to declare narrativetrace-proxy" >&2
  exit 1
}

find build/narrativetrace -name "*.md" 2>/dev/null | grep -q . || {
  echo "expected at least one rendered .md trace under build/narrativetrace" >&2
  exit 1
}

report=$(java -jar "$NARRATIVETRACE_CLI_JAR" doctor --json || true)

echo "$report" | python3 -c '
import json, sys
report = json.load(sys.stdin)
bad = [f for f in report.get("findings", []) if f["id"].startswith("toolchain.") and f.get("status") != "pass"]
if bad:
    print("expected every toolchain.* finding to hold:", bad, file=sys.stderr)
    sys.exit(1)
print("verify.sh: cold install produced a rendered trace and a clean toolchain")
'
