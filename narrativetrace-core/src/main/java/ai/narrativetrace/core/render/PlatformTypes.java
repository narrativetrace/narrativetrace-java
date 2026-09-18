/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.lang.reflect.Method;

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
 * an application class could counterfeit. See {@link ValueRenderer}'s own {@code PLATFORM_DEFINED}
 * cache, which delegates here so the two questions — "may I trust this type's own {@code
 * toString()}?" and "may I enumerate this type without going through a possibly-overridden method?"
 * — never drift onto two different answers for the same class.
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
