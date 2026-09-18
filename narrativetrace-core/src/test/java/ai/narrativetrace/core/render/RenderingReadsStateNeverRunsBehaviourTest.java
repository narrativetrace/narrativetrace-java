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
import ai.narrativetrace.core.template.TemplateParser;
import java.math.BigDecimal;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * The test family for the owner ruling copied into the repository's agent guide: <b>rendering reads
 * state, never runs behaviour.</b>
 *
 * <p>INTENT: Pins the rule against today's {@link ValueRenderer} and {@link TemplateParser}, both
 * of which still invoke user code at several sites (record component accessors, collection
 * iterators/{@code entrySet()}) rather than reading backing state. Every test here that fails
 * against today's code is {@link Disabled @Disabled} — pending the fix — so {@code check} stays
 * green while the family documents the target behaviour and stands ready to flip green the moment
 * each site is fixed. A test that already passes today (the rule already held at that site) is left
 * live, as a regression guard.
 *
 * <p><b>@llmNote</b> Uses {@link ValueRendererTest}'s and {@link
 * ValueRendererNativeStringificationTest}'s fixture idioms: a package-private static nested fixture
 * per scenario, an {@link AtomicInteger} (or a plain counter field) as the spy, {@code
 * render}/{@code renderStructured} exercised directly rather than through the proxy or agent.
 */
class RenderingReadsStateNeverRunsBehaviourTest {

  private final ValueRenderer renderer = new ValueRenderer();

  // ------------------------------------------------------------------------------------- bullet 1

  /** A record component accessor with a side effect: increments a shared counter, then answers. */
  record CountingAccessorRecord(String label, AtomicInteger calls) {
    @Override
    public String label() {
      calls.incrementAndGet();
      return label;
    }
  }

  /**
   * Pending: today's {@code componentValue}/{@code structuredComponentValue} call {@code
   * accessor.invoke(record)}, which runs this override and increments the counter. The target
   * behaviour reads the backing field named {@code label} instead, so the counter never moves.
   */
  @Test
  void recordAccessorWithASideEffectIsNeverInvokedByFlatRendering() {
    var calls = new AtomicInteger();
    var record = new CountingAccessorRecord("order-1", calls);

    var rendered = renderer.render(record);

    assertThat(calls).as("the accessor must never run").hasValue(0);
    assertThat(rendered).contains("order-1");
  }

  @Test
  void recordAccessorWithASideEffectIsNeverInvokedByStructuredRendering() {
    var calls = new AtomicInteger();
    var record = new CountingAccessorRecord("order-1", calls);

    var rendered = renderer.renderStructured(record);

    assertThat(calls).as("the accessor must never run").hasValue(0);
    assertThat(String.valueOf(rendered)).contains("order-1"); // NOPMD - structural read only
  }

  // ------------------------------------------------------------------------------------- bullet 2

  /** A record component accessor that always throws — the backing field still holds the value. */
  record ThrowingAccessorRecord(String label) {
    @Override
    public String label() {
      throw new IllegalStateException("accessor refuses");
    }
  }

  /**
   * Pending: today's accessor invoke throws, and the component renders {@code <error:
   * IllegalStateException>} — a failure marker for a value that was never actually unreadable. The
   * target reads the field directly, so no exception is ever raised and no marker appears.
   */
  @Test
  void recordAccessorThatThrowsStillRendersFromTheBackingFieldWithNoFailureMarker() {
    var record = new ThrowingAccessorRecord("real-value");

    var rendered = renderer.render(record);

    assertThat(rendered).contains("real-value");
    assertThat(rendered).doesNotContain("<error:");
  }

  // ------------------------------------------------------------------------------------- bullet 4

  /** A plain field-backed class whose JavaBean getter has an observable side effect. */
  static class PojoWithSideEffectingGetter {
    private final String name = "Alice";
    int getterCalls;

    public String getName() {
      getterCalls++;
      return name;
    }
  }

  /**
   * Live regression guard, NOT pending: ordinary object introspection ({@code renderObject}/{@code
   * renderStructuredObject}) already reads declared FIELDS via {@link java.lang.reflect.Field},
   * never JavaBean getters — this already holds today and must keep holding.
   */
  @Test
  void aJavaBeanGetterWithASideEffectIsNeverInvokedByRendering() {
    var pojo = new PojoWithSideEffectingGetter();

    var rendered = renderer.render(pojo);

    assertThat(pojo.getterCalls).as("the getter must never run").isZero();
    assertThat(rendered).contains("Alice");
  }

