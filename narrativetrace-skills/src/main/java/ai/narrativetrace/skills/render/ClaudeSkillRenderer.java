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
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.FailureNote;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders a typed {@link Skill} into the Claude plugin's {@code SKILL.md} shape: YAML frontmatter
 * ({@code name}, {@code description}, optional {@code when_to_use}, {@code allowed-tools} emitted
 * from the command vocabulary — harness principle 7, runtime-enforced, not just linted) followed by
 * a numbered-step body and the Always/Never lists. This is BUILD OUTPUT — the typed {@link Skill}
 * is the only place any of this text is hand-written.
 *
 * <p>A {@link StepBody.SnippetStep} is the one exception to "hand-written": its code comes from a
 * real repo file, read fresh at render time and wrapped in the same {@code <!-- snippet: path -->}
 * markers {@code documentation/sixty-seconds.md} uses, so the root {@code snippetCheck} task (see
 * {@code SnippetSupport}) — widened to also govern every {@code SKILL.md} under {@code
 * .claude/skills/} — catches the rendered page drifting from the source the moment either one
 * changes without the other.
 *
 * <p>{@link #readSnippetSource} strips a leading license-header block comment from the source
 * before embedding it (see that method) — the same rule buildSrc's {@code
 * SnippetSupport.stripLicenseHeader} enforces for every other embedded snippet, duplicated rather
 * than shared because a subproject's main source set cannot depend on buildSrc classes. Found
 * 2026-09-13: the public-snapshot publish pipeline stamps a BSL/Apache header onto every staged
 * {@code .java} file, including {@code sixty-seconds}' sources this renderer embeds, but never
 * regenerates the already-committed {@code SKILL.md} pages (they ship as-is via {@code
 * .publishignore}'s restore-exception) — so an un-stripped comparison would fail {@link
 * ai.narrativetrace.skills.render.RenderDriftTest} the moment it ran against a staged snapshot,
 * even though the in-tree checkout was always clean.
 */
public final class ClaudeSkillRenderer {

  private ClaudeSkillRenderer() {}

  /** Resolves the repo root from the {@code projectDir} system property Gradle's test task sets. */
  public static String render(Skill skill) {
    return render(skill, Path.of(System.getProperty("projectDir", ".")));
  }

  public static String render(Skill skill, Path repoRoot) {
    StringBuilder out = new StringBuilder();
    out.append("---\n");
    out.append("name: ").append(skill.claudeSegment()).append('\n');
    out.append("description: ").append(yamlQuote(skill.description())).append('\n');
    skill
        .whenToUseOptional()
        .ifPresent(w -> out.append("when_to_use: ").append(yamlQuote(w)).append('\n'));
    out.append("allowed-tools: ").append(String.join(", ", skill.allowedTools())).append('\n');
    out.append("---\n\n");

    out.append("# ").append(skill.canonicalName()).append("\n\n");

    List<SkillStep> steps = skill.steps();
    for (int i = 0; i < steps.size(); i++) {
      renderStep(out, i + 1, steps.get(i), repoRoot);
    }

    renderRuleSection(out, "Always", skill.always());
    renderRuleSection(out, "Never", skill.never());

    return out.toString();
  }

  private static void renderStep(StringBuilder out, int number, SkillStep step, Path repoRoot) {
    out.append("## ").append(number).append(". ").append(step.title()).append("\n\n");
    step.flagOptional().ifPresent(flag -> out.append("**Flagged:** ").append(flag).append("\n\n"));

    if (step.body() instanceof StepBody.CommandStep commands) {
      renderCommandStep(out, commands);
    } else if (step.body() instanceof StepBody.CodeStep code) {
      renderCodeStep(out, code);
    } else if (step.body() instanceof StepBody.SnippetStep snippet) {
      renderSnippetStep(out, snippet, repoRoot);
    }

    step.verifyOptional().ifPresent(v -> out.append("**verify:** ").append(v).append("\n\n"));
    for (FailureNote note : step.failure()) {
      out.append("**failure:** ")
          .append(note.symptom())
          .append(" → ")
          .append(note.cause())
          .append(" → ")
          .append(note.fix())
          .append("\n\n");
    }
  }

