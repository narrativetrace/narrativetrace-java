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
package ai.narrativetrace.cli.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionsTest {

  @Test
  void leadingIntReadsTheFirstDigitRun() {
    assertThat(Versions.leadingInt("21.0.1")).isEqualTo(21);
    assertThat(Versions.leadingInt("v17")).isEqualTo(17);
  }

  @Test
  void leadingIntDefaultsToZero() {
    assertThat(Versions.leadingInt(null)).isZero();
    assertThat(Versions.leadingInt("no digits here")).isZero();
  }

  @Test
  void compareOrdersByComponent() {
    assertThat(Versions.compare("5.11.4", "5.9.0")).isPositive();
    assertThat(Versions.compare("5.9.0", "5.11.4")).isNegative();
    assertThat(Versions.compare("5.9.0", "5.9.0")).isZero();
  }

  @Test
  void compareTreatsMissingComponentsAsZero() {
    assertThat(Versions.compare("6", "6.0.0")).isZero();
    assertThat(Versions.compare("6.0.1", "6")).isPositive();
  }

  @Test
  void compareIgnoresPreReleaseAndBuildMetadata() {
    assertThat(Versions.compare("5.9.0-RC1", "5.9.0+build2")).isZero();
  }

  @Test
  void inRangeIsInclusiveMinExclusiveMax() {
    assertThat(Versions.inRange("5.9.0", "5.9.0", "6.0.0")).isTrue();
    assertThat(Versions.inRange("5.11.4", "5.9.0", "6.0.0")).isTrue();
    assertThat(Versions.inRange("6.0.0", "5.9.0", "6.0.0")).isFalse();
    assertThat(Versions.inRange("5.8.9", "5.9.0", "6.0.0")).isFalse();
  }
}
