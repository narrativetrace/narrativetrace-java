/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import ai.narrativetrace.core.export.JsonEscape;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * JSON exporter for clarity results.
 *
 * <p>INTENT: The Gradle plugin and other automation layers consume this stable machine-readable
 * form instead of parsing Markdown reports.
 *
 * <p>Two overloads are provided: {@link #export(Map)} for the common case where scenario names are
 * unique, and {@link #export(List)} when the same scenario name may appear more than once (e.g.,
 * two test classes that both have a test named "customer places order"). The list overload
 * preserves all entries and is therefore the correct choice for suite-level aggregation.
 *
 * <p><b>Schema 1.2</b> (additive over 1.1): every scenario object carries an {@code elements} array
 * of per-element teaching notes ({@code kind}, {@code element}, {@code score}, {@code note}).
 * Consumers of 1.0/1.1 files keep working — the field is purely additive.
 */
public final class ClarityJsonExporter {

  /**
   * Exports clarity results keyed by unique scenario name.
   *
   * <p>When the same name appears in multiple test classes use {@link #export(List)} instead to
   * avoid silent deduplication.
   */
  public String export(Map<String, ClarityResult> results) {
    return export(List.copyOf(results.entrySet()));
  }

  /**
   * Exports clarity results from an ordered list of (scenarioName, result) pairs.
   *
   * <p>Duplicate scenario names are preserved as separate JSON array entries, which is required for
   * correct suite-level aggregation across test classes that share display names.
   */
  public String export(List<Map.Entry<String, ClarityResult>> results) {
    return export(results, List.of());
  }

  /**
   * Exports clarity results plus suite-level issues that belong to the whole run rather than to any
   * single scenario (e.g. {@code non-canonical-term} vocabulary violations from the glossary
   * harvest).
   *
   * <p>Suite issues land in the always-present top-level {@code suiteIssues} array (schema 1.1);
   * they never appear inside a scenario's {@code issues} and therefore never affect scenario
   * scores.
   */
  public String export(
      List<Map.Entry<String, ClarityResult>> results, List<ClarityIssue> suiteIssues) {
    var sb = new StringBuilder();
    sb.append("{\"version\":\"1.2\",\"scenarios\":[");
    appendJoined(
        sb, results, (out, entry) -> appendScenario(out, entry.getKey(), entry.getValue()));
    sb.append("],\"suiteIssues\":[");
    appendJoined(sb, suiteIssues, this::appendIssue);
    sb.append("]}");
    return sb.toString();
  }

  /**
   * Writes {@code items} as the <em>body</em> of a JSON array: each item through {@code writer},
   * separated by a single comma, with no brackets of its own — the caller has already opened the
   * array and closes it afterwards.
   *
   * <p>INTENT: Scenarios, issues and element notes were three copies of the same
   * comma-between-but-not-before loop, each with its own {@code first} flag. One loop, three
   * writers.
   *
   * @param <T> the item type
   * @param sb the buffer being built, positioned just after the opening bracket
   * @param items the items to write, in order; an empty list writes nothing at all
   * @param writer appends one item's JSON object
   */
  private <T> void appendJoined(
      StringBuilder sb, List<T> items, BiConsumer<StringBuilder, T> writer) {
    boolean first = true;
    for (var item : items) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      writer.accept(sb, item);
    }
  }

  private void appendScenario(StringBuilder sb, String name, ClarityResult result) {
    sb.append("{\"name\":\"").append(escapeJson(name)).append('"');
    sb.append(",\"overallScore\":").append(formatScore(result.overallScore()));
    sb.append(",\"methodNameScore\":").append(formatScore(result.methodNameScore()));
    sb.append(",\"classNameScore\":").append(formatScore(result.classNameScore()));
    sb.append(",\"parameterNameScore\":").append(formatScore(result.parameterNameScore()));
    sb.append(",\"structuralScore\":").append(formatScore(result.structuralScore()));
    sb.append(",\"cohesionScore\":").append(formatScore(result.cohesionScore()));
    sb.append(",\"issues\":[");
    appendJoined(sb, result.issues(), this::appendIssue);
    sb.append("],\"elements\":[");
    appendJoined(sb, result.elementNotes(), this::appendElement);
    sb.append("]}");
  }

  private void appendElement(StringBuilder sb, ElementNote note) {
    sb.append("{\"kind\":\"").append(escapeJson(note.kind())).append('"');
    sb.append(",\"element\":\"").append(escapeJson(note.element())).append('"');
    sb.append(",\"score\":").append(formatScore(note.score()));
    sb.append(",\"note\":\"").append(escapeJson(note.note())).append('"');
    sb.append('}');
  }

  private void appendIssue(StringBuilder sb, ClarityIssue issue) {
    sb.append("{\"category\":\"").append(escapeJson(issue.category())).append('"');
    sb.append(",\"element\":\"").append(escapeJson(issue.element())).append('"');
    sb.append(",\"suggestion\":\"").append(escapeJson(issue.suggestion())).append('"');
    sb.append(",\"severity\":\"").append(issue.severity().name()).append('"');
    sb.append(",\"occurrences\":").append(issue.occurrences());
    sb.append(",\"impactScore\":").append(formatScore(issue.impactScore()));
    sb.append('}');
  }

  private String formatScore(double score) {
    return String.format(Locale.US, "%.2f", score);
  }

  private String escapeJson(String s) {
    return JsonEscape.escape(s);
  }
}
