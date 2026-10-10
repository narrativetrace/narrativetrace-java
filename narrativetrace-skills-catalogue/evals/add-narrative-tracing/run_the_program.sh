#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Grades the published init prompt's step 4 — "Run the program" — as world state: the project the
# agent left behind RUNS, and its own standard output carries a rendered trace naming $1, the
# service boundary this case's fixture defines. Shared by both init-prompt cases; run with cwd set
# to the scaffolded fixture copy, from a case's graders/verify.sh.
#
# Why the program's stdout and not a file: the prompt sends the reader to llms.txt's "Install and
# first trace" block, which renders the trace with IndentedTextRenderer and prints it from `main`,
# then runs it with `./gradlew run`. A rendered `.md` under build/narrativetrace is the OTHER path
# (the Gradle plugin plus the JUnit extension writing test-time output), which this prompt never
# asks for — grading it failed both cases on agents that had done exactly what was asked.
set -e

service="$1"
if [ -z "$service" ]; then
  echo "usage: run_the_program.sh <TracedServiceName>" >&2
  exit 1
fi
# The name goes into a grep pattern, so find_the_trace.sh refuses anything but a Java identifier —
# here, before a build is spent, and again at its point of use.
sh "$(dirname "$0")/find_the_trace.sh" "$service"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
log="$work/program-output.txt"

# No `application` plugin means no `run` task: run the one class carrying a main method through the
# project's own runtime classpath instead. Still "the program runs" — and the fixture's build file
# is the agent's work, so a grader never edits it to add a plugin the agent chose not to apply.
run_from_compiled_classes() {
  main_source=$(grep -rl "static void main" src/main/java 2>/dev/null | head -1)
  if [ -z "$main_source" ]; then
    echo "no \`run\` task and no class with a main method — the prompt's \"Run the program\" step" >&2
    echo "has nothing to run in this project" >&2
    return 1
  fi
  main_class=$(printf '%s' "${main_source#src/main/java/}" | sed 's/\.java$//; s|/|.|g')
  cat >"$work/classpath.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.register('ntPrintRuntimeClasspath') {
        doLast { println rootProject.sourceSets.main.runtimeClasspath.asPath }
    }
}
GRADLE
  timeout 900 ./gradlew classes --console=plain -q >>"$log" 2>&1 || return 1
  classpath=$(timeout 900 ./gradlew -I "$work/classpath.gradle" ntPrintRuntimeClasspath \
    --console=plain -q 2>>"$log" | tail -1)
  [ -n "$classpath" ] || return 1
  timeout 300 java -cp "$classpath" "$main_class" >>"$log" 2>&1
}

# Bounded, always: a cold run resolves the plugin and the libraries over the network, and a program
# that hangs has to fail this case rather than the whole harness.
if timeout 900 ./gradlew run --console=plain -q >"$log" 2>&1; then
  :
elif grep -q "Task 'run' not found" "$log"; then
  run_from_compiled_classes || {
    echo "the program did not run" >&2
    cat "$log" >&2
    exit 1
  }
else
  echo "\`./gradlew run\` failed — the prompt says to run the program and read its output" >&2
  cat "$log" >&2
  exit 1
fi

sh "$(dirname "$0")/find_the_trace.sh" "$service" "$log" run_the_program.sh
