/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityIssue;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.glossary.BoundedContext;
import ai.narrativetrace.glossary.Glossary;
import ai.narrativetrace.glossary.GlossaryJsonWriter;
import ai.narrativetrace.glossary.GlossaryTerm;
import ai.narrativetrace.glossary.SynonymAlias;
import ai.narrativetrace.glossary.TermKind;
import ai.narrativetrace.glossary.TermStatus;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

class GlossaryHarvestStepTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC);

  private static final UnaryOperator<String> PACKAGES = className -> "com.acme.billing";

  @TempDir Path glossaryDir;
  @TempDir Path outputDir;

  private final ByteArrayOutputStream console = new ByteArrayOutputStream();

  private GlossaryHarvestStep enabledStep() {
    return GlossaryHarvestStep.into(
        glossaryDir, outputDir.resolve("glossary-usage.json"), PACKAGES, FIXED_CLOCK);
  }

  private static TraceTree traceOf(String className, String methodName, String... parameters) {
    var captures =
        Arrays.stream(parameters).map(name -> new ParameterCapture(name, "\"v\"", false)).toList();
    var node = new TraceNode(new MethodSignature(className, methodName, captures), List.of(), null);
    return new DefaultTraceTree(List.of(node));
  }

  @Test
  void writesGlossaryFilesIntoTheConfiguredDirectory() {
    var trees = List.of(traceOf("OverdraftService", "openOverdraftAccount", "overdraftAccountId"));

    enabledStep().run(trees, new PrintStream(console, true, StandardCharsets.UTF_8));

    assertThat(glossaryDir.resolve("glossary.json")).exists();
    assertThat(glossaryDir.resolve("glossary.md")).exists();
  }

  @Test
  void disabledStepWritesNothingAndReportsNoIssues() {
    var trees = List.of(traceOf("OverdraftService", "openOverdraftAccount", "overdraftAccountId"));

    var issues =
        GlossaryHarvestStep.disabled()
            .run(trees, new PrintStream(console, true, StandardCharsets.UTF_8));

    assertThat(issues).isEmpty();
    assertThat(glossaryDir.resolve("glossary.json")).doesNotExist();
    assertThat(glossaryDir.resolve("glossary.md")).doesNotExist();
    assertThat(console.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void deprecatedSynonymUseIsPrintedAndReturnedAsAClarityIssue() throws Exception {
    writeCuratedGlossaryWithSynonym();
    var trees = List.of(traceOf("AccountService", "openAccountWithOverdraft"));

    var issues = enabledStep().run(trees, new PrintStream(console, true, StandardCharsets.UTF_8));

    assertThat(console.toString(StandardCharsets.UTF_8)).contains("deprecated synonym in use");
    assertThat(issues).extracting(ClarityIssue::category).containsOnly("non-canonical-term");
  }

  @Test
  void glossaryWriteFailureIsReportedWithoutFailingTheSuite() throws Exception {
    var blocked = outputDir.resolve("blocked");
    Files.writeString(blocked, "not a directory");
    var step =
        GlossaryHarvestStep.into(
            blocked, outputDir.resolve("glossary-usage.json"), PACKAGES, FIXED_CLOCK);
    var errors = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));

    try {
      var issues =
          step.run(
              List.of(traceOf("OverdraftService", "openOverdraftAccount")),
              new PrintStream(console, true, StandardCharsets.UTF_8));

      assertThat(issues).isEmpty();
      assertThat(errors.toString(StandardCharsets.UTF_8)).contains("Failed to write glossary");
    } finally {
      System.setErr(originalErr);
    }
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
