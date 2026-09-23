#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Empower Agile
set -eu

case_dir=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
python3 "$case_dir/../verify_report.py" \
  build/narrativetrace/clarity-results.json build/narrativetrace/clarity-report.md

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat > "$work/framework.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.register('verifyJUnit4Framework') {
        doLast {
            assert rootProject.extensions.getByName('narrativeTrace').testFramework.get() == 'junit4'
            assert rootProject.tasks.named('test').get().options instanceof org.gradle.api.tasks.testing.junit.JUnitOptions
        }
    }
}
GRADLE
./gradlew -I "$work/framework.gradle" verifyJUnit4Framework --console=plain
# Reuse the real-report, preserved-policy, and strict-failure checks.
sh "$case_dir/../deviation-output-disabled/graders/verify.sh"
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

p = Path('build/test-results/test/TEST-com.example.orders.OrderServiceTest.xml')
suite = ET.parse(p).getroot()
assert int(suite.attrib['tests']) > 0, 'the original JUnit 4 test did not run'
assert int(suite.attrib['failures']) == 0
assert int(suite.attrib['errors']) == 0
PY
echo "verify.sh: JUnit 4 test ran, reports reproduced, framework and threshold preserved"
