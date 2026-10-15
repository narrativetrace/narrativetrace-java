#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Transcript verifier for the FRESH-SESSION case. Exit 0 = gate passed.
# Run with cwd set to the scaffolded fixture copy; $NARRATIVETRACE_TRANSCRIPT points at this
# trial's evidence, which lives outside this project.
#
# THE CASE
#
# Before the agent starts, the harness runs this checkout's own installer in the project
# (case.json: "registry": "narrativetrace-init" — the built CLI jar's `init`, no staging, the
# carrier bundled with the jar). The scaffold is NOT seeded with the rendered pages (a delivery
# turns the seeding off), so every page present is one `init` wrote. The agent then gets ONE turn,
# a fresh session with a doctor-shaped question. What is graded is what that session FINDS: its
# init event must list every NarrativeTrace skill, and it must reach for the doctor — the skill by
# name, or the doctor command itself.
#
# WHY THE HARNESS INSTALLS
#
# Benchmark shakedown run 4 (2026-10-10, item 166): the install wrote `.agents/skills` and the
# `AGENTS.md` section, the doctor reported the skills installed, and the next Claude Code session
# listed no NarrativeTrace skill — it loads `.claude/skills` only, which the installer wrote just
# where a `.claude/` directory or a `CLAUDE.md` already existed. That question is about the
# INSTALLER and the next session, so the install is the harness's deterministic step here; the
# agent's own run of the published prompt is fresh-session-after-the-prompt's subject. In the
# ports the install must come from the checkout the same way, never from a published package that
# predates the fix.
set -e

python3 "$(dirname "$0")/../../grade_the_fresh_session.py"
