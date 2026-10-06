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

import java.util.Optional;

/**
 * The one line that says a copied {@code SKILL.md} is ours and which carrier it came from.
 *
 * <p>INTENT: the installer must be able to tell a page it wrote from a page somebody else wrote,
 * years later and with no other state. That is what makes an upgrade safe (overwrite ours), a
 * foreign directory safe (refuse), and an uninstall safe (delete only ours).
 *
 * <p><b>@llmNote</b> The line goes AFTER the YAML frontmatter, never before it: a comment above the
 * opening {@code ---} stops the frontmatter from parsing, and every skill runtime reads that
 * frontmatter first.
 *
 * <p><b>@llmNote</b> Matching is anchored at column 0, the same rule the managed block's markers
 * follow, so a page that quotes the line in an example is not mistaken for an installed page.
 */
final class Provenance {

  static final String PREFIX = "<!-- installed by narrativetrace init from ";

  static final String SUFFIX = " — edit the catalogue, not this file -->";

  private static final String FRONTMATTER_FENCE = "---";

  private Provenance() {}

  /** The provenance line for one carrier coordinate. */
  static String line(String coordinate) {
    return PREFIX + coordinate + SUFFIX;
  }

  /** The coordinate a page was installed from, or empty when the page is not ours. */
  static Optional<String> coordinateIn(String page) {
    return MarkedBlock.splitKeepingTerminators(page).stream()
        .map(Provenance::content)
        .flatMap(line -> coordinateOf(line).stream())
        .findFirst();
  }

  /** The coordinate one line carries, or empty when the line is not a provenance line. */
  private static Optional<String> coordinateOf(String line) {
    if (!isProvenance(line)) {
      return Optional.empty();
    }
    String coordinate = line.substring(PREFIX.length(), line.length() - SUFFIX.length()).trim();
    return coordinate.isEmpty() ? Optional.empty() : Optional.of(coordinate);
  }

  /**
   * The page with exactly one provenance line for {@code coordinate}, placed after the frontmatter
   * — replacing any line a previous install left, so stamping twice is stamping once.
   */
  static String stamp(String page, String coordinate) {
    String eol = MarkedBlock.eolOf(page);
    StringBuilder out = new StringBuilder();
    int insertAfter = frontmatterEnd(page);
    int number = 0;
    for (String raw : MarkedBlock.splitKeepingTerminators(page)) {
      number++;
      if (!isProvenance(content(raw))) {
        out.append(raw);
      }
      if (number == insertAfter) {
        out.append(line(coordinate)).append(eol);
      }
    }
    return insertAfter == 0 ? line(coordinate) + eol + eol + out : out.toString();
  }

  /**
   * A whole line, not a fragment: the length guard matters because {@link #PREFIX} ends with a
   * space and {@link #SUFFIX} starts with one, so the two can OVERLAP in a hand-edited line.
   */
  private static boolean isProvenance(String line) {
    return line.length() >= PREFIX.length() + SUFFIX.length()
        && line.startsWith(PREFIX)
        && line.endsWith(SUFFIX);
  }

  /** The 1-based line number of the frontmatter's closing fence, or 0 when there is none. */
  private static int frontmatterEnd(String page) {
    var lines = MarkedBlock.splitKeepingTerminators(page);
    if (lines.isEmpty() || !content(lines.get(0)).equals(FRONTMATTER_FENCE)) {
      return 0;
    }
    for (int i = 1; i < lines.size(); i++) {
      if (content(lines.get(i)).equals(FRONTMATTER_FENCE)) {
        return i + 1;
      }
    }
    return 0;
  }

  /** A line without its terminator. */
  private static String content(String line) {
    String text = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
  }
}
