/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.init.Action;
import ai.narrativetrace.tooling.init.ExecutionReport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a build says when it refreshes an install, and when it cannot. One line either way: a task
 * wired in front of {@code classes} runs on every build, and a paragraph there is noise nobody
 * reads twice.
 */
class SkillsRefreshAnnouncementTest {

  private static final String CARRIER = "ai.narrativetrace:narrativetrace-skills:0.2.4";

  @Test
  void namesEveryFileItRewroteAndTheCarrierItRewroteThemTo() {
    String line =
        SkillsRefreshAnnouncement.refreshed(
            CARRIER, List.of(".agents/skills/narrativetrace-doctor/SKILL.md", "AGENTS.md"));

    assertThat(line)
        .isEqualTo(
            "NarrativeTrace: refreshed 2 files to "
                + CARRIER
                + " — .agents/skills/narrativetrace-doctor/SKILL.md, AGENTS.md");
    assertThat(line.lines()).hasSize(1);
  }

  @Test
  void countsOneFileInTheSingular() {
    assertThat(SkillsRefreshAnnouncement.refreshed(CARRIER, List.of("AGENTS.md")))
        .startsWith("NarrativeTrace: refreshed 1 file to ");
  }

  @Test
  void keepsThePathsInThePlansOwnOrder() {
    assertThat(SkillsRefreshAnnouncement.refreshed(CARRIER, List.of("b", "a", "c")))
        .endsWith("— b, a, c");
  }

  /** Nothing rewritten is silence, not an announcement — so building one is a programming error. */
  @Test
  void refusesToAnnounceARefreshThatRewroteNothing() {
    assertThatThrownBy(() -> SkillsRefreshAnnouncement.refreshed(CARRIER, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theWarningSaysWhatCouldNotBeResolvedAndThatNothingWasWritten() {
    String line = SkillsRefreshAnnouncement.unresolved("no repositories are configured");

    assertThat(line)
        .isEqualTo(
            "NarrativeTrace: could not check whether this project's agent skills are up to date —"
                + " the skills carrier would not resolve (no repositories are configured). Nothing"
                + " was changed.");
    assertThat(line.lines()).hasSize(1);
  }

  // --- what a real execution produced -----------------------------------------------------

  private static ExecutionReport report(ExecutionReport.Applied... results) {
    return new ExecutionReport(CARRIER, List.of(results));
  }

  private static ExecutionReport.Applied applied(String path) {
    return new ExecutionReport.Applied(
        new Action.ReplaceBlock(Path.of(path), "old\n", "new\n"),
        ExecutionReport.Status.APPLIED,
        "");
  }

  private static ExecutionReport.Applied refused(String path) {
    return new ExecutionReport.Applied(
        new Action.ReplaceBlock(Path.of(path), "old\n", "new\n"),
        ExecutionReport.Status.REFUSED,
        "AccessDeniedException: read-only");
  }

  @Test
  void announcesOnlyWhatWasActuallyWritten() {
    String line =
        SkillsRefreshAnnouncement.applied(report(applied("AGENTS.md"), refused("other.md")));

    assertThat(line).contains("refreshed 1 file to ").endsWith("— AGENTS.md");
  }

  @Test
  void announcesNothingWhenNothingWasWritten() {
    assertThat(SkillsRefreshAnnouncement.applied(report(refused("AGENTS.md")))).isNull();
    assertThat(SkillsRefreshAnnouncement.applied(report())).isNull();
  }

  /**
   * A refusal here is the filesystem saying no — a read-only file, a directory in the way. Silence
   * would leave a project one version behind with nothing to read about it.
   */
  @Test
  void warnsAboutWhatItCouldNotWrite() {
    String line = SkillsRefreshAnnouncement.refused(report(applied("a.md"), refused("AGENTS.md")));

    assertThat(line)
        .isEqualTo(
            "NarrativeTrace: could not refresh AGENTS.md (AccessDeniedException: read-only)."
                + " Run narrativetraceInit to see the whole plan.");
    assertThat(line.lines()).hasSize(1);
  }

  @Test
  void saysNothingWhenEverythingWasWritten() {
    assertThat(SkillsRefreshAnnouncement.refused(report(applied("AGENTS.md")))).isNull();
  }

  @Test
  void namesEveryFileItCouldNotWriteOnTheOneLine() {
    String line = SkillsRefreshAnnouncement.refused(report(refused("a.md"), refused("b.md")));

    assertThat(line).contains("a.md").contains("b.md");
    assertThat(line.lines()).hasSize(1);
  }

  /** A resolution failure's own message is a paragraph; one line means one line. */
  @Test
  void aMultiLineReasonIsFlattenedIntoTheOneLine() {
    String line =
        SkillsRefreshAnnouncement.unresolved("could not find it\n  searched: here\nthere");

    assertThat(line.lines()).hasSize(1);
    assertThat(line).contains("could not find it searched: here there");
  }
}
