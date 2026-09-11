/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.security.corpus.CorpusCase;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.oracle.Emitters;
import ai.narrativetrace.security.oracle.Formats;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Target 3 of the parity document's fuzzing list: every output format, whatever the value
 * contained.
 *
 * <p>INTENT: The oracle is well-formedness read back by the consumer's own parser — Jackson plus
 * the canonical schema for JSON, the Mermaid statement grammar for diagrams, SnakeYAML for
 * frontmatter — because an escaper that is merely plausible passes every eyeball test until the day
 * it does not.
 *
 * <p><b>@llmNote</b> Each hostile string enters twice, by the two routes production actually has.
 * As a <em>captured value</em> it passes through {@link ValueRenderer}, which sanitizes control
 * characters at capture time; as <em>narration and error context</em> it does not, because that
 * text is prose an author wrote (and, through {@code @Narrated}, prose an author wrote *around*
 * user data). Only checking the first route would leave the unsanitized half of the surface
 * untested.
 */
class OutputFormatPropertyTest {

  /** The frontmatter keys a clean capture produces. A hostile value must add none. */
  private static final Set<String> FRONTMATTER_KEYS =
      Set.of("type", "scenario", "entry_point", "duration_ms", "method_count", "error_count");

  /** How much of a hostile value has to be absent before absence means anything. */
  private static final int DISTINCTIVE_PROBE_LENGTH = 8;

  private final ValueRenderer renderer = new ValueRenderer();

  @TestFactory
  List<DynamicTest> everyCorpusStringLeavesEveryFormatWellFormed() {
    return HostileCorpus.strings().stream()
        .map(
            hostile ->
                DynamicTest.dynamicTest(
                    hostile.id(),
                    () -> {
                      assertWellFormedIncludingArtifacts(asCapturedValue(hostile));
                      assertWellFormed(Emitters.renderers(asNarration(hostile)));
                      // The third route: the same hostile text as the scenario, which reaches the
                      // YAML frontmatter, the Markdown body header, the structural header and the
                      // JSON scenario name (2026-09-08 audit, findings 1 and 2).
                      assertWellFormed(
                          Emitters.renderers(asCapturedValue(hostile), hostile.value()));
                    }))
        .toList();
  }

  @Test
  void everyCorpusStringKeepsEveryFormatBounded() {
    for (var hostile : HostileCorpus.strings()) {
      Oracles.boundedSize(Emitters.renderers(asCapturedValue(hostile)));
      Oracles.boundedSize(Emitters.renderers(asNarration(hostile)));
    }
  }

  @Test
  void everyCorpusStringRendersIdenticallyTwice() {
    for (var hostile : HostileCorpus.strings()) {
      var tree = asCapturedValue(hostile);
      Oracles.idempotent(
          "every renderer for " + hostile.id(), () -> String.valueOf(Emitters.renderers(tree)));
    }
  }

  /**
   * The structural artifact's claim is stronger than well-formedness: it carries <em>no</em>
   * runtime value at all, which is what makes it the AI-safe projection. A hostile value reaching
   * it would be a hole in ADR-002, not a formatting bug.
   */
  @Test
  void noCorpusStringReachesTheStructuralProjection() {
    for (var hostile : HostileCorpus.strings()) {
      // A probe shorter than this is not evidence: a lone newline is in every artifact already.
      if (hostile.value().length() < DISTINCTIVE_PROBE_LENGTH) {
        continue;
      }
      var outputs = Emitters.everyOutput(asCapturedValue(hostile));
      var probe = hostile.value().substring(0, DISTINCTIVE_PROBE_LENGTH);

      assertThat(outputs.get("renderer:structural"))
          .as("%s reached the value-free structural projection", hostile.id())
          .doesNotContain(probe);
    }
  }

