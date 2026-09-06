/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class EnvironmentCheckTest {

  @Test
  void acceptsTheSupportedFloorExactly() {
    assertThat(EnvironmentCheck.problem("8.0", 17)).isEmpty();
  }

  @Test
  void acceptsVersionsAboveTheFloor() {
    assertThat(EnvironmentCheck.problem("8.14.2", 21)).isEmpty();
    assertThat(EnvironmentCheck.problem("9.0", 25)).isEmpty();
  }

  @Test
  void rejectsGradleBelowTheFloor() {
    assertThat(EnvironmentCheck.problem("7.6.4", 17))
        .get()
        .asString()
        .contains("Gradle 8.0")
        .contains("7.6.4");
  }

  @Test
  void comparesGradleVersionsNumericallyNotLexically() {
    assertThat(EnvironmentCheck.problem("10.1", 17)).isEmpty();
    assertThat(EnvironmentCheck.problem("7.10", 17)).isPresent();
  }

  @Test
  void rejectsJavaBelowSeventeen() {
    assertThat(EnvironmentCheck.problem("8.14.2", 11))
        .get()
        .asString()
        .contains("Java 17")
        .contains("11");
  }

  @Test
  void reportsBothProblemsInOneMessage() {
    var problem = EnvironmentCheck.problem("7.6", 11);

    assertThat(problem).get().asString().contains("Gradle 8.0").contains("Java 17");
  }

  @Test
  void toleratesGradleMilestoneAndReleaseCandidateVersions() {
    assertThat(EnvironmentCheck.problem("8.0-milestone-1", 17)).isEmpty();
    assertThat(EnvironmentCheck.problem("8.5-rc-2", 17)).isEmpty();
    assertThat(EnvironmentCheck.problem("7.6-rc-1", 17)).isPresent();
  }

  @Test
  void treatsAnUnparseableGradleVersionAsAcceptable() {
    assertThat(EnvironmentCheck.problem("weird-build", 17)).isEmpty();
  }

  @Test
  void treatsAnUnknownJavaVersionAsAcceptable() {
    assertThat(EnvironmentCheck.problem("8.14.2", EnvironmentCheck.UNKNOWN_JAVA)).isEmpty();
  }

  @Test
  void rejectsANullGradleVersion() {
    assertThatThrownBy(() -> EnvironmentCheck.problem(null, 17))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("gradleVersion");
  }

  @Test
  void verifyThrowsOnAnUnsupportedEnvironment() {
    assertThatThrownBy(() -> EnvironmentCheck.verify("7.6", 11))
        .isInstanceOf(GradleException.class)
        .hasMessageContaining("Gradle 8.0");
  }

  @Test
  void verifyIsSilentOnASupportedEnvironment() {
    EnvironmentCheck.verify("8.14.2", 17);
  }

  @Test
  void namesThePluginSoTheMessageIsSelfExplanatory() {
    Optional<String> problem = EnvironmentCheck.problem("7.6", 17);

    assertThat(problem).get().asString().contains("ai.narrativetrace");
  }
}
