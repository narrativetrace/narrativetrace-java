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
package ai.narrativetrace.tooling.frameworks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvidenceTest {

  private final Evidence basePackages = new Evidence.YamlKey("narrativetrace", "base-packages");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativetrace:\n  base-packages:\n    - com.acme\n",
        "narrativetrace:\n  base-packages: com.acme\n",
        "narrativetrace:\n  base-packages: [com.acme]\n",
        "narrativetrace:\n  logger-name: x\n\n  base-packages:\n    - com.acme\n",
        "server:\n  port: 8080\nnarrativetrace:\n  base-packages: com.acme\n",
        "narrativetrace:\n# routing for the service\n  base-packages:\n    - com.acme\n",
        "narrativetrace: # tracing\n  base-packages: com.acme\n",
        "narrativetrace:\n  base-packages:\n  - com.acme\n",
        "narrativetrace:\n  base-packages: [a, b] # two packages\n",
      })
  void aNonEmptyKeyUnderTheParentIsEvidence(String yaml) {
    assertThat(basePackages.foundIn(yaml)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativetrace:\n  base-packages: []\n",
        "narrativetrace:\n  base-packages: [ ]\n",
        "narrativetrace:\n  base-packages:\n",
        "narrativetrace:\n  base-packages: # later\n  logger-name: x\n",
        "narrativetrace:\n  logger-name: x\nother:\n  base-packages:\n    - com.acme\n",
        "narrativetracer:\n  base-packages: com.acme\n",
        "base-packages: com.acme\n",
        "narrativetrace:\n  base-packages: \"\"\n",
        "narrativetrace:\n  base-packages: ''\n",
        "narrativetrace:\n  micronaut:\n    base-packages: com.acme\n",
        "narrativetrace:\n  base-packages:\n  -\n",
        "",
      })
  void anEmptyMisplacedOrNearMissKeyIsNot(String yaml) {
    assertThat(basePackages.foundIn(yaml)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"@EnableNarrativeTrace\nclass A {}", "x @EnableNarrativeTrace(y)"})
  void aMatchingPatternIsEvidenceAnywhereInTheText(String text) {
    assertThat(Evidence.matching("@EnableNarrativeTrace(?!\\w)").foundIn(text)).isTrue();
  }

  /** A tab before the {@code #} starts a comment as much as a space does. */
  @Test
  void aTabStartedCommentIsNotAValue() {
    assertThat(basePackages.foundIn("narrativetrace:\n  base-packages:\t# later\n")).isFalse();
    assertThat(basePackages.foundIn("narrativetrace:\t# tracing\n  base-packages: a\n")).isTrue();
    assertThat(basePackages.foundIn("narrativetrace:\n  base-packages: a\t# x # y\n")).isTrue();
  }
}
