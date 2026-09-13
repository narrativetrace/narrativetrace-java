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
package ai.narrativetrace.skills.catalogue;

/**
 * Command and verify literals shared by both skills — deduplicated so the duplication ratchet
 * catches a second hand-copied literal, mirroring the TypeScript reference's {@code
 * doctor-commands.ts}.
 *
 * <p>Every command here is what an ADOPTER runs, in their own project — never a fixture path from
 * this repository (frozen ruling, {@code agent-skills-2026-09-12.md} §7 ruling 9: file paths
 * generalise, never a fixture path). {@code SkillReplayer} maps each of these onto the {@code
 * sixty-seconds} fixture for Tier A2 replay; that mapping is the replayer's own concern, not
 * something these constants encode. Every command is a single, unpiped {@code ./gradlew}/{@code
 * git}/{@code find} invocation — never a shell pipeline.
 *
 * <p>{@code narrativetraceDoctor} (the {@code ai.narrativetrace} Gradle plugin task) runs the same
 * doctor checks {@code narrativetrace-cli}'s {@code doctor} verb does, in-process, and never fails
 * the build on the doctor's own findings — findings are a normal, expected outcome, not a crash.
 *
 * <p>The two {@code VERIFY_*} constants are prose, not commands — the redaction step's verify names
 * the {@code trap.redaction-proof} check by id, not a finding count (frozen ruling,
 * agent-skills-2026-09-12.md §7 ruling 9) — but they also name {@link #DOCTOR_REPORT_PATH}, the
 * relative path {@code SkillReplayer} reads (resolved against the fixture) to check the same claim
 * mechanically.
 */
public final class DoctorCommands {

  /** Builds this project with its real toolchain — the install step's verify. */
  public static final String BUILD_PROJECT = "./gradlew build";

  /** Runs the first trace exactly as the "Install and first trace" walkthrough shows it. */
  public static final String RUN_PROJECT = "./gradlew run";

  /**
   * Runs the {@code ai.narrativetrace} Gradle plugin's {@code narrativetraceDoctor} task: the
   * doctor's eleven checks, in-process, against this project.
   */
  public static final String RUN_DOCTOR_GRADLE = "./gradlew narrativetraceDoctor";

  /** Where {@link #RUN_DOCTOR_GRADLE} leaves its report, relative to the project it diagnosed. */
  public static final String DOCTOR_REPORT_PATH = "build/narrativetrace/doctor-report.json";

  public static final String VERIFY_ELEVEN_FINDINGS =
      "the JSON report at " + DOCTOR_REPORT_PATH + " is well-formed, naming all eleven findings";

  public static final String VERIFY_REDACTION_FINDING_PRESENT =
      "the report at " + DOCTOR_REPORT_PATH + " names the trap.redaction-proof check";

  /** Lists every rendered trace under the project's default output directory. */
  public static final String FIND_RENDERED_TRACES = "find build/narrativetrace -name \"*.md\"";

  /** No stale approval-mismatch artifact after a run — scoped to where baselines actually live. */
  public static final String NO_STALE_RECEIVED_FILE = "git status --short src/test/narratives";

  private DoctorCommands() {}
}
