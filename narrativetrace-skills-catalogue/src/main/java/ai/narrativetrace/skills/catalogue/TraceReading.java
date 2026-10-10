/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.SkillSection;

/**
 * How to read a trace — the reference text every skill that reads one renders, written once.
 *
 * <p>INTENT: the verify skill reads a trace to check a change, the debug skill to find a defect;
 * both need the same answer to "which artifact answers which question" and the same list of shapes
 * that mean something went wrong. Two {@link SkillSection} constants rendered into both pages keep
 * the two skills from teaching two different ways to read the same file.
 */
final class TraceReading {

  /** Which flavour answers which question, cheapest first, with where each one is written. */
  static final SkillSection FLAVOURS =
      new SkillSection(
          "Which flavour answers which question",
          """
| flavour | where | carries | answers |
|---|---|---|---|
| structural `.nt` | `build/narrativetrace/structural/<TestClass>/<scenario>.nt` | shape only: calls, order, nesting, parameter names, multiplicity, span ids — dozens of lines for a whole flow | did the flow do what I meant? |
| Markdown narrative | `build/narrativetrace/traces/<TestClass>/<scenario>.md` | values (redacted), outcomes, durations | what value crossed this boundary? — read one span, by id |
| indented text | `IndentedTextRenderer`, the console | the same values as plain text | the same question, in a console or a failure message |
| sequence diagram | `build/narrativetrace/diagrams/<TestClass>/<scenario>.mmd` | who called whom, in order, across threads | ordering across components and threads |
| approval delta | the failing test's message: `.received.nt` against `.approved.nt` | what changed in the shape, citing both sides' ids | is this change intended? |
| prose | `ProseRenderer` | narration for a person | explaining the flow to the user — never read it to check the code |

Use the cheapest flavour that answers the question, and look at values only where the
shape says to look. A span id (`#1`, `#1.3`, `#1.3.2`) is the span's position in the tree
and the same in every flavour: find in the `.md` the span the `.nt` flagged by its id.
Redaction stays on — the deny-list and `[REDACTED]` are never turned off to see more; a
redacted value that matters is reasoned about by its parameter name and the shape around
it.
""");

  /** The shapes in a structural trace that mean the flow did something other than intended. */
  static final SkillSection SHAPES =
      new SkillSection(
          "Shapes that mean something went wrong",
          """
          - a call made twice that the intent makes once
          - a call before its precondition — a notification before the payment that it announces
          - a branch never taken that the intent takes
          - a retry that masks a failure
          - a side effect inside a loop
          - a swallowed exception: a thrown outcome `!!` under a call that returned normally
          - a cleanup that never ran
          - a value crossing a boundary that should have been redacted
          """);

  /** Every claim about a trace points at the span it rests on. */
  static final ReasonedRule CITE_SPAN_IDS =
      new ReasonedRule(
          "Cite a span id for every claim about the trace",
          "an id points at one span in every flavour, so a reviewer can check the claim; a claim"
              + " without one cannot be checked");

  /** Redaction is never the price of seeing more. */
  static final ReasonedRule NEVER_REDACTION_OFF =
      new ReasonedRule(
          "Never turn redaction off to see more",
          "a redacted value that matters is reasoned about by its name and shape; turning"
              + " redaction off puts the user's secrets in the transcript");

  private TraceReading() {}
}
