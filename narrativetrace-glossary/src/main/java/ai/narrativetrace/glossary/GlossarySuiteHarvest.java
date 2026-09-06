/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityIssue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * One suite run's glossary harvest: read, harvest, merge, write, report.
 *
 * <p>INTENT: The orchestration the suite hook needs (ADR-012 Phase 5) in one glossary-owned
 * collaborator, so JUnit extensions stay free of glossary mechanics: read the committed {@code
 * glossary.json} (or start empty), harvest the run's traces, merge additively, rewrite {@code
 * glossary.json} and {@code glossary.md}, write the volatile usage report, and hand back the
 * console vocabulary line plus {@code non-canonical-term} clarity issues.
 *
 * <p>The vocabulary check (deprecated-synonym violations) fires only when {@code glossary.json}
 * exists before the run: a project without a committed glossary never receives {@code
 * non-canonical-term} issues.
 */
public final class GlossarySuiteHarvest {

  private final Path glossaryDir;
  private final Path usageReportFile;
  private final UnaryOperator<String> packageOf;
  private final Clock clock;

  /**
   * @param glossaryDir directory holding {@code glossary.json} and {@code glossary.md}, typically
   *     the repository root
   * @param usageReportFile target for the volatile usage report, typically {@code
   *     build/narrativetrace/glossary-usage.json}
   * @param packageOf maps a simple class name to its package ({@code null} when unknown)
   * @param clock source of {@code firstSeen} dates for new terms
   */
  public GlossarySuiteHarvest(
      Path glossaryDir, Path usageReportFile, UnaryOperator<String> packageOf, Clock clock) {
    if (glossaryDir == null) {
      throw new IllegalArgumentException("glossaryDir must not be null");
    }
    if (usageReportFile == null) {
      throw new IllegalArgumentException("usageReportFile must not be null");
    }
    if (packageOf == null) {
      throw new IllegalArgumentException("packageOf must not be null");
    }
    if (clock == null) {
      throw new IllegalArgumentException("clock must not be null");
    }
    this.glossaryDir = glossaryDir;
    this.usageReportFile = usageReportFile;
    this.packageOf = packageOf;
    this.clock = clock;
  }

  /** Outcome of one run: the console summary block and the run's vocabulary clarity issues. */
  public record Result(String summary, List<ClarityIssue> issues) {}

  /**
   * Executes the harvest for one suite run.
   *
   * @param trees trace trees accumulated over the suite
   * @return console summary line plus vocabulary clarity issues of this run
   * @throws IOException if the glossary files or usage report cannot be written
   */
  public Result run(List<TraceTree> trees) throws IOException {
    return execute(trees, false);
  }

  /**
   * Executes the harvest for one scan of compiled classes.
   *
   * <p>Identical to {@link #run} except that {@code @Narrated} / {@code @OnError} templates are
   * harvested — safe here and only here, because a statically scanned signature carries the raw
   * annotation text rather than narration with runtime values interpolated.
   *
   * @param trees synthetic trees from {@link GlossaryStaticScanner}
   * @return console summary line plus vocabulary clarity issues of this scan
   * @throws IOException if the glossary files or usage report cannot be written
   */
  public Result runStatic(List<TraceTree> trees) throws IOException {
    return execute(trees, true);
  }

  private Result execute(List<TraceTree> trees, boolean staticMode) throws IOException {
    if (trees == null) {
      throw new IllegalArgumentException("trees must not be null");
    }
    var glossaryFileExists = Files.exists(glossaryDir.resolve("glossary.json"));
    var existing = readGlossary();
    var harvester = new GlossaryHarvester(new ContextResolver(existing), packageOf);
    var harvest = staticMode ? harvester.harvestStatic(trees) : harvester.harvest(trees);
    var merge = new GlossaryMerger(clock).merge(existing, harvest);
    // The vocabulary check only runs against a committed glossary: without a glossary.json
    // present before the run there is no curated vocabulary to violate, and no
    // non-canonical-term issues may reach the clarity report or its CI gate.
    var violations =
        glossaryFileExists
            ? VocabularyViolations.collect(existing, merge.suppressedAliasUses())
            : List.<VocabularyViolation>of();

    writeGlossaryFiles(merge.glossary());
    new GlossaryUsageReport().write(usageReportFile, harvest, merge.newTerms(), violations);

    var summary =
        new VocabularySummaryFormatter().formatSummary(merge.newTerms().size(), violations);
    return new Result(summary, NonCanonicalTermIssues.from(violations));
  }

  private Glossary readGlossary() throws IOException {
    var glossaryFile = glossaryDir.resolve("glossary.json");
    if (!Files.exists(glossaryFile)) {
      return new Glossary(1, Map.of(), List.of());
    }
    return new GlossaryJsonReader().read(Files.readString(glossaryFile, StandardCharsets.UTF_8));
  }

  private void writeGlossaryFiles(Glossary glossary) throws IOException {
    Files.createDirectories(glossaryDir);
    Files.writeString(
        glossaryDir.resolve("glossary.json"),
        new GlossaryJsonWriter().write(glossary),
        StandardCharsets.UTF_8);
    Files.writeString(
        glossaryDir.resolve("glossary.md"),
        new GlossaryMarkdownRenderer().render(glossary),
        StandardCharsets.UTF_8);
  }
}
