#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
set -eu

json="build/narrativetrace/clarity-scan-results.json"
markdown="build/narrativetrace/clarity-scan-report.md"
explanation="build/narrativetrace/clarity-explanation.md"
verifier="$(dirname "$0")/../../verify_report.py"
# The explanation file is the agent's own write, not a Gradle task output — checked against
# what the agent actually read, before the clean rebuild below (which, like any `clean`, removes
# it along with the rest of build/ and does not regenerate it).
python3 "$verifier" "$json" "$markdown" "$explanation"

./gradlew clean clarityScan --console=plain
python3 "$verifier" "$json" "$markdown"

# The happy-path prompt asks only for a first static naming report — no enforcement was
# requested. The fixture's own build.gradle.kts (minScore=0.0, maxHighIssues=Int.MAX_VALUE) must
# stay exactly as it was; "add enforcement only when requested" is not just a rule for what the
# agent adds, it also forbids raising or lowering a threshold nobody asked to change.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat > "$work/policy.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.register('verifyClarityPolicy') {
        doLast {
            def gate = rootProject.tasks.named('clarityCheck').get()
            assert gate.minScore.get() == 0.0d : 'no enforcement was requested; minScore was changed'
            assert gate.maxHighIssues.get() == Integer.MAX_VALUE : 'no enforcement was requested; maxHighIssues was changed'
            assert !gate.warnOnly.get() : 'no enforcement was requested; warnOnly was changed'
        }
    }
}
GRADLE
./gradlew -I "$work/policy.gradle" verifyClarityPolicy --console=plain

echo "verify.sh: agent produced and clean reproduction verified fresh static scan artifacts, with no enforcement added"
