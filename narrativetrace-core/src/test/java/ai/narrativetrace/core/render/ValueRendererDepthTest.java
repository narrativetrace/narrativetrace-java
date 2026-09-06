/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.annotation.NotTraced;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Regression: a deep object graph is not a cycle, and following one recursively used to be a {@link
 * StackOverflowError} raised inside instrumentation.
 *
 * <p>INTENT: The cycle guard answers "have I been here before?", which a chain never trips. Found
 * by {@code narrativetrace-security-tests} replaying the hostile corpus' {@code depth-10000-chain}
 * shape: a linked list, a parent-child tree or a JSON document mapped to nested maps is enough, and
 * the {@code Error} escapes {@code render()} into the traced application — an observability failure
 * turned into an application failure, which the pipeline contract forbids.
 *
 * <p><b>@llmNote</b> The depth cap is the fourth cap beside the string, collection and field
 * limits, and it behaves like them: the output says it truncated. A reader who sees {@code
 * <max-depth>} has learned that the graph continued.
 */
class ValueRendererDepthTest {

  private final ValueRenderer renderer = new ValueRenderer();

  /** One component, so a chain of these is depth and nothing else. */
  record Node(Object next) {}

  /** The shape the leak-hunting corpus carries: a redacted component under an arbitrary depth. */
  record Card(String number, @NotTraced String cvv) {}

  @Test
  void aTenThousandDeepRecordChainRendersInsteadOfOverflowingTheStack() {
    var deep = chainOfRecords(10_000);

    assertThatCode(() -> renderer.render(deep)).doesNotThrowAnyException();
  }

  @Test
  void theStructuredPathSurvivesTheSameChain() {
    var deep = chainOfRecords(10_000);

    assertThatCode(() -> renderer.renderStructured(deep)).doesNotThrowAnyException();
  }

  @Test
  void aDeepChainOfEveryContainerKindAlsoSurvives() {
    assertThatCode(() -> renderer.render(chainOf(10_000, List::of))).doesNotThrowAnyException();
    assertThatCode(() -> renderer.render(chainOf(10_000, Optional::of))).doesNotThrowAnyException();
    assertThatCode(() -> renderer.render(chainOf(10_000, inner -> new Object[] {inner})))
        .doesNotThrowAnyException();
    assertThatCode(() -> renderer.render(chainOf(10_000, ValueRendererDepthTest::singleEntryMap)))
        .doesNotThrowAnyException();
  }

  @Test
  void theTruncatedGraphSaysSoRatherThanFallingSilent() {
    var deep = chainOfRecords(RenderWalk.MAX_DEPTH + 5);

    assertThat(renderer.render(deep)).contains("<max-depth>");
  }

  @Test
  void aGraphExactlyAtTheCapRendersWholeWithNoMarker() {
    var atCap = chainOfRecords(RenderWalk.MAX_DEPTH - 1);

    assertThat(renderer.render(atCap)).doesNotContain("<max-depth>").contains("\"leaf\"");
  }

  @Test
  void aRedactedComponentBelowTheCapIsStillRedactedRatherThanTruncated() {
    var nested = chainAround(new Card("4111", "cvv-901"), 4);

    assertThat(renderer.render(nested)).contains("[REDACTED]").doesNotContain("cvv-901");
  }

  @Test
  void aRedactedComponentBeyondTheCapIsUnreachableRatherThanPrinted() {
    var buried = chainAround(new Card("4111", "cvv-901"), RenderWalk.MAX_DEPTH + 10);

    assertThat(renderer.render(buried)).contains("<max-depth>").doesNotContain("cvv-901");
  }

  @Test
  void theCapIsPerPathRatherThanPerRenderCall() {
    var siblings = new ArrayList<Object>();
    siblings.add(chainOfRecords(3));
    siblings.add(new Card("4111", "cvv-901"));

    assertThat(renderer.render(siblings))
        .as("a shallow sibling after a deep one must still render in full")
        .contains("[REDACTED]");
  }

  private static Object chainOfRecords(int depth) {
    return chainOf(depth, Node::new);
  }

  private static Object chainOf(int depth, java.util.function.UnaryOperator<Object> wrap) {
    Object current = "leaf";
    for (var i = 0; i < depth; i++) {
      current = wrap.apply(current);
    }
    return current;
  }

  /** The payload buried under {@code depth} record levels. */
  private static Object chainAround(Object payload, int depth) {
    Object current = payload;
    for (var i = 0; i < depth; i++) {
      current = new Node(current);
    }
    return current;
  }

  private static Object singleEntryMap(Object inner) {
    var map = new LinkedHashMap<String, Object>();
    map.put("next", inner);
    return Map.copyOf(map);
  }
}
