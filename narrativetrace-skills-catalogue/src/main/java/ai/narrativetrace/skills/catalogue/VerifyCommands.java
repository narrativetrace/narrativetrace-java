/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

/**
 * The commands and done-conditions of {@code narrativetrace-verify}, in the closed Java vocabulary.
 *
 * <p>Each command is what an ADOPTER runs in their own project; {@code SkillReplayer} maps every
 * one onto the {@code sixty-seconds} fixture for Tier A2 replay.
 */
public final class VerifyCommands {

  /** Runs the one test that drives the changed path, with tracing on (the extension's default). */
  public static final String RUN_THE_PATH =
      "./gradlew test --tests <the smallest test that drives the real path>";

  /** Runs the whole suite — what approval mode compares, every traced test at once. */
  public static final String RUN_THE_SUITE = "./gradlew test";

  /** Lists the value-free structural traces the run wrote. */
  public static final String FIND_STRUCTURAL =
      "find build/narrativetrace/structural -name \"*.nt\"";

  /** Lists the value-bearing Markdown narratives the run wrote. */
  public static final String FIND_NARRATIVES = "find build/narrativetrace/traces -name \"*.md\"";

  /** Lists the review copies an approval-mode run wrote beside the baselines. */
  public static final String FIND_RECEIVED = "find src/test/narratives -name \"*.received.nt\"";

  /** Promotes every reviewed {@code .received.nt} to its {@code .approved.nt} baseline. */
  public static final String APPROVE = "./gradlew approveNarratives";

  private VerifyCommands() {}
}
