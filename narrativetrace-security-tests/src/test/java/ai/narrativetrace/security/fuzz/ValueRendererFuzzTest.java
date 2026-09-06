/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.security.corpus.GraphCase;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.corpus.HostileGraphs;
import ai.narrativetrace.security.oracle.Oracles;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.List;

/**
 * Tier B target 2: coverage-guided fuzzing of the value renderer, with the redaction oracle.
 *
 * <p>INTENT: The graph is built from the fuzzer's own bytes rather than from a fixed list — the
 * leading bytes choose a corpus shape, a wrapper stack, a depth and a width, and the rest becomes a
 * hostile string inside it. Coverage then steers those choices toward the renderer's untaken
 * branches, which is the whole reason this tier exists beside the property.
 *
 * <p><b>@llmNote</b> The sentinel is fresh per input. A fixed secret would let one input's passing
 * output mask another's, and would be findable by a renderer that special-cased it.
 *
 * <p><b>@edgeCase</b> Depth and width are capped well below what the property already covers. This
 * target exists to explore <em>shape combinations</em> under coverage guidance, not to re-measure
 * scale; an uncapped width would spend the whole budget allocating.
 */
class ValueRendererFuzzTest {

  private static final List<String> LAYERS =
      List.of(
          "optional",
          "atomicReference",
          "atomicReferenceArray",
          "entryValue",
          "entryKey",
          "future",
          "list",
          "array",
          "map",
          "record",
          "holder");

  /** Field names the vocabulary corpus says must hide their value, in every language it carries. */
  private static final List<String> SENSITIVE_NAMES =
      HostileCorpus.redactions().stream()
          .filter(ai.narrativetrace.security.corpus.RedactionCase::isName)
          .filter(ai.narrativetrace.security.corpus.RedactionCase::expectsRedaction)
          .map(ai.narrativetrace.security.corpus.RedactionCase::name)
          .toList();

  private static final int MAX_STACK = 8;
  private static final int MAX_WIDTH = 64;

  private final ValueRenderer renderer = new ValueRenderer();

  @FuzzTest(maxDuration = FuzzBudget.PER_TARGET)
  void noGraphLeaksTheRedactedComponent(FuzzedDataProvider data) {
    var sentinel = Oracles.freshSentinel();
    var graph = build(data, sentinel);

    var flat = renderer.render(graph);
    var structured = String.valueOf(renderer.renderStructured(graph));

    assertThat(flat).doesNotContain(sentinel);
    assertThat(structured).doesNotContain(sentinel);
  }

  /**
   * Either a shape the corpus names, a sensitive field name the vocabulary corpus names, or a
   * generated wrapper stack around the sentinel record.
   *
   * <p><b>@llmNote</b> The vocabulary branch is how this target inherits {@code redaction.json}:
   * the sentinel is planted behind a name the deny-list must know, wrapped in whatever stack the
   * fuzzer's bytes chose. Only rows declaring {@code redacted} are used — planting the sentinel
   * behind {@code circuitBreaker} would assert the opposite of what that row exists to pin.
   */
  private static Object build(FuzzedDataProvider data, String sentinel) {
    var corpus = HostileCorpus.graphs();
    if (data.consumeBoolean()) {
      return HostileGraphs.build(corpus.get(data.consumeInt(0, corpus.size() - 1)), sentinel);
    }
    if (data.consumeBoolean()) {
      return underASensitiveName(data, sentinel);
    }
    var layers = new java.util.ArrayList<String>();
    var stackSize = data.consumeInt(0, MAX_STACK);
    for (var i = 0; i < stackSize; i++) {
      layers.add(LAYERS.get(data.consumeInt(0, LAYERS.size() - 1)));
    }
    return HostileGraphs.build(generated(layers, data.consumeInt(0, MAX_WIDTH)), sentinel);
  }

  private static GraphCase generated(List<String> layers, int width) {
    if (layers.isEmpty()) {
      return new GraphCase(
          "fuzz-width",
          "generated width",
          "width",
          List.of(),
          null,
          "list",
          null,
          null,
          "secret-record",
          width);
    }
    return new GraphCase(
        "fuzz-stack",
        "generated stack",
        null,
        List.copyOf(layers),
        null,
        null,
        null,
        null,
        "secret-record",
        0);
  }

  /** The sentinel behind a deny-listed field name, inside a wrapper stack the fuzzer chose. */
  private static Object underASensitiveName(FuzzedDataProvider data, String sentinel) {
    var names = SENSITIVE_NAMES;
    var payload =
        java.util.Map.of(names.get(data.consumeInt(0, names.size() - 1)), (Object) sentinel);
    var layers = new java.util.ArrayList<String>();
    var stackSize = data.consumeInt(0, MAX_STACK);
    for (var i = 0; i < stackSize; i++) {
      layers.add(LAYERS.get(data.consumeInt(0, LAYERS.size() - 1)));
    }
    return HostileGraphs.wrap(layers, payload);
  }
}
