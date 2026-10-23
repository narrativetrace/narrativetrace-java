#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
set -eu

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat > "$work/framework.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.register('verifyTestNgFramework') {
        doLast {
            assert rootProject.extensions.getByName('narrativeTrace').testFramework.get() == 'testng'
            assert rootProject.tasks.named('test').get().options instanceof org.gradle.api.tasks.testing.testng.TestNGOptions
            def jupiter = rootProject.configurations.getByName('testRuntimeClasspath').resolvedConfiguration
                .resolvedArtifacts.findAll { it.moduleVersion.id.group == 'org.junit.jupiter' }
            assert jupiter.isEmpty() : 'a Jupiter artifact was added to the TestNG suite'
        }
    }
}
GRADLE
./gradlew -I "$work/framework.gradle" verifyTestNgFramework clean test --console=plain

python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

results = Path('build/test-results/test')
suites = [ET.parse(p).getroot() for p in results.glob('TEST-*.xml')]
assert suites, 'the TestNG suite did not run'
assert sum(int(s.attrib['tests']) for s in suites) > 0, 'no TestNG test ran'
for s in suites:
    assert int(s.attrib['failures']) == 0
    assert int(s.attrib['errors']) == 0

traces = Path('build/narrativetrace/traces')
files = [p for p in traces.rglob('*') if p.is_file() and p.suffix in ('.md', '.nt')]
assert files, 'the TestNG run wrote no trace file under build/narrativetrace/traces'
assert any('customerPlacesAnOrder' in p.name for p in files), 'no trace file for the fixture test'
PY
echo "verify.sh: TestNG kept, no Jupiter, trace file written by the fixture's test"
