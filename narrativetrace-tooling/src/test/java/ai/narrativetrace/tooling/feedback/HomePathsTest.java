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
package ai.narrativetrace.tooling.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The one normalisation the draft performs before the gate reads anything. */
class HomePathsTest {

  @Test
  void rewritesEveryPlatformsHomeDirectoryToATilde() {
    assertThat(HomePaths.toTilde("/Users/ada/work")).isEqualTo("~/work");
    assertThat(HomePaths.toTilde("/home/ada/work")).isEqualTo("~/work");
    assertThat(HomePaths.toTilde("C:\\Users\\ada\\work")).isEqualTo("~\\work");
  }

  @Test
  void rewritesAHomeDirectoryWithNothingAfterIt() {
    assertThat(HomePaths.toTilde("the project is in /Users/ada")).isEqualTo("the project is in ~");
  }

  @Test
  void rewritesEveryOccurrenceOnEveryLine() {
    assertThat(HomePaths.toTilde("/home/ada/a\n/home/bob/b")).isEqualTo("~/a\n~/b");
  }

  @Test
  void leavesAlonePathsThatAreNotSomebodysHome() {
    assertThat(HomePaths.toTilde("/home")).isEqualTo("/home");
    assertThat(HomePaths.toTilde("/usr/share/ada")).isEqualTo("/usr/share/ada");
    assertThat(HomePaths.toTilde("build/narrativetrace")).isEqualTo("build/narrativetrace");
    assertThat(HomePaths.toTilde("~/already-rewritten")).isEqualTo("~/already-rewritten");
  }

  @Test
  void whatItRewritesTheGateThenNeverSees() {
    String rewritten = HomePaths.toTilde("ran it in /Users/ada/work and /home/ada/work");

    assertThat(ValueFreeCheck.rulesRefusing(rewritten))
        .as("the rewrite is what keeps an ordinary sentence filable")
        .isEmpty();
  }
}
