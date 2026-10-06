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
# The name goes into a grep pattern below, so it has to BE a Java identifier and not a regex: a `.`
# would become a wildcard and match `OrderaService.placeOrder(` for `Order.Service`, which is the
# exact near miss the guard's own boundary check cannot see. InitPromptFixtureShapeTest already
# constrains the name by reading it as an identifier out of the case's grader, so this refuses at the
# point of USE rather than trusting a caller two files away (adversarial pass, milestone 5).
case "$service" in
  *[!A-Za-z0-9_]* | [0-9]*)
    echo "the graded service name must be a Java identifier, not \"$service\" — it goes into a" >&2
    echo "grep pattern, where a regex metacharacter silently widens the match" >&2
    exit 1
    ;;
esac

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

# A rendered trace line is `<Service>.<method>(…)` — from IndentedTextRenderer, MarkdownRenderer or
# StructuralTraceRenderer alike, which is what "whatever renderer produced it" can honestly mean
# here: the sequence-diagram renderers (`OrderService->>OrderService: placeOrder(…)`) and
# ProseRenderer ("The order service place order for …") carry no service-qualified call token at
# all, and a guard loose enough to accept those would accept any line merely naming the service.
#
# Markdown wraps the call in emphasis and its values in code spans — `- **OrderService.placeOrder**(
# customerId: `"C-1234"`, …)` — so the markup sits between the method name and its paren and the
# match below fails on a perfectly correct trace (round 4, 2026-09-25). Strip the markup first.
# Only `*` and a backtick: neither can occur inside a Java identifier, so removing them can only
# rejoin fragments the guard already treated as separated. `_` is deliberately NOT stripped even
# though Markdown can emphasise with it — `_` IS an identifier character, so stripping it would let
# `Order_Service.placeOrder(` pass for `OrderService`, and no renderer here emits `_` emphasis.
# Both halves of the guard therefore still hold exactly as rehearsed when it was added: the leading
# `(^|[^A-Za-z0-9_])` rejects `MyOrderService.placeOrder(`, the literal name rejects
# `IOrderService.placeOrder(`. The failure message prints the ORIGINAL output, not the stripped one.
#
# TWO accepted shapes, because the library has two sanctioned output formats and the prompt names
# neither: "run the program, paste the trace". A trial on 2026-09-25 printed a perfectly good trace
# through the canonical JSON exporter — every call, the redacted parameter marked `"redacted": true`
# — and this guard rejected it, which measured the grader rather than the agent.
#   text/markdown: a service-qualified call token, `<Service>.<method>(`
#   canonical JSON: the service as a WHOLE quoted string under "className", beside a "methodName"
# The JSON form's near-miss protection is stronger than the text form's, not weaker: an exact quoted
# string cannot match `IOrderService` or `MyOrderService` at all. Requiring "methodName" beside it is
# what keeps the shape a TRACE rather than any JSON that happens to name a class.
LC_ALL=C tr -d '*`' <"$log" >"$work/unmarked.txt"
if LC_ALL=C grep -Eq "(^|[^A-Za-z0-9_])${service}\.[A-Za-z_][A-Za-z0-9_]*\(" "$work/unmarked.txt"; then
  echo "run_the_program.sh: the program ran and printed a rendered trace naming $service"
elif LC_ALL=C grep -Eq "\"className\"[[:space:]]*:[[:space:]]*\"${service}\"" "$work/unmarked.txt" &&
  LC_ALL=C grep -q '"methodName"' "$work/unmarked.txt"; then
  echo "run_the_program.sh: the program ran and printed a canonical-JSON trace naming $service"
else
  echo "expected the program's own output to carry a rendered trace naming $service — either a" >&2
  echo "$service.<method>( call token or a canonical-JSON event naming it in \"className\"" >&2
  echo "--- what it printed instead ---" >&2
  cat "$log" >&2
  exit 1
fi
