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
package ai.narrativetrace.cli.doctor;

import ai.narrativetrace.cli.doctor.checks.ApprovalTracesCheck;
import ai.narrativetrace.cli.doctor.checks.ExtensionRegisteredCheck;
import ai.narrativetrace.cli.doctor.checks.JdkVersionCheck;
import ai.narrativetrace.cli.doctor.checks.Junit5RangeCheck;
import ai.narrativetrace.cli.doctor.checks.LauncherCheck;
import ai.narrativetrace.cli.doctor.checks.LlmsBeforeYouStartCheck;
import ai.narrativetrace.cli.doctor.checks.OutputPropertyCheck;
import ai.narrativetrace.cli.doctor.checks.ParameterArg0Check;
import ai.narrativetrace.cli.doctor.checks.RedactionProofCheck;
import ai.narrativetrace.cli.doctor.checks.SilentSinkCheck;
import ai.narrativetrace.cli.doctor.checks.UnusedNotTracedImportCheck;
import java.util.List;

/**
 * The doctor's registry: eleven checks, in a fixed, deliberate order (toolchain, then
 * configuration, then traps — worst-surprise-last), run against one {@link DoctorSnapshot}. Adding
 * a check means appending a new entry here and to the constructor call below — the id is what stays
 * stable across releases, never the position in this list.
 */
public final class DoctorChecks {

  public static final List<DoctorCheck> ALL =
      List.of(
          new JdkVersionCheck(),
          new Junit5RangeCheck(),
          new LauncherCheck(),
          new OutputPropertyCheck(),
          new ExtensionRegisteredCheck(),
          new SilentSinkCheck(),
          new ParameterArg0Check(),
          new UnusedNotTracedImportCheck(),
          new RedactionProofCheck(),
          new ApprovalTracesCheck(),
          new LlmsBeforeYouStartCheck());

  private DoctorChecks() {}

  /** Runs every registered check against {@code snapshot} and computes the exit code. */
  public static DoctorReport run(DoctorSnapshot snapshot) {
    List<Finding> findings = ALL.stream().map(check -> check.run(snapshot)).toList();
    int exitCode = findings.stream().anyMatch(Finding::isFailing) ? 1 : 0;
    return new DoctorReport(findings, exitCode);
  }
}
