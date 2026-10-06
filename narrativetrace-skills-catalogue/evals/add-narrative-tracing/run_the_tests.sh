#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Grades the published init prompt's step 5 — "Add one test that traces a call with a deny-listed
# parameter and asserts the trace shows `[REDACTED]` for it" — as world state: the assertion is in a
# test source, and the test task is green. Shared by both init-prompt cases; run with cwd set to the
# scaffolded fixture copy.
#
# The literal is the one the doctor's trap.redaction-proof check greps for, quotes included, so this
# gate and that finding can never disagree about what counts as proof.
set -e

grep -rq '"\[REDACTED\]"' src/test 2>/dev/null || {
  echo "expected a test asserting the literal \"[REDACTED]\" — the prompt's step 5" >&2
  echo "--- what is under src/test ---" >&2
  find src/test -name "*.java" -o -name "*.kt" 2>/dev/null >&2 || echo "(nothing)" >&2
  exit 1
}

# Bounded like every other command here: a cold run resolves dependencies over the network, and a
# hanging suite has to fail this case rather than the harness.
log=$(mktemp)
if timeout 900 ./gradlew test --console=plain >"$log" 2>&1; then
  echo "run_the_tests.sh: the redaction assertion is there and ./gradlew test is green"
  rm -f "$log"
else
  echo "\`./gradlew test\` failed — the prompt's redaction test has to pass, not just exist" >&2
  cat "$log" >&2
  rm -f "$log"
  exit 1
fi
