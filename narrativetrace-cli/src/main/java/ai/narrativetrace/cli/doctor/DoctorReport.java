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

import java.util.List;

/**
 * The full result of a doctor run: every finding, worst-first-agnostic order (registration order —
 * rendering decides presentation order), plus the exit code the CLI process should return.
 *
 * <p>Exit codes: {@code 0} — every check passed; {@code 1} — the doctor ran and at least one check
 * failed. (A third value, {@code 2}, exists only at the CLI argument-parsing layer — see {@code
 * Cli} — for "could not run at all"; a completed {@code DoctorReport} is never a 2.)
 */
public record DoctorReport(List<Finding> findings, int exitCode) {

  public DoctorReport {
    findings = List.copyOf(findings);
    if (exitCode != 0 && exitCode != 1) {
      throw new IllegalArgumentException("a DoctorReport's exitCode is 0 or 1, got " + exitCode);
    }
  }

  public long failureCount() {
    return findings.stream().filter(Finding::isFailing).count();
  }

  public boolean allPassed() {
    return exitCode == 0;
  }
}
