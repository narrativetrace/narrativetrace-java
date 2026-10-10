/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.lint;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.ProListing;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import ai.narrativetrace.skills.catalogue.ProListings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tier A: schema/frontmatter validity, referenced fixtures exist, the closed command vocabulary,
 * description budgets, no duplicate names, Pro-listing statuses agreeing with the feature guide,
 * and no private citations in shipped text. Seconds, zero cost, no LLM — mirrors {@code
 * packages/skills/__tests__/lints.test.ts} in the TypeScript reference.
 */
class CatalogueLintsTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));
  private static final List<Skill> SKILLS = CatalogueIndex.ALL;

  @Test
  void catalogueIsNonEmptyWithNoDuplicateNames() {
    assertThat(SKILLS).isNotEmpty();
    assertThat(Lints.duplicateCanonicalNames(SKILLS)).isEmpty();
  }

  @Test
  void everyDescriptionFitsItsBudget() {
    assertThat(Lints.descriptionBudgetViolations(SKILLS)).isEmpty();
  }

  @Test
  void everyCommandStaysInTheClosedVocabulary() {
    assertThat(Lints.vocabularyViolations(SKILLS)).isEmpty();
  }

  @Test
  void everyDeclaredAllowedToolIsABareVocabularyCommand() {
    assertThat(Lints.allowedToolsViolations(SKILLS)).isEmpty();
  }

  /**
   * The absence of {@code allowed-tools} on a reporting skill is a safety property, so it is
   * asserted rather than trusted. The field grants its listed tools for the turn that LOADS the
   * skill without prompting — a reporting skill that declared the wrapper would pre-approve its own
   * publishing command, and the harness would stop asking exactly where asking is the product.
   */
  @Test
  void noSkillPreApprovesItsOwnPublishingCommand() {
    assertThat(Lints.publishingNotPreApproved(SKILLS)).isEmpty();
  }

  @Test
  void noSkillPreApprovesItsOwnPromotion() {
    assertThat(Lints.promotionNotPreApproved(SKILLS)).isEmpty();
  }

  @Test
  void everyNonJudgmentalStepCarriesVerifyOrFlag() {
    assertThat(Lints.stepsWithoutVerifyOrFlag(SKILLS)).isEmpty();
  }

  @Test
  void everyDeclaredFixtureExistsOnDisk() {
    assertThat(Lints.missingFixtures(SKILLS, REPO_ROOT)).isEmpty();
  }

  @Test
  void noShippedTextCitesAPrivateSource() {
    assertThat(Lints.citationViolations(SKILLS)).isEmpty();
  }

  @Test
  void proListingStatusesAgreeWithTheFeatureGuide() {
    // ProListings.ALL is deliberately empty in this port — see its own doc comment. An empty
    // feature-guide map is therefore a vacuous pass, not a placeholder to remove later without
    // thought: populate both together, from the Pro repo's own feature-guide.md.
    Map<String, String> featureGuideStatusByName = Map.of();
    List<ProListing> listings = ProListings.ALL;

    assertThat(Lints.proListingStatusDisagreements(listings, featureGuideStatusByName)).isEmpty();
  }
}
