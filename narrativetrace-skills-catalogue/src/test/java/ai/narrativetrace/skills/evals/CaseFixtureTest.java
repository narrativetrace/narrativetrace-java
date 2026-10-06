/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaseFixtureTest {

  @Test
  void defaultsToSixtySecondsWhenNoCaseJsonExists(@TempDir Path caseDir) {
    assertThat(CaseFixture.fixtureFor(caseDir)).isEqualTo("sixty-seconds");
  }

  @Test
  void readsTheFixtureFieldWhenCaseJsonExists(@TempDir Path caseDir) throws Exception {
    Files.writeString(
        caseDir.resolve("case.json"),
        "{ \"fixture\": \"narrativetrace-skills-catalogue/evals/fixtures/empty-project\" }");
    assertThat(CaseFixture.fixtureFor(caseDir))
        .isEqualTo("narrativetrace-skills-catalogue/evals/fixtures/empty-project");
  }

  @Test
  void rejectsACaseJsonWithNoFixtureField(@TempDir Path caseDir) throws Exception {
    Files.writeString(caseDir.resolve("case.json"), "{ \"other\": \"x\" }");
    assertThatThrownBy(() -> CaseFixture.fixtureFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
