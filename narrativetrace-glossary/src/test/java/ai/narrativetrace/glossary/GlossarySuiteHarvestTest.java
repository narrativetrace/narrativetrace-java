/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlossarySuiteHarvestTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC);

  private static final UnaryOperator<String> PACKAGES = className -> "com.acme.billing";

  @TempDir Path glossaryDir;
  @TempDir Path outputDir;

  private GlossarySuiteHarvest harvestInto(Path dir) {
    return new GlossarySuiteHarvest(
        dir, outputDir.resolve("glossary-usage.json"), PACKAGES, FIXED_CLOCK);
  }

  private static TraceTree traceOf(String className, String methodName, String... parameters) {
    var captures =
        Arrays.stream(parameters).map(name -> new ParameterCapture(name, "\"v\"", false)).toList();
    var node = new TraceNode(new MethodSignature(className, methodName, captures), List.of(), null);
    return new DefaultTraceTree(List.of(node));
  }

  @Test
  void firstRunCreatesGlossaryJsonAndMarkdown() throws Exception {
    var trees = List.of(traceOf("OverdraftService", "openOverdraftAccount", "overdraftAccountId"));

    harvestInto(glossaryDir).run(trees);

    assertThat(glossaryDir.resolve("glossary.json")).exists();
    assertThat(glossaryDir.resolve("glossary.md")).exists();
  }

  @Test
  void harvestingTheSameSuiteTwiceIsByteIdentical() throws Exception {
    var trees = List.of(traceOf("OverdraftService", "openOverdraftAccount", "overdraftAccountId"));
    var harvest = harvestInto(glossaryDir);

    harvest.run(trees);
    var jsonAfterFirst = Files.readString(glossaryDir.resolve("glossary.json"));
    var markdownAfterFirst = Files.readString(glossaryDir.resolve("glossary.md"));
    harvest.run(trees);

    assertThat(glossaryDir.resolve("glossary.json")).hasContent(jsonAfterFirst);
    assertThat(glossaryDir.resolve("glossary.md")).hasContent(markdownAfterFirst);
  }

  @Test
  void deprecatedSynonymUseIsReportedAndSuppressedFromGlossary() throws Exception {
    writeCuratedGlossaryWithSynonym();
    var trees = List.of(traceOf("AccountService", "openAccountWithOverdraft"));

    var result = harvestInto(glossaryDir).run(trees);

    assertThat(result.summary()).contains("deprecated synonym in use");
    assertThat(result.issues()).isNotEmpty();
    assertThat(result.issues().get(0).category()).isEqualTo("non-canonical-term");
    assertThat(glossaryDir.resolve("glossary.json"))
        .content()
        .doesNotContain("\"term\": \"account with overdraft\"");
    assertThat(outputDir.resolve("glossary-usage.json"))
        .content()
        .contains("openAccountWithOverdraft");
  }

  @Test
  void vocabularyCheckIsSkippedWhenNoGlossaryFileExists() throws Exception {
    var trees = List.of(traceOf("AccountService", "openAccountWithOverdraft"));

    var result = harvestInto(glossaryDir).run(trees);

    assertThat(result.issues()).isEmpty();
    assertThat(result.summary()).doesNotContain("deprecated synonym in use");
  }

  @Test
  void staticRunCommitsTemplateTermsThatATraceRunWouldNotSee() throws Exception {
    var node =
        new TraceNode(
            new MethodSignature(
                "OverdraftService", "openAccount", List.of(), "Opening for {customerId}", null),
            List.of(),
            null);
    var trees = List.<TraceTree>of(new DefaultTraceTree(List.of(node)));

    harvestInto(glossaryDir).runStatic(trees);

    assertThat(glossaryDir.resolve("glossary.json"))
        .content()
        .contains("Opening for {customerId}")
        .contains("template");
  }

  @Test
  void traceRunLeavesTemplatesOutOfTheCommittedGlossary() throws Exception {
    var node =
        new TraceNode(
            new MethodSignature(
                "OverdraftService", "openAccount", List.of(), "Opening for C-123", null),
            List.of(),
            null);
    var trees = List.<TraceTree>of(new DefaultTraceTree(List.of(node)));

    harvestInto(glossaryDir).run(trees);

    assertThat(glossaryDir.resolve("glossary.json")).content().doesNotContain("C-123");
  }

  @Test
  void malformedCommittedGlossaryFailsFastWithPreciseError() throws Exception {
    Files.writeString(glossaryDir.resolve("glossary.json"), "{ not json");
    var trees = List.of(traceOf("OverdraftService", "openOverdraftAccount"));
    var harvest = harvestInto(glossaryDir);

    assertThatThrownBy(() -> harvest.run(trees)).isInstanceOf(IllegalArgumentException.class);
    assertThat(glossaryDir.resolve("glossary.md")).doesNotExist();
  }

  @Test
  void guardsRejectNullArguments() {
    var usage = outputDir.resolve("glossary-usage.json");
    var harvest = harvestInto(glossaryDir);

    assertThatThrownBy(() -> new GlossarySuiteHarvest(null, usage, PACKAGES, FIXED_CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GlossarySuiteHarvest(glossaryDir, null, PACKAGES, FIXED_CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GlossarySuiteHarvest(glossaryDir, usage, null, FIXED_CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GlossarySuiteHarvest(glossaryDir, usage, PACKAGES, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> harvest.run(null)).isInstanceOf(IllegalArgumentException.class);
  }

  private void writeCuratedGlossaryWithSynonym() throws Exception {
    var curated =
        new Glossary(
            1,
            Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
            List.of(
                new GlossaryTerm(
                    "overdraft account",
                    "billing",
                    TermKind.NOUN_PHRASE,
                    TermStatus.CURATED,
                    null,
                    Map.of(),
                    List.of(new SynonymAlias("account with overdraft", "legacy phrasing")),
                    List.of(),
                    LocalDate.of(2026, 8, 11))));
    Files.writeString(
        glossaryDir.resolve("glossary.json"), new GlossaryJsonWriter().write(curated));
  }
}
