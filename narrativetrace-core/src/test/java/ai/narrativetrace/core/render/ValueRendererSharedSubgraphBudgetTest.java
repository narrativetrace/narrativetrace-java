/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.event.RenderedValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * ADV-2026-10-23-1 — rendering a value may never cost more than the value is: the output a graph
 * renders to is bounded by the number of distinct objects in it, not by the number of
 * <em>paths</em> through it.
 *
 * <p>INTENT: {@code RenderWalk}'s ancestor set is scoped to the current path and removed in a
 * {@code finally} on the way out, deliberately, so a shared object reached twice is not mistaken
 * for a cycle ({@code ValueRendererTest#sharedReferenceInSiblingFieldsIsNotFlaggedAsCircular} pins
 * that for one level, and the hostile corpus' {@code diamond} shape for one level too). Nothing
 * bounds the same decision when the sharing repeats down the graph: each rung of a ladder whose two
 * fields hold the same next rung doubles the walk, so an object graph of <em>n</em> objects is
 * rendered as 2<sup>n</sup> nodes. The caps that exist (string 200, collection 5, fields 5, depth
 * {@link RenderWalk#MAX_DEPTH}) all bound one level; none bounds the total, and a graph is allowed
 * to be {@code MAX_DEPTH} deep.
 *
 * <p><b>@llmNote</b> This is the master invariant, not a cosmetic one: at {@code MAX_DEPTH} the
 * flat path renders a 33-object graph to ~2.3 &times; 10<sup>11</sup> characters, on the caller's
 * thread, inside instrumentation. The traced application does not come back.
 */
class ValueRendererSharedSubgraphBudgetTest {

  /** Rungs deep enough to show the doubling, shallow enough that the test itself still finishes. */
  private static final int RUNGS = 16;

  /**
   * Ceilings for a {@value #RUNGS}-rung ladder, which is {@value #RUNGS} + 1 distinct objects.
   * Generous by three orders of magnitude over any honest rendering of seventeen objects, and the
   * walk still blows through them.
   */
  private static final int MAX_CHARACTERS = 64 * 1024;

  private static final int MAX_STRUCTURED_NODES = 4096;

  private final ValueRenderer renderer = new ValueRenderer();

  /** Two fields, one referent: shared, never cyclic, so no cycle guard may refuse it. */
  @SuppressWarnings("unused")
  static final class Rung {
    private final Object left;
    private final Object right;

    Rung(Object next) {
      this.left = next;
      this.right = next;
    }
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  @DisplayName("ADV-2026-10-23-1: a shared-subgraph ladder renders within a bounded ceiling")
  void aSharedSubgraphLadderRendersWithinABoundedCeiling() {
    var rendered = renderer.render(ladder(RUNGS));

    assertThat(rendered.length())
        .as("%d objects rendered to %d characters", RUNGS + 1, rendered.length())
        .isLessThan(MAX_CHARACTERS);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  @DisplayName("ADV-2026-10-23-1: the structured path bounds the same ladder")
  void theStructuredPathBoundsTheSameLadder() {
    var nodes = countNodes(renderer.renderStructured(ladder(RUNGS)));

    assertThat(nodes)
        .as("%d objects rendered to %d structured nodes", RUNGS + 1, nodes)
        .isLessThan(MAX_STRUCTURED_NODES);
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  @DisplayName("B-185: a depth-20 ladder renders in linear size on both paths")
  void aDepth20LadderRendersInLinearSizeOnBothPaths() {
    var ladder = ladder(20);

    var flat = renderer.render(ladder);
    var nodes = countNodes(renderer.renderStructured(ladder));

    assertThat(flat.length()).as("21 objects, flat").isLessThan(4096);
    assertThat(nodes).as("21 objects, structured").isLessThan(4 * 21);
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  @DisplayName("B-185: a ladder as deep as the depth cap renders in linear size on both paths")
  void aLadderAsDeepAsTheDepthCapRendersInLinearSizeOnBothPaths() {
    var ladder = ladder(RenderWalk.MAX_DEPTH);

    var flat = renderer.render(ladder);
    var nodes = countNodes(renderer.renderStructured(ladder));

    assertThat(flat.length()).as("33 objects, flat").isLessThan(8192);
    assertThat(nodes).as("33 objects, structured").isLessThan(4 * 33);
  }

  @Test
  @DisplayName("B-185: a repeated instance renders as a back-reference, the same on both paths")
  void aRepeatedInstanceRendersAsABackReferenceTheSameOnBothPaths() {
    var ladder = ladder(4);

    var flat = renderer.render(ladder);
    var structured = renderer.renderStructured(ladder);

    assertThat(flat).contains("<repeat: Rung>");
    assertThat(occurrences(flat, "<repeat: Rung>"))
        .as("one back-reference per rung that holds a non-trivial rung twice")
        .isEqualTo(countStrings(structured, "<repeat: Rung>"))
        .isPositive();
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  @DisplayName("B-185: an unshared graph past the total budget ends in the node-budget marker")
  void anUnsharedGraphPastTheTotalBudgetEndsInTheNodeBudgetMarker() {
    var tree = distinctTree(6);

    var flat = renderer.render(tree);
    var structured = renderer.renderStructured(tree);

    assertThat(flat).contains("<max-nodes>");
    assertThat(countStrings(structured, "<max-nodes>")).isPositive();
    assertThat(countNodes(structured))
        .as("the budget bounds the walk, the open path's siblings add only markers")
        .isLessThan(RenderWalk.MAX_NODES + 5 * RenderWalk.MAX_DEPTH);
  }

  /** A summary hook that counts its own invocations. */
  static final class Counted {
    int calls;

    @NarrativeSummary
    public String toNarrativeSummary() {
      calls++;
      return "counted";
    }
  }

  @Test
  @DisplayName("B-185 hypothesis: a summary hook runs at most once per instance per capture")
  void aSummaryHookRunsAtMostOncePerInstancePerCapture() {
    var counted = new Counted();

    var capture = renderer.renderForCapture(List.of(counted, counted, List.of(counted)));

    assertThat(capture.rendered()).isEqualTo("[counted, counted, [counted]]");
    assertThat(counted.calls).as("one capture, one instance, two render paths").isEqualTo(1);
  }

  /** A five-wide tree of distinct lists, {@code levels} deep: no sharing at all. */
  private static Object distinctTree(int levels) {
    if (levels == 0) {
      return "leaf";
    }
    var children = new ArrayList<Object>();
    for (var i = 0; i < 5; i++) {
      children.add(distinctTree(levels - 1));
    }
    return children;
  }

  private static int occurrences(String text, String needle) {
    var count = 0;
    for (var i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
      count++;
    }
    return count;
  }

  private static int countStrings(RenderedValue value, String text) {
    if (value instanceof RenderedValue.ObjectVal object) {
      return object.fields().values().stream().mapToInt(v -> countStrings(v, text)).sum();
    }
    if (value instanceof RenderedValue.ListVal list) {
      return list.elements().stream().mapToInt(v -> countStrings(v, text)).sum();
    }
    return value instanceof RenderedValue.StringVal s && s.value().equals(text) ? 1 : 0;
  }

  /** A ladder of {@code rungs} rungs: {@code rungs + 1} distinct objects, 2^{@code rungs} paths. */
  private static Object ladder(int rungs) {
    Object current = "leaf";
    for (var i = 0; i < rungs; i++) {
      current = new Rung(current);
    }
    return current;
  }

  private static int countNodes(RenderedValue value) {
    if (value instanceof RenderedValue.ObjectVal object) {
      return 1 + countNodes(object.fields().values());
    }
    if (value instanceof RenderedValue.ListVal list) {
      return 1 + countNodes(list.elements());
    }
    return 1;
  }

  private static int countNodes(Collection<RenderedValue> children) {
    var total = 0;
    for (var child : children) {
      total += countNodes(child);
    }
    return total;
  }
}
