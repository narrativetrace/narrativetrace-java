/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.FailureNote;
import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillSection;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeSkillRendererTest {

  @Test
  void rendersFrontmatterWithWhenToUseWhenPresent() {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "A description with a \"quote\" and a \\backslash.",
            "When to use it.",
            "sixty-seconds",
            List.of(
                new SkillStep("Step one", new StepBody.CommandStep(List.of("git status")), null)),
            List.of(),
            List.of(),
            CommandVocabulary.JAVA);

    String rendered = ClaudeSkillRenderer.render(skill);

    assertThat(rendered).startsWith("---\nname: narrativetrace-x\n");
    assertThat(rendered)
        .contains("description: \"A description with a \\\"quote\\\" and a \\\\backslash.\"\n");
    assertThat(rendered).contains("when_to_use: \"When to use it.\"\n");
    assertThat(rendered).contains("allowed-tools: Bash(./gradlew *), Bash(git *), Bash(find *)\n");
    assertThat(rendered).contains("# narrativetrace-x");
    assertThat(rendered).contains("## 1. Step one");
    assertThat(rendered).contains("```bash\ngit status\n```");
  }

  @Test
  void omitsWhenToUseLineWhenAbsent() {
    Skill skill = skillWithOneStep(null);
    assertThat(ClaudeSkillRenderer.render(skill)).doesNotContain("when_to_use:");
  }

  @Test
  void anEmptyCommandStepRendersNoFence() {
    Skill skill =
        skillWithSteps(
            List.of(new SkillStep("Judge it", new StepBody.CommandStep(List.of()), null)));
    assertThat(ClaudeSkillRenderer.render(skill)).doesNotContain("```bash");
  }

  @Test
  void codeStepAddsATrailingNewlineWhenMissing() {
    Skill skill =
        skillWithSteps(
            List.of(
                new SkillStep(
                    "Snippet", new StepBody.CodeStep("java", "int x = 1;"), "it compiles")));
    String rendered = ClaudeSkillRenderer.render(skill);
    assertThat(rendered).contains("```java\nint x = 1;\n```\n\n");
  }

  @Test
  void snippetStepReadsTheSourceFileAndWrapsItInMarkers(@TempDir Path repoRoot) throws IOException {
    Path source = repoRoot.resolve("sixty-seconds/src/main/java/com/example/orders/Main.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "class Main {}\n");
    Skill skill =
        skillWithSteps(
            List.of(
                new SkillStep(
                    "First trace",
                    new StepBody.SnippetStep(
                        "java", "sixty-seconds/src/main/java/com/example/orders/Main.java"),
                    "it runs")));

    String rendered = ClaudeSkillRenderer.render(skill, repoRoot);

    assertThat(rendered)
        .contains(
            "<!-- snippet: sixty-seconds/src/main/java/com/example/orders/Main.java -->\n"
                + "```java\n"
                + "class Main {}\n"
                + "```\n"
                + "<!-- /snippet -->\n");
  }

  /**
   * The publish pipeline stamps a BSL block-comment license header onto every staged {@code .java}
   * file (scripts/publish-public.sh's {@code emit_block_header}) that the in-tree source, and the
   * already-committed {@code SKILL.md} it ships alongside, never carry — an un-stripped embed would
   * drift the moment the renderer ran against a staged snapshot. Reproduces the exact shape that
   * header takes.
   */
  @Test
  void snippetStepStripsAStampedLicenseHeaderBeforeEmbedding(@TempDir Path repoRoot)
      throws IOException {
    Path source = repoRoot.resolve("sixty-seconds/src/main/java/com/example/orders/Main.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "/*\n"
            + " * Copyright (c) 2026 Empower Agile\n"
            + " *\n"
            + " * SPDX-License-Identifier: BUSL-1.1\n"
            + " * Licensed under the Business Source License 1.1 (see LICENSE); Change Date:"
            + " four\n"
            + " * years from publication; Change License: Apache-2.0\n"
            + " */\n"
            + "// src/main/java/com/example/orders/Main.java\n"
            + "class Main {}\n");
    Skill skill =
        skillWithSteps(
            List.of(
                new SkillStep(
                    "First trace",
                    new StepBody.SnippetStep(
                        "java", "sixty-seconds/src/main/java/com/example/orders/Main.java"),
                    "it runs")));

    String rendered = ClaudeSkillRenderer.render(skill, repoRoot);

    assertThat(rendered)
        .contains(
            "<!-- snippet: sixty-seconds/src/main/java/com/example/orders/Main.java -->\n"
                + "```java\n"
                + "// src/main/java/com/example/orders/Main.java\n"
                + "class Main {}\n"
                + "```\n"
                + "<!-- /snippet -->\n")
        .doesNotContain("SPDX-License-Identifier")
        .doesNotContain("Empower Agile");
  }

  /**
   * A leading block comment that is NOT a stamped license header (e.g. a real Javadoc) survives.
   */
  @Test
  void snippetStepPreservesANonLicenseLeadingBlockComment(@TempDir Path repoRoot)
      throws IOException {
    Path source = repoRoot.resolve("sixty-seconds/src/main/java/com/example/orders/Main.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "/** A real Javadoc. */\nclass Main {}\n");
    Skill skill =
        skillWithSteps(
            List.of(
                new SkillStep(
                    "First trace",
                    new StepBody.SnippetStep(
                        "java", "sixty-seconds/src/main/java/com/example/orders/Main.java"),
                    "it runs")));

    String rendered = ClaudeSkillRenderer.render(skill, repoRoot);

    assertThat(rendered).contains("/** A real Javadoc. */\nclass Main {}\n");
  }

  @Test
  void rendersFailureNotesAndFlags() {
    Skill skill =
        skillWithSteps(
            List.of(
                new SkillStep(
                    "Flagged step",
                    new StepBody.CommandStep(List.of()),
                    null,
                    List.of(new FailureNote("symptom", "cause", "fix")),
                    "unstudied — eval cell pending")));
    String rendered = ClaudeSkillRenderer.render(skill);
    assertThat(rendered).contains("**Flagged:** unstudied — eval cell pending");
    assertThat(rendered).contains("**failure:** symptom → cause → fix");
  }

  @Test
  void rendersAStepConditionBetweenTheHeadingAndTheBody() {
    Skill skill =
        skillWithSteps(
            List.of(
                new SkillStep(
                    "Conditional step",
                    new StepBody.CodeStep("kotlin", "plugins { java }"),
                    "./gradlew build",
                    List.of(),
                    null,
                    "only when the project has no entry point")));
    String rendered = ClaudeSkillRenderer.render(skill);
    assertThat(rendered)
        .contains(
            "## 1. Conditional step\n\n"
                + "**when:** only when the project has no entry point\n\n"
                + "```kotlin");
  }

  @Test
  void rendersNoWhenLineForAnUnconditionalStep() {
    assertThat(ClaudeSkillRenderer.render(skillWithOneStep(null))).doesNotContain("**when:**");
  }

  @Test
  void omitsAlwaysAndNeverSectionsWhenEmpty() {
    Skill skill = skillWithOneStep(null);
    String rendered = ClaudeSkillRenderer.render(skill);
    assertThat(rendered).doesNotContain("## Always").doesNotContain("## Never");
  }

  @Test
  void rendersAlwaysAndNeverWithReasons() {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "A description.",
            null,
            "sixty-seconds",
            List.of(new SkillStep("Step", new StepBody.CommandStep(List.of()), "verify")),
            List.of(new ReasonedRule("always do X", "because")),
            List.of(new ReasonedRule("never do Y", "because also")),
            CommandVocabulary.JAVA);
    String rendered = ClaudeSkillRenderer.render(skill);
    assertThat(rendered).contains("## Always\n\n- always do X (because)\n");
    assertThat(rendered).contains("## Never\n\n- never do Y (because also)\n");
  }

  @Test
  void rendersEachSectionAfterTheStepsAndBeforeTheRules() {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.GUIDED,
            "A description.",
            null,
            "sixty-seconds",
            List.of(new SkillStep("Step", new StepBody.CommandStep(List.of()), "verify")),
            List.of(new ReasonedRule("always do X", "because")),
            List.of(),
            List.of(),
            List.of(new SkillSection("How to read a trace", "| flavour | answers |\n")));
    String rendered = ClaudeSkillRenderer.render(skill);
    assertThat(rendered)
        .contains(
            "**verify:** verify\n\n## How to read a trace\n\n| flavour | answers |\n\n## Always");
  }

  @Test
  void aSkillBuiltWithoutSectionsRendersNone() {
    assertThat(skillWithOneStep(null).sections()).isEmpty();
  }

  /**
   * The two characters nothing else feeds this method: a literal backslash and a quote. Both are
   * escaped, and the ORDER is what makes them work together — the escape character is doubled
   * FIRST, before the replacements that insert escapes of their own. Get that wrong and the
   * frontmatter `description` breaks, which is the one field an agent reads to decide whether to
   * invoke a skill at all. (The newline case below catches a full reversal too; what it cannot
   * catch is a description carrying a backslash, because it never feeds one.)
   */
  @Test
  void yamlQuoteEscapesTheEscapeCharacterBeforeTheCharactersItEscapes() {
    Skill skill = skillDescribedAs("a path C:\\tmp and a \"quote\"\nthen a second line");

    String rendered = ClaudeSkillRenderer.render(skill);

    assertThat(rendered)
        .contains("description: \"a path C:\\\\tmp and a \\\"quote\\\"\\nthen a second line\"\n");
  }

  private static Skill skillDescribedAs(String description) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.MECHANICAL,
        description,
        null,
        "sixty-seconds",
        List.of(new SkillStep("Step", new StepBody.CommandStep(List.of()), "verify")),
        List.of(),
        List.of(),
        CommandVocabulary.JAVA);
  }

  @Test
  void yamlQuoteEscapesNewlinesAndDropsCarriageReturns() {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "line one\r\nline two",
            null,
            "sixty-seconds",
            List.of(new SkillStep("Step", new StepBody.CommandStep(List.of()), "verify")),
            List.of(),
            List.of(),
            CommandVocabulary.JAVA);
    String rendered = ClaudeSkillRenderer.render(skill);
    assertThat(rendered).contains("description: \"line one\\nline two\"\n");
  }

  private static Skill skillWithOneStep(String whenToUse) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.MECHANICAL,
        "A description.",
        whenToUse,
        "sixty-seconds",
        List.of(new SkillStep("Step", new StepBody.CommandStep(List.of()), "verify")),
        List.of(),
        List.of(),
        CommandVocabulary.JAVA);
  }

  private static Skill skillWithSteps(List<SkillStep> steps) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.MECHANICAL,
        "A description.",
        null,
        "sixty-seconds",
        steps,
        List.of(),
        List.of(),
        CommandVocabulary.JAVA);
  }
}
