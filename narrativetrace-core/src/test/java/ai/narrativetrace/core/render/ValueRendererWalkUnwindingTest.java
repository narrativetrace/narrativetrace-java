/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NarrativeElements;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * {@link RenderWalk}'s unwinding — {@code remove} on the way out of a container, {@code ascend} on
 * the way out of a level — pinned at each of the renderer's own call sites.
 *
 * <p>INTENT: Both are one-line {@code finally} bodies, and PIT's {@code VoidMethodCallMutator}
 * survived on every one of them, so nothing proved they ran. They are what makes the ancestor set
 * path-scoped rather than render-scoped: drop {@code remove} and the second sibling that happens to
 * be the same instance is reported as a cycle it is not; drop {@code ascend} and the depth cap
 * becomes a per-render budget, so a wide list runs out of depth part-way through.
 *
 * <p><b>@llmNote</b> Each test asserts the shared value's own content appears <em>twice</em> rather
 * than asserting the absence of an identity marker: the marker's text is a format detail, the count
 * is the behaviour. One occurrence means the second visit was refused.
 */
class ValueRendererWalkUnwindingTest {

  private static final String MARK = "the-shared-mark";

  private final ValueRenderer renderer = new ValueRenderer();

  @Test
  void aCollectionVisitedTwiceInOneRenderIsRenderedTwice() {
    var shared = List.of(MARK);

    assertThat(occurrences(renderer.render(List.of(shared, shared)))).isEqualTo(2);
  }

  @Test
  void aMapEntryVisitedTwiceInOneRenderIsRenderedTwice() {
    var shared = new AbstractMap.SimpleImmutableEntry<>("k", MARK);

    assertThat(occurrences(renderer.render(List.of(shared, shared)))).isEqualTo(2);
  }

  @Test
  void anAtomicReferenceVisitedTwiceInOneRenderIsRenderedTwice() {
    var shared = new AtomicReference<>(MARK);

    assertThat(occurrences(renderer.render(List.of(shared, shared)))).isEqualTo(2);
  }

  @Test
  void anArrayVisitedTwiceInOneRenderIsRenderedTwice() {
    var shared = new String[] {MARK};

    assertThat(occurrences(renderer.render(new Object[][] {shared, shared}))).isEqualTo(2);
  }

  @Test
  void aHashMapSubclassVisitedTwiceInOneRenderIsRenderedTwice() {
    var shared = new Registry();
    shared.put("k", MARK);

    assertThat(occurrences(renderer.render(List.of(shared, shared)))).isEqualTo(2);
  }

  @Test
  void anArrayListSubclassVisitedTwiceInOneRenderIsRenderedTwice() {
    var shared = new Bag();
    shared.add(MARK);

    assertThat(occurrences(renderer.render(List.of(shared, shared)))).isEqualTo(2);
  }

  @Test
  void aDeclaredSafeIterableVisitedTwiceInOneRenderIsRenderedTwice() {
    var shared = new Declared();

    assertThat(occurrences(renderer.render(List.of(shared, shared)))).isEqualTo(2);
  }

  @Test
  void aWideListOfShallowSiblingsDoesNotRunOutOfDepthPartWayThrough() {
    var wide = new ArrayList<Object>();
    for (var i = 0; i < RenderWalk.MAX_DEPTH + 8; i++) {
      wide.add(new Leaf("item-" + i));
    }

    assertThat(new ValueRenderer(200, wide.size(), wide.size()).render(wide))
        .as("the depth cap is per path, so no sibling may be refused for the depth of its peers")
        .doesNotContain("<max-depth>");
  }

  private static int occurrences(String rendered) {
    var count = 0;
    var from = rendered.indexOf(MARK);
    while (from >= 0) {
      count++;
      from = rendered.indexOf(MARK, from + MARK.length());
    }
    return count;
  }

  /** One field, so a list of these is width and nothing else. */
  record Leaf(String name) {}

  /** A user subclass of a platform map: enumerated through the ancestor's own state. */
  static final class Registry extends HashMap<String, Object> {
    private static final long serialVersionUID = 1L;
  }

  /** A user subclass of a platform list: the same, through the list ancestor's state. */
  static final class Bag extends ArrayList<Object> {
    private static final long serialVersionUID = 1L;
  }

  /** The third sanctioned hook: elements the type declares safe to enumerate. */
  @NarrativeElements
  static final class Declared implements Iterable<Object> {
    @Override
    public Iterator<Object> iterator() {
      return List.<Object>of(MARK).iterator();
    }
  }
}
