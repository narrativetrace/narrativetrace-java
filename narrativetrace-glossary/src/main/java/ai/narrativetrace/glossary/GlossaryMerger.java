/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Additive merge of a {@link HarvestResult} into an existing {@link Glossary}.
 *
 * <p>INTENT: The merge rules of ADR-012, enforced structurally: existing entries are never removed
 * or mutated (human-authored fields are sacrosanct), unseen {@code (context, phrase)} pairs join as
 * {@code harvested} terms, phrases matching a deprecated alias are suppressed and reported, and
 * re-merging the same harvest is a no-op (idempotence). {@code firstSeen} comes from the injected
 * {@link Clock}, set once at entry creation.
 *
 * <p><b>@llmNote</b> The schema-2 {@code abbreviations} section is human-owned and passes through
 * untouched. Accepting shorthand is a decision someone makes; a harvester inferring it from what it
 * happened to see is exactly the defect the section was introduced to remove.
 */
public final class GlossaryMerger {

  /** New terms record at most this many observed source sites. */
  private static final int SOURCE_LIMIT = 3;

  private static final String UNASSIGNED_DESCRIPTION =
      "Harvested terms not yet mapped to a context";

  private final Clock clock;

  /**
   * @param clock source of {@code firstSeen} dates for newly created terms; must not be {@code
   *     null}
   */
  public GlossaryMerger(Clock clock) {
    if (clock == null) {
      throw new IllegalArgumentException("clock must not be null");
    }
    this.clock = clock;
  }

  /**
   * Merges harvested observations into the glossary, additively.
   *
   * @param existing glossary to merge into; must not be {@code null}
   * @param harvest observations of one run; must not be {@code null}
   * @return merged glossary plus the new terms and suppressed alias uses of this merge
   */
  public MergeResult merge(Glossary existing, HarvestResult harvest) {
    if (existing == null) {
      throw new IllegalArgumentException("existing glossary must not be null");
    }
    if (harvest == null) {
      throw new IllegalArgumentException("harvest must not be null");
    }
    var suppressed = new ArrayList<HarvestCandidate>();
    var newTerms = buildNewTerms(classifyUnseen(existing, harvest, suppressed));
    var merged = mergedGlossary(existing, newTerms);
    assertAdditive(existing, merged);
    return new MergeResult(merged, newTerms, suppressed);
  }

  /** Sorts candidates into suppressed alias uses and accumulators for genuinely new terms. */
  private static Map<TermKey, NewTermAccumulator> classifyUnseen(
      Glossary existing, HarvestResult harvest, List<HarvestCandidate> suppressed) {
    var aliasIndex = AliasIndex.of(existing);
    var existingKeys = new HashSet<TermKey>();
    existing.terms().forEach(term -> existingKeys.add(TermKey.of(term)));
    var accumulators = new LinkedHashMap<TermKey, NewTermAccumulator>();
    for (var candidate : harvest.candidates()) {
      var key = new TermKey(candidate.context(), candidate.phrase());
      if (aliasIndex.isAlias(key)) {
        suppressed.add(candidate);
      } else if (!existingKeys.contains(key)) {
        accumulators
            .computeIfAbsent(key, k -> new NewTermAccumulator(candidate.kind()))
            .addSite(candidate.site());
      }
    }
    return accumulators;
  }

  private List<GlossaryTerm> buildNewTerms(Map<TermKey, NewTermAccumulator> accumulators) {
    var firstSeen = LocalDate.now(clock);
    return accumulators.entrySet().stream()
        .map(e -> e.getValue().toTerm(e.getKey(), firstSeen))
        .toList();
  }

  private static Glossary mergedGlossary(Glossary existing, List<GlossaryTerm> newTerms) {
    var contexts = new HashMap<>(existing.contexts());
    for (var term : newTerms) {
      contexts.computeIfAbsent(term.context(), GlossaryMerger::declaredContext);
    }
    var terms = new ArrayList<>(existing.terms());
    terms.addAll(newTerms);
    // Abbreviations are human-owned, like definitions and translations: harvesting never writes
    // them, so the merge carries the section through untouched.
    return new Glossary(existing.schemaVersion(), contexts, existing.abbreviations(), terms);
  }

  private static BoundedContext declaredContext(String name) {
    var description = ContextResolver.UNASSIGNED.equals(name) ? UNASSIGNED_DESCRIPTION : null;
    return new BoundedContext(name, List.of(), description);
  }

  /** Postcondition: merge never removes or replaces an existing entry. */
  private static void assertAdditive(Glossary existing, Glossary merged) {
    assert merged.terms().size() >= existing.terms().size()
        : "merge must never shrink the glossary";
    assert existing.terms().stream().allMatch(term -> merged.terms().contains(term))
        : "merge must keep every existing entry unchanged";
  }

  /** Collects the kind of the first observation and up to {@link #SOURCE_LIMIT} distinct sites. */
  private static final class NewTermAccumulator {

    private final TermKind kind;
    private final LinkedHashSet<String> sites = new LinkedHashSet<>();

    private NewTermAccumulator(TermKind kind) {
      this.kind = kind;
    }

    private void addSite(String site) {
      if (sites.size() < SOURCE_LIMIT) {
        sites.add(site);
      }
    }

    private GlossaryTerm toTerm(TermKey key, LocalDate firstSeen) {
      return new GlossaryTerm(
          key.normalized(),
          key.context(),
          kind,
          TermStatus.HARVESTED,
          null,
          Map.of(),
          List.of(),
          List.copyOf(sites),
          firstSeen);
    }
  }
}
