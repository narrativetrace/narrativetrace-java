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
import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.core.render.ValueRenderer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every {@code graphs.json} row, rendered through BOTH paths, must reach the same verdict: the same
 * enumeration routing, and the same answer on whether the deny-list withheld anything.
 *
 * <p>INTENT: The rendering rule ("rendering reads state, never runs behaviour", owner ruling
 * 2026-09-17) is a property of the renderer, not of one of its two output encodings — and it
 * drifted exactly that way: the flat path grew the origin dispatch while {@code
 * renderStructuredComplex} went on enumerating any {@code Collection}/{@code Map} through the
 * value's own overridable members. The two paths now switch over one shared decision, and this is
 * the test that keeps them there: whatever the corpus throws at them, both must agree on whether
 * the value's elements were walked, and how.
 *
 * <p><b>@llmNote</b> The routing witness is deliberately COARSE — elements, entries, an opaque
 * marker, or not enumerated at all — because that, and only that, is what the origin decision
 * decides. The two paths encode the same verdict differently on purpose (a {@code ListVal} against
 * {@code [...]}, a cap as a list size against a trailing {@code …}), so asserting the renderings
 * equal would pin the encodings rather than the routing.
 *
 * <p><b>@llmNote</b> Routing alone was not enough, and the gap was this shape of test's own blind
 * spot rather than an oversight in a row: every scalar is {@code NOT_ENUMERATED} on both paths, so
 * a value one channel READ through its own text while the other WALKED its fields witnessed as
 * agreement. The second half of the verdict — did the deny-list withhold anything — is what closes
 * it, and it is what the {@code number-subclass-tostring-door} row needs. See {@link #withheld}.
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

  /**
   * The two things a rendering witnesses about the shared decision: how the value's elements were
   * routed, and whether the deny-list withheld any part of it.
   */
  private record Verdict(Route route, boolean withheld) {}

  private static final String OPAQUE_SUFFIX = "<size unknown>";

  private final ValueRenderer renderer = new ValueRenderer();

  private static Stream<GraphCase> corpus() {
    return HostileCorpus.graphs().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  void bothRenderingPathsReachTheSameVerdictOnEveryCorpusRow(GraphCase graphCase) {
    var graph = HostileGraphs.build(graphCase, "sentinel-" + graphCase.id());

    var flatText = renderer.render(graph);
    var structuredValue = renderer.renderStructured(graph);

    var flat = new Verdict(flatRoute(graph, flatText), withheld(flatText));
    var structured =
        new Verdict(
            structuredRoute(graph, structuredValue), withheld(String.valueOf(structuredValue)));

    assertThat(structured)
        .as(
            "%s: the structured path must reach exactly the flat path's verdict%n  flat:       %s%n"
                + "  structured: %s",
            graphCase.id(), flatText, structuredValue)
        .isEqualTo(flat);
  }

  /**
   * Whether the deny-list withheld anything — the second half of the shared decision, and the half
   * that catches a split the routing witness cannot see.
   *
   * <p>INTENT: The routing witness answers {@code NOT_ENUMERATED} for both paths whenever a value
   * is no composite at all, which is every scalar — so a value one path READ through its own {@code
   * toString()} while the other WALKED its fields looked like agreement. It was not: a user {@code
   * Number} subclass carrying a deny-listed field printed the field whole on the flat path and the
   * marker on the structured one, one value and two answers, and the leaking one was the channel
   * every human-readable output uses. Whether a member was hidden is a property of the shared
   * decision, never of an encoding, so the two channels must agree on it for every row.
   *
   * <p><b>@llmNote</b> Presence of the marker, not its count or position: the two paths
   * legitimately cap and order differently (a trailing {@code …} against a shorter list), and
   * pinning how many members were withheld would pin those encodings instead of the decision. A
   * hostile fixture could of course PRINT the marker itself from its own text — and a fixture that
   * did would be hiding its secret rather than leaking it, which the containment oracle covers from
   * the other side.
   */
  private static boolean withheld(String rendered) {
    return rendered.contains(RedactionPolicy.MARKER);
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
