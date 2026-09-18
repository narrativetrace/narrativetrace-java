/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.annotation.NotTraced;
import java.net.URI;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Objects whose {@code toString}, {@code hashCode}, {@code equals} or accessors misbehave — the
 * third-party DTOs a tracing library has no control over.
 *
 * <p>INTENT: These are the shapes the {@code graphs.json} {@code hostileMember} kind names. Each
 * holds a {@link HostileGraphs.Secret}, so the redaction oracle applies to all of them: a renderer
 * that falls back to {@code toString} because introspection failed must not print past the
 * redaction, and an exception raised inside instrumentation must not carry the value out in its
 * message.
 *
 * <p><b>@llmNote</b> {@link Blocking} sleeps for a bounded time rather than forever, on purpose: a
 * hung test reports nothing, however long the corpus run is given. Nothing here asserts a
 * wall-clock bound on it (family release rule 3, 2026-09-13: wall-clock, GC and scheduler are never
 * test inputs) — the property this fixture still exercises is that a slow {@code toString()} is
 * walked like any other, with redaction intact, not that it finishes within some budget.
 */
public final class HostileMembers {

  /** How long {@link Blocking#toString()} stalls the renderer. */
  public static final long BLOCK_MILLIS = 250;

  /** U+200B, a format character {@code Character.isJavaIdentifierPart} and no deny-list accepts. */
  public static final char ZERO_WIDTH_SPACE = 0x200b;

  /** U+0440, the Cyrillic letter that renders identically to a Latin {@code p}. */
  public static final char CYRILLIC_ER = 0x0440;

  /** Size of the string {@link Huge#toString()} returns. */
  public static final int HUGE_LENGTH = 1024 * 1024;

  private HostileMembers() {}

