/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-11-02-1 — {@code MarkdownTableEscape.prose} decides whether a line reopens the
 * document's structure with {@code line.startsWith(...)}, so every marker it escapes is forgeable
 * by indenting it one space: CommonMark allows up to three spaces of indentation before an ATX
 * heading, a fenced code block, a blockquote, a list item, a thematic break and a GFM table row.
 *
 * <p>INTENT: {@code GlossaryDocumentStructureForgeryTest} pinned the column-zero half of this
 * (ADV-2026-10-30-2, B-251). The escape it drove reads only column zero, so the same description
 * that may no longer write {@code ## Accepted abbreviations} may still write {@code " ## Accepted
 * abbreviations"}, and the document's shape is an input's to choose again. The oracle is the
 * family's own: a Markdown reader strips a line's indentation before it decides what the line is,
 * which is exactly what the security suite's fence rule ({@code line.strip().startsWith("```")})
 * already does.
 *
 * <p><b>@llmNote</b> Suspected cause {@code MarkdownTableEscape#escapeStructuralLine} — the {@code
 * startsWith} ladder and both {@code Pattern}s anchor at index 0 of the raw line rather than at the
 * first non-indent character.
 */
class GlossaryIndentedStructureForgeryTest {

  private final GlossaryMarkdownRenderer renderer = new GlossaryMarkdownRenderer();

  @Test
  @DisplayName("ADV-2026-11-02-1: a one-space-indented fence may not unbalance the document")
  void anIndentedFenceDoesNotUnbalanceTheDocumentsCodeFences() {
    var hostile = renderer.render(glossaryDescribedAs("Billing domain.\n ```\nnot code\n"));

    assertThat(fenceCount(hostile) % 2).as("every fence this document opens, it closes").isZero();
  }

  @Test
  @DisplayName("ADV-2026-11-02-1: a three-space-indented heading may not join the outline")
  void anIndentedHeadingDoesNotForgeASecondLevelHeading() {
    var benign = renderer.render(glossaryDescribedAs("Billing domain."));
    var hostile =
        renderer.render(glossaryDescribedAs("Billing domain.\n\n   ## Accepted abbreviations\n"));

    assertThat(headingCount(hostile))
        .as("one context, so one heading — the benign document's own count")
        .isEqualTo(headingCount(benign));
  }

  @Test
  @DisplayName("ADV-2026-11-02-1: an indented blockquote, list item and table row stay prose")
  void anIndentedBlockMarkerIsStillEscaped() {
    assertThat(readableAsPlainParagraph(" > quoted")).as("blockquote").isTrue();
    assertThat(readableAsPlainParagraph("  - item")).as("list item").isTrue();
    assertThat(readableAsPlainParagraph("   | a | b |")).as("table row").isTrue();
    assertThat(readableAsPlainParagraph(" ***")).as("thematic break").isTrue();
  }

  /** Whether a description survives the render with no line a Markdown reader reads as a block. */
  private boolean readableAsPlainParagraph(String description) {
    var rendered = renderer.render(glossaryDescribedAs("Billing domain.\n\n" + description + "\n"));
    return rendered
        .lines()
        .map(GlossaryIndentedStructureForgeryTest::deindented)
        .noneMatch(
            line ->
                line.startsWith(">")
                    || line.startsWith("|")
                    || line.matches("(?:[-*+]|\\d+[.)])(?:\\s.*|)")
                    || line.matches("(?:[-*_]\\s*){3,}"));
  }

  /** How many fenced-code delimiters a reader sees — the security suite's own rule. */
  private static long fenceCount(String markdown) {
    return markdown.lines().filter(line -> line.strip().startsWith("```")).count();
  }

  /** How many ATX headings a reader sees, indentation stripped the way CommonMark strips it. */
  private static long headingCount(String markdown) {
    return markdown
        .lines()
        .map(GlossaryIndentedStructureForgeryTest::deindented)
        .filter(line -> line.matches("#{1,6} .*"))
        .count();
  }

  /** Drops the up-to-three spaces of indentation CommonMark allows before a block marker. */
  private static String deindented(String line) {
    var start = 0;
    while (start < 3 && start < line.length() && line.charAt(start) == ' ') {
      start++;
    }
    return line.substring(start);
  }

  private static Glossary glossaryDescribedAs(String description) {
    return new Glossary(
        1,
        Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), description)),
        List.of());
  }
}
