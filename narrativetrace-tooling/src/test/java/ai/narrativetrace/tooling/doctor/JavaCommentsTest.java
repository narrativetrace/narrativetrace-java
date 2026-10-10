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

class JavaCommentsTest {

  @Test
  void aLineCommentGoesAndItsLineBreakStays() {
    assertThat(JavaComments.strip("a // b\nc")).isEqualTo("a \nc");
  }

  @Test
  void aBlockCommentBecomesOneSpaceSoNeighboursNeverFuse() {
    assertThat(JavaComments.strip("a/* x\n y */b")).isEqualTo("a b");
  }

  @Test
  void anUnterminatedBlockCommentEndsAtTheEndOfTheText() {
    assertThat(JavaComments.strip("a /* never closed")).isEqualTo("a  ");
  }

  @Test
  void aLineCommentOnTheLastLineHasNoBreakToKeep() {
    assertThat(JavaComments.strip("a // b")).isEqualTo("a ");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "String u = \"http://x\";|String u = \"http://x\";",
        "String u = \"a\\\"//b\";|String u = \"a\\\"//b\";",
        "String s = \"/* not a comment */\";|String s = \"/* not a comment */\";",
      })
  void aLiteralIsCopiedVerbatim(String source, String expected) {
    assertThat(JavaComments.strip(source)).isEqualTo(expected);
  }

  @Test
  void aQuoteInACharLiteralDoesNotOpenAString() {
    assertThat(JavaComments.strip("char c = '\"'; // q")).isEqualTo("char c = '\"'; ");
  }

  @Test
  void aTextBlockIsCopiedVerbatimIncludingItsSlashes() {
    String block = "var t = \"\"\"\n  // not a comment\n  \"\"\"; // real";
    assertThat(JavaComments.strip(block))
        .isEqualTo("var t = \"\"\"\n  // not a comment\n  \"\"\"; ");
  }

  @Test
  void aStringThatNeverClosesStopsAtTheLineEnd() {
    assertThat(JavaComments.strip("\"open // x\nb // y")).isEqualTo("\"open // x\nb ");
  }

  @Test
  void aTrailingBackslashInsideALiteralDoesNotRunPastTheText() {
    assertThat(JavaComments.strip("\"a\\")).isEqualTo("\"a\\");
  }

  @Test
  void emptyTextStaysEmpty() {
    assertThat(JavaComments.strip("")).isEmpty();
  }
}
