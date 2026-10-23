/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

/**
 * The element cap on the two ancestor-state walks, at the cap and one past it, on both rendering
 * paths.
 *
 * <p>INTENT: A user subclass of {@code ArrayList} or {@code HashMap} is enumerated through the
 * platform ancestor's own state, which gives those two shapes their own cap arithmetic — {@code
 * renderHashMapEntries}' {@code map.size() > maxCollectionItems} and {@code ancestorEntries}'
 * {@code entries.size() < maxCollectionItems}, neither shared with the platform-collection walk.
 * PIT's {@code ConditionalsBoundaryMutator} survived on the first, so nothing distinguished
 * "exactly five entries" from "five of six": a cap that announces truncation when it did not
 * truncate tells a reader the graph continued when it did not.
 *
 * <p><b>@llmNote</b> The assertions count rendered values rather than naming them. {@code HashMap}
 * hands its five of six out in table order, which is not an order a test may depend on; how many
 * came out is the behaviour, which five is not.
 */
class ValueRendererAncestorStateCapTest {

  private static final int CAP = 5;

  private static final String VALUE_PREFIX = "val-";

  private final ValueRenderer renderer = new ValueRenderer();

  @Test
  void aHashMapSubclassAtTheCapIsRenderedWholeWithNoTruncationMarker() {
    var rendered = renderer.render(registryOf(CAP));

    assertThat(valuesIn(rendered)).isEqualTo(CAP);
    assertThat(rendered).doesNotContain("…").doesNotContain("...");
  }

  @Test
  void aHashMapSubclassOnePastTheCapRendersTheCapAndSaysItTruncated() {
    var rendered = renderer.render(registryOf(CAP + 1));

    assertThat(valuesIn(rendered)).isEqualTo(CAP);
    assertThat(rendered).containsAnyOf("…", "...");
  }

  @Test
  void anArrayListSubclassAtTheCapIsRenderedWholeWithNoTruncationMarker() {
    var rendered = renderer.render(bagOf(CAP));

    assertThat(valuesIn(rendered)).isEqualTo(CAP);
    assertThat(rendered).doesNotContain("…").doesNotContain("...");
  }

  @Test
  void anArrayListSubclassOnePastTheCapRendersTheCapAndSaysItTruncated() {
    var rendered = renderer.render(bagOf(CAP + 1));

    assertThat(valuesIn(rendered)).isEqualTo(CAP);
    assertThat(rendered).containsAnyOf("…", "...");
  }

  @Test
  void theStructuredPathCapsTheSameHashMapSubclassAtTheSameCount() {
    assertThat(structuredValuesIn(renderer.renderStructured(registryOf(CAP)))).isEqualTo(CAP);
    assertThat(structuredValuesIn(renderer.renderStructured(registryOf(CAP + 1)))).isEqualTo(CAP);
  }

  @Test
  void theStructuredPathCapsTheSameArrayListSubclassAtTheSameCount() {
    assertThat(structuredValuesIn(renderer.renderStructured(bagOf(CAP)))).isEqualTo(CAP);
    assertThat(structuredValuesIn(renderer.renderStructured(bagOf(CAP + 1)))).isEqualTo(CAP);
  }

  private static Registry registryOf(int entries) {
    var registry = new Registry();
    for (var i = 0; i < entries; i++) {
      registry.put("k" + i, VALUE_PREFIX + i);
    }
    return registry;
  }

  private static Bag bagOf(int elements) {
    var bag = new Bag();
    for (var i = 0; i < elements; i++) {
      bag.add(VALUE_PREFIX + i);
    }
    return bag;
  }

  private static int valuesIn(String rendered) {
    var count = 0;
    var from = rendered.indexOf(VALUE_PREFIX);
    while (from >= 0) {
      count++;
      from = rendered.indexOf(VALUE_PREFIX, from + VALUE_PREFIX.length());
    }
    return count;
  }

  private static int structuredValuesIn(RenderedValue value) {
    return valuesIn(value.toString());
  }

  /** A user subclass of a platform map, overriding nothing: the honest ancestor read applies. */
  static final class Registry extends HashMap<String, Object> {
    private static final long serialVersionUID = 1L;
  }

  /** The same for a platform list. */
  static final class Bag extends ArrayList<Object> {
    private static final long serialVersionUID = 1L;
  }
}
