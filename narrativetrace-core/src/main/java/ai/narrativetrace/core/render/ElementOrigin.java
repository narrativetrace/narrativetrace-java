/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.annotation.NarrativeElements;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * How — and whether — a value's elements may be walked: the ONE origin decision the rendering rule
 * turns on, asked once per value and shared by both rendering paths.
 *
 * <p>INTENT: {@link ValueRenderer} renders every value twice over, through {@code render} (flat
 * text) and {@code renderStructured} (a {@link ai.narrativetrace.api.event.RenderedValue} tree).
 * Each used to decide for itself whether a {@code Collection} or {@code Map} was safe to enumerate,
 * and they drifted: the flat path grew the origin dispatch the rendering rule requires while the
 * structured path stayed origin-blind, enumerating any {@code Collection}/{@code Map} through the
 * value's own — overridable — {@code iterator()} or {@code entrySet()}. A rule that holds on one
 * path and not the other is not a rule. This enum is the decision itself, separated from both
 * renderings of it, so the two paths cannot answer differently again: each switches over the same
 * verdict and only the ENCODING of the answer differs.
 *
 * <p><b>@llmNote</b> Keyed on ORIGIN — the defining class loader, via {@link PlatformTypes} — never
 * on {@code instanceof} alone, which cannot tell a plain {@link ArrayList} from a user subclass
 * whose {@code iterator()} has a side effect. {@link NarrativeElements}, the third sanctioned hook,
 * is asked FIRST and is the only way a type with no platform-owned state is ever enumerated.
 *
 * @see ValueRenderer#renderIfEnumerable
 * @see ValueRenderer#renderStructuredIfEnumerable
 */
enum ElementOrigin {

  /** The author declared the type's elements safe: enumerate through its own {@code iterator()}. */
  DECLARED_ITERABLE,

  /** The same hook for a {@code Map}: enumerate through its own {@code entrySet()}. */
  DECLARED_MAP,

  /**
   * A collection the platform itself defines: its iterator is the JDK's, never overridden below.
   */
  PLATFORM_COLLECTION,

  /** A map the platform itself defines: its {@code entrySet()} is the JDK's. */
  PLATFORM_MAP,

  /** A user subclass of {@link ArrayList}: read the ancestor's own array, never the override. */
  ARRAY_LIST_ANCESTOR_STATE,

  /** A user subclass of {@link HashMap}: walk the ancestor's own table, never the override. */
  HASH_MAP_ANCESTOR_STATE,

  /**
   * A bare user {@code Iterable} — no platform origin, not a {@code Collection}/{@code Map} at all:
   * its type name and, where free, its size, with no elements and no call to its iterator.
   */
  OPAQUE_ITERABLE,

  /**
   * A composite whose elements may not be touched — a subclass of an ABSTRACT platform base, a
   * hand-rolled {@code Collection}/{@code Map} with no platform ancestor, or a subclass of a
   * concrete platform type this renderer has no honest ancestor state read for. It renders as an
   * ordinary object, its own declared fields only; a field that happens to hold a platform
   * collection renders as that collection in turn, one level down.
   */
  OBJECT_STATE,

  /** Not a composite at all: the caller continues with arrays, records, hooks and leaf text. */
  NOT_A_COMPOSITE;

  /**
   * The verdict for one value.
   *
   * <p><b>@edgeCase</b> A platform-defined bare {@code Iterable} that is neither a {@code
   * Collection} nor a {@code Map} — {@link java.nio.file.Path} is the everyday one — is NOT opaque.
   * The rule withholds elements from an <em>application</em> iterable, whose iterator is
   * application code; a {@code Path} carries no application state and its own text is the JDK's, so
   * it falls through to the leaf branch and renders {@code tmp/narrativetrace} rather than a
   * contentless {@code UnixPath<size unknown>} marker.
   */
  static ElementOrigin of(Object value) {
    var clazz = value.getClass();
    if (clazz.isAnnotationPresent(NarrativeElements.class)) {
      if (value instanceof Iterable) {
        return DECLARED_ITERABLE;
      }
      if (value instanceof Map) {
        return DECLARED_MAP;
      }
    }
    if (value instanceof Collection) {
      return collectionOrigin(clazz);
    }
    if (value instanceof Map) {
      return mapOrigin(clazz);
    }
    if (value instanceof Iterable) {
      return PlatformTypes.isPlatformDefined(clazz) ? NOT_A_COMPOSITE : OPAQUE_ITERABLE;
    }
    return NOT_A_COMPOSITE;
  }

  /**
   * Whether the APPLICATION defines this class as a composite — a {@code Collection}, {@code Map}
   * or {@code Iterable} of its own.
   *
   * <p>INTENT: A composite never renders through its own {@code toString()}, on either path. The
   * inherited {@code AbstractCollection.toString()} of a FIELDLESS subclass is the door this
   * closes: the subclass declares no state, so {@code rendersItsOwnString} was willing to stand
   * behind its text — but that text is produced by walking {@code iterator()}, which the subclass
   * overrode. Stringification is a leaf's privilege; a composite's is always somebody's element
   * walk.
   *
   * <p><b>@llmNote</b> Platform composites are excluded because their text IS the JDK's: a {@code
   * Path} or a {@code Charset} renders its own short native form, exactly as {@code
   * PLATFORM_DEFINED} intends one level up. Every platform {@code Collection}/{@code Map} is
   * dispatched to its own enumeration branch long before this question is asked.
   */
  static boolean isApplicationComposite(Class<?> clazz) {
    return !PlatformTypes.isPlatformDefined(clazz)
        && (Iterable.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz));
  }

  /**
   * {@code ArrayList} is the one concrete platform collection with an honest, ancestor-owned state
   * read ({@code toArray()}, the JDK's own direct array copy). A subclass that overrode either that
   * or {@code size()} has taken the read back, so there is nothing honest left to read and the
   * subclass is object-introspected like any hand-rolled type.
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals") // a Class is identified by identity
  private static ElementOrigin collectionOrigin(Class<?> clazz) {
    if (PlatformTypes.isPlatformDefined(clazz)) {
      return PLATFORM_COLLECTION;
    }
    var ancestorIsArrayList =
        PlatformTypes.platformAncestor(clazz, Collection.class) == ArrayList.class;
    return ancestorIsArrayList
            && !PlatformTypes.overrides(clazz, ArrayList.class, "toArray")
            && !PlatformTypes.overrides(clazz, ArrayList.class, "size")
        ? ARRAY_LIST_ANCESTOR_STATE
        : OBJECT_STATE;
  }

  /** Same ruling for {@code HashMap}, whose {@code forEach} walks the JDK's own internal table. */
  @SuppressWarnings("PMD.CompareObjectsWithEquals") // a Class is identified by identity
  private static ElementOrigin mapOrigin(Class<?> clazz) {
    if (PlatformTypes.isPlatformDefined(clazz)) {
      return PLATFORM_MAP;
    }
    var ancestorIsHashMap = PlatformTypes.platformAncestor(clazz, Map.class) == HashMap.class;
    return ancestorIsHashMap
            && !PlatformTypes.overrides(clazz, HashMap.class, "forEach", BiConsumer.class)
            && !PlatformTypes.overrides(clazz, HashMap.class, "size")
        ? HASH_MAP_ANCESTOR_STATE
        : OBJECT_STATE;
  }
}
