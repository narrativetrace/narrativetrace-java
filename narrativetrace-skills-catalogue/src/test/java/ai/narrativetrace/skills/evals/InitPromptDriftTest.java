/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Docs-as-tests rule 8, applied to the init prompt: a prompt we publish is a prompt we replay, so
 * the published text and the replayed text may never drift apart. The prompt lives in five
 * published places — the English README and its three language mirrors (the prompt itself stays in
 * English on purpose; only the sentence introducing it is translated) and {@code
 * documentation/llms.txt} — in the trigger case that asks whether the skill fires on the text we
 * tell people to paste, and in every Tier B case whose {@code prompt.md} IS the prompt and nothing
 * else. This test is what makes those copies safe: they are byte-identical or {@code check} fails.
 *
 * <p>The CASE copies are DISCOVERED rather than listed, and the four expected ones are then
 * asserted to be among them. A listed-only comparison could not see a new case's copy at all, which
 * is how a copy ends up outside the gate it was supposed to be inside; discovery sees every one,
 * and the explicit list still catches a case whose prompt stopped being the published prompt.
 *
 * @llmNote Editing the prompt means editing every copy. The trigger copy is a YAML block scalar, so
 *     it is compared de-indented; the mirrors' blob-hash headers are a separate concern ({@code
 *     translationCheck}), and this test only compares the prompt itself.
 */
class InitPromptDriftTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  private static final List<String> PAGES_WITH_A_FENCED_BLOCK =
      List.of("README.md", "LEAME.md", "LEIAME.md", "自述文件.md", "documentation/llms.txt");

  private static final Path EVALS_DIR = Path.of("narrativetrace-skills-catalogue", "evals");

  /** The prompt's own first line — what makes a {@code prompt.md} a copy of it. */
  private static final String FIRST_LINE = "Set up NarrativeTrace in this project";

  /**
   * Every case that replays the published prompt today: its two branches, the Spring Boot project
   * (Phase 6, D4), and the two registry paths that reach it through a registry install first. Each
   * must be among the DISCOVERED copies.
   */
  private static final List<String> CASES_WHOSE_PROMPT_IS_THE_WHOLE_FILE =
      List.of(
          "add-narrative-tracing/init-prompt-empty-project/prompt.md",
          "add-narrative-tracing/init-prompt-existing-project/prompt.md",
          "add-narrative-tracing/init-prompt-spring-boot-project/prompt.md",
          "add-narrative-tracing/registry-claude-marketplace/prompt.md",
          "add-narrative-tracing/registry-npx-skills/prompt.md");

  /** The trigger case that fires the skill on the published text — a YAML block scalar. */
  private static final String TRIGGER_CASE =
      "narrativetrace-skills-catalogue/evals/add-narrative-tracing/trigger.yaml";

  @Test
  void everyCopyOfTheInitPromptIsByteIdentical() throws IOException {
    List<Path> replays = caseCopiesOfThePublishedPrompt();
    assertThat(replays.stream().map(InitPromptDriftTest::relativeToEvals).toList())
        .as("every case that replays the published prompt")
        .containsAll(CASES_WHOSE_PROMPT_IS_THE_WHOLE_FILE);

    List<String> copies = new ArrayList<>();
    for (String page : PAGES_WITH_A_FENCED_BLOCK) {
      copies.add(fencedInitPrompt(REPO_ROOT.resolve(page)));
    }
    for (Path casePrompt : replays) {
      copies.add(Files.readString(casePrompt).strip());
    }
    copies.add(blockScalarInitPrompt(REPO_ROOT.resolve(TRIGGER_CASE)));
    assertThat(copies).hasSize(PAGES_WITH_A_FENCED_BLOCK.size() + replays.size() + 1);
    assertThat(copies)
        .as("every copy of the init prompt, published or replayed, byte for byte")
        .containsOnly(copies.get(0));
  }

  /** Every {@code prompt.md} under {@code evals/} whose first line is the published prompt's. */
  private static List<Path> caseCopiesOfThePublishedPrompt() throws IOException {
    try (Stream<Path> files = Files.walk(REPO_ROOT.resolve(EVALS_DIR))) {
      return files
          .filter(path -> path.getFileName().toString().equals("prompt.md"))
          .filter(InitPromptDriftTest::opensWithThePromptsFirstLine)
          .sorted()
          .toList();
    }
  }

  private static boolean opensWithThePromptsFirstLine(Path promptFile) {
    try {
      return Files.readString(promptFile).strip().startsWith(FIRST_LINE);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + promptFile, e);
    }
  }

  private static String relativeToEvals(Path promptFile) {
    return REPO_ROOT.resolve(EVALS_DIR).relativize(promptFile).toString();
  }

  @Test
  void theReplayedPromptIsTheOneAnAgentIsToldToPaste() throws IOException {
    String published = fencedInitPrompt(REPO_ROOT.resolve("README.md"));

    assertThat(published)
        .startsWith("Set up NarrativeTrace in this project and show me its first trace.");
    assertThat(published).contains("https://narrativetrace.ai/java/llms.txt");
    assertThat(published).contains("never disable redaction");
  }

  /**
   * The four things the prompt promises that something downstream is built to deliver: the
   * installer step and its human gate, the skill-if-available branch, the redaction proof the
   * doctor's {@code trap.redaction-proof} check reads, and the doctor task itself. Each is graded
   * by a Tier B case, so a silent edit here would quietly move the graders' goalposts.
   *
   * <p>Asserted against the prompt with its hard wrap flattened: what is frozen is the wording, and
   * a re-wrap that keeps every word is not drift. Byte-for-byte identity ACROSS the copies is the
   * other test's subject.
   */
  @Test
  void theInstallerStepNamesTheDiffFlagAndKeepsTheHumanGate() throws IOException {
    String flowed = flowed(fencedInitPrompt(REPO_ROOT.resolve("README.md")));

    assertThat(flowed).contains("run `./gradlew narrativetraceInit --diff` and show me the diff");
    assertThat(flowed)
        .as("Gradle's built-in --dry-run skips every task in the graph, so the flag is --diff")
        .doesNotContain("narrativetraceInit --dry-run");
    assertThat(flowed).contains("Run it for real only after I have seen the diff.");
    assertThat(flowed)
        .contains("If the `add-narrative-tracing` skill is now available, follow it.");
    assertThat(flowed).contains("asserts the trace shows `[REDACTED]` for it");
    assertThat(flowed).contains("run the doctor (`./gradlew narrativetraceDoctor`)");
    assertThat(flowed).contains("if you cannot fetch URLs, say so and I will paste llms.txt");
  }

  /**
   * The YAML block scalar whose first line is the prompt's, de-indented by the block's own indent.
   *
   * <p>Hand-parsed rather than read through a YAML library: this module takes no dependency it does
   * not need, and the one shape that matters here — {@code - |} followed by an indented block — is
   * exactly the shape a comparison must not be lenient about.
   */
  private static String blockScalarInitPrompt(Path yaml) throws IOException {
    List<String> lines = Files.readAllLines(yaml);
    int marker = indexOfBlockScalarMarker(lines);
    if (marker < 0) {
      throw new AssertionError(yaml + " carries no block-scalar init prompt");
    }
    int indent = indentOf(lines.get(marker + 1));
    List<String> body = new ArrayList<>();
    for (String line : lines.subList(marker + 1, lines.size())) {
      if (line.isBlank()) {
        body.add("");
      } else if (indentOf(line) < indent) {
        break;
      } else {
        body.add(line.substring(indent));
      }
    }
    return String.join("\n", body).strip();
  }

  /** The {@code - |} line whose block opens with the prompt's first line, or -1. */
  private static int indexOfBlockScalarMarker(List<String> lines) {
    for (int i = 0; i < lines.size() - 1; i++) {
      if (lines.get(i).strip().equals("- |")
          && lines.get(i + 1).strip().startsWith("Set up NarrativeTrace in this project")) {
        return i;
      }
    }
    return -1;
  }

  /** Leading spaces on a line — YAML block scalars are space-indented, never tab-indented. */
  private static int indentOf(String line) {
    int spaces = 0;
    while (spaces < line.length() && line.charAt(spaces) == ' ') {
      spaces++;
    }
    return spaces;
  }

  /** The prompt as one flowed paragraph — every run of whitespace collapsed to a single space. */
  private static String flowed(String prompt) {
    return prompt.replaceAll("\\s+", " ");
  }

  /**
   * The one fenced block on the page that opens with the prompt's first line, without its fences.
   */
  private static String fencedInitPrompt(Path page) throws IOException {
    List<String> lines = Files.readAllLines(page);
    for (int i = 0; i < lines.size() - 1; i++) {
      if (lines.get(i).startsWith("```")
          && lines.get(i + 1).startsWith("Set up NarrativeTrace in this project")) {
        List<String> body = new ArrayList<>();
        for (int j = i + 1; j < lines.size() && !lines.get(j).equals("```"); j++) {
          body.add(lines.get(j));
        }
        return String.join("\n", body).strip();
      }
    }
    throw new AssertionError(page + " carries no fenced init-prompt block");
  }
}
