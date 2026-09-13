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
        "{ \"fixture\": \"narrativetrace-skills/evals/fixtures/empty-project\" }");
    assertThat(CaseFixture.fixtureFor(caseDir))
        .isEqualTo("narrativetrace-skills/evals/fixtures/empty-project");
  }

  @Test
  void rejectsACaseJsonWithNoFixtureField(@TempDir Path caseDir) throws Exception {
    Files.writeString(caseDir.resolve("case.json"), "{ \"other\": \"x\" }");
    assertThatThrownBy(() -> CaseFixture.fixtureFor(caseDir))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
