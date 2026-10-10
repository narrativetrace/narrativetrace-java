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
package ai.narrativetrace.tooling.frameworks;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The wiring lines of every {@link Wiring.Snippet} row, read from {@code wiring-snippets.md} on
 * this library's own classpath.
 *
 * <p>INTENT: the doctor's fix line must carry the exact lines a project adds, and those lines must
 * never be typed — they are a compiled, tested fixture's. The resource holds one {@code ## <row
 * id>} section per row, each wrapping a fenced block in the same {@code <!-- snippet: path region=…
 * -->} markers the documentation uses, so the build's {@code snippetSync} writes the fixture's
 * current text into it and {@code snippetCheck} fails the build when the two drift. The jar then
 * carries the text to wherever the doctor runs, with no repository in sight.
 */
public final class WiringSnippets {

  /** The resource's name, next to this class. */
  public static final String RESOURCE = "wiring-snippets.md";

  private static final String HEADING = "## ";
  private static final String FENCE = "```";

  private static final Map<String, Entry> ENTRIES = load();

  private WiringSnippets() {}

  /**
   * One row's snippet: the fixture the marker names, its region ({@code null} for a whole file),
   * and the fenced block's body.
   */
  public record Entry(String fixture, String region, String body) {}

  /** The wiring lines of the row with this id. */
  public static String text(String rowId) {
    return entry(rowId).body();
  }

  /**
   * The parsed entry for this row.
   *
   * @throws IllegalStateException when the resource has no section for the row — a table row
   *     without its snippet is a build defect the drift test exists to catch, never a runtime case
   */
  public static Entry entry(String rowId) {
    Entry entry = ENTRIES.get(rowId);
    if (entry == null) {
      throw new IllegalStateException(RESOURCE + " has no section for row " + rowId);
    }
    return entry;
  }

  /** Every section, by row id, in document order. */
  public static Map<String, Entry> entries() {
    return ENTRIES;
  }

  private static Map<String, Entry> load() {
    try (InputStream in = WiringSnippets.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " is missing from the classpath");
      }
      return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Parses the resource's text: each {@code ## id} section's snippet marker and the fenced block
   * right after it. A fenced block is skipped whole wherever it appears, so a heading INSIDE one is
   * body, never a new section. Line-based on purpose — no pattern walks the document.
   */
  static Map<String, Entry> parse(String markdown) {
    List<String> lines = List.of(markdown.split("\n", -1));
    Map<String, Entry> entries = new LinkedHashMap<>();
    int i = 0;
    while (i < lines.size()) {
      String line = lines.get(i);
      if (line.startsWith(FENCE)) {
        i = closingFence(lines, i + 1) + 1;
      } else if (line.startsWith(HEADING)) {
        i = readSection(lines, i + 1, line.substring(HEADING.length()).strip(), entries);
      } else {
        i++;
      }
    }
    return Collections.unmodifiableMap(entries);
  }

  /**
   * Reads one section from the line after its heading up to its marked block, recording the entry.
   * Returns where the scan resumes: after the block, or at the first heading or unmarked fence.
   */
  private static int readSection(
      List<String> lines, int from, String id, Map<String, Entry> entries) {
    int i = from;
    while (i < lines.size()
        && !lines.get(i).startsWith(HEADING)
        && !lines.get(i).startsWith(FENCE)) {
      Optional<Entry> marker = marker(lines.get(i));
      if (marker.isPresent() && i + 1 < lines.size() && lines.get(i + 1).startsWith(FENCE)) {
        int close = closingFence(lines, i + 2);
        if (close == lines.size()) {
          return close;
        }
        String body = String.join("\n", lines.subList(i + 2, close));
        entries.putIfAbsent(
            id,
            new Entry(
                marker.get().fixture(), marker.get().region(), body.isEmpty() ? "" : body + "\n"));
        return close + 1;
      }
      i++;
    }
    return i;
  }

  /**
   * The index of the first line at or after {@code from} that closes a fence, or the number of
   * lines when none does — an unterminated block, which is never an entry.
   */
  private static int closingFence(List<String> lines, int from) {
    int i = from;
    while (i < lines.size() && !lines.get(i).startsWith(FENCE)) {
      i++;
    }
    return i;
  }

  /**
   * {@code <!-- snippet: path [region=name] -->} as an entry with an empty body; empty for any
   * other line.
   */
  private static Optional<Entry> marker(String line) {
    String trimmed = line.strip();
    if (!trimmed.startsWith("<!--") || !trimmed.endsWith("-->")) {
      return Optional.empty();
    }
    String[] words = trimmed.substring(4, trimmed.length() - 3).strip().split("\\s+");
    if (words.length < 2 || !"snippet:".equals(words[0])) {
      return Optional.empty();
    }
    String region = null;
    for (int w = 2; w < words.length; w++) {
      if (words[w].startsWith("region=")) {
        region = words[w].substring("region=".length());
      }
    }
    return Optional.of(new Entry(words[1], region, ""));
  }
}
