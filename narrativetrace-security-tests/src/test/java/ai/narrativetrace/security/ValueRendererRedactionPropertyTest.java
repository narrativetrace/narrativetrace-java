/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.security.corpus.GraphCase;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.corpus.HostileGraphs;
import ai.narrativetrace.security.oracle.Emitters;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * Target 2 of the parity document's fuzzing list, and the reason the suite exists: the value
 * renderer over hostile object graphs, with the <b>redaction oracle</b>.
 *
 * <p>INTENT: Redaction is the one rendering rule whose failure mode is a leak rather than an ugly
 * line, and every instance of it found so far ({@code Optional}, {@code AtomicReference}, {@code
 * AtomicReferenceArray}, a standalone {@code Map.Entry}, a template naming a redacted path) was
 * found by a reader looking at output, not by a test. The oracle here is mechanical instead: plant
 * a random token behind {@code @NotTraced} anywhere in an arbitrary graph, render the graph, drive
 * every emitter the product ships, and assert the token is in no byte of any of them.
 *
 * <p><b>@llmNote</b> The sentinel is fresh per case and checked whole *and* by prefix. A partial
 * leak through a truncating emitter is still a leak, and a fixed secret string would let one case's
 * passing output hide another's.
 *
 * <p><b>@edgeCase</b> Only graph cases whose corpus entry says {@code payload: "secret-record"}
 * carry a sentinel. Cases that carry prose in an exception message deliberately do not: showing an
 * application's own exception message is the contract, so asserting containment there would pin the
 * opposite of it.
 */
class ValueRendererRedactionPropertyTest {

  private final ValueRenderer renderer = new ValueRenderer();

  @Test
  void everyHostileGraphKeepsARedactedValueOutOfEveryOutput() {
    for (var graphCase : HostileCorpus.graphs()) {
      if (graphCase.carriesSecret()) {
        assertContained(graphCase);
      }
    }
  }

  @Test
  void everyHostileGraphRendersWithoutThrowingAndInBoundedTime() {
    for (var graphCase : HostileCorpus.graphs()) {
      var graph = HostileGraphs.build(graphCase, Oracles.freshSentinel());
      Oracles.withinBudget("render " + graphCase.id(), () -> renderer.render(graph));
      Oracles.withinBudget(
          "renderStructured " + graphCase.id(), () -> renderer.renderStructured(graph));
    }
  }

  /**
   * A graph the renderer cannot walk must still say so in a way a reader can act on. Silence would
   * satisfy containment too, which is why this exists beside it.
   */
  @Test
  void aRedactedComponentShowsTheMarkerRatherThanNothing() {
    var sentinel = Oracles.freshSentinel();
    var rendered = renderer.render(HostileGraphs.secret(sentinel));

    assertThat(rendered).contains("[REDACTED]").doesNotContain(sentinel);
  }

  @Test
  void renderingIsIdempotentForEveryHostileGraph() {
    for (var graphCase : HostileCorpus.graphs()) {
      var sentinel = Oracles.freshSentinel();
      var graph = HostileGraphs.build(graphCase, sentinel);
      Oracles.idempotent("render " + graphCase.id(), () -> renderer.render(graph));
    }
  }

  @Test
  void renderingStartsNoBackgroundThread() {
    for (var graphCase : HostileCorpus.graphs()) {
      renderer.render(HostileGraphs.build(graphCase, Oracles.freshSentinel()));
    }

    Oracles.noLibraryThreadLeft();
  }

  /**
   * The bug class rather than the reported instance: redaction survives <em>any</em> stack of the
   * wrappers the renderer opens, to any depth, in any order. Every leak found so far was one
   * particular stack of depth one.
   */
  @Property(tries = 120)
  void aRedactedComponentSurvivesAnyStackOfWrappers(
      @ForAll("wrapperStacks") java.util.List<String> layers) {
    var sentinel = Oracles.freshSentinel();
    var graphCase =
        new GraphCase(
            "generated",
            "generated stack",
            null,
            layers,
            null,
            null,
            null,
            null,
            "secret-record",
            0);

    assertContained(graphCase, sentinel);
  }

  /** The same, through the structured path the OpenTelemetry exporter reads. */
  @Property(tries = 120)
  void theStructuredPathRedactsWhereverTheFlatPathDoes(
      @ForAll("wrapperStacks") java.util.List<String> layers) {
    var sentinel = Oracles.freshSentinel();
    var graph =
        HostileGraphs.build(
            new GraphCase(
                "generated",
                "generated stack",
                null,
                layers,
                null,
                null,
                null,
                null,
                "secret-record",
                0),
            sentinel);

    assertThat(String.valueOf(renderer.renderStructured(graph))).doesNotContain(sentinel);
  }

  /** Depth alone must not defeat the guard: a chain is not a cycle, and neither may leak. */
  @Property(tries = 50)
  void aRedactedComponentSurvivesAnArbitrarilyDeepChain(
      @ForAll @IntRange(min = 1, max = 200) int depth) {
    var sentinel = Oracles.freshSentinel();
    var graphCase =
        new GraphCase(
            "generated-depth",
            "generated chain",
            "repeatLayer",
            java.util.List.of(),
            "holder",
            null,
            null,
            null,
            "secret-record",
            depth);

    assertContained(graphCase, sentinel);
  }

  /** Width alone must not defeat it either: the payload may sit past any truncation limit. */
  @Property(tries = 30)
  void aRedactedComponentSurvivesAnArbitrarilyWideContainer(
      @ForAll @IntRange(min = 0, max = 500) int width, @ForAll("containers") String container) {
    var sentinel = Oracles.freshSentinel();
    var graphCase =
        new GraphCase(
            "generated-width",
            "generated width",
            "width",
            java.util.List.of(),
            null,
            container,
            null,
            null,
            "secret-record",
            width);

    assertContained(graphCase, sentinel);
  }

  private void assertContained(GraphCase graphCase) {
    assertContained(graphCase, Oracles.freshSentinel());
  }

  private void assertContained(GraphCase graphCase, String sentinel) {
    var graph = HostileGraphs.build(graphCase, sentinel);
    var outputs =
        Oracles.withinBudget("every output for " + graphCase.id(), () -> everyOutput(graph));

    Oracles.containsNoSentinel(outputs, sentinel);
    Oracles.boundedSize(outputs);
  }

  /** Both renderer paths, then every emitter the product ships, over one graph. */
  private Map<String, String> everyOutput(Object graph) {
    var flat = renderer.render(graph);
    var outputs = new LinkedHashMap<String, String>();
    outputs.put("renderer:value-flat", flat);
    outputs.put("renderer:value-structured", String.valueOf(renderer.renderStructured(graph)));
    outputs.putAll(Emitters.everyOutput(Emitters.treeOf(flat, flat)));
    return outputs;
  }

  @Provide
  Arbitrary<java.util.List<String>> wrapperStacks() {
    return Arbitraries.of(
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
            "holder")
        .list()
        .ofMinSize(0)
        .ofMaxSize(6);
  }

  @Provide
  Arbitrary<String> containers() {
    return Arbitraries.of("list", "listWithNulls", "array", "map");
  }
}
