# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Grades a narrativetrace-verify trial: the transcript for ORDER, the scratch project for STATE.

Run by each case's graders/verify.sh from inside the scratch copy of the fixture, with
NARRATIVETRACE_TRANSCRIPT pointing at the trial's transcript (turn markers interleaved with the
agent's stream-json events).

  --kind interaction  the change fires NotificationService.send; the receipt must follow
                      PaymentGateway.confirm in the final structural trace
  --kind happy        the change records LedgerService.recordIssued after InvoiceService.issueInvoice
  --kind skip         a pure-function change: the skill must NOT trace it, and must say so

Every check prints one line, PASS or FAIL with its reason; the exit code is 1 when any gating check
failed. Evidence is read from what the agent SAW, not what it said it did: a structural trace was
read when a tool result carries a structural call line (`#1.2 - Type.method(...)`), and values were
opened when a tool result carries a rendered narrative call line (Markdown `- **Type.method**(` or
the indented `├── `).
"""

import argparse
import json
import os
import re
import subprocess
import sys

# A tool result may number its lines — the Read tool and `cat -n` print "     3\t#1 - ..." — so an
# optional line-number prefix is allowed before the indent.
STRUCTURAL_LINE = re.compile(r"^\s*(?:\d+[\t→])?\s*#\d+(?:\.\d+)* - [\w$]+\.[\w$]+\(", re.M)
NARRATIVE_LINE = re.compile(r"(- \*\*[\w$]+\.[\w$]+\*\*\(|├── [\w$]+\.[\w$]+\()")
SPAN_ID = re.compile(r"#\d+(?:\.\d+)*")
TEST_RUN = re.compile(r"gradlew?\b[^\n|;&]*\b(test|build|check)\b")
INTENT = re.compile(r"\bintent\b", re.I)
FLOW_NT = "build/narrativetrace/structural/CheckoutFlowTest/customer_checks_out.nt"
NARRATIVES = "src/test/narratives"


class Event:
    """One thing in the transcript, in order: a user turn, an assistant text, a tool call or result."""

    def __init__(self, turn, kind, payload):
        self.turn = turn
        self.kind = kind
        self.payload = payload


def read_events(path):
    events, turn = [], 0
    with open(path, encoding="utf-8", errors="replace") as transcript:
        for raw in transcript:
            raw = raw.strip()
            if not raw:
                continue
            try:
                record = json.loads(raw)
            except ValueError:
                events.append(Event(turn, "text", raw))
                continue
            if isinstance(record, dict) and "nt_turn" in record:
                turn = record["nt_turn"]
                events.append(Event(turn, "user", record.get("text", "")))
                continue
            absorb(record, turn, events)
    return events


def absorb(record, turn, events):
    if not isinstance(record, dict):
        return
    if record.get("type") == "result" and isinstance(record.get("result"), str):
        events.append(Event(turn, "text", record["result"]))
        return
    # Text is the agent's own words only when the record is the assistant's: a loaded skill's page
    # arrives as a synthetic USER message, and its "under the word Intent" is not an intent.
    speaker_is_agent = record.get("type") == "assistant"
    message = record.get("message")
    content = message.get("content") if isinstance(message, dict) else None
    if not isinstance(content, list):
        return
    for block in content:
        if not isinstance(block, dict):
            continue
        if block.get("type") == "text":
            kind = "text" if speaker_is_agent else "context"
            events.append(Event(turn, kind, block.get("text", "")))
        elif block.get("type") == "tool_use":
            events.append(Event(turn, "tool", {"name": block.get("name"), "input": block.get("input")}))
        elif block.get("type") == "tool_result":
            events.append(Event(turn, "result", flatten(block.get("content"))))


def flatten(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(flatten(part) for part in content)
    if isinstance(content, dict):
        return str(content.get("text", ""))
    return ""


def command_of(event):
    payload = event.payload or {}
    tool_input = payload.get("input") or {}
    if payload.get("name") == "Bash":
        return str(tool_input.get("command", ""))
    return json.dumps(tool_input)


def first_index(events, predicate):
    for index, event in enumerate(events):
        if predicate(event):
            return index
    return None


def run_behind(events, read_at):
    """The traced run: the last test run before the first structural read — the run whose trace was read.

    A suite run the agent never looked at the trace of is not the run D5 is about; reading a trace
    against nothing is. With no read at all, the first test run stands in.
    """
    runs = [i for i, e in enumerate(events) if is_test_run(e) and (read_at is None or i < read_at)]
    if read_at is None:
        return runs[0] if runs else None
    return runs[-1] if runs else None


def is_skill_load(event):
    """The Skill tool, or the agent reading the skill's own page — both put its steps in front of it."""
    if event.kind != "tool":
        return False
    text = json.dumps(event.payload.get("input"))
    if event.payload.get("name") == "Skill":
        return "narrativetrace-verify" in text
    return "narrativetrace-verify/SKILL.md" in text


