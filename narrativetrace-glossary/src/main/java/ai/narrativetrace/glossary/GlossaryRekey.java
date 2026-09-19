/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Carries curated entries across a change to normalization, before the harvest is merged.
 *
 * <p>INTENT: Term identity is the normalized phrase, so any change to normalization strands curated
 * entries under a key nothing will look up again. This moves them: a term whose successor the
 * harvest now produces takes that key, definition and translations intact, and a term whose every
 * source was language plumbing retires. Everything else is untouched — the glossary is otherwise
 * additive, and a term whose code was merely deleted stays exactly where it is.
 *
 * <p>Both halves are decided against the harvest, never against a term's text alone. A phrase the
 * harvest still produces is current by definition and never moves, which is what keeps an honest
 * {@code get or create account} out of the property-read rule; and a phrase the harvest does not
 * produce is not a successor, so no key is ever invented.
 */
public final class GlossaryRekey {

  /** Members a language writes for you: nothing they named is anyone's vocabulary. */
  private static final Set<String> PLUMBING_MEMBERS = Set.of("values", "valueOf", "copy");

  private static final Pattern COMPONENT_ACCESSOR = Pattern.compile("component\\d+");

  /** Phrase heads a rule change drops, each leaving the rest of the phrase as the successor. */
  private static final List<String> RETIRED_HEADS = List.of("get ", "is ", "per ");

  private GlossaryRekey() {}

  /**
   * Re-keys the glossary against what the harvest now produces.
   *
   * @param glossary the committed glossary as read; must not be {@code null}
   * @param harvest the current run's observations; must not be {@code null}
   * @return the glossary with retired keys carried to their successors
   */
  public static Glossary migrate(Glossary glossary, HarvestResult harvest) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    if (harvest == null) {
      throw new IllegalArgumentException("harvest must not be null");
    }
    var produced =
        harvest.candidates().stream()
            .map(candidate -> new TermKey(candidate.context(), candidate.phrase()))
            .collect(java.util.stream.Collectors.toSet());
    var carried = new LinkedHashMap<TermKey, GlossaryTerm>();
    for (var term : glossary.terms()) {
      fateOf(term, produced)
          .ifPresent(kept -> carried.merge(TermKey.of(kept), kept, GlossaryRekey::combine));
    }
    return new Glossary(
        glossary.schemaVersion(),
        glossary.contexts(),
        glossary.abbreviations(),
        List.copyOf(carried.values()));
  }

  /** The term as it survives, or empty when nothing will ever produce it again. */
  private static Optional<GlossaryTerm> fateOf(GlossaryTerm term, Set<TermKey> produced) {
    if (produced.contains(TermKey.of(term))) {
      return Optional.of(term);
    }
    if (wasLanguagePlumbing(term)) {
      return Optional.empty();
    }
    return Optional.of(
        successorPhrase(term)
            .filter(phrase -> produced.contains(new TermKey(term.context(), phrase)))
            .map(phrase -> underPhrase(term, phrase))
            .orElse(term));
  }

  /**
   * The phrase this term's identifier normalizes to now, when a rule change moved it.
   *
   * <p><b>@edgeCase</b> A property read only moves when a source of the term actually was one:
   * {@code get} at the head of a phrase can be the verb someone wrote. Template terms are raw
   * annotation text and are never normalized, so no head rule applies to them.
   */
  private static Optional<String> successorPhrase(GlossaryTerm term) {
    if (term.kind() == TermKind.TEMPLATE) {
      return Optional.empty();
    }
    return RETIRED_HEADS.stream()
        .filter(head -> term.term().startsWith(head))
        .filter(head -> "per ".equals(head) || readsAProperty(term))
        .map(head -> term.term().substring(head.length()))
        .findFirst();
  }

  private static boolean readsAProperty(GlossaryTerm term) {
    return term.sources().stream()
        .map(GlossaryRekey::memberOf)
        .anyMatch(member -> member.startsWith("get") || member.startsWith("is"));
  }

  private static boolean wasLanguagePlumbing(GlossaryTerm term) {
    return !term.sources().isEmpty()
        && term.sources().stream().map(GlossaryRekey::memberOf).allMatch(GlossaryRekey::isPlumbing);
  }

  private static boolean isPlumbing(String member) {
    return PLUMBING_MEMBERS.contains(member) || COMPONENT_ACCESSOR.matcher(member).matches();
  }

  private static String memberOf(String source) {
    return source.substring(source.lastIndexOf('.') + 1);
  }

  /** The same entry under its new key; the phrase a property read leaves behind is a noun. */
  private static GlossaryTerm underPhrase(GlossaryTerm term, String phrase) {
    return new GlossaryTerm(
        phrase,
        term.context(),
        phrase.indexOf(' ') < 0 ? TermKind.WORD : TermKind.NOUN_PHRASE,
        term.status(),
        term.definition(),
        term.translations(),
        term.synonyms(),
        term.sources(),
        term.firstSeen());
  }

  /** The surviving entry keeps everything it has and gains what the retired key carried. */
  private static GlossaryTerm combine(GlossaryTerm kept, GlossaryTerm arriving) {
    var translations = new LinkedHashMap<String, String>(arriving.translations());
    translations.putAll(kept.translations());
    var synonyms = new ArrayList<>(kept.synonyms());
    arriving.synonyms().stream().filter(alias -> !synonyms.contains(alias)).forEach(synonyms::add);
    var sources = new LinkedHashSet<>(kept.sources());
    sources.addAll(arriving.sources());
    return new GlossaryTerm(
        kept.term(),
        kept.context(),
        kept.kind(),
        kept.status() == TermStatus.HARVESTED ? arriving.status() : kept.status(),
        kept.definition() == null ? arriving.definition() : kept.definition(),
        Map.copyOf(translations),
        List.copyOf(synonyms),
        List.copyOf(sources),
        kept.firstSeen().isBefore(arriving.firstSeen()) ? kept.firstSeen() : arriving.firstSeen());
  }
}
