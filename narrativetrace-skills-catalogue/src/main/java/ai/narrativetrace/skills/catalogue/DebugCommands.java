/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

/**
 * The commands of {@code narrativetrace-debug}, in the closed Java vocabulary.
 *
 * <p>Each command is what an ADOPTER runs in their own project; {@code SkillReplayer} maps every
 * one onto the {@code sixty-seconds} fixture for Tier A2 replay. The trace-finding commands the
 * debug loop shares with the verify loop are {@link VerifyCommands}' own.
 */
public final class DebugCommands {

  /** Runs the one test that drives the reported input through the real path, tracing on. */
  public static final String REPRODUCE =
      "./gradlew test --tests <the test that reproduces the symptom>";

  /** Lists the sequence diagrams the run wrote — ordering across components and threads. */
  public static final String FIND_DIAGRAMS = "find build/narrativetrace/diagrams -name \"*.mmd\"";

  private DebugCommands() {}
}
