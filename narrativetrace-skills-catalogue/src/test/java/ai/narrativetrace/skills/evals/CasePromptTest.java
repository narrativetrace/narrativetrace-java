/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * INTENT: {@link CasePrompt} is what stands between a case document's own "Expected trajectory" and
 * "Grading" sections and the agent being graded against them. These tests pin the two kinds, the
 * unwrap rules a case document's blockquote follows, the refusal a malformed one takes, and — the
 * enumeration test — that every real {@code prompt.md} under the catalogue's {@code evals/}
 * directory resolves under the rule with no grader section ever reaching what is sent.
 */
class CasePromptTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));
  private static final Path EVALS_DIR = REPO_ROOT.resolve("narrativetrace-skills-catalogue/evals");
  private static final Path SOME_PATH = Path.of("some/case/prompt.md");

  @Test
  void aFirstLineOfCaseColonIsACaseDocument() {
    assertThat(CasePrompt.kind("# Case: happy-path\n\nSome text.\n"))
        .isEqualTo(CasePrompt.Kind.CASE_DOCUMENT);
  }

  @Test
  void anyOtherFirstLineIsABarePrompt() {
    assertThat(CasePrompt.kind("Set up NarrativeTrace in this project.\n"))
        .isEqualTo(CasePrompt.Kind.BARE_PROMPT);
  }

  @Test
  void aBarePromptIsSentByteForByte() {
    String text =
        "Set up NarrativeTrace in this project.\n\n1. Do the thing.\n   wrapped on purpose.\n";
    assertThat(CasePrompt.taskPrompt(SOME_PATH, text)).isEqualTo(text);
  }

  @Test
  void aCaseDocumentsBlockquoteIsUnwrappedIntoOneParagraph() {
    String text =
        """
        # Case: happy-path

        **Task prompt** (given to the agent, catalogue loaded):

        > This is a brand new Java project. I want to add NarrativeTrace and see a trace from a
        > real method call within the next few minutes.

        **Expected trajectory:** irrelevant to the agent.
        """;
    assertThat(CasePrompt.taskPrompt(SOME_PATH, text))
        .isEqualTo(
            "This is a brand new Java project. I want to add NarrativeTrace and see a trace from a"
                + " real method call within the next few minutes.")
        .doesNotContain("Expected trajectory");
  }

  @Test
  void blankLinesBetweenTheTaskPromptLineAndTheBlockquoteAreSkipped() {
    String text =
        """
        # Case: x

        **Task prompt:**


        > One line.

        **Grading:** irrelevant.
        """;
    assertThat(CasePrompt.taskPrompt(SOME_PATH, text)).isEqualTo("One line.");
  }

  @Test
  void anEmptyQuoteLineIsAParagraphBreakAndParagraphsJoinOnOneBlankLine() {
    String text =
        """
        # Case: x

        **Task prompt:**

        > First paragraph,
        > wrapped once.
        >
        > Second paragraph.

        **Grading:** irrelevant.
        """;
    assertThat(CasePrompt.taskPrompt(SOME_PATH, text))
        .isEqualTo("First paragraph, wrapped once.\n\nSecond paragraph.");
  }

  @Test
  void aCaseDocumentWithNoTaskPromptLineRefusesRatherThanFallingBackToTheWholeFile() {
    String text = "# Case: x\n\nJust a paragraph, no Task prompt line at all.\n";
    assertThatThrownBy(() -> CasePrompt.taskPrompt(SOME_PATH, text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(SOME_PATH.toString());
  }

  @Test
  void aCaseDocumentWithATaskPromptLineAndNoBlockquoteAlsoRefuses() {
    String text = "# Case: x\n\n**Task prompt:**\n\nNo quote marker on this line.\n";
    assertThatThrownBy(() -> CasePrompt.taskPrompt(SOME_PATH, text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(SOME_PATH.toString());
  }

  static List<Path> everyPromptMdUnderEvals() {
    try (var stream = Files.walk(EVALS_DIR)) {
      return stream
          .filter(p -> p.getFileName().toString().equals("prompt.md"))
          .sorted()
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException("could not list " + EVALS_DIR, e);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyPromptMdUnderEvals")
  void everyPromptMdResolvesUnderTheRuleWithNoGraderSectionEverSent(Path promptMd) {
    String text = readQuietly(promptMd);
    String sent;
    try {
      sent = CasePrompt.taskPrompt(promptMd, text);
    } catch (IllegalArgumentException refused) {
      assertThat(refused.getMessage())
          .as(promptMd + " refuses rather than falling back to the whole file")
          .contains(promptMd.toString());
      return;
    }
    assertThat(sent)
        .as(promptMd + " must never hand the agent its own grading sections")
        .doesNotContain("Expected trajectory")
        .doesNotContain("Grading")
        .doesNotContain("graders/");
  }

  static List<Path> everyCaseDocumentUnderEvals() {
    return everyPromptMdUnderEvals().stream()
        .filter(p -> CasePrompt.kind(readQuietly(p)) == CasePrompt.Kind.CASE_DOCUMENT)
        .collect(Collectors.toList());
  }

  /**
   * B-95: a {@code # Case:} document is this repository's own documentation of a case — written as
   * if a task prompt is there to read. A missing {@code **Task prompt} blockquote in one is a
   * defect in the document, not a legitimate shape {@link CasePrompt} should quietly refuse; {@link
   * #everyPromptMdResolvesUnderTheRuleWithNoGraderSectionEverSent} above tolerates the refusal
   * because that is correct for a document that STAYS malformed, but every real case document under
   * {@code evals/} must actually resolve one.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("everyCaseDocumentUnderEvals")
  void everyCaseDocumentResolvesATaskPromptRatherThanRefusing(Path promptMd) {
    String text = readQuietly(promptMd);
    assertThatCode(() -> CasePrompt.taskPrompt(promptMd, text))
        .as(promptMd + " is a case document and must resolve a task prompt, not refuse")
        .doesNotThrowAnyException();
  }

  private static String readQuietly(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + path, e);
    }
  }
}
