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

import ai.narrativetrace.api.annotation.NarrativeElements;
import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.event.RenderedValue;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The renderer is total: no value a user can hand it makes it throw, and one unrenderable part of a
 * value does not discard the rest.
 *
 * <p>INTENT: This class encodes the five FAIL lines of the 2026-09-01 pre-publish bug hunt plus the
 * nested cases it asked for. Rendering runs on the application thread inside proxy and agent entry
 * and exit, so anything escaping here reaches host code — including {@link Error} subclasses, which
 * a user {@code toString()} or {@code iterator()} raises as readily as an exception.
 */
class ValueRendererTotalityTest {

  private final ValueRenderer renderer = new ValueRenderer();

  // ---------------------------------------------------------------- hostile fixtures

  /** A Number whose every conversion and whose toString() throw. */
  private static final class HostileNumber extends Number {
    @Override
    public int intValue() {
      throw new IllegalStateException("number intValue crashed");
    }

    @Override
    public long longValue() {
      throw new IllegalStateException("number longValue crashed");
    }

    @Override
    public float floatValue() {
      throw new IllegalStateException("number floatValue crashed");
    }

    @Override
    public double doubleValue() {
      throw new IllegalStateException("number doubleValue crashed");
    }

    @Override
    public String toString() {
      throw new IllegalStateException("number toString crashed");
    }
  }

  /** An enum constant whose toString() throws an Error. */
  private enum HostileEnum {
    VALUE;

    @Override
    public String toString() {
      throw new AssertionError("enum toString assertion");
    }
  }

  /** A future that will not say whether it is done. */
  private static final class HostileFuture implements Future<String> {
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      throw new IllegalStateException("future isCancelled crashed");
    }

    @Override
    public boolean isDone() {
      throw new IllegalStateException("future isDone crashed");
    }

    @Override
    public String get() {
      throw new IllegalStateException("future get crashed");
    }

