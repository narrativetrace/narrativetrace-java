/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.lint;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.ProListing;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tier A lints — schema/frontmatter validity, referenced files exist, the closed command
 * vocabulary, description budgets, catalogue-index-vs-disk agreement, Pro-listing statuses vs the
 * feature guide, and no private citations in shipped text. Seconds, zero cost, no LLM: mirrors
 * {@code packages/skills/__tests__/lints.test.ts} in the TypeScript reference.
 */
public final class Lints {

  /** Catalogue-wide description budget — well under this keeps every skill's description live. */
  public static final int CATALOGUE_CHAR_BUDGET = 40_000;

  /**
   * Step titles allowed to skip {@code verify}/{@code flag}: inherently judgmental steps a
   * mechanical check cannot stand in for (harness §7). Extend this list, never bypass a step's own
   * lint failure by adding a flag it does not need.
   */
  public static final Set<String> JUDGMENTAL_STEP_TITLES =
      Set.of("Read the rendered trace before asserting");

  /**
   * Patterns that must never appear in shipped skill text: a private planning-note citation, a
   * reference to the Pro repository, a CI config filename, or a git SHA — rationales stay in
   * shipped text, citations go (frozen ruling, agent-skills-2026-09-12.md §7 ruling 9).
   */
  private static final List<Pattern> CITATION_PATTERNS =
      List.of(
          Pattern.compile("planning/"),
          Pattern.compile("narrative-trace-java-pro"),
          Pattern.compile("\\.gitlab-ci\\.yml"),
          Pattern.compile("§\\d"),
          Pattern.compile("\\b[0-9a-f]{7,40}\\b"));

  private Lints() {}

  /** Skills whose description (or catalogue sum) exceeds budget. */
  public static List<String> descriptionBudgetViolations(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    int total = 0;
    for (Skill skill : skills) {
      total += skill.description().length();
      if (!skill.descriptionFitsBudget()) {
        violations.add(
            skill.canonicalName()
                + ": description is "
                + skill.description().length()
                + " chars, over the "
                + Skill.DESCRIPTION_BUDGET_CHARS
                + "-char budget");
      }
    }
    if (total > CATALOGUE_CHAR_BUDGET) {
      violations.add(
          "catalogue-wide descriptions total "
              + total
              + " chars, over the "
              + CATALOGUE_CHAR_BUDGET
              + "-char budget");
    }
    return List.copyOf(violations);
  }

  /** Every command string, across every skill, whose first token is outside the vocabulary. */
  public static List<String> vocabularyViolations(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    for (Skill skill : skills) {
      for (String command : skill.vocabularyViolations()) {
        violations.add(skill.canonicalName() + ": \"" + command + "\" is outside the vocabulary");
      }
    }
    return List.copyOf(violations);
  }

  /**
   * Every declared allowed tool that is not a bare command of the closed vocabulary: an entry
   * outside {@link CommandVocabulary#JAVA}, or one already written in a platform's own rendered
   * spelling (a {@code Bash(...)} tool pattern).
   *
   * <p>This is what pins the rendering the Claude flavour emits: a catalogue entry is the COMMAND,
   * and {@link CommandVocabulary#claudeToolPattern} is the single place it becomes a tool pattern.
   * A hand-written {@code Bash(git *)} in the catalogue would render as {@code Bash(Bash(git *) *)}
   * — a rule that matches nothing, in a field whose whole purpose is to match something.
   */
  public static List<String> allowedToolsViolations(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    for (Skill skill : skills) {
      for (String tool : skill.allowedTools()) {
        if (tool.contains("(")) {
          violations.add(
              skill.canonicalName()
                  + ": allowed tool \""
                  + tool
                  + "\" is a rendered platform spelling — declare the bare command and let the"
                  + " renderer spell it");
        } else if (!CommandVocabulary.JAVA.contains(tool)) {
          violations.add(
              skill.canonicalName() + ": allowed tool \"" + tool + "\" is outside the vocabulary");
        }
      }
    }
    return List.copyOf(violations);
  }

