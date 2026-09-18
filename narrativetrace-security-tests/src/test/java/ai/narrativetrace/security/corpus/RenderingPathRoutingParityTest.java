/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.core.render.ValueRenderer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every {@code graphs.json} row, routed through BOTH rendering paths, must reach the same
 * enumeration verdict.
 *
 * <p>INTENT: The rendering rule ("rendering reads state, never runs behaviour", owner ruling
 * 2026-09-17) is a property of the renderer, not of one of its two output encodings — and it
 * drifted exactly that way: the flat path grew the origin dispatch while {@code
 * renderStructuredComplex} went on enumerating any {@code Collection}/{@code Map} through the
 * value's own overridable members. The two paths now switch over one shared decision, and this is
 * the test that keeps them there: whatever the corpus throws at them, both must agree on whether
 * the value's elements were walked, and how.
 *
 * <p><b>@llmNote</b> The witness is deliberately COARSE — elements, entries, an opaque marker, or
 * not enumerated at all — because that, and only that, is what the shared decision decides. The two
 * paths encode the same verdict differently on purpose (a {@code ListVal} against {@code [...]}, a
 * cap as a list size against a trailing {@code …}), so asserting the renderings equal would pin the
 * encodings rather than the routing.
 *
 * <p><b>@edgeCase</b> Two documented asymmetries are read through, not around. A value that is not
 * an {@code Iterable}, {@code Map}, {@code Map.Entry}, array or {@code AtomicReferenceArray} can
 * never be enumerated by either path, so it is {@code NOT_ENUMERATED} by shape — this keeps a
 * hostile scalar whose own {@code toString()} happens to start with {@code [} from being misread as
 * a list. And a standalone {@code Map.Entry} renders {@code key=value} flat but a one-field {@code
 * Map} object structured, by design (see {@code renderStructuredEntry}); both are entries, and the
 * witness says so.
 */
class RenderingPathRoutingParityTest {

  /** What the shared origin decision decided, as far as an output can witness it. */
  private enum Route {
    ELEMENTS,
    ENTRIES,
    OPAQUE,
    NOT_ENUMERATED
  }

  private static final String OPAQUE_SUFFIX = "<size unknown>";

  private final ValueRenderer renderer = new ValueRenderer();

  private static Stream<GraphCase> corpus() {
    return HostileCorpus.graphs().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  void bothRenderingPathsRouteEveryCorpusRowToTheSameEnumerationVerdict(GraphCase graphCase) {
    var graph = HostileGraphs.build(graphCase, "sentinel-" + graphCase.id());

    var flat = flatRoute(graph, renderer.render(graph));
    var structured = structuredRoute(graph, renderer.renderStructured(graph));

    assertThat(structured)
        .as("%s: the structured path must route exactly as the flat path does", graphCase.id())
        .isEqualTo(flat);
  }

  private static Route flatRoute(Object graph, String text) {
    if (!mayBeEnumerated(graph)) {
      return Route.NOT_ENUMERATED;
    }
    if (graph instanceof Map.Entry) {
      return Route.ENTRIES;
    }
    if (text.endsWith(OPAQUE_SUFFIX)) {
      return Route.OPAQUE;
    }
    if (text.startsWith("[")) {
      return Route.ELEMENTS;
    }
    return text.startsWith("{") ? Route.ENTRIES : Route.NOT_ENUMERATED;
  }

  private static Route structuredRoute(Object graph, RenderedValue value) {
    if (!mayBeEnumerated(graph)) {
      return Route.NOT_ENUMERATED;
    }
    if (graph instanceof Map.Entry) {
      return Route.ENTRIES;
    }
    if (value instanceof RenderedValue.StringVal s && s.value().endsWith(OPAQUE_SUFFIX)) {
      return Route.OPAQUE;
    }
    if (value instanceof RenderedValue.ListVal) {
      return Route.ELEMENTS;
    }
    return value instanceof RenderedValue.ObjectVal o && "Map".equals(o.typeName())
        ? Route.ENTRIES
        : Route.NOT_ENUMERATED;
  }

  /** The only shapes either path can ever walk elements out of. */
  private static boolean mayBeEnumerated(Object graph) {
    return graph instanceof Iterable
        || graph instanceof Map
        || graph instanceof Map.Entry
        || graph instanceof AtomicReferenceArray
        || graph != null && graph.getClass().isArray();
  }
}
