/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityIssue;
import ai.narrativetrace.glossary.GlossarySuiteHarvest;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The suite-end glossary harvest, as the JUnit 5 extension sees it.
 *
 * <p>INTENT: Keep glossary mechanics out of {@link NarrativeTraceExtension} (ADR-012 Phase 5). The
 * extension holds one of these and calls {@link #run} once per suite, beside the clarity report.
 */
final class GlossaryHarvestStep {

  /** The configured harvest, or {@code null} when glossary harvesting is switched off. */
  private final GlossarySuiteHarvest harvest;

  private GlossaryHarvestStep(GlossarySuiteHarvest harvest) {
    this.harvest = harvest;
  }

  /**
   * Creates a step that does nothing — the default, since harvesting writes the glossary outside
   * the build directory and must be opted into explicitly.
   */
  static GlossaryHarvestStep disabled() {
    return new GlossaryHarvestStep(null);
  }

  /**
   * Creates a step that harvests into {@code glossaryDir}.
   *
   * @param glossaryDir directory holding the committed {@code glossary.json} / {@code glossary.md}
   * @param usageReportFile target for the volatile usage report, under the build directory
   * @param packageOf maps a simple class name to its package ({@code null} when unknown)
   * @param clock source of {@code firstSeen} dates for newly harvested terms
   */
  static GlossaryHarvestStep into(
      Path glossaryDir, Path usageReportFile, UnaryOperator<String> packageOf, Clock clock) {
    return new GlossaryHarvestStep(
        new GlossarySuiteHarvest(glossaryDir, usageReportFile, packageOf, clock));
  }

  /**
   * Harvests the suite's traces, rewrites the glossary files, and prints the vocabulary summary.
   *
   * @param trees trace trees accumulated over the suite
   * @param out console stream receiving the vocabulary summary
   * @return {@code non-canonical-term} issues to fold into the clarity report; empty when
   *     harvesting is switched off or the glossary could not be written
   */
  List<ClarityIssue> run(List<TraceTree> trees, PrintStream out) {
    if (harvest == null) {
      return List.of();
    }
    try {
      var result = harvest.run(trees);
      out.println(result.summary());
      return result.issues();
    } catch (IOException e) {
      System.err.println("Failed to write glossary: " + e.getMessage());
      return List.of();
    }
  }
}
