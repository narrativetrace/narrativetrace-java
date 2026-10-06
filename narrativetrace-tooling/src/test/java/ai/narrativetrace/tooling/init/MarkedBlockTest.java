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

import org.junit.jupiter.api.Test;

/**
 * The managed block is found by its markers, and everything the installer does to a consumer file
 * is expressed as an edit of that region. These cases pin the marker rule itself — column 0,
 * outside fenced code — and the reversibility every {@code init} / {@code uninstall} round trip
 * depends on.
 */
class MarkedBlockTest {

  private static final String BLOCK =
      "<!-- narrativetrace:start ai.narrativetrace:narrativetrace-skills:1.2.3 -->\n"
          + "## NarrativeTrace\n"
          + "<!-- narrativetrace:end -->\n";

  // --- finding the region -------------------------------------------------------------------

  @Test
  void findsOneRegionAndReadsItsCoordinate() {
    String text = "# Title\n\n" + BLOCK;

    MarkedBlock.Scan scan = MarkedBlock.scan(text);

    assertThat(scan.problems()).isEmpty();
    assertThat(scan.regions()).hasSize(1);
    assertThat(scan.regions().get(0).coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills:1.2.3");
    assertThat(text.substring(scan.regions().get(0).start(), scan.regions().get(0).end()))
        .isEqualTo(BLOCK);
  }

  @Test
  void findsARegionWhoseOpeningMarkerCarriesNoCoordinate() {
    MarkedBlock.Scan scan =
        MarkedBlock.scan("<!-- narrativetrace:start -->\nbody\n<!-- narrativetrace:end -->\n");

    assertThat(scan.regions()).hasSize(1);
    assertThat(scan.regions().get(0).coordinate()).isEmpty();
  }

  @Test
  void readsNoCoordinateFromAnOpeningMarkerThatNeverCloses() {
    MarkedBlock.Scan scan =
        MarkedBlock.scan("<!-- narrativetrace:start oops\nbody\n<!-- narrativetrace:end -->\n");

    assertThat(scan.regions()).hasSize(1);
    assertThat(scan.regions().get(0).coordinate()).isEmpty();
  }

  @Test
  void findsTwoRegionsSoTheCallerCanRefuseThem() {
    MarkedBlock.Scan scan = MarkedBlock.scan(BLOCK + "\ntext\n\n" + BLOCK);

    assertThat(scan.regions()).hasSize(2);
    assertThat(scan.problems()).isEmpty();
  }

  @Test
  void reportsAStartWithNoEndNamingTheLine() {
    MarkedBlock.Scan scan = MarkedBlock.scan("a\n<!-- narrativetrace:start -->\nb\n");

    assertThat(scan.regions()).isEmpty();
    assertThat(scan.problems()).singleElement().asString().contains("line 2").contains("no end");
  }

  @Test
  void reportsAnEndWithNoStartNamingTheLine() {
    MarkedBlock.Scan scan = MarkedBlock.scan("a\n<!-- narrativetrace:end -->\n");

    assertThat(scan.problems()).singleElement().asString().contains("line 2").contains("no start");
  }

  @Test
  void reportsASecondStartBeforeTheFirstEnd() {
    MarkedBlock.Scan scan =
        MarkedBlock.scan(
            "<!-- narrativetrace:start -->\n<!-- narrativetrace:start -->\n"
                + "<!-- narrativetrace:end -->\n");

    assertThat(scan.problems()).singleElement().asString().contains("line 2");
  }

  // --- near misses: what is NOT a marker -----------------------------------------------------

  @Test
  void ignoresAMarkerInsideAFencedCodeBlock() {
    String text = "# Docs\n\n```\n" + BLOCK + "```\n";

    MarkedBlock.Scan scan = MarkedBlock.scan(text);

    assertThat(scan.regions()).isEmpty();
    assertThat(scan.problems()).isEmpty();
  }

  @Test
  void ignoresAMarkerInsideATildeFencedCodeBlock() {
    assertThat(MarkedBlock.scan("~~~\n" + BLOCK + "~~~\n").regions()).isEmpty();
  }

  @Test
  void seesAMarkerAgainAfterTheFenceCloses() {
    String text = "```\n<!-- narrativetrace:start -->\n```\n" + BLOCK;

    assertThat(MarkedBlock.scan(text).regions()).hasSize(1);
  }

  @Test
  void ignoresAnIndentedMarker() {
    String text = "  <!-- narrativetrace:start -->\n  <!-- narrativetrace:end -->\n";

    assertThat(MarkedBlock.scan(text).regions()).isEmpty();
    assertThat(MarkedBlock.scan(text).problems()).isEmpty();
  }

  @Test
  void ignoresThisRepositorysOwnSkillsMarkers() {
    String text = "<!-- narrativetrace:skills:start -->\nx\n<!-- narrativetrace:skills:end -->\n";

    assertThat(MarkedBlock.scan(text).regions()).isEmpty();
    assertThat(MarkedBlock.scan(text).problems()).isEmpty();
  }

  @Test
  void findsAMarkerOnTheFirstLineBehindAByteOrderMark() {
    MarkedBlock.Scan scan = MarkedBlock.scan("﻿" + BLOCK);

    assertThat(scan.regions()).hasSize(1);
  }

  @Test
  void findsARegionWhoseEndMarkerIsTheLastLineWithoutATrailingNewline() {
    String text = "<!-- narrativetrace:start -->\nbody\n<!-- narrativetrace:end -->";

    MarkedBlock.Scan scan = MarkedBlock.scan(text);

    assertThat(scan.regions()).hasSize(1);
    assertThat(text.substring(scan.regions().get(0).start(), scan.regions().get(0).end()))
        .isEqualTo(text);
  }

  // --- editing ------------------------------------------------------------------------------

  @Test
  void replacesOnlyTheRegionAndLeavesTheRestByteForByte() {
    String text = "# Title\n\n" + BLOCK + "\ntail\n";
    MarkedBlock.Region region = MarkedBlock.scan(text).regions().get(0);

    String replaced = MarkedBlock.replace(text, region, "<!-- new -->\n");

    assertThat(replaced).isEqualTo("# Title\n\n<!-- new -->\n\ntail\n");
  }

  @Test
  void replacePreservesWindowsLineEndings() {
    String text = "# Title\r\n\r\n" + BLOCK.replace("\n", "\r\n");
    MarkedBlock.Region region = MarkedBlock.scan(text).regions().get(0);

    String replaced = MarkedBlock.replace(text, region, MarkedBlock.withEol("x\n", "\r\n"));

    assertThat(replaced).isEqualTo("# Title\r\n\r\nx\r\n");
  }

  @Test
  void appendsAfterExactlyOneBlankLine() {
    assertThat(MarkedBlock.append("# Title\n", BLOCK)).isEqualTo("# Title\n\n" + BLOCK);
  }

  @Test
  void appendsToAFileWithoutATrailingNewlineByAddingOne() {
    assertThat(MarkedBlock.append("# Title", BLOCK)).isEqualTo("# Title\n\n" + BLOCK);
  }

  @Test
  void appendsToAnEmptyFileWithoutALeadingBlankLine() {
    assertThat(MarkedBlock.append("", BLOCK)).isEqualTo(BLOCK);
  }

  @Test
  void appendsWithTheFilesOwnLineEnding() {
    String crlf = BLOCK.replace("\n", "\r\n");

    assertThat(MarkedBlock.append("# Title\r\n", crlf)).isEqualTo("# Title\r\n\r\n" + crlf);
  }

  @Test
  void removingWhatWasAppendedRestoresTheFileByteForByte() {
    for (String original : new String[] {"# Title\n", "a\n\n\n", "x\r\n", "# T\r\n\r\n", ""}) {
      String block = MarkedBlock.withEol(BLOCK, MarkedBlock.eolOf(original));
      String appended = MarkedBlock.append(original, block);

      MarkedBlock.Region region = MarkedBlock.scan(appended).regions().get(0);

      assertThat(MarkedBlock.remove(appended, region))
          .as("append then remove is a round trip for %s", original.replace("\n", "\\n"))
          .isEqualTo(original);
    }
  }

  @Test
  void removingABlockAtTheStartOfTheFileLeavesTheRest() {
    String text = BLOCK + "tail\n";
    MarkedBlock.Region region = MarkedBlock.scan(text).regions().get(0);

    assertThat(MarkedBlock.remove(text, region)).isEqualTo("tail\n");
  }

  // --- unfinished fences ----------------------------------------------------------------------

  @Test
  void knowsWhenAFileEndsInsideAFenceThatWasNeverClosed() {
    assertThat(MarkedBlock.endsInsideFence("# Title\n\n```\ncode\n")).isTrue();
    assertThat(MarkedBlock.endsInsideFence("~~~\ncode\n")).isTrue();
    assertThat(MarkedBlock.endsInsideFence("```\ncode\n```\n")).isFalse();
    assertThat(MarkedBlock.endsInsideFence("```\na\n```\ntext\n```\nb\n")).isTrue();
    assertThat(MarkedBlock.endsInsideFence("# Title\n")).isFalse();
    assertThat(MarkedBlock.endsInsideFence("")).isFalse();
    assertThat(MarkedBlock.endsInsideFence("  ```\nindented, not a fence\n")).isFalse();
  }

  // --- line endings -------------------------------------------------------------------------

  @Test
  void readsTheFilesLineEndingFromItsFirstTerminator() {
    assertThat(MarkedBlock.eolOf("a\r\nb\n")).isEqualTo("\r\n");
    assertThat(MarkedBlock.eolOf("a\nb\r\n")).isEqualTo("\n");
    assertThat(MarkedBlock.eolOf("no terminator")).isEqualTo("\n");
    assertThat(MarkedBlock.eolOf("")).isEqualTo("\n");
  }

  @Test
  void readsTheLineEndingOfAFileThatStartsWithABlankLine() {
    assertThat(MarkedBlock.eolOf("\nx")).isEqualTo("\n");
    assertThat(MarkedBlock.eolOf("\r\nx")).isEqualTo("\r\n");
  }

  @Test
  void treatsALoneCarriageReturnAsAFinishedLine() {
    assertThat(MarkedBlock.append("x\r", BLOCK)).isEqualTo("x\r\n" + BLOCK);
  }

  @Test
  void rewritesABlocksLineEndings() {
    assertThat(MarkedBlock.withEol("a\nb\n", "\r\n")).isEqualTo("a\r\nb\r\n");
    assertThat(MarkedBlock.withEol("a\r\nb\r\n", "\n")).isEqualTo("a\nb\n");
  }
}
