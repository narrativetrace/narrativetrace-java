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

/**
 * {@code trap.approval-traces} — a stale {@code .received.nt} sits next to its approval baseline. A
 * {@code .received.nt} file only exists when the last run's rendered trace diverged from the
 * committed {@code .approved.nt} — reviewed and promoted, or reviewed and reverted, never left in
 * the tree either way.
 */
public final class ApprovalTracesCheck implements DoctorCheck {

  public static final String ID = "trap.approval-traces";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    List<String> received =
        snapshot.approvalDirFiles().keySet().stream()
            .filter(p -> p.endsWith(".received.nt"))
            .sorted()
            .toList();
    if (received.isEmpty()) {
      return Finding.pass(
          ID,
          "No stale .received.nt files in the approval-trace directories",
          DocAnchors.STRUCTURAL_TRACE_APPROVAL);
    }
    return Finding.fail(
        ID,
        "Stale .received.nt file(s): " + String.join(", ", received),
        "Review each .received.nt diff against its .approved.nt baseline, run"
            + " ./gradlew approveNarratives to promote it, then delete the .received.nt file —"
            + " never commit a .received.nt file.",
        DocAnchors.STRUCTURAL_TRACE_APPROVAL);
  }
}
