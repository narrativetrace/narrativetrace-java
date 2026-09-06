/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.fuzz;

/**
 * The one place the coverage-guided tier's time budget is written down.
 *
 * <p>INTENT: {@code @FuzzTest(maxDuration = ...)} needs a compile-time constant, so the budget
 * cannot come from a Gradle property or an environment variable without being repeated per target.
 * Every target references {@link #PER_TARGET}; changing the schedule's cost is changing this line.
 *
 * <p><b>@llmNote</b> The budget applies only in fuzzing mode ({@code JAZZER_FUZZ=1}, which {@code
 * ./gradlew fuzz} sets). In an ordinary {@code check} the same targets run in Jazzer's regression
 * mode, replaying the committed seed corpus in milliseconds, and the duration is ignored.
 */
public final class FuzzBudget {

  /**
   * How long each target fuzzes on the schedule. Four targets, so a scheduled run costs about
   * twenty minutes of the JDK-21 job — the same order as the benchmark run it sits beside.
   */
  public static final String PER_TARGET = "5m";

  private FuzzBudget() {}
}
