/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ProseRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.security.corpus.GraphCase;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.corpus.HostileGraphs;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Replays every {@code graphs.json} shape through the CAPTURE path, not the renderer.
 *
 * <p>INTENT: {@code ValueRendererRedactionPropertyTest} drives {@code ValueRenderer} directly,
 * which is a layer below the one an application uses. The oracle contract's clause 2 is about the
 * other layer: the event reaches the audit emitter, the buffered consumer and every listener
 * attached through the pipeline SPI, none of which is a renderer, so a secret merely masked on its
 * way out of one has already travelled. This class puts each graph in through the door a caller
 * uses — a traced interface method — and asserts against the captured {@link ParameterCapture} as
 * well as the text.
 *
 * <p><b>@llmNote</b> The parameter is deliberately named {@code graph}: a name no deny-list knows.
 * Under {@code password} the name axis would answer at capture and every row would pass without the
 * graph ever being walked, which is the failure mode this whole suite exists to make impossible.
 *
 * <p><b>@edgeCase</b> The call must also SUCCEED. A graph whose {@code toString}, summary method or
 * accessor throws is host-supplied code running inside instrumentation; the traced method's own
 * result always wins, so the return value is asserted beside the containment.
 */
class CapturePathGraphConformanceTest {

  /** The interface every case is traced through; {@code graph} is not a deny-listed name. */
  public interface GraphService {

    /**
     * Echoes a fixed token, so the assertion below distinguishes "the call ran" from "the proxy
     * swallowed it".
     *
     * @param graph the hostile object graph under test
     * @return the fixed token {@code ok}
     */
    String handle(Object graph);
  }

  private static Stream<GraphCase> corpus() {
    return HostileCorpus.graphs().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  @DisplayName("every graph shape survives a real traced call without leaking or failing it")
  void everyGraphShapeHoldsAtCapture(GraphCase graphCase) {
    var sentinel = Oracles.freshSentinel();
    var graph = HostileGraphs.build(graphCase, sentinel);
    var context = new ThreadLocalNarrativeContext();
    var traced =
        NarrativeTraceProxy.trace((GraphService) argument -> "ok", GraphService.class, context);

    assertThat(traced.handle(graph))
        .as("[%s] a hostile graph must not change what the traced method returns", graphCase.id())
        .isEqualTo("ok");

    var tree = context.captureTrace();
    var outputs = new LinkedHashMap<String, String>();
    outputs.put("capture:parameters", renderedParameters(tree));
    outputs.put("emitter:markdown", new MarkdownRenderer().render(tree));
    outputs.put("emitter:prose", new ProseRenderer().render(tree));
    outputs.put("emitter:indented", new IndentedTextRenderer().render(tree));

    if (graphCase.carriesSecret()) {
      Oracles.containsNoSentinel(Map.copyOf(outputs), sentinel);
    }
    Oracles.boundedSize(Map.copyOf(outputs));
  }

  private static String renderedParameters(ai.narrativetrace.api.tree.TraceTree tree) {
    return tree.roots().stream()
        .flatMap(node -> node.signature().parameters().stream())
        .map(ParameterCapture::renderedValue)
        .reduce("", (left, right) -> left + "\n" + right);
  }
}