  private static void renderCommandStep(StringBuilder out, StepBody.CommandStep commands) {
    if (!commands.commands().isEmpty()) {
      out.append("```bash\n");
      commands.commands().forEach(c -> out.append(c).append('\n'));
      out.append("```\n\n");
    }
  }

  private static void renderCodeStep(StringBuilder out, StepBody.CodeStep code) {
    out.append("```").append(code.language()).append('\n');
    out.append(code.code());
    if (!code.code().endsWith("\n")) {
      out.append('\n');
    }
    out.append("```\n\n");
  }

  private static void renderSnippetStep(
      StringBuilder out, StepBody.SnippetStep snippet, Path repoRoot) {
    String content = readSnippetSource(repoRoot, snippet.path());
    out.append("<!-- snippet: ").append(snippet.path()).append(" -->\n");
    out.append("```").append(snippet.language()).append('\n');
    out.append(content);
    if (!content.endsWith("\n")) {
      out.append('\n');
    }
    out.append("```\n");
    out.append("<!-- /snippet -->\n\n");
  }

  /**
   * A {@link StepBody.SnippetStep}'s current source text, read fresh (never cached) so the render
   * always reflects whatever is on disk right now — the same file the root {@code snippetCheck}/
   * {@code snippetSync} tasks compare the committed page against — with any stamped license header
   * stripped first (see {@link #stripLicenseHeader}).
   */
  private static String readSnippetSource(Path repoRoot, String path) {
    try {
      return stripLicenseHeader(Files.readString(repoRoot.resolve(path)));
    } catch (IOException e) {
      throw new UncheckedIOException("could not read snippet source '" + path + "'", e);
    }
  }

  private static final List<String> LICENSE_HEADER_MARKERS =
      List.of("SPDX-License-Identifier", "Licensed under");

  /**
   * Strips a leading {@code /* ... *}{@code /} block-comment license header from {@code text}
   * before it is embedded — never the page, never the in-tree source itself. Mirrors buildSrc's
   * {@code SnippetSupport.stripLicenseHeader} (not reusable here: buildSrc classes are not visible
   * to a subproject's main source set), narrowed to the block-comment shape {@code
   * scripts/publish-public.sh}'s {@code emit_block_header} always stamps onto a {@code .java} file
   * — the only source type this catalogue's {@link StepBody.SnippetStep}s currently embed.
   *
   * <p>Only a leading block comment whose own text names the header ({@link
   * #LICENSE_HEADER_MARKERS}) is removed, along with the blank line right after it when there is
   * one — a source file's own leading block comment (a real Javadoc, say) never carries that text
   * and is preserved verbatim; a file with no leading block comment at all is returned unchanged.
   */
  private static String stripLicenseHeader(String text) {
    if (!text.startsWith("/*")) {
      return text;
    }
    int closeIdx = text.indexOf("*/");
    if (closeIdx < 0) {
      return text;
    }
    String header = text.substring(0, closeIdx);
    if (LICENSE_HEADER_MARKERS.stream().noneMatch(header::contains)) {
      return text;
    }
    int bodyStart = closeIdx + 2;
    if (text.startsWith("\n", bodyStart)) {
      bodyStart++;
    }
    if (text.startsWith("\n", bodyStart)) {
      bodyStart++;
    }
    return text.substring(bodyStart);
  }

  private static void renderRuleSection(
      StringBuilder out, String heading, List<ai.narrativetrace.skills.ReasonedRule> rules) {
    if (rules.isEmpty()) {
      return;
    }
    out.append("## ").append(heading).append("\n\n");
    rules.forEach(
        r -> out.append("- ").append(r.rule()).append(" (").append(r.reason()).append(")\n"));
    out.append('\n');
  }

  /** A YAML double-quoted scalar: escapes backslashes and quotes, keeps it one line. */
  private static String yamlQuote(String text) {
    String escaped =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    return "\"" + escaped + "\"";
  }
}
