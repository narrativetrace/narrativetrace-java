/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.narrativetrace.clarity.DomainVocabulary;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlossaryVocabularyTest {

  private static final Map<String, BoundedContext> CONTEXTS =
      Map.of("trading", new BoundedContext("trading", List.of("com.acme.trading"), null));

  private static GlossaryTerm term(String text, TermKind kind, TermStatus status) {
    return term(text, kind, status, List.of());
  }

  private static GlossaryTerm term(
      String text, TermKind kind, TermStatus status, List<SynonymAlias> synonyms) {
    return new GlossaryTerm(
        text,
        "trading",
        kind,
        status,
        null,
        Map.of(),
        synonyms,
        List.of(),
        LocalDate.of(2020, 1, 1));
  }

  private static Glossary glossaryOf(GlossaryTerm... terms) {
    return new Glossary(1, CONTEXTS, List.of(terms));
  }

  @Test
  void aVerbPhraseDeclaresItsLeadingVerbAndTrailingNouns() {
    var vocabulary =
        GlossaryVocabulary.of(
            glossaryOf(term("settle trade", TermKind.VERB_PHRASE, TermStatus.CURATED)));

    assertThat(vocabulary.isDomainVerb("settle")).isTrue();
    assertThat(vocabulary.isDomainNoun("trade")).isTrue();
    assertThat(vocabulary.isDomainNoun("settle")).isFalse();
    assertThat(vocabulary.isDomainVerb("trade")).isFalse();
  }

  @Test
  void aNounPhraseDeclaresEveryTokenAsANoun() {
    var vocabulary =
        GlossaryVocabulary.of(
            glossaryOf(term("credit tranche", TermKind.NOUN_PHRASE, TermStatus.CURATED)));

    assertThat(vocabulary.isDomainNoun("credit")).isTrue();
    assertThat(vocabulary.isDomainNoun("tranche")).isTrue();
    assertThat(vocabulary.verbs()).isEmpty();
  }

  @Test
  void aWordDeclaresItselfAsANoun() {
    var vocabulary =
        GlossaryVocabulary.of(glossaryOf(term("fx", TermKind.WORD, TermStatus.HARVESTED)));

    assertThat(vocabulary.isDomainNoun("fx")).isTrue();
    assertThat(vocabulary.isAcceptedAbbreviation("fx"))
        .as("a committed word is a domain noun; only the abbreviations section accepts shorthand")
        .isFalse();
  }

  @Test
  void harvestedTermsCountBecauseTheCommitIsTheApproval() {
    var vocabulary =
        GlossaryVocabulary.of(
            glossaryOf(term("fold position", TermKind.VERB_PHRASE, TermStatus.HARVESTED)));

    assertThat(vocabulary.isDomainVerb("fold")).isTrue();
  }

  @Test
  void staleTermsAreNoLongerVocabulary() {
    var vocabulary =
        GlossaryVocabulary.of(
            glossaryOf(term("unwind position", TermKind.VERB_PHRASE, TermStatus.STALE)));

    assertThat(vocabulary.isEmpty()).isTrue();
  }

  @Test
  void templateEntriesAreNotVocabulary() {
    var vocabulary =
        GlossaryVocabulary.of(
            glossaryOf(
                term("settled {amount} for {trade}", TermKind.TEMPLATE, TermStatus.CURATED)));

    assertThat(vocabulary.isEmpty()).isTrue();
  }

  @Test
  void deprecatedSynonymsNeverBecomeVocabulary() {
    var vocabulary =
        GlossaryVocabulary.of(
            glossaryOf(
                term(
                    "tranche",
                    TermKind.WORD,
                    TermStatus.CURATED,
                    List.of(new SynonymAlias("slice", null)))));

    assertThat(vocabulary.isDomainNoun("tranche")).isTrue();
    assertThat(vocabulary.isDomainVerb("slice")).isFalse();
    assertThat(vocabulary.isAcceptedAbbreviation("slice")).isFalse();
  }

  @Test
  void everyBoundedContextContributesBecauseIdentifiersCarryNoPackage() {
    var contexts =
        Map.of(
            "trading",
            new BoundedContext("trading", List.of("com.acme.trading"), null),
            "billing",
            new BoundedContext("billing", List.of("com.acme.billing"), null));
    var billingTerm =
        new GlossaryTerm(
            "invoice",
            "billing",
            TermKind.WORD,
            TermStatus.CURATED,
            null,
            Map.of(),
            List.of(),
            List.of(),
            LocalDate.of(2020, 1, 1));

    var vocabulary =
        GlossaryVocabulary.of(
            new Glossary(
                1,
                contexts,
                List.of(term("tranche", TermKind.WORD, TermStatus.CURATED), billingTerm)));

    assertThat(vocabulary.isDomainNoun("tranche")).isTrue();
    assertThat(vocabulary.isDomainNoun("invoice")).isTrue();
  }

  @Test
  void rejectsANullGlossary() {
    assertThatIllegalArgumentException().isThrownBy(() -> GlossaryVocabulary.of(null));
  }

  @Test
  void aNullDirectoryMeansNoCommittedVocabulary() {
    assertThat(GlossaryVocabulary.from(null)).isEqualTo(DomainVocabulary.empty());
  }

  @Test
  void aDirectoryWithoutAGlossaryMeansNoCommittedVocabulary(@TempDir Path tempDir) {
    assertThat(GlossaryVocabulary.from(tempDir)).isEqualTo(DomainVocabulary.empty());
    assertThat(GlossaryVocabulary.from(tempDir.resolve("absent")))
        .isEqualTo(DomainVocabulary.empty());
  }

  @Test
  void readsTheCommittedGlossaryFromItsDirectory(@TempDir Path tempDir) throws Exception {
    Files.writeString(
        tempDir.resolve("glossary.json"),
        """
        {
          "schemaVersion": 1,
          "contexts": {"trading": {"packages": ["com.acme.trading"]}},
          "terms": [
            {
              "term": "settle trade",
              "context": "trading",
              "kind": "verb-phrase",
              "status": "curated",
              "firstSeen": "2020-01-01"
            }
          ]
        }
        """);

    var vocabulary = GlossaryVocabulary.from(tempDir);

    assertThat(vocabulary.isDomainVerb("settle")).isTrue();
    assertThat(vocabulary.isDomainNoun("trade")).isTrue();
  }

  @Test
  void aMalformedCommittedGlossaryFailsLoudlyRatherThanScoringWithoutIt(@TempDir Path tempDir)
      throws Exception {
    Files.writeString(tempDir.resolve("glossary.json"), "{ not json");

    assertThatIllegalArgumentException().isThrownBy(() -> GlossaryVocabulary.from(tempDir));
  }

  @Test
  void aDirectoryNamedLikeTheGlossaryIsNotAGlossary(@TempDir Path tempDir) throws Exception {
    Files.createDirectory(tempDir.resolve("glossary.json"));

    assertThat(GlossaryVocabulary.from(tempDir)).isEqualTo(DomainVocabulary.empty());
  }

  @Test
  void anUnreadableCommittedGlossaryFailsLoudly(@TempDir Path tempDir) throws Exception {
    var file = tempDir.resolve("glossary.json");
    Files.writeString(file, "{\"schemaVersion\": 1, \"contexts\": {}, \"terms\": []}");
    Files.setPosixFilePermissions(file, java.util.Set.of());
    // A root-owned test run can read anything; the fail-loudly path is only observable when the
    // permission bits actually deny the read.
    org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(file));

    assertThatExceptionOfType(UncheckedIOException.class)
        .isThrownBy(() -> GlossaryVocabulary.from(tempDir))
        .withMessageContaining("glossary.json");
  }

  @Test
  void theAbbreviationsSectionCrossesTheBridgeUnchanged() {
    var glossary =
        new Glossary(2, Map.of(), Map.of("fx", "foreign exchange", "calc", "calculate"), List.of());

    var vocabulary = GlossaryVocabulary.of(glossary);

    assertThat(vocabulary.isAcceptedAbbreviation("fx")).isTrue();
    assertThat(vocabulary.expansionOf("calc")).isEqualTo("calculate");
  }

  /**
   * The defect item 43 exists to remove: before schema 2, committing the phrase {@code calc total}
   * accepted {@code calc} repository-wide and the built-in {@code calc → calculate} hint went
   * silent, though nobody had decided anything about {@code calc}.
   */
  @Test
  void aPhraseTokenDoesNotSuppressTheBuiltInAbbreviationDictionary() {
    var vocabulary =
        GlossaryVocabulary.of(
            glossaryOf(term("calc total", TermKind.NOUN_PHRASE, TermStatus.HARVESTED)));

    assertThat(vocabulary.isDomainNoun("calc")).isTrue();
    assertThat(vocabulary.isAcceptedAbbreviation("calc")).isFalse();
    assertThat(new ai.narrativetrace.clarity.AbbreviationDictionary(vocabulary).lookup("calc"))
        .as("the spell-out hint must survive a committed phrase that happens to contain it")
        .isNotNull();
  }

  @Test
  void aListedAbbreviationIsAcceptedAndSpelledOutFromItsDeclaredExpansion() {
    var glossary = new Glossary(2, Map.of(), Map.of("calc", "calculate"), List.of());
    var vocabulary = GlossaryVocabulary.of(glossary);
    var dictionary = new ai.narrativetrace.clarity.AbbreviationDictionary(vocabulary);

    assertThat(dictionary.lookup("calc")).as("accepted, so never penalized").isNull();
    assertThat(dictionary.projectExpansionOf("calc")).isEqualTo("calculate");
  }
}
