/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The structural delta between two {@code .nt} artifacts (see {@code StructuralTraceRenderer}).
 *
 * <p>INTENT: The comparison engine for the test-loop feedback surfaces — the post-run console delta
 * line, the failure delta against the last green artifact, and approval-mode verification. Sameness
 * is byte equality of the artifact: the renderer is deterministic, so byte-identical means
 * behaviorally identical, and any difference is real change worth surfacing.
 */
public final class StructuralDelta {

  private final String baseline;
  private final String current;
  private final boolean unchanged;

  private StructuralDelta(String baseline, String current) {
    this.baseline = baseline;
    this.current = current;
    this.unchanged = baseline.equals(current);
  }

  /**
   * Compares a baseline artifact (last green or approved) against the current one.
   *
   * @throws IllegalArgumentException if either document is null — absence of a baseline is a
   *     caller-level state (a new scenario), not a delta
   */
  public static StructuralDelta between(String baseline, String current) {
    if (baseline == null) {
      throw new IllegalArgumentException("baseline must not be null");
    }
    if (current == null) {
      throw new IllegalArgumentException("current must not be null");
    }
    return new StructuralDelta(baseline, current);
  }

  /** True iff the two artifacts are byte-identical — the scenario's structure did not change. */
  public boolean unchanged() {
    return unchanged;
  }

  /**
   * Compact per-signature call-count changes, e.g. {@code +4 calls
   * CurrencyConverter.toBaseCurrency}; empty when {@link #unchanged()}.
   */
  public String summary() {
    if (unchanged) {
      return "";
    }
    var changes = countChanges();
    if (changes.isEmpty()) {
      return "structure changed";
    }
    return changes.entrySet().stream()
        .map(entry -> formatChange(entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(", "));
  }

  /**
   * Full-document line diff in the conventional format: {@code -} removed, {@code +} added, one
   * leading space on unchanged context lines; empty when {@link #unchanged()}. Artifacts are small
   * (one test scenario), so no hunk elision is applied — the whole document stays readable.
   */
  public String diff() {
    if (unchanged) {
      return "";
    }
    return LineDiff.unified(baseline, current);
  }

  private Map<String, Integer> countChanges() {
    var counts = new LinkedHashMap<String, Integer>();
    callSignatures(current).forEach(signature -> counts.merge(signature, 1, Integer::sum));
    callSignatures(baseline).forEach(signature -> counts.merge(signature, -1, Integer::sum));
    counts.values().removeIf(count -> count == 0);
    return counts;
  }

  /**
   * Call lines are {@code - Class.method(params)} at any indent; fork markers and blanks are not
   * calls.
   */
  private static List<String> callSignatures(String document) {
    return document
        .lines()
        .map(String::stripLeading)
        .filter(line -> line.startsWith("- "))
        .map(StructuralDelta::signatureOf)
        .toList();
  }

  private static String signatureOf(String callLine) {
    var open = callLine.indexOf('(');
    return open < 0 ? callLine.substring(2) : callLine.substring(2, open);
  }

  private static String formatChange(String signature, int count) {
    var magnitude = Math.abs(count);
    var noun = magnitude == 1 ? " call " : " calls ";
    return (count > 0 ? "+" : "-") + magnitude + noun + signature;
  }
}
