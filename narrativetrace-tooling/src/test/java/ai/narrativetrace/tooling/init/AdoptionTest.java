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
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The rule that decides whether a page nobody stamped is ours after all — and, just as important,
 * the near misses it must NOT accept. Every row here is a page a registry, a person or another
 * release could really leave in a project.
 */
class AdoptionTest {

  private static final String PAGE =
      "---\nname: doctor\ndescription: What doctor does.\n---\n\nbody\n";

  @Test
  void acceptsThePageTheCarrierRenders() {
    assertThat(Adoption.isAdoptable(PAGE, PAGE)).isTrue();
  }

  /** A checkout with the other line ending is the same page: that is the one thing normalised. */
  @Test
  void acceptsThePageWithTheOtherLineEnding() {
    assertThat(Adoption.isAdoptable(PAGE.replace("\n", "\r\n"), PAGE)).isTrue();
    assertThat(Adoption.isAdoptable(PAGE.replace("\n", "\r"), PAGE)).isTrue();
  }

  @Test
  void refusesAPageWithOneTrailingSpace() {
    assertThat(Adoption.isAdoptable(PAGE.replace("body", "body "), PAGE)).isFalse();
  }

  @Test
  void refusesAPageWhoseFrontmatterKeysWereReordered() {
    String reordered = "---\ndescription: What doctor does.\nname: doctor\n---\n\nbody\n";

    assertThat(Adoption.isAdoptable(reordered, PAGE)).isFalse();
  }

  @Test
  void refusesAPageThatLostItsFinalNewline() {
    assertThat(Adoption.isAdoptable(PAGE.stripTrailing(), PAGE)).isFalse();
  }

  @Test
  void refusesAnotherReleasesWording() {
    assertThat(Adoption.isAdoptable(PAGE.replace("body", "older body"), PAGE)).isFalse();
  }

  /** A skill directory with no page at all is somebody's empty directory, never a copy of ours. */
  @Test
  void refusesAnEmptyPageEvenAgainstAnEmptyRendering() {
    assertThat(Adoption.isAdoptable("", "")).isFalse();
    assertThat(Adoption.isAdoptable("", PAGE)).isFalse();
  }

  @Test
  void refusesToCompareWithNothing() {
    assertThatThrownBy(() -> Adoption.isAdoptable(null, PAGE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Adoption.isAdoptable(PAGE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
