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

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private static final Path EVALS_DIR = REPO_ROOT.resolve("narrativetrace-skills/evals");

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

  @Test
  void theDoctorSkillCarriesTheRedactionGapDeviationCase() {
    Path deviation = EVALS_DIR.resolve("narrativetrace-doctor").resolve("deviation-redaction-gap");
    assertThat(deviation.resolve("prompt.md")).exists();
    assertThat(deviation.resolve("graders").resolve("verify.sh")).exists();
    assertThat(CaseFixture.fixtureFor(deviation))
        .isEqualTo("narrativetrace-skills/evals/fixtures/redaction-gap");
    assertThat(REPO_ROOT.resolve(CaseFixture.fixtureFor(deviation))).isDirectory();
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
