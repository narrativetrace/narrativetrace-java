/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.corpus.TraceShapes;
import ai.narrativetrace.security.oracle.Emitters;
import ai.narrativetrace.security.oracle.Formats;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * A deep or cyclic {@code TraceNode} tree must not crash or hang any renderer or exporter — {@code
 * ai.narrativetrace.core.tree.TreeWalk} is the shared bound every one of them now goes through.
 * Companion to {@code OutputFormatPropertyTest} (target 3 of the parity document's fuzzing list),
 * scoped to the tree-shape corpus rather than the value corpus: those hostile-corpus.strings cases
 * exercise a hostile *value* inside an otherwise ordinary one-node tree, this one exercises a
 * hostile *tree structure* around an ordinary value.
 */
class TraceShapeBoundPropertyTest {

  @TestFactory
  List<DynamicTest> everyTraceShapeLeavesEveryFormatWellFormedAndBounded() {
    return HostileCorpus.traceShapes().stream()
        .map(
            shape ->
                DynamicTest.dynamicTest(
                    shape.id(),
                    () -> {
                      var tree = TraceShapes.build(shape);
                      var outputs =
                          Oracles.withinBudget(shape.id(), () -> Emitters.everyOutput(tree));

                      Oracles.boundedSize(outputs);
                      Formats.validatesAgainstChapterTreeSchema(
                          "renderer:json", outputs.get("renderer:json"));
                      Formats.isWellFormedMermaid(
                          "renderer:mermaid", outputs.get("renderer:mermaid"));
                      Formats.isWellFormedPlantUml(
                          "renderer:plantuml", outputs.get("renderer:plantuml"));
                      Formats.everyJsonArtifactParses(outputs);
                    }))
        .toList();
  }

  /**
   * The cyclic shapes specifically: every renderer must say so, with the same marker text {@link
   * ai.narrativetrace.core.tree.TreeWalk.Reason#CYCLE} defines, not merely avoid crashing.
   */
  @TestFactory
  List<DynamicTest> everyCyclicTraceShapeCarriesTheCycleMarker() {
    return HostileCorpus.traceShapes().stream()
        .filter(shape -> "cycle".equals(shape.kind()))
        .map(
            shape ->
                DynamicTest.dynamicTest(
                    shape.id(),
                    () -> {
                      var tree = TraceShapes.build(shape);
                      Map<String, String> outputs = Emitters.renderers(tree);

                      assertThat(outputs.get("renderer:mermaid")).contains("(cycle)");
                      assertThat(outputs.get("renderer:plantuml")).contains("(cycle)");
                      assertThat(outputs.get("renderer:markdown")).contains("(cycle)");
                    }))
        .toList();
  }
}
