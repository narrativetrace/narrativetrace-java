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
package ai.narrativetrace.tooling.init;

import java.nio.file.Path;

/**
 * Turns a plan or a report into the three things a caller needs: human text, a unified diff for
 * {@code --dry-run}, and JSON for whatever wraps the command.
 *
 * <p>INTENT: the same envelope shape the doctor already emits — {@code {carrier, actions[],
 * exitCode}} — so a skill that gates on one can gate on the other without learning a second format.
 *
 * <p><b>@llmNote</b> JSON is written by hand: this library takes zero dependencies. Paths are
 * emitted with {@code /} on every platform, so a report is the same text wherever it ran.
 */
public final class PlanRenderer {

  /**
   * What the plan and the report say about a page that was already ours in everything but a line.
   *
   * <p>Quoted verbatim by {@code documentation/agent-skills.md}'s "From a registry" section (rule
   * 8, docs as tests) — the marker pair below is that embed's source, never typed into the page.
   */
  // snippet:begin adoptedNote
  private static final String ADOPTED =
      "adopted: identical to this carrier's page, so only the provenance line is added";

  // snippet:end adoptedNote

  private PlanRenderer() {}

  /**
   * The whole of what a caller shows for a plan it has NOT applied: the JSON envelope, or the
   * summary followed by the unified diff.
   *
   * <p><b>@llmNote</b> Both, not either: the summary alone says nothing about what would change,
   * and the diff alone says nothing when there is nothing to change. Every entry point asks THIS
   * rather than composing its own pair — a rule that holds on one surface only is not a rule.
   */
  public static String render(InitPlan plan, boolean json) {
    return json ? renderJson(plan) : renderText(plan) + renderDiff(plan);
  }

  /** The whole of what a caller shows for a plan it has applied. */
  public static String render(ExecutionReport report, boolean json) {
    return json ? renderJson(report) : renderText(report);
  }

  /** One line per action, refusals marked, with the carrier and the counts at the top. */
  public static String renderText(InitPlan plan) {
    require(plan);
    StringBuilder out = new StringBuilder();
    out.append("narrativetrace — ").append(plan.carrier()).append('\n');
    if (plan.isEmpty()) {
      out.append("nothing to do.\n");
      return out.toString();
    }
    out.append(plan.actions().size())
        .append(" action(s), ")
        .append(plan.refusals().size())
        .append(" refusal(s)\n\n");
    for (Action action : plan.actions()) {
      out.append(pad(action.kind()))
          .append(display(action.path()))
          .append(note(action))
          .append('\n');
    }
    return out.toString();
  }

  /** One line per action, each saying whether it happened. */
  public static String renderText(ExecutionReport report) {
    require(report);
    long applied = report.results().size() - refused(report);
    StringBuilder out = new StringBuilder();
    out.append("narrativetrace — ").append(report.carrier()).append('\n');
    out.append(applied).append(" applied, ").append(refused(report)).append(" refused\n\n");
    for (ExecutionReport.Applied result : report.results()) {
      out.append(pad(result.status() == ExecutionReport.Status.APPLIED ? "applied" : "refused"))
          .append(pad(result.action().kind()))
          .append(display(result.action().path()))
          .append(result.detail().isEmpty() ? note(result.action()) : " — " + result.detail())
          .append('\n');
    }
    return out.toString();
  }

  /** The full unified diff a dry run shows: one file at a time, in plan order. */
  public static String renderDiff(InitPlan plan) {
    require(plan);
    StringBuilder out = new StringBuilder();
    for (Action action : plan.actions()) {
      if (action instanceof Action.FileEdit edit) {
        out.append(UnifiedDiff.render(display(action.path()), edit.before(), edit.after()));
      } else {
        out.append("# ")
            .append(display(action.path()))
            .append(" — ")
            .append(describe(action))
            .append('\n');
      }
    }
    return out.toString();
  }

  /** {@code {"carrier", "actions":[{kind,path,status}], "exitCode"}} for a plan not yet applied. */
  public static String renderJson(InitPlan plan) {
    require(plan);
    StringBuilder out = new StringBuilder();
    open(out, plan.carrier());
    for (int i = 0; i < plan.actions().size(); i++) {
      Action action = plan.actions().get(i);
      String status = action instanceof Action.Refuse ? "refused" : "planned";
      row(out, action, status, i == plan.actions().size() - 1);
    }
    return close(out, plan.exitCode());
  }

  /** The same envelope for a plan that has been applied. */
  public static String renderJson(ExecutionReport report) {
    require(report);
    StringBuilder out = new StringBuilder();
    open(out, report.carrier());
    for (int i = 0; i < report.results().size(); i++) {
      ExecutionReport.Applied result = report.results().get(i);
      String status = result.status() == ExecutionReport.Status.APPLIED ? "applied" : "refused";
      row(out, result.action(), status, i == report.results().size() - 1);
    }
    return close(out, report.exitCode());
  }

  private static void open(StringBuilder out, String carrier) {
    out.append("{\n  \"carrier\": ").append(quote(carrier)).append(",\n  \"actions\": [\n");
  }

  private static void row(StringBuilder out, Action action, String status, boolean last) {
    out.append("    {\"kind\": ")
        .append(quote(action.kind()))
        .append(", \"path\": ")
        .append(quote(display(action.path())))
        .append(", \"status\": ")
        .append(quote(status))
        .append('}')
        .append(last ? "\n" : ",\n");
  }

  private static String close(StringBuilder out, int exitCode) {
    return out.append("  ],\n  \"exitCode\": ").append(exitCode).append("\n}\n").toString();
  }

  private static long refused(ExecutionReport report) {
    return report.results().stream()
        .filter(result -> result.status() == ExecutionReport.Status.REFUSED)
        .count();
  }

  private static String describe(Action action) {
    return action instanceof Action.Refuse refusal ? "refused: " + refusal.reason() : action.kind();
  }

  /**
   * What a line says after the path: why a refusal refused, or — for an adoption — that nothing of
   * anybody's was overwritten. Asked by BOTH the plan's text and the report's, because an adoption
   * a person only sees in a preview is an adoption they were never told about.
   */
  private static String note(Action action) {
    if (action instanceof Action.Refuse refusal) {
      return " — " + refusal.reason();
    }
    if (action instanceof Action.ReplaceLink replacement) {
      return " — replaces the symbolic link "
          + display(replacement.link())
          + " → "
          + replacement.target();
    }
    return action instanceof Action.AdoptPage ? " — " + ADOPTED : "";
  }

  /**
   * A path as every platform should read it: its own elements joined with {@code /}, so a Windows
   * separator never reaches the output and a backslash that is part of a NAME survives.
   */
  private static String display(Path path) {
    StringBuilder out = new StringBuilder();
    for (Path element : path) {
      out.append(out.length() == 0 ? "" : "/").append(element);
    }
    return out.toString();
  }

  private static String pad(String word) {
    return word.length() >= 8 ? word + " " : (word + "        ").substring(0, 8);
  }

  private static void require(Object rendered) {
    if (rendered == null) {
      throw new IllegalArgumentException("there is nothing to render");
    }
  }

  /** Quotes and escapes a JSON string: the paths and reasons here are the only free text. */
  private static String quote(String value) {
    StringBuilder out = new StringBuilder(value.length() + 2).append('"');
    value.chars().forEach(character -> escape(out, character));
    return out.append('"').toString();
  }

  private static void escape(StringBuilder out, int character) {
    if (character == '"' || character == '\\') {
      out.append('\\').append((char) character);
    } else if (character < 0x20) {
      out.append(String.format("\\u%04x", character));
    } else {
      out.append((char) character);
    }
  }
}
