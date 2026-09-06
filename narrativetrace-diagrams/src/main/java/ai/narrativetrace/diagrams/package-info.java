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
 * for documentation and test artifacts.
 *
 * <p>INTENT: Use these when visual caller-callee flow is more useful than prose or nested lists.
 * They expose plain {@code render} methods and can be adapted with method references where a core
 * {@code NarrativeRenderer} is expected.
 */
package ai.narrativetrace.diagrams;
