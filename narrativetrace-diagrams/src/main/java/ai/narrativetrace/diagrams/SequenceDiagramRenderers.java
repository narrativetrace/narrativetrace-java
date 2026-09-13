/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.api.render.NarrativeRenderer;

/**
 * The one place the JUnit 4 and JUnit 5 integrations get the two sequence-diagram renderers from,
 * instead of each naming {@link MermaidSequenceDiagramRenderer} and {@link
 * PlantUmlSequenceDiagramRenderer} itself.
 *
 * <p>INTENT: {@code narrativetrace-core} cannot depend on this module — diagrams depends on core,
 * never the reverse — so {@code TraceTestSupport}'s format dispatch takes the two renderers as
 * {@link NarrativeRenderer} parameters rather than naming their concrete types. Both {@code
 * NarrativeTraceExtension} (junit5) and {@code NarrativeTraceRule} (junit4) already depend on this
 * module to supply those parameters; before this class existed, each wrote its own {@code new
 * MermaidSequenceDiagramRenderer()::render} / {@code new PlantUmlSequenceDiagramRenderer()::render}
 * pair — the junit4/junit5 duplication cluster this class replaces with one shared source.
 */
public final class SequenceDiagramRenderers {

  private SequenceDiagramRenderers() {}

  /** A Mermaid renderer, stateless — every call is equivalent to any other. */
  public static NarrativeRenderer mermaid() {
    return new MermaidSequenceDiagramRenderer();
  }

  /** A PlantUML renderer, stateless — every call is equivalent to any other. */
  public static NarrativeRenderer plantUml() {
    return new PlantUmlSequenceDiagramRenderer();
  }
}
