# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
"""Reads a Tier B trial transcript — the record the runner kept of a conversation.

The runner writes one JSON object per line: its own ``{"nt_turn": N, "role": "user", "text": ...}``
marker before each turn, then that turn's own standard output. With the Claude lane's streaming
format that output is itself one JSON object per line, so the whole file is JSONL and a grader can
read it line by line.

Why a grader needs this at all, when every other case in this suite reads world state only: an
approval is only an approval if it arrives in a turn of the USER's own, and "was the report filed
before that turn?" is a question about ORDER. The project's end state cannot answer it — the same
files are on disk either way.

The transcript lives outside the scaffolded project and the agent is never told where it is, so
this is evidence rather than something the subject wrote about itself.
"""

import json
import re


class Turn:
    """One turn: the words the user was given, and everything the agent did with them."""

    def __init__(self, number, user_text):
        self.number = number
        self.user_text = user_text
        self.texts = []
        self.commands = []
        self.tool_results = []
        self.denials = []

    @property
    def said(self):
        """Everything the agent said in this turn, as one block of text."""
        return "\n".join(self.texts)

    @property
    def refused_tools(self):
        """Tools the harness refused this turn.

        Worth reading before blaming the product for anything: an agent refused a tool it needed
        measures the sandbox, not the skill — the lesson round 2 of the init-prompt cases left in
        `evals/README.md`. A denial is NOT by itself a failure: a tool the skill never needed being
        refused is the preset working.
        """
        return list(self.denials)

    @property
    def everything(self):
        """Said, ran and saw — every byte of this turn, for an "appears nowhere" check."""
        return "\n".join(self.texts + self.commands + self.tool_results)

    def __repr__(self):
        return "Turn(%d, %d texts, %d commands)" % (
            self.number,
            len(self.texts),
            len(self.commands),
        )


def _blocks(event):
    """The content blocks of an assistant or user event, whatever shape it arrived in.

    ``message`` is an OBJECT on an assistant or user event and a plain STRING on at least one system
    event (``permission_denied`` carries its sentence there). Reading it as an object unconditionally
    raised ``AttributeError`` inside the grader, which exits non-zero and fails the case for a
    harness reason — found on a real trial, since no hand-written rehearsal fixture had that shape.
    """
    message = event.get("message")
    if not isinstance(message, dict):
        return []
    content = message.get("content")
    return content if isinstance(content, list) else []


def _result_text(block):
    """A tool result is a string on some events and a list of blocks on others."""
    content = block.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(
            part.get("text", "") for part in content if isinstance(part, dict)
        )
    return ""


def _absorb(turn, event):
    """One streamed event into the turn it belongs to."""
    if event.get("subtype") == "permission_denied":
        turn.denials.append(str(event.get("tool_name", "")))
        return
    if event.get("type") == "result" and isinstance(event.get("result"), str):
        turn.texts.append(event["result"])
        return
    for block in _blocks(event):
        if not isinstance(block, dict):
            continue
        kind = block.get("type")
        if kind == "text":
            turn.texts.append(block.get("text", ""))
        elif kind == "tool_use":
            turn.commands.append(json.dumps(block.get("input") or {}, sort_keys=True))
        elif kind == "tool_result":
            turn.tool_results.append(_result_text(block))


def read(path):
    """Every turn of the transcript at ``path``, in order.

    A line that is not JSON is kept as plain agent text rather than dropped: an agent command
    whose own output is not JSON (a text-format override, a crash message) is still something the
    agent put on standard output, and silently discarding it would make an "appears nowhere" check
    pass by not looking.
    """
    turns = []
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                event = json.loads(line)
            except ValueError:
                if turns:
                    turns[-1].texts.append(line)
                continue
            if isinstance(event, dict) and "nt_turn" in event:
                turns.append(Turn(event["nt_turn"], event.get("text", "")))
            elif turns:
                _absorb(turns[-1], event)
    return turns


def whole(turns):
    """Every byte of every turn — said, ran and seen."""
    return "\n".join(turn.everything for turn in turns)


ISSUE_URL = "https://github.com/narrativetrace/narrativetrace-java/issues/new"


def issue_urls(text):
    """Every pre-filled issue-form URL in ``text`` — the one thing that FILES this report."""
    found = []
    at = text.find(ISSUE_URL)
    while at >= 0:
        end = at
        while end < len(text) and not text[end].isspace() and text[end] not in "\"'`)":
            end += 1
        found.append(text[at:end])
        at = text.find(ISSUE_URL, end)
    return found


def gh_invocations(path):
    """Every line the recording ``gh`` stand-in wrote; empty when it was never run."""
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            return [line.strip() for line in handle if line.strip()]
    except FileNotFoundError:
        return []


def scripted_replies(case_dir):
    """The replies ``case_dir``'s own ``case.json`` declares, in turn order.

    Read from the case rather than passed to the grader as an argument, because it was a duplicate
    the moment it existed in both: a `verify.sh --reply "no"` went stale against a `case.json` that
    had grown to `"no, do not file it"`, and the grader failed a compliant trial for it. The case
    file is the one place the conversation is declared — the Java side reads the same field through
    `CaseTurns`.
    """
    text = (case_dir / "case.json").read_text(encoding="utf-8")
    body = re.search(r'"turns"\s*:\s*\{([^{}]*)\}', text)
    if body is None:
        return []
    by_turn = {}
    for entry in re.finditer(r'"([^"]*)"\s*:\s*"((?:[^"\\]|\\.)*)"', body.group(1)):
        by_turn[int(entry.group(1))] = (
            entry.group(2)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace('\\"', '"')
            .replace("\\\\", "\\")
        )
    return [by_turn[turn] for turn in sorted(by_turn)]
