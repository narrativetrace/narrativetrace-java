/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Sequence-diagram renderers for Mermaid and PlantUML.
 *
 * <p>{@link ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer} and {@link
 * ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer} turn trace trees into diagram markup
 * for documentation and test artifacts, and implement {@code
 * ai.narrativetrace.api.render.NarrativeRenderer}.
 *
 * <p>INTENT: Use these when visual caller-callee flow is more useful than prose or nested lists.
 * {@link ai.narrativetrace.diagrams.SequenceDiagramRenderers} is where a caller that only needs the
 * {@code NarrativeRenderer} interface — never {@code renderWithAliases} — gets an instance without
 * naming either concrete class.
 */
package ai.narrativetrace.diagrams;
