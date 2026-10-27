/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TraceDepth;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The deep mode marks an internal (private) call in rendered text and Markdown with a leading
 * {@code ·}, and nowhere else: the default rendering of a tree with no internal call is untouched.
 */
class DeepTraceRenderingTest {

  private static TraceTree tree(boolean internal) {
    var helperSignature =
        new MethodSignature(
            "Pricing", "withTax", List.of(new ParameterCapture("net", "200", false, null, "int")));
    var helper =
        new TraceNode(
            internal ? helperSignature.asInternal() : helperSignature,
            List.of(),
            new TraceOutcome.Returned("221"),
            1_000_000L);
    var root =
        new TraceNode(
            new MethodSignature("Pricing", "total", List.of()),
            List.of(helper),
            new TraceOutcome.Returned("225"),
            2_000_000L);
    return new DefaultTraceTree(List.of(root));
  }

  @Test
  void textMarksAnInternalCallAndOnlyThat() {
    var text = new IndentedTextRenderer().render(tree(true));

    assertThat(text).contains("· Pricing.withTax(net: 200)").doesNotContain("· Pricing.total");
    assertThat(new IndentedTextRenderer().render(tree(false))).doesNotContain("·");
  }

  @Test
  void markdownMarksAnInternalCall() {
    var markdown = MarkdownRenderer.unfolded().render(tree(true));

    assertThat(markdown).contains("· **Pricing.withTax**").doesNotContain("· **Pricing.total**");
    assertThat(MarkdownRenderer.unfolded().render(tree(false))).doesNotContain("·");
  }

  @Test
  void structuralArtifactMarksTheCallAndDeclaresTheDepthInItsHeader() {
    var renderer = new StructuralTraceRenderer();

    assertThat(renderer.renderDocument(tree(true), "prices", TraceDepth.INTERNAL))
        .isEqualTo(
            "scenario: prices\n"
                + "depth: internal\n"
                + "\n"
                + "#1 - Pricing.total() → value\n"
                + "  #1.1 - · Pricing.withTax(net) → value\n");
    assertThat(renderer.renderDocument(tree(false), "prices", TraceDepth.PUBLIC))
        .isEqualTo(renderer.renderDocument(tree(false), "prices"))
        .doesNotContain("depth:");
  }
}
