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
 * {@code trap.llms-before-you-start} — the pre-flight counterpart to {@link ParameterArg0Check}: a
 * project that depends on {@code narrativetrace-junit5} but whose own build file never declares the
 * {@code -parameters} compiler flag is set up to degrade to {@code arg0}-style names the first time
 * it renders a trace, before any output has been produced to catch it empirically. This is the Java
 * shape of {@code llms.txt}'s "Before you start" block — codified as a check instead of left as a
 * paragraph a reader might skip, the same principle behind the TypeScript reference's ESM/{@code
 * "type": "module"} pre-flight check.
 */
public final class LlmsBeforeYouStartCheck implements DoctorCheck {

  public static final String ID = "trap.llms-before-you-start";

  private static final String PARAMETERS_FLAG = "-parameters";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    boolean tracingInUse = snapshot.declaresDependency("ai.narrativetrace:narrativetrace-junit5");
    if (!tracingInUse) {
      return Finding.pass(
          ID,
          "narrativetrace-junit5 is not in use — nothing needs -parameters yet",
          DocAnchors.SIXTY_SECONDS_BEFORE_YOU_START);
    }
    if (snapshot.compilerArgsDeclareParameters()
        || snapshot.buildFileContent().contains(PARAMETERS_FLAG)) {
      return Finding.pass(
          ID,
          "The build declares the -parameters compiler flag",
          DocAnchors.SIXTY_SECONDS_BEFORE_YOU_START);
    }
    return Finding.fail(
        ID,
        "narrativetrace-junit5 is declared but the build never adds the -parameters compiler flag",
        "Add tasks.withType<JavaCompile> { options.compilerArgs.add(\"-parameters\") } before your"
            + " first traced test run — set up after the fact, parameter names silently degrade to"
            + " arg0, arg1, ... in every trace already rendered.",
        DocAnchors.SIXTY_SECONDS_BEFORE_YOU_START);
  }
}
