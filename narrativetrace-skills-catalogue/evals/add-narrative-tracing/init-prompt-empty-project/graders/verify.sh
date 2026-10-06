#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier for the PUBLISHED init prompt, empty-project branch. Exit 0 = gate passed.
# Run with cwd set to the scaffolded fixture copy.
#
# This case's prompt.md IS the published prompt, byte for byte, with no case scaffolding around it
# (InitPromptDriftTest enforces that), so what this grades is what a reader actually pasted. Every
# rule the prompt states is graded in grade_the_prompt.sh, shared with the other branch; what stays
# here is this branch's own subject — the service the fixture ships.
set -e

sh "$(dirname "$0")/../../grade_the_prompt.sh" OrderService