  /** Non-judgmental steps missing both {@code verify} and {@code flag}. */
  public static List<String> stepsWithoutVerifyOrFlag(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    for (Skill skill : skills) {
      for (SkillStep step : skill.stepsWithoutVerifyOrFlag()) {
        if (!JUDGMENTAL_STEP_TITLES.contains(step.title())) {
          violations.add(
              skill.canonicalName()
                  + ": step \""
                  + step.title()
                  + "\" has neither verify nor flag, and is not a known judgmental step");
        }
      }
    }
    return List.copyOf(violations);
  }

  /** Every {@code fixture} directory a skill declares must actually exist under the repo root. */
  public static List<String> missingFixtures(List<Skill> skills, Path repoRoot) {
    List<String> violations = new ArrayList<>();
    for (Skill skill : skills) {
      if (!Files.isDirectory(repoRoot.resolve(skill.fixture()))) {
        violations.add(skill.canonicalName() + ": fixture \"" + skill.fixture() + "\" not found");
      }
    }
    return List.copyOf(violations);
  }

  /** No duplicate canonical names, and the catalogue is non-empty. */
  public static List<String> duplicateCanonicalNames(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    Set<String> seen = new java.util.HashSet<>();
    for (Skill skill : skills) {
      if (!seen.add(skill.canonicalName())) {
        violations.add("duplicate canonical name: " + skill.canonicalName());
      }
    }
    return List.copyOf(violations);
  }

  /**
   * Every Pro listing's status must match the runtime's own feature guide (keyed by canonical
   * name). Empty by construction while {@code ProListings.ALL} is empty — see its doc comment.
   */
  public static List<String> proListingStatusDisagreements(
      List<ProListing> listings, java.util.Map<String, String> featureGuideStatusByName) {
    List<String> violations = new ArrayList<>();
    for (ProListing listing : listings) {
      String documented = featureGuideStatusByName.get(listing.canonicalName());
      if (documented == null) {
        violations.add(listing.canonicalName() + ": not found in the feature guide");
      } else if (!documented.equals(listing.featureGuideStatusText())) {
        violations.add(
            listing.canonicalName()
                + ": listing says \""
                + listing.featureGuideStatusText()
                + "\", feature guide says \""
                + documented
                + "\"");
      }
    }
    return List.copyOf(violations);
  }

  /**
   * Commands that reach a channel where something becomes PUBLIC, and must therefore never be
   * pre-approved by the skill that names them.
   *
   * <p>Today that is the problem-report verb, in both its spellings. The pattern is a list rather
   * than one literal because a port's spelling differs and a second publishing command is a
   * question somebody has to answer, not a regex somebody widens.
   */
  private static final List<Pattern> PUBLISHING_COMMANDS =
      List.of(Pattern.compile("\\bnarrativetraceFeedback\\b"), Pattern.compile("\\bfeedback\\b"));

  /**
   * A skill whose steps can make something public must declare NO allowed tool that would
   * pre-approve the command doing it.
   *
   * <p>INTENT: the Claude flavour's {@code allowed-tools} grants its listed tools for the turn that
   * LOADS the skill, without prompting. A reporting skill that declared {@code ./gradlew} would
   * therefore pre-approve its own reporting command — the harness would stop asking exactly where
   * asking is the product. The absence of the field is the safety property, so it is linted rather
   * than left to whoever edits the catalogue next.
   *
   * <p><b>@llmNote</b> Named {@code sendNotPreApproved} in the design, before the private endpoint
   * was deferred and "send" became "file publicly". Same rule, honest name: nothing is sent
   * anywhere, and what must not be pre-approved is the step that publishes.
   */
  public static List<String> publishingNotPreApproved(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    for (Skill skill : skills) {
      for (String command : skill.commandStrings()) {
        if (publishes(command)) {
          collectPreApprovals(skill, command, violations);
        }
      }
    }
    return List.copyOf(violations);
  }

