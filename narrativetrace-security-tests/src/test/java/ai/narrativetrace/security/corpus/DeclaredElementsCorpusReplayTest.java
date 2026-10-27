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
import org.junit.jupiter.api.Test;

/**
 * Corpus replay for the two rows that pin the origin decision's element side on BOTH rendering
 * paths: {@code declared-elements} (the third sanctioned hook) and {@code
 * structured-path-user-subclass-ancestor-state} (a user subclass of a platform list and of a
 * platform map, read through the ancestor's own state).
 *
 * <p>INTENT: {@link RenderingPathRoutingParityTest} replays every row and asserts the coarse
 * verdict, which for these two rows is "elements" on both paths. It cannot say that the SAME
 * elements, under the SAME cap, were shown, nor that the fixture's own members did or did not run.
 * Those are the properties the rows exist for, so they are asserted here, flat and structured side
 * by side.
 *
 * <p><b>@llmNote</b> The two paths encode a cap differently on purpose (a trailing {@code …} in
 * text against a shorter list in the tree), so the witness is the set of element markers present,
 * not the shape of the rendering.
 */
class DeclaredElementsCorpusReplayTest {

  private static final String DECLARED_ROW = "declared-elements";
  private static final String SUBCLASS_ROW = "structured-path-user-subclass-ancestor-state";

  private final ValueRenderer renderer = new ValueRenderer();

  private static GraphCase row(String id) {
    return HostileCorpus.graphs().stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no graphs.json row with id " + id));
  }

  private static Object build(String id, String sentinel) {
    return HostileGraphs.build(row(id), sentinel);
  }

  private static String flatten(RenderedValue value) {
    if (value instanceof RenderedValue.StringVal s) {
      return s.value();
    }
    if (value instanceof RenderedValue.ListVal l) {
      return l.elements().stream()
          .map(DeclaredElementsCorpusReplayTest::flatten)
          .toList()
          .toString();
    }
    if (value instanceof RenderedValue.ObjectVal o) {
      return o.typeName() + flattenFields(o.fields());
    }
    return String.valueOf(value);
  }

  private static String flattenFields(Map<String, RenderedValue> fields) {
    var out = new StringBuilder("{");
    fields.forEach((k, v) -> out.append(k).append('=').append(flatten(v)).append(", "));
    return out.append('}').toString();
  }

  @Test
  void declaredElementsShowTheSameElementsUnderTheSameCapOnBothPaths() {
    var sentinel = "sentinel-declared-flat";
    var flat = renderer.render(build(DECLARED_ROW, sentinel));
    var structured =
        flatten(renderer.renderStructured(build(DECLARED_ROW, "sentinel-declared-structured")));

    for (var text : new String[] {flat, structured}) {
      assertThat(text).contains("elem-1", "elem-4", "entry-key-0", "entry-key-4");
      assertThat(text).doesNotContain("elem-5", "elem-6", "entry-key-5", "entry-key-6");
      assertThat(text).doesNotContain("sentinel-declared");
    }
  }

  @Test
  void aRaisingDeclaredHookRendersTheTypedErrorMarkerOnBothPaths() {
    var flat = renderer.render(build(DECLARED_ROW, "sentinel-declared-raise-flat"));
    var structured =
        flatten(renderer.renderStructured(build(DECLARED_ROW, "sentinel-declared-raise-struct")));

    assertThat(flat).contains("<error: IllegalStateException>");
    assertThat(structured).contains("<error: IllegalStateException>");
  }

  @Test
  void aUserSubclassOfAPlatformListAndMapShowsAncestorStateOnBothPaths() {
    var flat = renderer.render(build(SUBCLASS_ROW, "sentinel-subclass-flat"));
    var structured =
        flatten(renderer.renderStructured(build(SUBCLASS_ROW, "sentinel-subclass-structured")));

    for (var text : new String[] {flat, structured}) {
      assertThat(text).contains("ancestor-list-element", "ancestor-map-value");
      assertThat(text).doesNotContain("sentinel-subclass").doesNotContain("<error");
    }
  }

  @Test
  void theDeclaredHookRunsOnceUnderTheGuardOnEachPathAndTheSubclassOverridesNeverRun() {
    for (var structured : new boolean[] {false, true}) {
      var declared = HostileGraphs.declaredElements("sentinel-hook-counts");
      render(declared, structured);
      var iterable = (HostileMembers.DeclaredElementsIterable) declared.get(0);
      var map = (HostileMembers.DeclaredElementsMap) declared.get(1);
      assertThat(iterable.iteratorCalls).as("iterable hook, structured=%s", structured).hasValue(1);
      assertThat(iterable.guardActiveDuringIteration).isTrue();
      assertThat(map.entrySetCalls).as("map hook, structured=%s", structured).hasValue(1);
      assertThat(map.guardActiveDuringEntrySet).isTrue();

      var subclasses = HostileGraphs.userSubclassAncestorState("sentinel-override-counts");
      render(subclasses, structured);
      assertThat(((HostileMembers.AncestorStateList) subclasses.get(0)).overrideCalls)
          .as("list override, structured=%s", structured)
          .hasValue(0);
      assertThat(((HostileMembers.AncestorStateMap) subclasses.get(1)).overrideCalls)
          .as("map override, structured=%s", structured)
          .hasValue(0);
    }
  }

  private void render(Object graph, boolean structured) {
    if (structured) {
      renderer.renderStructured(graph);
    } else {
      renderer.render(graph);
    }
  }
}
