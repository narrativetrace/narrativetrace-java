#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
set -eu

verifier="$(dirname "$0")/../../verify_report.py"
json="build/narrativetrace/clarity-results.json"
markdown="build/narrativetrace/clarity-report.md"
# The agent must have produced a real test report before the grader rebuilds anything.
python3 "$verifier" "$json" "$markdown"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat > "$work/policy.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.register('verifyClarityPolicy') {
        doLast {
            def gate = rootProject.tasks.named('clarityCheck').get()
            assert gate.minScore.get() == 0.50d : 'existing minScore was changed'
            assert !gate.warnOnly.get() : 'enforcement was made advisory'
            assert rootProject.extensions.getByName('narrativeTrace').enabled.get()
        }
    }
}
GRADLE
./gradlew -I "$work/policy.gradle" verifyClarityPolicy clean clarityCheck --console=plain
python3 "$verifier" "$json" "$markdown"

cat > "$work/strict.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    if (rootProject.tasks.findByName('clarityCheck') != null) {
        rootProject.tasks.named('clarityCheck') { minScore.set(1.01d) }
    }
}
GRADLE
if ./gradlew -I "$work/strict.gradle" clarityCheck --console=plain > "$work/strict.log" 2>&1; then
  echo "clarityCheck unexpectedly passed the negative-control threshold" >&2
  exit 1
fi
if ! grep -q "Clarity check failed" "$work/strict.log" || ! grep -q "threshold 1.01" "$work/strict.log"; then
  cat "$work/strict.log" >&2
  exit 1
fi
# The strict policy lived only in an init script; confirm the real policy still passes.
./gradlew -I "$work/policy.gradle" verifyClarityPolicy clarityCheck --console=plain
echo "verify.sh: output repair preserved the threshold and strict enforcement"
