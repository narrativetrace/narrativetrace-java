/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.core.export.JsonEscape;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders the per-run {@code glossary-usage.json} report.
 *
 * <p>INTENT: The build-directory home for everything volatile — new terms of this run, vocabulary
 * violations, and per-term usage counts. These never enter the committed glossary (anti-churn,
 * ADR-012). Output is deterministic: usage aggregates by {@code (context, phrase)} in the
 * harvester's sorted order.
 */
public final class GlossaryUsageReport {

  /**
   * Renders the report document.
   *
   * @param harvest observations of the run; must not be {@code null}
   * @param newTerms terms the merge added; must not be {@code null}
   * @param violations aggregated vocabulary violations; must not be {@code null}
   * @return deterministic JSON text ending in a newline
   */
  public String render(
      HarvestResult harvest, List<GlossaryTerm> newTerms, List<VocabularyViolation> violations) {
    if (harvest == null) {
      throw new IllegalArgumentException("harvest must not be null");
    }
    if (newTerms == null) {
      throw new IllegalArgumentException("newTerms must not be null");
    }
    if (violations == null) {
      throw new IllegalArgumentException("violations must not be null");
    }
    return "{\n"
        + "  \"newTerms\": "
        + array(newTerms.stream().map(GlossaryUsageReport::newTermEntry).toList())
        + ",\n  \"violations\": "
        + array(violations.stream().map(GlossaryUsageReport::violationEntry).toList())
        + ",\n  \"usage\": "
        + array(usageEntries(harvest))
        + "\n}\n";
  }

  /**
   * Renders the report and writes it to the given file, creating parent directories.
   *
   * @param file target file, typically {@code build/narrativetrace/glossary-usage.json}
   * @param harvest observations of the run; must not be {@code null}
   * @param newTerms terms the merge added; must not be {@code null}
   * @param violations aggregated vocabulary violations; must not be {@code null}
   */
  public void write(
      Path file,
      HarvestResult harvest,
      List<GlossaryTerm> newTerms,
      List<VocabularyViolation> violations) {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    var content = render(harvest, newTerms, violations);
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write glossary usage report to " + file, e);
    }
  }

  private static String newTermEntry(GlossaryTerm term) {
    return "{ \"term\": " + quoted(term.term()) + ", \"context\": " + quoted(term.context()) + " }";
  }

  private static String violationEntry(VocabularyViolation violation) {
    var suggested =
        violation.suggestedIdentifier() == null
            ? ""
            : ", \"suggestedIdentifier\": " + quoted(violation.suggestedIdentifier());
    return "{ \"context\": "
        + quoted(violation.context())
        + ", \"alias\": "
        + quoted(violation.alias())
        + ", \"canonicalTerm\": "
        + quoted(violation.canonicalTerm())
        + ", \"site\": "
        + quoted(violation.site())
        + ", \"identifier\": "
        + quoted(violation.identifier())
        + suggested
        + ", \"occurrences\": "
        + violation.occurrences()
        + " }";
  }

  /** Aggregates candidate occurrences by (context, phrase); insertion order is already sorted. */
  private static List<String> usageEntries(HarvestResult harvest) {
    var totals = new LinkedHashMap<String, Integer>();
    for (var candidate : harvest.candidates()) {
      var key = quoted(candidate.context()) + ", \"phrase\": " + quoted(candidate.phrase());
      totals.merge(key, candidate.occurrences(), Integer::sum);
    }
    return totals.entrySet().stream()
        .map(e -> "{ \"context\": " + e.getKey() + ", \"occurrences\": " + e.getValue() + " }")
        .toList();
  }

  private static String array(List<String> entries) {
    if (entries.isEmpty()) {
      return "[]";
    }
    return entries.stream().collect(Collectors.joining(",\n    ", "[\n    ", "\n  ]"));
  }

  private static String quoted(String value) {
    return "\"" + JsonEscape.escape(value) + "\"";
  }
}
