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
import ai.narrativetrace.api.event.RenderedValue.ListVal;
import ai.narrativetrace.api.event.RenderedValue.ObjectVal;
import ai.narrativetrace.api.event.RenderedValue.StringVal;
import java.time.LocalDate;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The STRUCTURED twin of {@link RenderingReadsStateNeverRunsBehaviourTest}: every bullet the flat
 * suite pins, asserted through {@link ValueRenderer#renderStructured(Object)} too — with the same
 * counters-stay-zero / no-failure-marker expectations — plus the two gaps the abstract-base
 * refinement left open.
 *
 * <p>INTENT: Gap 1 — {@code renderStructuredComplex} enumerates any {@code Collection}/{@code Map}
 * unconditionally, origin-blind, where the flat path already dispatches by origin. Gap 2 — a
 * composite must never render through its own {@code toString()} on EITHER path (the "toString
 * door": a fieldless {@code AbstractCollection}/{@code AbstractMap} subclass still reaches its
 * overridden {@code iterator()}/{@code entrySet()} through the inherited {@code toString()} via
 * {@code rendersItsOwnString}, which both paths share).
 *
 * <p><b>@llmNote</b> Reuses {@link RenderingReadsStateNeverRunsBehaviourTest}'s fixture classes
 * (package-private, so visible here in the same package) rather than duplicating them — a fixture
 * shape asserted on the flat path and the structured path must be the SAME shape, or the twin
 * proves nothing about parity. {@link #bothPaths()} parametrizes the cases whose assertion is
 * identical in shape on both paths (a counter stays zero; the flattened text contains or excludes
 * some substring); the {@code @NarrativeElements} cap/guard cases stay dedicated per-path tests
 * because the structured cap is a {@code ListVal} size, not a "…" marker in text — the two paths do
 * not encode truncation the same way, so a shared body would either duplicate that difference or
 * hide it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RenderingReadsStateNeverRunsBehaviourStructuredTest {

  private final ValueRenderer renderer = new ValueRenderer();

  /** The flat renderer, and the structured renderer flattened to text for containment checks. */
  private Stream<Arguments> bothPaths() {
    Function<Object, String> flat = renderer::render;
    Function<Object, String> structured = v -> flatten(renderer.renderStructured(v));
    return Stream.of(
        Arguments.of(Named.of("flat", flat)), Arguments.of(Named.of("structured", structured)));
  }

  /** Turns a {@link RenderedValue} tree into searchable text, mirroring the flat form's shape. */
  private static String flatten(RenderedValue value) {
    if (value instanceof StringVal s) {
      return s.value();
    }
    if (value instanceof ObjectVal o) {
      return o.typeName() + flattenFields(o.fields());
    }
    if (value instanceof ListVal l) {
      return l.elements().stream()
          .map(RenderingReadsStateNeverRunsBehaviourStructuredTest::flatten)
          .collect(Collectors.joining(", ", "[", "]"));
    }
    return String.valueOf(value);
  }

  private static String flattenFields(Map<String, RenderedValue> fields) {
    return fields.entrySet().stream()
        .map(e -> e.getKey() + "=" + flatten(e.getValue()))
        .collect(Collectors.joining(", ", "{", "}"));
  }

  // ------------------------------------------------------------------------------------- bullet 2

  /** Structured twin: {@link RenderingReadsStateNeverRunsBehaviourTest.ThrowingAccessorRecord}. */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void recordAccessorThatThrowsStillRendersFromTheBackingFieldWithNoFailureMarker(
      Function<Object, String> renderAsText) {
    var record = new RenderingReadsStateNeverRunsBehaviourTest.ThrowingAccessorRecord("real-value");

    var rendered = renderAsText.apply(record);

    assertThat(rendered).contains("real-value");
    assertThat(rendered).doesNotContain("<error:");
  }

  // ------------------------------------------------------------------------------------- bullet 4

  /**
   * Structured twin: {@link RenderingReadsStateNeverRunsBehaviourTest.PojoWithSideEffectingGetter}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aJavaBeanGetterWithASideEffectIsNeverInvokedByRendering(
      Function<Object, String> renderAsText) {
    var pojo = new RenderingReadsStateNeverRunsBehaviourTest.PojoWithSideEffectingGetter();

    var rendered = renderAsText.apply(pojo);

    assertThat(pojo.getterCalls).as("the getter must never run").isZero();
    assertThat(rendered).contains("Alice");
  }

  // ------------------------------------------------------------------------------------ bullet 5a

  /**
   * Structured twin: {@link RenderingReadsStateNeverRunsBehaviourTest.SideEffectingIteratorList}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aPlatformCollectionSubclassWithASideEffectingIteratorStillRendersItsElements(
      Function<Object, String> renderAsText) {
    var list = new RenderingReadsStateNeverRunsBehaviourTest.SideEffectingIteratorList();
    list.add("alpha");
    list.add("beta");

    var rendered = renderAsText.apply(list);

    assertThat(list.iteratorCalls).as("the overridden iterator() must never run").hasValue(0);
    assertThat(rendered).contains("alpha").contains("beta");
  }

  // ------------------------------------------------------------------------------------ bullet 5b

  /**
   * Structured twin: {@link RenderingReadsStateNeverRunsBehaviourTest.SideEffectingEntrySetMap}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aPlatformMapSubclassWithASideEffectingEntrySetStillRendersItsEntries(
      Function<Object, String> renderAsText) {
    var map = new RenderingReadsStateNeverRunsBehaviourTest.SideEffectingEntrySetMap();
    map.put("zulu-key", "zulu-value");

    var rendered = renderAsText.apply(map);

    assertThat(map.entrySetCalls).as("the overridden entrySet() must never run").hasValue(0);
    assertThat(rendered).contains("zulu-key").contains("zulu-value");
  }

  // ------------------------------------------------------------------------------------- bullet 6

  /** Structured twin: {@link RenderingReadsStateNeverRunsBehaviourTest.HandRolledList}. */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aHandRolledListDelegatingToAPrivateFieldRendersThatFieldAsTheListWithoutItsOwnIterator(
      Function<Object, String> renderAsText) {
    var list = new RenderingReadsStateNeverRunsBehaviourTest.HandRolledList();
    list.seed("alpha", "beta");

    var rendered = renderAsText.apply(list);

    assertThat(list.iteratorCalls).as("the hand-rolled iterator() must never run").hasValue(0);
    assertThat(rendered).contains("HandRolledList");
    assertThat(rendered).contains("alpha").contains("beta");
  }

  // ------------------------------------------------------------------------------------- bullet 7

  /** Structured twin: {@link RenderingReadsStateNeverRunsBehaviourTest.UserIterable}. */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aUserIterableThatIsNotAPlatformCollectionRendersATypeMarkerWithNoElements(
      Function<Object, String> renderAsText) {
    var iterable = new RenderingReadsStateNeverRunsBehaviourTest.UserIterable();

    renderAsText.apply(iterable);

    assertThat(iterable.iteratorCalls).as("iterator() must never run").hasValue(0);
  }

  // ------------------------------------------------------------------------------------ bullet 11

  /**
   * Structured twin: {@link
   * RenderingReadsStateNeverRunsBehaviourTest.CountingThrowingAbstractMapSubclass}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aSubclassOfAnAbstractMapPlatformBaseIsObjectIntrospectedNeverCallingItsOverride(
      Function<Object, String> renderAsText) {
    var value = new RenderingReadsStateNeverRunsBehaviourTest.CountingThrowingAbstractMapSubclass();

    var rendered = renderAsText.apply(value);

    assertThat(value.entrySetCalls).as("entrySet() must never run").hasValue(0);
    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).contains("CountingThrowingAbstractMapSubclass");
    assertThat(rendered).contains("abstract-map");
  }

  // ------------------------------------------------------------------------------------ bullet 12

  /**
   * Structured twin: {@link
   * RenderingReadsStateNeverRunsBehaviourTest.CountingThrowingAbstractCollectionSubclass}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aSubclassOfAnAbstractCollectionPlatformBaseIsObjectIntrospectedNeverCallingItsOverride(
      Function<Object, String> renderAsText) {
    var value =
        new RenderingReadsStateNeverRunsBehaviourTest.CountingThrowingAbstractCollectionSubclass();

    var rendered = renderAsText.apply(value);

    assertThat(value.iteratorCalls).as("iterator() must never run").hasValue(0);
    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).contains("CountingThrowingAbstractCollectionSubclass");
    assertThat(rendered).contains("abstract-collection");
  }

  // --------------------------------------------------------------------------- gap 1 (structured)

  /**
   * Gap 1: {@code renderStructuredComplex} enumerates any {@code Collection} unconditionally —
   * origin-blind, unlike the flat path's {@code renderCollectionByOrigin} — so a hand-rolled
   * collection's own {@code iterator()} is called on the structured path even though bullet 6 above
   * already proves the flat path never calls it.
   */
  @Test
  void aHandRolledCollectionIsNeverEnumeratedByTheStructuredPathEither() {
    var list = new RenderingReadsStateNeverRunsBehaviourTest.HandRolledList();
    list.seed("alpha", "beta");

    var rendered = renderer.renderStructured(list);

    assertThat(list.iteratorCalls)
        .as("the structured path must never call the hand-rolled iterator() either")
        .hasValue(0);
    assertThat(flatten(rendered)).contains("HandRolledList").contains("alpha").contains("beta");
  }

  // --------------------------------------------------------------------------- gap 2: toString
  // door

  /**
   * A FIELDLESS user subclass of the ABSTRACT platform base {@code AbstractCollection}. No INSTANCE
   * field anywhere in its hierarchy, so the fieldless rule of the day trusted it to stringify
   * itself — but the text it was trusted for is {@code AbstractCollection}'s OWN inherited {@code
   * toString()}, which walks {@code iterator()} internally, running this override.
   *
   * <p><b>@llmNote</b> The spy counter is deliberately {@code static}: the fieldless precondition
   * this fixture holds is about DECLARED instance fields, which {@code static} members are not — an
   * instance-field spy (the obvious first attempt) would silently defeat it, and the fixture would
   * then test something else without saying so. Every test using it resets the counter first.
   */
  static final class FieldlessAbstractCollectionSubclass extends AbstractCollection<Object> {
    static final AtomicInteger ITERATOR_CALLS = new AtomicInteger();

    @Override
    public Iterator<Object> iterator() {
      ITERATOR_CALLS.incrementAndGet();
      throw new UnsupportedOperationException("iterator() must never be called by rendering");
    }

    @Override
    public int size() {
      return 3;
    }
  }

  /** Same door, the other ABSTRACT platform base: {@code AbstractMap}, also fieldless. */
  static final class FieldlessAbstractMapSubclass extends AbstractMap<String, Object> {
    static final AtomicInteger ENTRY_SET_CALLS = new AtomicInteger();

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      ENTRY_SET_CALLS.incrementAndGet();
      throw new UnsupportedOperationException("entrySet() must never be called by rendering");
    }
  }

  /**
   * Gap 2: the fieldless {@code AbstractCollection} subclass never renders through its inherited
   * {@code toString()} on either path — a composite is never trusted for its own stringification,
   * whatever the reason {@code rendersItsOwnString} thought it could be.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aFieldlessAbstractCollectionSubclassNeverRendersThroughItsInheritedToString(
      Function<Object, String> renderAsText) {
    FieldlessAbstractCollectionSubclass.ITERATOR_CALLS.set(0);
    var value = new FieldlessAbstractCollectionSubclass();

    var rendered = renderAsText.apply(value);

    assertThat(FieldlessAbstractCollectionSubclass.ITERATOR_CALLS)
        .as(
            "the overridden iterator() must never run, even reached through the inherited"
                + " toString()")
        .hasValue(0);
    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).contains("FieldlessAbstractCollectionSubclass");
  }

  /**
   * Same rule, the {@code AbstractMap} sibling — {@code entrySet()} through the same door. Split
   * from the {@code AbstractCollection} test above rather than sharing {@link #bothPaths()}: {@code
   * AbstractMap} itself declares two {@code transient} instance fields of its own ({@code keySet},
   * {@code values}, the {@code entrySet()}/{@code keySet()} view cache), so back when trust turned
   * on "declares no instance field anywhere in the hierarchy" this sibling was already refused for
   * that unrelated reason while the {@code AbstractCollection} one was not — which is why the door
   * was fieldless-{@code AbstractCollection}-specific then. Both are refused outright now (a user
   * subclass is on no leaf list), and the two paths stay split so each guard reads on its own.
   */
  @Test
  void aFieldlessAbstractMapSubclassAlreadyNeverRendersThroughItsInheritedToStringFlat() {
    FieldlessAbstractMapSubclass.ENTRY_SET_CALLS.set(0);
    var value = new FieldlessAbstractMapSubclass();

    var rendered = renderer.render(value);

    assertThat(FieldlessAbstractMapSubclass.ENTRY_SET_CALLS)
        .as("the overridden entrySet() must never run")
        .hasValue(0);
    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).contains("FieldlessAbstractMapSubclass");
  }

  /**
   * Same fixture, the STRUCTURED path — gap 1 (origin-blind {@code Map} enumeration) applies here
   * too.
   */
  @Test
  void aFieldlessAbstractMapSubclassNeverRendersThroughItsInheritedToStringStructured() {
    FieldlessAbstractMapSubclass.ENTRY_SET_CALLS.set(0);
    var value = new FieldlessAbstractMapSubclass();

    var rendered = flatten(renderer.renderStructured(value));

    assertThat(FieldlessAbstractMapSubclass.ENTRY_SET_CALLS)
        .as(
            "the overridden entrySet() must never run, even reached through the inherited"
                + " toString()")
        .hasValue(0);
    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).contains("FieldlessAbstractMapSubclass");
  }

  /**
   * The same rule for a hand-rolled composite (not a {@code Collection}/{@code Map} at all): a
   * class WITH fields and an overridden {@code toString()} that counts its own calls. Complements
   * the fieldless cases above from the other direction — a composite that plainly has state is
   * never trusted for its own text either, on the same one invariant.
   */
  static final class HandRolledCompositeWithCountingToString {
    private final String label = "composite-value"; // NOPMD - read by object introspection
    final AtomicInteger toStringCalls = new AtomicInteger();

    @Override
    public String toString() {
      toStringCalls.incrementAndGet();
      return "HandRolledCompositeWithCountingToString{label=" + label + "}";
    }
  }

  @ParameterizedTest
  @MethodSource("bothPaths")
  void aHandRolledCompositeWithFieldsNeverRendersThroughItsOwnCountingToString(
      Function<Object, String> renderAsText) {
    var value = new HandRolledCompositeWithCountingToString();

    var rendered = renderAsText.apply(value);

    assertThat(value.toStringCalls).as("toString() must never run").hasValue(0);
    assertThat(rendered).contains("composite-value");
  }

  /**
   * What "stateless leaf" means since 2026-09-19: a PLATFORM leaf type, named in an explicit list,
   * and nothing else. A user class with no instance field anywhere used to qualify on emptiness
   * alone — and emptiness is not statelessness: the same class can hold its state in a static
   * identity-keyed side table, a {@code ClassValue} or a {@code ThreadLocal} and print it from its
   * own {@code toString()}, where no field walk would ever see it. So the counterexample cuts the
   * other way now: this class's curated text is never read, on either path, and the hook that
   * remains for a user type is {@code @NarrativeSummary}.
   */
  static final class StatelessLeafWithCuratedToString {
    @Override
    public String toString() {
      return "stateless-leaf-text";
    }
  }

  @ParameterizedTest
  @MethodSource("bothPaths")
  void aUserClassWithNoFieldsNeverRendersThroughItsCuratedToString(
      Function<Object, String> renderAsText) {
    var value = new StatelessLeafWithCuratedToString();

    var rendered = renderAsText.apply(value);

    assertThat(rendered).doesNotContain("stateless-leaf-text");
    assertThat(rendered).contains("StatelessLeafWithCuratedToString");
  }

  /** The counterexample that must stay GREEN: a platform leaf's own text, on both paths. */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aPlatformLeafStillRendersThroughItsOwnText(Function<Object, String> renderAsText) {
    var rendered = renderAsText.apply(LocalDate.of(2026, 9, 19));

    assertThat(rendered).contains("2026-09-19");
  }

  // ------------------------------------------------------------------------------------- bullet 8

  /**
   * Structured twin: {@link RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeIterable}. The
   * structured cap is a {@link ListVal} size rather than a "…" marker in text, so this stays a
   * dedicated test rather than sharing {@link #bothPaths()}'s body.
   */
  @Test
  void aTypeDeclaringTheThirdHookIsEnumeratedCappedAndGuardedStructured() {
    var declared = new RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeIterable();

    var rendered = renderer.renderStructured(declared);

    assertThat(declared.iteratorCalls).hasValue(1);
    assertThat(declared.renderingGuardActiveDuringIteration)
        .as("the hook must run under the rendering guard")
        .isTrue();
    assertThat(rendered).isInstanceOf(ListVal.class);
    assertThat(((ListVal) rendered).elements()).hasSize(5);
    var text = flatten(rendered);
    assertThat(text).contains("a", "b", "c", "d", "e");
    assertThat(text).doesNotContain("f").doesNotContain("g");
  }

  /**
   * Structured twin: {@link
   * RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeThrowingIterable}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void aThrowingThirdHookRendersAFailureMarkerOnBothPaths(Function<Object, String> renderAsText) {
    var rendered =
        renderAsText.apply(
            new RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeThrowingIterable());

    assertThat(rendered).contains("<error: IllegalStateException>");
  }

  // ------------------------------------------------------------------------------------ bullet 13

  /**
   * Structured twin: {@link
   * RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeAbstractMapSubclass}.
   */
  @Test
  void
      aNarrativeElementsAnnotatedAbstractMapSubclassIsEnumeratedThroughItsOverrideCappedAndGuardedStructured() {
    var declared = new RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeAbstractMapSubclass();

    var rendered = renderer.renderStructured(declared);

    assertThat(declared.entrySetCalls).hasValue(1);
    assertThat(declared.renderingGuardActiveDuringEntrySet)
        .as("the hook must run under the rendering guard")
        .isTrue();
    assertThat(rendered).isInstanceOf(ObjectVal.class);
    var fields = ((ObjectVal) rendered).fields();
    assertThat(fields).hasSize(5);
    var text = flatten(rendered);
    assertThat(text).contains("k0").contains("v0").contains("k4").contains("v4");
    assertThat(text)
        .doesNotContain("k5")
        .doesNotContain("v5")
        .doesNotContain("k6")
        .doesNotContain("v6");
  }

  /**
   * Structured twin: {@link
   * RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeThrowingAbstractMapSubclass}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void
      aNarrativeElementsAnnotatedAbstractMapSubclassWhoseOverrideThrowsDegradesToTheFailureMarkerOnBothPaths(
          Function<Object, String> renderAsText) {
    var rendered =
        renderAsText.apply(
            new RenderingReadsStateNeverRunsBehaviourTest
                .DeclaredSafeThrowingAbstractMapSubclass());

    assertThat(rendered).contains("<error: IllegalStateException>");
  }

  // ------------------------------------------------------------------------------------ bullet 14

  /**
   * Structured twin: {@link
   * RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeAbstractCollectionSubclass}.
   */
  @Test
  void
      aNarrativeElementsAnnotatedAbstractCollectionSubclassIsEnumeratedThroughItsOverrideCappedAndGuardedStructured() {
    var declared =
        new RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeAbstractCollectionSubclass();

    var rendered = renderer.renderStructured(declared);

    assertThat(declared.iteratorCalls).hasValue(1);
    assertThat(declared.renderingGuardActiveDuringIteration)
        .as("the hook must run under the rendering guard")
        .isTrue();
    assertThat(rendered).isInstanceOf(ListVal.class);
    assertThat(((ListVal) rendered).elements()).hasSize(5);
    var text = flatten(rendered);
    assertThat(text).contains("a", "b", "c", "d", "e");
    assertThat(text).doesNotContain("f").doesNotContain("g");
  }

  /**
   * Structured twin: {@link
   * RenderingReadsStateNeverRunsBehaviourTest.DeclaredSafeThrowingAbstractCollectionSubclass}.
   */
  @ParameterizedTest
  @MethodSource("bothPaths")
  void
      aNarrativeElementsAnnotatedAbstractCollectionSubclassWhoseOverrideThrowsDegradesToTheFailureMarkerOnBothPaths(
          Function<Object, String> renderAsText) {
    var rendered =
        renderAsText.apply(
            new RenderingReadsStateNeverRunsBehaviourTest
                .DeclaredSafeThrowingAbstractCollectionSubclass());

    assertThat(rendered).contains("<error: IllegalStateException>");
  }
}