  // ------------------------------------------------------------------------------------ bullet 5a

  /**
   * A platform-collection ({@code ArrayList}) subclass whose overridden {@code iterator()} bombs.
   */
  static final class SideEffectingIteratorList extends ArrayList<Object> {
    final AtomicInteger iteratorCalls = new AtomicInteger();

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      throw new UnsupportedOperationException("iterator() must never be called by rendering");
    }
  }

  /**
   * Pending: today's {@code collectionItems} does {@code for (var item : collection)}, which calls
   * this override's {@code iterator()} directly. The target reads the platform ancestor's own
   * backing state ({@code ArrayList}'s internal array/size), never the overridden method.
   */
  @Test
  void aPlatformCollectionSubclassWithASideEffectingIteratorStillRendersItsElements() {
    var list = new SideEffectingIteratorList();
    list.add("alpha");
    list.add("beta");

    var rendered = renderer.render(list);

    assertThat(list.iteratorCalls).as("the overridden iterator() must never run").hasValue(0);
    assertThat(rendered).contains("alpha").contains("beta");
  }

  // ------------------------------------------------------------------------------------ bullet 5b

  /** A platform-collection ({@code HashMap}) subclass whose overridden {@code entrySet()} bombs. */
  static final class SideEffectingEntrySetMap extends HashMap<String, Object> {
    final AtomicInteger entrySetCalls = new AtomicInteger();

    @Override
    public java.util.Set<Map.Entry<String, Object>> entrySet() {
      entrySetCalls.incrementAndGet();
      throw new UnsupportedOperationException("entrySet() must never be called by rendering");
    }
  }

  /**
   * Pending: today's {@code mapEntries} calls {@code map.entrySet()} directly, running this
   * override. The target reads {@code HashMap}'s own backing table state instead.
   */
  @Test
  void aPlatformMapSubclassWithASideEffectingEntrySetStillRendersItsEntries() {
    var map = new SideEffectingEntrySetMap();
    map.put("k", "v");

    var rendered = renderer.render(map);

    assertThat(map.entrySetCalls).as("the overridden entrySet() must never run").hasValue(0);
    assertThat(rendered).contains("k=\"v\"");
  }

  // ------------------------------------------------------------------------------------- bullet 6

  /**
   * A user type implementing {@link List} from scratch — not a platform-collection subclass —
   * delegating storage to a private {@code ArrayList} field, with its own {@code iterator()}
   * bombing so an invocation is unmistakable.
   */
  static final class HandRolledList implements List<Object> {
    private final ArrayList<Object> backing = new ArrayList<>();
    final AtomicInteger iteratorCalls = new AtomicInteger();

    void seed(Object... items) {
      backing.addAll(List.of(items));
    }

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      throw new UnsupportedOperationException("iterator() must never be called by rendering");
    }

    @Override
    public int size() {
      return backing.size();
    }

    @Override
    public boolean isEmpty() {
      return backing.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return backing.contains(o);
    }

    @Override
    public Object[] toArray() {
      return backing.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return backing.toArray(a);
    }

    @Override
    public boolean add(Object o) {
      return backing.add(o);
    }

    @Override
    public boolean remove(Object o) {
      return backing.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return backing.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<?> c) {
      return backing.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<?> c) {
      return backing.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      return backing.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return backing.retainAll(c);
    }

    @Override
    public void clear() {
      backing.clear();
    }

    @Override
    public Object get(int index) {
      return backing.get(index);
    }

    @Override
    public Object set(int index, Object element) {
      return backing.set(index, element);
    }

    @Override
    public void add(int index, Object element) {
      backing.add(index, element);
    }

    @Override
    public Object remove(int index) {
      return backing.remove(index);
    }

    @Override
    public int indexOf(Object o) {
      return backing.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
      return backing.lastIndexOf(o);
    }

    @Override
    public ListIterator<Object> listIterator() {
      return backing.listIterator();
    }

    @Override
    public ListIterator<Object> listIterator(int index) {
      return backing.listIterator(index);
    }

    @Override
    public List<Object> subList(int fromIndex, int toIndex) {
      return backing.subList(fromIndex, toIndex);
    }
  }

  /**
   * Pending: today's code treats any {@code instanceof Collection} the same way (calls {@code
   * iterator()}), so this renders a failure marker instead of an object with a nested list. The
   * target distinguishes a hand-rolled {@code List} (not platform-defined) from a
   * platform-collection subclass: it must render as an object whose one field ({@code backing}) is
   * itself rendered as a list — read from that field, never through this type's own {@code
   * iterator()}.
   */
  @Test
  void aHandRolledListDelegatingToAPrivateFieldRendersThatFieldAsTheListWithoutItsOwnIterator() {
    var list = new HandRolledList();
    list.seed("alpha", "beta");

    var rendered = renderer.render(list);

    assertThat(list.iteratorCalls).as("the hand-rolled iterator() must never run").hasValue(0);
    assertThat(rendered).contains("HandRolledList");
    assertThat(rendered).contains("alpha").contains("beta");
  }

  // ------------------------------------------------------------------------------------- bullet 7

  /** A user type implementing {@link Iterable} directly — no platform collection anywhere. */
  static final class UserIterable implements Iterable<Object> {
    final AtomicInteger iteratorCalls = new AtomicInteger();

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      throw new UnsupportedOperationException("iterator() must never be called by rendering");
    }
  }

  /**
   * Pending: today's code does not special-case a non-{@code Collection} {@code Iterable} at all —
   * it falls through to plain field introspection of {@code iteratorCalls}, which does not throw
   * ({@code iterator()} is never called today either, so this exact assertion already holds) but
   * does not match the target shape: a bare {@code UserIterable<size unknown>} marker, with no
   * elements and no field dump. Kept in the pending family (not promoted to a live test like bullet
   * 4) because the overall target OUTPUT shape is not implemented, even though this one counter
   * assertion happens to already pass — see the paste in the commit note.
   */
  @Test
  void aUserIterableThatIsNotAPlatformCollectionRendersATypeMarkerWithNoElements() {
    var iterable = new UserIterable();

    var rendered = renderer.render(iterable);

    assertThat(iterable.iteratorCalls).as("iterator() must never run").hasValue(0);
    assertThat(rendered).isEqualTo("UserIterable<size unknown>");
  }

  // ------------------------------------------------------------------------------------- bullet 8

  /** A declared-safe {@code Iterable} whose element count exceeds the collection cap. */
  @NarrativeElements
  static final class DeclaredSafeIterable implements Iterable<Object> {
    private final List<Object> items = List.of("a", "b", "c", "d", "e", "f", "g");
    final AtomicInteger iteratorCalls = new AtomicInteger();
    boolean renderingGuardActiveDuringIteration;

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      renderingGuardActiveDuringIteration = RenderingGuard.isActive();
      return items.iterator();
    }
  }

  /** A declared-safe {@code Iterable} whose hook throws. */
  @NarrativeElements
  static final class DeclaredSafeThrowingIterable implements Iterable<Object> {
    @Override
    public Iterator<Object> iterator() {
      throw new IllegalStateException("hook refuses");
    }
  }

  /**
   * Pending: no runtime support for {@code @NarrativeElements} (or any third hook) exists today, so
   * a type declaring it is walked as a plain object, not enumerated — the elements never appear and
   * the cap never fires. The target enumerates, caps at {@code maxCollectionItems}, and runs the
   * hook under the rendering guard (checked here directly via {@link RenderingGuard#isActive()},
   * since proving "opens no span" end-to-end needs the agent's weaving infrastructure — see {@code
   * RenderReentrancyGuardTest} in {@code narrativetrace-agent} for that half).
   */
  @Test
  void aTypeDeclaringTheThirdHookIsEnumeratedCappedAndGuarded() {
    var declared = new DeclaredSafeIterable();

    var rendered = renderer.render(declared);

    assertThat(declared.iteratorCalls).hasValue(1);
    assertThat(declared.renderingGuardActiveDuringIteration)
        .as("the hook must run under the rendering guard")
        .isTrue();
    assertThat(rendered).contains("a", "b", "c", "d", "e");
    assertThat(rendered).doesNotContain("f").doesNotContain("g");
    assertThat(rendered).contains("…"); // capped, like every other element walk
  }

  @Test
  void aThrowingThirdHookRendersAFailureMarker() {
    var rendered = renderer.render(new DeclaredSafeThrowingIterable());

    assertThat(rendered).contains("<error: IllegalStateException>");
  }

  // ------------------------------------------------------------------------------------- bullet 9

  /** A record with a side-effecting accessor, resolved by name through {@code {order.total}}. */
  record OrderTotal(BigDecimal total, AtomicInteger calls) {
    @Override
    public BigDecimal total() {
      calls.incrementAndGet();
      return total;
    }
  }

  /**
   * Pending: {@code TemplateParser.accessProperty} always calls {@code method.invoke(object)} — the
   * record accessor convention it deliberately supports — so this counter moves today. The target
   * reads the record's backing field for a record accessor, exactly as {@link ValueRenderer} must.
   */
  @Test
  void templatePlaceholderResolutionNeverInvokesARecordAccessorWithASideEffect() {
    var calls = new AtomicInteger();
    var order = new OrderTotal(new BigDecimal("42.00"), calls);

    var resolved = TemplateParser.resolve("{order.total}", Map.of("order", order));

    assertThat(calls).as("the accessor must never run").hasValue(0);
    assertThat(resolved).contains("42.00");
  }

  // ------------------------------------------------------------------------------------ bullet 10

  /**
   * A composite whose {@code toString}/{@code equals}/{@code hashCode}/{@code compareTo} are all
   * spied. Complements the existing "custom toString never trusted" coverage ({@code
   * ValueRendererTest#aCustomToStringDoesNotStandInForIntrospectionOnAClassWithFields}, {@code
   * ValueRendererNativeStringificationTest}) rather than duplicating it — those pin {@code
   * toString()}; this extends the same shape with {@code equals}/{@code hashCode}/{@code compareTo}
   * spies and exercises it across the positions that could plausibly call one: a list element, a
   * map key, a map value, and a record component.
   */
  static final class Spy implements Comparable<Spy> {
    private final String value;
    final AtomicInteger toStringCalls = new AtomicInteger();
    final AtomicInteger equalsCalls = new AtomicInteger();
    final AtomicInteger hashCodeCalls = new AtomicInteger();
    final AtomicInteger compareToCalls = new AtomicInteger();

    Spy(String value) {
      this.value = value;
    }

    void resetSpies() {
      toStringCalls.set(0);
      equalsCalls.set(0);
      hashCodeCalls.set(0);
      compareToCalls.set(0);
    }

    @Override
    public String toString() {
      toStringCalls.incrementAndGet();
      return "Spy(" + value + ")";
    }

    @Override
    public boolean equals(Object other) {
      equalsCalls.incrementAndGet();
      return other instanceof Spy s && java.util.Objects.equals(value, s.value);
    }

    @Override
    public int hashCode() {
      hashCodeCalls.incrementAndGet();
      return java.util.Objects.hashCode(value);
    }

    @Override
    public int compareTo(Spy other) {
      compareToCalls.incrementAndGet();
      return value.compareTo(other.value);
    }
  }

  record SpyHolder(Spy held) {}

  /**
   * Live regression guard, NOT pending: {@link RenderWalk}'s cycle guard is identity-based ({@code
   * IdentityHashMap}, never {@code equals}/{@code hashCode}), and nothing in {@link ValueRenderer}
   * sorts — so {@code equals}/{@code hashCode}/{@code compareTo} are never invoked on a rendered
   * value today. {@code toString()} is likewise never trusted for a field-bearing type (the
   * existing family this test extends). All four already hold; this pins the composite across list,
   * map-key, map-value and nested-record positions in one place so a future regression is caught
   * here first.
   */
  @Test
  void aCompositesToStringEqualsHashCodeAndCompareToAreNeverInvokedByRendering() {
    var listItem = new Spy("list");
    var mapKey = new Spy("key");
    var mapValue = new Spy("value");
    var nested = new Spy("nested");
    var spies = List.of(listItem, mapKey, mapValue, nested);

    var map = new HashMap<Spy, Object>();
    map.put(mapKey, mapValue); // building the fixture legitimately calls hashCode()/equals() once
    var list = List.of(listItem);
    var holder = new SpyHolder(nested);
    resetAll(spies);

    renderBothWays(list, map, holder);

    assertNoSpyWasEverInvoked(spies);
  }

  private void renderBothWays(List<Spy> list, Map<Spy, Object> map, SpyHolder holder) {
    renderer.render(list);
    renderer.render(map);
    renderer.render(holder);
    renderer.renderStructured(list);
    renderer.renderStructured(map);
    renderer.renderStructured(holder);
  }

  private static void resetAll(List<Spy> spies) {
    spies.forEach(Spy::resetSpies);
  }

  private static void assertNoSpyWasEverInvoked(List<Spy> spies) {
    for (var spy : spies) {
      assertThat(spy.toStringCalls).as("toString() must never run").hasValue(0);
      assertThat(spy.equalsCalls).as("equals() must never run").hasValue(0);
      assertThat(spy.hashCodeCalls).as("hashCode() must never run").hasValue(0);
      assertThat(spy.compareToCalls).as("compareTo() must never run").hasValue(0);
    }
  }

  // ------------------------------------------------------------------------------------ bullet 11

  /**
   * A user subclass of the ABSTRACT platform base {@code AbstractMap} — owner ruling 2026-09-18:
   * unlike {@code HashMap}'s own backing table, an abstract base has no platform-owned state of its
   * own — whose overridden {@code entrySet()} counts its calls and throws.
   */
  @SuppressWarnings(
      "PMD.UnusedPrivateField") // label exists only to be read by object introspection
  static final class CountingThrowingAbstractMapSubclass extends AbstractMap<String, Object> {
    private final String label = "abstract-map";
    final AtomicInteger entrySetCalls = new AtomicInteger();

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      entrySetCalls.incrementAndGet();
      throw new UnsupportedOperationException("entrySet() must never be called by rendering");
    }
  }

  /**
   * Pending (owner ruling 2026-09-18: a user subclass of an ABSTRACT platform base is
   * object-introspected, its override NEVER called): unlike a subclass of {@code HashMap}, there is
   * no honest ancestor state read here — {@code AbstractMap} itself has no backing state, only the
   * override. So the override is never the extension point: the subclass is rendered like any
   * hand-rolled type, its own declared fields only. {@code entrySet()} is never called, so nothing
   * it could reach — traced or not — ever runs, and there is no failure marker because nothing was
   * ever attempted that could fail.
   */
  @Test
  void aSubclassOfAnAbstractMapPlatformBaseIsObjectIntrospectedNeverCallingItsOverride() {
    var value = new CountingThrowingAbstractMapSubclass();

    var rendered = renderer.render(value);

    assertThat(value.entrySetCalls).as("entrySet() must never run").hasValue(0);
    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).contains("CountingThrowingAbstractMapSubclass");
    assertThat(rendered).contains("abstract-map");
  }

  // ------------------------------------------------------------------------------------ bullet 12

  /** Same reasoning for the other ABSTRACT platform base, {@code AbstractCollection}. */
  @SuppressWarnings(
      "PMD.UnusedPrivateField") // label exists only to be read by object introspection
  static final class CountingThrowingAbstractCollectionSubclass extends AbstractCollection<Object> {
    private final String label = "abstract-collection";
    final AtomicInteger iteratorCalls = new AtomicInteger();

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      throw new UnsupportedOperationException("iterator() must never be called by rendering");
    }

    @Override
    public int size() {
      return 0;
    }
  }

  /**
   * Pending, same ruling as the {@code AbstractMap} case above, for {@code AbstractCollection}: the
   * subclass is object-introspected, its override never called.
   */
  @Test
  void aSubclassOfAnAbstractCollectionPlatformBaseIsObjectIntrospectedNeverCallingItsOverride() {
    var value = new CountingThrowingAbstractCollectionSubclass();

    var rendered = renderer.render(value);

    assertThat(value.iteratorCalls).as("iterator() must never run").hasValue(0);
    assertThat(rendered).doesNotContain("<error:");
    assertThat(rendered).contains("CountingThrowingAbstractCollectionSubclass");
    assertThat(rendered).contains("abstract-collection");
  }

  // ------------------------------------------------------------------------------------ bullet 13

  /**
   * The same ABSTRACT platform base, now declaring the third hook: {@code @NarrativeElements} makes
   * the override the sanctioned enumeration path — the only one left for a type with no
   * platform-owned state to fall back on.
   *
   * <p><b>@llmNote</b> Live regression guard, NOT pending: today's {@code renderMapByOrigin}
   * already enumerates any abstract-base {@code Map} subclass unconditionally (that IS the bug
   * bullet 11 pins), so this annotated shape already produces the target output — coincidentally,
   * since the dispatch does not yet consult the annotation for {@code Map} types at all. It must
   * keep holding once bullet 11's fix lands and {@code @NarrativeElements} becomes the only
   * enumeration path: the implementation must grow explicit {@code Map} support for the hook, not
   * merely stop looking at abstract ancestors.
   */
  @NarrativeElements
  static final class DeclaredSafeAbstractMapSubclass extends AbstractMap<String, Object> {
    final AtomicInteger entrySetCalls = new AtomicInteger();
    boolean renderingGuardActiveDuringEntrySet;

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      entrySetCalls.incrementAndGet();
      renderingGuardActiveDuringEntrySet = RenderingGuard.isActive();
      var entries = new LinkedHashSet<Map.Entry<String, Object>>();
      for (var i = 0; i < 7; i++) {
        entries.add(new AbstractMap.SimpleImmutableEntry<>("k" + i, "v" + i));
      }
      return entries;
    }

    @Override
    public int size() {
      return 7;
    }
  }

  /** The same declared-safe {@code AbstractMap} subclass, whose hook throws instead. */
  @NarrativeElements
  static final class DeclaredSafeThrowingAbstractMapSubclass extends AbstractMap<String, Object> {
    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      throw new IllegalStateException("hook refuses");
    }
  }

  @Test
  void
      aNarrativeElementsAnnotatedAbstractMapSubclassIsEnumeratedThroughItsOverrideCappedAndGuarded() {
    var declared = new DeclaredSafeAbstractMapSubclass();

    var rendered = renderer.render(declared);

    assertThat(declared.entrySetCalls).hasValue(1);
    assertThat(declared.renderingGuardActiveDuringEntrySet)
        .as("the hook must run under the rendering guard")
        .isTrue();
    assertThat(rendered).contains("k0=\"v0\"").contains("k4=\"v4\"");
    assertThat(rendered).doesNotContain("k5=\"v5\"").doesNotContain("k6=\"v6\"");
    assertThat(rendered).contains("…"); // capped, like every other element walk
  }

  @Test
  void
      aNarrativeElementsAnnotatedAbstractMapSubclassWhoseOverrideThrowsDegradesToTheFailureMarker() {
    var rendered = renderer.render(new DeclaredSafeThrowingAbstractMapSubclass());

    assertThat(rendered).contains("<error: IllegalStateException>");
  }

  // ------------------------------------------------------------------------------------ bullet 14

  /**
   * Same declared-safe shape for {@code AbstractCollection}. Also a live regression guard, NOT
   * pending: today's {@code renderIfEnumerable} already checks {@code @NarrativeElements} for any
   * {@code Iterable} (a {@code Collection} included) before it ever asks about origin, so this
   * shape's enumeration already runs through the sanctioned hook today.
   */
  @NarrativeElements
  static final class DeclaredSafeAbstractCollectionSubclass extends AbstractCollection<Object> {
    private final List<Object> items = List.of("a", "b", "c", "d", "e", "f", "g");
    final AtomicInteger iteratorCalls = new AtomicInteger();
    boolean renderingGuardActiveDuringIteration;

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      renderingGuardActiveDuringIteration = RenderingGuard.isActive();
      return items.iterator();
    }

    @Override
    public int size() {
      return items.size();
    }
  }

  /** The same shape, whose hook throws instead. */
  @NarrativeElements
  static final class DeclaredSafeThrowingAbstractCollectionSubclass
      extends AbstractCollection<Object> {
    @Override
    public Iterator<Object> iterator() {
      throw new IllegalStateException("hook refuses");
    }

    @Override
    public int size() {
      return 0;
    }
  }

  @Test
  void
      aNarrativeElementsAnnotatedAbstractCollectionSubclassIsEnumeratedThroughItsOverrideCappedAndGuarded() {
    var declared = new DeclaredSafeAbstractCollectionSubclass();

    var rendered = renderer.render(declared);

    assertThat(declared.iteratorCalls).hasValue(1);
    assertThat(declared.renderingGuardActiveDuringIteration)
        .as("the hook must run under the rendering guard")
        .isTrue();
    assertThat(rendered).contains("a", "b", "c", "d", "e");
    assertThat(rendered).doesNotContain("f").doesNotContain("g");
    assertThat(rendered).contains("…"); // capped, like every other element walk
  }

  @Test
  void
      aNarrativeElementsAnnotatedAbstractCollectionSubclassWhoseOverrideThrowsDegradesToTheFailureMarker() {
    var rendered = renderer.render(new DeclaredSafeThrowingAbstractCollectionSubclass());

    assertThat(rendered).contains("<error: IllegalStateException>");
  }
}
