/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

/**
 * One per-element teaching note, emitted at every score.
 *
 * <p>INTENT: Clarity is a teacher, not a judge. Unlike {@link ClarityIssue} — a threshold-gated
 * actionable — an {@code ElementNote} explains the "why" behind an element's score whether that
 * score is good or bad, so a bare 0.86 is no longer a verdict without an appeal process.
 *
 * @param kind Element kind: {@code "method"}, {@code "class"}, {@code "parameter"}, or {@code
 *     "property"}.
 * @param element Fully-qualified element as it appears in issues: {@code "Class.method"} for
 *     methods and properties, the class name for classes, the parameter name for parameters.
 * @param score The element's own score from the same scorer the owning dimension uses, 0.0 (poor)
 *     to 1.0 (excellent).
 * @param note One-line plain-language explanation from {@link ElementNoteComposer}, no trailing
 *     period.
 */
public record ElementNote(String kind, String element, double score, String note) {}