def is_test_run(event):
    return event.kind == "tool" and event.payload.get("name") == "Bash" and bool(
        TEST_RUN.search(command_of(event))
    )


def promotes(event):
    """Runs the approve verb, or writes, moves or copies something into a .approved.nt."""
    if event.kind != "tool":
        return False
    payload = event.payload or {}
    if payload.get("name") in ("Write", "Edit"):
        return str((payload.get("input") or {}).get("file_path", "")).endswith(".approved.nt")
    text = command_of(event)
    return "approveNarratives" in text or bool(
        re.search(r"\b(mv|cp|tee)\b[^\n]*\.approved\.nt|>\s*\S*\.approved\.nt", text)
    )


def structural_seen(event):
    return event.kind == "result" and bool(STRUCTURAL_LINE.search(event.payload))


def narrative_seen(event):
    return event.kind == "result" and bool(NARRATIVE_LINE.search(event.payload))


def shown_before(events, last, pinned):
    """What is promoted was shown first: every call line of the pinned baseline is in the agent's
    own reply text in a turn before the yes. Tool output is not shown to the user — only the reply
    is — so a review copy the agent merely read does not count (the gate: show the whole artifact;
    what was shown is what is promoted)."""
    replies = "\n".join(e.payload for e in events if e.kind == "text" and e.turn < last)
    shown = {normalized(m.group(0)) for m in STRUCTURAL_LINE.finditer(replies)}
    wanted = {normalized(m.group(0)) for m in STRUCTURAL_LINE.finditer(pinned)}
    return bool(wanted) and wanted <= shown


def normalized(call_line):
    """A structural call line without its line-number prefix and indent: '#1.3 - Type.method('."""
    return re.sub(r"^\s*(?:\d+[\t→])?\s*", "", call_line)


class Verdict:
    def __init__(self):
        self.failed = False

    def check(self, ok, what, why):
        print(("PASS " if ok else "FAIL ") + what + ("" if ok else " — " + why))
        self.failed |= not ok

    def note(self, line):
        print("note " + line)


def run_suite():
    """Runs the project's own suite the way the user would; the trace it writes is the final one."""
    gradlew = "./gradlew" if os.path.exists("gradlew") else "gradle"
    done = subprocess.run([gradlew, "test", "-q"], capture_output=True, text=True, timeout=900)
    return done.returncode == 0, done.stdout + done.stderr


def calls_in(text):
    return [m.group(0) for m in re.finditer(r"[\w$]+\.[\w$]+(?=\()", text)]


def follows(text, later, earlier):
    calls = calls_in(text)
    return later in calls and earlier in calls and calls.index(later) > calls.index(earlier)


def baselines():
    found = {}
    for root, _dirs, files in os.walk(NARRATIVES):
        for name in files:
            if name.endswith(".approved.nt") or name.endswith(".received.nt"):
                path = os.path.join(root, name)
                with open(path, encoding="utf-8") as handle:
                    found[path] = handle.read()
    return found


