#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Transcript verifier for the fresh session AFTER THE PUBLISHED PROMPT. Exit 0 = gate passed.
# Run with cwd set to the scaffolded fixture copy; $NARRATIVETRACE_TRANSCRIPT points at this
# trial's evidence, which lives outside this project.
#
# THE CASE
#
# The whole real path, end to end, with no help from the harness: the scaffold starts WITHOUT the
# seeded skill pages ("seedSkills": false), turn 1 is the published init prompt byte for byte
# (InitPromptDriftTest enforces that), turn 2 is a scripted approval of the install diff — the
# prompt says "run it for real only after I have seen the diff", and an agent that honours it stops
# there — and turn 3 is a NEW SESSION ("freshTurns": [3]) asking a doctor-shaped question. Graded
# like fresh-session-sees-the-skills: the last turn's init event lists every NarrativeTrace skill
# and the agent reaches for the doctor.
#
# WHY A SECOND CASE
#
# fresh-session-sees-the-skills runs the install itself (the narrativetrace-init delivery) and
# measures only what a later session finds. This one keeps the agent's own install in the loop, so
# it is the case that catches an agent stopping before the install (two of three Antigravity runs,
# the first TypeScript and Python runs, and Haiku's first unseeded Java run all did) — a red here
# with the other case green is the prompt path, not the installer.
set -e

python3 "$(dirname "$0")/../../grade_the_fresh_session.py"
