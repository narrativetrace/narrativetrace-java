/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

/**
 * The commands of {@code narrativetrace-explain}, in the closed Java vocabulary.
 *
 * <p>Each command is what an ADOPTER runs in their own project; {@code SkillReplayer} maps every
 * one onto the {@code sixty-seconds} fixture for Tier A2 replay. The trace-finding commands this
 * skill shares with the verify loop are {@link VerifyCommands}' own — one home per concept.
 *
 * <p>INTENT: the one command of its own is the run, and its placeholder is the difference between
 * this skill and the verify skill. Verify runs "the smallest test that drives the real path" of a
 * CHANGE it just made; explain runs the scenario that already exercises the flow somebody ASKED
 * about, and writes one only where none exists.
 */
public final class ExplainCommands {

  /** Runs the one existing scenario that exercises the flow asked about, with tracing on. */
  public static final String RUN_THE_SCENARIO =
      "./gradlew test --tests <the test that exercises the flow you were asked about>";

  private ExplainCommands() {}
}
