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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code trap.parameter-arg0} — rendered trace output containing {@code arg0}-style placeholders,
 * the empirical signature of a project compiled without {@code -parameters}. Reads rendered output
 * files, exactly as the TypeScript reference's identically-named check does — this is the post-hoc
 * counterpart to {@link LlmsBeforeYouStartCheck}'s pre-flight build-file scan.
 */
public final class ParameterArg0Check implements DoctorCheck {

  public static final String ID = "trap.parameter-arg0";

  private static final Pattern ARG_PLACEHOLDER = Pattern.compile("\\barg[0-9]+\\b");

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    List<String> offending =
        snapshot.outputFiles().entrySet().stream()
            .filter(e -> ARG_PLACEHOLDER.matcher(e.getValue()).find())
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    if (offending.isEmpty()) {
      return Finding.pass(
          ID,
          "No rendered output shows arg0-style parameter placeholders",
          DocAnchors.TROUBLESHOOTING_ARG0);
    }
    return Finding.fail(
        ID,
        "Rendered output shows arg0-style placeholders in: " + String.join(", ", offending),
        "Pass -parameters to javac (tasks.withType<JavaCompile> { options.compilerArgs.add("
            + "\"-parameters\") } — already the default toolchain setting in this repo's own build,"
            + " confirm your project's build carries it too).",
        DocAnchors.TROUBLESHOOTING_ARG0);
  }
}
