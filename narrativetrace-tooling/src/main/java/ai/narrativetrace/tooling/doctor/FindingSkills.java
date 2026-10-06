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
package ai.narrativetrace.tooling.doctor;

import java.util.Map;
import java.util.Set;

/**
 * Which catalogue skill fixes each class of finding — the table {@link Finding#skill()} reads.
 *
 * <p>INTENT: a finding tells a person what is wrong and how to fix it; an agent with the skills
 * installed can also be told WHICH tested procedure to follow next, by name, instead of improvising
 * one. That mapping belongs in one greppable place, not sprinkled across twelve checks where a new
 * check can quietly ship without anybody deciding.
 *
 * <p><b>@llmNote</b> The rule behind the table: a check that fires because the INSTALL is
 * incomplete points at {@code add-narrative-tracing} (it owns the dependency block, the {@code
 * -parameters} flag and the extension wiring); a check that fires on an already-wired project
 * behaving wrongly points at {@code narrativetrace-doctor} (its steps own proving redaction,
 * reading a rendered trace, and the approval-trace flow). Two ids carry NO skill, and that is a
 * decision rather than an omission: no skill installs a JDK, and no skill can install the skills.
 *
 * <p><b>@llmNote</b> Keyed on the id LITERALS rather than on each check's {@code ID} constant on
 * purpose: {@code ai.narrativetrace.tooling.doctor.checks} already depends on this package, and
 * importing it back here to read twelve constants would close that into a package cycle the
 * architecture gate forbids.
 */
public final class FindingSkills {

  /** The skill a first install follows: dependencies, {@code -parameters}, extension wiring. */
  static final String ADD_NARRATIVE_TRACING = "add-narrative-tracing";

  /** The skill a wired-but-misbehaving project follows: diagnosis, read-only. */
  static final String NARRATIVETRACE_DOCTOR = "narrativetrace-doctor";

  private static final Map<String, String> BY_ID =
      Map.of(
          "toolchain.junit5-range", ADD_NARRATIVE_TRACING,
          "toolchain.launcher", ADD_NARRATIVE_TRACING,
          "config.extension-registered", ADD_NARRATIVE_TRACING,
          "config.output-property", NARRATIVETRACE_DOCTOR,
          "trap.silent-sink", NARRATIVETRACE_DOCTOR,
          "trap.parameter-arg0", ADD_NARRATIVE_TRACING,
          "trap.unused-not-traced-import", NARRATIVETRACE_DOCTOR,
          "trap.redaction-proof", NARRATIVETRACE_DOCTOR,
          "trap.approval-traces", NARRATIVETRACE_DOCTOR,
          "trap.llms-before-you-start", ADD_NARRATIVE_TRACING);

  /**
   * Check ids that deliberately carry no skill. Listed rather than left to fall through, so {@link
   * #knows(String)} can tell "decided: none" from "nobody decided yet".
   */
  private static final Set<String> NO_SKILL =
      Set.of(
          // No skill upgrades a JDK: the fix is sdkman, jenv, or a CI image.
          "toolchain.jdk-version",
          // The finding IS that the skills are absent; naming one would point at a missing page.
          "config.skills-installed");

  private FindingSkills() {}

  /**
   * The catalogue skill that fixes this class of finding, or {@code null} when none does.
   *
   * <p>A {@code null} id answers {@code null} rather than throwing: this runs inside {@link
   * Finding}'s constructor, BEFORE its own validation, and an id-shaped mistake must still surface
   * as that constructor's {@code IllegalArgumentException} rather than as a lookup's NPE.
   */
  public static String forCheck(String id) {
    return id == null ? null : BY_ID.get(id);
  }

  /** Whether the table has a decision — a skill or a deliberate none — for this check id. */
  public static boolean knows(String id) {
    return id != null && (BY_ID.containsKey(id) || NO_SKILL.contains(id));
  }
}
