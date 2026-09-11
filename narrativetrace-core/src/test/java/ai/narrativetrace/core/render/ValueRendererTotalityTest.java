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

  /** A map that will not produce its entries. */
  private static final class HostileMap extends AbstractMap<String, String> {
    @Override
    public Set<Entry<String, String>> entrySet() {
      throw new IllegalStateException("map entrySet crashed");
    }
  }

  /** A collection that iterates fine and then refuses to say how big it is. */
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

  /** A collection that will not iterate at all. */
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

  /** A collection that yields two items and then throws mid-iteration. */
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

  /** A map whose entry set holds one readable entry and one that will not give up its key. */
  private static final class HalfReadableMap extends AbstractMap<String, String> {
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

  @Test
  void aNumberWhoseToStringThrowsRendersItsTypeInsteadOfEscaping() {
    assertThat(renderer.render(new HostileNumber())).isEqualTo("<HostileNumber>");
  }

  /**
   * A {@code Number} that answers no conversion is not a structured scalar, so the walk reaches it
   * — and it has no state, so it keeps its own text, which throws. The typed marker is what is
   * left.
   */
  @Test
  void aNumberThatRefusesEveryConversionRendersTheTypedMarkerStructurally() {
    assertThat(renderer.renderStructured(new HostileNumber()))
        .isEqualTo(new RenderedValue.StringVal("<error: IllegalStateException>"));
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

  @Test
  void aRecordAccessorThrowingAnErrorMarksOnlyThatComponent() {
    assertThat(renderer.render(new HostileRecord("ignored")))
        .isEqualTo("HostileRecord(id: <error: AssertionError>)");
    assertThat(renderer.renderStructured(new HostileRecord("ignored")))
        .isEqualTo(
            new RenderedValue.ObjectVal(
                "HostileRecord",
                Map.of("id", new RenderedValue.StringVal("<error: AssertionError>"))));
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
   * A user subclass of a platform temporal is NOT a platform type, so its own {@code toString()} is
   * not trusted — and its state is inherited, so the walk has nothing it is allowed to print. The
   * empty brace pair is the honest answer: no leak, and no pretence that anything was read. The
   * structured path scalar-handles {@code Date} before the walk, so it still answers the type
   * marker; the two paths diverge here because the value IS two different things to them.
   */
  @Test
  void aTemporalSubclassThatAnswersNothingRendersNoStateRatherThanItsOwnString() {
    assertThat(renderer.render(new HostileDate())).isEqualTo("HostileDate{}");
    assertThat(renderer.renderStructured(new HostileDate()))
        .isEqualTo(new RenderedValue.StringVal("<HostileDate>"));
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
