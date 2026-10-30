/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The rules {@code GlossaryRekey.combine} applies when a re-keyed entry lands on a successor that
 * already exists, and the boundaries of the two rules that decide an entry's fate.
 *
 * <p>INTENT: {@code GlossaryRekeyTest} pins the journeys — what moves, what retires, what stays —
 * and asserts the merged translations of one of them. The field-by-field precedence of the merge
 * itself was unpinned, and so was every near miss of the plumbing pattern: both are curated data in
 * a committed file, so a wrong winner silently loses someone's definition.
 */
class GlossaryRekeyMergeRulesTest {

  private static final LocalDate EARLIER = LocalDate.of(2026, 1, 31);
  private static final LocalDate LATER = LocalDate.of(2026, 8, 15);

  @Test
  void aHarvestedSurvivorTakesTheArrivingEntrysCuratedStatus() {
    var migrated = migrate(kept(TermStatus.HARVESTED, null), arriving(TermStatus.CURATED, null));

    assertThat(migrated.terms())
        .singleElement()
        .extracting(GlossaryTerm::status)
        .isEqualTo(TermStatus.CURATED);
  }

  @Test
  void aCuratedSurvivorKeepsItsStatusWhenAHarvestedEntryArrives() {
    var migrated = migrate(kept(TermStatus.CURATED, null), arriving(TermStatus.HARVESTED, null));

    assertThat(migrated.terms())
        .singleElement()
        .extracting(GlossaryTerm::status)
        .isEqualTo(TermStatus.CURATED);
  }

  @Test
  void anArrivingDefinitionFillsTheSurvivorsEmptyOne() {
    var migrated =
        migrate(kept(TermStatus.CURATED, null), arriving(TermStatus.CURATED, "The person who."));

    assertThat(migrated.terms())
        .singleElement()
        .extracting(GlossaryTerm::definition)
        .isEqualTo("The person who.");
  }

  @Test
  void theSurvivorsOwnDefinitionIsNeverOverwritten() {
    var migrated =
        migrate(
            kept(TermStatus.CURATED, "Who wrote it."), arriving(TermStatus.CURATED, "The other."));

    assertThat(migrated.terms())
        .singleElement()
        .extracting(GlossaryTerm::definition)
        .isEqualTo("Who wrote it.");
  }

  @Test
  void theEarlierFirstSeenDateSurvivesWhicheverEntryCarriesIt() {
    var keptIsLater = migrate(keptSeen(LATER), arrivingSeen(EARLIER));
    var keptIsEarlier = migrate(keptSeen(EARLIER), arrivingSeen(LATER));

    assertThat(keptIsLater.terms())
        .singleElement()
        .extracting(GlossaryTerm::firstSeen)
        .isEqualTo(EARLIER);
    assertThat(keptIsEarlier.terms())
        .singleElement()
        .extracting(GlossaryTerm::firstSeen)
        .isEqualTo(EARLIER);
  }

  @Test
  void theSurvivorsTranslationWinsForALocaleBothEntriesCarry() {
    var migrated =
        migrate(
            term(
                "author", TermKind.WORD, Map.of("es", "autor"), List.of(), LATER, "Book.getAuthor"),
            term(
                "get author",
                TermKind.VERB_PHRASE,
                Map.of("es", "obtener autor", "de", "Autor"),
                List.of(),
                LATER,
                "Book.getAuthor"));

    assertThat(migrated.terms())
        .singleElement()
        .extracting(GlossaryTerm::translations)
        .isEqualTo(Map.of("es", "autor", "de", "Autor"));
  }

  @Test
  void aSynonymBothEntriesCarryIsListedOnce() {
    var alias = new SynonymAlias("writer alias", "renamed 2025");
    var migrated =
        migrate(
            term("author", TermKind.WORD, Map.of(), List.of(alias), LATER, "Book.getAuthor"),
            term(
                "get author",
                TermKind.VERB_PHRASE,
                Map.of(),
                List.of(alias),
                LATER,
                "Book.getAuthor"));

    assertThat(migrated.terms())
        .singleElement()
        .extracting(GlossaryTerm::synonyms)
        .isEqualTo(List.of(alias));
  }

