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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code --json} envelope for each of the verb's three paths, plus the refusal every one of
 * them can end in.
 *
 * <p>INTENT: an agent reads this, a person reads the human text, and the exit code is in BOTH so
 * neither has to infer it. Every envelope names the verb it came from, because an agent that
 * pipelines {@code draft} into {@code url} needs to be able to tell which answer it is holding.
 *
 * <p><b>@llmNote</b> Written by hand, like {@code DoctorRender}'s: this library declares zero
 * dependencies, and the shape is small and fixed. The quoting is the only subtle part and it is in
 * one method, so no envelope can escape differently from another.
 */
public final class FeedbackJson {

  private FeedbackJson() {}

  /** A drafted report: the facts, where the two files landed, and why no trace was attached. */
  public static String drafted(
      FeedbackReport report, String draftFile, String bodyFile, String traceNote) {
    Map<String, String> envelope = new LinkedHashMap<>();
    envelope.put("verb", "draft");
    envelope.put("status", "drafted");
    envelope.put("runtime", report.runtime());
    envelope.put("category", report.category().id());
    envelope.put("install", report.install());
    envelope.put("step", report.step());
    envelope.put("language", report.language());
    envelope.put("agent", report.agent().describe());
    envelope.put("draftFile", draftFile);
    envelope.put("bodyFile", bodyFile);
    envelope.put("traceNote", traceNote);
    return object(envelope, 0);
  }

  /** The gate refused: every field, rule and reason, and exit 2. */
  public static String refused(String verb, FeedbackDraft.Refused refused) {
    StringBuilder out = new StringBuilder("{\n  \"verb\": ").append(quote(verb));
    out.append(",\n  \"status\": \"refused\",\n  \"violations\": [\n");
    for (int i = 0; i < refused.violations().size(); i++) {
      ValueFreeViolation violation = refused.violations().get(i);
      out.append("    {\"field\": ")
          .append(quote(violation.field()))
          .append(", \"rule\": ")
          .append(quote(violation.rule().id()))
          .append(", \"reason\": ")
          .append(quote(violation.rule().reason()))
          .append('}')
          .append(i == refused.violations().size() - 1 ? "\n" : ",\n");
    }
    return out.append("  ],\n  \"exitCode\": 2\n}\n").toString();
  }

  /** The pre-filled URL, and the file whose contents the user pastes once it is open. */
  public static String url(String url, String bodyFile) {
    Map<String, String> envelope = new LinkedHashMap<>();
    envelope.put("verb", "url");
    envelope.put("status", "ready");
    envelope.put("url", url);
    envelope.put("bodyFile", bodyFile);
    return object(envelope, 0);
  }

  /** The {@code gh} line, printed and never run. */
  public static String gh(String command) {
    Map<String, String> envelope = new LinkedHashMap<>();
    envelope.put("verb", "gh");
    envelope.put("status", "ready");
    envelope.put("command", command);
    return object(envelope, 0);
  }

  /** There is no {@code gh} line to offer, and why — exit 1: the verb ran, the path is not open. */
  public static String ghUnavailable(String reason) {
    Map<String, String> envelope = new LinkedHashMap<>();
    envelope.put("verb", "gh");
    envelope.put("status", "unavailable");
    envelope.put("reason", reason);
    return object(envelope, 1);
  }

  private static String object(Map<String, String> envelope, int exitCode) {
    StringBuilder out = new StringBuilder("{\n");
    for (Map.Entry<String, String> member : envelope.entrySet()) {
      out.append("  ")
          .append(quote(member.getKey()))
          .append(": ")
          .append(quote(member.getValue()))
          .append(",\n");
    }
    return out.append("  \"exitCode\": ").append(exitCode).append("\n}\n").toString();
  }

  /** An RFC 8259 quoted string: the two mandatory escapes and every control character escaped. */
  private static String quote(String text) {
    StringBuilder out = new StringBuilder(text.length() + 2).append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> out.append(c < 0x20 ? String.format("\\u%04x", (int) c) : c);
      }
    }
    return out.append('"').toString();
  }
}
