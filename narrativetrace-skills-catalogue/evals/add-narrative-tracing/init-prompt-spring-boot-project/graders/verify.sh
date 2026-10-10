#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier for the PUBLISHED init prompt on the runtime's main web framework (Phase 6,
# D4): an existing Spring Boot service. Exit 0 = gate passed. Run with cwd set to the scaffolded
# fixture copy.
#
# prompt.md IS the published prompt, byte for byte (InitPromptDriftTest). The prompt never names
# Spring; what this case measures is whether the framework-aware path — the add-narrative-tracing
# skill's "run the doctor, apply every config.<framework>-* fix" step — gets an agent from a cold
# Spring Boot project to a trace of a REAL request. So, beyond what the shared prompt grader asserts
# for every init-prompt case (redaction test green, no .received.nt on disk, every doctor finding
# holding):
#
#   1. the doctor's config.spring-enabled is green — named first, because it is this case's subject
#      and "every finding holds" would otherwise bury it in a list;
#   2. the program is run the way a server is: run_the_server.sh requires the boot jar to still start
#      the project's own @SpringBootApplication (a demo given to the `application` plugin as its
#      mainClass becomes the jar's Start-Class and fails here by name), starts it, requests
#      GET /accounts/ACC-4711, and requires a trace naming AccountService in the output printed
#      AFTER that request — a startup demo trace does not count;
#   3. the fixture's own service and controller are still there: the trace has to be of THIS
#      application's boundary, not of a class the agent wrote beside it.
set -e

here=$(dirname "$0")

report=$(java -jar "$NARRATIVETRACE_CLI_JAR" doctor --json || true)
printf '%s\n' "$report" | python3 -c '
import json, sys
findings = {f["id"]: f for f in json.load(sys.stdin).get("findings", [])}
spring = findings.get("config.spring-enabled")
if spring is None or spring.get("status") != "pass":
    print("expected the doctor to report config.spring-enabled green; it reported:", file=sys.stderr)
    print(json.dumps(spring, indent=2) if spring else "  (no such finding)", file=sys.stderr)
    sys.exit(1)
print("verify.sh: config.spring-enabled is green —", spring.get("message", ""))
'

for kept in AccountService DefaultAccountService AccountController; do
  test -f "src/main/java/com/example/accounts/$kept.java" || {
    echo "the fixture's own $kept must still be there — the trace has to be of this application" >&2
    exit 1
  }
done

sh "$here/../../grade_the_prompt.sh" AccountService run_the_server.sh /accounts/ACC-4711
