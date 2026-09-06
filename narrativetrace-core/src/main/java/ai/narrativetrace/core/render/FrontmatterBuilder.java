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

/**
 * Builds YAML frontmatter for Markdown trace documents.
 *
 * <p>INTENT: Pair this with {@link MarkdownRenderer#renderDocument} when trace files need machine-
 * readable metadata alongside the human-readable body.
 */
public final class FrontmatterBuilder {

  private String scenario;

  public FrontmatterBuilder scenario(String scenario) {
    this.scenario = scenario;
    return this;
  }

  public String build(TraceTree tree) {
    var sb = new StringBuilder("---\n");
    sb.append("type: trace\n");
    if (scenario != null) {
      sb.append("scenario: ").append(yamlSafe(scenario)).append("\n");
    }
    if (!tree.roots().isEmpty()) {
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
    int methodCount = countNodes(tree);
    int errorCount = countErrors(tree);
    sb.append("method_count: ").append(methodCount).append("\n");
    sb.append("error_count: ").append(errorCount).append("\n");
    appendLoss(tree, sb);
    sb.append("---\n");
    return sb.toString();
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

  /**
   * A value that may stand in the frontmatter without quotes.
   *
   * <p><b>@llmNote</b> An allow-list, not a deny-list, and that inversion is the fix. The previous
   * rule quoted a value only when it spotted one of {@code : # " \ \n}, which let through every
   * other character YAML treats specially — a leading {@code `} or {@code [} is a parse error, a
   * leading {@code &} or {@code *} is an anchor or alias, and a raw C0 control is rejected outright
   * by the parser. Enumerating what is safe cannot have that shape of gap.
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
    return true;
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
   * A well-formed surrogate pair passes through; anything outside YAML's printable set becomes
   * {@code \\uXXXX}.
   *
   * @return the index to read from next
   */
  private static int appendPlainOrEscaped(StringBuilder sb, String value, int index) {
    var c = value.charAt(index);
    if (Character.isHighSurrogate(c)
        && index + 1 < value.length()
        && Character.isLowSurrogate(value.charAt(index + 1))) {
      sb.append(c).append(value.charAt(index + 1));
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
