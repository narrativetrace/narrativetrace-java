# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Grades the fresh-session cases: the session AFTER the install must see the skills it left.

Shared by two cases. ``fresh-session-sees-the-skills`` lets the harness run this checkout's
installer first and gives the agent one fresh turn; ``fresh-session-after-the-prompt`` keeps the
agent's own install in the loop (the published prompt, a scripted approval of the diff, then a
fresh turn). Either way two things gate, both read from the transcript's LAST turn:

  1. the turn's ``system/init`` event lists every skill the catalogue ships (the objective half:
     the skills are LOADED, whatever the agent then does with them);
  2. the agent reached for the doctor — the ``narrativetrace-doctor`` skill by name through the
     Skill tool, or the doctor command itself — rather than answering from memory.

Why both: item 166 (2026-10-10) was an install that went green and a next session that listed no
skill. Gate 1 is that finding, stated as a measurement. Gate 2 is why the skills matter at all: a
session that lists them and never uses them on a question written to trigger one is the skill
failing to fire, which the trigger samples measure on a phrasing set and this case measures once,
on a real project the agent just installed into.

Run from the scaffolded project copy, with $NARRATIVETRACE_TRANSCRIPT pointing at this trial's
evidence, which lives outside this project. The transcript reader is the feedback cases' own —
one record format, one reader. Neither case starts with the harness's usual copy of the rendered
pages in the scaffold (a delivery turns the seeding off; the prompt case declares
``"seedSkills": false``): the seeded pages would list every skill whether or not the install wrote
them, which is exactly the question — and did, on this case's first two runs.
"""

import os
import re
import sys

sys.path.insert(
    0,
    os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "narrativetrace-feedback"
    ),
)
import transcript  # noqa: E402

EXPECTED_SKILLS = (
    "add-narrative-tracing",
    "add-narrativetrace-clarity",
    "narrativetrace-doctor",
    "narrativetrace-feedback",
    "narrativetrace-verify",
    "narrativetrace-debug",
)

DOCTOR_SKILL = "narrativetrace-doctor"
DOCTOR_COMMAND = re.compile(r"narrativetraceDoctor\b|narrativetrace\s+doctor\b")


def fail(message):
    print("grade_the_fresh_session: " + message, file=sys.stderr)
    sys.exit(1)


def main():
    path = os.environ.get("NARRATIVETRACE_TRANSCRIPT")
    if not path or not os.path.isfile(path):
        fail("no transcript at $NARRATIVETRACE_TRANSCRIPT — the runner records every trial")
    turns = transcript.read(path)
    if not turns:
        fail("the transcript records no turn at all")
    fresh = turns[-1]

    missing = [s for s in EXPECTED_SKILLS if s not in fresh.available_skills]
    if missing:
        fail(
            "the fresh session's init event does not list these skills: %s (it lists: %s)"
            % (", ".join(missing), ", ".join(fresh.available_skills) or "nothing")
        )

    used_the_skill = any('"skill": "%s"' % DOCTOR_SKILL in c for c in fresh.commands)
    ran_the_doctor = any(DOCTOR_COMMAND.search(c) for c in fresh.commands)
    if not (used_the_skill or ran_the_doctor):
        fail(
            "the fresh session listed the skills but reached for neither the %s skill nor the"
            " doctor command on a question written to trigger it" % DOCTOR_SKILL
        )

    print("grade_the_fresh_session: the fresh session lists every skill and reached for the"
          " doctor (%s)" % ("skill" if used_the_skill else "command"))


if __name__ == "__main__":
    main()
