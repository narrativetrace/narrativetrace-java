#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# The whole of what the PUBLISHED init prompt promises, graded as world state
# (skill-harness-design.md principle 1: assert the world, never output text equality). Shared by
# both init-prompt cases; run with cwd set to the scaffolded fixture copy, from a case's
# graders/verify.sh, with $1 naming the service boundary that case's fixture defines.
#
# $NARRATIVETRACE_CLI_JAR is the built, zero-dependency narrativetrace-cli jar the runner points at
# (`./gradlew :narrativetrace-cli:jar` first). $NARRATIVETRACE_AGENT_TRANSCRIPT is OPTIONAL: set it
# to a file holding the agent's own output and step 3's gate can be graded on what the agent showed,
# not only on what it left behind.
set -e

service="$1"
if [ -z "$service" ]; then
  echo "usage: grade_the_prompt.sh <TracedServiceName>" >&2
  exit 1
fi
here=$(dirname "$0")

# Step 2 and 4, at their cheapest observable: the build declares NarrativeTrace at all.
grep -q "ai.narrativetrace" build.gradle.kts || {
  echo "expected build.gradle.kts to declare a NarrativeTrace dependency or the plugin" >&2
  exit 1
}

# Step 3, and its HUMAN GATE. The prompt says to run `narrativetraceInit --diff`, show the diff, and
# apply it only once a person has seen it — so in a non-interactive trial the COMPLIANT outcome is
# "showed it and stopped". Both outcomes pass, and neither is required:
#   applied   — a skill page of ours is on disk, carrying the installer's own provenance line
#   previewed — the plugin is declared, so the task existed to be run (and, when a transcript was
#               captured, the diff itself is in it)
# Only "neither" fails: an agent that never reached step 3 at all.
skills_applied=no
if grep -rqs "installed by narrativetrace init from" .agents/skills 2>/dev/null; then
  skills_applied=yes
  echo "step 3: the installer was applied — .agents/skills carries our provenance line"
elif grep -qs "ai.narrativetrace\"\?)\? version\|id(\"ai.narrativetrace\")\|id 'ai.narrativetrace'" build.gradle.kts; then
  echo "step 3: the plugin is declared, so narrativetraceInit --diff was there to run"
elif [ -n "$NARRATIVETRACE_AGENT_TRANSCRIPT" ] &&
  grep -qs "narrativetraceInit" "$NARRATIVETRACE_AGENT_TRANSCRIPT"; then
  echo "step 3: the transcript shows the installer preview"
else
  echo "step 3 left no trace: no plugin in the build, no installed skills, no preview in a" >&2
  echo "transcript — the prompt's third step never happened" >&2
  exit 1
fi
export NT_SKILLS_APPLIED="$skills_applied"

# Step 6's first half, graded where the prompt puts it: the program's own standard output.
sh "$here/run_the_program.sh" "$service"

# Step 5: the redaction test exists, and passes. Existing is not enough — a test nobody can run
# proves nothing, and the doctor's trap.redaction-proof only ever reads the source.
sh "$here/run_the_tests.sh"

# One of the prompt's own rules, as world state rather than as a promise.
if find . -name "*.received.nt" | grep -q .; then
  echo "the prompt forbids leaving .received.nt files behind, and one is on disk" >&2
  exit 1
fi

# Step 6's second half: the doctor report, and every finding in it.
report=$(java -jar "$NARRATIVETRACE_CLI_JAR" doctor --json || true)

echo "$report" | python3 -c '
import json, os, sys
report = json.load(sys.stdin)
findings = {f["id"]: f.get("status") for f in report.get("findings", [])}
if len(findings) != 12:
    print("expected the doctor to report twelve findings, got", len(findings), file=sys.stderr)
    sys.exit(1)
# The prompt now reaches every check: it declares JUnit (the range and launcher rules apply again),
# registers the extension, keeps -parameters, and writes the redaction test. The one finding it
# cannot answer on its own is whether the skills are INSTALLED — that is step 3, whose human gate
# the prompt deliberately leaves open, so it is graded only when the agent did apply it.
exempt = set() if os.environ.get("NT_SKILLS_APPLIED") == "yes" else {"config.skills-installed"}
bad = sorted(i for i, s in findings.items() if s != "pass" and i not in exempt)
if bad:
    print("expected every doctor finding to hold; these did not:", bad, file=sys.stderr)
    sys.exit(1)
print("verify.sh: the published init prompt produced a running trace, a passing redaction test")
print("           and a doctor report with nothing left to fix")
'
