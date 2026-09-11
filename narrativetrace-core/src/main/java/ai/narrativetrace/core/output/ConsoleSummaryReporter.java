/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import ai.narrativetrace.api.event.TraceLoss;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats suite and test summaries for console output.
 *
 * <p>INTENT: Keep JUnit extensions free of console formatting details while still producing a
 * stable, human-readable summary footer.
 */
public final class ConsoleSummaryReporter {

  public String formatTestResult(String testName, long durationMs) {
    return "    ✓ " + testName + " (" + durationMs + "ms)";
  }

  public String formatTestResult(String testName, long durationMs, double clarityScore) {
    return "    ✓ "
        + testName
        + " ("
        + durationMs
        + "ms, clarity: "
        + String.format("%.2f", clarityScore)
        + ")";
  }

  public String formatTestFailure(
      String testName,
      long durationMs,
      String exceptionType,
      String location,
      String traceFilePath) {
    return "    ✗ "
        + testName
        + " ("
        + durationMs
        + "ms)\n"
        + "      > "
        + exceptionType
        + " at "
        + location
        + "\n"
        + "      > Full trace: "
        + traceFilePath;
  }

  public String formatSuiteHeader() {
    return "NarrativeTrace — Recording test narratives\n";
  }

  /**
   * One line summarizing every scenario's structural status against its last green artifact, e.g.
   * {@code 4 scenarios unchanged · 1 changed: "Weekend trip…" (+4 calls
   * CurrencyConverter.toBaseCurrency)}.
   */
  public String formatDeltaLine(List<ScenarioDelta> deltas) {
    var unchanged = countKind(deltas, ScenarioDelta.Kind.UNCHANGED);
    var fresh = countKind(deltas, ScenarioDelta.Kind.NEW);
    var changed = deltas.stream().filter(d -> d.kind() == ScenarioDelta.Kind.CHANGED).toList();
    var segments = new ArrayList<String>();
    if (unchanged > 0) {
      segments.add(withNoun(segments, unchanged) + " unchanged");
    }
    if (fresh > 0) {
      segments.add(withNoun(segments, fresh) + " new");
    }
    if (!changed.isEmpty()) {
      segments.add(withNoun(segments, changed.size()) + " changed: " + describeChanged(changed));
    }
    return String.join(" · ", segments);
  }

  private static long countKind(List<ScenarioDelta> deltas, ScenarioDelta.Kind kind) {
    return deltas.stream().filter(d -> d.kind() == kind).count();
  }

  /**
   * The word "scenario(s)" rides on the first segment only: {@code 4 scenarios unchanged · 1 new}.
   */
  private static String withNoun(List<String> segments, long count) {
    if (!segments.isEmpty()) {
      return String.valueOf(count);
    }
    return count + (count == 1 ? " scenario" : " scenarios");
  }

  private static String describeChanged(List<ScenarioDelta> changed) {
    return changed.stream()
        .map(delta -> "\"" + truncate(delta.scenario()) + "\" (" + delta.summary() + ")")
        .collect(java.util.stream.Collectors.joining(", "));
  }

  /** Scenario names are capped so one changed scenario cannot flood the one-line summary. */
  private static String truncate(String scenario) {
    if (scenario.length() <= 32) {
      return scenario;
    }
    return scenario.substring(0, 32).stripTrailing() + "…";
  }

  public String formatSuiteFooter(int scenarioCount, String outputPath) {
    return "\nNarrativeTrace — Suite complete\n"
        + "  "
        + scenarioCount
        + " scenarios recorded\n"
        + "  Reports: "
        + outputPath;
  }

  public String formatSuiteFooter(
      int scenarioCount, String outputPath, List<Double> clarityScores) {
    return formatSuiteFooter(scenarioCount, outputPath, clarityScores, TraceLoss.none());
  }

  /**
   * The suite footer, with one line naming what the run lost when anything was lost.
   *
   * <p>INTENT: A short trace must never be indistinguishable from a quiet one. The buffered path
   * sheds events under load, refuses async scopes above the adoption cap, and discards spans whose
   * owning request had already reset before the work finished; all three are best-effort by design,
   * and all three are invisible without this line. It is omitted entirely at zero loss, so the
   * ordinary footer is unchanged.
   */
  public String formatSuiteFooter(
      int scenarioCount, String outputPath, List<Double> clarityScores, TraceLoss loss) {
    int high = 0, moderate = 0, low = 0;
    for (var score : clarityScores) {
      if (score >= 0.7) high++;
      else if (score >= 0.4) moderate++;
      else low++;
    }
    int total = clarityScores.size();
    int highPct = Math.round(100f * high / total);
    int moderatePct = Math.round(100f * moderate / total);
    int lowPct = Math.round(100f * low / total);
    return "\nNarrativeTrace — Suite complete\n"
        + "  "
        + scenarioCount
        + " scenarios recorded\n"
        + "  Clarity: "
        + highPct
        + "% high | "
        + moderatePct
        + "% moderate | "
        + lowPct
        + "% low\n"
        + lossLine(loss)
        + "  Reports: "
        + outputPath;
  }

  /**
   * The loss line, or nothing at all when the run lost nothing across all three sources.
   *
   * <p><b>@llmNote</b> {@code discardedSpans} is checked independently of {@link TraceLoss#any()}:
   * that method deliberately excludes it (see its {@code @edgeCase}) because a discard alone does
   * not make {@code this} tree incomplete. This footer still surfaces it as its own clause when
   * nonzero — an operational signal worth showing, kept out of the trigger that other renderers
   * hang their own "is this trace incomplete" decision off.
   */
  private static String lossLine(TraceLoss loss) {
    if (!loss.any() && loss.discardedSpans() == 0) {
      return "";
    }
    var parts = new ArrayList<String>();
    if (loss.droppedEvents() > 0) {
      parts.add(count(loss.droppedEvents(), "event") + " dropped (buffer full)");
    }
    if (loss.refusedScopes() > 0) {
      parts.add(
          count(loss.refusedScopes(), "async scope")
              + " refused (cap or uncollected), "
              + count(loss.refusedSpans(), "span"));
    }
    if (loss.discardedSpans() > 0) {
      parts.add(count(loss.discardedSpans(), "late span") + " discarded (parent reset)");
    }
    return "  Incomplete: " + String.join(", ", parts) + "\n";
  }

  private static String count(long value, String noun) {
    return value + " " + noun + (value == 1 ? "" : "s");
  }
}
