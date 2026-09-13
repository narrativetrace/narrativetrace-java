/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Sporadic-lanes policy, rule 3 (owner-ruled 2026-09-13): "Deterministic tiers first, always. A
 * cheaper-lane run refuses to start unless the skill's Tier A lints and Tier A2 replay are green at
 * HEAD — a Tier B trial on a skill whose replay is red is quota burned on a known defect." {@link
 * RunCommand} is injectable (the same seam {@code SkillReplayer} would use) so a unit test can fake
 * the command without a real {@code ./gradlew} invocation. Mirrors the TypeScript reference's
 * {@code evals/tier-precondition.ts}.
 */
public final class TierPrecondition {

  private TierPrecondition() {}

  /** Runs {@code command} in {@code cwd}, throwing on a non-zero exit. */
  @FunctionalInterface
  public interface RunCommand {
    void run(List<String> command, Path cwd) throws IOException, InterruptedException;
  }

  /** The Tier A lint classes and the Tier A2 replay class, run together via {@code ./gradlew}. */
  static final List<String> TIER_A_AND_A2_TEST_PATTERNS =
      List.of(
          "ai.narrativetrace.skills.lint.*", "ai.narrativetrace.skills.replay.TierA2ReplayTest");

  /** The real, closed-vocabulary command this precondition runs when not faked by a test. */
  public static void runViaProcessBuilder(List<String> command, Path cwd)
      throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command).directory(cwd.toFile()).inheritIO().start();
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IOException(String.join(" ", command) + " exited " + exit);
    }
  }

  /**
   * The exact {@code ./gradlew} invocation this precondition runs — the closed per-port vocabulary.
   */
  public static List<String> command() {
    List<String> args = new java.util.ArrayList<>();
    args.add("./gradlew");
    args.add(":narrativetrace-skills:test");
    for (String pattern : TIER_A_AND_A2_TEST_PATTERNS) {
      args.add("--tests");
      args.add(pattern);
    }
    return List.copyOf(args);
  }

  /** Throws with a quota-preserving explanation when the skills module's Tier A/A2 suite is red. */
  public static void assertDeterministicTiersGreen(Path repoRoot, RunCommand run) {
    try {
      run.run(command(), repoRoot);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException(
          "Tier A lints / Tier A2 replay are not green at HEAD for narrativetrace-skills — a"
              + " sporadic-lane (codex/gemini) trial refuses to start on top of a known defect."
              + " Fix narrativetrace-skills' own tests before spending quota here.",
          e);
    }
  }
}
