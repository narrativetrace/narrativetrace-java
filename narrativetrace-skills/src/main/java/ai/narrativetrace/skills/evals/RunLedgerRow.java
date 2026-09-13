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
package ai.narrativetrace.skills.evals;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One row the runner appends to {@code ledger/runs.jsonl} — one trial, any platform. {@code
 * caseName} serializes as {@code "case"}: the shorter Java-side name sidesteps the reserved word
 * without changing the wire shape the promotion renderer (and, if ever cross-checked, the
 * TypeScript reference's own ledger) reads.
 */
public record RunLedgerRow(
    String date,
    String skill,
    String caseName,
    Platform platform,
    String model,
    int trial,
    boolean pass) {

  public RunLedgerRow {
    requireText(date, "date");
    requireText(skill, "skill");
    requireText(caseName, "caseName");
    if (platform == null) {
      throw new IllegalArgumentException("a RunLedgerRow's platform must not be null");
    }
    requireText(model, "model");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("a RunLedgerRow's " + field + " must not be blank");
    }
  }

  public String result() {
    return pass ? "pass" : "fail";
  }

  /**
   * One JSON object per line — the shape {@link #toJsonLine()} writes, {@code ledger/runs.jsonl}'s
   * format.
   */
  public String toJsonLine() {
    return "{"
        + "\"date\":"
        + quote(date)
        + ","
        + "\"skill\":"
        + quote(skill)
        + ","
        + "\"case\":"
        + quote(caseName)
        + ","
        + "\"platform\":"
        + quote(platform.name().toLowerCase(java.util.Locale.ROOT))
        + ","
        + "\"model\":"
        + quote(model)
        + ","
        + "\"trial\":"
        + trial
        + ","
        + "\"result\":"
        + quote(result())
        + "}";
  }

  private static String quote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /**
   * A JSON string value: any run of escaped-pair-or-plain-character, {@code (?:\\.|[^"\\])*} —
   * unlike a bare {@code [^"]*}, this does not stop early at the {@code "} inside an escaped {@code
   * \"} that {@link #quote(String)} itself can produce.
   */
  private static String stringField(String json, String key) {
    Matcher m = Pattern.compile("\"" + key + "\":\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
    if (!m.find()) {
      throw new IllegalArgumentException("no \"" + key + "\" field in: " + json);
    }
    return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static int intField(String json, String key) {
    Matcher m = Pattern.compile("\"" + key + "\":(-?\\d+)").matcher(json);
    if (!m.find()) {
      throw new IllegalArgumentException("no \"" + key + "\" field in: " + json);
    }
    return Integer.parseInt(m.group(1));
  }

  /**
   * Parses one {@link #toJsonLine()}-shaped JSON object — this ledger's only producer and reader.
   */
  public static RunLedgerRow parseJsonLine(String json) {
    return new RunLedgerRow(
        stringField(json, "date"),
        stringField(json, "skill"),
        stringField(json, "case"),
        Platform.parse(stringField(json, "platform"))
            .orElseThrow(() -> new IllegalArgumentException("unknown platform in: " + json)),
        stringField(json, "model"),
        intField(json, "trial"),
        "pass".equals(stringField(json, "result")));
  }

  /** Every non-blank line of {@code ledger/runs.jsonl}-shaped content, one row per line. */
  public static List<RunLedgerRow> parseJsonl(String content) {
    List<RunLedgerRow> rows = new ArrayList<>();
    for (String line : content.split("\n", -1)) {
      String trimmed = line.strip();
      if (!trimmed.isEmpty()) {
        rows.add(parseJsonLine(trimmed));
      }
    }
    return List.copyOf(rows);
  }
}
