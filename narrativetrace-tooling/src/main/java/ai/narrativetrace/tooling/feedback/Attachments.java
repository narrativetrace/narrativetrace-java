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
package ai.narrativetrace.tooling.feedback;

/**
 * The only two attachment kinds a report may carry: the doctor's own JSON report, and at most one
 * structural trace.
 *
 * <p>INTENT: a closed set, because every other artifact a project has carries runtime values. A
 * rendered narrative, a log file and a source file are all refused by construction here rather than
 * by a rule — there is no field to put them in.
 *
 * <p><b>@llmNote</b> {@code doctorReport} and {@code doctorUnavailable} are exclusive and
 * exhaustive (Q3 as ruled): exactly one is non-empty. "No doctor report and no reason" would read,
 * to triage, as a reporter who did not bother rather than as a project whose build cannot run the
 * task — and those two get different answers.
 *
 * @param doctorReport the doctor's JSON report verbatim, or {@code ""}
 * @param doctorUnavailable why there is no doctor report, or {@code ""} when there is one
 * @param structuralTrace one {@code .nt} file's content, or {@code ""}
 */
public record Attachments(String doctorReport, String doctorUnavailable, String structuralTrace) {

  public Attachments {
    if (doctorReport == null || doctorUnavailable == null || structuralTrace == null) {
      throw new IllegalArgumentException("an absent attachment is \"\", never null");
    }
    if (doctorReport.isBlank() == doctorUnavailable.isBlank()) {
      throw new IllegalArgumentException(
          "a report carries the doctor's JSON or says why it has none, never both and never"
              + " neither");
    }
  }

  /** A report with the doctor's JSON, and optionally one structural trace. */
  public static Attachments of(String doctorReport, String structuralTrace) {
    return new Attachments(doctorReport, "", structuralTrace);
  }

  /** A report whose project could not run the doctor, with the reason it could not. */
  public static Attachments withoutDoctorReport(String reason, String structuralTrace) {
    return new Attachments("", reason, structuralTrace);
  }

  /** Whether the doctor's own JSON is attached. */
  public boolean hasDoctorReport() {
    return !doctorReport.isBlank();
  }

  /** Whether a structural trace is attached. */
  public boolean hasStructuralTrace() {
    return !structuralTrace.isBlank();
  }
}
