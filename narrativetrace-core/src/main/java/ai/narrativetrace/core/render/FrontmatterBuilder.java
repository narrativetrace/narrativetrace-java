/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.regex.Pattern;

/**
 * Builds YAML frontmatter for Markdown trace documents.
 *
 * <p>INTENT: Pair this with {@link MarkdownRenderer#renderDocument} when trace files need machine-
 * readable metadata alongside the human-readable body.
 */
public final class FrontmatterBuilder {

  private String scenario;
  private String runName;

  public FrontmatterBuilder scenario(String scenario) {
    this.scenario = scenario;
    return this;
  }

  /**
   * The enclosing test-suite run's three-word phrase (2026-09-13 ruling, item 2), or {@code null}
   * to omit the field entirely — a document rendered outside a tracked run names no run.
   *
   * <p><b>@llmNote</b> This is the ONLY frontmatter field {@link
   * ai.narrativetrace.core.output.RunIdentity} ever reaches: never folded into {@code scenario},
   * never read back by {@code entry_point}/{@code trace_id}/{@code trace_name}, and never present
   * on the structural {@code .nt} artifact at all (item 3).
   */
  public FrontmatterBuilder runName(String runName) {
    this.runName = runName;
    return this;
  }

  public String build(TraceTree tree) {
    var sb = new StringBuilder("---\n");
    sb.append("type: trace\n");
    if (runName != null) {
      sb.append("run: ").append(yamlSafe(runName)).append("\n");
    }
    if (scenario != null) {
      sb.append("scenario: ").append(yamlSafe(scenario)).append("\n");
    }
    appendRootFields(tree, sb);
    int methodCount = countNodes(tree);
    int errorCount = countErrors(tree);
    sb.append("method_count: ").append(methodCount).append("\n");
    sb.append("error_count: ").append(errorCount).append("\n");
    appendLoss(tree, sb);
    sb.append("---\n");
    return sb.toString();
  }

  /**
   * {@code entry_point}, {@code duration_ms} and, when the root carries one, {@code
   * trace_id}/{@code trace_name} — empty on an empty tree.
   */
  private static void appendRootFields(TraceTree tree, StringBuilder sb) {
    if (tree.roots().isEmpty()) {
      return;
    }
    var root = tree.roots().get(0);
    var sig = root.signature();
    sb.append("entry_point: ")
        .append(yamlSafe(sig.className() + "." + sig.methodName()))
        .append("\n");
    // The scenario's span, matching the JSON export — one number, one meaning.
    sb.append("duration_ms: ").append(DurationFormat.millis(tree.durationNanos())).append("\n");
    if (root.spanContext() != null) {
      sb.append("trace_id: ").append(root.spanContext().traceId()).append("\n");
      sb.append("trace_name: ")
          .append(TraceNamer.name(root.spanContext().traceId().value()))
          .append("\n");
    }
  }

  /**
   * The machine-readable half of the loss footer: absent entirely on a clean capture, so no
   * existing document gained a key, and unambiguous on a lossy one.
   */
  private static void appendLoss(TraceTree tree, StringBuilder sb) {
    var loss = tree.loss();
    if (!loss.any()) {
      return;
    }
    sb.append("incomplete: true\n");
    sb.append("dropped_events: ").append(loss.droppedEvents()).append("\n");
    sb.append("refused_scopes: ").append(loss.refusedScopes()).append("\n");
    sb.append("refused_spans: ").append(loss.refusedSpans()).append("\n");
  }

  /**
   * Counts every node under {@code tree}, bounded and cycle-safe via {@link TreeWalk}: a
   * hand-built, replayed or deserialized tree is not guaranteed acyclic ({@code TraceNode.children}
   * is an undefended list), and a genuinely deep tree is ordinary for a recursive business method.
   * A node beyond the walk's bound still counts as itself; only its unreachable descendants are
   * excluded.
   */
  private int countNodes(TraceTree tree) {
    var count = new int[] {0};
    for (var root : tree.roots()) {
      TreeWalk.walk(
          root, TraceNode::children, (n, depth) -> count[0]++, (n, depth, reason) -> count[0]++);
    }
    return count[0];
  }

