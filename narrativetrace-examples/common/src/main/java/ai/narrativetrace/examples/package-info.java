/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Developer-oriented example utilities and walkthrough entry points.
 *
 * <p>This package is the front door to the examples module. The goal is not to expose a reusable
 * API, but to give developers a guided tour through the different ways NarrativeTrace can be used
 * in practice.
 *
 * <h2>How to read this module</h2>
 *
 * <p>Start with the package that matches the question you are trying to answer:
 *
 * <ul>
 *   <li>{@code ai.narrativetrace.examples.ecommerce}: "How does tracing look in a realistic Spring
 *       application with async work and multiple services?"
 *   <li>{@code ai.narrativetrace.examples.clarity}: "How does the clarity subsystem score good and
 *       bad naming?"
 *   <li>{@code ai.narrativetrace.examples.minecraft}: "How much do names alone change the quality
 *       of a trace?"
 * </ul>
 *
 * <p>{@link ai.narrativetrace.examples.PlantUmlImageRenderer} is a small utility for turning
 * generated {@code .puml} files into SVG assets so example output can be viewed outside text-only
 * tooling.
 *
 * <p>INTENT: Treat the examples module like a set of tutorials. Follow the main classes first, then
 * drill into the supporting services and records to see how the trace was shaped.
 */
package ai.narrativetrace.examples;
