/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.template;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Bounded, generational cache of parsed templates.
 *
 * <p>INTENT: {@link TemplateParser#resolve} is public and keys its cache on the caller's template
 * string. An unbounded map there is a host-memory-exhaustion vector reachable from the
 * <em>synchronous</em> path — the one path where nothing is droppable and no best-effort discard
 * can help — as soon as any adapter, plugin or downstream caller resolves a template built from
 * request data. Found by an adversarial review, which grew the map to 50,000 entries with a scratch
 * probe and watched fuzz RSS reach 1,384 MB.
 *
 * <p><b>@pattern</b> Generational (two-space) cache, the eviction policy that fits the access shape
 * here. Templates are overwhelmingly annotation constants: a handful of strings resolved on every
 * single call, for the life of the process. Anything that grows the cache without bound is by
 * definition <em>not</em> one of those. So entries are admitted into a {@code live} generation;
 * when it fills, it is demoted wholesale to {@code previous} and a fresh {@code live} starts. A
 * template still in use is found in {@code previous} on its next resolution and promoted back into
 * {@code live} — it costs a map put, never a re-parse. A single-use hostile template is simply
 * never promoted, and is released when the generation behind it is dropped.
 *
 * <p><b>@llmNote</b> The two rejected policies, and why the hot path decided it. A plain size cap
 * that <em>stops admitting</em> when full converts this memory DoS into a permanent CPU one: an
 * attacker fills the cache with junk first, and every real annotation template then re-parses on
 * every call forever. Clearing the whole cache on overflow is bounded but re-parses the hot
 * templates once per clear, at whatever rate the attacker chooses. Only the generational policy
 * keeps a constantly-used template resident while under flood.
 *
 * <p><b>@llmNote</b> Hot-path cost is unchanged from the unbounded map it replaces: a hit is one
 * {@link ConcurrentHashMap#get} against {@code live}, exactly as before. Only a miss pays the extra
 * {@code previous} lookup, and a miss was already paying for a regex parse.
 *
 * <p><b>@edgeCase</b> Unlike {@code computeIfAbsent}, two threads missing on the same template
 * concurrently may both parse it. That is deliberate: parsing is pure and its results are equal, so
 * the loser wastes work but cannot produce a wrong answer, and dropping {@code computeIfAbsent}
 * removes its per-bin lock from the resolution path.
 *
 * @llmNote Thread-safe. {@code live} and {@code previous} are {@code volatile} references that are
 *     replaced, never structurally mutated as a pair; rotation is serialised so exactly one thread
 *     retires a given generation.
 */
final class BoundedTemplateCache {

  private final int generationSize;

  private volatile ConcurrentHashMap<String, List<TemplateParser.Segment>> live;

  private volatile Map<String, List<TemplateParser.Segment>> previous;

  /**
   * @param generationSize entries admitted before a generation is retired; total retention is at
   *     most twice this
   * @throws IllegalArgumentException if {@code generationSize} is not positive
   */
  BoundedTemplateCache(int generationSize) {
    if (generationSize < 1) {
      throw new IllegalArgumentException("generationSize must be positive, was " + generationSize);
    }
    this.generationSize = generationSize;
    this.live = new ConcurrentHashMap<>();
    this.previous = Map.of();
    assert invariant() : "BoundedTemplateCache constructed in an inconsistent state";
  }

  /**
   * The parsed form of {@code template}, parsing it only if neither generation holds it.
   *
   * @param template the template string, used as the cache key
   * @param parser how to parse a template this cache has not seen; must not return {@code null}
   * @return the parsed segments, never {@code null}
   * @throws IllegalArgumentException if {@code template} or {@code parser} is {@code null}
   * @sideEffects may admit an entry and may retire the live generation
   */
  List<TemplateParser.Segment> get(
      String template, Function<String, List<TemplateParser.Segment>> parser) {
    if (template == null) {
      throw new IllegalArgumentException("template must not be null");
    }
    if (parser == null) {
      throw new IllegalArgumentException("parser must not be null");
    }
    var generation = live;
    var hit = generation.get(template);
    if (hit != null) {
      return hit;
    }
    var carried = previous.get(template);
    if (carried != null) {
      generation.put(template, carried);
      return carried;
    }
    var parsed = parser.apply(template);
    admit(template, parsed);
    assert parsed != null : "parser must not return null";
    return parsed;
  }

  /** Admits a freshly parsed template, retiring the live generation once it is full. */
  private void admit(String template, List<TemplateParser.Segment> parsed) {
    var generation = live;
    generation.put(template, parsed);
    if (generation.size() >= generationSize) {
      retire(generation);
    }
  }

  /**
   * Demotes a full generation and starts an empty one.
   *
   * <p><b>@edgeCase</b> Synchronised and re-checked: without the identity test, two threads that
   * both observe the same full generation would retire it twice, and the second would overwrite
   * {@code previous} with it and discard the generation the first had just started — silently
   * halving the cache. Contention here is negligible because this runs only on the parse path.
   */
  private synchronized void retire(ConcurrentHashMap<String, List<TemplateParser.Segment>> full) {
    // NOPMD CompareObjectsWithEquals - reference identity is the point: two generations
    // with equal contents are still different objects, and only the one currently live may
    // be retired.
    if (live != full) { // NOPMD
      return;
    }
    previous = full;
    live = new ConcurrentHashMap<>();
  }

  /** How many parsed templates are currently retained across both generations. */
  int size() {
    return live.size() + previous.size();
  }

  /** Whether this exact template is retained in either generation. */
  boolean contains(String template) {
    return live.containsKey(template) || previous.containsKey(template);
  }

  // ---------------------------------------------------------------
  // Invariant — checked by JUnit extension at setUp/tearDown
  // ---------------------------------------------------------------

  boolean invariant() {
    // Category 1 — neither generation may be null
    if (live == null) return false;
    if (previous == null) return false;

    // Category 2 — the configured bound is meaningful
    if (generationSize < 1) return false;

    // Category 4 — a retired generation is never larger than the bound that retired it;
    // the live one may overshoot by the number of threads admitting concurrently, so it is
    // bounded by the bound rather than pinned to it.
    if (previous.size() > generationSize) return false;

    // Category 6 — no null keys or values can be present; ConcurrentHashMap forbids both,
    // so this is structural rather than checked, and Map.of() is immutable and empty.

    // Category 3 — the two generations are distinct objects, so retiring one cannot alias
    // the other and make eviction a no-op.
    // NOPMD CompareObjectsWithEquals - identity again: two empty generations are equal but
    // must not be the same object, or retiring one would alias the other and evict nothing.
    return live != previous; // NOPMD
  }
}
