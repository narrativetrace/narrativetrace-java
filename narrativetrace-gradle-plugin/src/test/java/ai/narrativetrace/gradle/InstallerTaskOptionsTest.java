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

import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.init.InitOptions.Vendor;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

/**
 * The task options a person types on the command line, turned into what the installer library
 * takes. A wrong value fails here, with the accepted ones named — never silently as a default.
 */
class InstallerTaskOptionsTest {

  private static InitOptions options(String only, String vendor) {
    return InstallerTaskOptions.from(false, false, false, only, vendor);
  }

  @Test
  void noOptionsMeansTheLibrarysOwnDefaults() {
    assertThat(options("", "")).isEqualTo(InitOptions.defaults());
  }

  @Test
  void anUnsetOptionIsTheSameAsAnEmptyOne() {
    assertThat(options(null, null)).isEqualTo(InitOptions.defaults());
  }

  @Test
  void eachSwitchReachesItsOwnOption() {
    assertThat(InstallerTaskOptions.from(true, false, false, "", "").dryRun()).isTrue();
    assertThat(InstallerTaskOptions.from(false, true, false, "", "").writeExisting()).isTrue();
    assertThat(InstallerTaskOptions.from(false, false, true, "", "").force()).isTrue();
  }

  @Test
  void onlyTakesEitherHalfOfTheInstall() {
    assertThat(options("skills", "").scope()).isEqualTo(Scope.SKILLS);
    assertThat(options("agents-md", "").scope()).isEqualTo(Scope.AGENTS_MD);
  }

  @Test
  void vendorTurnsTheClaudeFlavourOnOrOff() {
    assertThat(options("", "claude").vendorClaude()).isEqualTo(Vendor.ON);
    assertThat(options("", "none").vendorClaude()).isEqualTo(Vendor.OFF);
  }

  @Test
  void anUnknownOnlyFailsAndNamesWhatIsAccepted() {
    assertThatThrownBy(() -> options("everything", ""))
        .isInstanceOf(GradleException.class)
        .hasMessageContaining("--only")
        .hasMessageContaining("skills")
        .hasMessageContaining("agents-md")
        .hasMessageContaining("everything");
  }

  @Test
  void anUnknownVendorFailsAndNamesWhatIsAccepted() {
    assertThatThrownBy(() -> options("", "cursor"))
        .isInstanceOf(GradleException.class)
        .hasMessageContaining("--vendor")
        .hasMessageContaining("claude")
        .hasMessageContaining("none")
        .hasMessageContaining("cursor");
  }

  /** Trailing whitespace comes free with a shell; it must not cost a person a failed build. */
  @Test
  void aValueIsTrimmedBeforeItIsRead() {
    assertThat(options(" skills ", " claude ").scope()).isEqualTo(Scope.SKILLS);
    assertThat(options(" skills ", " claude ").vendorClaude()).isEqualTo(Vendor.ON);
  }
}
