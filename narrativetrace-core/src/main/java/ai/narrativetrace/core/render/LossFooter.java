/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.ArrayList;

/**
 * The one sentence every renderer appends when a capture lost events.
 *
 * <p>INTENT: Shedding is best-effort by design, and best-effort is only acceptable when it is
 * <em>loud</em>. A trace file that quietly omits half a run is worse than no trace file: the reader
 * draws conclusions from an absence that means "the buffer was full", not "this never happened".
 * One definition of the sentence lives here so text, prose, Markdown and both diagram formats say
 * the same thing, differing only in how their syntax spells a comment.
 *
 * <p><b>@llmNote</b> Silent at zero loss, deliberately and by every path: a clean capture's output
 * is byte-for-byte what it was before this existed, so no approved baseline moved and no fixture
 * changed. Only a lossy capture gains a line.
 *
 * <p><b>@edgeCase</b> {@link StructuralTraceRenderer} does <em>not</em> carry this footer, and must
 * not. Its output is the approval baseline and the cross-platform conformance fixture format, and
 * its contract is to be byte-identical for identical <em>behaviour</em>; a footer that appears when
 * an unrelated buffer filled would make every baseline non-deterministic. Loss on that path is
 * reported by {@code NarrativeApproval} instead, which is where the comparison happens.
 */
public final class LossFooter {

  /** The knob a reader who is losing events needs to raise. */
  public static final String CAPACITY_KEY = "narrativetrace.buffer.capacity";

  private static final String PREFIX = "⚠ Incomplete narrative: ";

  private LossFooter() {}

  /**
   * The footer sentence for a tree, or an empty string when the capture lost nothing.
   *
   * @param tree the rendered tree; its {@link TraceTree#loss()} is the source
   * @return the sentence without any leading or trailing newline, or {@code ""}
   */
  public static String sentence(TraceTree tree) {
    return sentence(tree.loss());
  }

  /**
   * The footer sentence for a loss reading, or an empty string when nothing was lost.
   *
   * @param loss what the capture is missing
   * @return the sentence without any leading or trailing newline, or {@code ""}
   */
  public static String sentence(TraceLoss loss) {
    if (loss == null || !loss.any()) {
      return "";
    }
    var parts = new ArrayList<String>();
    if (loss.droppedEvents() > 0) {
      parts.add(
          count(loss.droppedEvents(), "event")
              + " shed under load (buffer full) — raise "
              + CAPACITY_KEY);
    }
    if (loss.refusedScopes() > 0) {
      parts.add(
          count(loss.refusedScopes(), "async scope")
              + " not adopted (adoption cap), "
              + count(loss.refusedSpans(), "span")
              + " missing");
    }
    return PREFIX + String.join("; ", parts) + ".";
  }

  /**
   * The sentence as a block appended to a rendered body, each line carrying {@code linePrefix}.
   *
   * <p>INTENT: Diagram formats need their comment marker on the line and plain formats need
   * nothing; both are the same call with a different prefix, so no renderer re-derives the layout.
   *
   * @param tree the rendered tree
   * @param linePrefix comment syntax for the target format, e.g. {@code "%% "} or {@code "' "}
   * @return {@code "\n\n" + linePrefix + sentence}, or {@code ""} when the capture lost nothing
   */
  public static String block(TraceTree tree, String linePrefix) {
    var sentence = sentence(tree);
    return sentence.isEmpty() ? "" : "\n\n" + linePrefix + sentence;
  }

  private static String count(long value, String noun) {
    return value + " " + noun + (value == 1 ? "" : "s");
  }
}
