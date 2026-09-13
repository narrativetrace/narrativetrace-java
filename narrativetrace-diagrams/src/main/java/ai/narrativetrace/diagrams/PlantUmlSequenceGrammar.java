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
 * PlantUML grammar: {@code ->} call arrows, {@code -->} returns, {@code -[#red]->} throws, {@code
 * hnote over} for an in-flight outcome or a walk limit.
 *
 * <p>INTENT: PlantUML has no alias mode, so this grammar is a stateless singleton with a single
 * caller: {@link PlantUmlSequenceDiagramRenderer#render}.
 */
final class PlantUmlSequenceGrammar implements SequenceGrammar {

  static final PlantUmlSequenceGrammar INSTANCE = new PlantUmlSequenceGrammar();

  private PlantUmlSequenceGrammar() {}

  @Override
  public String header() {
    return "@startuml\n";
  }

  @Override
  public String participant(DiagramLabel label) {
    return "participant " + label.text() + "\n";
  }

  @Override
  public String callArrow(DiagramLabel caller, DiagramLabel target, DiagramLabel signature) {
    return caller.text() + " -> " + target.text() + ": " + signature.text() + "\n";
  }

  @Override
  public String returnArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel message) {
    return target.text() + " --> " + caller.text() + ": " + message.text() + "\n";
  }

  @Override
  public String throwArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel exceptionType) {
    return target.text() + " -[#red]-> " + caller.text() + ": " + exceptionType.text() + "\n";
  }

  @Override
  public String incomplete(DiagramLabel target) {
    return "hnote over " + target.text() + " : in-flight\n";
  }

  @Override
  public String limitedNote(DiagramLabel target, TreeWalk.Reason reason) {
    return "hnote over " + target.text() + " : " + reason.marker() + "\n";
  }

  @Override
  public String footer() {
    return "@enduml";
  }
}
