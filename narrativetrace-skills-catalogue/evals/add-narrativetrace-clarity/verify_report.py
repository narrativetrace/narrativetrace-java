# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Check real report data, both before grading and after a clean reproduction."""

import json
import pathlib
import sys

report = pathlib.Path(sys.argv[1])
markdown = pathlib.Path(sys.argv[2])
data = json.loads(report.read_text())
scenarios = data.get("scenarios")
assert isinstance(scenarios, list) and scenarios, "report has no scenarios"
all_elements = []
for scenario in scenarios:
    score = scenario.get("overallScore")
    assert isinstance(score, (int, float)) and 0 <= score <= 1, "invalid score"
    assert isinstance(scenario.get("issues"), list), "missing issues array"
    elements = scenario.get("elements")
    assert isinstance(elements, list) and elements, "scenario has no elements"
    assert all(e.get("element") and e.get("note") for e in elements), "missing notes"
    all_elements.extend(elements)
assert "Elements" in markdown.read_text(), "Markdown has no element explanations"

# Optional third argument: the agent's own per-element explanation writeup
# (build/narrativetrace/clarity-explanation.md by default — see
# AddNarrativeTraceClaritySkill's "Explain scores, notes, and coverage" step). Makes
# judgment-rubric.md item 2 (report fidelity) mechanically gradeable: every element's exact
# name and exact note, verbatim, must appear somewhere in the agent's own writeup — not just a
# paraphrase the cheapest model might get wrong (e.g. calling a domain-specific noun generic).
if len(sys.argv) > 3:
    explanation = pathlib.Path(sys.argv[3])
    assert explanation.is_file(), f"{explanation} is missing"
    explanation_text = explanation.read_text()
    for element in all_elements:
        name = element["element"]
        note = element["note"]
        # The report prints methods as Class.method; a writeup that names the member alone
        # (`placeOrder`) still identifies it. The NOTE is what must be verbatim — that is where
        # the cheapest model misjudged the report (a domain noun called generic).
        simple_name = name.rsplit(".", 1)[-1]
        assert simple_name in explanation_text, f"{explanation} is missing element {name!r}"
        assert note in explanation_text, (
            f"{explanation} is missing the exact note for {name!r}: {note!r}"
        )

print("Verified nonempty scenarios, scores, issues, and per-element notes")
