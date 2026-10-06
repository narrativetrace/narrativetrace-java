#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier for the PUBLISHED init prompt, existing-project branch. Exit 0 = gate passed.
# Run with cwd set to the scaffolded fixture copy.
#
# This case's prompt.md IS the published prompt, byte for byte, with no case scaffolding around it
# (InitPromptDriftTest enforces that), so what this grades is what a reader actually pasted. Every
# rule the prompt states is graded in grade_the_prompt.sh, shared with the other branch; what stays
# here is this branch's own subject — step 2's other half, "work inside the existing project and
# trace one real service boundary": the trace has to name the fixture's own interface, and the
# implementation that was already here has to still be here.
set -e

sh "$(dirname "$0")/../../grade_the_prompt.sh" InvoiceService

test -f src/main/java/com/example/billing/DefaultInvoiceService.java || {
  echo "the fixture's existing implementation must still be there" >&2
  exit 1
}
