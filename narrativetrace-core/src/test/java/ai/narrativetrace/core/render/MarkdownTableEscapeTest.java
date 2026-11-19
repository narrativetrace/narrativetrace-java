/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownTableEscapeTest {

  @Test
  @DisplayName("B-249: a cell's own backslash does not uncap the pipe it precedes")
  void cellEscapesTheBackslashBeforeTheLiveBackslashCanUncapThePipe() {
    var escaped = MarkdownTableEscape.cell("a\\|b");
    var row = "| " + escaped + " |";

    assertThat(splitRow(row)).containsExactly(escaped);
  }

  @Test
  @DisplayName("B-249: the splitter's own oracle — an escaped backslash leaves the next pipe live")
  void splitterTreatsAnEscapedBackslashAsLeavingTheFollowingPipeLive() {
    var twoBackslashes = "\\\\";
    var row = "| " + twoBackslashes + "| b |";

    assertThat(splitRow(row)).containsExactly(twoBackslashes, "b");
  }

  @Test
  @DisplayName("B-249: a code-span cell keeps its backslash once, unlike an ordinary cell")
  void codeSpanCellDoesNotDoubleTheBackslash() {
    assertThat(MarkdownTableEscape.codeSpanCell("a\\|b")).isEqualTo("a\\%7Cb");
  }

  @Test
  @DisplayName("B-251: a line of three or more asterisks is escaped at the line start")
  void proseEscapesAThreeOrMoreAsteriskThematicBreak() {
    assertThat(MarkdownTableEscape.prose("***")).isEqualTo("\\***");
    assertThat(MarkdownTableEscape.prose("*****")).isEqualTo("\\*****");
  }

  @Test
  @DisplayName("B-251: a line of three or more underscores is escaped at the line start")
  void proseEscapesAThreeOrMoreUnderscoreThematicBreak() {
    assertThat(MarkdownTableEscape.prose("___")).isEqualTo("\\___");
  }

  @Test
  @DisplayName("B-251: a spaced-out thematic break is escaped at the line start")
  void proseEscapesASpacedOutThematicBreak() {
    assertThat(MarkdownTableEscape.prose("* * *")).isEqualTo("\\* * *");
  }

  @Test
  @DisplayName("B-251: emphasis and italics are not mistaken for a thematic break")
  void proseLeavesEmphasisAndItalicsUnchanged() {
    assertThat(MarkdownTableEscape.prose("**bold** word")).isEqualTo("**bold** word");
    assertThat(MarkdownTableEscape.prose("_em_ word")).isEqualTo("_em_ word");
  }

  @Test
  @DisplayName("B-251: a leading blockquote marker is escaped at the line start")
  void proseEscapesALeadingBlockquoteMarker() {
    assertThat(MarkdownTableEscape.prose("> injected")).isEqualTo("\\> injected");
  }

  @Test
  @DisplayName("B-251: a blockquote marker mid-line is not mistaken for the line start")
  void proseLeavesAMidLineBlockquoteMarkerUnchanged() {
    assertThat(MarkdownTableEscape.prose("a > b")).isEqualTo("a > b");
  }

  @Test
  @DisplayName(
      "ADV-2026-11-09-1: prose must escape a setext underline and a dash thematic break"
          + " — interpolated text can only ever read as a paragraph")
  void proseEscapesASetextUnderlineAndADashThematicBreak() {
    assertThat(MarkdownTableEscape.prose("---")).isEqualTo("\\---");
    assertThat(MarkdownTableEscape.prose("===")).isEqualTo("\\===");
    assertThat(MarkdownTableEscape.prose("harmless\n---")).isEqualTo("harmless\n\\---");
  }

  @Test
  @DisplayName(
      "ADV-2026-11-09-2: prose must escape a structural line that CommonMark allows to be"
          + " indented by up to three spaces")
  void proseEscapesAStructuralLineIndentedByUpToThreeSpaces() {
    assertThat(MarkdownTableEscape.prose(" # Injected heading")).isEqualTo(" \\# Injected heading");
    assertThat(MarkdownTableEscape.prose("  > injected quote")).isEqualTo("  \\> injected quote");
    assertThat(MarkdownTableEscape.prose("   - injected item")).isEqualTo("   \\- injected item");
    assertThat(MarkdownTableEscape.prose("  ```json")).isEqualTo("  \\`\\`\\`json");
  }

  @Test
  @DisplayName(
      "ADV-2026-11-09-3: a code-span cell must neutralize the backtick that closes the span"
          + " it is about to be placed inside")
  void codeSpanCellNeutralizesTheBacktickThatWouldCloseTheSpan() {
    assertThat(MarkdownTableEscape.codeSpanCell("a`b")).isEqualTo("a%60b");
  }

  @Test
  @DisplayName("B-264: a four-space indented line is a code block already and stays untouched")
  void proseLeavesAFourSpaceIndentedLineUnchanged() {
    assertThat(MarkdownTableEscape.prose("    # not a heading")).isEqualTo("    # not a heading");
  }

  @Test
  @DisplayName("B-264: a mid-sentence hyphen is not mistaken for a dash line")
  void proseLeavesAMidSentenceHyphenUnchanged() {
    assertThat(MarkdownTableEscape.prose("a - b")).isEqualTo("a - b");
  }

  @Test
  @DisplayName("B-264: a mid-sentence equals comparison is not mistaken for a setext underline")
  void proseLeavesAMidSentenceEqualsComparisonUnchanged() {
    assertThat(MarkdownTableEscape.prose("x == y")).isEqualTo("x == y");
  }

  @Test
  @DisplayName("B-264: an underscored identifier is not mistaken for emphasis or a thematic break")
  void proseLeavesASnakeCaseIdentifierUnchanged() {
    assertThat(MarkdownTableEscape.prose("snake_case_name")).isEqualTo("snake_case_name");
  }

  @Test
  @DisplayName("B-264: a lone setext-length dash run is escaped at any length, not just three")
  void proseEscapesADashRunShorterThanAThematicBreak() {
    assertThat(MarkdownTableEscape.prose("-")).isEqualTo("\\-");
    assertThat(MarkdownTableEscape.prose("--")).isEqualTo("\\--");
  }

  @Test
  @DisplayName("B-264: an equals run of any length is escaped as a setext H1 underline")
  void proseEscapesAnEqualsRunOfAnyLength() {
    assertThat(MarkdownTableEscape.prose("=")).isEqualTo("\\=");
    assertThat(MarkdownTableEscape.prose("== ")).isEqualTo("\\== ");
  }

  @Test
  @DisplayName("each character of a leading heading run is escaped, not just the first")
  void proseEscapesEveryCharacterOfALeadingHeadingRun() {
    assertThat(MarkdownTableEscape.prose("## Accepted abbreviations"))
        .isEqualTo("\\#\\# Accepted abbreviations");
  }

  @Test
  @DisplayName("a leading run that is the whole line is escaped to its last character")
  void proseEscapesALeadingRunThatReachesTheEndOfTheLine() {
    assertThat(MarkdownTableEscape.prose("#")).isEqualTo("\\#");
    assertThat(MarkdownTableEscape.prose("~~~")).isEqualTo("\\~\\~\\~");
  }

  @Test
  @DisplayName("a leading backtick fence is escaped character by character")
  void proseEscapesALeadingBacktickFence() {
    assertThat(MarkdownTableEscape.prose("```java")).isEqualTo("\\`\\`\\`java");
  }

  @Test
  @DisplayName("a leading pipe would open a table row, so it is escaped")
  void proseEscapesALeadingTableRow() {
    assertThat(MarkdownTableEscape.prose("| a | b |")).isEqualTo("\\| a | b |");
  }

  @Test
  @DisplayName("a leading unordered list marker is escaped")
  void proseEscapesALeadingUnorderedListMarker() {
    assertThat(MarkdownTableEscape.prose("- item")).isEqualTo("\\- item");
  }

  @Test
  @DisplayName("two asterisks are one short of a thematic break and stay prose")
  void proseLeavesATwoCharacterMarkerRunUnchanged() {
    assertThat(MarkdownTableEscape.prose("**")).isEqualTo("**");
    assertThat(MarkdownTableEscape.prose("__")).isEqualTo("__");
  }

  @Test
  @DisplayName("a marker that is not at the line start is left alone")
  void proseLeavesAMidLineMarkerUnchanged() {
    assertThat(MarkdownTableEscape.prose("a # b")).isEqualTo("a # b");
    assertThat(MarkdownTableEscape.prose("a ```b")).isEqualTo("a ```b");
  }

  @Test
  @DisplayName("every line is judged on its own, and the line breaks survive")
  void proseEscapesEachLineIndependently() {
    assertThat(MarkdownTableEscape.prose("# h\nplain\n> q")).isEqualTo("\\# h\nplain\n\\> q");
  }

  @Test
  @DisplayName("empty prose stays empty")
  void proseLeavesAnEmptyStringEmpty() {
    assertThat(MarkdownTableEscape.prose("")).isEmpty();
  }

  @Test
  @DisplayName("every line terminator collapses to exactly one space")
  void collapseLineBreaksTurnsEveryTerminatorIntoASingleSpace() {
    assertThat(MarkdownTableEscape.collapseLineBreaks("a\r\nb\nc\rd")).isEqualTo("a b c d");
  }

  @Test
  @DisplayName("an inline code span neutralizes both its own delimiter and the pipe")
  void inlineCodePercentEncodesTheDelimiterAndThePipe() {
    assertThat(MarkdownTableEscape.inlineCode("a`b|c")).isEqualTo("`a%60b%7Cc`");
  }

  @Test
  @DisplayName("every character of a leading heading run is escaped, not just the first")
  void proseEscapesEachCharacterOfALeadingHeadingRun() {
    assertThat(MarkdownTableEscape.prose("# heading")).isEqualTo("\\# heading");
    assertThat(MarkdownTableEscape.prose("### heading")).isEqualTo("\\#\\#\\# heading");
    assertThat(MarkdownTableEscape.prose("###")).isEqualTo("\\#\\#\\#");
  }

  @Test
  @DisplayName("a leading code fence is escaped in both of its spellings")
  void proseEscapesALeadingFenceInBothSpellings() {
    assertThat(MarkdownTableEscape.prose("```json")).isEqualTo("\\`\\`\\`json");
    assertThat(MarkdownTableEscape.prose("~~~")).isEqualTo("\\~\\~\\~");
  }

  @Test
  @DisplayName("a spaced-out dash thematic break is escaped as the list marker it also is")
  void proseEscapesASpacedOutDashThematicBreak() {
    assertThat(MarkdownTableEscape.prose("- - -")).isEqualTo("\\- - -");
  }

  @Test
  @DisplayName("an inline code span neutralizes both its own delimiter and the column separator")
  void inlineCodeNeutralizesItsDelimiterAndThePipe() {
    assertThat(MarkdownTableEscape.inlineCode("a`b|c")).isEqualTo("`a%60b%7Cc`");
  }

  @Test
  @DisplayName("a CRLF collapses to one space, not two")
  void collapseLineBreaksTreatsCrLfAsASingleBreak() {
    assertThat(MarkdownTableEscape.collapseLineBreaks("a\r\nb")).isEqualTo("a b");
    assertThat(MarkdownTableEscape.collapseLineBreaks("a\r\rb")).isEqualTo("a  b");
  }

  /**
   * Splits a GFM table row the way a reader does: on a pipe not preceded by an odd run of
   * backslashes, after dropping the row's own leading and trailing pipe.
   */
  private static List<String> splitRow(String row) {
    var body = row.strip();
    body = body.substring(1, body.length() - 1);
    var cells = new ArrayList<String>();
    var current = new StringBuilder();
    var backslashRun = 0;
    for (var i = 0; i < body.length(); i++) {
      var c = body.charAt(i);
      if (c == '|' && backslashRun % 2 == 0) {
        cells.add(current.toString().strip());
        current = new StringBuilder();
        backslashRun = 0;
        continue;
      }
      current.append(c);
      backslashRun = c == '\\' ? backslashRun + 1 : 0;
    }
    cells.add(current.toString().strip());
    return cells;
  }
}