  private static boolean publishes(String command) {
    return PUBLISHING_COMMANDS.stream().anyMatch(pattern -> pattern.matcher(command).find());
  }

  private static void collectPreApprovals(Skill skill, String command, List<String> violations) {
    String tool = CommandVocabulary.firstToken(command);
    if (skill.allowedTools().contains(tool)) {
      violations.add(
          skill.canonicalName()
              + ": declares allowed tool \""
              + tool
              + "\", which pre-approves its own publishing command \""
              + command
              + "\" — a skill that files something public must let the harness ask");
    }
  }

  /**
   * The only tools a skill that promotes an approval baseline may pre-approve: read-only discovery.
   */
  public static final List<String> PROMOTING_SKILL_ALLOWANCE = List.of("find");

  /** Commands that write a committed approval baseline on the user's behalf. */
  private static final List<Pattern> PROMOTING_COMMANDS =
      List.of(Pattern.compile("\\bapproveNarratives\\b"));

  /**
   * A skill whose steps write into the project on the user's yes — promoting an approval baseline —
   * declares no allowed tool beyond {@link #PROMOTING_SKILL_ALLOWANCE}.
   *
   * <p>INTENT: the same reason as {@link #publishingNotPreApproved}, for a different durable act.
   * The promotion runs through the build wrapper, so a skill that pre-approved {@code ./gradlew}
   * would pre-approve its own promotion — and {@code git} could commit the result — in the very
   * turn the gate says must stop and ask. Read-only discovery is all such a skill may skip asking
   * for.
   */
  public static List<String> promotionNotPreApproved(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    for (Skill skill : skills) {
      if (skill.commandStrings().stream().anyMatch(Lints::promotes)) {
        for (String tool : skill.allowedTools()) {
          if (!PROMOTING_SKILL_ALLOWANCE.contains(tool)) {
            violations.add(
                skill.canonicalName()
                    + ": promotes an approval baseline yet declares allowed tool \""
                    + tool
                    + "\", beyond the allowance "
                    + PROMOTING_SKILL_ALLOWANCE
                    + " — a skill that writes into the project on the user's yes must let the"
                    + " harness ask");
          }
        }
      }
    }
    return List.copyOf(violations);
  }

  private static boolean promotes(String command) {
    return PROMOTING_COMMANDS.stream().anyMatch(pattern -> pattern.matcher(command).find());
  }

  /** Citation-shaped text in a skill's own prose fields — rationales stay, citations go. */
  public static List<String> citationViolations(List<Skill> skills) {
    List<String> violations = new ArrayList<>();
    for (Skill skill : skills) {
      for (String prose : proseOf(skill)) {
        for (Pattern pattern : CITATION_PATTERNS) {
          if (pattern.matcher(prose).find()) {
            violations.add(
                skill.canonicalName() + ": text matches forbidden pattern " + pattern.pattern());
          }
        }
      }
    }
    return List.copyOf(violations);
  }

  private static List<String> proseOf(Skill skill) {
    List<String> prose = new ArrayList<>();
    prose.add(skill.description());
    skill.whenToUseOptional().ifPresent(prose::add);
    for (SkillStep step : skill.steps()) {
      prose.add(step.title());
      step.verifyOptional().ifPresent(prose::add);
      step.flagOptional().ifPresent(prose::add);
      step.conditionOptional().ifPresent(prose::add);
      step.failure()
          .forEach(
              f -> {
                prose.add(f.symptom());
                prose.add(f.cause());
                prose.add(f.fix());
              });
      if (step.body() instanceof StepBody.CodeStep code) {
        prose.add(code.code());
      }
    }
    skill
        .always()
        .forEach(
            r -> {
              prose.add(r.rule());
              prose.add(r.reason());
            });
    skill
        .never()
        .forEach(
            r -> {
              prose.add(r.rule());
              prose.add(r.reason());
            });
    return prose;
  }
}
