/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADV-2026-10-30-1 — the suite-end glossary harvest reads the committed {@code glossary.json} and
 * lets a parse failure out of {@code afterAll}, so an unreadable input file turns a green suite
 * red: an observability failure becomes an application failure.
 *
 * <p>INTENT: the product already decided this question, twice, in this very module. {@code
 * NarrativeTraceExtension.projectVocabulary} reads the SAME file behind {@code catch (Exception)}
 * with the comment "a bad glossary must not fail the suite", and {@code GlossaryHarvestStep} itself
 * degrades a write failure ({@code GlossaryHarvestStepTest#glossaryWriteFailureIsReportedWithout
 * FailingTheSuite}). Only the harvest's READ half is unguarded: {@code GlossarySuiteHarvest.run}
 * declares {@code IOException}, {@code GlossaryHarvestStep.run} catches exactly that, and {@code
 * JsonParser} reports every syntax error as an {@code IllegalArgumentException} — which is not an
 * {@code IOException} and so runs straight through the extension into JUnit.
 *
 * <p><b>@llmNote</b> Reachable from ordinary repository life, not from an attack: {@code
 * glossary.json} is a committed, hand-edited file, so a merge-conflict marker, a truncated write or
 * a trailing comma is the normal way it goes bad. The failure surfaces as a container-level error
 * on {@code afterAll} with a character offset for its only message, which names neither the
 * glossary nor the file — the harvest's own write-failure path, by contrast, prints "Failed to
 * write glossary" and carries on.
 */
class MalformedCommittedGlossaryTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);

  private static final UnaryOperator<String> PACKAGES = className -> "com.acme.billing";

  @TempDir Path glossaryDir;
  @TempDir Path outputDir;

  private final ByteArrayOutputStream console = new ByteArrayOutputStream();

  @Test
  @DisplayName(
      "ADV-2026-10-30-1: a conflict marker in the committed glossary may not fail the suite")
  void aMalformedCommittedGlossaryDegradesInsteadOfFailingTheSuite() throws Exception {
    Files.writeString(
        glossaryDir.resolve("glossary.json"),
        "<<<<<<< HEAD\n{\n  \"schemaVersion\": 1,\n  \"contexts\": {},\n  \"terms\": []\n}\n",
        StandardCharsets.UTF_8);

    assertThatCode(this::harvest).doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "ADV-2026-10-30-1: a trailing comma in the committed glossary may not fail the suite")
  void aTrailingCommaInTheCommittedGlossaryDegradesInsteadOfFailingTheSuite() throws Exception {
    Files.writeString(
        glossaryDir.resolve("glossary.json"),
        "{\n  \"schemaVersion\": 1,\n  \"contexts\": {},\n  \"terms\": [],\n}\n",
        StandardCharsets.UTF_8);

    assertThatCode(this::harvest).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("ADV-2026-10-30-1: an unreadable glossary leaves the suite's clarity issues empty")
  void anUnreadableGlossaryYieldsNoClarityIssues() throws Exception {
    Files.writeString(
        glossaryDir.resolve("glossary.json"), "not json at all", StandardCharsets.UTF_8);

    var issues = harvest();

    assertThat(issues).as("the harvest degrades, exactly as a write failure does").isEmpty();
  }

  private List<?> harvest() {
    return GlossaryHarvestStep.into(
            glossaryDir, outputDir.resolve("glossary-usage.json"), PACKAGES, FIXED_CLOCK)
        .run(
            List.of(traceOf("OverdraftService", "openOverdraftAccount")),
            new PrintStream(console, true, StandardCharsets.UTF_8));
  }

  private static TraceTree traceOf(String className, String methodName) {
    var node =
        new TraceNode(new MethodSignature(className, methodName, List.of()), List.of(), null);
    return new DefaultTraceTree(List.of(node));
  }
}
