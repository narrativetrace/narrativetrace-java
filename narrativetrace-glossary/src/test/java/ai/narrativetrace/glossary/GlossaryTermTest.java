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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GlossaryTermTest {

  @Test
  void createsTermWithAllFields() {
    var term =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.CURATED,
            "Account permitted to go below zero up to an agreed limit.",
            Map.of("es", "cuenta con descubierto"),
            List.of(new SynonymAlias("account with overdraft", "legacy v1 API phrasing")),
            List.of("billing.OverdraftService.openOverdraftAccount"),
            LocalDate.of(2026, 8, 11));

    assertThat(term.term()).isEqualTo("overdraft account");
    assertThat(term.context()).isEqualTo("billing");
    assertThat(term.kind()).isEqualTo(TermKind.NOUN_PHRASE);
    assertThat(term.status()).isEqualTo(TermStatus.CURATED);
    assertThat(term.definition()).contains("below zero");
    assertThat(term.translations()).containsEntry("es", "cuenta con descubierto");
    assertThat(term.synonyms()).hasSize(1);
    assertThat(term.sources()).hasSize(1);
    assertThat(term.firstSeen()).isEqualTo(LocalDate.of(2026, 8, 11));
  }

  @Test
  void rejectsBlankTerm() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> minimalTerm(" ", "billing"))
        .withMessageContaining("term");
  }

  @Test
  void rejectsBlankContext() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> minimalTerm("overdraft account", ""))
        .withMessageContaining("context");
  }

  @Test
  void rejectsNullKindStatusOrFirstSeen() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new GlossaryTerm(
                    "a",
                    "b",
                    null,
                    TermStatus.HARVESTED,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    LocalDate.EPOCH))
        .withMessageContaining("kind");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new GlossaryTerm(
                    "a",
                    "b",
                    TermKind.WORD,
                    null,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    LocalDate.EPOCH))
        .withMessageContaining("status");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new GlossaryTerm(
                    "a",
                    "b",
                    TermKind.WORD,
                    TermStatus.HARVESTED,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    null))
        .withMessageContaining("firstSeen");
  }

  @Test
  void copiesCollectionsDefensively() {
    var sources = new ArrayList<String>();
    sources.add("billing.OverdraftService.openOverdraftAccount");
    var term =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.HARVESTED,
            null,
            Map.of(),
            List.of(),
            sources,
            LocalDate.EPOCH);

    sources.clear();

    assertThat(term.sources()).hasSize(1);
  }

  @Test
  void synonymAliasRejectsBlankAlias() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SynonymAlias(" ", null))
        .withMessageContaining("alias");
  }

  @Test
  void synonymAliasAllowsNullNote() {
    assertThat(new SynonymAlias("account with overdraft", null).note()).isNull();
  }

  private static GlossaryTerm minimalTerm(String term, String context) {
    return new GlossaryTerm(
        term,
        context,
        TermKind.NOUN_PHRASE,
        TermStatus.HARVESTED,
        null,
        Map.of(),
        List.of(),
        List.of(),
        LocalDate.EPOCH);
  }
}
