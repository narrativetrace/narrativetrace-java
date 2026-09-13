/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * The one traversal both sequence-diagram renderers share: bounded, cycle-safe, emitting exactly
 * one call arrow on the way into a node and exactly one outcome (return, throw, or in-flight note)
 * on the way out — {@link TreeWalk}'s enter/exit pair matches this shape exactly.
 *
 * <p>INTENT: {@link MermaidSequenceDiagramRenderer} and {@link PlantUmlSequenceDiagramRenderer}
 * used to each own a private copy of this enter/exit/limit pair, differing only in the grammar
 * (arrow syntax, note wording) and the label mapping they closed over. Extracted by composition,
 * not inheritance: this class owns the walk and the caller-chain bookkeeping — written once — a
 * {@link SequenceGrammar} owns everything the two formats disagree about, and the caller-supplied
 * {@code participantLabel} function owns how a raw class name becomes the {@link DiagramLabel} an
 * arrow names (identity/quoted for both formats' plain mode, an alias lookup for Mermaid's alias
 * mode) — deliberately a parameter here, never a grammar hook, per {@link DiagramLabel}'s own
 * invariant that a raw trace string never reaches a grammar.
 *
 * <p><b>@llmNote</b> A node beyond {@link TreeWalk#MAX_DEPTH} or already on the current path
 * ({@code onLimit}) still gets its own call arrow and outcome, rendered exactly like a leaf, plus
 * {@code grammar.limitedNote(...)} — the walk simply never descends into its children. This is the
 * behavior every existing renderer test, and {@code SequenceWalkContractTest}, pin for both
 * grammars at once.
 */
final class SequenceWalk {

  private SequenceWalk() {}

  /**
   * Walks one root, appending every arrow and note this grammar produces to {@code sb}.
   *
   * @param participantLabel how a raw class name becomes the label an arrow names
   */
  static void render(
      TraceNode root,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    Deque<String> callerChain = new ArrayDeque<>();
    TreeWalk.walk(
        root,
        TraceNode::children,
        (node, depth) -> enterNode(node, callerChain, grammar, participantLabel, sb),
        (node, depth) -> exitNode(node, callerChain, grammar, participantLabel, sb),
        (node, depth, reason) ->
            limitedNode(node, callerChain, grammar, participantLabel, sb, reason));
  }

  private static void enterNode(
      TraceNode node,
      Deque<String> callerChain,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, grammar, participantLabel, sb);
    callerChain.push(target);
  }

  private static void exitNode(
      TraceNode node,
      Deque<String> callerChain,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    var target = callerChain.pop();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendOutcome(node, caller, target, grammar, participantLabel, sb);
  }

  /** A node the walk stopped at: rendered as a leaf (arrow in, outcome out), plus the marker. */
  private static void limitedNode(
      TraceNode node,
      Deque<String> callerChain,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb,
      TreeWalk.Reason reason) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, grammar, participantLabel, sb);
    appendOutcome(node, caller, target, grammar, participantLabel, sb);
    sb.append(grammar.limitedNote(participantLabel.apply(target), reason));
  }

  private static void appendCallArrow(
      TraceNode node,
      String caller,
      String target,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    var method = DiagramLabel.identifier(node.signature().methodName());
    var params =
        node.signature().parameters().stream().map(p -> DiagramLabel.identifier(p.name())).toList();
    var signature = method.withParameters(params);
    sb.append(
        grammar.callArrow(
            participantLabel.apply(caller), participantLabel.apply(target), signature));
  }

  private static void appendOutcome(
      TraceNode node,
      String caller,
      String target,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    var outcome = node.outcome();
    if (outcome instanceof TraceOutcome.Returned returned) {
      var message = DiagramLabel.message(returned.renderedValue(), returned.structuredValue());
      sb.append(
          grammar.returnArrow(
              participantLabel.apply(target), participantLabel.apply(caller), message));
    } else if (outcome instanceof TraceOutcome.Threw threw) {
      var exceptionType = DiagramLabel.identifier(threw.exception().getClass().getSimpleName());
      sb.append(
          grammar.throwArrow(
              participantLabel.apply(target), participantLabel.apply(caller), exceptionType));
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append(grammar.incomplete(participantLabel.apply(target)));
    }
  }
}
