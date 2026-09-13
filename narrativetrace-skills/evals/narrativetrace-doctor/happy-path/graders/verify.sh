#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier (skill-harness-design.md principle 1: assert the world, never output text
# equality). Exit 0 = gate passed. Run with cwd set to the scaffolded fixture copy;
# $NARRATIVETRACE_CLI_JAR is the built, zero-dependency narrativetrace-cli jar the runner points at
# (build it first with `./gradlew :narrativetrace-cli:jar`).
set -e

report=$(java -jar "$NARRATIVETRACE_CLI_JAR" doctor --json || true)

echo "$report" | python3 -c '
import json, sys
report = json.load(sys.stdin)
findings = report.get("findings", [])
if len(findings) != 11:
    print("expected 11 findings, got", len(findings), file=sys.stderr)
    sys.exit(1)
print("verify.sh: doctor report is well-formed and names all eleven findings")
'
