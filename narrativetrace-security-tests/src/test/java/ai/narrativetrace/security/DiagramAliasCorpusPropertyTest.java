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
import ai.narrativetrace.security.corpus.CorpusCase;
import ai.narrativetrace.security.corpus.HostileCorpus;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Mermaid's alias mode, driven by a class name — the one route the shared hostile corpus never
 * reached before.
 *
 * <p>INTENT: {@code renderer:mermaid-aliases} has been in {@link
 * ai.narrativetrace.security.oracle.Emitters#renderers} since the corpus itself was built, so every
 * corpus <em>value</em> already drove {@code renderWithAliases} through the captured-value and
 * narration routes. What no per-commit test ever did was drive it through the <em>metadata</em>
 * route — a hostile string as the class name itself, which is the only field {@link
 * ai.narrativetrace.diagrams.DiagramText#aliasToken} ever turns into a bare, unquoted token. Only
 * the budgeted, non-per-commit Jazzer target ({@code OutputFormatFuzzTest}) exercised that route,
 * which is exactly why a class literally named {@code end} slipped through: the corpus ran every
 * commit and never once tried it.
 *
 * <p><b>@llmNote</b> The oracle here is stricter than {@link
 * ai.narrativetrace.security.oracle.Formats#isWellFormedMermaid}: well-formedness alone accepts a
 * participant line whose alias happens to be a Mermaid keyword, because the regex only checks the
 * *shape* of a {@code participant} statement, not what Mermaid's own grammar reserves — {@code end}
 * matches {@code [A-Za-z0-9_]} just as well as {@code end_} does. Three assertions pin the actual
 * contract: (1) exactly one participant line per distinct class — a collision silently merging two
 * different classes into one participant would still be well formed; (2) every alias token contains
 * only {@code [A-Za-z0-9_]}, Mermaid's own bare-token grammar; and (3) no alias token, lowercased,
 * equals a Mermaid sequence-diagram keyword. {@link #MERMAID_RESERVED_ALIASES} is kept
 * <em>independent</em> of {@code DiagramText}'s own reserved-word set rather than importing it — a
 * test that asks production code for the very list it is being checked against cannot fail when
 * that list is wrong.
 */
class DiagramAliasCorpusPropertyTest {

  private static final Pattern BARE_ALIAS_TOKEN = Pattern.compile("[A-Za-z0-9_]+");
  private static final String CORPUS_PREFIX = "diagram-alias-";

  /**
   * Mermaid sequence-diagram keywords, independently sourced from {@code sequenceDiagram.jison}
   * (mermaid-js/mermaid, verified 2026-09-13) the same way {@code DiagramText} documents its own
   * set — see that class for the per-keyword lexer-rule citations.
   */
  private static final Set<String> MERMAID_RESERVED_ALIASES =
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

  private final MermaidSequenceDiagramRenderer renderer = new MermaidSequenceDiagramRenderer();

  @TestFactory
  List<DynamicTest> everyDiagramAliasCorpusCaseProducesASafeParticipant() {
    return HostileCorpus.strings().stream()
        .filter(hostile -> hostile.id().startsWith(CORPUS_PREFIX))
        .map(
            hostile ->
                DynamicTest.dynamicTest(
                    hostile.id(), () -> assertSafeAliasFor(oneClassTree(hostile))))
        .toList();
  }

  @Test
  void everyDiagramAliasCorpusCaseIsActuallyPresent() {
    // The property above silently tests nothing if a rename ever drops these ids from the corpus.
    var ids =
        HostileCorpus.strings().stream()
            .map(CorpusCase::id)
            .filter(id -> id.startsWith(CORPUS_PREFIX))
            .toList();

    assertThat(ids)
        .containsExactlyInAnyOrder(
            "diagram-alias-arrow",
            "diagram-alias-quote-collision",
            "diagram-alias-reserved-word",
            "diagram-alias-empty");
  }

  /**
   * The collision the corpus row {@code diagram-alias-quote-collision} names but cannot exercise
   * alone: two <em>different</em> classes ({@code a"b} and {@code a'b}) whose aliases sanitize to
   * the same bare token ({@code ab}). Disambiguation must still keep them as two participants,
   * never silently merge them into one.
   */
  @Test
  void quoteAndApostropheClassNamesThatSanitizeToTheSameAliasStayDistinctParticipants() {
    var diagram = renderer.renderWithAliases(twoClassTree("a\"b", "a'b"));

    assertParticipantCountAndCleanAliases(diagram, 2);
  }

  private void assertSafeAliasFor(TraceTree tree) {
    var diagram = renderer.renderWithAliases(tree);
    assertParticipantCountAndCleanAliases(diagram, 1);
  }

  private void assertParticipantCountAndCleanAliases(String diagram, int expectedParticipants) {
    var participantLines =
        diagram.lines().map(String::strip).filter(line -> line.startsWith("participant ")).toList();

    assertThat(participantLines)
        .as("exactly one participant line per distinct class: %s", diagram)
        .hasSize(expectedParticipants);
    for (var line : participantLines) {
      var alias = line.substring("participant ".length()).split(" ", 2)[0];
      assertThat(alias)
          .as("a participant alias containing anything but [A-Za-z0-9_]: %s", line)
          .matches(BARE_ALIAS_TOKEN);
      assertThat(MERMAID_RESERVED_ALIASES)
          .as("a participant alias that is a bare Mermaid keyword: %s", line)
          .doesNotContain(alias.toLowerCase(Locale.ROOT));
    }
  }

  private static TraceTree oneClassTree(CorpusCase hostile) {
    return oneClassTree(hostile.value());
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

  private static TraceTree twoClassTree(String callerClassName, String targetClassName) {
    var child =
        new TraceNode(
            new MethodSignature(targetClassName, "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    var root =
        new TraceNode(
            new MethodSignature(callerClassName, "call", List.of()),
            List.of(child),
            new TraceOutcome.Returned("true"),
            2_000_000L);
    return new DefaultTraceTree(List.of(root));
  }
}
