/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.security.oracle.Emitters;
import ai.narrativetrace.security.oracle.Formats;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Tier B target 3: coverage-guided fuzzing of every output format.
 *
 * <p>INTENT: One hostile value, every route production has — captured (through {@link
 * ValueRenderer}), narrated (author prose, unescaped), thrown (an exception message), and
 * <em>named</em> (class, method, parameter and return type on a {@code MethodSignature}) — through
 * every in-memory renderer, each read back by its own parser.
 *
 * <p><b>@llmNote</b> The metadata route was added after the 2026-09-02 adversarial audit's finding
 * 4. Fuzzing values alone was structurally unable to find it: values arrive at the renderers
 * already escaped, and it was the unescaped names beside them that broke the diagram grammars.
 *
 * <p><b>@llmNote</b> Only the in-memory renderers run here, not the file writers. A fuzzing loop
 * executes tens of thousands of inputs per minute, and a temp directory per input would make the
 * filesystem the thing being measured. The written artifacts are covered by the property beside
 * this one, which runs the corpus rather than a budget.
 */
class OutputFormatFuzzTest {

  private static final Set<String> FRONTMATTER_KEYS =
      Set.of("type", "scenario", "entry_point", "duration_ms", "method_count", "error_count");

  private final ValueRenderer renderer = new ValueRenderer();

  @FuzzTest(maxDuration = FuzzBudget.PER_TARGET)
  void everyFormatStaysWellFormed(byte[] data) {
    var value = new String(data, StandardCharsets.UTF_8);

    assertWellFormed(Emitters.renderers(Emitters.treeOf(rendered(value), rendered(value))));
    assertWellFormed(Emitters.renderers(Emitters.treeNarrating(value, value)));
    assertWellFormed(Emitters.renderers(Emitters.treeThrowing(new IllegalStateException(value))));
    assertWellFormed(Emitters.renderers(Emitters.treeWithHostileMetadata(value)));
  }

  private String rendered(String value) {
    return renderer.render(value);
  }

  private static void assertWellFormed(java.util.Map<String, String> outputs) {
    Formats.isWellFormedProse("renderer:prose", outputs.get("renderer:prose"));
    Formats.carriesNoControlCharacterInAnyLine(
        "renderer:indented", outputs.get("renderer:indented"));
    Formats.validatesAgainstChapterTreeSchema("renderer:json", outputs.get("renderer:json"));
    Formats.isWellFormedMermaid("renderer:mermaid", outputs.get("renderer:mermaid"));
    Formats.isWellFormedMermaid(
        "renderer:mermaid-aliases", outputs.get("renderer:mermaid-aliases"));
    Formats.isWellFormedPlantUml("renderer:plantuml", outputs.get("renderer:plantuml"));
    assertThat(Formats.frontmatterOf("renderer:frontmatter", outputs.get("renderer:frontmatter")))
        .containsOnlyKeys(FRONTMATTER_KEYS.toArray(String[]::new));
    assertThat(
            Formats.frontmatterOf(
                "renderer:markdown-document", outputs.get("renderer:markdown-document")))
        .containsOnlyKeys(FRONTMATTER_KEYS.toArray(String[]::new));
  }
}