  /** The sibling that shares the prefix: {@code component1x} is not {@code component\d+}. */
  @Test
  void aNearMissOfThePlumbingPatternIsNotLanguagePlumbing() {
    var glossary =
        glossaryOf(
            term(
                "component 1 x",
                TermKind.NOUN_PHRASE,
                Map.of(),
                List.of(),
                LATER,
                "Book.component1x"),
            term("values of", TermKind.VERB_PHRASE, Map.of(), List.of(), LATER, "Book.valuesOf"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("author"));

    assertThat(migrated.terms())
        .extracting(GlossaryTerm::term)
        .containsExactly("component 1 x", "values of");
  }

  /** A source with no dot in it is its own member name — the whole string, not an empty one. */
  @Test
  void aSourceWithoutAQualifierIsReadAsItsOwnMemberName() {
    var glossary =
        glossaryOf(term("copy", TermKind.VERB_PHRASE, Map.of(), List.of(), LATER, "copy"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("author"));

    assertThat(migrated.terms()).isEmpty();
  }

  @Test
  void aPropertyReadNamedWithIsMovesToTheNounItReads() {
    var glossary =
        glossaryOf(
            term("is overdue", TermKind.VERB_PHRASE, Map.of(), List.of(), LATER, "Loan.isOverdue"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("overdue"));

    assertThat(migrated.terms()).extracting(GlossaryTerm::term).containsExactly("overdue");
  }

  /** Template terms are raw annotation text, so no head rule may rewrite one. */
  @Test
  void aTemplateTermIsNeverReKeyed() {
    var glossary =
        glossaryOf(
            term("get author", TermKind.TEMPLATE, Map.of(), List.of(), LATER, "Book.getAuthor"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("author"));

    assertThat(migrated.terms()).extracting(GlossaryTerm::term).containsExactly("get author");
  }

  /** Still produced, and plumbing: current by definition, so the retirement rule never runs. */
  @Test
  void aPlumbingSourcedTermTheHarvestStillProducesIsKept() {
    var glossary = glossaryOf(term("copy", TermKind.WORD, Map.of(), List.of(), LATER, "Book.copy"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("copy"));

    assertThat(migrated.terms()).extracting(GlossaryTerm::term).containsExactly("copy");
  }

  private static Glossary migrate(GlossaryTerm survivor, GlossaryTerm mover) {
    return GlossaryRekey.migrate(glossaryOf(survivor, mover), harvestOf("author"));
  }

  private static GlossaryTerm kept(TermStatus status, String definition) {
    return new GlossaryTerm(
        "author",
        "library",
        TermKind.WORD,
        status,
        definition,
        Map.of(),
        List.of(),
        List.of("Book.getAuthor"),
        LATER);
  }

  private static GlossaryTerm arriving(TermStatus status, String definition) {
    return new GlossaryTerm(
        "get author",
        "library",
        TermKind.VERB_PHRASE,
        status,
        definition,
        Map.of(),
        List.of(),
        List.of("Book.getAuthor"),
        LATER);
  }

  private static GlossaryTerm keptSeen(LocalDate firstSeen) {
    return term("author", TermKind.WORD, Map.of(), List.of(), firstSeen, "Book.getAuthor");
  }

  private static GlossaryTerm arrivingSeen(LocalDate firstSeen) {
    return term(
        "get author", TermKind.VERB_PHRASE, Map.of(), List.of(), firstSeen, "Book.getAuthor");
  }

  private static GlossaryTerm term(
      String phrase,
      TermKind kind,
      Map<String, String> translations,
      List<SynonymAlias> synonyms,
      LocalDate firstSeen,
      String... sources) {
    return new GlossaryTerm(
        phrase,
        "library",
        kind,
        TermStatus.CURATED,
        null,
        translations,
        synonyms,
        List.of(sources),
        firstSeen);
  }

  private static Glossary glossaryOf(GlossaryTerm... terms) {
    return new Glossary(
        1,
        Map.of("library", new BoundedContext("library", List.of("com.acme.library"), null)),
        List.of(terms));
  }

  private static HarvestResult harvestOf(String... phrases) {
    return new HarvestResult(
        java.util.Arrays.stream(phrases)
            .map(
                phrase ->
                    new HarvestCandidate("library", phrase, TermKind.WORD, "Book.site", "site", 1))
            .toList());
  }
}
