/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * JUnit 4 rules for per-test and per-class trace output.
 *
 * <p>{@link ai.narrativetrace.junit4.NarrativeTraceRule} handles one test method at a time, while
 * {@link ai.narrativetrace.junit4.NarrativeTraceClassRule} accumulates traces across the class and
 * writes clarity artifacts and console summaries. {@link
 * ai.narrativetrace.junit4.NarrativeTestCase} wires the two together behind a single {@code
 * extends}, for suites free to spend their one superclass slot on it — the two rules stay the
 * primary API for suites that are not.
 *
 * <p>INTENT: Use this package in legacy JUnit 4 suites that still want the same trace output model
 * as the JUnit 5 extension.
 */
package ai.narrativetrace.junit4;