  /**
   * Same bound as {@link #countNodes}; a node the walk stopped at still answers for its own
   * outcome.
   */
  private int countErrors(TraceTree tree) {
    var count = new int[] {0};
    for (var root : tree.roots()) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (n, depth) -> countIfError(n, count),
          (n, depth, reason) -> countIfError(n, count));
    }
    return count[0];
  }

  private static void countIfError(TraceNode node, int[] count) {
    if (node.outcome() instanceof TraceOutcome.Threw) {
      count[0]++;
    }
  }

  /** YAML 1.1 core-schema bool scalars, every letter case the spec allows. */
  private static final Pattern BOOL_RESOLVER =
      Pattern.compile(
          "^(?:y|Y|yes|Yes|YES|n|N|no|No|NO|true|True|TRUE|false|False|FALSE"
              + "|on|On|ON|off|Off|OFF)$");

  /** YAML 1.1's null scalars; the empty and lone-space forms are already caught above. */
  private static final Pattern NULL_RESOLVER = Pattern.compile("^(?:~|null|Null|NULL)$");

  /** Decimal, hex, octal and binary integers, with sign and {@code _} digit separators. */
  private static final Pattern INT_RESOLVER =
      Pattern.compile(
          "^(?:[-+]?0b_*[0-1][0-1_]*"
              + "|[-+]?0_*[0-7][0-7_]*"
              + "|[-+]?(?:0|[1-9][0-9_]*)"
              + "|[-+]?0x_*[0-9a-fA-F][0-9a-fA-F_]*)$");

  /**
   * Whether a value's bare form resolves to something other than a string under YAML 1.1's core
   * schema: a boolean, null, an integer, a float or a timestamp.
   *
   * <p><b>@llmNote</b> Deliberately the full core schema, not SnakeYAML 2.3's own narrower {@code
   * Resolver} (which drops the bare {@code y}/{@code n} bool forms): the frontmatter is a contract
   * with every YAML reader a document may be fed to, not only the one this repository happens to
   * test with.
   *
   * <p><b>@llmNote</b> The float and timestamp checks are hand-written scans, not {@link Pattern}s
   * like the three above: their regex equivalents would pair adjacent quantifiers over overlapping
   * digit/underscore classes across alternatives, which can backtrack super-linearly; the input is
   * an unvalidated test or scenario name, so that is a denial-of-service surface. See {@link
   * #isFloatLiteral} and {@link #isTimestampLiteral}.
   */
  private static boolean resolvesToNonString(String value) {
    return BOOL_RESOLVER.matcher(value).matches()
        || NULL_RESOLVER.matcher(value).matches()
        || INT_RESOLVER.matcher(value).matches()
        || isFloatLiteral(value)
        || isTimestampLiteral(value);
  }

  /**
   * Fixed and exponent notation, {@code .inf}/{@code .nan}, with sign and {@code _} separators —
   * the same accepted language as the regex this replaced.
   *
   * <p><b>@llmNote</b> Every branch advances a single index and never revisits a position, so the
   * scan is linear in {@code value.length()} for any input — package-private so the boundary and
   * near-miss forms can be asserted directly, not only through {@link #yamlSafe}'s outer character
   * gate (which already quotes any value starting with {@code .} or a sign before this check ever
   * runs).
   */
  static boolean isFloatLiteral(String value) {
    var index = 0;
    var hasSign = !value.isEmpty() && (value.charAt(0) == '-' || value.charAt(0) == '+');
    if (hasSign) {
      index++;
    }
    if (index >= value.length()) {
      return false;
    }
    var c = value.charAt(index);
    if (c == '.') {
      return isDotLeadingFloat(value, index + 1, hasSign);
    }
    return Character.isDigit(c) && isDigitLeadingFloat(value, index);
  }

  /** {@code digit (digit|_)* '.' (digit|_)* exponent?}, or the same with a mandatory exponent. */
  private static boolean isDigitLeadingFloat(String value, int from) {
    var afterDigits = scanDigitsAndUnderscores(value, from);
    if (afterDigits < value.length() && value.charAt(afterDigits) == '.') {
      return isFractionalPart(value, afterDigits + 1);
    }
    return isMandatoryExponent(value, afterDigits);
  }

  /** {@code (digit|_)* exponent?}, reached just past the decimal point. */
  private static boolean isFractionalPart(String value, int from) {
    var afterFraction = scanDigitsAndUnderscores(value, from);
    return scanOptionalExponent(value, afterFraction) == value.length();
  }

  /** {@code [eE] [-+]? digit+}, required rather than optional, to the end of the value. */
  private static boolean isMandatoryExponent(String value, int from) {
    if (from >= value.length() || (value.charAt(from) != 'e' && value.charAt(from) != 'E')) {
      return false;
    }
    return scanOptionalExponent(value, from) == value.length();
  }

  /**
   * {@code . (inf|Inf|INF)}, {@code . (nan|NaN|NAN)} (no sign — the caller checked), or {@code .
   * (digit|_)+ exponent?}.
   */
  private static boolean isDotLeadingFloat(String value, int from, boolean hasSign) {
    var rest = value.substring(from);
    if (rest.equals("inf") || rest.equals("Inf") || rest.equals("INF")) {
      return true;
    }
    if (!hasSign && (rest.equals("nan") || rest.equals("NaN") || rest.equals("NAN"))) {
      return true;
    }
    var afterFraction = scanDigitsAndUnderscores(value, from);
    return afterFraction > from && scanOptionalExponent(value, afterFraction) == value.length();
  }

  /** Advances past a run of digits and {@code _}; may consume nothing. */
  private static int scanDigitsAndUnderscores(String value, int from) {
    var i = from;
    while (i < value.length() && (Character.isDigit(value.charAt(i)) || value.charAt(i) == '_')) {
      i++;
    }
    return i;
  }

  /**
   * Advances past an optional {@code [eE] [-+]? digit+}; returns {@code from} unchanged when no
   * {@code e}/{@code E} is present, or -1 when one is present with no digit after it.
   */
  private static int scanOptionalExponent(String value, int from) {
    if (from >= value.length() || (value.charAt(from) != 'e' && value.charAt(from) != 'E')) {
      return from;
    }
    var i = from + 1;
    if (i < value.length() && (value.charAt(i) == '-' || value.charAt(i) == '+')) {
      i++;
    }
    var digitsStart = i;
    while (i < value.length() && Character.isDigit(value.charAt(i))) {
      i++;
    }
    return i == digitsStart ? -1 : i;
  }

  /**
   * The {@code YYYY-MM-DD} date form and the full date-time timestamp form — the same accepted
   * language as the regex this replaced.
   *
   * <p><b>@llmNote</b> Every field below is a bounded digit run immediately followed by a literal
   * delimiter, so taking the maximum available digits (up to each field's width) is never ambiguous
   * and never needs reconsidering — there is nothing to backtrack into. Package-private for the
   * same direct-test reason as {@link #isFloatLiteral}.
   */
  static boolean isTimestampLiteral(String value) {
    return isDateOnly(value) || isFullTimestamp(value);
  }

  /** {@code digit{4} '-' digit{2} '-' digit{2}}, exactly, with nothing else in the value. */
  private static boolean isDateOnly(String value) {
    return hasExactDigits(value, 0, 4)
        && at(value, 4, '-')
        && hasExactDigits(value, 5, 2)
        && at(value, 7, '-')
        && hasExactDigits(value, 8, 2)
        && value.length() == 10;
  }

  /** {@code digit{4} '-' digit{1,2} '-' digit{1,2}} then a separator and a time of day. */
  private static boolean isFullTimestamp(String value) {
    if (!hasExactDigits(value, 0, 4) || !at(value, 4, '-')) {
      return false;
    }
    var month = countLeadingDigits(value, 5, 2);
    if (month == 0 || !at(value, 5 + month, '-')) {
      return false;
    }
    var dayStart = 5 + month + 1;
    var day = countLeadingDigits(value, dayStart, 2);
    if (day == 0) {
      return false;
    }
    var afterSeparator = skipDateTimeSeparator(value, dayStart + day);
    return afterSeparator >= 0 && isTimeOfDay(value, afterSeparator);
  }

  /** A single {@code T}/{@code t}, or one or more spaces/tabs; -1 when neither is present. */
  private static int skipDateTimeSeparator(String value, int from) {
    if (from >= value.length()) {
      return -1;
    }
    var c = value.charAt(from);
    if (c == 'T' || c == 't') {
      return from + 1;
    }
    var i = from;
    while (i < value.length() && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) {
      i++;
    }
    return i > from ? i : -1;
  }

  /** {@code digit{1,2} ':' digit{2} ':' digit{2} fraction? zone?}, to the end of the value. */
  private static boolean isTimeOfDay(String value, int from) {
    var hour = countLeadingDigits(value, from, 2);
    if (hour == 0 || !at(value, from + hour, ':') || !hasExactDigits(value, from + hour + 1, 2)) {
      return false;
    }
    var afterMinute = from + hour + 3;
    if (!at(value, afterMinute, ':') || !hasExactDigits(value, afterMinute + 1, 2)) {
      return false;
    }
    var afterFraction = skipOptionalFraction(value, afterMinute + 3);
    return isValidTimezoneSuffix(value, afterFraction);
  }

  /** An optional {@code '.' digit*}; returns {@code from} unchanged when no {@code .} is there. */
  private static int skipOptionalFraction(String value, int from) {
    if (from >= value.length() || value.charAt(from) != '.') {
      return from;
    }
    var i = from + 1;
    while (i < value.length() && Character.isDigit(value.charAt(i))) {
      i++;
    }
    return i;
  }

  /** Nothing, {@code space* 'Z'}, or {@code space* offset}, each reaching the end of the value. */
  private static boolean isValidTimezoneSuffix(String value, int from) {
    if (from == value.length()) {
      return true;
    }
    var i = from;
    while (i < value.length() && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) {
      i++;
    }
    if (i < value.length() && value.charAt(i) == 'Z') {
      return i + 1 == value.length();
    }
    return isOffset(value, i);
  }

  /** {@code [-+] digit{1,2} (':' digit{2})?}, reaching the end of the value. */
  private static boolean isOffset(String value, int from) {
    if (from >= value.length() || (value.charAt(from) != '-' && value.charAt(from) != '+')) {
      return false;
    }
    var hourDigits = countLeadingDigits(value, from + 1, 2);
    if (hourDigits == 0) {
      return false;
    }
    var afterHour = from + 1 + hourDigits;
    if (afterHour == value.length()) {
      return true;
    }
    return at(value, afterHour, ':')
        && hasExactDigits(value, afterHour + 1, 2)
        && afterHour + 3 == value.length();
  }

  /** The count of leading digits at {@code from}, capped at {@code max}; 0 when there are none. */
  private static int countLeadingDigits(String value, int from, int max) {
    var count = 0;
    while (from + count < value.length()
        && count < max
        && Character.isDigit(value.charAt(from + count))) {
      count++;
    }
    return count;
  }

  /** Whether {@code count} consecutive digits sit at {@code from} without running past the end. */
  private static boolean hasExactDigits(String value, int from, int count) {
    if (from + count > value.length()) {
      return false;
    }
    for (var i = from; i < from + count; i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /** Whether {@code value} has {@code ch} at {@code index}, without running past the end. */
  private static boolean at(String value, int index, char ch) {
    return index < value.length() && value.charAt(index) == ch;
  }

  /**
   * A value that may stand in the frontmatter without quotes.
   *
   * <p><b>@llmNote</b> An allow-list, not a deny-list, and that inversion is the fix. The previous
   * rule quoted a value only when it spotted one of {@code : # " \ \n}, which let through every
   * other character YAML treats specially — a leading {@code `} or {@code [} is a parse error, a
   * leading {@code &} or {@code *} is an anchor or alias, and a raw C0 control is rejected outright
   * by the parser. Enumerating what is safe cannot have that shape of gap.
   *
   * <p><b>@llmNote</b> The character allow-list is necessary but not sufficient: it closes every
   * way a bare value could become a parse error or a sibling key, but says nothing about a bare
   * value a parser <em>resolves</em> to a different type — {@link #resolvesToNonString} is the
   * second, independent clause that closes that gap.
   */
  private static boolean isPlainScalar(String value) {
    if (value.isEmpty() || value.charAt(0) == ' ' || value.endsWith(" ")) {
      return false;
    }
    if (!Character.isLetterOrDigit(value.charAt(0))) {
      return false;
    }
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      var ordinary = Character.isLetterOrDigit(c) || c == ' ' || c == '.' || c == '_' || c == '-';
      if (!ordinary) {
        return false;
      }
    }
    return !resolvesToNonString(value);
  }

  /**
   * Renders a value as a YAML scalar: bare when it is plainly safe, double-quoted otherwise.
   *
   * <p>INTENT: Frontmatter is the machine-readable half of a trace document, so a value that makes
   * it unparseable destroys the whole file for tooling, and a value that <em>parses</em> as extra
   * structure is worse — the 2026-09-02 audit's metadata fuzz route produced frontmatter carrying
   * injected {@code Human:} and {@code Assistant:} keys from a class name, which is a prompt
   * injection against anything reading the document as a conversation.
   *
   * <p><b>@edgeCase</b> Escaping is YAML's own, not {@link ControlEscape}'s. Both are safe, but
   * YAML's {@code \\n} and {@code \\uXXXX} are escapes a parser <em>decodes</em>, so a value that
   * really did contain a newline round-trips as a newline instead of becoming the literal two
   * characters. Frontmatter is read by tooling; fidelity is part of being correct here.
   *
   * @param value the raw text; {@code null} renders as an empty quoted scalar
   * @return the value as a YAML scalar, safe to place after {@code key: }
   */
  static String yamlSafe(String value) {
    if (value == null) {
      return "\"\"";
    }
    if (isPlainScalar(value)) {
      return value;
    }
    var sb = new StringBuilder(value.length() + 2).append('"');
    var index = 0;
    while (index < value.length()) {
      index = appendEscaped(sb, value, index);
    }
    return sb.append('"').toString();
  }

  /**
   * One character in YAML double-quoted style: a mnemonic escape, {@code \\uXXXX}, or itself.
   *
   * @return the index to read from next, which is two ahead for a surrogate pair
   */
  private static int appendEscaped(StringBuilder sb, String value, int index) {
    switch (value.charAt(index)) {
      case '\\' -> sb.append("\\\\");
      case '"' -> sb.append("\\\"");
      case '\n' -> sb.append("\\n");
      case '\r' -> sb.append("\\r");
      case '\t' -> sb.append("\\t");
      default -> {
        return appendPlainOrEscaped(sb, value, index);
      }
    }
    return index + 1;
  }

  /**
   * A well-formed surrogate pair becomes one {@code \\UXXXXXXXX} escape; anything outside YAML's
   * printable set becomes {@code \\uXXXX}.
   *
   * <p><b>@edgeCase</b> Supplementary characters are YAML-printable, but a raw pair puts surrogate
   * halves into the emitted chars, and a downstream parser that reads in fixed-size {@code char}
   * chunks can land its buffer boundary between the halves — SnakeYAML 2.3 reads 1024-char chunks
   * and, when a chunk ends on a high surrogate, reads one char past its own buffer:
   * IndexOutOfBoundsException on spec-valid YAML (2026-09-08 audit; found by the output-format
   * fuzzer). Frontmatter is a machine-readable interoperability contract, so it is emitted
   * BMP-only: the 8-digit {@code \\U} escape is YAML's own ({@code ns-esc-32-bit}), a conforming
   * parser decodes it back to the same code point, and no boundary can ever split what is no longer
   * a pair.
   *
   * @return the index to read from next, which is two ahead for a surrogate pair
   */
  private static int appendPlainOrEscaped(StringBuilder sb, String value, int index) {
    var c = value.charAt(index);
    if (Character.isHighSurrogate(c)
        && index + 1 < value.length()
        && Character.isLowSurrogate(value.charAt(index + 1))) {
      var codePoint = Character.toCodePoint(c, value.charAt(index + 1));
      sb.append(String.format("\\U%08x", codePoint));
      return index + 2;
    }
    if (isYamlPrintable(c)) {
      sb.append(c);
    } else {
      sb.append(String.format("\\u%04x", (int) c));
    }
    return index + 1;
  }

  /**
   * YAML 1.2's {@code c-printable}, minus the characters already handled above.
   *
   * <p><b>@edgeCase</b> Narrower than {@code !Character.isISOControl}, and that is the point: a
   * lone surrogate, {@code U+FFFE}, {@code U+FFFF} and the C1 block are not control characters by
   * Java's test but are rejected outright by a YAML parser ("special characters are not allowed").
   * A trace whose class name contained one produced a document no tool could read. Found by the
   * metadata fuzz route's {@code noncharacter} fixture.
   */
  private static boolean isYamlPrintable(char c) {
    return (c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD);
  }
}
