#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier. Exit 0 = gate passed. Run with cwd set to the scaffolded fixture copy;
# $NARRATIVETRACE_CLI_JAR is the built, zero-dependency narrativetrace-cli jar the runner points at
# (build it first with `./gradlew :narrativetrace-cli:jar`).
set -e

report=$(java -jar "$NARRATIVETRACE_CLI_JAR" doctor --json || true)

echo "$report" | python3 -c '
import json, sys
report = json.load(sys.stdin)
by_id = {f["id"]: f for f in report.get("findings", [])}
finding = by_id.get("trap.redaction-proof")
if finding is None or finding.get("status") != "fail":
    print("expected trap.redaction-proof to fail on the redaction-gap fixture", file=sys.stderr)
    sys.exit(1)
print("verify.sh: doctor correctly flags the unproven redaction")
'
