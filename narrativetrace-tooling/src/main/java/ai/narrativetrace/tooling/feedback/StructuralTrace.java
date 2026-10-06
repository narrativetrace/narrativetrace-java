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
package ai.narrativetrace.tooling.feedback;

import java.util.regex.Pattern;

/**
 * Whether a file is a structural trace — the one artifact a report may attach, because it is the
 * one artifact that carries no runtime value by construction.
 *
 * <p>INTENT: defence in depth over the value-free rules, not a substitute for them. The rules
 * decide about TEXT; this decides about a FILE's claim to be a {@code .nt}. A rendered narrative
 * copied to a {@code .nt} name is caught by the rules anyway — it is full of values — but a file
 * that passes the rules while not being a structural trace at all (a log with nothing interesting
 * in it, a half-written note) is not the attachment the report promises, and attaching it would put
 * an unreviewed file shape into a public issue.
 *
 * <p><b>@llmNote</b> The grammar is the published one: a {@code scenario:} header, then call lines
 * {@code Type.method(name, name)} with an optional outcome of the returned marker, {@code !!
 * TypeName} or the incomplete marker, and concurrency markers such as {@code ~ fork [2]}. Anything
 * else makes the whole file not-a-structural-trace. Loosening this means loosening what a report
 * may attach, so loosen the FORMAT first and this after.
 *
 * <p><b>@llmNote</b> A call line is taken apart with {@code indexOf} and one-quantifier patterns
 * rather than matched by a single regex. The obvious regex needs a nested quantifier for the dotted
 * name, which is both a backtracking hazard and a shape the security scanner refuses on sight — and
 * a check that has to be excused by an exclusion is a check nobody trusts.
 */
public final class StructuralTrace {

  /** One identifier: a class name, a method name, a parameter name. No dot, by construction. */
  private static final Pattern SEGMENT = Pattern.compile("[\\w$]+");

  /** The whole parameter list: names, commas and spaces. Never a colon, never a value. */
  private static final Pattern PARAMETERS = Pattern.compile("[\\w$, ]*");

  private static final Pattern HEADER = Pattern.compile("^scenario: \\S.*$");

  /** A concurrency marker with an optional bracketed count, e.g. {@code ~ fork [2]}. */
  private static final Pattern MARKER_LINE = Pattern.compile("^\\s*~ [\\w-]+( \\[\\d+\\])?$");

  private static final String BULLET = "- ";
  private static final String RETURNED = " → value";
  private static final String INCOMPLETE = " ?? incomplete";
  private static final String THREW = " !! ";

  private StructuralTrace() {}

  /**
   * Whether this content parses as a structural trace.
   *
   * @throws IllegalArgumentException when {@code content} is null — an absent file is {@code ""}
   */
  public static boolean looksStructural(String content) {
    if (content == null) {
      throw new IllegalArgumentException("the grammar check reads content, never null");
    }
    boolean headerSeen = false;
    for (String line : content.split("\n", -1)) {
      if (line.isBlank()) {
        continue;
      }
      if (!headerSeen) {
        headerSeen = HEADER.matcher(line).matches();
        if (!headerSeen) {
          return false;
        }
      } else if (!isCallLine(line) && !MARKER_LINE.matcher(line).matches()) {
        return false;
      }
    }
    return headerSeen;
  }

  /** {@code - Type.method(a, b)} plus at most one outcome marker. */
  private static boolean isCallLine(String line) {
    String bullet = line.strip();
    if (!bullet.startsWith(BULLET)) {
      return false;
    }
    String call = bullet.substring(BULLET.length());
    int open = call.indexOf('(');
    int close = call.lastIndexOf(')');
    if (open < 0 || close < open) {
      return false;
    }
    return isDottedName(call.substring(0, open), 2)
        && PARAMETERS.matcher(call.substring(open + 1, close)).matches()
        && isOutcome(call.substring(close + 1));
  }

  /** Nothing (void), the returned marker, a thrown type name, or the incomplete marker. */
  private static boolean isOutcome(String suffix) {
    if (suffix.isEmpty() || RETURNED.equals(suffix) || INCOMPLETE.equals(suffix)) {
      return true;
    }
    return suffix.startsWith(THREW) && isDottedName(suffix.substring(THREW.length()), 1);
  }

  /** A dotted name of at least {@code minimumSegments} identifier segments. */
  private static boolean isDottedName(String name, int minimumSegments) {
    String[] segments = name.split("\\.", -1);
    if (segments.length < minimumSegments) {
      return false;
    }
    for (String segment : segments) {
      if (!SEGMENT.matcher(segment).matches()) {
        return false;
      }
    }
    return true;
  }
}
