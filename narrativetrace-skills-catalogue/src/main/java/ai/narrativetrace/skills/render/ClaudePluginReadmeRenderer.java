/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.MarketplaceListing;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.catalogue.ReleaseLookup;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders {@code .claude/README.md}: the plugin folder's README, which Anthropic's plugin directory
 * shows as the listing description and whose security scan reads for behaviour the plugin does not
 * disclose. It says what the plugin is, what its skills run, fetch and send, and under which
 * licence it ships.
 *
 * <p>Every disclosed fact is derived from the typed catalogue, never typed twice: the skill names
 * and their one-line summaries from {@link Skill#description()}, the commands and Gradle tasks from
 * {@link Skill#commandStrings()}, the version lookup from {@link ReleaseLookup}, and the licence,
 * homepage and repository from the {@link MarketplaceListing}. A command added to a skill therefore
 * reaches the README with the next render, and {@code RenderDriftTest} fails the build until it
 * does.
 *
 * <p>The README names no version number, for the reason the plugin manifest does not: a release
 * that leaves the skills alone leaves this folder byte-identical.
 *
 * <p><b>@llmNote</b> The statements about what the feedback skill sends are not derived: they are
 * the contract of that skill (it drafts, shows the draft, asks once, then prints a URL and never
 * opens it), pinned by the skill's own tests and by an absence of any network client in the tooling
 * the skill runs. Change them only together with that skill.
 */
public final class ClaudePluginReadmeRenderer {

  private static final String GRADLE = "./gradlew";

  private ClaudePluginReadmeRenderer() {}

  public static String render(MarketplaceListing listing, List<Skill> skills) {
    StringBuilder out = new StringBuilder();
    appendIntroduction(out, listing, skills);
    appendSkills(out, skills);
    appendRuns(out, skills);
    appendFetches(out);
    appendSends(out, listing);
    appendLicense(out, listing);
    return out.toString();
  }

  private static void appendIntroduction(
      StringBuilder out, MarketplaceListing listing, List<Skill> skills) {
    out.append("# ").append(listing.name()).append("\n\n");
    out.append(listing.description()).append(' ');
    out.append(
        "This plugin carries "
            + skills.size()
            + " skills and nothing else: no hooks, no MCP servers, no executables. Each skill is a"
            + " page of instructions that Claude follows in your Java project, using your own"
            + " build and Claude Code's normal permission prompts.\n\n");
    out.append("Install it from this repository's marketplace:\n\n");
    out.append("```\n");
    out.append("/plugin marketplace add ").append(slug(listing)).append('\n');
    out.append("/plugin install ").append(listing.name()).append('@').append(listing.name());
    out.append("\n```\n\n");
  }

  private static void appendSkills(StringBuilder out, List<Skill> skills) {
    out.append("## Skills\n\n");
    for (Skill skill : skills) {
      out.append("- `")
          .append(skill.canonicalName())
          .append("`: ")
          .append(summary(skill))
          .append('\n');
    }
    out.append('\n');
  }

  private static void appendRuns(StringBuilder out, List<Skill> skills) {
    Set<String> tasks = new TreeSet<>();
    Set<String> others = new LinkedHashSet<>();
    for (Skill skill : skills) {
      for (String command : skill.commandStrings()) {
        if (command.startsWith(GRADLE)) {
          tasks.addAll(gradleTasks(command));
        } else {
          others.add(command);
        }
      }
    }
    out.append("## What the skills run\n\n");
    out.append("The plugin pre-approves no command. The first time a skill runs one of these in");
    out.append(" your project, Claude Code asks you.\n\n");
    appendGradleTasks(out, tasks);
    appendOtherCommands(out, others);
  }

  private static void appendGradleTasks(StringBuilder out, Set<String> tasks) {
    out.append("- `").append(GRADLE).append("` with the tasks ");
    out.append(joinQuoted(tasks));
    out.append(". `build`, `run` and `test` run your project's own build, application and tests.");
    out.append(
        " The NarrativeTrace tasks read your project and write under `build/narrativetrace/`");
    out.append(" and `src/test/narratives/`; `narrativetraceInit --diff` only previews an install");
    out.append(
        " and writes nothing. Applying the install, which writes `AGENTS.md` and skill files");
    out.append(" into your project, and promoting an approval baseline with `approveNarratives`");
    out.append(" are separate steps that wait for your yes.\n");
  }

  private static void appendOtherCommands(StringBuilder out, Set<String> others) {
    out.append("- `git` and `find`, to read what is in your project:\n\n");
    out.append("```\n");
    others.forEach(command -> out.append(command).append('\n'));
    out.append("```\n\n");
    out.append(
        "Two skills also edit your project's files: they add the NarrativeTrace dependencies");
    out.append(" or Gradle plugin to your build file and, in a project with no entry point, a");
    out.append(" small console app that shows a first trace.\n\n");
  }

  private static void appendFetches(StringBuilder out) {
    out.append("## What the skills fetch\n\n");
    out.append("- The newest release number of NarrativeTrace, with one read-only request to");
    out.append(" Maven Central's metadata for the core library, so the skills never write a");
    out.append(" version from memory. The skills tell Claude to run this, or to open the same");
    out.append(" address with any fetch tool:\n\n");
    out.append("```\n").append(ReleaseLookup.COMMAND).append("\n```\n\n");
    out.append("- Dependencies: your own Gradle build resolves the NarrativeTrace libraries and");
    out.append(" Gradle plugin, and what they depend on, from Maven Central and the Gradle Plugin");
    out.append(" Portal, as it resolves any dependency. The skills fetch nothing else.\n\n");
  }

  private static void appendSends(StringBuilder out, MarketplaceListing listing) {
    out.append("## What the skills send\n\n");
    out.append("Nothing leaves your machine from this plugin: no skill uploads, posts or");
    out.append(
        " transmits anything, and it has no hooks, MCP servers or network code of its own.\n\n");
    out.append("`narrativetrace-feedback` is the one skill about reporting a defect in");
    out.append(" NarrativeTrace itself. It drafts a report from your project into");
    out.append(" `build/narrativetrace/feedback/` (the install coordinates, the doctor's JSON");
    out.append(" report and at most one structural trace, and it refuses to write a report that");
    out.append(" carries a value from your own run), shows you the whole draft, and asks once");
    out.append(" whether to file it. Only after your yes does it print a pre-filled issue address");
    out.append(" (")
        .append(listing.repository())
        .append("/issues/new?...) and the name of the body");
    out.append(" file to paste. It never opens that address and never runs the command it prints:");
    out.append(" nothing is sent unless you open the address yourself, in your own browser, under");
    out.append(" your own account, and the issue you file is public.\n\n");
  }

  private static void appendLicense(StringBuilder out, MarketplaceListing listing) {
    out.append("## License and links\n\n");
    out.append("The skills in this plugin are licensed under ").append(listing.license());
    out.append(".\n\n");
    out.append("- Homepage: ").append(listing.homepage()).append('\n');
    out.append("- Repository: ").append(listing.repository()).append('\n');
  }

  /** The first sentence of the description; a "Use when" lead-in is cut at its first colon. */
  private static String summary(Skill skill) {
    String description = skill.description();
    int end = description.indexOf(". ");
    String sentence = end < 0 ? description : description.substring(0, end + 1);
    if (sentence.startsWith("Use when") && sentence.contains(":")) {
      sentence = sentence.substring(0, sentence.indexOf(':')) + ".";
    }
    return sentence;
  }

  /** The task names of a wrapper command line: the words before its first option or placeholder. */
  private static List<String> gradleTasks(String command) {
    List<String> tasks = new java.util.ArrayList<>();
    for (String token : command.substring(GRADLE.length()).trim().split("\\s+")) {
      if (token.startsWith("-") || token.startsWith("<")) {
        break;
      }
      tasks.add(token);
    }
    return tasks;
  }

  private static String joinQuoted(Set<String> values) {
    return String.join(", ", values.stream().map(v -> "`" + v + "`").toList());
  }

  private static String slug(MarketplaceListing listing) {
    String prefix = "https://github.com/";
    return listing.repository().startsWith(prefix)
        ? listing.repository().substring(prefix.length())
        : listing.repository();
  }
}
