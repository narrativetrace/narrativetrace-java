/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromotionRendererTest {

  private static Skill skill(String canonicalName) {
    return new Skill(
        canonicalName,
        SkillClass.MECHANICAL,
        "d",
        null,
        "sixty-seconds",
        List.of(new SkillStep("s", new StepBody.CommandStep(List.of()), "v")),
        List.of(),
        List.of(),
        CommandVocabulary.JAVA);
  }

  @Test
  void everyCellIsNotYetRunWithNoRuns() {
    String rendered = PromotionRenderer.render(List.of(skill("narrativetrace-doctor")), List.of());
    assertThat(rendered)
        .contains("| `narrativetrace-doctor` | not yet run | not yet run | not yet run |");
  }

  @Test
  void aSinglePassingRunIsGreenNotApproved() {
    RunLedgerRow row =
        new RunLedgerRow(
            "2026-09-13", "narrativetrace-doctor", "happy-path", Platform.CLAUDE, "haiku", 1, true);
    String rendered =
        PromotionRenderer.render(List.of(skill("narrativetrace-doctor")), List.of(row));
    assertThat(rendered)
        .contains(
            "| `narrativetrace-doctor` | green (haiku, 2026-09-13) | not yet run | not yet run |");
  }

  @Test
  void aSingleFailingRunIsRed() {
    RunLedgerRow row =
        new RunLedgerRow(
            "2026-09-13", "narrativetrace-doctor", "happy-path", Platform.CODEX, "mini", 1, false);
    String rendered =
        PromotionRenderer.render(List.of(skill("narrativetrace-doctor")), List.of(row));
    assertThat(rendered).contains("red (mini, 2026-09-13)");
  }

  @Test
  void happyPathAndTriggerBothGreenIsApproved() {
    List<RunLedgerRow> runs =
        List.of(
            new RunLedgerRow(
                "2026-09-13",
                "narrativetrace-doctor",
                "happy-path",
                Platform.CLAUDE,
                "haiku",
                1,
                true),
            new RunLedgerRow(
                "2026-09-13",
                "narrativetrace-doctor",
                "trigger",
                Platform.CLAUDE,
                "haiku",
                1,
                true));
    String rendered = PromotionRenderer.render(List.of(skill("narrativetrace-doctor")), runs);
    assertThat(rendered)
        .contains("| `narrativetrace-doctor` | approved | not yet run | not yet run |");
  }

  @Test
  void oneGreenCaseAloneIsNotApproved() {
    List<RunLedgerRow> runs =
        List.of(
            new RunLedgerRow(
                "2026-09-13",
                "narrativetrace-doctor",
                "happy-path",
                Platform.CLAUDE,
                "haiku",
                1,
                true));
    String rendered = PromotionRenderer.render(List.of(skill("narrativetrace-doctor")), runs);
    assertThat(rendered)
        .contains(
            "| `narrativetrace-doctor` | green (haiku, 2026-09-13) | not yet run | not yet run |");
  }

  @Test
  void theLatestRunWinsTheCellWhenSeveralExist() {
    List<RunLedgerRow> runs =
        List.of(
            new RunLedgerRow(
                "2026-09-10",
                "narrativetrace-doctor",
                "happy-path",
                Platform.CODEX,
                "mini",
                1,
                false),
            new RunLedgerRow(
                "2026-09-13",
                "narrativetrace-doctor",
                "happy-path",
                Platform.CODEX,
                "mini",
                2,
                false));
    String rendered = PromotionRenderer.render(List.of(skill("narrativetrace-doctor")), runs);
    assertThat(rendered).contains("red (mini, 2026-09-13)").doesNotContain("2026-09-10");
  }

  @Test
  void rendersOneRowPerSkillInCatalogueOrder() {
    String rendered =
        PromotionRenderer.render(
            List.of(skill("narrativetrace-doctor"), skill("add-narrative-tracing")), List.of());
    int doctorIndex = rendered.indexOf("`narrativetrace-doctor`");
    int addIndex = rendered.indexOf("`add-narrative-tracing`");
    assertThat(doctorIndex).isPositive();
    assertThat(addIndex).isGreaterThan(doctorIndex);
  }
}
