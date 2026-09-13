/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.core.tree.TreeWalk;

/**
 * Mermaid {@code sequenceDiagram} grammar: {@code ->>} call arrows, {@code -->>} returns, {@code
 * -x} throws, {@code Note over} for an in-flight outcome or a walk limit.
 *
 * <p>INTENT: Stateless — Mermaid's alias mode is a difference in the label-mapping function {@link
 * MermaidSequenceDiagramRenderer} passes to {@link SequenceWalk}, not in this grammar, so one
 * instance serves both {@code render} and {@code renderWithAliases}.
 */
final class MermaidSequenceGrammar implements SequenceGrammar {

  static final MermaidSequenceGrammar INSTANCE = new MermaidSequenceGrammar();

  private MermaidSequenceGrammar() {}

  @Override
  public String header() {
    return "sequenceDiagram\n";
  }

  @Override
  public String participant(DiagramLabel label) {
    return "    participant " + label.text() + "\n";
  }

  @Override
  public String callArrow(DiagramLabel caller, DiagramLabel target, DiagramLabel signature) {
    return "    " + caller.text() + "->>" + target.text() + ": " + signature.text() + "\n";
  }

  @Override
  public String returnArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel message) {
    return "    " + target.text() + "-->>" + caller.text() + ": " + message.text() + "\n";
  }

  @Override
  public String throwArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel exceptionType) {
    return "    " + target.text() + "-x" + caller.text() + ": " + exceptionType.text() + "\n";
  }

  @Override
  public String incomplete(DiagramLabel target) {
    return "    Note over " + target.text() + ": in-flight\n";
  }

  @Override
  public String limitedNote(DiagramLabel target, TreeWalk.Reason reason) {
    return "    Note over " + target.text() + ": " + reason.marker() + "\n";
  }

  @Override
  public String footer() {
    return "";
  }
}
