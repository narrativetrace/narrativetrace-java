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

/**
 * Renders a {@link DoctorReport} as human text or as JSON. The {@code narrativetrace-cli} module
 * takes zero dependencies (the same contract {@code narrativetrace-api} holds), so JSON is written
 * by hand rather than pulled in from a library — the shape is small and fixed, and every character
 * that needs escaping in a {@link Finding}'s free-text fields is a quote, a backslash, or a control
 * character.
 */
public final class DoctorRender {

  private DoctorRender() {}

  /** Worst-first: every failure, then every pass, then a one-line summary. */
  public static String renderHuman(DoctorReport report) {
    StringBuilder out = new StringBuilder();
    long failures = report.failureCount();
    out.append("narrativetrace doctor — ")
        .append(report.findings().size())
        .append(" check(s), ")
        .append(failures)
        .append(" finding(s)\n\n");
    report.findings().stream().filter(Finding::isFailing).forEach(f -> appendFinding(out, f));
    report.findings().stream().filter(f -> !f.isFailing()).forEach(f -> appendFinding(out, f));
    if (failures == 0) {
      out.append("All checks passed.\n");
    } else {
      out.append(failures)
          .append(" finding(s). Exit code ")
          .append(report.exitCode())
          .append(".\n");
    }
    return out.toString();
  }

  private static void appendFinding(StringBuilder out, Finding f) {
    out.append('[')
        .append(f.status())
        .append("] ")
        .append(f.id())
        .append(" — ")
        .append(f.message())
        .append('\n');
    if (f.isFailing()) {
      out.append("  fix:  ").append(f.fix()).append('\n');
    }
    out.append("  docs: ").append(f.docUrl()).append('\n');
  }

  /** {"findings": [...], "exitCode": N} — a stable, machine-readable mirror of renderHuman. */
  public static String renderJson(DoctorReport report) {
    StringBuilder out = new StringBuilder();
    out.append("{\n  \"findings\": [\n");
    for (int i = 0; i < report.findings().size(); i++) {
      Finding f = report.findings().get(i);
      out.append("    {\n")
          .append("      \"id\": ")
          .append(quote(f.id()))
          .append(",\n")
          .append("      \"status\": ")
          .append(quote(f.status() == Finding.Status.PASS ? "pass" : "fail"))
          .append(",\n")
          .append("      \"message\": ")
          .append(quote(f.message()))
          .append(",\n")
          .append("      \"fix\": ")
          .append(quote(f.fix()))
          .append(",\n")
          .append("      \"docUrl\": ")
          .append(quote(f.docUrl()))
          .append('\n')
          .append("    }")
          .append(i == report.findings().size() - 1 ? "\n" : ",\n");
    }
    out.append("  ],\n  \"exitCode\": ").append(report.exitCode()).append("\n}\n");
    return out.toString();
  }

  private static String quote(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
    return sb.toString();
  }
}
