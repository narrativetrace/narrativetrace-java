/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownEscapeTest {

  @Test
  void textEscapesAmpersandLessThanAndGreaterThan() {
    assertThat(MarkdownEscape.text("a&b<c>d")).isEqualTo("a&amp;b&lt;c&gt;d");
  }

  @Test
  void textEscapesAmpersandBeforeAngleBracketsSoEntitiesAreNotDoubleDecoded() {
    // A literal "&lt;" in the source must survive as visible "&lt;", i.e. its '&' is itself
    // escaped.
    assertThat(MarkdownEscape.text("&lt;")).isEqualTo("&amp;lt;");
  }

  @Test
  void textLeavesPlainProseUnchanged() {
    assertThat(MarkdownEscape.text("Card expired")).isEqualTo("Card expired");
  }

  @Test
  void codeWrapsBacktickFreeContentInSingleBacktickSpan() {
    assertThat(MarkdownEscape.code("\"valid\"")).isEqualTo("`\"valid\"`");
  }

  @Test
  void codeWidensFenceAndPadsForASingleInternalBacktick() {
    assertThat(MarkdownEscape.code("a`b")).isEqualTo("`` a`b ``");
  }

  @Test
  void codeWidensFenceBeyondTheLongestBacktickRun() {
    // A run of two backticks needs a three-backtick fence, else it would still break out.
    assertThat(MarkdownEscape.code("a``b")).isEqualTo("``` a``b ```");
  }

  @Test
  void codeHandlesContentThatIsEntirelyABacktick() {
    assertThat(MarkdownEscape.code("`")).isEqualTo("`` ` ``");
  }

  @Test
  void codeMeasuresLongestRunNotTotalBacktickCount() {
    // Two single backticks separated by text: the longest RUN is 1, so a two-backtick fence is
    // enough. Guards the run reset — without it the count would accumulate to 2 and over-widen.
    assertThat(MarkdownEscape.code("`a`")).isEqualTo("`` `a` ``");
  }

  /**
   * Regression: every site that interpolates here is a single line, so a raw line break is markup.
   * An exception message carrying a newline and a fence opened a fenced code block from inside a
   * value — and exception messages commonly echo user input, which is the reason this class exists.
   * Found by the security suite's AI-consumer injection oracle.
   */
  @Test
  void aNewlineInProseCannotEndTheLineItWasPlacedOn() {
    assertThat(MarkdownEscape.text("before\n```java")).isEqualTo("before\\n```java");
  }

  @Test
  void aCarriageReturnInProseCannotEndTheLineEither() {
    assertThat(MarkdownEscape.text("before\rafter")).isEqualTo("before\\rafter");
  }

  @Test
  void anEscapeSequenceInProseIsRenderedInert() {
    assertThat(MarkdownEscape.text("x" + (char) 0x1b + "[31m")).isEqualTo("x\\u001b[31m");
  }

  @Test
  void aNewlineInACodeSpanCannotEndTheSpan() {
    assertThat(MarkdownEscape.code("before\nafter")).isEqualTo("`before\\nafter`");
  }

  @Test
  void controlEscapingComposesWithHtmlEscaping() {
    assertThat(MarkdownEscape.text("<b>\n</b>")).isEqualTo("&lt;b&gt;\\n&lt;/b&gt;");
  }

  @Test
  void alreadyEscapedTextIsUnchangedBySanitizingAgain() {
    assertThat(MarkdownEscape.text("before\\nafter")).isEqualTo("before\\nafter");
  }
}
