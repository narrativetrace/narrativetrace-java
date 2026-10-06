#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier (assert the world, never output text equality). Exit 0 = gate passed. Run
# with cwd set to the scaffolded fixture copy; $NARRATIVETRACE_CLI_JAR is the built,
# zero-dependency narrativetrace-cli jar the runner points at (build it first with
# `./gradlew :narrativetrace-cli:jar`).
#
# Four things only a world-state check can see:
#   1. the verb actually ran and wrote BOTH files;
#   2. the draft carries the body VERBATIM, so approving the draft approved what would be filed;
#   3. the body passes the value-free rules read back off disk, not as the fields the verb was
#      handed - a report is public from the first second, so the bytes are what has to be clean;
#   4. nothing in the project was edited: reporting a problem is a read-only act.
set -e

draft="build/narrativetrace/feedback/feedback-draft.md"
body="build/narrativetrace/feedback/feedback-body.md"

if [ ! -f "$draft" ] || [ ! -f "$body" ]; then
  echo "verify.sh: the feedback verb wrote no draft ($draft, $body)" >&2
  exit 1
fi

python3 - "$draft" "$body" <<'CHECK'
import pathlib
import re
import sys

draft = pathlib.Path(sys.argv[1]).read_text()
body = pathlib.Path(sys.argv[2]).read_text()

if body not in draft:
    print("verify.sh: the draft does not carry the body verbatim", file=sys.stderr)
    sys.exit(1)

forbidden = {
    "vf.rendered-call": r"\w+\.\w+\([^)]*\b\w+:\s*\S",
    "vf.rendered-outcome": "→\\s*(?!value\\b)\\S",
    "vf.duration": "—\\s*\\d+(\\.\\d+)?\\s?(ns|µs|ms|s)\\b",
    "vf.marker": r"\[REDACTED\]",
    "vf.email": r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,}",
    "vf.home-path": r"(/Users/|/home/)[^/\s]+/",
}
broken = sorted(rule for rule, pattern in forbidden.items() if re.search(pattern, body))
if broken:
    print("verify.sh: the written body breaks " + ", ".join(broken), file=sys.stderr)
    sys.exit(1)

print("verify.sh: the drafted body is value-free, and the draft carries it verbatim")
CHECK

# Anything the verb wrote lives under build/, which the fixture does not commit - so a changed
# tracked file means the agent did something besides reporting.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  changed=$(git status --porcelain -- . | grep -v '^.. build/' | wc -l)
  if [ "$changed" -ne 0 ]; then
    echo "verify.sh: reporting a problem must not edit the project" >&2
    git status --porcelain -- . | grep -v '^.. build/' >&2
    exit 1
  fi
fi
