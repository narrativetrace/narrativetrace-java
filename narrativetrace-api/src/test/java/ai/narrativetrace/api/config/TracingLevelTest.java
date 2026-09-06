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
package ai.narrativetrace.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TracingLevelTest {

  @Test
  void levelsHaveCorrectOrdering() {
    assertThat(TracingLevel.OFF.ordinal()).isLessThan(TracingLevel.ERRORS.ordinal());
    assertThat(TracingLevel.ERRORS.ordinal()).isLessThan(TracingLevel.SUMMARY.ordinal());
    assertThat(TracingLevel.SUMMARY.ordinal()).isLessThan(TracingLevel.NARRATIVE.ordinal());
    assertThat(TracingLevel.NARRATIVE.ordinal()).isLessThan(TracingLevel.DETAIL.ordinal());
  }

  @Test
  void isEnabledReturnsTrueWhenCurrentLevelIsAtOrAboveRequested() {
    assertThat(TracingLevel.DETAIL.isEnabled(TracingLevel.NARRATIVE)).isTrue();
    assertThat(TracingLevel.DETAIL.isEnabled(TracingLevel.DETAIL)).isTrue();
    assertThat(TracingLevel.NARRATIVE.isEnabled(TracingLevel.ERRORS)).isTrue();
    assertThat(TracingLevel.ERRORS.isEnabled(TracingLevel.ERRORS)).isTrue();
  }

  @Test
  void isEnabledReturnsFalseWhenCurrentLevelIsBelowRequested() {
    assertThat(TracingLevel.OFF.isEnabled(TracingLevel.ERRORS)).isFalse();
    assertThat(TracingLevel.ERRORS.isEnabled(TracingLevel.SUMMARY)).isFalse();
    assertThat(TracingLevel.NARRATIVE.isEnabled(TracingLevel.DETAIL)).isFalse();
  }

  @Test
  void offDisablesEverything() {
    assertThat(TracingLevel.OFF.isEnabled(TracingLevel.OFF)).isTrue();
    assertThat(TracingLevel.OFF.isEnabled(TracingLevel.ERRORS)).isFalse();
    assertThat(TracingLevel.OFF.isEnabled(TracingLevel.SUMMARY)).isFalse();
    assertThat(TracingLevel.OFF.isEnabled(TracingLevel.NARRATIVE)).isFalse();
    assertThat(TracingLevel.OFF.isEnabled(TracingLevel.DETAIL)).isFalse();
  }

  @Test
  void fromNameParsesExactEnumName() {
    assertThat(TracingLevel.fromName("NARRATIVE", TracingLevel.DETAIL))
        .isEqualTo(TracingLevel.NARRATIVE);
    assertThat(TracingLevel.fromName("OFF", TracingLevel.DETAIL)).isEqualTo(TracingLevel.OFF);
  }

  @Test
  void fromNameIsCaseAndWhitespaceTolerant() {
    assertThat(TracingLevel.fromName("  narrative ", TracingLevel.DETAIL))
        .isEqualTo(TracingLevel.NARRATIVE);
  }

  @Test
  void fromNameFallsBackOnNullBlankOrUnknown() {
    assertThat(TracingLevel.fromName(null, TracingLevel.DETAIL)).isEqualTo(TracingLevel.DETAIL);
    assertThat(TracingLevel.fromName("", TracingLevel.ERRORS)).isEqualTo(TracingLevel.ERRORS);
    assertThat(TracingLevel.fromName("bogus", TracingLevel.SUMMARY))
        .isEqualTo(TracingLevel.SUMMARY);
  }
}
