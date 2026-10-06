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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FindingTest {

  @Test
  void passHasAnEmptyFix() {
    Finding f =
        Finding.pass(
            "toolchain.jdk-version", "Java 21 satisfies 17+", "https://narrativetrace.ai/docs/x");
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.fix()).isEmpty();
    assertThat(f.isFailing()).isFalse();
  }

  @Test
  void failCarriesItsFix() {
    Finding f =
        Finding.fail(
            "trap.silent-sink", "no sink", "attach one", "https://narrativetrace.ai/docs/x");
    assertThat(f.isFailing()).isTrue();
    assertThat(f.fix()).isEqualTo("attach one");
  }

  @Test
  void rejectsBlankId() {
    assertThatThrownBy(() -> Finding.pass(" ", "message", "https://x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullStatus() {
    assertThatThrownBy(() -> new Finding("id", null, "message", "", "https://x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankMessage() {
    assertThatThrownBy(() -> Finding.pass("id", "", "https://x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonHttpsDocUrl() {
    assertThatThrownBy(() -> Finding.pass("id", "message", "http://insecure"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Finding("id", Finding.Status.PASS, "message", "", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAFailingFindingWithNoFix() {
    assertThatThrownBy(() -> Finding.fail("id", "message", "", "https://x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Finding("id", Finding.Status.FAIL, "message", null, "https://x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPassingFindingWithANullFixDefaultsToEmpty() {
    Finding f = new Finding("id", Finding.Status.PASS, "message", null, "https://x");
    assertThat(f.fix()).isEmpty();
  }

  @Test
  void aFindingCarriesTheCatalogueSkillThatFixesItsClass() {
    Finding f =
        Finding.fail(
            "trap.silent-sink", "no sink", "attach one", "https://narrativetrace.ai/docs/x");
    assertThat(f.skill()).isEqualTo("narrativetrace-doctor");
  }

  @Test
  void aFindingWhoseClassNoSkillFixesCarriesNone() {
    Finding f =
        Finding.fail(
            "toolchain.jdk-version",
            "Java 11 does not satisfy 17+",
            "Upgrade the JDK",
            "https://narrativetrace.ai/docs/x");
    assertThat(f.skill()).isNull();
  }

  @Test
  void rejectsABlankSkillName() {
    assertThatThrownBy(
            () -> new Finding("id", Finding.Status.PASS, "message", "", "https://x", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A canonical construction may name any skill: the table decides only for the short form. */
  @Test
  void theCanonicalConstructorTakesTheSkillAsGiven() {
    Finding f =
        new Finding(
            "trap.silent-sink",
            Finding.Status.PASS,
            "message",
            "",
            "https://x",
            "add-narrativetrace-clarity");
    assertThat(f.skill()).isEqualTo("add-narrativetrace-clarity");
  }
}