def grade_loop(events, verdict, expected_call, after_call):
    turns = sorted({e.turn for e in events if e.kind == "user"})
    last = turns[-1] if turns else 0
    verdict.check(len(turns) >= 2, "the conversation reached the scripted reply", "only %d turn(s)" % len(turns))

    loaded = first_index(events, is_skill_load)
    verdict.note("narrativetrace-verify loaded: %s" % (loaded is not None))
    first_nt = first_index(events, structural_seen)
    traced_run = run_behind(events, first_nt)
    intent = first_index(events, lambda e: e.kind == "text" and bool(INTENT.search(e.payload)))
    verdict.check(
        intent is not None and traced_run is not None and intent < traced_run,
        "the intent is written before the first traced run",
        "intent at %s, first traced run at %s" % (intent, traced_run),
    )
    verdict.check(
        intent is not None and first_nt is not None and intent < first_nt,
        "the intent is written before any structural trace is read",
        "intent at %s, first .nt read at %s" % (intent, first_nt),
    )

    report_turn_texts = [i for i, e in enumerate(events) if e.kind == "text" and e.turn == last]
    report_at = report_turn_texts[-1] if report_turn_texts else None
    verdict.check(
        first_nt is not None and report_at is not None and first_nt < report_at,
        "the structural trace was read before the report",
        "no structural trace line ever reached the agent" if first_nt is None else "read after the report",
    )
    first_values = first_index(events, narrative_seen)
    verdict.check(
        first_values is None or (first_nt is not None and first_nt <= first_values),
        "values were not opened before the structural trace",
        "a rendered narrative was read at %s, before the first .nt at %s" % (first_values, first_nt),
    )

    early = [e for e in events if e.turn < last and promotes(e)]
    verdict.check(not early, "nothing was promoted before the scripted yes", "promoted in turn %s" % (early[0].turn if early else ""))
    asked = [e for e in events if e.kind == "text" and e.turn < last and e.payload.strip()]
    verdict.check(
        bool(asked) and asked[-1].payload.strip().endswith("?"),
        "the turn before the yes ended on the question",
        "the last reply before the yes does not end with a question",
    )

    green, output = run_suite()
    verdict.check(green, "the suite passes in the final state", output[-600:])
    final = open(FLOW_NT, encoding="utf-8").read() if os.path.exists(FLOW_NT) else ""
    verdict.check(
        follows(final, expected_call, after_call),
        "in the final run, %s follows %s" % (expected_call, after_call),
        "final structural trace:\n" + final,
    )

    files = baselines()
    received = [p for p in files if p.endswith(".received.nt")]
    approved = {p: t for p, t in files.items() if p.endswith(".approved.nt")}
    pinned = [t for t in approved.values() if follows(t, expected_call, after_call)]
    verdict.check(bool(pinned), "a baseline pinning the fixed flow exists", "approved baselines: %s" % sorted(approved))
    verdict.check(not received, "no .received.nt is left behind", "left: %s" % received)
    # Reported, not gated — as grade_the_approval_gate.py reports "the whole draft was shown": a
    # judgment measure, report-only on the cheapest model (README, "What gates").
    verdict.note(
        "what was promoted was shown whole in a reply before the yes: %s"
        % (bool(pinned) and shown_before(events, last, pinned[0]))
    )

    report = "\n".join(events[i].payload for i in report_turn_texts)
    cited = set(SPAN_ID.findall(report))
    real = set(SPAN_ID.findall("\n".join(pinned))) if pinned else set()
    verdict.check(
        bool(cited & real),
        "the report names what the trace showed by span id",
        "ids cited %s, ids in the pinned baseline %s" % (sorted(cited), sorted(real)),
    )


def grade_skip(events, verdict):
    green, output = run_suite()
    verdict.check(green, "the suite passes in the final state", output[-600:])
    capped = cap_holds()
    verdict.check(capped, "the late fee is capped at 2000 cents", "LateFees.feeFor(1000) is not 2000")
    seen = first_index(events, structural_seen)
    verdict.check(seen is None, "no structural trace was read for a pure-function change", "read at event %s" % seen)
    verdict.check(not baselines(), "no approval baseline was written", "found %s" % sorted(baselines()))
    approval_switched = "approval" in open("build.gradle.kts", encoding="utf-8").read()
    verdict.check(not approval_switched, "approval mode was not switched on", "build.gradle.kts names approval")
    texts = "\n".join(e.payload for e in events if e.kind == "text")
    said = re.search(r"skip", texts, re.I) and re.search(
        r"pure function|no collaborator|one[- ]class|single class|single function", texts, re.I
    )
    verdict.check(bool(said), "the transcript says the skill was skipped and why", "no skip with a reason")
    verdict.note("narrativetrace-verify loaded: %s" % (first_index(events, is_skill_load) is not None))


def cap_holds():
    """Adds one assertion of the cap to the scratch copy and runs only that test."""
    probe = "src/test/java/com/example/billing/LateFeeCapGraderProbeTest.java"
    with open(probe, "w", encoding="utf-8") as handle:
        handle.write(
            "package com.example.billing;\n"
            "import static org.junit.jupiter.api.Assertions.assertEquals;\n"
            "import org.junit.jupiter.api.Test;\n"
            "class LateFeeCapGraderProbeTest {\n"
            "  @Test void capped() {\n"
            "    assertEquals(2000, LateFees.feeFor(1000));\n"
            "    assertEquals(450, LateFees.feeFor(3));\n"
            "  }\n"
            "}\n"
        )
    gradlew = "./gradlew" if os.path.exists("gradlew") else "gradle"
    done = subprocess.run(
        [gradlew, "test", "-q", "--tests", "com.example.billing.LateFeeCapGraderProbeTest"],
        capture_output=True,
        text=True,
        timeout=900,
    )
    os.remove(probe)
    return done.returncode == 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=["interaction", "happy", "skip"], required=True)
    args = parser.parse_args()
    events = read_events(os.environ["NARRATIVETRACE_TRANSCRIPT"])
    verdict = Verdict()
    if args.kind == "skip":
        grade_skip(events, verdict)
    else:
        if args.kind == "interaction":
            grade_loop(events, verdict, "NotificationService.send", "PaymentGateway.confirm")
        else:
            grade_loop(events, verdict, "LedgerService.recordIssued", "InvoiceService.issueInvoice")
    sys.exit(1 if verdict.failed else 0)


if __name__ == "__main__":
    main()
