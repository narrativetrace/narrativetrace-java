/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.tooling.init.ExecutionReport;
import java.util.List;

/**
 * The one line {@code narrativetraceRefreshSkills} prints — when it rewrote something, and when it
 * could not.
 *
 * <p>INTENT: this task is wired in front of {@code classes}, so it runs on every build. Anything it
 * says has to fit on one line and has to be worth reading; a build that says nothing is a build
 * where nothing needed doing, and that is the common case.
 *
 * <p><b>@llmNote</b> A resolution failure's own message runs to several lines. It is flattened
 * here, on purpose: "one line" is the contract, and a warning that breaks it buries the next task's
 * output.
 *
 * <p><b>@pattern</b> Same shape as {@link GlossaryScanAnnouncement} — the message is a pure
 * function with its own unit test, and the task only logs what it returns.
 */
final class SkillsRefreshAnnouncement {

  private SkillsRefreshAnnouncement() {}

  /**
   * What was rewritten, and to which carrier.
   *
   * @throws IllegalArgumentException when nothing was rewritten — that case is silence, not an
   *     announcement
   */
  static String refreshed(String coordinate, List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      throw new IllegalArgumentException("a refresh that rewrote nothing announces nothing");
    }
    return "NarrativeTrace: refreshed "
        + paths.size()
        + (paths.size() == 1 ? " file to " : " files to ")
        + coordinate
        + " — "
        + String.join(", ", paths);
  }

  /** What an execution actually wrote, or {@code null} when it wrote nothing. */
  static String applied(ExecutionReport report) {
    List<String> paths = paths(report, ExecutionReport.Status.APPLIED);
    return paths.isEmpty() ? null : refreshed(report.carrier(), paths);
  }

  /**
   * What the filesystem would not let it write, or {@code null} when it wrote everything. A
   * read-only file or a directory in the way would otherwise leave a project one version behind
   * with nothing to read about it.
   */
  static String refused(ExecutionReport report) {
    List<String> refusals =
        report.results().stream()
            .filter(result -> result.status() == ExecutionReport.Status.REFUSED)
            .map(result -> result.action().path() + " (" + oneLine(result.detail()) + ")")
            .toList();
    return refusals.isEmpty()
        ? null
        : "NarrativeTrace: could not refresh "
            + String.join(", ", refusals)
            + ". Run narrativetraceInit to see the whole plan.";
  }

  /**
   * The carrier is out of reach — offline, or no repository declares it — so nothing was checked
   * and nothing was changed.
   */
  static String unresolved(String reason) {
    return "NarrativeTrace: could not check whether this project's agent skills are up to date —"
        + " the skills carrier would not resolve ("
        + oneLine(reason)
        + "). Nothing was changed.";
  }

  private static List<String> paths(ExecutionReport report, ExecutionReport.Status status) {
    return report.results().stream()
        .filter(result -> result.status() == status)
        .map(result -> result.action().path().toString())
        .toList();
  }

  /** Whitespace runs, line breaks included, collapse to one space. */
  private static String oneLine(String reason) {
    return reason == null ? "no reason given" : reason.replaceAll("\\s+", " ").trim();
  }
}
