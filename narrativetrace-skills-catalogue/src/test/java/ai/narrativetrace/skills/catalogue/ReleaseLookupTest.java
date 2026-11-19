/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import org.junit.jupiter.api.Test;

/**
 * The newest-release lookup the Java skills share. Every library and the Gradle plugin are
 * published at one version, so the one lookup serves the install and the plugin line alike.
 */
class ReleaseLookupTest {

  @Test
  void theMetadataIsTheCoreArtifactsOnMavenCentral() {
    assertThat(ReleaseLookup.METADATA_URL)
        .isEqualTo(
            "https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/maven-metadata.xml");
  }

  @Test
  void theCommandIsOnePosixLineThatReadsTheReleaseElementAndNothingElse() {
    assertThat(ReleaseLookup.COMMAND)
        .isEqualTo(
            "curl -s " + ReleaseLookup.METADATA_URL + " | grep -o '<release>[^<]*' | cut -d'>' -f2")
        .doesNotContain("\n");
  }

  @Test
  void theStepCarriesTheCommandAndTheUrlFallbackAndTellsTheAgentNeverToGuess() {
    SkillStep step = ReleaseLookup.step();

    assertThat(((StepBody.CodeStep) step.body()).code()).contains(ReleaseLookup.COMMAND);
    assertThat(step.verify())
        .contains("<version>")
        .contains(ReleaseLookup.METADATA_URL)
        .contains("<release>")
        .contains("never");
    assertThat(step.failure()).isNotEmpty();
  }

  @Test
  void nothingInTheStepNamesAVersionNumber() {
    SkillStep step = ReleaseLookup.step();

    assertThat(((StepBody.CodeStep) step.body()).code())
        .doesNotContainPattern("\\d+\\.\\d+\\.\\d+");
    assertThat(step.verify()).doesNotContainPattern("\\d+\\.\\d+\\.\\d+");
  }
}
