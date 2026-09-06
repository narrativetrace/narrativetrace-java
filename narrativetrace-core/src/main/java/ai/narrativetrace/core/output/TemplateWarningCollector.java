/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.template.TemplateParser;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects unresolved annotation placeholders that survived template resolution.
 *
 * <p>INTENT: Use this as a post-capture safety net. When a placeholder such as {@code
 * {customer.name}} could not be resolved, the unresolved token remains in the rendered signature
 * and this collector can turn it into an actionable warning.
 */
public final class TemplateWarningCollector {

  public record TemplateWarning(
      String className, String methodName, String placeholder, String field) {}

  private TemplateWarningCollector() {}

  /**
   * Bounded and cycle-safe via {@link TreeWalk}: a hand-built, replayed or deserialized tree is not
   * guaranteed acyclic ({@code TraceNode.children} is an undefended list), and a genuinely deep
   * tree is ordinary for a recursive business method. A node beyond the walk's bound still
   * contributes its own warnings; only its unreachable descendants are excluded.
   */
  public static List<TemplateWarning> collect(TraceTree trace) {
    var warnings = new ArrayList<TemplateWarning>();
    for (var root : trace.roots()) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (n, depth) -> collectFromNode(n, warnings),
          (n, depth, reason) -> collectFromNode(n, warnings));
    }
    return List.copyOf(warnings);
  }

  public static String format(List<TemplateWarning> warnings) {
    if (warnings.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder();
    sb.append("WARNING: Unresolved template placeholder(s) detected:\n");
    for (var w : warnings) {
      sb.append("  - ")
          .append(w.className)
          .append('.')
          .append(w.methodName)
          .append(": {")
          .append(w.placeholder)
          .append("} in ")
          .append(w.field)
          .append('\n');
      if (isUnsupportedNestedPath(w.placeholder)) {
        sb.append("      ↳ nested path not supported")
            .append(" — only one property level resolves (e.g. {object.property})\n");
      }
    }
    return sb.toString();
  }

  /**
   * Whether a surviving placeholder is a multi-level property path (two or more dots, e.g. {@code
   * expense.payer.name}). {@link TemplateParser} resolves only a single {@code object.property}
   * level, so a deeper path can never resolve — it is a structural authoring error, deterministic
   * regardless of runtime data, unlike a one-level path that merely saw {@code null}. Flagged at
   * test time only; runtime resolution stays graceful (literal, never throws).
   */
  static boolean isUnsupportedNestedPath(String placeholder) {
    int dots = 0;
    for (int i = 0; i < placeholder.length(); i++) {
      if (placeholder.charAt(i) == '.') {
        dots++;
      }
    }
    return dots >= 2;
  }

  private static void collectFromNode(TraceNode node, List<TemplateWarning> warnings) {
    var sig = node.signature();
    for (var placeholder : TemplateParser.findUnresolvedInResult(sig.narration())) {
      warnings.add(
          new TemplateWarning(sig.className(), sig.methodName(), placeholder, "narration"));
    }
    for (var placeholder : TemplateParser.findUnresolvedInResult(sig.errorContext())) {
      warnings.add(
          new TemplateWarning(sig.className(), sig.methodName(), placeholder, "errorContext"));
    }
  }
}
