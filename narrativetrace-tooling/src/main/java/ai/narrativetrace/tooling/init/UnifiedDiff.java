/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.init;

import java.util.List;

/**
 * The smallest unified diff that tells the truth about one file.
 *
 * <p>INTENT: {@code --dry-run} has to SHOW what would change, and the tooling library takes zero
 * dependencies. The algorithm is deliberately not Myers: the common prefix and suffix are trimmed
 * and everything between them is shown as removed-then-added, in one hunk with three lines of
 * context.
 *
 * <p><b>@llmNote</b> The result is a correct unified diff, not a MINIMAL one. For what the
 * installer actually edits — a delimited section inside a file, or a page it rewrites whole — the
 * trimmed middle IS the change. Trading minimality away buys a diff with no quadratic table in it,
 * which is what makes this safe on a file of any size.
 *
 * <p><b>@llmNote</b> A line keeps a carriage return it had, so a change of line endings shows as a
 * changed line rather than as nothing at all.
 */
final class UnifiedDiff {

  private static final int CONTEXT = 3;

  private static final String NO_NEWLINE = "\\ No newline at end of file";

  private UnifiedDiff() {}

  /**
   * The diff of one file, or {@code ""} when the two texts are equal.
   *
   * @param path the label both sides are named with
   * @param before the whole file before, {@code ""} when it did not exist
   * @param after the whole file after, {@code ""} when it is being deleted
   */
  static String render(String path, String before, String after) {
    if (before.equals(after)) {
      return "";
    }
    List<String> old = lines(before);
    List<String> current = lines(after);
    int prefix = commonPrefix(old, current);
    int suffix = commonSuffix(old, current, prefix);
    StringBuilder out = new StringBuilder();
    out.append("--- ").append(before.isEmpty() ? "/dev/null" : "a/" + path).append('\n');
    out.append("+++ ").append(after.isEmpty() ? "/dev/null" : "b/" + path).append('\n');
    appendHunk(out, old, current, prefix, suffix, before, after);
    return out.toString();
  }

  private static void appendHunk(
      StringBuilder out,
      List<String> old,
      List<String> current,
      int prefix,
      int suffix,
      String before,
      String after) {
    int start = Math.max(0, prefix - CONTEXT);
    int oldEnd = Math.min(old.size(), old.size() - suffix + CONTEXT);
    int newEnd = Math.min(current.size(), current.size() - suffix + CONTEXT);
    out.append("@@ -")
        .append(header(start, oldEnd - start, old.isEmpty()))
        .append(" +")
        .append(header(start, newEnd - start, current.isEmpty()))
        .append(" @@\n");
    for (int i = start; i < prefix; i++) {
      out.append(' ').append(old.get(i)).append('\n');
    }
    appendSide(out, old, prefix, old.size() - suffix, '-', before);
    appendSide(out, current, prefix, current.size() - suffix, '+', after);
    for (int i = old.size() - suffix; i < oldEnd; i++) {
      out.append(' ').append(old.get(i)).append('\n');
    }
  }

  /** The {@code start,count} half of a hunk header: a side with no lines starts at 0. */
  private static String header(int start, int count, boolean empty) {
    return empty ? "0,0" : (start + 1) + "," + count;
  }

  private static void appendSide(
      StringBuilder out, List<String> lines, int from, int to, char sign, String text) {
    for (int i = from; i < to; i++) {
      out.append(sign).append(lines.get(i)).append('\n');
      if (i == lines.size() - 1 && !text.endsWith("\n")) {
        out.append(NO_NEWLINE).append('\n');
      }
    }
  }

  /** Lines without their newline; a carriage return stays, so an ending change is visible. */
  private static List<String> lines(String text) {
    return MarkedBlock.splitKeepingTerminators(text).stream()
        .map(line -> line.endsWith("\n") ? line.substring(0, line.length() - 1) : line)
        .toList();
  }

  private static int commonPrefix(List<String> old, List<String> current) {
    int i = 0;
    while (i < old.size() && i < current.size() && old.get(i).equals(current.get(i))) {
      i++;
    }
    return i;
  }

  private static int commonSuffix(List<String> old, List<String> current, int prefix) {
    int i = 0;
    while (i < old.size() - prefix
        && i < current.size() - prefix
        && old.get(old.size() - 1 - i).equals(current.get(current.size() - 1 - i))) {
      i++;
    }
    return i;
  }
}
