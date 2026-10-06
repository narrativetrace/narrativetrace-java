/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tier B's engine-neutral case layout: every catalogue skill carries a {@code trigger.yaml} and a
 * {@code happy-path} case (prompt + grader) under {@code evals/<canonicalName>/}, and every fixture
 * a case names actually exists. No trial is run here — this only proves the shape is complete, the
 * same "referenced files exist" spirit as {@code lint.Lints#missingFixtures}.
 */
class EvalCaseLayoutTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));
  private static final Path EVALS_DIR = REPO_ROOT.resolve("narrativetrace-skills-catalogue/evals");

  /** A directory a tool left beside the cases, rather than a case somebody wrote. */
  private static final java.util.regex.Pattern NOT_A_CASE =
      java.util.regex.Pattern.compile("^[._]");

  static List<Skill> skills() {
    return CatalogueIndex.ALL;
  }

  @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
  @org.junit.jupiter.params.provider.MethodSource("skills")
  void everySkillCarriesATriggerSetAndAHappyPathCase(Skill skill) {
    Path skillDir = EVALS_DIR.resolve(skill.canonicalName());
    assertThat(skillDir.resolve("trigger.yaml")).exists();

    Path happyPath = skillDir.resolve("happy-path");
    assertThat(happyPath.resolve("prompt.md")).exists();
    assertThat(happyPath.resolve("graders").resolve("verify.sh")).exists();
    assertThat(REPO_ROOT.resolve(CaseFixture.fixtureFor(happyPath))).isDirectory();
  }

  /**
   * Every case directory that is NOT {@code happy-path}, under every catalogue skill — the
   * deviation cases and every other named case alike (the init-prompt cases, say). Scoped by "not
   * the happy path" rather than by a {@code deviation-} prefix so a case whose name does not follow
   * that convention is still covered by construction, which is the whole point of deriving the list
   * instead of hand-writing one test per case.
   *
   * <p>A directory whose name begins with {@code _} or {@code .} is not a case and is skipped —
   * {@code __pycache__}, which a grader importing a shared Python helper leaves behind. Scoped by
   * NAME rather than by "has a prompt.md", deliberately: the latter would let a real case that lost
   * its prompt disappear from this test instead of failing it.
   */
  static List<Path> nonHappyPathCases() {
    List<Path> cases = new ArrayList<>();
    for (Skill skill : skills()) {
      Path skillDir = EVALS_DIR.resolve(skill.canonicalName());
      if (!Files.isDirectory(skillDir)) {
        continue;
      }
      try (var entries = Files.list(skillDir)) {
        entries
            .filter(Files::isDirectory)
            .filter(p -> !p.getFileName().toString().equals("happy-path"))
            .filter(p -> !NOT_A_CASE.matcher(p.getFileName().toString()).find())
            .sorted()
            .forEach(cases::add);
      } catch (IOException e) {
        throw new UncheckedIOException("could not list " + skillDir, e);
      }
    }
    return cases;
  }

  @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
  @org.junit.jupiter.params.provider.MethodSource("nonHappyPathCases")
  void everyOtherCaseCarriesAPromptGraderAndAnExistingFixture(Path namedCase) {
    assertThat(namedCase.resolve("prompt.md")).exists();
    assertThat(namedCase.resolve("graders").resolve("verify.sh")).exists();
    assertThat(namedCase.resolve("case.json"))
        .as(namedCase + " must declare its own fixture, distinct from the skill's default")
        .exists();
    assertThat(REPO_ROOT.resolve(CaseFixture.fixtureFor(namedCase))).isDirectory();
  }

  @Test
  void everyTriggerFileNamesAtLeastOnePositiveAndOneNegativePhrasing() throws Exception {
    for (Skill skill : skills()) {
      String content =
          Files.readString(EVALS_DIR.resolve(skill.canonicalName()).resolve("trigger.yaml"));
      assertThat(content)
          .as(skill.canonicalName() + "'s trigger.yaml")
          .contains("positive:")
          .contains("negative:");
      long positiveLines = content.lines().filter(l -> l.strip().startsWith("- \"")).count();
      assertThat(positiveLines)
          .as(skill.canonicalName() + "'s trigger.yaml phrasing count")
          .isGreaterThan(1);
    }
  }
}
