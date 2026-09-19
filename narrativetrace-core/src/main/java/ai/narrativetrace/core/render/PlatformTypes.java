/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.io.File;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/**
 * Whether a class's own state and behaviour are defined by the platform, never by application code
 * — the origin test the rendering rule needs wherever {@code instanceof} alone cannot say whether a
 * method the renderer is about to call is safe to run.
 *
 * <p>INTENT: {@link ValueRenderer} used to decide "may I enumerate this?" with a bare {@code
 * instanceof Collection}/{@code instanceof Map} test, which cannot tell a plain {@link
 * java.util.ArrayList} from a user subclass that overrides {@code iterator()} with a side effect.
 * This class answers the question the rule actually asks — is this class's own module {@code
 * java.base} (equivalently: is it defined by the bootstrap or platform class loader), never a user
 * class loader — so {@link ValueRenderer} can tell an exact platform type, a user subclass of one,
 * and a hand-rolled lookalike apart before it calls anything.
 *
 * <p><b>@llmNote</b> Decided by defining class loader, not by package or module name string, which
 * an application class could counterfeit.
 *
 * <p>The second question this class answers is narrower and must not be confused with the first:
 * {@link #isStatelessLeaf} names, one by one, the platform types whose own {@code toString()}
 * rendering may call. Platform origin is a precondition of that list, never a substitute for it — a
 * platform value can hold a whole application document, and a value with no readable field can hold
 * its state somewhere a field walk cannot follow.
 */
final class PlatformTypes {

  private PlatformTypes() {}

  /**
   * Whether the JDK itself defines {@code clazz} — the bootstrap loader ({@code null}) or the
   * platform loader, which between them own every platform module including {@code java.base}. A
   * class on the application class path, even one named {@code java.util.Date}, is loaded by the
   * system loader and answers {@code false} here.
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals") // a loader is identified by identity
  static boolean isPlatformDefined(Class<?> clazz) {
    var loader = clazz.getClassLoader();
    return loader == null || loader == ClassLoader.getPlatformClassLoader(); // NOPMD
  }

  /**
   * The exact platform classes whose own {@code toString()} is a pure read of the state they hold
   * themselves — the closed list {@link #isStatelessLeaf} answers from.
   *
   * <p>INTENT: Derived from what {@link ValueRenderer} already treats as a leaf — the boxed
   * primitives and {@link ScalarTrust}'s numerics its scalar branches short-circuit, plus the value
   * types that used to reach their own {@code toString()} through the fieldless/platform-origin
   * test this list replaces. Every entry holds a number, a moment, an identifier, a locator, a
   * pattern or a single primitive cell, and prints exactly that.
   *
   * <p><b>@edgeCase</b> Nothing that stringifies text a caller handed it is here, however small: a
   * {@link StringBuilder}, a {@code StringWriter}, a {@code CharBuffer} or a {@link Throwable}
   * prints the application's own content, and a document-shaped platform value would print a whole
   * subtree — the very content the redaction axes exist to inspect, which the stringification path
   * cannot see field names for. Those render as objects instead, naming their type.
   */
  private static final Set<Class<?>> STATELESS_LEAVES =
      Set.of(
          Boolean.class,
          Byte.class,
          Character.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          String.class,
          BigInteger.class,
          BigDecimal.class,
          Instant.class,
          LocalDate.class,
          LocalTime.class,
          LocalDateTime.class,
          OffsetTime.class,
          OffsetDateTime.class,
          ZonedDateTime.class,
          Duration.class,
          Period.class,
          Year.class,
          YearMonth.class,
          MonthDay.class,
          Date.class,
          UUID.class,
          Locale.class,
          Currency.class,
          URI.class,
          URL.class,
          Inet4Address.class,
          Inet6Address.class,
          File.class,
          Pattern.class,
          Class.class,
          AtomicBoolean.class,
          AtomicInteger.class,
          AtomicLong.class,
          LongAdder.class,
          DoubleAdder.class);

