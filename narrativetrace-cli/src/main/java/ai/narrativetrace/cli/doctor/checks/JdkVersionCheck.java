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
package ai.narrativetrace.cli.doctor.checks;

import ai.narrativetrace.cli.doctor.DocAnchors;
import ai.narrativetrace.cli.doctor.DoctorCheck;
import ai.narrativetrace.cli.doctor.DoctorSnapshot;
import ai.narrativetrace.cli.doctor.Finding;
import ai.narrativetrace.cli.doctor.Requirements;
import ai.narrativetrace.cli.doctor.Versions;

/**
 * {@code toolchain.jdk-version} — the running JDK against NarrativeTrace's minimum feature version
 * ({@value Requirements#MIN_JAVA_FEATURE_VERSION}), the Java analogue of the TypeScript reference's
 * {@code engines.node} check.
 */
public final class JdkVersionCheck implements DoctorCheck {

  public static final String ID = "toolchain.jdk-version";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    int running = Versions.leadingInt(snapshot.runningJavaVersion());
    int required = Requirements.MIN_JAVA_FEATURE_VERSION;
    if (running >= required) {
      return Finding.pass(
          ID,
          "Running Java "
              + snapshot.runningJavaVersion()
              + " satisfies the required "
              + required
              + "+",
          DocAnchors.INSTALLATION_PREREQUISITES);
    }
    return Finding.fail(
        ID,
        "Running Java "
            + snapshot.runningJavaVersion()
            + " does not satisfy the required "
            + required
            + "+",
        "Upgrade to a JDK "
            + required
            + "+ (sdkman, jenv, or your CI image) — NarrativeTrace's runtime and JUnit 5 extension"
            + " both require it.",
        DocAnchors.INSTALLATION_PREREQUISITES);
  }
}
