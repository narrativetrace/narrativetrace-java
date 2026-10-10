#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# narrativetrace-debug / happy-path — the debug loop on the case it was designed for, asked in the
# user's own words with the skill's trigger phrase ("debug this with the trace") and the numbers
# already in cents. Same fixture, defect and grader as debug-value-divergence, whose header
# documents every gate; that case is the support-ticket form, this one the direct ask.
set -e
python3 "$(dirname "$0")/../../grade_the_debug.py"
