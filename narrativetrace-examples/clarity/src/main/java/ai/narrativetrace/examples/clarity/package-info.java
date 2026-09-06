/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Tutorial example: understanding clarity scores through deliberately varied naming quality.
 *
 * <p>This package is structured as a guided contrast. Some services use domain-specific,
 * well-factored names; others intentionally use vague or misleading names so the clarity analyzer
 * has something meaningful to critique.
 *
 * <h2>Suggested reading order</h2>
 *
 * <ol>
 *   <li>{@link ai.narrativetrace.examples.clarity.ClarityDemoExample}: executes the scenarios and
 *       prints both traces and clarity reports.
 *   <li>{@link ai.narrativetrace.examples.clarity.DefaultReservationService}: example of strong,
 *       domain-specific naming.
 *   <li>{@link ai.narrativetrace.examples.clarity.DefaultBookingManager}: middling naming that is
 *       serviceable but less expressive.
 *   <li>{@link ai.narrativetrace.examples.clarity.DefaultDataProcessor}: intentionally weak naming
 *       that the analyzer should penalize.
 * </ol>
 *
 * <p>INTENT: Use this package when you want to learn what the clarity subsystem rewards, what it
 * penalizes, and how those judgments connect back to real traces.
 */
package ai.narrativetrace.examples.clarity;
