# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Grades a narrativetrace-explain trial: the transcript for ORDER, the scratch project for STATE.

Run by each case's graders/verify.sh from inside the scratch copy of
`fixtures/existing-service-checkout`, with NARRATIVETRACE_TRANSCRIPT pointing at the trial's
transcript. The transcript parsing is the verify grader's own (`grade_the_verify.py`), imported
rather than copied: both read the same stream-json with the same rules (assistant-record text only,
numbered reads allowed, evidence from what the agent SAW).

  --kind explain  an unfamiliar flow: the skill ran, a trace was produced, and the answer cites a
                  span id that trace actually has
  --kind control  a pure function: nothing installed, nothing run, nothing traced, and the answer
                  comes from the source

The fixture's checkout flow is `CheckoutFlowTest.customer_checks_out` driving
`DefaultCheckoutService.checkout`: it issues the invoice, authorizes the payment, calls every
`CheckoutListener` — `Checkout.compose` wires none — and settles through `CardSettlement`, which is
not itself wrapped, so the gateway's `confirm` shows up under the root. `LedgerService` and
`NotificationService` are handed in and never called. That is what an explanation of this flow has
to get right, and none of it is visible in the comments.

What GATES is mechanical: the skill was invoked, a trace exists, a structural trace reached the
agent before its answer, the answer cites an id that trace carries, and the production code was not
touched (the skill's own "never change the code in order to explain it"). What is REPORTED rather
than gated is judgment — whether the calls were named in order, whether the unobserved code was
named, whether values were opened — the same split `evals/README.md`'s "What gates" table draws,
and the same one grade_the_verify.py uses for "what was promoted was shown whole".

Every check prints one line, PASS or FAIL with its reason; the exit code is 1 when any gating check
failed.
"""

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "narrativetrace-verify"))
import grade_the_verify as gv  # noqa: E402 — the shared transcript reader

SKILL = "narrativetrace-explain"
FIXTURE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "fixtures", "existing-service-checkout"
)
TRACE_ROOT = "build/narrativetrace"
STRUCTURAL = os.path.join(TRACE_ROOT, "structural")
# Only the three directories a traced RUN writes. Scoped rather than walking build/narrativetrace
# whole, because the clarity report lands there too and a naming report is not a trace: counting it
# would make "nothing was traced" false for the control case the moment anything scanned.
TRACE_DIRECTORIES = (STRUCTURAL, os.path.join(TRACE_ROOT, "traces"), os.path.join(TRACE_ROOT, "diagrams"))
BUILD_FILES = ("build.gradle.kts", "settings.gradle.kts")
# The flow's calls, in the order the run makes them — reported, never gated: whether a cheap model
# narrates them in order is a judgment measure, and the ordering claim is the product's promise
# rather than this harness's oracle.
FLOW_CALLS = [
    "InvoiceService.issueInvoice",
    "PaymentGateway.authorize",
    "PaymentGateway.confirm",
]
GRADLE_RUN = re.compile(r"(^|[\s;&|(])\.?/?gradlew?\b")
NOT_OBSERVED = re.compile(r"not observ|unobserv|no span|not traced|untraced|not wrapped", re.I)
FROM_THE_SOURCE = re.compile(r"reading the source|from the source|pure function|no collaborator", re.I)


def skill_invoked(events):
    """The Skill tool, naming this skill — what "the skill triggered" means mechanically."""
    for event in events:
        if event.kind == "tool" and (event.payload or {}).get("name") == "Skill":
            if SKILL in json.dumps(event.payload.get("input")):
                return True
    return False


def page_read(events):
    """The agent opened the page itself instead of invoking it — a different observation."""
    return gv.first_index(
        events,
        lambda e: e.kind == "tool" and SKILL + "/SKILL.md" in json.dumps(e.payload.get("input")),
    )


def ran_gradle(events):
    """The first index at which the agent ran the build wrapper at all."""
    return gv.first_index(
        events,
        lambda e: e.kind == "tool"
        and (e.payload or {}).get("name") == "Bash"
        and bool(GRADLE_RUN.search(gv.command_of(e))),
    )


def trace_files():
    """Every trace the run left in the project, by path — the state half of "a trace was produced"."""
    found = {}
    for directory in TRACE_DIRECTORIES:
        for root, _dirs, files in os.walk(directory):
            for name in files:
                if name.endswith((".nt", ".md", ".mmd", ".json")):
                    path = os.path.join(root, name)
                    with open(path, encoding="utf-8", errors="replace") as handle:
                        found[path] = handle.read()
    return found


def ids_in(texts):
    ids = set()
    for text in texts:
        ids.update(gv.SPAN_ID.findall(text))
    return ids


def final_answer(events):
    """The agent's own text in the last turn — the explanation the user reads."""
    turns = sorted({e.turn for e in events if e.kind == "user"})
    last = turns[-1] if turns else 0
    return "\n".join(e.payload for e in events if e.kind == "text" and e.turn == last)


def changed_under(relative):
    """Files under `relative` that differ from the fixture's own, plus any added or removed."""
    source = os.path.join(FIXTURE, relative)
    differences = []
    for root, _dirs, files in os.walk(source):
        for name in files:
            original = os.path.join(root, name)
            copied = os.path.join(relative, os.path.relpath(original, source))
            if not os.path.exists(copied) or read_bytes(copied) != read_bytes(original):
                differences.append(copied)
    for root, _dirs, files in os.walk(relative):
        for name in files:
            copied = os.path.join(root, name)
            original = os.path.join(source, os.path.relpath(copied, relative))
            if not os.path.exists(original):
                differences.append(copied)
    return sorted(set(differences))


def changed_build_files():
    return sorted(
        name
        for name in BUILD_FILES
        if read_bytes(name) != read_bytes(os.path.join(FIXTURE, name))
    )


def read_bytes(path):
    if not os.path.isfile(path):
        return None
    with open(path, "rb") as handle:
        return handle.read()


def calls_named_in_order(answer):
    """Whether the answer names at least two of the flow's calls, in the order the run made them."""
    positions = [(answer.find(call), call) for call in FLOW_CALLS]
    seen = [(at, call) for at, call in positions if at >= 0]
    return len(seen) >= 2 and seen == sorted(seen)


def grade_explain(events, verdict):
    verdict.check(skill_invoked(events), "the Skill tool invoked " + SKILL, "it never did")
    verdict.note("the page was read rather than invoked: %s" % (page_read(events) is not None))

    traces = trace_files()
    structural = {p: t for p, t in traces.items() if p.endswith(".nt")}
    verdict.check(
        bool(structural),
        "the run left a structural trace in the project",
        "nothing under %s — found %s" % (STRUCTURAL, sorted(traces)),
    )

    first_nt = gv.first_index(events, gv.structural_seen)
    answer_at = last_text_index(events)
    verdict.check(
        first_nt is not None and answer_at is not None and first_nt < answer_at,
        "a structural trace reached the agent before its answer",
        "no structural trace line ever reached the agent"
        if first_nt is None
        else "read at %s, after the answer at %s" % (first_nt, answer_at),
    )

    answer = final_answer(events)
    cited = ids_in([answer])
    real = ids_in(structural.values()) or ids_in(traces.values())
    verdict.check(
        bool(cited & real),
        "the answer cites a span id the trace it produced actually has",
        "ids cited %s, ids in the trace %s" % (sorted(cited), sorted(real)),
    )

    verdict.check(
        not changed_under("src/main") and not changed_build_files(),
        "the production code and the build were left alone",
        "changed: %s %s" % (changed_under("src/main"), changed_build_files()),
    )
    report_the_judgment(events, verdict, answer)


def report_the_judgment(events, verdict, answer):
    verdict.note("the flow's calls are named in the order they ran: %s" % calls_named_in_order(answer))
    verdict.note("the answer names what was not observed: %s" % bool(NOT_OBSERVED.search(answer)))
    verdict.note(
        "values were opened: %s" % (gv.first_index(events, gv.narrative_seen) is not None)
    )
    verdict.note("the scenario's own sources changed: %s" % changed_under("src/test"))


def last_text_index(events):
    indexes = [i for i, e in enumerate(events) if e.kind == "text" and e.payload.strip()]
    return indexes[-1] if indexes else None


def grade_control(events, verdict):
    verdict.check(
        not changed_build_files(),
        "no dependency was added and no build file changed",
        "changed: %s" % changed_build_files(),
    )
    verdict.check(
        not changed_under("src"),
        "the project's sources were left alone",
        "changed: %s" % changed_under("src"),
    )
    at = ran_gradle(events)
    verdict.check(at is None, "nothing was run", "the build wrapper ran at event %s" % at)
    traces = trace_files()
    seen = gv.first_index(events, lambda e: gv.structural_seen(e) or gv.narrative_seen(e))
    verdict.check(
        not traces and seen is None,
        "nothing was traced",
        "traces on disk %s, a trace line reached the agent at %s" % (sorted(traces), seen),
    )

    answer = final_answer(events)
    verdict.check(
        bool(re.search(r"feeFor|LateFees", answer))
        and "150" in answer
        and bool(re.search(r"\b(zero|0)\b", answer)),
        "the answer comes from the source: the method, the rate and the not-late case",
        "answer:\n" + answer[-800:],
    )
    verdict.note("the skill was invoked: %s" % skill_invoked(events))
    verdict.note("the reply says why it is not tracing: %s" % bool(FROM_THE_SOURCE.search(answer)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=["explain", "control"], required=True)
    args = parser.parse_args()
    events = gv.read_events(os.environ["NARRATIVETRACE_TRANSCRIPT"])
    verdict = gv.Verdict()
    if args.kind == "explain":
        grade_explain(events, verdict)
    else:
        grade_control(events, verdict)
    sys.exit(1 if verdict.failed else 0)


if __name__ == "__main__":
    main()