    @Override
    public String get(long timeout, TimeUnit unit) {
      throw new IllegalStateException("future get crashed");
    }
  }

  /**
   * A map that will not produce its entries.
   *
   * <p><b>@llmNote</b> Re-expressed 2026-09-18, same ruling and same reason as {@link
   * HalfReadableMap}: {@code AbstractMap} owns no entries, so the only walk that ever asks this
   * type for its entries is the declared-safe hook. The assertion is untouched — what the test pins
   * is the totality of that walk, not which door it came through.
   */
  @NarrativeElements
  private static final class HostileMap extends AbstractMap<String, String> {
    @Override
    public Set<Entry<String, String>> entrySet() {
      throw new IllegalStateException("map entrySet crashed");
    }
  }

  /**
   * A collection that iterates fine and then refuses to say how big it is.
   *
   * <p><b>@llmNote</b> Re-expressed 2026-09-18 (owner ruling: a user subclass of an ABSTRACT
   * platform base is object-introspected, its override NEVER called): {@code AbstractCollection}
   * has no platform-owned state, so this shape's totality intent — an otherwise-readable
   * enumeration whose size answer alone is hostile — only survives under the third sanctioned hook.
   * The un-annotated twin, {@link UndeclaredSizelessCollection} below, asserts the opposite: plain
   * object introspection, never the override.
   */
  @NarrativeElements
  private static final class SizelessCollection extends AbstractCollection<String> {
    @Override
    public Iterator<String> iterator() {
      return List.of("a", "b").iterator();
    }

    @Override
    public int size() {
      throw new AssertionError("collection size assertion");
    }
  }

  /** Same shape as {@link SizelessCollection}, without the declared-safe hook. */
  @SuppressWarnings(
      "PMD.UnusedPrivateField") // label exists only to be read by object introspection
  private static final class UndeclaredSizelessCollection extends AbstractCollection<String> {
    private final String label = "sizeless";

    @Override
    public Iterator<String> iterator() {
      return List.of("a", "b").iterator();
    }

    @Override
    public int size() {
      throw new AssertionError("collection size assertion");
    }
  }

  /**
   * A collection that will not iterate at all.
   *
   * <p><b>@llmNote</b> Re-expressed 2026-09-18, same ruling and same reason as {@link
   * SizelessCollection}: an {@code AbstractCollection} subclass is only ever enumerated through the
   * declared-safe hook, so that is where this shape's totality intent lives. Assertions untouched.
   */
  @NarrativeElements
  private static final class UniterableCollection extends AbstractCollection<String> {
    @Override
    public Iterator<String> iterator() {
      throw new IllegalStateException("iterator crashed");
    }

    @Override
    public int size() {
      throw new IllegalStateException("size crashed");
    }
  }

  /**
   * A collection that yields two items and then throws mid-iteration.
   *
   * <p><b>@llmNote</b> Re-expressed 2026-09-18, same ruling as {@link SizelessCollection}. This is
   * the shape that proves the declared-safe hook is totality-guarded like every other element walk:
   * a hook that dies half way keeps what it already yielded. Assertions untouched.
   */
  @NarrativeElements
  private static final class HalfIterableCollection extends AbstractCollection<String> {
    @Override
    public Iterator<String> iterator() {
      return new Iterator<>() {
        private int served;

        @Override
        public boolean hasNext() {
          return true;
        }

        @Override
        public String next() {
          if (served >= 2) {
            throw new IllegalStateException("iterator crashed mid-way");
          }
          served = served + 1;
          return "item" + served;
        }
      };
    }

    @Override
    public int size() {
      return 5;
    }
  }

  /** A value whose {@code @NarrativeSummary} method throws an Error. */
  public static final class HostileSummary {
    private final String id;

    HostileSummary(String id) {
      this.id = id;
    }

    String id() {
      return id;
    }

    @NarrativeSummary
    public String summary() {
      throw new AssertionError("summary assertion");
    }
  }

  /** A record whose accessor throws an Error. */
  public record HostileRecord(String id) {
    @Override
    public String id() {
      throw new AssertionError("accessor assertion");
    }
  }

  /** A standalone map entry whose key cannot be read. */
  private static final class HostileEntry implements Map.Entry<String, String> {
    @Override
    public String getKey() {
      throw new AssertionError("getKey assertion");
    }

    @Override
    public String getValue() {
      return "v";
    }

    @Override
    public String setValue(String value) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * A map whose entry set holds one readable entry and one that will not give up its key.
   *
   * <p><b>@llmNote</b> Re-expressed 2026-09-18, same ruling as {@link SizelessCollection}: {@code
   * AbstractMap} has no platform-owned state either, so this totality intent — a hostile entry
   * inside an otherwise-readable enumeration — moves under {@code @NarrativeElements}. The
   * un-annotated twin, {@link UndeclaredHalfReadableMap} below, asserts plain object introspection.
   */
  @NarrativeElements
  private static final class HalfReadableMap extends AbstractMap<String, String> {
    @Override
    public Set<Entry<String, String>> entrySet() {
      var entries = new java.util.LinkedHashSet<Entry<String, String>>();
      entries.add(new java.util.AbstractMap.SimpleEntry<>("good", "value"));
      entries.add(new HostileEntry());
      return entries;
    }
  }

  /** Same shape as {@link HalfReadableMap}, without the declared-safe hook. */
  @SuppressWarnings(
      "PMD.UnusedPrivateField") // label exists only to be read by object introspection
  private static final class UndeclaredHalfReadableMap extends AbstractMap<String, String> {
    private final String label = "half-readable";

    @Override
    public Set<Entry<String, String>> entrySet() {
      var entries = new java.util.LinkedHashSet<Entry<String, String>>();
      entries.add(new java.util.AbstractMap.SimpleEntry<>("good", "value"));
      entries.add(new HostileEntry());
      return entries;
    }
  }

  /** A temporal value that answers neither its epoch millis nor its own name. */
  private static final class HostileDate extends java.util.Date {
    private static final long serialVersionUID = 1L;

    @Override
    public long getTime() {
      throw new AssertionError("getTime assertion");
    }

    @Override
    public String toString() {
      throw new AssertionError("date toString assertion");
    }
  }

  // ---------------------------------------------------------------- the report's five FAIL lines

  /**
   * A {@code Number} subclass is a composite with a numeric base, so neither path asks it for
   * anything — no conversion, no text. Its {@code toString()} cannot fail a render it is never
   * entered by, and the honest answer is the object it is, named, with no readable field of its
   * own.
   */
  @Test
  void aNumberWhoseToStringThrowsIsWalkedSoItIsNeverEntered() {
    assertThat(renderer.render(new HostileNumber())).isEqualTo("HostileNumber{}");
  }

  /** The same value and the same answer on the other path: one decision, so they cannot drift. */
  @Test
  void aNumberThatRefusesEveryConversionRendersAsItsTypeStructurally() {
    assertThat(renderer.renderStructured(new HostileNumber()))
        .isEqualTo(new RenderedValue.ObjectVal("HostileNumber", java.util.Map.of()));
  }

  @Test
  void anEnumWhoseToStringThrowsAnErrorRendersItsTypeInsteadOfEscaping() {
    assertThat(renderer.render(HostileEnum.VALUE)).isEqualTo("<HostileEnum>");
    assertThat(renderer.renderStructured(HostileEnum.VALUE))
        .isEqualTo(new RenderedValue.StringVal("<HostileEnum>"));
  }

  @Test
  void aFutureThatWillNotSayWhetherItIsDoneRendersAsFailed() {
    assertThat(renderer.render(new HostileFuture())).isEqualTo("<failed>");
    assertThat(renderer.renderStructured(new HostileFuture()))
        .isEqualTo(new RenderedValue.StringVal("<failed>"));
  }

  @Test
  void aMapThatWillNotProduceItsEntriesRendersTheFailureMarkerAlone() {
    assertThat(renderer.render(new HostileMap())).isEqualTo("{<error: IllegalStateException>}");
    assertThat(renderer.renderStructured(new HostileMap()))
        .isEqualTo(
            new RenderedValue.ObjectVal(
                "Map",
                Map.of(
                    "<error: IllegalStateException>",
                    new RenderedValue.StringVal("<error: IllegalStateException>"))));
  }

  @Test
  void aCollectionThatWillNotSayHowBigItIsStillRendersTheItemsItYielded() {
    assertThat(renderer.render(new SizelessCollection())).isEqualTo("[\"a\", \"b\"]");
    assertThat(renderer.renderStructured(new SizelessCollection()))
        .isEqualTo(
            new RenderedValue.ListVal(
                List.of(new RenderedValue.StringVal("a"), new RenderedValue.StringVal("b"))));
  }

  /**
   * Pending (owner ruling 2026-09-18: a user subclass of an ABSTRACT platform base is
   * object-introspected, its override NEVER called): without {@code @NarrativeElements}, {@link
   * UndeclaredSizelessCollection} has no honest ancestor state to fall back on either, so it is
   * rendered like any hand-rolled type — its own declared field, never the elements its {@code
   * iterator()} would yield, and no failure marker (its {@code size()} is never consulted).
   */
  @Test
  void anUndeclaredSizelessCollectionIsObjectIntrospectedRatherThanEnumerated() {
    var rendered = renderer.render(new UndeclaredSizelessCollection());

    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).doesNotContain("\"a\"").doesNotContain("\"b\"");
    assertThat(rendered).contains("UndeclaredSizelessCollection");
    assertThat(rendered).contains("sizeless");
  }

  // ---------------------------------------------------------------- nested-failure cases

  @Test
  void aCollectionThatWillNotIterateRendersTheFailureMarkerAlone() {
    assertThat(renderer.render(new UniterableCollection()))
        .isEqualTo("[<error: IllegalStateException>]");
  }

  @Test
  void anIteratorThatDiesHalfwayKeepsTheItemsItAlreadyYielded() {
    assertThat(renderer.render(new HalfIterableCollection()))
        .isEqualTo("[\"item1\", \"item2\", <error: IllegalStateException>]");
  }

  /**
   * A platform-defined collection can be hostile too, without any user type in the dispatch: {@code
   * AbstractMap}'s own {@code keySet()} view is declared by the JDK, so origin says "enumerate" —
   * and every question it forwards to the map underneath, its size and its iterator alike, refuses.
   *
   * <p>INTENT: the totality of the PLATFORM collection walk, which the hostile user collections
   * above no longer reach: a user subclass of an abstract platform base is object-introspected, so
   * those shapes now enter through the declared-safe hook's own walk instead. Both walks must stay
   * total, and this is the one that no user type can be substituted into.
   */
  @Test
  void aPlatformCollectionViewOverAMapThatAnswersNothingRendersTheFailureMarkerAlone() {
    assertThat(renderer.render(new HostileMap().keySet()))
        .isEqualTo("[<error: IllegalStateException>]");
  }

  @Test
  void oneUnrenderableElementCostsOnlyItsOwnSlot() {
    var list = List.of("first", new UniterableCollection(), "last");

    assertThat(renderer.render(list))
        .isEqualTo("[\"first\", [<error: IllegalStateException>], \"last\"]");
  }

  @Test
  void oneUnrenderableArrayElementCostsOnlyItsOwnSlot() {
    var array = new Object[] {"first", new UniterableCollection(), "last"};

    assertThat(renderer.render(array))
        .isEqualTo("[\"first\", [<error: IllegalStateException>], \"last\"]");
  }

  @Test
  void oneUnrenderableMapEntryCostsOnlyItsOwnSlot() {
    var map = new java.util.LinkedHashMap<String, Object>();
    map.put("good", "value");
    map.put("bad", new HostileMap());

    assertThat(renderer.render(map))
        .isEqualTo("{good=\"value\", bad={<error: IllegalStateException>}}");
  }

  /**
   * Falling back to ordinary rendering hid the failure — the reader could not tell a broken summary
   * apart from a class that never declared one. The failed part names the raised type instead, and
   * never its message: {@code "summary assertion"} is application text that in the field routinely
   * interpolates the value that would not format.
   */
  @Test
  void aSummaryMethodThrowingAnErrorRendersTheTypedMarker() {
    assertThat(renderer.render(new HostileSummary("S-1")))
        .isEqualTo("<error: AssertionError>")
        .doesNotContain("summary");
    assertThat(renderer.renderStructured(new HostileSummary("S-1")))
        .isEqualTo(new RenderedValue.StringVal("<error: AssertionError>"));
  }

  /**
   * Updated 2026-09-17 for the rendering rule: a record component is read from its backing field,
   * never through its accessor, so an accessor throwing an {@link Error} is never invoked and never
   * seen. See {@link RenderingReadsStateNeverRunsBehaviourTest}.
   */
  @Test
  void aRecordAccessorThrowingAnErrorStillRendersFromTheBackingField() {
    assertThat(renderer.render(new HostileRecord("ignored")))
        .isEqualTo("HostileRecord(id: \"ignored\")");
    assertThat(renderer.renderStructured(new HostileRecord("ignored")))
        .isEqualTo(
            new RenderedValue.ObjectVal(
                "HostileRecord", Map.of("id", new RenderedValue.StringVal("ignored"))));
  }

  @Test
  void aValueThatDefeatsEveryLocalFallbackStillRendersItsTypeAtTheBoundary() {
    assertThat(renderer.render(new HostileEntry())).isEqualTo("<HostileEntry>");
    assertThat(renderer.renderStructured(new HostileEntry()))
        .isEqualTo(new RenderedValue.StringVal("<HostileEntry>"));
  }

  @Test
  void aListElementThatCannotBeReadAtAllLosesOnlyItsOwnSlot() {
    var list = List.of("a", new HostileEntry(), "b");

    assertThat(renderer.render(list)).isEqualTo("[\"a\", <error: AssertionError>, \"b\"]");
    assertThat(renderer.renderStructured(list))
        .isEqualTo(
            new RenderedValue.ListVal(
                List.of(
                    new RenderedValue.StringVal("a"),
                    new RenderedValue.StringVal("<error: AssertionError>"),
                    new RenderedValue.StringVal("b"))));
  }

  @Test
  void anArrayElementThatCannotBeReadAtAllLosesOnlyItsOwnSlot() {
    var array = new Object[] {"a", new HostileEntry(), "b"};

    assertThat(renderer.render(array)).isEqualTo("[\"a\", <error: AssertionError>, \"b\"]");
    assertThat(renderer.renderStructured(array))
        .isEqualTo(
            new RenderedValue.ListVal(
                List.of(
                    new RenderedValue.StringVal("a"),
                    new RenderedValue.StringVal("<error: AssertionError>"),
                    new RenderedValue.StringVal("b"))));
  }

  @Test
  void aMapEntryThatWillNotGiveUpItsKeyLosesOnlyThatEntry() {
    assertThat(renderer.render(new HalfReadableMap()))
        .isEqualTo("{good=\"value\", <error: AssertionError>}");
    assertThat(renderer.renderStructured(new HalfReadableMap()))
        .isEqualTo(
            new RenderedValue.ObjectVal(
                "Map",
                Map.of(
                    "good",
                    new RenderedValue.StringVal("value"),
                    "<error: AssertionError>",
                    new RenderedValue.StringVal("<error: AssertionError>"))));
  }

  /**
   * Pending, same ruling as {@link
   * #anUndeclaredSizelessCollectionIsObjectIntrospectedRatherThanEnumerated}: without
   * {@code @NarrativeElements}, {@link UndeclaredHalfReadableMap} is object-introspected, its
   * {@code entrySet()} override never called, so neither the readable entry nor the hostile one
   * ever appears, and there is no failure marker.
   */
  @Test
  void anUndeclaredHalfReadableMapIsObjectIntrospectedRatherThanPartiallyEnumerated() {
    var rendered = renderer.render(new UndeclaredHalfReadableMap());

    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).doesNotContain("good=").doesNotContain("value");
    assertThat(rendered).contains("UndeclaredHalfReadableMap");
    assertThat(rendered).contains("half-readable");
  }

  /**
   * A user subclass of a platform temporal is NOT a platform type, so its own {@code toString()} is
   * not trusted — and its state is inherited, so the walk has nothing it is allowed to print. The
   * empty brace pair is the honest answer: no leak, and no pretence that anything was read.
   *
   * <p><b>@llmNote</b> Both paths now, and the divergence this test used to document is what
   * closed: the structured typed-temporal form used to scalar-handle any {@code Date} before the
   * walk, which meant reading {@code getTime()} — an application override on a subclass, the very
   * thing the rule forbids — and answering a typed attribute from whatever it returned, while the
   * flat path walked the same value's fields. The typed forms are keyed on origin now, so a user
   * subclass of a platform numeric or temporal type is the composite it is on both channels.
   */
  @Test
  void aTemporalSubclassThatAnswersNothingRendersNoStateRatherThanItsOwnString() {
    assertThat(renderer.render(new HostileDate())).isEqualTo("HostileDate{}");
    assertThat(renderer.renderStructured(new HostileDate()))
        .isEqualTo(new RenderedValue.ObjectVal("HostileDate", java.util.Map.of()));
  }

  @Test
  void nothingAUserCanHandTheRendererEscapesEitherEntryPoint() {
    var hostile =
        List.of(
            new HostileNumber(),
            HostileEnum.VALUE,
            new HostileFuture(),
            new HostileMap(),
            new SizelessCollection(),
            new UniterableCollection(),
            new HostileSummary("S-1"),
            new HostileRecord("x"),
            new HostileEntry(),
            new HalfReadableMap(),
            new HostileDate());

    for (var value : hostile) {
      assertThatCode(() -> renderer.render(value)).doesNotThrowAnyException();
      assertThatCode(() -> renderer.renderStructured(value)).doesNotThrowAnyException();
    }
  }
}
