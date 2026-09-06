/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoTracesTest {

  @AfterEach
  void clearProperties() {
    System.clearProperty("narrativetrace.demo.translationDir");
    System.clearProperty("narrativetrace.demo.locale");
    System.clearProperty("narrativetrace.glossary.path");
  }

  private static DefaultTraceTree tree() {
    return new DefaultTraceTree(
        List.of(
            new TraceNode(
                new MethodSignature("OrderService", "placeOrder", List.of()),
                List.of(),
                new TraceOutcome.Returned("\"order-1\""))));
  }

  private static void configureTranslation(Path dir, Path glossaryDir) throws Exception {
    var glossary =
        """
        {
          "schemaVersion": 1,
          "contexts": {
            "shop": { "packages": ["ai.narrativetrace.examples"], "description": "demo" }
          },
          "terms": [
            {
              "term": "place order",
              "context": "shop",
              "kind": "verb-phrase",
              "status": "curated",
              "translations": { "es": "realizar pedido" },
              "sources": [],
              "firstSeen": "2026-08-15"
            }
          ]
        }
        """;
    var glossaryFile = glossaryDir.resolve("glossary.json");
    Files.writeString(glossaryFile, glossary);
    System.setProperty("narrativetrace.demo.translationDir", dir.toString());
    System.setProperty("narrativetrace.demo.locale", "es");
    System.setProperty("narrativetrace.glossary.path", glossaryFile.toString());
  }

  @Test
  void writesATranslatedFileNamedAfterTheScenarioWhenTranslationIsConfigured(
      @TempDir Path dir, @TempDir Path glossaryDir) throws Exception {
    configureTranslation(dir, glossaryDir);

    DemoTraces.capture("Scenario 1: Successful Order + Async Notification", tree());

    try (var entries = Files.list(dir)) {
      var files = entries.toList();
      assertThat(files).hasSize(1);
      var file = files.get(0);
      assertThat(file.getFileName().toString())
          .matches("\\d\\d_scenario_1_successful_order_async_notification\\.md");
      assertThat(file)
          .content()
          .startsWith("=== Scenario 1: Successful Order + Async Notification ===\n");
      assertThat(file).content().contains("OrderService.realizar pedido (placeOrder) ()");
      assertThat(file).content().contains("\"order-1\"");
    }
  }

  @Test
  void captureOrderSurvivesAlphabeticalFileSorting(@TempDir Path dir, @TempDir Path glossaryDir)
      throws Exception {
    configureTranslation(dir, glossaryDir);

    DemoTraces.capture("Unrefactored: Player Joins World", tree());
    DemoTraces.capture("Refactored: Player Joins World", tree());

    try (var entries = Files.list(dir)) {
      var names = entries.map(p -> p.getFileName().toString()).sorted().toList();
      assertThat(names.get(0)).contains("unrefactored");
      assertThat(names.get(1)).contains("_refactored");
    }
  }

  @Test
  void doesNothingWhenTranslationIsNotConfigured(@TempDir Path dir) throws Exception {
    DemoTraces.capture("Scenario 1", tree());

    try (var entries = Files.list(dir)) {
      assertThat(entries.toList()).isEmpty();
    }
  }

  @Test
  void doesNothingWhenOnlyTheDirButNoLocaleIsConfigured(@TempDir Path dir) throws Exception {
    System.setProperty("narrativetrace.demo.translationDir", dir.toString());

    DemoTraces.capture("Scenario 1", tree());

    try (var entries = Files.list(dir)) {
      assertThat(entries.toList()).isEmpty();
    }
  }

  @Test
  void anEmptyTraceWritesNothing(@TempDir Path dir, @TempDir Path glossaryDir) throws Exception {
    configureTranslation(dir, glossaryDir);

    DemoTraces.capture("Scenario 1", new DefaultTraceTree(List.of()));

    try (var entries = Files.list(dir)) {
      assertThat(entries.toList()).isEmpty();
    }
  }

  @Test
  void aMissingGlossaryNeverBreaksTheDemoRunAndWritesNothing(@TempDir Path dir) throws Exception {
    System.setProperty("narrativetrace.demo.translationDir", dir.toString());
    System.setProperty("narrativetrace.demo.locale", "es");

    DemoTraces.capture("Scenario 1", tree());

    try (var entries = Files.list(dir)) {
      assertThat(entries.toList()).isEmpty();
    }
  }

  @Test
  void anUnwritableDirNeverBreaksTheDemoRun(@TempDir Path dir, @TempDir Path glossaryDir)
      throws Exception {
    configureTranslation(dir, glossaryDir);
    var blocked = dir.resolve("occupied");
    Files.writeString(blocked, "a plain file, not a directory");
    System.setProperty("narrativetrace.demo.translationDir", blocked.toString());

    DemoTraces.capture("Scenario 1", tree());
  }
}
