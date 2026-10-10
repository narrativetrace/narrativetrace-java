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
package ai.narrativetrace.tooling.doctor.checks;

import ai.narrativetrace.tooling.doctor.DocAnchors;
import ai.narrativetrace.tooling.doctor.DoctorCheck;
import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@code config.approval-mode} — committed {@code .approved.nt} baselines, with approval mode off.
 *
 * <p>INTENT: a baseline is the durable form of what a flow is supposed to do, and it only guards
 * anything while a run compares against it. With approval mode off the tests pass whatever the
 * structure does, so a committed baseline reads as protection the project does not have.
 *
 * <p><b>@llmNote</b> "On" is read where the switch is actually thrown: the plugin's {@code
 * approval} property in any build manifest ({@code approval.set(true)}, {@code approval = true}),
 * the test JVM's {@code narrativetrace.approval} property spelled in a manifest, or the same key as
 * a system or Gradle property. Manifests are read through {@link DoctorSnapshot#manifestText()},
 * which drops comment-only lines — a commented-out switch is not a switch. A {@code .received.nt}
 * alone is not a baseline: it is what a first approval run writes.
 */
public final class ApprovalModeCheck implements DoctorCheck {

  public static final String ID = "config.approval-mode";

  static final String KEY = "narrativetrace.approval";

  /**
   * The switch as a build script spells it, once whitespace is removed and case folded: the plugin
   * property ({@code approval.set(true)}, {@code approval = true}), the JVM flag ({@code
   * -Dnarrativetrace.approval=true}) or {@code systemProperty("narrativetrace.approval", "true")}.
   * Bounded on both sides, so {@code preapproval = true} and {@code approval = trueish} are not the
   * switch.
   */
  private static final Pattern SWITCHED_ON =
      Pattern.compile("(?<![\\w$])approval(?:\\.set\\(true\\)|=true|\",\"true\")(?![\\w$])");

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    boolean baselines =
        snapshot.approvalDirFiles().keySet().stream().anyMatch(p -> p.endsWith(".approved.nt"));
    if (!baselines) {
      return Finding.pass(
          ID,
          "No .approved.nt baselines — nothing for approval mode to compare yet",
          DocAnchors.STRUCTURAL_TRACE_APPROVAL);
    }
    if (switchedOn(snapshot)) {
      return Finding.pass(
          ID,
          ".approved.nt baselines exist and approval mode is on — every run compares them",
          DocAnchors.STRUCTURAL_TRACE_APPROVAL);
    }
    return Finding.fail(
        ID,
        ".approved.nt baselines exist but approval mode is off — nothing compares them",
        "Turn approval mode on: narrativeTrace { approval.set(true) } in build.gradle.kts (without"
            + " the plugin, give the test JVM -Dnarrativetrace.approval=true), then run the tests:"
            + " a run whose structure differs from its baseline fails and writes a .received.nt to"
            + " review.",
        DocAnchors.STRUCTURAL_TRACE_APPROVAL);
  }

  private static boolean switchedOn(DoctorSnapshot snapshot) {
    if (isTrue(snapshot.systemProperties().get(KEY))
        || isTrue(snapshot.gradleProperties().get(KEY))) {
      return true;
    }
    String compact = snapshot.buildScriptCode().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    return SWITCHED_ON.matcher(compact).find();
  }

  private static boolean isTrue(String value) {
    return value != null && value.trim().equalsIgnoreCase("true");
  }
}
