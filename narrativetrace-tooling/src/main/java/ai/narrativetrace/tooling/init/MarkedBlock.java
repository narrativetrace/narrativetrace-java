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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The delimited section the installer owns inside a consumer's file, and every edit that section
 * can undergo.
 *
 * <p>INTENT: one place decides what counts as a marker. Everything the installer writes into a file
 * it did not create lives between {@code <!-- narrativetrace:start … -->} and {@code <!--
 * narrativetrace:end -->}, so a re-run replaces exactly that region and an uninstall removes
 * exactly that region — the property that makes both operations safe on a file somebody else owns.
 *
 * <p><b>@llmNote</b> The marker rule is narrow on purpose: a marker counts only when its line
 * STARTS with the marker text (column 0, no indentation) and the line is OUTSIDE a fenced code
 * block. A document that shows the markers in an example fence therefore keeps its own meaning, and
 * this repository's own {@code narrativetrace:skills:*} markers are not consumer markers — the
 * prefix differs.
 *
 * <p><b>@llmNote</b> {@link #append} and {@link #remove} are inverses: appending separates the
 * block from what was there with exactly one blank line, and removing takes that blank line back.
 * The one thing an append cannot undo is the final newline it adds to a file that had none — a
 * block has to start on its own line.
 */
final class MarkedBlock {

  /** The opening marker, up to but not including the optional coordinate stamp. */
  static final String START = "<!-- narrativetrace:start";

  static final String END = "<!-- narrativetrace:end -->";

  /**
   * Written as the first line of a file the installer CREATED, so an uninstall can tell a file it
   * may delete from one it merely appended to.
   */
  static final String CREATED_NOTE = "<!-- narrativetrace:created -->";

  private static final char BOM = '﻿';

  private MarkedBlock() {}

  /**
   * One managed region inside a file.
   *
   * @param start offset of the first character of the opening marker's line
   * @param end offset just past the closing marker's line, its terminator included
   * @param startLine 1-based line of the opening marker, so a refusal can name it
   * @param coordinate the stamp on the opening marker, empty when it carries none
   */
  record Region(int start, int end, int startLine, String coordinate) {}

  /**
   * One line of a file, with everything a marker decision needs.
   *
   * @param number 1-based line number
   * @param start offset of the line's first character
   * @param end offset just past the line's terminator
   * @param content the line without its terminator, and without a leading byte-order mark
   * @param inFence whether the line sits inside a fenced code block
   */
  record Line(int number, int start, int end, String content, boolean inFence) {}

  /**
   * Splits a file into lines that know their offsets and whether they are inside a fence — the one
   * primitive both the marker scan and the import-line check read the file through.
   */
  static List<Line> lines(String text) {
    List<Line> lines = new ArrayList<>();
    boolean inFence = false;
    int offset = 0;
    int number = 0;
    for (String raw : splitKeepingTerminators(text)) {
      number++;
      String content = strip(raw, number);
      boolean fenceLine = isFence(content);
      lines.add(new Line(number, offset, offset + raw.length(), content, inFence || fenceLine));
      if (fenceLine) {
        inFence = !inFence;
      }
      offset += raw.length();
    }
    return lines;
  }

  /**
   * What a scan found: every complete region, and every marker that cannot be part of one.
   *
   * @param regions complete start/end pairs, in file order
   * @param problems human-readable, line-numbered marker problems — a caller REFUSES a file that
   *     has any, rather than guessing which marker was meant
   */
  record Scan(List<Region> regions, List<String> problems) {

    Scan {
      regions = List.copyOf(regions);
      problems = List.copyOf(problems);
    }

    /** True when the file carries exactly one well-formed region and nothing questionable. */
    boolean hasExactlyOneRegion() {
      return problems.isEmpty() && regions.size() == 1;
    }
  }

  /**
   * Finds every managed region in a file. Never throws: a malformed file is described, not read.
   */
  static Scan scan(String text) {
    List<Region> regions = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    Scanner scanner = new Scanner(regions, problems);
    for (Line line : lines(text)) {
      if (!line.inFence()) {
        scanner.accept(line);
      }
    }
    scanner.finish();
    return new Scan(regions, problems);
  }

  /**
   * Whether the text ends with a fenced code block still open.
   *
   * <p><b>@llmNote</b> Load-bearing: anything appended to such a file lands INSIDE that fence,
   * where neither this scanner nor any Markdown reader will see it as a marker or an import — so
   * the next run appends again, and the run after that. Appending to one is refused, not attempted.
   */
  static boolean endsInsideFence(String text) {
    return lines(text).stream().filter(line -> isFence(line.content())).count() % 2 == 1;
  }

  /** A fenced-code delimiter, recognised at column 0 like every other marker here. */
  private static boolean isFence(String content) {
    return content.startsWith("```") || content.startsWith("~~~");
  }

  /** The first line outside a fence whose text is exactly this — what an uninstall removes. */
  static Optional<Line> lineIs(String text, String wanted) {
    return firstLine(text, content -> content.equals(wanted));
  }

  /**
   * The first line outside a fence whose text, trailing whitespace ignored, is this — what an
   * install reads as "already there", because trailing spaces change nothing for a reader.
   */
  static Optional<Line> lineIsIgnoringTrailingSpace(String text, String wanted) {
    return firstLine(text, content -> content.stripTrailing().equals(wanted));
  }

  private static Optional<Line> firstLine(String text, Predicate<String> matches) {
    return lines(text).stream()
        .filter(line -> !line.inFence() && matches.test(line.content()))
        .findFirst();
  }

  /** Replaces one region with {@code block}, byte for byte everywhere else. */
  static String replace(String text, Region region, String block) {
    return text.substring(0, region.start()) + block + text.substring(region.end());
  }

  /**
   * Appends {@code block} to {@code text}, separated by exactly one blank line — nothing at all
   * when the file is empty. A file that did not end with a newline gets one: a block starts on its
   * own line.
   */
  static String append(String text, String block) {
    if (text.isEmpty()) {
      return block;
    }
    String eol = eolOf(text);
    String head = text.endsWith("\n") || text.endsWith("\r") ? text : text + eol;
    return head + eol + block;
  }

  /** Removes one region and the single blank line {@link #append} would have put before it. */
  static String remove(String text, Region region) {
    return cut(text, region.start(), region.end());
  }

  /** Removes one line and the single blank line {@link #append} would have put before it. */
  static String remove(String text, Line line) {
    return cut(text, line.start(), line.end());
  }

  private static String cut(String text, int start, int end) {
    String head = text.substring(0, start);
    String eol = eolOf(text);
    if (head.endsWith(eol + eol)) {
      head = head.substring(0, head.length() - eol.length());
    }
    return head + text.substring(end);
  }

  /** The line ending a file uses, decided by its FIRST terminator; {@code \n} when it has none. */
  static String eolOf(String text) {
    int newline = text.indexOf('\n');
    if (newline < 0) {
      return "\n";
    }
    return newline > 0 && text.charAt(newline - 1) == '\r' ? "\r\n" : "\n";
  }

  /** The same text with every line ending rewritten to {@code eol}. */
  static String withEol(String text, String eol) {
    return text.replace("\r\n", "\n").replace("\n", eol);
  }

  /** Splits into lines that still carry their terminators, so offsets stay exact. */
  static List<String> splitKeepingTerminators(String text) {
    List<String> lines = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int newline = text.indexOf('\n', start);
      int endExclusive = newline < 0 ? text.length() : newline + 1;
      lines.add(text.substring(start, endExclusive));
      start = endExclusive;
    }
    return lines;
  }

  /** The line's content for MATCHING only: no terminator, and no byte-order mark on line 1. */
  private static String strip(String line, int number) {
    String content = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    if (content.endsWith("\r")) {
      content = content.substring(0, content.length() - 1);
    }
    return number == 1 && content.startsWith(String.valueOf(BOM)) ? content.substring(1) : content;
  }

  /** Walks the lines once, holding the one piece of state a scan needs: the open start marker. */
  private static final class Scanner {

    private final List<Region> regions;
    private final List<String> problems;
    private int openOffset = -1;
    private int openLine;
    private String openCoordinate = "";

    private Scanner(List<Region> regions, List<String> problems) {
      this.regions = regions;
      this.problems = problems;
    }

    private void accept(Line line) {
      if (line.content().startsWith(START)) {
        openMarker(line.content(), line.start(), line.number());
      } else if (line.content().startsWith(END)) {
        closeMarker(line.end(), line.number());
      }
    }

    private void openMarker(String content, int offset, int number) {
      if (openOffset >= 0) {
        problems.add(
            "line "
                + number
                + ": a narrativetrace:start marker inside the block opened at line "
                + openLine);
        return;
      }
      openOffset = offset;
      openLine = number;
      openCoordinate = coordinateIn(content);
    }

    private void closeMarker(int endExclusive, int number) {
      if (openOffset < 0) {
        problems.add("line " + number + ": a narrativetrace:end marker with no start above it");
        return;
      }
      regions.add(new Region(openOffset, endExclusive, openLine, openCoordinate));
      openOffset = -1;
    }

    /** After the last line: a start that never closed is a problem, not a region. */
    private void finish() {
      if (openOffset >= 0) {
        problems.add("line " + openLine + ": a narrativetrace:start marker with no end below it");
      }
    }

    /** The bare coordinate between the marker prefix and the comment's close. */
    private static String coordinateIn(String content) {
      String rest = content.substring(START.length());
      int close = rest.indexOf("-->");
      return close < 0 ? "" : rest.substring(0, close).trim();
    }
  }
}
