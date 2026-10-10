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
import ai.narrativetrace.core.render.SpanId;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
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
 * <p>Each call arrow is followed by {@code grammar.spanNote(...)} citing the span's {@link SpanId},
 * the same id every other flavour prints for that call.
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
   * Walks every root in order, appending every arrow and note this grammar produces to {@code sb}.
   *
   * @param participantLabel how a raw class name becomes the label an arrow names
   */
  static void renderAll(
      List<TraceNode> roots,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    var ids = SpanId.idsOf(roots, null);
    for (var i = 0; i < roots.size(); i++) {
      render(roots.get(i), ids.get(i), grammar, participantLabel, sb);
    }
  }

  /**
   * The ids of one visited node's children, handed out as the walk reaches each child — in capture
   * order, which is the order {@link TreeWalk} visits them and the order {@link SpanId#idsOf} lists
   * them.
   */
  private static final class ChildIds {
    private final List<String> ids;
    private int next;

    ChildIds(List<String> ids) {
      this.ids = ids;
    }

    String take() {
      return ids.get(next++);
    }
  }

  private static void render(
      TraceNode root,
      String rootId,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    Deque<String> callerChain = new ArrayDeque<>();
    Deque<ChildIds> childIds = new ArrayDeque<>();
    TreeWalk.walk(
        root,
        TraceNode::children,
        (node, depth) -> {
          var id = childIds.isEmpty() ? rootId : childIds.peek().take();
          enterNode(node, id, callerChain, grammar, participantLabel, sb);
          childIds.push(new ChildIds(SpanId.idsOf(node.children(), id)));
        },
        (node, depth) -> {
          childIds.pop();
          exitNode(node, callerChain, grammar, participantLabel, sb);
        },
        (node, depth, reason) -> {
          var id = childIds.peek().take();
          limitedNode(node, id, callerChain, grammar, participantLabel, sb, reason);
        });
  }

  private static void enterNode(
      TraceNode node,
      String id,
      Deque<String> callerChain,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, grammar, participantLabel, sb);
    sb.append(grammar.spanNote(participantLabel.apply(target), DiagramLabel.spanId(id)));
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

  private static void limitedNode(
      TraceNode node,
      String id,
      Deque<String> callerChain,
      SequenceGrammar grammar,
      Function<String, DiagramLabel> participantLabel,
      StringBuilder sb,
      TreeWalk.Reason reason) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, grammar, participantLabel, sb);
    sb.append(grammar.spanNote(participantLabel.apply(target), DiagramLabel.spanId(id)));
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