  @Test
  void everyRendererIsPresentSoARenameCannotSilentlySkipOne() {
    var outputs = Emitters.everyOutput(Emitters.treeOf("\"a\"", "\"b\""));

    assertThat(outputs.keySet())
        .contains(
            "renderer:prose",
            "renderer:indented",
            "renderer:markdown",
            "renderer:markdown-document",
            "renderer:frontmatter",
            "renderer:structural",
            "renderer:json",
            "renderer:mermaid",
            "renderer:mermaid-aliases",
            "renderer:plantuml",
            "artifact:console");
    assertThat(outputs.keySet()).anyMatch(key -> key.endsWith(".json"));
    assertThat(outputs.keySet()).anyMatch(key -> key.endsWith(".mmd"));
    assertThat(outputs.keySet()).anyMatch(key -> key.endsWith(".nt"));
  }

  @Property(tries = 80)
  void anyGeneratedValueLeavesEveryFormatWellFormed(@ForAll("hostileText") String value) {
    assertWellFormed(
        Emitters.renderers(Emitters.treeOf(renderer.render(value), renderer.render(value))));
    assertWellFormed(Emitters.renderers(Emitters.treeNarrating(value, value)));
  }

  @Property(tries = 50)
  void anExceptionMessageLeavesEveryFormatWellFormed(@ForAll("hostileText") String message) {
    assertWellFormed(Emitters.renderers(Emitters.treeThrowing(new IllegalStateException(message))));
  }

  private TraceTree asCapturedValue(CorpusCase hostile) {
    var rendered = renderer.render(hostile.value());
    return Emitters.treeOf(rendered, rendered);
  }

  private TraceTree asNarration(CorpusCase hostile) {
    return Emitters.treeNarrating(hostile.value(), hostile.value());
  }

  /**
   * The full path, artifacts included: the production writer's JSON is what the canonical schema
   * governs, and a unit test of the exporter cannot see it. Reserved for the captured-value route
   * over the corpus, because a temp directory per generated input would make the filesystem the
   * thing being measured.
   */
  private void assertWellFormedIncludingArtifacts(TraceTree tree) {
    var outputs = Emitters.everyOutput(tree);

    assertWellFormed(outputs);
    Formats.everyJsonArtifactParses(outputs);
  }

  private void assertWellFormed(Map<String, String> outputs) {
    Formats.isWellFormedProse("renderer:prose", outputs.get("renderer:prose"));
    Formats.carriesNoControlCharacterInAnyLine(
        "renderer:indented", outputs.get("renderer:indented"));
    Formats.validatesAgainstChapterTreeSchema("renderer:json", outputs.get("renderer:json"));
    Formats.isWellFormedMermaid("renderer:mermaid", outputs.get("renderer:mermaid"));
    Formats.isWellFormedMermaid(
        "renderer:mermaid-aliases", outputs.get("renderer:mermaid-aliases"));
    Formats.isWellFormedPlantUml("renderer:plantuml", outputs.get("renderer:plantuml"));
    assertFrontmatterIsWellFormed(outputs);
  }

  private void assertFrontmatterIsWellFormed(Map<String, String> outputs) {
    assertThat(Formats.frontmatterOf("renderer:frontmatter", outputs.get("renderer:frontmatter")))
        .containsOnlyKeys(FRONTMATTER_KEYS.toArray(String[]::new));
    assertThat(
            Formats.frontmatterOf(
                "renderer:markdown-document", outputs.get("renderer:markdown-document")))
        .containsOnlyKeys(FRONTMATTER_KEYS.toArray(String[]::new));
  }

  /** The corpus alphabet, recombined — the part that finds what nobody listed. */
  @Provide
  Arbitrary<String> hostileText() {
    var alphabet =
        Arbitraries.of(
            "\"",
            "\\",
            "/",
            "\n",
            "\r",
            "\t",
            "{",
            "}",
            "[",
            "]",
            ":",
            ",",
            "`",
            ">",
            "-",
            " ",
            "A",
            "z",
            "0",
            "#",
            "|",
            "*",
            "&",
            "!",
            "%",
            "@",
            "'",
            "~",
            "$",
            "<",
            "```",
            "---",
            "->>",
            "%%",
            String.valueOf((char) 0x0000),
            String.valueOf((char) 0x001b),
            String.valueOf((char) 0x202e),
            String.valueOf((char) 0x200b),
            String.valueOf((char) 0xd800),
            "🙈");
    return alphabet.list().ofMinSize(0).ofMaxSize(24).map(parts -> String.join("", parts));
  }
}
