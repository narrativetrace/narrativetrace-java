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

class GlossaryTest {

  private static final BoundedContext BILLING =
      new BoundedContext("billing", List.of("com.acme.billing"), "Money");

  private static GlossaryTerm term(String term, String context, List<SynonymAlias> synonyms) {
    return new GlossaryTerm(
        term,
        context,
        TermKind.NOUN_PHRASE,
        TermStatus.HARVESTED,
        null,
        Map.of(),
        synonyms,
        List.of(),
        LocalDate.of(2026, 8, 11));
  }

  @Test
  void createsGlossaryWithContextsAndTerms() {
    var billing = new BoundedContext("billing", List.of("com.acme.billing"), "Money");
    var term =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.HARVESTED,
            null,
            Map.of(),
            List.of(),
            List.of(),
            LocalDate.of(2026, 8, 11));

    var glossary = new Glossary(1, Map.of("billing", billing), List.of(term));

    assertThat(glossary.schemaVersion()).isEqualTo(1);
    assertThat(glossary.contexts()).containsKey("billing");
    assertThat(glossary.terms()).containsExactly(term);
  }

  @Test
  void rejectsSchemaVersionBelowOne() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(0, Map.of(), List.of()))
        .withMessageContaining("schemaVersion");
  }

  @Test
  void rejectsDuplicateTermKey() {
    var duplicates =
        List.of(
            term("overdraft account", "billing", List.of()),
            term("overdraft account", "billing", List.of()));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(1, Map.of("billing", BILLING), duplicates))
        .withMessageContaining("duplicate");
  }

  @Test
  void allowsSameTermInDifferentContexts() {
    var support = new BoundedContext("support", List.of("com.acme.support"), null);
    var terms = List.of(term("policy", "billing", List.of()), term("policy", "support", List.of()));

    var glossary = new Glossary(1, Map.of("billing", BILLING, "support", support), terms);

    assertThat(glossary.terms()).hasSize(2);
  }

  @Test
  void rejectsTermReferencingUndeclaredContext() {
    var terms = List.of(term("overdraft account", "shipping", List.of()));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(1, Map.of("billing", BILLING), terms))
        .withMessageContaining("shipping");
  }

  @Test
  void rejectsAliasEqualToCanonicalTermInSameContext() {
    var terms =
        List.of(
            term("overdraft account", "billing", List.of(new SynonymAlias("credit line", null))),
            term("credit line", "billing", List.of()));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(1, Map.of("billing", BILLING), terms))
        .withMessageContaining("alias");
  }

  @Test
  void allowsAliasEqualToCanonicalTermInAnotherContext() {
    var support = new BoundedContext("support", List.of("com.acme.support"), null);
    var terms =
        List.of(
            term("overdraft account", "billing", List.of(new SynonymAlias("credit line", null))),
            term("credit line", "support", List.of()));

    var glossary = new Glossary(1, Map.of("billing", BILLING, "support", support), terms);

    assertThat(glossary.terms()).hasSize(2);
  }

  @Test
  void invariantHoldsForEveryConstructedGlossary() {
    var glossary =
        new Glossary(
            1,
            Map.of("billing", BILLING),
            List.of(term("overdraft account", "billing", List.of())));

    assertThat(glossary.invariant()).isTrue();
  }

  @Test
  void canonicalizesTermOrderToContextThenTerm() {
    var support = new BoundedContext("support", List.of(), null);
    var glossary =
        new Glossary(
            1,
            Map.of("billing", BILLING, "support", support),
            List.of(
                term("ticket", "support", List.of()),
                term("refund", "billing", List.of()),
                term("charge", "billing", List.of())));

    assertThat(glossary.terms())
        .extracting(GlossaryTerm::term)
        .containsExactly("charge", "refund", "ticket");
  }

  @Test
  void carriesAnAbbreviationsSection() {
    var glossary =
        new Glossary(2, Map.of(), Map.of("fx", "foreign exchange", "calc", "calculate"), List.of());

    assertThat(glossary.abbreviations())
        .containsEntry("fx", "foreign exchange")
        .containsEntry("calc", "calculate");
  }

  @Test
  void defaultsTheAbbreviationsSectionToEmpty() {
    assertThat(new Glossary(1, Map.of(), List.of()).abbreviations()).isEmpty();
  }

  @Test
  void canonicalizesAbbreviationKeysToLowercase() {
    var glossary = new Glossary(2, Map.of(), Map.of("FX", "foreign exchange"), List.of());

    assertThat(glossary.abbreviations()).containsExactly(Map.entry("fx", "foreign exchange"));
  }

  @Test
  void rejectsTwoAbbreviationsThatCanonicalizeToTheSameKey() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Glossary(
                    2, Map.of(), Map.of("FX", "foreign exchange", "fx", "effects"), List.of()))
        .withMessageContaining("fx");
  }

  @Test
  void rejectsAMultiTokenAbbreviation() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(2, Map.of(), Map.of("f x", "foreign exchange"), List.of()))
        .withMessageContaining("single token");
  }

  @Test
  void rejectsABlankAbbreviation() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(2, Map.of(), Map.of("  ", "foreign exchange"), List.of()))
        .withMessageContaining("abbreviation");
  }

  @Test
  void rejectsABlankExpansion() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(2, Map.of(), Map.of("fx", " "), List.of()))
        .withMessageContaining("expansion");
  }

  @Test
  void trimsSurroundingWhitespaceFromBothSides() {
    var glossary = new Glossary(2, Map.of(), Map.of(" fx ", " foreign exchange "), List.of());

    assertThat(glossary.abbreviations()).containsExactly(Map.entry("fx", "foreign exchange"));
  }

  @Test
  void canonicalizesTheSchemaVersionUpWhenAbbreviationsAreDeclared() {
    var glossary = new Glossary(1, Map.of(), Map.of("fx", "foreign exchange"), List.of());

    assertThat(glossary.schemaVersion()).isEqualTo(2);
  }

  @Test
  void canonicalizesTheSchemaVersionDownWhenNoAbbreviationsAreDeclared() {
    var glossary = new Glossary(2, Map.of(), Map.of(), List.of());

    assertThat(glossary.schemaVersion())
        .as("the version describes the shape the content needs, and this content needs schema 1")
        .isEqualTo(1);
  }

  @Test
  void stillRejectsAnImpossibleDeclaredSchemaVersion() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Glossary(0, Map.of(), Map.of("fx", "forex"), List.of()))
        .withMessageContaining("schemaVersion");
  }
}
