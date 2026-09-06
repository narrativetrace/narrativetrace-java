/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.glossary.BoundedContext;
import ai.narrativetrace.glossary.ContextResolver;
import ai.narrativetrace.glossary.Glossary;
import ai.narrativetrace.glossary.GlossaryHarvester;
import ai.narrativetrace.glossary.GlossaryJsonReader;
import ai.narrativetrace.glossary.GlossaryJsonWriter;
import ai.narrativetrace.glossary.GlossaryMarkdownRenderer;
import ai.narrativetrace.glossary.GlossaryMerger;
import ai.narrativetrace.glossary.GlossaryTerm;
import ai.narrativetrace.glossary.MergeResult;
import ai.narrativetrace.glossary.NonCanonicalTermIssues;
import ai.narrativetrace.glossary.SynonymAlias;
import ai.narrativetrace.glossary.TermKind;
import ai.narrativetrace.glossary.TermStatus;
import ai.narrativetrace.glossary.VocabularySummaryFormatter;
import ai.narrativetrace.glossary.VocabularyViolation;
import ai.narrativetrace.glossary.VocabularyViolations;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Full-pipeline walkthroughs 1 and 2 of the plan, exercised from outside the production package —
 * only the public API is available here, so package-private leakage would fail to compile.
 */
class GlossaryPipelineEndToEndTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC);

  private static final UnaryOperator<String> PACKAGES = className -> "com.acme.billing";

  private static TraceNode call(String className, String methodName, String... parameters) {
    var captures =
        java.util.Arrays.stream(parameters)
            .map(name -> new ParameterCapture(name, "\"v\"", false))
            .toList();
    return new TraceNode(new MethodSignature(className, methodName, captures), List.of(), null);
  }

  private static final Glossary EMPTY =
      new Glossary(
          1,
          Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
          List.of());

  @Test
  void walkthroughOneHarvestsNewVocabularyFromTraces() {
    var harvester = new GlossaryHarvester(new ContextResolver(EMPTY), PACKAGES);
    var run =
        harvester.harvest(
            List.of(
                new DefaultTraceTree(
                    List.of(
                        call("OverdraftService", "openOverdraftAccount", "overdraftAccountId")))));

    var merge = new GlossaryMerger(FIXED_CLOCK).merge(EMPTY, run);

    assertThat(merge.newTerms())
        .extracting(GlossaryTerm::term)
        .contains("open overdraft account", "overdraft account", "overdraft");
    assertThat(merge.newTerms()).allMatch(term -> term.status() == TermStatus.HARVESTED);
  }

  @Test
  void walkthroughTwoFlagsDeprecatedSynonymPhrasing() {
    var curated = curatedGlossary();
    var harvester = new GlossaryHarvester(new ContextResolver(curated), PACKAGES);
    var run =
        harvester.harvest(
            List.of(
                new DefaultTraceTree(List.of(call("AccountService", "openAccountWithOverdraft")))));

    var merge = new GlossaryMerger(FIXED_CLOCK).merge(curated, run);
    var violations = VocabularyViolations.collect(curated, merge.suppressedAliasUses());

    assertThat(merge.glossary().terms())
        .extracting(GlossaryTerm::term)
        .doesNotContain("account with overdraft");
    assertThat(violations).hasSize(1);
    assertThat(violations.get(0).suggestedIdentifier()).isEqualTo("openOverdraftAccount");
    assertSurfacesReportViolation(merge, violations);
  }

  private static void assertSurfacesReportViolation(
      MergeResult merge, List<VocabularyViolation> violations) {
    var summary =
        new VocabularySummaryFormatter().formatSummary(merge.newTerms().size(), violations);
    assertThat(summary)
        .contains("deprecated synonym in use")
        .contains(
            "openAccountWithOverdraft → use openOverdraftAccount (billing: \"overdraft"
                + " account\")");
    var issues = NonCanonicalTermIssues.from(violations);
    assertThat(issues.get(0).category()).isEqualTo("non-canonical-term");
    assertThat(issues.get(0).element())
        .isEqualTo("billing.AccountService.openAccountWithOverdraft");
    assertThat(new GlossaryMarkdownRenderer().render(merge.glossary()))
        .contains("## billing")
        .contains("account with overdraft — legacy phrasing");
  }

  /** Curated glossary as a developer would commit it, round-tripped through the JSON file form. */
  private static Glossary curatedGlossary() {
    var curatedTerm =
        new GlossaryTerm(
            "overdraft account",
            "billing",
            TermKind.NOUN_PHRASE,
            TermStatus.CURATED,
            "Account permitted to go below zero.",
            Map.of("es", "cuenta con descubierto"),
            List.of(new SynonymAlias("account with overdraft", "legacy phrasing")),
            List.of("OverdraftService.openOverdraftAccount"),
            java.time.LocalDate.of(2026, 8, 11));
    var written =
        new GlossaryJsonWriter().write(new Glossary(1, EMPTY.contexts(), List.of(curatedTerm)));
    return new GlossaryJsonReader().read(written);
  }
}
