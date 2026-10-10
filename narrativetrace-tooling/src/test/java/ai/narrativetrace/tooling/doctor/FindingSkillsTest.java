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
package ai.narrativetrace.tooling.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FindingSkillsTest {

  /**
   * The whole table, spelled out. A change of mind about which skill fixes which finding class is a
   * change to a published contract — an agent reads {@code skill} and follows that procedure — so
   * it should have to edit a row here, not just a line of production code.
   */
  @ParameterizedTest
  @CsvSource({
    "toolchain.junit5-range, add-narrative-tracing",
    "toolchain.launcher, add-narrative-tracing",
    "config.extension-registered, add-narrative-tracing",
    "config.output-property, narrativetrace-doctor",
    "config.approval-mode, narrativetrace-verify",
    "trap.silent-sink, narrativetrace-doctor",
    "trap.parameter-arg0, add-narrative-tracing",
    "trap.unused-not-traced-import, narrativetrace-doctor",
    "trap.redaction-proof, narrativetrace-doctor",
    "trap.approval-traces, narrativetrace-doctor",
    "trap.llms-before-you-start, add-narrative-tracing",
    "config.spring-enabled, add-narrative-tracing",
    "config.spring-web-filter, add-narrative-tracing",
    "config.micronaut-base-packages, add-narrative-tracing",
    "config.servlet-filter, add-narrative-tracing",
    "config.junit4-rule, add-narrative-tracing",
    "config.micrometer-accessor, add-narrative-tracing",
    "config.otel-listener, add-narrative-tracing",
  })
  void namesTheSkillThatFixesEachFindingClass(String id, String skill) {
    assertThat(FindingSkills.forCheck(id)).isEqualTo(skill);
  }

  /** Two ids carry no skill on purpose, and the table still KNOWS them — decided, not forgotten. */
  @ParameterizedTest
  @CsvSource({"toolchain.jdk-version", "config.skills-installed"})
  void namesNoSkillWhereNoneFixesIt(String id) {
    assertThat(FindingSkills.forCheck(id)).isNull();
    assertThat(FindingSkills.knows(id)).isTrue();
  }

  @Test
  void anIdTheTableNeverHeardOfIsNeitherKnownNorMapped() {
    assertThat(FindingSkills.forCheck("trap.invented-yesterday")).isNull();
    assertThat(FindingSkills.knows("trap.invented-yesterday")).isFalse();
  }

  /**
   * A null id reaches this table from {@link Finding}'s constructor, before that constructor has
   * rejected it — so the lookup answers rather than throwing, and the id-shaped mistake still
   * surfaces as the constructor's own message.
   */
  @Test
  void aNullIdIsAnswered() {
    assertThat(FindingSkills.forCheck(null)).isNull();
    assertThat(FindingSkills.knows(null)).isFalse();
  }
}
