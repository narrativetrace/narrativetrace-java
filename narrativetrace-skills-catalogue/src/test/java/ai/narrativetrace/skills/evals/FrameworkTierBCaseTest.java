/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.frameworks.CheckBinding;
import ai.narrativetrace.tooling.frameworks.FrameworkRow;
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The framework table's last column — the Tier B case that exercises a row (Phase 6, D4) — names a
 * case that really exists here: a {@code <skill>/<case>} directory with a prompt, a grader and a
 * fixture. The table ships in {@code narrativetrace-tooling}, which cannot see this directory, so
 * the catalogue holds the reference instead of the table holding a path it cannot check.
 */
class FrameworkTierBCaseTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));
  private static final Path EVALS_DIR = REPO_ROOT.resolve("narrativetrace-skills-catalogue/evals");

  static List<FrameworkRow> rowsWithATierBCase() {
    return FrameworkTable.ROWS.stream()
        .filter(row -> !FrameworkRow.NO_TIER_B_CASE.equals(row.tierBCase()))
        .toList();
  }

  /** D4: the runtime's main web framework carries the first case, graded on its own check. */
  @Test
  void theSpringRowIsExercisedByTheSpringBootInitPromptCase() {
    assertThat(FrameworkTable.row("spring").orElseThrow().tierBCase())
        .isEqualTo("add-narrative-tracing/init-prompt-spring-boot-project");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rowsWithATierBCase")
  void aNamedCaseIsACaseWithAPromptAGraderAndAFixture(FrameworkRow row) {
    Path namedCase = EVALS_DIR.resolve(row.tierBCase());

    assertThat(namedCase.resolve("prompt.md")).as(row.id()).isRegularFile();
    assertThat(namedCase.resolve("graders/verify.sh")).as(row.id()).isRegularFile();
    assertThat(REPO_ROOT.resolve(CaseFixture.fixtureFor(namedCase))).as(row.id()).isDirectory();
  }

  /** The grader names the row's own check, so the case measures what the row promises. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("rowsWithATierBCase")
  void theCasesGraderAssertsTheRowsCheck(FrameworkRow row) throws Exception {
    String grader =
        Files.readString(EVALS_DIR.resolve(row.tierBCase()).resolve("graders/verify.sh"));

    assertThat(row.check()).as(row.id()).isInstanceOf(CheckBinding.WiringCheck.class);
    assertThat(grader).as(row.id()).contains(((CheckBinding.WiringCheck) row.check()).id());
  }
}
