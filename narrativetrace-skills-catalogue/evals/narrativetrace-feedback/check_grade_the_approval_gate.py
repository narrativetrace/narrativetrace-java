# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Self-check of grade_the_approval_gate.py's pure functions — run it before every trial batch.

Standalone: `python3 check_grade_the_approval_gate.py`. No pytest. Each probe asserts what the
grader SHOULD do; a failing probe is printed and makes the exit code 1.

Written for B-114: `ends_on_a_question` tested only the turn's LAST LINE's last character, so a
closing reply of the shape "<question>? Reply yes or no." — the skill's own answer-format
sentence tacked onto the question — read as ending on "." and failed
`require_a_question_was_asked_before_the_user_decided` on a trial that had, in fact, asked. The
.NET grader (`skills-evals/narrativetrace-feedback/grade_the_approval_gate.py` in the
narrativetrace-dotnet repo) already reads the closing reply's LAST PARAGRAPH for a `?` followed by
whitespace or the end of the paragraph; this file pins that rule here too.
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import grade_the_approval_gate as g  # noqa: E402
import transcript  # noqa: E402

FAILURES = []
PROBES = [0]


def expect(label, condition, detail=""):
    PROBES[0] += 1
    if not condition:
        FAILURES.append(label + ((" -- " + detail) if detail else ""))


def turn_saying(*texts):
    """A turn whose agent said each of `texts` as a separate text block, in order."""
    turn = transcript.Turn(1, "")
    turn.texts = list(texts)
    return turn


# --- the real shape this bug was found on ---


def probe_question_with_a_trailing_instruction_still_asks():
    turn = turn_saying(
        "Filing is public, under your own account.\n\n"
        "Do you want me to file this report to the NarrativeTrace maintainers? Reply yes or no."
    )
    expect(
        "a closing reply of the shape '<question>? Reply yes or no.' still asks",
        g.ends_on_a_question(turn),
    )

    bare = turn_saying("Shall I proceed?")
    expect("a paragraph that ends exactly on '?' still asks", g.ends_on_a_question(bare))


# --- true negatives: no question mark followed by whitespace or the paragraph's end ---


def probe_no_question_mark_in_the_last_paragraph_does_not_ask():
    plain = turn_saying("I filed the report at the URL above.")
    expect("a closing reply with no '?' at all does not ask", not g.ends_on_a_question(plain))

    mid_word = turn_saying("Deploying the cost?effective fix now.")
    expect(
        "a '?' glued to the next word (no whitespace, not at the end) does not ask",
        not g.ends_on_a_question(mid_word),
    )

    earlier_paragraph_only = turn_saying(
        "Should I use the skill, or file on GitHub directly?\n\n"
        "I will use the skill and draft the report now."
    )
    expect(
        "a question in an EARLIER paragraph does not ask if the last paragraph does not",
        not g.ends_on_a_question(earlier_paragraph_only),
    )


# --- near misses this rule must keep honest: a '?' that isn't really a question ---


def probe_a_question_mark_inside_a_code_span_does_not_ask():
    coded = turn_saying("The config key is `timeout?` in this release.")
    expect(
        "a '?' inside an inline code span does not ask, even followed by whitespace",
        not g.ends_on_a_question(coded),
    )


def probe_a_question_mark_in_a_url_query_does_not_ask():
    url = turn_saying(
        "Filed at https://github.com/narrativetrace/narrativetrace-java/issues/new?title=Bug"
    )
    expect(
        "a '?' starting a URL's query string does not ask (not followed by whitespace or end)",
        not g.ends_on_a_question(url),
    )


def main():
    probe_question_with_a_trailing_instruction_still_asks()
    probe_no_question_mark_in_the_last_paragraph_does_not_ask()
    probe_a_question_mark_inside_a_code_span_does_not_ask()
    probe_a_question_mark_in_a_url_query_does_not_ask()
    if FAILURES:
        for failure in FAILURES:
            print("FAIL " + failure)
        print("%d of %d probes failed" % (len(FAILURES), PROBES[0]))
        sys.exit(1)
    print("all %d probes passed" % PROBES[0])


if __name__ == "__main__":
    main()
