/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import ai.narrativetrace.security.corpus.HostileCorpus;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * PLAIN mode (the default, alias-free {@code render()}), driven by a class name — the route named
 * OPEN by the 2026-09-13 cross-port alias-mode review: "in PLAIN mode the participant/arrow token
 * goes through the quote-if-needed path, which does not quote a bare reserved word — a class
 * literally named {@code end} may break plain Mermaid and PlantUML rendering too."
 *
 * <p>INTENT: the per-commit corpus property tests (e.g. {@code OutputFormatPropertyTest}) already
 * feed every corpus string through PLAIN mode as a captured VALUE and as narration — never as the
 * CLASS NAME that becomes the bare participant/arrow token itself. That gap is exactly why the
 * alias-mode sibling of this bug (fixed the same day, see {@code DiagramAliasCorpusPropertyTest})
 * survived every green gate: the corpus ran every commit and never once drove metadata.
 *
 * <p><b>@llmNote</b> The oracle is stricter than {@code Formats#isWellFormedMermaid}/{@code
 * isWellFormedPlantUml}: well-formedness alone accepts a participant line whose bare token happens
 * to be a keyword, because the regex only checks the line's *shape*, not what either grammar
 * reserves. The reserved-word sets below are kept <em>independent</em> of {@code DiagramText}'s own
 * sets rather than imported — a test that asks production code for the very list it is being
 * checked against cannot fail when that list is wrong, the same rule {@code
 * DiagramAliasCorpusPropertyTest} already follows.
 */
class DiagramPlainModeReservedWordCorpusPropertyTest {

  /**
   * Mermaid sequence-diagram keywords, independently sourced from {@code sequenceDiagram.jison}
   * (mermaid-js/mermaid, verified 2026-09-13) — see {@code DiagramText} for the per-keyword lexer
   * citations.
   */
  private static final Set<String> MERMAID_RESERVED_WORDS =
      Set.of(
          "sequencediagram",
          "participant",
          "actor",
          "create",
          "destroy",
          "box",
          "loop",
          "rect",
          "opt",
          "alt",
          "else",
          "par",
          "par_over",
          "and",
          "critical",
          "option",
          "break",
          "end",
          "links",
          "link",
          "properties",
          "details",
          "over",
          "note",
          "activate",
          "deactivate",
          "autonumber",
          "off",
          "title");

  /**
   * PlantUML sequence-diagram keywords, independently sourced from plantuml.com/sequence-diagram
   * (verified 2026-09-13) — see {@code DiagramText} for the full per-category citation.
   */
  private static final Set<String> PLANTUML_RESERVED_WORDS =
      Set.of(
          "participant",
          "actor",
          "boundary",
          "control",
          "entity",
          "database",
          "collections",
          "queue",
          "alt",
          "else",
          "opt",
          "loop",
          "par",
          "break",
          "critical",
          "group",
          "end",
          "note",
          "ref",
          "activate",
          "deactivate",
          "destroy",
          "create",
          "return",
          "box",
          "title",
          "header",
          "footer",
          "newpage",
          "autonumber",
          "hide",
          "show",
          "skinparam",
          "mainframe",
          "partition");

  /**
   * The union both grammars' plain-mode sanitizer must defend against, applied to either output.
   */
  private static final Set<String> ANY_RESERVED_WORD =
      union(MERMAID_RESERVED_WORDS, PLANTUML_RESERVED_WORDS);

  private final MermaidSequenceDiagramRenderer mermaid = new MermaidSequenceDiagramRenderer();
  private final PlantUmlSequenceDiagramRenderer plantUml = new PlantUmlSequenceDiagramRenderer();

  @TestFactory
  List<DynamicTest> everyHostileCorpusStringIsSafeAsAPlainModeMermaidClassName() {
    return HostileCorpus.strings().stream()
        .map(
            hostile ->
                DynamicTest.dynamicTest(
                    hostile.id(),
                    () ->
                        assertNoUnquotedReservedParticipant(
                            mermaid.render(oneClassTree(hostile.value())), "participant ")))
        .toList();
  }

  @TestFactory
  List<DynamicTest> everyHostileCorpusStringIsSafeAsAPlainModePlantUmlClassName() {
    return HostileCorpus.strings().stream()
        .map(
            hostile ->
                DynamicTest.dynamicTest(
                    hostile.id(),
                    () ->
                        assertNoUnquotedReservedParticipant(
                            plantUml.render(oneClassTree(hostile.value())), "participant ")))
        .toList();
  }

  @Test
  void aClassLiterallyNamedEndRendersAQuotedParticipantInBothGrammars() {
    // The exact reference case from the finding, pinned directly (not only via the corpus sweep).
    var tree = oneClassTree("end");

    assertThat(mermaid.render(tree))
        .contains("participant \"end\"")
        .doesNotContain("participant end\n");
    assertThat(plantUml.render(tree))
        .contains("participant \"end\"")
        .doesNotContain("participant end\n");
  }

  private void assertNoUnquotedReservedParticipant(String diagram, String prefix) {
    var participantLines =
        diagram.lines().map(String::strip).filter(l -> l.startsWith(prefix)).toList();
    for (var line : participantLines) {
      var token = line.substring(prefix.length());
      if (isQuoted(token)) {
        continue; // a quoted token is safe in either grammar regardless of its content
      }
      assertThat(ANY_RESERVED_WORD)
          .as("a bare (unquoted) plain-mode participant token that is a reserved keyword: %s", line)
          .doesNotContain(token.toLowerCase(Locale.ROOT));
    }
  }

  private static boolean isQuoted(String token) {
    return token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"");
  }

  private static TraceTree oneClassTree(String className) {
    var node =
        new TraceNode(
            new MethodSignature(className, "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    var merged = new java.util.HashSet<String>(a);
    merged.addAll(b);
    return Set.copyOf(merged);
  }
}
