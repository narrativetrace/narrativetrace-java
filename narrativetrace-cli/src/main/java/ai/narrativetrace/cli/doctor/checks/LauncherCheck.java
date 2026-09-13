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

/**
 * {@code toolchain.launcher} — install completeness. A project that declares {@code junit-jupiter}
 * but never puts {@code org.junit.platform:junit-platform-launcher} on {@code testRuntimeOnly} gets
 * an unhelpful "no tests found" from Gradle instead of a real error — the Java analogue of the
 * TypeScript reference's sibling-package resolution check.
 */
public final class LauncherCheck implements DoctorCheck {

  public static final String ID = "toolchain.launcher";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    boolean usesJupiter = snapshot.declaresDependency("org.junit.jupiter:");
    if (!usesJupiter || snapshot.launcherOnTestRuntimeOnly()) {
      return Finding.pass(
          ID,
          usesJupiter
              ? "org.junit.platform:junit-platform-launcher is present on testRuntimeOnly"
              : "JUnit Jupiter is not in use — nothing to launch",
          DocAnchors.INSTALLATION_DEPENDENCIES);
    }
    return Finding.fail(
        ID,
        "junit-jupiter is declared but org.junit.platform:junit-platform-launcher is missing from"
            + " testRuntimeOnly",
        "Add testRuntimeOnly(\"org.junit.platform:junit-platform-launcher:1.11.4\") — without it,"
            + " a Gradle build with mismatched plugin versions can silently report \"0 tests"
            + " found\" instead of running the suite.",
        DocAnchors.INSTALLATION_DEPENDENCIES);
  }
}
