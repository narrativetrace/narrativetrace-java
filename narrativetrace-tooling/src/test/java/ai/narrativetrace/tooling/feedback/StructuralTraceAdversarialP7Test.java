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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Span-id placement and boundary spellings for {@link StructuralTrace#looksStructural}. */
class StructuralTraceAdversarialP7Test {

  @Test
  void anIndentedForkMarkerOpeningWithASpanIdIsAccepted() {
    assertThat(StructuralTrace.looksStructural("scenario: s\n\n#1 - A.a()\n  #1.1 ~ fork [2]\n"))
        .isTrue();
  }

  @Test
  void aSpanIdOnTheHeaderLineIsRefused() {
    assertThat(StructuralTrace.looksStructural("#1 scenario: s\n\n- A.a()\n")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"- A.a() !! ", "- A.a() !! java..Ex", "~ fork []", "- A.a(x: y)"})
  void aMalformedOutcomeMarkerOrParameterListIsRefused(String line) {
    assertThat(StructuralTrace.looksStructural("scenario: s\n\n" + line + "\n")).as(line).isFalse();
  }

  @Test
  void aDollarSignInANestedClassOrSyntheticMethodNameIsAccepted() {
    assertThat(StructuralTrace.looksStructural("scenario: s\n\n- A$B.lambda$0(x)\n")).isTrue();
  }

  @Test
  void aThrownOutcomeWithAPackageQualifiedTypeIsAccepted() {
    assertThat(
            StructuralTrace.looksStructural(
                "scenario: s\n\n- A.a() !! java.lang.IllegalStateException\n"))
        .isTrue();
  }

  @Test
  void aCarriageReturnTerminatedTraceIsRecognisedAsStructural() {
    // SUSPECTED BUG: the header pattern cannot match a line ending in CR, so a structural trace
    // written with CRLF line endings (a Windows checkout) is refused as not structural at all.
    assertThat(StructuralTrace.looksStructural("scenario: s\r\n\r\n- A.a()\r\n")).isTrue();
  }
}