  /**
   * The platform leaf FAMILIES: an abstract platform value type whose every implementation is
   * JDK-internal, so the exact class cannot be named in {@link #STATELESS_LEAVES} at all — a {@link
   * Path} is a {@code sun.nio.fs.UnixPath}, a {@link ZoneId} a {@code java.time.ZoneRegion}, a
   * {@link Charset} a {@code sun.nio.cs.UTF_8}.
   *
   * <p><b>@llmNote</b> Trust still turns on the EXACT class's origin: {@link #isStatelessLeaf}
   * consults this list only after {@link #isPlatformDefined} has answered for the runtime class
   * itself, so an application subclass of {@code Path} or {@code Charset} — whose {@code
   * toString()} is application code — is refused exactly as it would be if it were named after one.
   * The list is enumerated by hand for the same reason the exact one is: "the platform defines it"
   * alone is no statement that a value is a leaf.
   */
  private static final List<Class<?>> STATELESS_LEAF_FAMILIES =
      List.of(Path.class, ZoneId.class, Charset.class);

  /**
   * Whether {@code clazz} is a stateless leaf — the one kind of value whose own {@code toString()}
   * rendering may call (the narrative-summary hook is the other sanctioned one). The single
   * decision both of {@link ValueRenderer}'s paths ask, so they cannot answer it differently.
   *
   * <p>INTENT: Membership is by the exact class, which IS its origin (a class named {@code
   * java.util.UUID} loaded from an application jar is a different {@link Class} object and answers
   * {@code false} here) — never by assignability to a listed type, never by the defining loader
   * alone, and above all never by "declares no instance field". A value with nothing for reflection
   * to read is not thereby a value with nothing to tell: a fieldless class can hold its state in a
   * static identity-keyed side table, a {@link ClassValue} or a {@link ThreadLocal}, all invisible
   * to a field walk and all freely readable from inside its own {@code toString()} — which would
   * then reach the trace with only the value-shape scan and the length cap in front of it, no field
   * name to match and no {@code @NotTraced} to honor. The cross-runtime finding that ruled this (a
   * runtime bridging box, a JSON element, a C-level structure: each fieldless, each printing state
   * a field walk could not see) closed the same door in every NarrativeTrace runtime, each with its
   * own explicit list of platform leaves.
   *
   * <p><b>@edgeCase</b> An enum never arrives here: both rendering paths answer a {@code Enum} in
   * their scalar branch, where its text is sanitized precisely because a constant body may supply
   * its own {@code toString()}. A subclass is never a leaf because its base is.
   */
  static boolean isStatelessLeaf(Class<?> clazz) {
    if (STATELESS_LEAVES.contains(clazz)) {
      return true;
    }
    if (!isPlatformDefined(clazz)) {
      return false;
    }
    for (var family : STATELESS_LEAF_FAMILIES) {
      if (family.isAssignableFrom(clazz)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The nearest platform-defined ancestor of {@code clazz} that itself implements {@code
   * relevantInterface}, or {@code null} when none exists — a hand-rolled type with no platform base
   * to fall back on, built straight from the interface with no {@code extends} in its lineage.
   *
   * <p><b>@llmNote</b> Deliberately stops before {@link Object}: every class descends from {@code
   * Object}, and {@code Object} is platform-defined, so including it would answer "yes, a platform
   * ancestor exists" for every class on earth — the one wrong answer this method exists to avoid.
   * {@code relevantInterface} (usually {@link java.util.Collection} or {@link java.util.Map}) is
   * what keeps an unrelated platform ancestor from being mistaken for a collection ancestor.
   */
  static Class<?> platformAncestor(Class<?> clazz, Class<?> relevantInterface) {
    for (var c = clazz.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
      if (isPlatformDefined(c) && relevantInterface.isAssignableFrom(c)) {
        return c;
      }
    }
    return null;
  }

  /**
   * Whether {@code clazz} overrides {@code ancestor}'s {@code methodName} — so it can no longer be
   * trusted as a pure, side-effect-free read of the ancestor's own state.
   *
   * <p><b>@edgeCase</b> A method the reflective lookup cannot resolve at all answers {@code true}
   * (do not trust it): an unresolvable method is not a fact this predicate can vouch for either
   * way, and the safe reading of "unknown" is the same as "overridden."
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals") // a Class is identified by identity
  static boolean overrides(
      Class<?> clazz, Class<?> ancestor, String methodName, Class<?>... params) {
    try {
      Method resolved = clazz.getMethod(methodName, params);
      return resolved.getDeclaringClass() != ancestor; // NOPMD - identity is the actual question
    } catch (NoSuchMethodException e) {
      return true;
    }
  }
}
