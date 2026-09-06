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

class GlossaryTranslatorTest {

  private static GlossaryTerm term(
      String term, String context, TermKind kind, Map<String, String> translations) {
    return new GlossaryTerm(
        term,
        context,
        kind,
        TermStatus.CURATED,
        null,
        translations,
        List.of(),
        List.of(),
        LocalDate.of(2026, 8, 11));
  }

  private static Glossary glossaryOf(GlossaryTerm... terms) {
    return new Glossary(
        1,
        Map.of(
            "billing", new BoundedContext("billing", List.of("com.acme.billing"), null),
            "insurance", new BoundedContext("insurance", List.of("com.acme.insurance"), null)),
        List.of(terms));
  }

  @Test
  void translatesAnExactPhraseEntryForTheLocale() {
    var glossary =
        glossaryOf(
            term(
                "overdraft account",
                "billing",
                TermKind.NOUN_PHRASE,
                Map.of("es", "cuenta con descubierto")));
    var translator = new GlossaryTranslator(glossary);

    var result = translator.translate("overdraft account", "billing", "es");

    assertThat(result.text()).isEqualTo("cuenta con descubierto");
    assertThat(result.complete()).isTrue();
  }

  @Test
  void fallsBackToPerTokenWordEntriesWhenNoExactPhraseMatches() {
    var glossary =
        glossaryOf(
            term("payment", "billing", TermKind.WORD, Map.of("es", "pago")),
            term("declined", "billing", TermKind.WORD, Map.of("es", "rechazado")));
    var translator = new GlossaryTranslator(glossary);

    var result = translator.translate("payment declined", "billing", "es");

    assertThat(result.text()).isEqualTo("pago rechazado");
    assertThat(result.complete()).isTrue();
  }

  @Test
  void partialTokenCoverageRendersMixedTextAndReportsIncomplete() {
    var glossary = glossaryOf(term("payment", "billing", TermKind.WORD, Map.of("es", "pago")));
    var translator = new GlossaryTranslator(glossary);

    var result = translator.translate("payment declined", "billing", "es");

    assertThat(result.text()).isEqualTo("pago declined");
    assertThat(result.complete()).isFalse();
  }

  @Test
  void unknownPhraseRendersAsIsAndReportsIncomplete() {
    var translator = new GlossaryTranslator(glossaryOf());

    var result = translator.translate("payment declined", "billing", "es");

    assertThat(result.text()).isEqualTo("payment declined");
    assertThat(result.complete()).isFalse();
  }

  @Test
  void aTermFromAnotherContextNeverApplies() {
    var glossary = glossaryOf(term("policy", "insurance", TermKind.WORD, Map.of("es", "póliza")));
    var translator = new GlossaryTranslator(glossary);

    var result = translator.translate("policy", "billing", "es");

    assertThat(result.text()).isEqualTo("policy");
    assertThat(result.complete()).isFalse();
  }

  @Test
  void exactEntryWithoutTheLocaleFallsThroughToTokenEntries() {
    var glossary =
        glossaryOf(
            term(
                "payment declined",
                "billing",
                TermKind.NOUN_PHRASE,
                Map.of("fr", "paiement refusé")),
            term("payment", "billing", TermKind.WORD, Map.of("es", "pago")),
            term("declined", "billing", TermKind.WORD, Map.of("es", "rechazado")));
    var translator = new GlossaryTranslator(glossary);

    var result = translator.translate("payment declined", "billing", "es");

    assertThat(result.text()).isEqualTo("pago rechazado");
    assertThat(result.complete()).isTrue();
  }

  @Test
  void rejectsNullOrBlankArguments() {
    var translator = new GlossaryTranslator(glossaryOf());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> translator.translate(null, "billing", "es"))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> translator.translate("x", " ", "es"))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> translator.translate("x", "billing", ""))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GlossaryTranslator(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
