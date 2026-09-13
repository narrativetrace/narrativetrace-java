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
 * The per-format literals {@link SequenceWalk} needs to render one trace tree as a sequence diagram
 * — everything Mermaid and PlantUML disagree about, in one place.
 *
 * <p>INTENT: {@link MermaidSequenceDiagramRenderer} and {@link PlantUmlSequenceDiagramRenderer}
 * share one traversal ({@link SequenceWalk}); the two formats differ only in header/footer text,
 * participant declaration syntax, arrow syntax, return/throw/limited-node notation, and — for
 * Mermaid's alias mode — how a class name becomes the label an arrow names (a walk concern, not a
 * grammar one; see {@link SequenceWalk#render}). Every hook here is a pure function of its inputs.
 *
 * <p><b>@llmNote</b> Every hook that carries trace-derived text takes a {@link DiagramLabel}, never
 * a {@code String} — see {@link DiagramLabel}'s own class doc. A grammar implementation cannot
 * receive a raw trace string; only {@link SequenceWalk} ever turns a raw class name into a label,
 * through the label-mapping function each renderer supplies, before any hook here is called.
 */
interface SequenceGrammar {

  /** The opening line(s) of the diagram, before any participant declaration. */
  String header();

  /**
   * One participant declaration line, already composed (plain display name, or Mermaid's alias).
   */
  String participant(DiagramLabel label);

  /** One call arrow, caller to target, naming the call signature. */
  String callArrow(DiagramLabel caller, DiagramLabel target, DiagramLabel signature);

  /** One return arrow, target back to caller, carrying the return message. */
  String returnArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel message);

  /** One throw arrow, target back to caller, naming the exception type. */
  String throwArrow(DiagramLabel target, DiagramLabel caller, DiagramLabel exceptionType);

  /** The note for a node whose outcome never arrived (an in-flight call). */
  String incomplete(DiagramLabel target);

  /** The note appended after a node the walk stopped at instead of descending into. */
  String limitedNote(DiagramLabel target, TreeWalk.Reason reason);

  /** The closing line(s) of the diagram, after every root has been walked. */
  String footer();
}
