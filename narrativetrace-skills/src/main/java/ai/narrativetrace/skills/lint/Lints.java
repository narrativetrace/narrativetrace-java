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
package ai.narrativetrace.skills.lint;

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
