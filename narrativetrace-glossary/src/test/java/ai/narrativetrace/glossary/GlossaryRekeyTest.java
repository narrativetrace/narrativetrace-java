/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GlossaryRekeyTest {

  private static final LocalDate SEEN = LocalDate.of(2026, 8, 15);

  private static Glossary glossaryOf(GlossaryTerm... terms) {
    return new Glossary(
        1,
        Map.of("library", new BoundedContext("library", List.of("com.acme.library"), null)),
        List.of(terms));
  }

  private static GlossaryTerm term(
      String phrase, TermKind kind, Map<String, String> translations, String... sources) {
    return new GlossaryTerm(
        phrase,
        "library",
        kind,
        translations.isEmpty() ? TermStatus.HARVESTED : TermStatus.CURATED,
        null,
        translations,
        List.of(),
        List.of(sources),
        SEEN);
  }

  private static HarvestResult harvestOf(String... phrases) {
    return new HarvestResult(
        java.util.Arrays.stream(phrases)
            .map(
                phrase ->
                    new HarvestCandidate("library", phrase, TermKind.WORD, "Book.site", "site", 1))
            .toList());
  }

  @Test
  void aCuratedPropertyReadMovesToTheNounItReadsWithItsTranslationIntact() {
    var glossary =
        glossaryOf(
            term("get author", TermKind.VERB_PHRASE, Map.of("es", "autor"), "Book.getAuthor"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("author"));

    assertThat(migrated.terms())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.term()).isEqualTo("author");
              assertThat(t.kind()).isEqualTo(TermKind.WORD);
              assertThat(t.translations()).containsEntry("es", "autor");
            });
  }

  @Test
  void aMovedEntryMergesIntoTheSuccessorThatAlreadyExists() {
    var glossary =
        glossaryOf(
            term("author", TermKind.WORD, Map.of("es", "autor"), "Book.getAuthor"),
            term("get author", TermKind.VERB_PHRASE, Map.of("de", "Autor"), "Book.getAuthor"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("author"));

    assertThat(migrated.terms())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.term()).isEqualTo("author");
              assertThat(t.translations())
                  .containsEntry("es", "autor")
                  .containsEntry("de", "Autor");
              assertThat(t.sources()).containsExactly("Book.getAuthor");
            });
  }

  @Test
  void anObjectNounStartingWithAFunctionWordMovesToWhatIsLeftOfIt() {
    var glossary =
        glossaryOf(
            term(
                "per night",
                TermKind.NOUN_PHRASE,
                Map.of("es", "por noche"),
                "Room.pricePerNight"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("night"));

    assertThat(migrated.terms())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.term()).isEqualTo("night");
              assertThat(t.translations()).containsEntry("es", "por noche");
            });
  }

  @Test
  void aTermWhoseEverySourceWasLanguagePlumbingRetires() {
    var glossary =
        glossaryOf(
            term(
                "copy", TermKind.VERB_PHRASE, Map.of("es", "ejemplar"), "Book.copy", "Member.copy"),
            term("component 1", TermKind.NOUN_PHRASE, Map.of(), "Book.component1"),
            term("value of", TermKind.VERB_PHRASE, Map.of(), "CustomerTier.valueOf"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("author"));

    assertThat(migrated.terms()).isEmpty();
  }

  /** The near miss: a phrase the harvest still produces is current, whatever it begins with. */
  @Test
  void aPhraseTheHarvestStillProducesIsNeverMoved() {
    var glossary =
        glossaryOf(
            term(
                "get or create account",
                TermKind.VERB_PHRASE,
                Map.of("es", "obtener o crear cuenta"),
                "Ledger.getOrCreateAccount"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("get or create account"));

    assertThat(migrated.terms())
        .extracting(GlossaryTerm::term)
        .containsExactly("get or create account");
  }

  @Test
  void aTermWhoseSuccessorTheHarvestDoesNotProduceStaysWhereItIs() {
    var glossary =
        glossaryOf(
            term("get author", TermKind.VERB_PHRASE, Map.of("es", "autor"), "Book.getAuthor"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("title"));

    assertThat(migrated.terms()).extracting(GlossaryTerm::term).containsExactly("get author");
  }

  @Test
  void aTermWhoseCodeWasSimplyDeletedIsKept() {
    var glossary =
        glossaryOf(term("overdraft", TermKind.WORD, Map.of("es", "descubierto"), "Old.x"));

    var migrated = GlossaryRekey.migrate(glossary, harvestOf("author"));

    assertThat(migrated.terms()).extracting(GlossaryTerm::term).containsExactly("overdraft");
  }

  @Test
  void rejectsNullInput() {
    assertThatIllegalArgumentException().isThrownBy(() -> GlossaryRekey.migrate(null, harvestOf()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GlossaryRekey.migrate(glossaryOf(), null));
  }
}