  /** A {@code toString} that throws — the commonest hostile DTO. */
  public record Throwing(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      throw new IllegalStateException("toString refuses");
    }
  }

  /** A {@code toString} whose exception message carries the secret out with it. */
  public record ThrowingWithPayload(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      throw new IllegalStateException("cannot render " + held.secret());
    }
  }

  /** A {@code toString} that recurses until the stack ends. */
  public record Recursing(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      return "recursing " + this;
    }
  }

  /** A {@code toString} that blocks briefly rather than forever, so a hostile run still ends. */
  public record Blocking(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      try {
        Thread.sleep(BLOCK_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return "eventually";
    }
  }

  /** A {@code toString} returning a mebibyte — the bounded-output case. */
  public record Huge(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      return "z".repeat(HUGE_LENGTH);
    }
  }

  /** A {@code toString} returning {@code null}, which the JDK contract forbids. */
  public record NullReturning(HostileGraphs.Secret held) {
    @Override
    public String toString() {
      return null;
    }
  }

  /**
   * A {@code Number} subclass whose {@code toString()} is a forged narrative line, not a number —
   * the shape {@code ValueRenderer.renderScalar} used to trust unsanitized because {@code Number}
   * is a fast-pathed scalar type. Carries a newline (log/Markdown-line forgery), a Markdown fence,
   * JSON object structure and the two non-JSON floating-point spellings, all in one payload so a
   * single case exercises every renderer's scalar-numeric path at once.
   *
   * <p><b>@llmNote</b> Takes a {@code held} constructor argument purely to match every other {@code
   * hostileMember} shape's factory signature in {@link HostileGraphs#hostile}; it is never read.
   * This shape is a scalar (a {@code Number}), so it is never introspected field-by-field the way
   * {@link Throwing} and its siblings are — the redaction oracle does not apply to it, only the
   * structure-forging one does.
   */
  @SuppressWarnings({
    "PMD.UnusedPrivateField", // kept only to match the uniform hostile() factory
    "PMD.NonSerializableClass" // extends Number, which is Serializable; never actually serialized
  })
  public static final class NumberHostileToString extends Number {
    private final HostileGraphs.Secret held;

    NumberHostileToString(HostileGraphs.Secret held) {
      this.held = held;
    }

    @Override
    public String toString() {
      return "1\n```\n{\"outcome\": \"success\"}\nNaN Infinity -Infinity\n```";
    }

    @Override
    public int intValue() {
      return 1;
    }

    @Override
    public long longValue() {
      return 1L;
    }

    @Override
    public float floatValue() {
      return 1f;
    }

    @Override
    public double doubleValue() {
      return 1d;
    }
  }

  /** A {@code hashCode} that throws — breaks any identity or hash set the renderer keeps. */
  public record HashThrowing(HostileGraphs.Secret held) {
    @Override
    public int hashCode() {
      throw new IllegalStateException("hashCode refuses");
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }
  }

  /** An {@code equals} that throws. */
  public record EqualsThrowing(HostileGraphs.Secret held) {
    @Override
    public boolean equals(Object other) {
      throw new IllegalStateException("equals refuses");
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }

  /** A record component accessor that throws while the renderer is reading components. */
  public record AccessorThrowing(HostileGraphs.Secret held, String label) {
    @Override
    public String label() {
      throw new IllegalStateException("accessor refuses");
    }
  }

  /**
   * A record component accessor with a side effect instead of a failure — counts its own calls, for
   * the "rendering reads state, never runs behaviour" rule (the repository's agent guide, owner
   * ruling 2026-09-17): the target reads the backing field directly, so the counter must never
   * move.
   *
   * <p><b>@llmNote</b> {@code calls} is {@code @NotTraced} so its own rendered text stays the
   * constant {@code [REDACTED]} marker rather than the live count — otherwise two renders of the
   * same instance would print two different numbers and this row would counterfeit a failure of the
   * unrelated {@code renderingIsIdempotentForEveryHostileGraph} property, which renders every
   * corpus row twice and compares the text.
   */
  public record CountingAccessor(HostileGraphs.Secret held, @NotTraced AtomicInteger calls) {
    @Override
    public HostileGraphs.Secret held() {
      calls.incrementAndGet();
      return held;
    }
  }

  /**
   * A platform-collection ({@link ArrayList}) subclass whose overridden {@code iterator()} both
   * counts its own calls and refuses — for the same rule: the target reads {@code ArrayList}'s own
   * backing state through the platform ancestor, never this override.
   */
  public static final class SideEffectingIteratorList extends ArrayList<Object> {
    public final AtomicInteger iteratorCalls = new AtomicInteger();

    SideEffectingIteratorList(HostileGraphs.Secret held) {
      add(held);
    }

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      throw new UnsupportedOperationException("iterator() must never be called by rendering");
    }
  }

  /**
   * A user {@link java.util.Collection} implemented from scratch — not a platform-collection
   * subclass, so it carries no platform ancestor state to fall back on. Same rule: the target must
   * never enumerate it by calling its own {@code iterator()}; it renders as an object instead.
   */
  public static final class LookalikeCollection implements java.util.Collection<Object> {
    private final List<Object> backing;
    public final AtomicInteger iteratorCalls = new AtomicInteger();

    LookalikeCollection(HostileGraphs.Secret held) {
      this.backing = new ArrayList<>(List.of(held));
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
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      throw new UnsupportedOperationException("iterator() must never be called by rendering");
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
    public boolean containsAll(java.util.Collection<?> c) {
      return backing.containsAll(c);
    }

    @Override
    public boolean addAll(java.util.Collection<?> c) {
      return backing.addAll(c);
    }

    @Override
    public boolean removeAll(java.util.Collection<?> c) {
      return backing.removeAll(c);
    }

    @Override
    public boolean retainAll(java.util.Collection<?> c) {
      return backing.retainAll(c);
    }

    @Override
    public void clear() {
      backing.clear();
    }
  }

  /**
   * A user subclass of the ABSTRACT platform base {@code AbstractMap} — no platform-owned state of
   * its own, unlike {@link SideEffectingIteratorList}'s {@link ArrayList} ancestor — whose
   * overridden {@code entrySet()} both counts its own calls and refuses, for the rule's 2026-09-18
   * refinement: the target object-introspects the subclass and never calls the override.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // held exists to be read by object introspection
  public static final class AbstractMapSubclassOverride extends AbstractMap<String, Object> {
    public final AtomicInteger entrySetCalls = new AtomicInteger();
    private final HostileGraphs.Secret held;

    AbstractMapSubclassOverride(HostileGraphs.Secret held) {
      this.held = held;
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      entrySetCalls.incrementAndGet();
      throw new UnsupportedOperationException("entrySet() must never be called by rendering");
    }
  }

  /**
   * Same rule, for the other ABSTRACT platform base, {@code AbstractCollection}: an overridden
   * {@code iterator()} that counts its own calls and refuses.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // held exists to be read by object introspection
  public static final class AbstractCollectionSubclassOverride extends AbstractCollection<Object> {
    public final AtomicInteger iteratorCalls = new AtomicInteger();
    private final HostileGraphs.Secret held;

    AbstractCollectionSubclassOverride(HostileGraphs.Secret held) {
      this.held = held;
    }

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
   * A FIELDLESS user subclass of the ABSTRACT platform base {@code AbstractCollection} — the
   * "toString door" the rendering rule's 2026-09-18 abstract-base refinement left open: {@code
   * rendersItsOwnString} trusts any type with no instance fields of its own to stand behind its own
   * stringification — and this class declares none — but the text it then trusts is {@code
   * AbstractCollection}'s OWN inherited {@code toString()}, which walks {@code iterator()}
   * internally. A fieldless subclass whose override counts its calls and refuses therefore still
   * reaches it, through a {@code toString()} the subclass never wrote a line of. {@link
   * AbstractCollectionSubclassOverride} above pins the field-bearing sibling of this same shape,
   * which the abstract-base refinement already made safe; this one has no field to make {@code
   * rendersItsOwnString} distrust it.
   *
   * <p><b>@llmNote</b> No constructor argument, unlike every other {@code hostileMember} shape:
   * accepting one only to discard it (the {@link NumberHostileToString} pattern) would still read
   * as a stored field to a careless refactor, and the whole point of this fixture is to carry zero.
   * The corpus factory's {@code held} local is simply unused on this arm of the switch, which is
   * legal and unremarkable Java. The row it backs carries no sentinel to look for.
   *
   * <p><b>@llmNote</b> The spy counter is {@code static}, not instance: {@code
   * rendersItsOwnString}'s "has no instance field" test excludes {@code static} members, so an
   * instance-field spy would defeat the very fieldless precondition this fixture exists to hold.
   * Callers reset it before use.
   */
  public static final class FieldlessAbstractSubclassToStringDoor
      extends AbstractCollection<Object> {
    public static final AtomicInteger ITERATOR_CALLS = new AtomicInteger();

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

  /** A JavaBean whose getter throws while the renderer is introspecting it. */
  public static final class GetterThrowing {

    private final HostileGraphs.Secret held;

    GetterThrowing(HostileGraphs.Secret held) {
      this.held = held;
    }

    /** Never returns; the renderer must degrade rather than propagate. */
    public String getDetail() {
      throw new IllegalStateException("getter refuses");
    }

    /** Reachable, and behind {@code @NotTraced} at the next level down. */
    public HostileGraphs.Secret getHeld() {
      return held;
    }
  }

  /**
   * Hostile *names*, where names actually come from data: map keys.
   *
   * <p>The deny-list matches on the rendered key text, so a key carrying an invisible code point
   * (U+200B inside the word "password") reads as an ordinary key to a human and matches nothing.
   * The secret here is protected by {@code @NotTraced} one level down, not by its name — which is
   * the point. Redaction that depended on a name being readable would be no redaction at all.
   */
  public static Map<String, Object> hostileKeyNames(HostileGraphs.Secret held) {
    var map = new LinkedHashMap<String, Object>();
    map.put("pass" + ZERO_WIDTH_SPACE + "word", "visible-but-unmatched");
    map.put("PASSWORD", "upper-case");
    map.put("pass_word", "separated");
    map.put(CYRILLIC_ER + "assword", "cyrillic-lookalike");
    map.put("held", held);
    return map;
  }

  /** More fields than the renderer's five-field cap, with the secret beyond it. */
  @SuppressWarnings("PMD.UnusedPrivateField") // every field exists to be introspected
  public static final class ManyFields {

    private final String a = "1";
    private final String b = "2";
    private final String c = "3";
    private final String d = "4";
    private final String e = "5";
    private final String f = "6";
    private final String g = "7";
    private final String h = "8";
    private final String i = "9";
    private final String j = "10";
    private final String k = "11";
    private final HostileGraphs.Secret secret;

    ManyFields(HostileGraphs.Secret held) {
      this.secret = held;
    }
  }

  /**
   * A plain class carrying a deny-listed field and a hand-written {@code toString} that prints it —
   * the shape the 2026-09-11 five-runtime investigation found leaking at depth zero.
   *
   * <p>INTENT: No nesting, no annotation, no wrapper. Just a class an application author wrote a
   * pleasant {@code toString} for years before anyone traced it. Every runtime except .NET trusted
   * that {@code toString} once the type declared one, and the trust check never consulted the name
   * deny-list, so {@code password=hunter2} reached every output.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class CuratedToString {

    private final String username = "ada";
    private final String password;

    CuratedToString(String password) {
      this.password = password;
    }

    @Override
    public String toString() {
      return "Login{username=" + username + ", password=" + password + "}";
    }
  }

  /**
   * A class with no sensitive field of its own whose hand-written {@code toString} interpolates a
   * nested holder that has one.
   *
   * <p><b>@llmNote</b> The held record's own generated {@code toString} prints its
   * {@code @NotTraced} component in full — which is exactly why the outer curated {@code toString}
   * is the leak: one interpolation carries a value the annotation promised no output would show.
   * The outer class is innocent by every name-based test there is.
   *
   * <p><b>@edgeCase</b> A plain class, deliberately not a record. Java dispatches a record to its
   * component walk <em>before</em> it ever asks whether the type stringifies itself, so a record
   * realisation of this shape would exercise the record branch and prove nothing about native
   * stringification — the thing the row exists for.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class CuratedToStringNested {

    private final String sessionId = "session-7";
    private final HostileGraphs.Secret holder;

    CuratedToStringNested(HostileGraphs.Secret holder) {
      this.holder = holder;
    }

    @Override
    public String toString() {
      return "Session{id=" + sessionId + ", holder=" + holder + "}";
    }
  }

  /**
   * A composite carrying a deny-listed field, built to be used as a {@code Map} KEY.
   *
   * <p><b>@llmNote</b> A key has to become text before it can be printed, which is the one place
   * native stringification is hardest to avoid — so this shape carries a curated {@code toString}
   * too. A renderer that reaches for it on the key path leaks there even when the value path is
   * already safe.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class SensitiveKey {

    private final String username = "ada";
    private final String password;

    SensitiveKey(String password) {
      this.password = password;
    }

    @Override
    public String toString() {
      return "Key{username=" + username + ", password=" + password + "}";
    }
  }

  /**
   * A holder whose {@code @NarrativeSummary} throws, with the payload in the exception message.
   *
   * <p>INTENT: The summary marker is the ONLY opt-in to curated rendering left, so its failure mode
   * is now part of the contract: the traced call succeeds, the failed part renders the typed marker
   * {@code <error: IllegalStateException>}, and the message — which carries the very value that
   * failed to format — reaches no output.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by the summary method and by introspection
  public static final class ThrowingSummary {

    private final HostileGraphs.Secret held;

    ThrowingSummary(HostileGraphs.Secret held) {
      this.held = held;
    }

    /** Never returns; the renderer must degrade to a typed, value-free marker. */
    @NarrativeSummary
    public String describe() {
      throw new IllegalStateException("cannot summarise " + held.secret());
    }
  }

  /**
   * A deny-listed field name whose value is a platform type the renderer would otherwise trust to
   * stringify itself — the {@code platform-type-name-redacted} row.
   *
   * <p><b>@llmNote</b> The name axis and the platform-type carve-out are two different questions:
   * this record proves the first is asked, and answered, before the second is ever consulted. A
   * renderer that reached the carve-out first would print the URI's own text — sentinel included —
   * because {@link java.net.URI} is exactly the kind of type the carve-out exists to trust.
   */
  public record NamedPlatformValue(URI password) {}

  /**
   * A user class named after a platform type ({@link java.util.Date}) but defined by application
   * code — the {@code platform-lookalike-walked} row.
   *
   * <p><b>@llmNote</b> Deliberately not {@code java.util.Date} itself. Trust is decided by defining
   * class loader, per {@code ValueRenderer.PLATFORM_DEFINED}, never by a class's own simple name —
   * a name-based test would have trusted this class's {@code toString()} and printed the field it
   * hides.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class Date {

    private final String username = "ada";
    private final String password;

    Date(String password) {
      this.password = password;
    }

    @Override
    public String toString() {
      return "Date{username=" + username + ", password=" + password + "}";
    }
  }

  /**
   * A user subclass of a platform type ({@link java.util.Date}) — the {@code
   * platform-subclass-walked} row.
   *
   * <p><b>@llmNote</b> The most-derived type's own defining loader decides trust, never an
   * ancestor's: this class is application-loaded even though its superclass is not, so it must be
   * walked exactly like {@link Date} above, not trusted merely because {@code extends Date} reaches
   * a platform type one step up.
   */
  @SuppressWarnings("PMD.UnusedPrivateField") // read by toString and by introspection
  public static final class ApplicationDate extends java.util.Date {

    private static final long serialVersionUID = 1L;

    private final String password;

    ApplicationDate(String password) {
      super(0L);
      this.password = password;
    }

    @Override
    public String toString() {
      return "ApplicationDate{password=" + password + "}";
    }
  }

  /** A ring: every node holds the next, and the last holds the first. */
  @SuppressWarnings("PMD.UnusedPrivateField") // both fields exist to be read reflectively
  public static final class Ring {

    private final HostileGraphs.Secret held;

    private Ring next;

    Ring(HostileGraphs.Secret held) {
      this.held = held;
    }

    void linkTo(Ring node) {
      this.next = node;
    }
  }
}
