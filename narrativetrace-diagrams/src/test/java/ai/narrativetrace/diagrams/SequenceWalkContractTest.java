/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The invariant composition is supposed to guarantee: whichever grammar {@link SequenceWalk} is
 * given, the walk emits exactly one call arrow and exactly one outcome per node it visits or stops
 * at — never more, never fewer, whatever the tree's shape or the metadata's content.
 *
 * <p>INTENT: {@link MermaidSequenceDiagramRendererTest} and {@link
 * PlantUmlSequenceDiagramRendererTest} already pin each grammar's own output text; this test pins
 * the shared traversal contract those two grammars can never diverge on, by counting hook
 * invocations rather than parsing rendered text — parsing would be fooled by the hostile-metadata
 * cases below, where the trace-derived text legitimately contains arrow-like substrings.
 */
class SequenceWalkContractTest {

  private static final Function<String, DiagramLabel> PLAIN_LABEL = DiagramLabel::quotedIdentifier;

  /** Counts how many times each hook fired, while still producing the real grammar's output. */
  private static final class CountingGrammar implements SequenceGrammar {
    private final SequenceGrammar delegate;
    private int callArrows;
    private int returns;
    private int throwsCount;
    private int incompletes;
    private int limitedNotes;

    CountingGrammar(SequenceGrammar delegate) {
      this.delegate = delegate;
    }

    int outcomes() {
      return returns + throwsCount + incompletes;
    }

    @Override
    public String header() {
      return delegate.header();
    }

    @Override
    public String participant(DiagramLabel label) {
      return delegate.participant(label);
    }

    @Override
    public String callArrow(DiagramLabel caller, DiagramLabel target, DiagramLabel signature) {
      callArrows++;
      return delegate.callArrow(caller, target, signature);
    }

    @Override
    public String returnArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel message) {
      returns++;
      return delegate.returnArrow(target, caller, message);
    }

    @Override
    public String throwArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel exceptionType) {
      throwsCount++;
      return delegate.throwArrow(target, caller, exceptionType);
    }

    @Override
    public String incomplete(DiagramLabel target) {
      incompletes++;
      return delegate.incomplete(target);
    }

    @Override
    public String limitedNote(DiagramLabel target, TreeWalk.Reason reason) {
      limitedNotes++;
      return delegate.limitedNote(target, reason);
    }

    @Override
    public String footer() {
      return delegate.footer();
    }
  }

  private static TraceNode leaf(String className, String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""));
  }

  private static TraceNode deepChain(int depth) {
    TraceNode current = leaf("Recursive", "bottom", List.of());
    for (var i = 0; i < depth; i++) {
      current = leaf("Recursive", "call" + i, List.of(current));
    }
    return current;
  }

  private static TraceNode cyclicRing() {
    var childHolder = new ArrayList<TraceNode>();
    var b = leaf("Ring", "b", childHolder);
    var a = leaf("Ring", "a", List.of(b));
    childHolder.add(a);
    return a;
  }

  private static final String QUOTE_AND_BREAK =
      "Victim\"\nparticipant InjectedActor\nclick InjectedActor href \"https://attacker.example\"";
  private static final String NOTE_FORGERY = "Victim\nnote over Victim: forged\n";
  private static final String ARROW_LOOKALIKE = "->>-->>-x-> --> -[#red]->";

  private static TraceNode hostileMetadata(String hostile) {
    return new TraceNode(
        new MethodSignature(
            hostile, hostile, List.of(new ParameterCapture(hostile, "\"v\"", false))),
        List.of(),
        new TraceOutcome.Threw(new RuntimeException(hostile)));
  }

  static Stream<Arguments> hostileTrees() {
    return Stream.of(
        Arguments.of("deep chain", deepChain(5_000), 5_001),
        // 3, not 2: TreeWalk visits a and b, then stops at the re-encountered a with onLimit —
        // three emissions total, matching MermaidSequenceDiagramRendererTest's own cyclic case.
        Arguments.of("cyclic ring", cyclicRing(), 3),
        Arguments.of("quote+break metadata", hostileMetadata(QUOTE_AND_BREAK), 1),
        Arguments.of("note forgery metadata", hostileMetadata(NOTE_FORGERY), 1),
        Arguments.of("arrow-lookalike metadata", hostileMetadata(ARROW_LOOKALIKE), 1));
  }

  /**
   * How many nodes a plain {@link TreeWalk} actually visits or stops at, independent of any
   * grammar.
   */
  private static int nodeCount(TraceNode root) {
    var count = new int[1];
    TreeWalk.walk(
        root, TraceNode::children, (n, depth) -> count[0]++, (n, depth, reason) -> count[0]++);
    return count[0];
  }

  /** How many of those nodes the walk stopped at instead of visiting (a cycle or the depth cap). */
  private static int limitCount(TraceNode root) {
    var count = new int[1];
    TreeWalk.walk(root, TraceNode::children, (n, depth) -> {}, (n, depth, reason) -> count[0]++);
    return count[0];
  }

  @ParameterizedTest(name = "mermaid: {0}")
  @MethodSource("hostileTrees")
  void mermaidEmitsExactlyOneCallArrowAndOneOutcomePerNode(
      String label, TraceNode root, int expected) {
    assertOneArrowAndOneOutcomePerNode(root, expected, MermaidSequenceGrammar.INSTANCE);
  }

  @ParameterizedTest(name = "plantuml: {0}")
  @MethodSource("hostileTrees")
  void plantUmlEmitsExactlyOneCallArrowAndOneOutcomePerNode(
      String label, TraceNode root, int expected) {
    assertOneArrowAndOneOutcomePerNode(root, expected, PlantUmlSequenceGrammar.INSTANCE);
  }

  private void assertOneArrowAndOneOutcomePerNode(
      TraceNode root, int expectedNodes, SequenceGrammar real) {
    assertThat(nodeCount(root)).isEqualTo(expectedNodes);

    var counting = new CountingGrammar(real);
    SequenceWalk.render(root, counting, PLAIN_LABEL, new StringBuilder());

    assertThat(counting.callArrows).as("call arrows").isEqualTo(expectedNodes);
    assertThat(counting.outcomes())
        .as("outcomes (return + throw + incomplete)")
        .isEqualTo(expectedNodes);
    assertThat(counting.limitedNotes).as("limited notes").isEqualTo(limitCount(root));
  }

  @Test
  void bothGrammarsAgreeOnNodeCountForTheSameHostileTree() {
    var root = hostileMetadata(QUOTE_AND_BREAK);

    var mermaid = new CountingGrammar(MermaidSequenceGrammar.INSTANCE);
    var plantUml = new CountingGrammar(PlantUmlSequenceGrammar.INSTANCE);
    SequenceWalk.render(root, mermaid, PLAIN_LABEL, new StringBuilder());
    SequenceWalk.render(root, plantUml, PLAIN_LABEL, new StringBuilder());

    assertThat(mermaid.callArrows).isEqualTo(plantUml.callArrows);
    assertThat(mermaid.outcomes()).isEqualTo(plantUml.outcomes());
  }
}
