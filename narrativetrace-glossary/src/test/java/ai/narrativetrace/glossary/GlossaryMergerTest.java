/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GlossaryMergerTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC);

  private static final Glossary EMPTY_BILLING =
      new Glossary(
          1,
          Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
          List.of());

  private final GlossaryMerger merger = new GlossaryMerger(FIXED_CLOCK);

  private static HarvestCandidate candidate(String context, String phrase, String site) {
    return new HarvestCandidate(context, phrase, TermKind.NOUN_PHRASE, site, "someIdentifier", 1);
  }

  @Test
  void addsUnseenTermAsHarvestedWithFirstSeenFromClock() {
    var harvest =
        new HarvestResult(
            List.of(candidate("billing", "overdraft account", "OverdraftService.open")));

    var result = merger.merge(EMPTY_BILLING, harvest);

    assertThat(result.newTerms())
        .containsExactly(
            new GlossaryTerm(
                "overdraft account",
                "billing",
                TermKind.NOUN_PHRASE,
                TermStatus.HARVESTED,
                null,
                Map.of(),
                List.of(),
                List.of("OverdraftService.open"),
                LocalDate.of(2026, 8, 11)));
    assertThat(result.glossary().terms()).containsAll(result.newTerms());
    assertThat(result.suppressedAliasUses()).isEmpty();
  }

  @Test
  void collectsAtMostThreeDistinctSourceSitesPerNewTerm() {
    var harvest =
        new HarvestResult(
            List.of(
                candidate("billing", "overdraft account", "A.a"),
                candidate("billing", "overdraft account", "B.b"),
                candidate("billing", "overdraft account", "C.c"),
                candidate("billing", "overdraft account", "D.d")));

    var result = merger.merge(EMPTY_BILLING, harvest);

    assertThat(result.newTerms()).hasSize(1);
    assertThat(result.newTerms().get(0).sources()).containsExactly("A.a", "B.b", "C.c");
  }

  @Test
  void leavesExistingTermCompletelyUntouched() {
    var curated =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.CURATED,
            "Human definition.",
            Map.of("es", "cuenta con descubierto"),
            List.of(),
            List.of("Old.site"),
            LocalDate.of(2020, 1, 1));
    var existing = new Glossary(1, EMPTY_BILLING.contexts(), List.of(curated));
    var harvest = new HarvestResult(List.of(candidate("billing", "overdraft account", "New.site")));

    var result = merger.merge(existing, harvest);

    assertThat(result.newTerms()).isEmpty();
    assertThat(result.glossary()).isEqualTo(existing);
    assertThat(result.glossary().terms().get(0)).isSameAs(curated);
  }

  @Test
  void declaresMissingContextForNewTerms() {
    var harvest = new HarvestResult(List.of(candidate("_unassigned", "ticket", "TicketDesk.open")));

    var result = merger.merge(EMPTY_BILLING, harvest);

    assertThat(result.glossary().contexts()).containsKey("_unassigned").containsKey("billing");
    assertThat(result.glossary().contexts().get("_unassigned").packages()).isEmpty();
    assertThat(result.glossary().contexts().get("_unassigned").description())
        .isEqualTo("Harvested terms not yet mapped to a context");
  }

  @Test
  void declaresOtherMissingContextsWithoutDescription() {
    var harvest = new HarvestResult(List.of(candidate("warehouse", "pallet", "Depot.store")));

    var result = merger.merge(EMPTY_BILLING, harvest);

    assertThat(result.glossary().contexts().get("warehouse").description()).isNull();
  }

  @Test
  void suppressesDeprecatedAliasUseInsteadOfAddingIt() {
    var canonical =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.CURATED,
            null,
            Map.of(),
            List.of(new SynonymAlias("account with overdraft", null)),
            List.of(),
            LocalDate.of(2020, 1, 1));
    var existing = new Glossary(1, EMPTY_BILLING.contexts(), List.of(canonical));
    var aliasUse = candidate("billing", "account with overdraft", "AccountService.open");

    var result = merger.merge(existing, new HarvestResult(List.of(aliasUse)));

    assertThat(result.newTerms()).isEmpty();
    assertThat(result.glossary()).isEqualTo(existing);
    assertThat(result.suppressedAliasUses()).containsExactly(aliasUse);
  }

  @Test
  void aliasSuppressionIsScopedToItsContext() {
    var canonical =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.CURATED,
            null,
            Map.of(),
            List.of(new SynonymAlias("account with overdraft", null)),
            List.of(),
            LocalDate.of(2020, 1, 1));
    var contexts =
        Map.of(
            "billing",
            EMPTY_BILLING.contexts().get("billing"),
            "support",
            new BoundedContext("support", List.of("com.acme.support"), null));
    var existing = new Glossary(1, contexts, List.of(canonical));
    var billingUse = candidate("billing", "account with overdraft", "AccountService.open");
    var supportUse = candidate("support", "account with overdraft", "HelpDesk.describe");

    var result = merger.merge(existing, new HarvestResult(List.of(billingUse, supportUse)));

    assertThat(result.suppressedAliasUses()).containsExactly(billingUse);
    assertThat(result.newTerms())
        .singleElement()
        .satisfies(
            term -> {
              assertThat(term.term()).isEqualTo("account with overdraft");
              assertThat(term.context()).isEqualTo("support");
              assertThat(term.status()).isEqualTo(TermStatus.HARVESTED);
            });
  }

  @Test
  void rejectsNullArguments() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> new GlossaryMerger(null))
        .withMessageContaining("clock");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> merger.merge(null, new HarvestResult(List.of())))
        .withMessageContaining("existing");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> merger.merge(EMPTY_BILLING, null))
        .withMessageContaining("harvest");
  }

  @Test
  void carriesTheAbbreviationsSectionThroughAHarvestThatAddsTerms() {
    var existing =
        new Glossary(
            2,
            Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
            Map.of("fx", "foreign exchange"),
            List.of());
    var harvest =
        new HarvestResult(List.of(candidate("billing", "overdraft account", "Overdraft.open")));

    var merged = merger.merge(existing, harvest).glossary();

    assertThat(merged.abbreviations()).containsExactly(Map.entry("fx", "foreign exchange"));
    assertThat(merged.terms()).hasSize(1);
  }

  @Test
  void aHarvestNeverInventsAnAbbreviation() {
    var harvest =
        new HarvestResult(List.of(candidate("billing", "calc total", "Calculator.calcTotal")));

    var merged = merger.merge(EMPTY_BILLING, harvest).glossary();

    assertThat(merged.abbreviations())
        .as("committing a phrase is not a decision to accept the shorthand inside it")
        .isEmpty();
  }
}
