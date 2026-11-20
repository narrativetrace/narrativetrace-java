/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.MarketplaceListing;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import ai.narrativetrace.skills.catalogue.ReleaseLookup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The plugin README is what Anthropic's directory shows as the listing description, and the
 * security scan reads it for behaviour the plugin does not disclose. These tests derive each
 * disclosed fact from the catalogue's own data independently of the renderer, so a command added to
 * a skill and not disclosed fails here.
 */
class ClaudePluginReadmeRendererTest {

  private static final MarketplaceListing LISTING = CatalogueIndex.MARKETPLACE;
  private static final String README =
      ClaudePluginReadmeRenderer.render(LISTING, CatalogueIndex.ALL);

  @Test
  void listsEverySkillByNameWithItsDescriptionsFirstSentence() {
    for (Skill skill : CatalogueIndex.ALL) {
      String firstSentence = skill.description().split("(?<=\\.)\\s", 2)[0];
      String line =
          README
              .lines()
              .filter(l -> l.contains("`" + skill.canonicalName() + "`"))
              .findFirst()
              .orElseThrow();

      assertThat(line)
          .as(skill.canonicalName())
          .startsWith("- ")
          .contains(firstSentence.split(":", 2)[0].strip().replaceAll("\\.$", ""));
    }
  }

  /** The directory's rule: at least 40 words, words inside code blocks not counted. */
  @Test
  void hasMoreThanFortyWordsOutsideCodeBlocks() {
    String prose = README.replaceAll("(?s)```.*?```", " ");

    assertThat(prose.trim().split("\\s+")).hasSizeGreaterThanOrEqualTo(40);
  }

  @Test
  void disclosesEveryGradleTaskTheSkillsRun() {
    Set<String> tasks = new TreeSet<>();
    for (Skill skill : CatalogueIndex.ALL) {
      for (String command : skill.commandStrings()) {
        if (command.startsWith("./gradlew")) {
          for (String token : command.substring("./gradlew".length()).trim().split("\\s+")) {
            if (token.startsWith("-") || token.startsWith("<")) {
              break;
            }
            tasks.add(token);
          }
        }
      }
    }

    assertThat(tasks).contains("narrativetraceInit", "narrativetraceDoctor", "test", "build");
    for (String task : tasks) {
      assertThat(README).as("task " + task).contains("`" + task + "`");
    }
  }

  @Test
  void disclosesEveryGitAndFindCommandAndTheReleaseLookupCommand() {
    Set<String> others = new LinkedHashSet<>();
    for (Skill skill : CatalogueIndex.ALL) {
      for (String command : skill.commandStrings()) {
        if (!command.startsWith("./gradlew")) {
          others.add(command);
        }
      }
    }

    assertThat(others)
        .isNotEmpty()
        .anyMatch(c -> c.startsWith("git"))
        .anyMatch(c -> c.startsWith("find"));
    for (String command : others) {
      assertThat(README).as(command).contains(command);
    }
    assertThat(README).contains(ReleaseLookup.COMMAND);
  }

  @Test
  void disclosesWhatItFetchesAndFromWhere() {
    assertThat(README)
        .contains(ReleaseLookup.METADATA_URL)
        .contains("Maven Central")
        .contains("Gradle Plugin Portal");
  }

  @Test
  void statesPreciselyWhatTheFeedbackSkillDoesAndThatNothingIsSent() {
    assertThat(README)
        .contains("narrativetrace-feedback")
        .contains("pre-filled")
        .contains(LISTING.repository() + "/issues/new")
        .contains("nothing is sent")
        .contains("never opens");
  }

  @Test
  void statesThePluginPreApprovesNoCommandAndCarriesNoHooksOrServers() {
    assertThat(README).contains("pre-approves no command").contains("no hooks");
  }

  @Test
  void carriesTheLicenseHomepageAndRepositoryFromTheListing() {
    assertThat(README)
        .contains(LISTING.license())
        .contains(LISTING.homepage())
        .contains(LISTING.repository());
  }

  @Test
  void namesTheInstallCommandsFromTheListing() {
    assertThat(README)
        .contains("/plugin marketplace add narrativetrace/narrativetrace-java")
        .contains("/plugin install " + LISTING.name() + "@" + LISTING.name());
  }

  @Test
  void carriesNoVersionNumberSoAReleaseThatLeavesTheSkillsAloneLeavesItByteIdentical() {
    Matcher version = Pattern.compile("\\d+\\.\\d+\\.\\d+").matcher(README);

    assertThat(version.find()).isFalse();
  }

  @Test
  void rendersByteIdenticallyEveryTime() {
    assertThat(ClaudePluginReadmeRenderer.render(LISTING, CatalogueIndex.ALL)).isEqualTo(README);
  }

  @Test
  void statesThePlainNoWarrantyLineInTheLicenseSection() {
    String license = README.substring(README.indexOf("## License and links"));

    assertThat(license)
        .contains(
            "NarrativeTrace is provided as is: no warranty, and no guarantee that it fits your"
                + " purpose. Use it at your own risk.");
  }

  /**
   * The quoted disclaimer is the repository's own LICENSE-APACHE section 7, character for
   * character.
   */
  @Test
  void quotesTheApacheDisclaimerOfWarrantyExactlyAsLicenseApacheHasIt() throws IOException {
    String licenseFile =
        Files.readString(Path.of(System.getProperty("projectDir"), "LICENSE-APACHE"));
    int start = licenseFile.indexOf("7. Disclaimer of Warranty.");
    String section7 = licenseFile.substring(start, licenseFile.indexOf("\n\n", start));
    String quoted =
        section7
            .lines()
            .map(line -> "> " + line.strip())
            .reduce((a, b) -> a + "\n" + b)
            .orElseThrow();

    assertThat(README).contains(quoted);
  }

  @Test
  void linksTheApacheLicenseFileAndNamesTheLimitationOfLiabilitySection() {
    assertThat(README)
        .contains(LISTING.repository() + "/blob/main/LICENSE-APACHE")
        .contains("Section 8")
        .contains("Limitation of Liability");
  }

  @Test
  void saysTheRuntimeTheSkillsInstallIsUnderTheBusinessSourceLicenseAndLinksLicense() {
    assertThat(README)
        .contains(
            "The NarrativeTrace runtime that these skills install is licensed under the Business"
                + " Source License 1.1; see [LICENSE]("
                + LISTING.repository()
                + "/blob/main/LICENSE).");
  }

  @Test
  void limitsTheSectionEightPointerToTheApacheLicensedParts() {
    assertThat(README).contains("limits liability further (Apache-licensed parts only).");
  }
}
