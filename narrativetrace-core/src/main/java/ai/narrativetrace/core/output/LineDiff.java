/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * Longest-common-subsequence line diff in the conventional format: {@code -} removed, {@code +}
 * added, one leading space on unchanged context lines.
 *
 * <p>INTENT: The rendering half of {@link StructuralDelta} — kept free of any {@code .nt} format
 * knowledge so the delta class owns what a change <em>means</em> and this class owns how a change
 * <em>reads</em>. Inputs are whole documents; ties between a deletion and an insertion resolve to
 * the deletion so removed lines always precede their replacements.
 */
final class LineDiff {

  private LineDiff() {}

  /**
   * True when every line of {@code current} appears in {@code baseline}, in order — i.e. the
   * current document differs from the baseline only by omission.
   *
   * <p>INTENT: The question to ask of a run known to be incomplete. Equality would fail on the
   * absences the best-effort path caused; containment tolerates exactly those and nothing else, so
   * an added, renamed or reordered line still comes back false.
   */
  static boolean isSubsequence(String baseline, String current) {
    var baselineLines = baseline.lines().toList();
    var currentLines = current.lines().toList();
    int matched = 0;
    for (var line : baselineLines) {
      if (matched < currentLines.size() && currentLines.get(matched).equals(line)) {
        matched++;
      }
    }
    return matched == currentLines.size();
  }

  /**
   * The diff with lines matched on {@code key}, so two lines whose keys agree are context even when
   * their bytes differ; {@code context} renders such a pair from (baseline line, current line).
   * Removed lines print as the baseline wrote them, added lines as the current document does.
   */
  static String unified(
      String baseline, String current, UnaryOperator<String> key, BinaryOperator<String> context) {
    var baselineLines = baseline.lines().toList();
    var currentLines = current.lines().toList();
    var baselineKeys = baselineLines.stream().map(key).toList();
    var currentKeys = currentLines.stream().map(key).toList();
    var table = lcsTable(baselineKeys, currentKeys);
    return render(
        table, new Sides(baselineLines, baselineKeys, currentLines, currentKeys), context);
  }

  /** Both documents, each as its printed lines and the keys those lines are matched on. */
  private record Sides(
      List<String> baseline,
      List<String> baselineKeys,
      List<String> current,
      List<String> currentKeys) {}

  private static int[][] lcsTable(List<String> baseline, List<String> current) {
    var table = new int[baseline.size() + 1][current.size() + 1];
    for (int i = baseline.size() - 1; i >= 0; i--) {
      for (int j = current.size() - 1; j >= 0; j--) {
        table[i][j] =
            baseline.get(i).equals(current.get(j))
                ? table[i + 1][j + 1] + 1
                : Math.max(table[i + 1][j], table[i][j + 1]);
      }
    }
    return table;
  }

  private static String render(int[][] table, Sides sides, BinaryOperator<String> context) {
    var baseline = sides.baseline();
    var current = sides.current();
    var sb = new StringBuilder();
    int i = 0;
    int j = 0;
    while (i < baseline.size() && j < current.size()) {
      if (sides.baselineKeys().get(i).equals(sides.currentKeys().get(j))) {
        sb.append(' ').append(context.apply(baseline.get(i++), current.get(j++))).append('\n');
      } else if (table[i + 1][j] >= table[i][j + 1]) {
        sb.append('-').append(baseline.get(i++)).append('\n');
      } else {
        sb.append('+').append(current.get(j++)).append('\n');
      }
    }
    while (i < baseline.size()) {
      sb.append('-').append(baseline.get(i++)).append('\n');
    }
    while (j < current.size()) {
      sb.append('+').append(current.get(j++)).append('\n');
    }
    return sb.toString();
  }
}
